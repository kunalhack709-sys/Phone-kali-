package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TerminalLine
import com.example.model.TerminalLineType
import com.example.model.TerminalTheme
import com.example.ui.SecStationViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(viewModel: SecStationViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val commandInput by viewModel.commandInput.collectAsState()
    val fontSize by viewModel.terminalFontSize.collectAsState()
    val terminalTheme by viewModel.terminalTheme.collectAsState()
    val searchQuery by viewModel.terminalSearchQuery.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val suggestions by viewModel.autoCompleteSuggestions.collectAsState()

    val activeSession = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.firstOrNull()
    val listState = rememberLazyListState()

    var showSettingsMenu by remember { mutableStateOf(false) }

    // Colors according to theme
    val (termBg, termFg, termAccent) = when (terminalTheme) {
        TerminalTheme.KALI -> Triple(ThemeTerminalKaliBg, ThemeTerminalKaliFg, KaliPrimary)
        TerminalTheme.MATRIX -> Triple(ThemeTerminalMatrixBg, ThemeTerminalMatrixFg, Color(0xFF22C55E))
        TerminalTheme.MONOKAI -> Triple(ThemeTerminalMonokaiBg, ThemeTerminalMonokaiFg, Color(0xFFF38BA8))
        TerminalTheme.CYBERPUNK -> Triple(ThemeTerminalCyberBg, ThemeTerminalCyberFg, Color(0xFF00F0FF))
    }

    // Scroll to bottom when lines change
    LaunchedEffect(activeSession?.lines?.size) {
        activeSession?.lines?.size?.let { size ->
            if (size > 0) {
                listState.animateScrollToItem(size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(termBg)
            .testTag("terminal_screen")
    ) {
        // Top Session Bar
        Surface(
            color = KaliSurfaceDark,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sessions Tabs Row
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(sessions) { s ->
                        val isSelected = s.id == activeSessionId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) KaliSurfaceVariant else Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) termAccent else KaliBorder.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .clickable { viewModel.repository.setActiveSession(s.id) }
                                .testTag("session_tab_${s.title.lowercase().replace(" ", "_")}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                if (s.isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                        color = termAccent
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = s.title,
                                    fontSize = 12.sp,
                                    color = if (isSelected) KaliText else KaliTextMuted,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (sessions.size > 1) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Session",
                                        tint = KaliTextMuted,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.repository.closeSession(s.id) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        IconButton(
                            onClick = { viewModel.repository.createNewSession() },
                            modifier = Modifier.size(28.dp).testTag("add_session_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Session",
                                tint = termAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Search Toggle
                IconButton(
                    onClick = { viewModel.toggleSearch() },
                    modifier = Modifier.size(32.dp).testTag("search_terminal_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Terminal",
                        tint = if (isSearchVisible) termAccent else KaliTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Copy Output
                IconButton(
                    onClick = {
                        val fullLog = activeSession?.lines?.joinToString("\n") { it.text } ?: ""
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Log", fullLog))
                        Toast.makeText(context, "Session copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp).testTag("copy_session_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Session",
                        tint = KaliTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Settings & Appearance Menu
                Box {
                    IconButton(
                        onClick = { showSettingsMenu = true },
                        modifier = Modifier.size(32.dp).testTag("terminal_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Terminal Settings",
                            tint = KaliTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false },
                        modifier = Modifier.background(KaliSurfaceDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Font Size: Small (12sp)", color = KaliText) },
                            onClick = { viewModel.setTerminalFontSize(12); showSettingsMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Font Size: Normal (14sp)", color = KaliText) },
                            onClick = { viewModel.setTerminalFontSize(14); showSettingsMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Font Size: Large (16sp)", color = KaliText) },
                            onClick = { viewModel.setTerminalFontSize(16); showSettingsMenu = false }
                        )
                        HorizontalDivider(color = KaliBorder)
                        TerminalTheme.entries.forEach { theme ->
                            DropdownMenuItem(
                                text = { Text("Theme: ${theme.displayName}", color = KaliText) },
                                onClick = { viewModel.setTerminalTheme(theme); showSettingsMenu = false }
                            )
                        }
                        HorizontalDivider(color = KaliBorder)
                        DropdownMenuItem(
                            text = { Text("Clear Session Buffer", color = KaliRed) },
                            onClick = {
                                activeSessionId.let { viewModel.repository.clearSession(it) }
                                showSettingsMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Search Bar (if visible)
        if (isSearchVisible) {
            Surface(
                color = KaliSurfaceDark,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setTerminalSearchQuery(it) },
                    placeholder = { Text("Filter terminal output...", color = KaliTextMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.FilterList, "Filter", tint = termAccent, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setTerminalSearchQuery("") }) {
                                Icon(Icons.Default.Clear, "Clear", tint = KaliTextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = KaliText,
                        unfocusedTextColor = KaliText,
                        focusedBorderColor = termAccent,
                        unfocusedBorderColor = KaliBorder
                    ),
                    singleLine = true
                )
            }
        }

        // Terminal Output Screen
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val linesToDisplay = remember(activeSession?.lines, searchQuery) {
                val allLines = activeSession?.lines ?: emptyList()
                if (searchQuery.isBlank()) allLines
                else allLines.filter { it.text.contains(searchQuery, ignoreCase = true) }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(linesToDisplay, key = { it.id }) { line ->
                    TerminalLineRow(line = line, fontSize = fontSize, theme = terminalTheme, termAccent = termAccent)
                }
            }
        }

        // Auto-complete Suggestions Row
        if (suggestions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KaliSurfaceDark)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(suggestions) { sugg ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = KaliSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, termAccent.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clickable { viewModel.applyTabCompletion(sugg) }
                            .testTag("tab_suggestion_$sugg")
                    ) {
                        Text(
                            text = sugg,
                            color = termAccent,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Control Keys Bar (Ctrl+C, Ctrl+D, Tab, Arrows, Pipe, Slash)
        Surface(
            color = KaliSurfaceDark,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ControlKeyButton(label = "Ctrl+C", color = KaliRed) { viewModel.sendCtrlC() }
                ControlKeyButton(label = "Ctrl+D") { viewModel.sendCtrlD() }
                ControlKeyButton(label = "Tab", color = termAccent) {
                    if (suggestions.isNotEmpty()) {
                        viewModel.applyTabCompletion(suggestions.first())
                    }
                }
                ControlKeyButton(label = "↑") { viewModel.historyPrevious() }
                ControlKeyButton(label = "↓") { viewModel.historyNext() }
                ControlKeyButton(label = "Clear") {
                    activeSessionId.let { viewModel.repository.clearSession(it) }
                }
                ControlKeyButton(label = "~") { viewModel.updateCommandInput(commandInput + "~") }
                ControlKeyButton(label = "/") { viewModel.updateCommandInput(commandInput + "/") }
                ControlKeyButton(label = "-") { viewModel.updateCommandInput(commandInput + "-") }
                ControlKeyButton(label = "|") { viewModel.updateCommandInput(commandInput + " | ") }
                ControlKeyButton(label = ">") { viewModel.updateCommandInput(commandInput + " > ") }
                ControlKeyButton(label = "&&") { viewModel.updateCommandInput(commandInput + " && ") }
            }
        }

        // Prompt and Command Input
        Surface(
            color = KaliSurfaceDark,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$",
                    color = KaliPromptColor,
                    fontSize = (fontSize + 2).sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                )

                TextField(
                    value = commandInput,
                    onValueChange = { viewModel.updateCommandInput(it) },
                    placeholder = {
                        Text(
                            "enter command (e.g., nmap, whois, pkg)...",
                            color = KaliTextMuted,
                            fontSize = fontSize.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input_field"),
                    textStyle = LocalTextStyle.current.copy(
                        color = KaliText,
                        fontSize = fontSize.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = termAccent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.executeCommand() }),
                    singleLine = true
                )

                IconButton(
                    onClick = { viewModel.executeCommand() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("execute_command_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Execute",
                        tint = termAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalLineRow(
    line: TerminalLine,
    fontSize: Int,
    theme: TerminalTheme,
    termAccent: Color
) {
    val lineText = line.text

    val textColor = when (line.type) {
        TerminalLineType.PROMPT -> KaliPromptColor
        TerminalLineType.STDIN -> KaliText
        TerminalLineType.STDOUT -> when (theme) {
            TerminalTheme.MATRIX -> Color(0xFF86EFAC)
            TerminalTheme.MONOKAI -> Color(0xFFCDD6F4)
            TerminalTheme.CYBERPUNK -> Color(0xFF00F0FF)
            TerminalTheme.KALI -> Color(0xFFE2E8F0)
        }
        TerminalLineType.STDERR -> KaliRed
        TerminalLineType.INFO -> termAccent
        TerminalLineType.WARNING -> KaliYellow
        TerminalLineType.SUCCESS -> KaliSecondary
    }

    Text(
        text = lineText,
        color = textColor,
        fontSize = fontSize.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (fontSize + 5).sp
    )
}

@Composable
private fun ControlKeyButton(
    label: String,
    color: Color = KaliText,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = KaliSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder),
        modifier = Modifier
            .clickable { onClick() }
            .testTag("key_${label.lowercase().replace("+", "_")}")
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
