package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SystemResourceInfo
import com.example.ui.SecStationViewModel
import com.example.ui.components.MetricPill
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun SystemScreen(viewModel: SecStationViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val metrics by viewModel.repository.systemMetrics.collectAsState()
    val logs by viewModel.repository.diagnosticLogs.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }

    // SAF Create Document for Backup Export
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val zipFile = viewModel.linuxEnv.backupUserEnvironment()
                    if (zipFile != null && zipFile.exists()) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            zipFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        Toast.makeText(context, "Backup exported successfully (${zipFile.length() / 1024} KB)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Backup archive generation failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // SAF Open Document for Restore
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val tempBackup = File(context.cacheDir, "temp_restore.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempBackup).use { output -> input.copyTo(output) }
                    }
                    val ok = viewModel.linuxEnv.restoreUserEnvironment(tempBackup)
                    if (ok) {
                        viewModel.repository.initInitialSession()
                        viewModel.refreshBrowserFiles()
                        Toast.makeText(context, "Environment restored from backup!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to restore backup archive", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Restore error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaliDarkBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("system_screen")
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // System Hardware & OS Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, KaliBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device & Architecture",
                                color = KaliText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.repository.refreshMetrics() },
                                modifier = Modifier.size(28.dp).testTag("refresh_metrics_btn")
                            ) {
                                Icon(Icons.Default.Refresh, "Refresh", tint = KaliPrimary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricPill("ABI", metrics.architecture, Icons.Default.Memory, KaliPrimary)
                            MetricPill("OS", metrics.osVersion.substringBefore(" ("), Icons.Default.PhoneAndroid, KaliSecondary)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // RAM usage bar
                        val ramFraction = if (metrics.totalRamMb > 0) metrics.usedRamMb.toFloat() / metrics.totalRamMb else 0f
                        Text(
                            text = "RAM: ${metrics.usedRamMb} MB / ${metrics.totalRamMb} MB used",
                            color = KaliTextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ramFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (ramFraction > 0.85f) KaliRed else KaliPrimary,
                            trackColor = KaliSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Storage usage bar
                        val storageUsedMb = metrics.storageTotalMb - metrics.storageAvailMb
                        val storageFraction = if (metrics.storageTotalMb > 0) storageUsedMb.toFloat() / metrics.storageTotalMb else 0f
                        Text(
                            text = "Storage: ${storageUsedMb / 1024} GB / ${metrics.storageTotalMb / 1024} GB",
                            color = KaliTextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { storageFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = KaliSecondary,
                            trackColor = KaliSurfaceVariant
                        )
                    }
                }
            }

            // Environment Management Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, KaliBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Userspace Environment Management",
                            color = KaliText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Prefix: /data/data/${context.packageName}/files/usr",
                            color = KaliTextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { backupLauncher.launch("kali_env_backup_${System.currentTimeMillis()}.zip") },
                                colors = ButtonDefaults.buttonColors(containerColor = KaliSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("backup_env_btn")
                            ) {
                                Icon(Icons.Default.Archive, "Backup", tint = KaliPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Backup ZIP", color = KaliPrimary, fontSize = 11.sp)
                            }

                            Button(
                                onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                                colors = ButtonDefaults.buttonColors(containerColor = KaliSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("restore_env_btn")
                            ) {
                                Icon(Icons.Default.Unarchive, "Restore", tint = KaliSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore ZIP", color = KaliSecondary, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.cleanupTemp()
                                    Toast.makeText(context, "Temporary files cleaned", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = KaliSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("clean_temp_btn")
                            ) {
                                Icon(Icons.Default.CleaningServices, "Clean", tint = KaliText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clean Temp", color = KaliText, fontSize = 11.sp)
                            }

                            Button(
                                onClick = { showResetDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = KaliRed.copy(alpha = 0.2f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, KaliRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("reset_env_btn")
                            ) {
                                Icon(Icons.Default.Warning, "Reset", tint = KaliRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Linux", color = KaliRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Real-time Diagnostic Logs
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, KaliBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Diagnostic & System Logs",
                                color = KaliText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = {
                                    val allLogs = logs.joinToString("\n")
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostic Logs", allLogs))
                                    Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp).testTag("copy_logs_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, "Copy Logs", tint = KaliPrimary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = Color(0xFF070B14),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder),
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(logs) { logEntry ->
                                    Text(
                                        text = logEntry,
                                        color = KaliTextMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetDialog = false },
            title = { Text("Reset Linux Environment?", color = KaliRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will delete all installed packages, temporary files, and re-bootstrap the rootless userspace filesystem. Your custom bug bounty workspace files will be re-initialized.",
                    color = KaliTextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isResetting = true
                        viewModel.resetLinuxEnvironment { ok ->
                            isResetting = false
                            showResetDialog = false
                            Toast.makeText(context, if (ok) "Environment re-initialized!" else "Reset failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaliRed),
                    enabled = !isResetting
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Confirm Reset", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }, enabled = !isResetting) {
                    Text("Cancel", color = KaliTextMuted)
                }
            },
            containerColor = KaliSurfaceDark
        )
    }
}
