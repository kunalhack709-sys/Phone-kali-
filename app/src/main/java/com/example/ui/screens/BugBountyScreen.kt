package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.model.BugBountyFinding
import com.example.model.BugBountyTarget
import com.example.model.FindingSeverity
import com.example.ui.SecStationViewModel
import com.example.ui.components.ScopeNoticeBanner
import com.example.ui.theme.*

@Composable
fun BugBountyScreen(viewModel: SecStationViewModel) {
    val context = LocalContext.current
    val targets by viewModel.repository.targets.collectAsState()
    val findings by viewModel.repository.findings.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Targets, 1: Findings, 2: Report
    var showAddTargetDialog by remember { mutableStateOf(false) }
    var showAddFindingDialog by remember { mutableStateOf(false) }
    var selectedTargetForReport by remember { mutableStateOf<String?>(targets.firstOrNull()?.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaliDarkBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("bug_bounty_screen")
    ) {
        // Scope Reminder Header
        ScopeNoticeBanner(modifier = Modifier.padding(bottom = 8.dp))

        // Tabs Row
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = KaliSurfaceDark,
            contentColor = KaliPrimary,
            divider = { HorizontalDivider(color = KaliBorder) }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Targets (${targets.size})", fontSize = 13.sp) },
                modifier = Modifier.testTag("tab_targets")
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Findings (${findings.size})", fontSize = 13.sp) },
                modifier = Modifier.testTag("tab_findings")
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Reports", fontSize = 13.sp) },
                modifier = Modifier.testTag("tab_reports")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (activeTab) {
            0 -> {
                // Targets Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Authorized Scope Targets",
                        color = KaliText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showAddTargetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = KaliPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_target_btn")
                    ) {
                        Icon(Icons.Default.Add, "Add", tint = Color(0xFF00354E), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Target", color = Color(0xFF00354E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(targets, key = { it.id }) { target ->
                        TargetCardItem(target = target, findingsCount = findings.count { it.targetId == target.id })
                    }
                }
            }
            1 -> {
                // Findings Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vulnerability Findings",
                        color = KaliText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showAddFindingDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = KaliSecondary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_finding_btn")
                    ) {
                        Icon(Icons.Default.BugReport, "Report", tint = Color(0xFF00354E), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log Finding", color = Color(0xFF00354E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(findings, key = { it.id }) { finding ->
                        val target = targets.firstOrNull { it.id == finding.targetId }
                        FindingCardItem(finding = finding, targetName = target?.programName ?: "Unknown Target")
                    }
                }
            }
            2 -> {
                // Reports Tab
                ReportGeneratorView(
                    targets = targets,
                    selectedTargetId = selectedTargetForReport,
                    onTargetSelected = { selectedTargetForReport = it },
                    onGenerateReport = { targetId ->
                        val reportMarkdown = viewModel.repository.generateBugBountyReport(targetId)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Bug Bounty Report", reportMarkdown))
                        Toast.makeText(context, "Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    getReportPreview = { targetId ->
                        viewModel.repository.generateBugBountyReport(targetId)
                    }
                )
            }
        }
    }

    // Add Target Dialog
    if (showAddTargetDialog) {
        var programName by remember { mutableStateOf("") }
        var targetAsset by remember { mutableStateOf("") }
        var inScope by remember { mutableStateOf("") }
        var outOfScope by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddTargetDialog = false },
            title = { Text("Add Scope Target", color = KaliText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = programName,
                        onValueChange = { programName = it },
                        label = { Text("Program Name") },
                        placeholder = { Text("e.g. Acme Corp Bug Bounty") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetAsset,
                        onValueChange = { targetAsset = it },
                        label = { Text("Target Asset / Domain") },
                        placeholder = { Text("*.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inScope,
                        onValueChange = { inScope = it },
                        label = { Text("In-Scope Rules") },
                        placeholder = { Text("api.example.com, auth.example.com") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = outOfScope,
                        onValueChange = { outOfScope = it },
                        label = { Text("Out-of-Scope Assets (Excluded)") },
                        placeholder = { Text("blog.example.com, third parties") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (programName.isNotBlank() && targetAsset.isNotBlank()) {
                            viewModel.repository.addTarget(programName, targetAsset, inScope, outOfScope, notes)
                            showAddTargetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaliPrimary)
                ) {
                    Text("Save Target", color = Color(0xFF00354E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTargetDialog = false }) {
                    Text("Cancel", color = KaliTextMuted)
                }
            },
            containerColor = KaliSurfaceDark
        )
    }

    // Add Finding Dialog
    if (showAddFindingDialog) {
        var selectedTargetId by remember { mutableStateOf(targets.firstOrNull()?.id ?: "") }
        var title by remember { mutableStateOf("") }
        var severity by remember { mutableStateOf(FindingSeverity.MEDIUM) }
        var cvss by remember { mutableStateOf("5.3") }
        var endpoint by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var steps by remember { mutableStateOf("") }
        var remediation by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddFindingDialog = false },
            title = { Text("Record Vulnerability Finding", color = KaliText, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Vulnerability Title") },
                            placeholder = { Text("e.g. Reflected Cross-Site Scripting (XSS)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("Vulnerable URL / Parameter") },
                            placeholder = { Text("https://example.com/search?q=") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FindingSeverity.entries.forEach { sev ->
                                FilterChip(
                                    selected = severity == sev,
                                    onClick = { severity = sev },
                                    label = { Text(sev.label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(sev.colorHex),
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description & Impact") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = steps,
                            onValueChange = { steps = it },
                            label = { Text("Steps to Reproduce") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && selectedTargetId.isNotBlank()) {
                            val score = cvss.toDoubleOrNull() ?: 5.0
                            viewModel.repository.addFinding(
                                targetId = selectedTargetId,
                                title = title,
                                severity = severity,
                                cvss = score,
                                endpoint = endpoint,
                                desc = description,
                                steps = steps,
                                remediation = remediation
                            )
                            showAddFindingDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaliSecondary)
                ) {
                    Text("Save Finding", color = Color(0xFF00354E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFindingDialog = false }) {
                    Text("Cancel", color = KaliTextMuted)
                }
            },
            containerColor = KaliSurfaceDark
        )
    }
}

@Composable
private fun TargetCardItem(target: BugBountyTarget, findingsCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, KaliBorder, RoundedCornerShape(10.dp))
            .testTag("target_card_${target.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = target.programName,
                    color = KaliText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = KaliSurfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$findingsCount Findings",
                        color = KaliPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Target: ${target.targetAsset}",
                color = KaliPromptColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "In-Scope: ${target.inScope}",
                color = KaliSecondary,
                fontSize = 11.sp
            )
            Text(
                text = "Out-of-Scope: ${target.outOfScope}",
                color = KaliRed.copy(alpha = 0.9f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun FindingCardItem(finding: BugBountyFinding, targetName: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, KaliBorder, RoundedCornerShape(10.dp))
            .testTag("finding_card_${finding.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = finding.title,
                    color = KaliText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = Color(finding.severity.colorHex).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(finding.severity.colorHex))
                ) {
                    Text(
                        text = finding.severity.label,
                        color = Color(finding.severity.colorHex),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Program: $targetName | Endpoint: ${finding.endpoint}",
                color = KaliTextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = finding.description,
                color = KaliText,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ReportGeneratorView(
    targets: List<BugBountyTarget>,
    selectedTargetId: String?,
    onTargetSelected: (String) -> Unit,
    onGenerateReport: (String) -> Unit,
    getReportPreview: (String) -> String
) {
    var previewText by remember(selectedTargetId) {
        mutableStateOf(selectedTargetId?.let { getReportPreview(it) } ?: "Select a target to generate assessment report.")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Select Assessment Target:", color = KaliText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEach { target ->
                FilterChip(
                    selected = selectedTargetId == target.id,
                    onClick = {
                        onTargetSelected(target.id)
                        previewText = getReportPreview(target.id)
                    },
                    label = { Text(target.programName, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KaliPrimary,
                        selectedLabelColor = Color(0xFF00354E),
                        containerColor = KaliSurfaceDark,
                        labelColor = KaliText
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Report Markdown Preview:", color = KaliTextMuted, fontSize = 12.sp)
            if (selectedTargetId != null) {
                Button(
                    onClick = { onGenerateReport(selectedTargetId) },
                    colors = ButtonDefaults.buttonColors(containerColor = KaliPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("copy_report_btn")
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = Color(0xFF00354E), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Markdown Report", color = Color(0xFF00354E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = KaliSurfaceDark,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                item {
                    Text(
                        text = previewText,
                        color = KaliText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
