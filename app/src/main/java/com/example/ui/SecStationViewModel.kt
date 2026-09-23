package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LinuxEnvironment
import com.example.data.ActiveSessionState
import com.example.data.SecStationRepository
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class ScreenTab(val title: String) {
    TERMINAL("Terminal"),
    TOOLS("Tools"),
    PACKAGES("Packages"),
    BUG_BOUNTY("Bug Bounty"),
    CTF_LABS("CTF / Labs"),
    FILES("Files"),
    SYSTEM("System & Logs")
}

class SecStationViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SecStationRepository(application)
    val linuxEnv: LinuxEnvironment = repository.linuxEnv

    private val _currentTab = MutableStateFlow(ScreenTab.TERMINAL)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    private val _isBootstrapped = MutableStateFlow(linuxEnv.isEnvironmentReady())
    val isBootstrapped: StateFlow<Boolean> = _isBootstrapped.asStateFlow()

    private val _setupSteps = MutableStateFlow<List<SetupStep>>(emptyList())
    val setupSteps: StateFlow<List<SetupStep>> = _setupSteps.asStateFlow()

    private val _isBootstrapping = MutableStateFlow(false)
    val isBootstrapping: StateFlow<Boolean> = _isBootstrapping.asStateFlow()

    // Terminal State
    val sessions = repository.sessions
    val activeSessionId = repository.activeSessionId

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _historyPosition = MutableStateFlow(-1)

    private val _terminalFontSize = MutableStateFlow(14)
    val terminalFontSize: StateFlow<Int> = _terminalFontSize.asStateFlow()

    private val _terminalTheme = MutableStateFlow(TerminalTheme.KALI)
    val terminalTheme: StateFlow<TerminalTheme> = _terminalTheme.asStateFlow()

    private val _terminalSearchQuery = MutableStateFlow("")
    val terminalSearchQuery: StateFlow<String> = _terminalSearchQuery.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _autoCompleteSuggestions = MutableStateFlow<List<String>>(emptyList())
    val autoCompleteSuggestions: StateFlow<List<String>> = _autoCompleteSuggestions.asStateFlow()

    // Active Running Command Job for Ctrl+C
    private var activeJob: Job? = null

    // File Manager State
    private val _currentBrowserDir = MutableStateFlow(linuxEnv.homeDir)
    val currentBrowserDir: StateFlow<File> = _currentBrowserDir.asStateFlow()

    private val _browserFiles = MutableStateFlow<List<LinuxFileItem>>(emptyList())
    val browserFiles: StateFlow<List<LinuxFileItem>> = _browserFiles.asStateFlow()

    private val _selectedFileContent = MutableStateFlow<Pair<String, String>?>(null) // (fileName, content)
    val selectedFileContent: StateFlow<Pair<String, String>?> = _selectedFileContent.asStateFlow()

    // Scope Reminder Modal State
    private val _scopeAcknowledged = MutableStateFlow(false)
    val scopeAcknowledged: StateFlow<Boolean> = _scopeAcknowledged.asStateFlow()

    init {
        if (isBootstrapped.value) {
            repository.initInitialSession()
            refreshBrowserFiles()
        } else {
            // Start bootstrap automatically on first launch
            startBootstrap()
        }
    }

    fun selectTab(tab: ScreenTab) {
        _currentTab.value = tab
        if (tab == ScreenTab.FILES) {
            refreshBrowserFiles()
        }
        if (tab == ScreenTab.SYSTEM) {
            repository.refreshMetrics()
        }
    }

    fun acknowledgeScope() {
        _scopeAcknowledged.value = true
    }

    fun startBootstrap() {
        _isBootstrapping.value = true
        _setupSteps.value = emptyList()
        viewModelScope.launch {
            val result = linuxEnv.bootstrap { step ->
                val current = _setupSteps.value.toMutableList()
                val existingIdx = current.indexOfFirst { it.title == step.title }
                if (existingIdx >= 0) {
                    current[existingIdx] = step
                } else {
                    current.add(step)
                }
                _setupSteps.value = current
            }
            _isBootstrapping.value = false
            if (result.isSuccess) {
                _isBootstrapped.value = true
                repository.initInitialSession()
                refreshBrowserFiles()
                repository.logDiagnostic("Bootstrap completed successfully.")
            } else {
                repository.logDiagnostic("Bootstrap failed: ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    // Terminal Commands
    fun updateCommandInput(input: String) {
        _commandInput.value = input
        val activeSession = sessions.value.firstOrNull { it.id == activeSessionId.value }
        val currentDir = activeSession?.cwd ?: linuxEnv.homeDir
        if (input.isNotBlank()) {
            _autoCompleteSuggestions.value = repository.shellExecutor.tabComplete(input, currentDir)
        } else {
            _autoCompleteSuggestions.value = emptyList()
        }
    }

    fun executeCommand(command: String? = null) {
        val cmd = (command ?: _commandInput.value).trim()
        if (cmd.isEmpty()) return

        val activeId = activeSessionId.value
        val session = sessions.value.firstOrNull { it.id == activeId } ?: return

        _commandInput.value = ""
        _historyPosition.value = -1
        _autoCompleteSuggestions.value = emptyList()

        repository.addSessionHistory(activeId, cmd)
        repository.setSessionRunning(activeId, true)

        val promptText = "kali@android:${formatPathForPrompt(session.cwd)}$ "
        repository.appendLineToSession(activeId, TerminalLine(text = promptText + cmd, type = TerminalLineType.PROMPT))

        if (cmd.equals("clear", ignoreCase = true)) {
            repository.clearSession(activeId)
            repository.setSessionRunning(activeId, false)
            return
        }

        activeJob = viewModelScope.launch {
            try {
                repository.shellExecutor.execute(
                    rawCommand = cmd,
                    currentDir = session.cwd,
                    onDirChanged = { newDir ->
                        repository.updateSessionCwd(activeId, newDir)
                    }
                ).collect { line ->
                    repository.appendLineToSession(activeId, line)
                }
            } finally {
                repository.setSessionRunning(activeId, false)
                activeJob = null
            }
        }
    }

    fun sendCtrlC() {
        activeJob?.cancel()
        activeJob = null
        val activeId = activeSessionId.value
        repository.setSessionRunning(activeId, false)
        repository.appendLineToSession(activeId, TerminalLine(text = "^C", type = TerminalLineType.WARNING))
    }

    fun sendCtrlD() {
        val activeId = activeSessionId.value
        if (sessions.value.size > 1) {
            repository.closeSession(activeId)
        } else {
            repository.appendLineToSession(activeId, TerminalLine(text = "exit (session reset)", type = TerminalLineType.INFO))
            repository.clearSession(activeId)
        }
    }

    fun applyTabCompletion(suggestion: String) {
        val current = _commandInput.value
        val lastWord = current.substringAfterLast(" ")
        val updated = if (current.contains(" ")) {
            current.substringBeforeLast(" ") + " " + suggestion
        } else {
            suggestion
        }
        _commandInput.value = updated
        _autoCompleteSuggestions.value = emptyList()
    }

    fun historyPrevious() {
        val activeId = activeSessionId.value
        val session = sessions.value.firstOrNull { it.id == activeId } ?: return
        val hist = session.history
        if (hist.isEmpty()) return

        val newPos = if (_historyPosition.value == -1) {
            hist.size - 1
        } else {
            (_historyPosition.value - 1).coerceAtLeast(0)
        }
        _historyPosition.value = newPos
        _commandInput.value = hist[newPos]
    }

    fun historyNext() {
        val activeId = activeSessionId.value
        val session = sessions.value.firstOrNull { it.id == activeId } ?: return
        val hist = session.history
        if (hist.isEmpty()) return

        if (_historyPosition.value < hist.size - 1 && _historyPosition.value != -1) {
            val newPos = _historyPosition.value + 1
            _historyPosition.value = newPos
            _commandInput.value = hist[newPos]
        } else {
            _historyPosition.value = -1
            _commandInput.value = ""
        }
    }

    fun setTerminalFontSize(size: Int) {
        _terminalFontSize.value = size
    }

    fun setTerminalTheme(theme: TerminalTheme) {
        _terminalTheme.value = theme
    }

    fun setTerminalSearchQuery(query: String) {
        _terminalSearchQuery.value = query
    }

    fun toggleSearch() {
        _isSearchVisible.value = !_isSearchVisible.value
        if (!_isSearchVisible.value) {
            _terminalSearchQuery.value = ""
        }
    }

    fun launchTool(tool: SecurityTool, customArgs: String) {
        val fullCmd = "${tool.binary} $customArgs".trim()
        _currentTab.value = ScreenTab.TERMINAL
        executeCommand(fullCmd)
    }

    private fun formatPathForPrompt(dir: File): String {
        val homePath = linuxEnv.homeDir.absolutePath
        val curPath = dir.absolutePath
        return when {
            curPath == homePath -> "~"
            curPath.startsWith(homePath) -> "~" + curPath.removePrefix(homePath)
            else -> curPath
        }
    }

    // File Manager Operations
    fun navigateBrowser(targetDir: File) {
        if (targetDir.exists() && targetDir.isDirectory) {
            _currentBrowserDir.value = targetDir
            refreshBrowserFiles()
        }
    }

    fun refreshBrowserFiles() {
        val dir = _currentBrowserDir.value
        val files = dir.listFiles()?.map { file ->
            val perms = buildString {
                append(if (file.isDirectory) "d" else "-")
                append(if (file.canRead()) "r" else "-")
                append(if (file.canWrite()) "w" else "-")
                append(if (file.canExecute()) "x" else "-")
            }
            LinuxFileItem(
                name = file.name,
                absolutePath = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = file.length(),
                permissions = perms,
                lastModified = file.lastModified()
            )
        }?.sortedWith(compareBy<LinuxFileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList()

        _browserFiles.value = files
    }

    fun openFileInViewer(file: File) {
        viewModelScope.launch {
            try {
                val content = if (file.length() > 500_000) {
                    file.readText().take(500_000) + "\n[... Content truncated due to file size ...]"
                } else {
                    file.readText()
                }
                _selectedFileContent.value = Pair(file.name, content)
            } catch (e: Exception) {
                _selectedFileContent.value = Pair(file.name, "Unable to read file: ${e.localizedMessage}")
            }
        }
    }

    fun closeFileViewer() {
        _selectedFileContent.value = null
    }

    fun saveFileContent(fileName: String, content: String) {
        val file = File(_currentBrowserDir.value, fileName)
        file.writeText(content)
        refreshBrowserFiles()
        closeFileViewer()
        repository.logDiagnostic("Saved file: ${file.name}")
    }

    fun createNewFile(name: String, isFolder: Boolean) {
        val target = File(_currentBrowserDir.value, name)
        if (isFolder) {
            target.mkdirs()
        } else {
            target.createNewFile()
        }
        refreshBrowserFiles()
        repository.logDiagnostic("Created ${if (isFolder) "folder" else "file"}: $name")
    }

    fun deleteFile(file: File) {
        file.deleteRecursively()
        refreshBrowserFiles()
        repository.logDiagnostic("Deleted: ${file.name}")
    }

    fun openInTerminal(dir: File) {
        val activeId = activeSessionId.value
        repository.updateSessionCwd(activeId, dir)
        _currentTab.value = ScreenTab.TERMINAL
    }

    // System Environment Actions
    fun resetLinuxEnvironment(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = linuxEnv.resetEnvironment()
            if (ok) {
                repository.initInitialSession()
                refreshBrowserFiles()
                repository.logDiagnostic("Linux environment reset completed.")
            }
            onComplete(ok)
        }
    }

    fun cleanupTemp() {
        val freed = linuxEnv.cleanTempFiles()
        repository.refreshMetrics()
        repository.logDiagnostic("Cleaned temporary cache: freed ${freed / 1024} KB")
    }
}
