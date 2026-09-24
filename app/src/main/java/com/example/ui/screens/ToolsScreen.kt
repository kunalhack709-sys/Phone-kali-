package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ApkAnalyzer
import com.example.core.ApkSecurityReport
import com.example.model.CompatibilityStatus
import com.example.model.SecurityTool
import com.example.model.ToolCategory
import com.example.ui.SecStationViewModel
import com.example.ui.components.CompatibilityBadge
import com.example.ui.components.ScopeNoticeBanner
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ToolsScreen(viewModel: SecStationViewModel) {
    val tools by viewModel.repository.tools.collectAsState()
    val scopeAcknowledged by viewModel.scopeAcknowledged.collectAsState()

    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTools = remember(tools, selectedCategory, searchQuery) {
        tools.filter { tool ->
            (selectedCategory == null || tool.category == selectedCategory) &&
            (searchQuery.isBlank() || tool.name.contains(searchQuery, ignoreCase = true) || tool.description.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaliDarkBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("tools_screen")
    ) {
        // Scope Notice
        if (!scopeAcknowledged) {
            ScopeNoticeBanner(
                onDismiss = { viewModel.acknowledgeScope() },
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search security tools...", color = KaliTextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, "Search", tint = KaliPrimary, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, "Clear", tint = KaliTextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("search_tools_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = KaliText,
                unfocusedTextColor = KaliText,
                focusedBorderColor = KaliPrimary,
                unfocusedBorderColor = KaliBorder
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Categories Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All Categories") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KaliPrimary,
                        selectedLabelColor = Color(0xFF00354E),
                        containerColor = KaliSurfaceDark,
                        labelColor = KaliText
                    )
                )
            }
            items(ToolCategory.entries) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KaliPrimary,
                        selectedLabelColor = Color(0xFF00354E),
                        containerColor = KaliSurfaceDark,
                        labelColor = KaliText
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tools List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(filteredTools, key = { it.id }) { tool ->
                ToolCardItem(
                    tool = tool,
                    onLaunchInTerminal = { customArgs ->
                        viewModel.launchTool(tool, customArgs)
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolCardItem(
    tool: SecurityTool,
    onLaunchInTerminal: (String) -> Unit
) {
    var customArgs by remember { mutableStateOf(tool.defaultArgs) }
    var isExpanded by remember { mutableStateOf(false) }

    // APK direct inspection state if it's apktool
    var apkReport by remember { mutableStateOf<ApkSecurityReport?>(null) }
    var isAnalyzingApk by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, KaliBorder, RoundedCornerShape(12.dp))
            .testTag("tool_card_${tool.binary}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tool.name,
                            color = KaliText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$ ${tool.binary}",
                            color = KaliPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = tool.category.displayName,
                        color = KaliTextMuted,
                        fontSize = 11.sp
                    )
                }

                CompatibilityBadge(status = tool.compatibility)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = tool.description,
                color = KaliText,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Limitations / Rootless Alternative Notice
            if (tool.compatibility != CompatibilityStatus.COMPATIBLE && tool.limitationReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = KaliSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (tool.compatibility == CompatibilityStatus.UNSUPPORTED) KaliRed.copy(alpha = 0.5f) else KaliYellow.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (tool.compatibility == CompatibilityStatus.UNSUPPORTED) Icons.Default.Block else Icons.Default.Info,
                                contentDescription = "Limitation",
                                tint = if (tool.compatibility == CompatibilityStatus.UNSUPPORTED) KaliRed else KaliYellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Android Sandbox Notice:",
                                color = if (tool.compatibility == CompatibilityStatus.UNSUPPORTED) KaliRed else KaliYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tool.limitationReason,
                            color = KaliTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        if (tool.rootlessAlternative != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Alternative: ${tool.rootlessAlternative}",
                                color = KaliSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Parameter Builder
            Text(
                text = "Command Parameters:",
                color = KaliTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = customArgs,
                onValueChange = { customArgs = it },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = KaliText
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KaliPrimary,
                    unfocusedBorderColor = KaliBorder,
                    focusedTextColor = KaliText,
                    unfocusedTextColor = KaliText
                ),
                singleLine = true
            )

            // Suggested Arg Chips
            if (tool.suggestedArgs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tool.suggestedArgs) { arg ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = KaliSurfaceVariant,
                            modifier = Modifier.clickable { customArgs = arg }
                        ) {
                            Text(
                                text = arg,
                                color = KaliPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (tool.binary) {
                    "nuclei" -> {
                        OutlinedButton(
                            onClick = { onLaunchInTerminal(customArgs) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliSecondary),
                            modifier = Modifier.testTag("nuclei_quick_scan_btn")
                        ) {
                            Icon(Icons.Default.Bolt, "Quick Scan", modifier = Modifier.size(14.dp), tint = KaliSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Audit Scan", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    "subfinder" -> {
                        OutlinedButton(
                            onClick = { onLaunchInTerminal(customArgs) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliSecondary),
                            modifier = Modifier.testTag("subfinder_quick_btn")
                        ) {
                            Icon(Icons.Default.TravelExplore, "Enum", modifier = Modifier.size(14.dp), tint = KaliSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Passive Enum", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    "httpx" -> {
                        OutlinedButton(
                            onClick = { onLaunchInTerminal(customArgs) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliSecondary),
                            modifier = Modifier.testTag("httpx_quick_btn")
                        ) {
                            Icon(Icons.Default.Http, "Probe", modifier = Modifier.size(14.dp), tint = KaliSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Probe HTTP", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    "naabu" -> {
                        OutlinedButton(
                            onClick = { onLaunchInTerminal(customArgs) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliSecondary),
                            modifier = Modifier.testTag("naabu_quick_btn")
                        ) {
                            Icon(Icons.Default.Router, "Port Scan", modifier = Modifier.size(14.dp), tint = KaliSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Port Scan", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    "dnsx" -> {
                        OutlinedButton(
                            onClick = { onLaunchInTerminal(customArgs) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliSecondary),
                            modifier = Modifier.testTag("dnsx_quick_btn")
                        ) {
                            Icon(Icons.Default.Dns, "Resolve", modifier = Modifier.size(14.dp), tint = KaliSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resolve DNS", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    "amass" -> {
                        OutlinedButton(
                            onClick = { onLaunchInTerminal(customArgs) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliSecondary),
                            modifier = Modifier.testTag("amass_quick_btn")
                        ) {
                            Icon(Icons.Default.Hub, "Map", modifier = Modifier.size(14.dp), tint = KaliSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Map Assets", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    "ffuf" -> {
                        OutlinedButton(
                            onClick = { onLaunchInTerminal(customArgs) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliSecondary),
                            modifier = Modifier.testTag("ffuf_quick_btn")
                        ) {
                            Icon(Icons.Default.FindInPage, "Fuzz", modifier = Modifier.size(14.dp), tint = KaliSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fuzz Endpoints", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    "proxy-setup" -> {
                        OutlinedButton(
                            onClick = { onLaunchInTerminal("--status") },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliSecondary),
                            modifier = Modifier.testTag("proxy_status_btn")
                        ) {
                            Icon(Icons.Default.SettingsEthernet, "Proxy", modifier = Modifier.size(14.dp), tint = KaliSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Proxy Status", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                if (tool.binary == "apktool") {
                    OutlinedButton(
                        onClick = {
                            isAnalyzingApk = true
                            coroutineScope.launch {
                                val target = File(customArgs.removePrefix("d ").trim())
                                apkReport = ApkAnalyzer.analyzeApk(target)
                                isAnalyzingApk = false
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliSecondary),
                        modifier = Modifier.testTag("inspect_apk_btn")
                    ) {
                        if (isAnalyzingApk) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = KaliSecondary, strokeWidth = 2.dp)
                        } else {
                            Text("Direct Scan", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Button(
                    onClick = { onLaunchInTerminal(customArgs) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tool.compatibility == CompatibilityStatus.UNSUPPORTED) KaliSurfaceVariant else KaliPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("run_tool_${tool.binary}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Run",
                        tint = if (tool.compatibility == CompatibilityStatus.UNSUPPORTED) KaliTextMuted else Color(0xFF00354E),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (tool.compatibility == CompatibilityStatus.UNSUPPORTED) "Run with Warnings" else "Run in Terminal",
                        color = if (tool.compatibility == CompatibilityStatus.UNSUPPORTED) KaliTextMuted else Color(0xFF00354E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // In-card APK Report results if scanned
            if (apkReport != null) {
                val r = apkReport!!
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = KaliSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "APK Analysis: ${r.fileName} (${r.fileSizeKb} KB)",
                            fontWeight = FontWeight.Bold,
                            color = KaliSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "DEX Files: ${r.dexFilesCount} | Signed: ${r.hasSignature} (${r.signatureType})",
                            color = KaliTextMuted,
                            fontSize = 11.sp
                        )
                        if (r.securityFindings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Security Warnings (${r.securityFindings.size}):", color = KaliYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            r.securityFindings.forEach { f ->
                                Text("• [${f.severity}] ${f.title}: ${f.description}", color = KaliText, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
