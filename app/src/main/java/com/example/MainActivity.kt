package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ScreenTab
import com.example.ui.SecStationViewModel
import com.example.ui.components.ScopeNoticeBanner
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SecStationApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecStationApp(viewModel: SecStationViewModel = viewModel()) {
    val isBootstrapped by viewModel.isBootstrapped.collectAsState()
    val isBootstrapping by viewModel.isBootstrapping.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val metrics by viewModel.repository.systemMetrics.collectAsState()

    var showScopeDialog by remember { mutableStateOf(false) }

    if (!isBootstrapped || isBootstrapping) {
        SetupScreen(
            viewModel = viewModel,
            onSetupFinished = {
                // Done bootstrapping, move to terminal
                viewModel.selectTab(ScreenTab.TERMINAL)
            }
        )
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(KaliDarkBg)
                .statusBarsPadding()
                .navigationBarsPadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "SecStation Icon",
                                tint = KaliPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "SecStation",
                                        color = KaliText,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = KaliSurfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "ROOTLESS",
                                            color = KaliPrimary,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${metrics.architecture} • ${currentTab.title}",
                                    color = KaliTextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showScopeDialog = true },
                            modifier = Modifier.testTag("scope_notice_icon_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GppMaybe,
                                contentDescription = "Scope Policy",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = KaliSurfaceDark
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = KaliSurfaceDark,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(0.5.dp, KaliBorder, RoundedCornerShape(0.dp)).testTag("bottom_nav_bar")
                ) {
                    val tabs = listOf(
                        Triple(ScreenTab.TERMINAL, Icons.Default.Terminal, "Terminal"),
                        Triple(ScreenTab.TOOLS, Icons.Default.Build, "Tools"),
                        Triple(ScreenTab.PACKAGES, Icons.Default.Inventory2, "Packages"),
                        Triple(ScreenTab.BUG_BOUNTY, Icons.Default.Shield, "Bounty"),
                        Triple(ScreenTab.CTF_LABS, Icons.Default.Flag, "CTF"),
                        Triple(ScreenTab.FILES, Icons.Default.Folder, "Files"),
                        Triple(ScreenTab.SYSTEM, Icons.Default.Settings, "System")
                    )

                    tabs.forEach { (tab, icon, label) ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00354E),
                                selectedTextColor = KaliPrimary,
                                unselectedIconColor = KaliTextMuted,
                                unselectedTextColor = KaliTextMuted,
                                indicatorColor = KaliPrimary
                            ),
                            modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(KaliDarkBg)
            ) {
                when (currentTab) {
                    ScreenTab.TERMINAL -> TerminalScreen(viewModel = viewModel)
                    ScreenTab.TOOLS -> ToolsScreen(viewModel = viewModel)
                    ScreenTab.PACKAGES -> PackagesScreen(viewModel = viewModel)
                    ScreenTab.BUG_BOUNTY -> BugBountyScreen(viewModel = viewModel)
                    ScreenTab.CTF_LABS -> CtfScreen(viewModel = viewModel)
                    ScreenTab.FILES -> FileManagerScreen(viewModel = viewModel)
                    ScreenTab.SYSTEM -> SystemScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Scope Policy Modal
    if (showScopeDialog) {
        AlertDialog(
            onDismissRequest = { showScopeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GppMaybe,
                        contentDescription = "Scope Rule",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scope & Authorization Rules", color = KaliText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. Explicit Authorization Required: Never execute security scans, fuzzing, or penetration testing against systems, IP addresses, or networks without documented permission from the owner.",
                        color = KaliText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "2. Rootless Sandbox Safety: SecStation operates strictly within Android unprivileged sandbox boundaries. Network tools use TCP connect mode without raw packet injection (CAP_NET_RAW).",
                        color = KaliTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "3. Safe Testing Targets: You can safely test 'scanme.nmap.org', 'httpbin.org', 'example.com', local loopback (127.0.0.1), and authorized lab IPs.",
                        color = KaliSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showScopeDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = KaliPrimary)
                ) {
                    Text("Understood", color = Color(0xFF00354E), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = KaliSurfaceDark
        )
    }
}
