package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
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
    val focusRequester = remember { FocusRequester() }

    var showSettingsMenu by remember { mutableStateOf(false) }
    var showKeyboardToolbar by remember { mutableStateOf(true) }

    // Latched modifier keys for on-screen terminal control (like Termux / Kali NetHunter)
    var isCtrlLatched by remember { mutableStateOf(false) }
    var isAltLatched by remember { mutableStateOf(false) }

    // Internal TextFieldValue to support accurate cursor movement via Arrow keys / Home / End
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = commandInput, selection = TextRange(commandInput.length)))
    }

    // Keep textFieldValue text synchronized when commandInput updates from outside
    LaunchedEffect(commandInput) {
        if (textFieldValue.text != commandInput) {
            textFieldValue = TextFieldValue(
                text = commandInput,
                selection = TextRange(commandInput.length)
            )
        }
    }

    // Colors according to theme
    val (termBg, termFg, termAccent) = when (terminalTheme) {
        TerminalTheme.KALI -> Triple(ThemeTerminalKaliBg, ThemeTerminalKaliFg, Color(0xFF00C0FF))
        TerminalTheme.MATRIX -> Triple(ThemeTerminalMatrixBg, ThemeTerminalMatrixFg, Color(0xFF22C55E))
        TerminalTheme.MONOKAI -> Triple(ThemeTerminalMonokaiBg, ThemeTerminalMonokaiFg, Color(0xFFF38BA8))
        TerminalTheme.CYBERPUNK -> Triple(ThemeTerminalCyberBg, ThemeTerminalCyberFg, Color(0xFF00F0FF))
    }

    // Auto-scroll to bottom when new terminal lines appear
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
        // === 1. Kali Linux Window Header (Desktop / QTerminal Aesthetic) ===
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
                // Window Traffic Light Controls (Red: close tab, Yellow: clear, Green: new tab)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5F56))
                            .clickable {
                                if (sessions.size > 1) {
                                    activeSessionId.let { viewModel.repository.closeSession(it) }
                                } else {
                                    viewModel.clearActiveSession()
                                }
                            }
                            .testTag("window_btn_close")
                    )
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFBD2E))
                            .clickable { viewModel.clearActiveSession() }
                            .testTag("window_btn_minimize")
                    )
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF27C93F))
                            .clickable { viewModel.repository.createNewSession() }
                            .testTag("window_btn_maximize")
                    )
                }

                // Window Title (kali@secstation: ~)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Kali Shell",
                        tint = termAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "kali@secstation: ${activeSession?.let { viewModel.formatPathForPrompt(it.cwd) } ?: "~"}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = KaliText,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Shell badge
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = if (activeSession?.isRunning == true) Color(0xFFD97706) else Color(0xFF16A34A).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (activeSession?.isRunning == true) Color(0xFFF59E0B) else Color(0xFF22C55E)
                        )
                    ) {
                        Text(
                            text = if (activeSession?.isRunning == true) "BUSY" else "BASH",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (activeSession?.isRunning == true) Color(0xFFFFFBEB) else Color(0xFF4ADE80),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Header Utility Actions
                // Toggle On-screen Keyboard Extra Keys
                IconButton(
                    onClick = { showKeyboardToolbar = !showKeyboardToolbar },
                    modifier = Modifier.size(30.dp).testTag("toggle_keyboard_toolbar_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Terminal Keyboard Controls",
                        tint = if (showKeyboardToolbar) termAccent else KaliTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Search Toggle
                IconButton(
                    onClick = { viewModel.toggleSearch() },
                    modifier = Modifier.size(30.dp).testTag("search_terminal_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Terminal",
                        tint = if (isSearchVisible) termAccent else KaliTextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Copy Session Buffer
                IconButton(
                    onClick = {
                        val fullLog = activeSession?.lines?.joinToString("\n") { it.text } ?: ""
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Log", fullLog))
                        Toast.makeText(context, "Session log copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp).testTag("copy_session_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Session",
                        tint = KaliTextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Terminal Appearance & Options Menu
                Box {
                    IconButton(
                        onClick = { showSettingsMenu = true },
                        modifier = Modifier.size(30.dp).testTag("terminal_settings_btn")
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
                                viewModel.clearActiveSession()
                                showSettingsMenu = false
                            }
                        )
                    }
                }
            }
        }

        // === 2. Session Tabs Bar ===
        Surface(
            color = KaliSurfaceVariant.copy(alpha = 0.6f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(sessions) { s ->
                        val isSelected = s.id == activeSessionId
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) KaliSurfaceDark else Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) termAccent else KaliBorder.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .clickable { viewModel.repository.setActiveSession(s.id) }
                                .testTag("session_tab_${s.title.lowercase().replace(" ", "_")}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                if (s.isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(9.dp),
                                        strokeWidth = 1.5.dp,
                                        color = termAccent
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                Text(
                                    text = s.title,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) KaliText else KaliTextMuted,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (sessions.size > 1) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Session",
                                        tint = KaliTextMuted,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clickable { viewModel.repository.closeSession(s.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Add New Terminal Tab
                IconButton(
                    onClick = { viewModel.repository.createNewSession() },
                    modifier = Modifier.size(26.dp).testTag("add_session_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Session",
                        tint = termAccent,
                        modifier = Modifier.size(16.dp)
                    )
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

        // === 3. Terminal Output Screen ===
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable { focusRequester.requestFocus() }
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

        // === 4. Auto-complete Suggestions Row ===
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

        // === 5. Termux / NetHunter Style Hardware & Auxiliary Keyboard Bar ===
        AnimatedVisibility(visible = showKeyboardToolbar) {
            Surface(
                color = KaliSurfaceDark,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder)
            ) {
                Column {
                    // Row 1: Modifier & Signal Hardware Keys (Ctrl, Alt, Esc, Tab, Arrows, Interrupts)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Latching CTRL Key
                        ControlKeyButton(
                            label = "CTRL",
                            isActive = isCtrlLatched,
                            activeColor = termAccent,
                            color = if (isCtrlLatched) termAccent else KaliText
                        ) {
                            isCtrlLatched = !isCtrlLatched
                        }

                        // Latching ALT Key
                        ControlKeyButton(
                            label = "ALT",
                            isActive = isAltLatched,
                            activeColor = KaliYellow,
                            color = if (isAltLatched) KaliYellow else KaliText
                        ) {
                            isAltLatched = !isAltLatched
                        }

                        // ESC Key
                        ControlKeyButton(label = "ESC") {
                            viewModel.updateCommandInput("")
                            isCtrlLatched = false
                            isAltLatched = false
                        }

                        // TAB Key
                        ControlKeyButton(label = "TAB", color = termAccent) {
                            if (suggestions.isNotEmpty()) {
                                viewModel.applyTabCompletion(suggestions.first())
                            } else {
                                val curText = textFieldValue.text
                                val newPos = textFieldValue.selection.start
                                val newText = curText.substring(0, newPos) + "  " + curText.substring(newPos)
                                viewModel.updateCommandInput(newText)
                                textFieldValue = TextFieldValue(newText, TextRange(newPos + 2))
                            }
                            isCtrlLatched = false
                        }

                        // Arrow Up (History Prev)
                        ControlKeyButton(label = "↑", color = KaliSecondary) {
                            viewModel.historyPrevious()
                        }

                        // Arrow Down (History Next)
                        ControlKeyButton(label = "↓", color = KaliSecondary) {
                            viewModel.historyNext()
                        }

                        // Arrow Left (Cursor Left)
                        ControlKeyButton(label = "←") {
                            val newPos = (textFieldValue.selection.start - 1).coerceAtLeast(0)
                            textFieldValue = textFieldValue.copy(selection = TextRange(newPos))
                        }

                        // Arrow Right (Cursor Right)
                        ControlKeyButton(label = "→") {
                            val newPos = (textFieldValue.selection.end + 1).coerceAtMost(textFieldValue.text.length)
                            textFieldValue = textFieldValue.copy(selection = TextRange(newPos))
                        }

                        // HOME Key
                        ControlKeyButton(label = "HOME") {
                            textFieldValue = textFieldValue.copy(selection = TextRange(0))
                        }

                        // END Key
                        ControlKeyButton(label = "END") {
                            textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
                        }

                        // Ctrl+C (Interrupt)
                        ControlKeyButton(label = "^C", color = KaliRed) {
                            viewModel.sendCtrlC()
                            isCtrlLatched = false
                        }

                        // Ctrl+L (Clear Screen)
                        ControlKeyButton(label = "^L", color = KaliYellow) {
                            viewModel.clearActiveSession()
                            isCtrlLatched = false
                        }

                        // Ctrl+D (EOF / Exit)
                        ControlKeyButton(label = "^D", color = KaliTextMuted) {
                            viewModel.sendCtrlD()
                            isCtrlLatched = false
                        }

                        // Reset
                        ControlKeyButton(label = "RESET", color = KaliTextMuted) {
                            viewModel.clearActiveSession()
                            viewModel.executeCommand("motd")
                        }
                    }

                    // Row 2: Shell Operators & Syntax Characters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KaliSurfaceVariant.copy(alpha = 0.4f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val symbolKeys = listOf("|", "/", "-", "_", "~", ">", "<", "&&", ";", "\"", "'", "*", "$", "=", ":", "\\", "(", ")")
                        symbolKeys.forEach { sym ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = KaliSurfaceDark,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder),
                                modifier = Modifier
                                    .clickable {
                                        val selStart = textFieldValue.selection.start
                                        val selEnd = textFieldValue.selection.end
                                        val cur = textFieldValue.text
                                        val newText = cur.substring(0, selStart) + sym + cur.substring(selEnd)
                                        viewModel.updateCommandInput(newText)
                                        textFieldValue = TextFieldValue(newText, TextRange(selStart + sym.length))
                                    }
                                    .testTag("sym_$sym")
                            ) {
                                Text(
                                    text = sym,
                                    color = Color(0xFF93C5FD),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Row 3: Kali Quick Security Tools
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KaliSurfaceVariant.copy(alpha = 0.2f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tools:", fontSize = 10.sp, color = KaliTextMuted, fontWeight = FontWeight.Bold)
                        QuickCmdChip("subfinder", termAccent) { viewModel.updateCommandInput("subfinder -d ") }
                        QuickCmdChip("httpx", termAccent) { viewModel.updateCommandInput("httpx -u ") }
                        QuickCmdChip("dnsx", termAccent) { viewModel.updateCommandInput("dnsx -d ") }
                        QuickCmdChip("naabu", termAccent) { viewModel.updateCommandInput("naabu -host ") }
                        QuickCmdChip("amass", termAccent) { viewModel.updateCommandInput("amass enum -passive -d ") }
                        QuickCmdChip("nuclei", termAccent) { viewModel.updateCommandInput("nuclei -u ") }
                        QuickCmdChip("ffuf", termAccent) { viewModel.updateCommandInput("ffuf -u ") }
                        QuickCmdChip("nmap", termAccent) { viewModel.updateCommandInput("nmap -sT ") }
                        QuickCmdChip("whois", termAccent) { viewModel.updateCommandInput("whois ") }
                        QuickCmdChip("dig", termAccent) { viewModel.updateCommandInput("dig ") }
                        QuickCmdChip("ping", termAccent) { viewModel.updateCommandInput("ping ") }
                        QuickCmdChip("curl", termAccent) { viewModel.updateCommandInput("curl ") }
                        QuickCmdChip("ls -la", KaliSecondary) { viewModel.executeCommand("ls -la") }
                        QuickCmdChip("pwd", KaliSecondary) { viewModel.executeCommand("pwd") }
                        QuickCmdChip("help", KaliSecondary) { viewModel.executeCommand("help") }
                    }
                }
            }
        }

        // === 6. Authentic Kali Linux Two-Tier Prompt & Command Input ===
        Surface(
            color = KaliSurfaceDark,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                // Tier 1: ┌──(kali㉿secstation)-[~/workspace]
                val pathStr = activeSession?.let { viewModel.formatPathForPrompt(it.cwd) } ?: "~"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = "┌──(",
                        color = Color(0xFF00C0FF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "kali",
                        color = Color(0xFF00C0FF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "㉿",
                        color = Color(0xFF38EF7D),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "secstation",
                        color = Color(0xFF00C0FF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ")-[",
                        color = Color(0xFF00C0FF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pathStr,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "]",
                        color = Color(0xFF00C0FF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tier 2: └─$ [Input Field with Hardware Keyboard Listener]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "└─$ ",
                        color = Color(0xFF00C0FF),
                        fontSize = (fontSize + 1).sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                    )

                    TextField(
                        value = textFieldValue,
                        onValueChange = { newVal ->
                            // Check if CTRL was latched via soft toolbar
                            if (isCtrlLatched) {
                                val typedChar = newVal.text.lastOrNull()?.lowercaseChar()
                                isCtrlLatched = false
                                when (typedChar) {
                                    'c' -> {
                                        viewModel.sendCtrlC()
                                        return@TextField
                                    }
                                    'l' -> {
                                        viewModel.clearActiveSession()
                                        return@TextField
                                    }
                                    'd' -> {
                                        viewModel.sendCtrlD()
                                        return@TextField
                                    }
                                    'u' -> {
                                        viewModel.updateCommandInput("")
                                        textFieldValue = TextFieldValue("")
                                        return@TextField
                                    }
                                    'a' -> {
                                        textFieldValue = textFieldValue.copy(selection = TextRange(0))
                                        return@TextField
                                    }
                                    'e' -> {
                                        textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
                                        return@TextField
                                    }
                                }
                            }

                            textFieldValue = newVal
                            viewModel.updateCommandInput(newVal.text)
                        },
                        placeholder = {
                            Text(
                                "enter command or press TAB / arrows...",
                                color = KaliTextMuted,
                                fontSize = fontSize.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                // Physical or Bluetooth Keyboard Handler
                                if (event.type == KeyEventType.KeyDown) {
                                    when {
                                        // Enter: Execute Command
                                        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                                            viewModel.executeCommand()
                                            true
                                        }
                                        // Ctrl+C: Cancel / Stop
                                        event.isCtrlPressed && event.key == Key.C -> {
                                            viewModel.sendCtrlC()
                                            true
                                        }
                                        // Ctrl+L: Clear screen
                                        event.isCtrlPressed && event.key == Key.L -> {
                                            viewModel.clearActiveSession()
                                            true
                                        }
                                        // Ctrl+D: Exit
                                        event.isCtrlPressed && event.key == Key.D -> {
                                            viewModel.sendCtrlD()
                                            true
                                        }
                                        // Ctrl+U: Clear line
                                        event.isCtrlPressed && event.key == Key.U -> {
                                            viewModel.updateCommandInput("")
                                            textFieldValue = TextFieldValue("")
                                            true
                                        }
                                        // Ctrl+A / Home: Cursor to start
                                        (event.isCtrlPressed && event.key == Key.A) || event.key == Key.MoveHome -> {
                                            textFieldValue = textFieldValue.copy(selection = TextRange(0))
                                            true
                                        }
                                        // Ctrl+E / End: Cursor to end
                                        (event.isCtrlPressed && event.key == Key.E) || event.key == Key.MoveEnd -> {
                                            textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
                                            true
                                        }
                                        // Arrow Up: History Previous
                                        event.key == Key.DirectionUp -> {
                                            viewModel.historyPrevious()
                                            true
                                        }
                                        // Arrow Down: History Next
                                        event.key == Key.DirectionDown -> {
                                            viewModel.historyNext()
                                            true
                                        }
                                        // Tab: Apply completion
                                        event.key == Key.Tab -> {
                                            if (suggestions.isNotEmpty()) {
                                                viewModel.applyTabCompletion(suggestions.first())
                                            }
                                            true
                                        }
                                        // Escape: Clear
                                        event.key == Key.Escape -> {
                                            viewModel.updateCommandInput("")
                                            textFieldValue = TextFieldValue("")
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
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

                    // Execute or Stop Process Button
                    if (activeSession?.isRunning == true) {
                        IconButton(
                            onClick = { viewModel.sendCtrlC() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("terminal_stop_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Stop Process (^C)",
                                tint = KaliRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.executeCommand() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("execute_command_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Execute Command",
                                tint = termAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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

    if (line.type == TerminalLineType.PROMPT && lineText.contains("┌──(kali㉿secstation)")) {
        // Authentic colored Kali Linux prompt in history
        val annotated = buildAnnotatedString {
            val parts = lineText.split("\n", limit = 2)
            val header = parts.firstOrNull() ?: ""
            val rest = if (parts.size > 1) parts[1] else ""

            // Header line: ┌──(kali㉿secstation)-[path]
            withStyle(SpanStyle(color = Color(0xFF00C0FF), fontWeight = FontWeight.Bold)) {
                append("┌──(")
                append("kali")
            }
            withStyle(SpanStyle(color = Color(0xFF38EF7D), fontWeight = FontWeight.Bold)) {
                append("㉿")
            }
            withStyle(SpanStyle(color = Color(0xFF00C0FF), fontWeight = FontWeight.Bold)) {
                append("secstation)-[")
            }
            val pathContent = header.substringAfter(")-[", "").substringBefore("]")
            withStyle(SpanStyle(color = Color(0xFFE2E8F0))) {
                append(pathContent)
            }
            withStyle(SpanStyle(color = Color(0xFF00C0FF), fontWeight = FontWeight.Bold)) {
                append("]")
            }

            if (rest.isNotEmpty()) {
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF00C0FF), fontWeight = FontWeight.Bold)) {
                    append("└─$ ")
                }
                val cmdExecuted = rest.removePrefix("└─$ ")
                withStyle(SpanStyle(color = KaliText, fontWeight = FontWeight.SemiBold)) {
                    append(cmdExecuted)
                }
            }
        }

        Text(
            text = annotated,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = (fontSize + 5).sp
        )
        return
    }

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
    isActive: Boolean = false,
    activeColor: Color = Color(0xFF00C0FF),
    color: Color = KaliText,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = if (isActive) activeColor.copy(alpha = 0.25f) else KaliSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) activeColor else KaliBorder.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .clickable { onClick() }
            .testTag("key_${label.lowercase().replace("+", "_").replace("^", "ctrl_")}")
    ) {
        Text(
            text = label,
            color = if (isActive) activeColor else color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun QuickCmdChip(
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = KaliSurfaceDark,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, accentColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .clickable { onClick() }
            .testTag("quick_cmd_${label.lowercase().replace(" ", "_").replace("-", "_")}")
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
