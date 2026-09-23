package com.example.ui.screens

import android.widget.Toast
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
import com.example.model.CtfChallenge
import com.example.ui.SecStationViewModel
import com.example.ui.ScreenTab
import com.example.ui.theme.*

@Composable
fun CtfScreen(viewModel: SecStationViewModel) {
    val context = LocalContext.current
    val challenges by viewModel.repository.ctfChallenges.collectAsState()

    var targetIp by remember { mutableStateOf("10.10.10.42") }
    var targetHost by remember { mutableStateOf("ctf-lab.internal") }
    var scratchpadNotes by remember { mutableStateOf("# Lab Evidence & Notes\n- Found port 8080 open\n- Header inspection returned auth token\n") }
    var showAddChallengeDialog by remember { mutableStateOf(false) }

    val totalPoints = challenges.sumOf { it.points }
    val earnedPoints = challenges.filter { it.isSolved }.sumOf { it.points }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaliDarkBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("ctf_screen")
    ) {
        // Lab Configuration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, KaliBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Flag, "CTF", tint = KaliTertiary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CTF & Security Lab Workspace",
                            color = KaliText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = Color(0xFF49007A),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$earnedPoints / $totalPoints pts",
                            color = Color(0xFFF3DAFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { targetIp = it },
                        label = { Text("Target IP", fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = KaliText),
                        modifier = Modifier.weight(1f).height(50.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text("Target Hostname", fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = KaliText),
                        modifier = Modifier.weight(1f).height(50.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Launch Commands
                Text("Quick Launch to Terminal:", color = KaliTextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            viewModel.selectTab(ScreenTab.TERMINAL)
                            viewModel.executeCommand("nmap -sT -Pn $targetIp")
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KaliSurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("quick_nmap_btn")
                    ) {
                        Text("nmap -sT", color = KaliPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = {
                            viewModel.selectTab(ScreenTab.TERMINAL)
                            viewModel.executeCommand("curl -i http://$targetIp/")
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KaliSurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("curl -i", color = KaliSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = {
                            viewModel.selectTab(ScreenTab.TERMINAL)
                            viewModel.executeCommand("ping $targetIp")
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KaliSurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("ping", color = KaliText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Challenges Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active Lab Challenges",
                color = KaliText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { showAddChallengeDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = KaliTertiary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, "Add", tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Challenge", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Challenge List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(challenges, key = { it.id }) { ch ->
                CtfChallengeCard(
                    challenge = ch,
                    onSubmitFlag = { flagInput ->
                        val correct = viewModel.repository.solveCtfChallenge(ch.id, flagInput)
                        if (correct) {
                            Toast.makeText(context, "Challenge Solved! +${ch.points} pts", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect Flag. Try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // Add Challenge Dialog
    if (showAddChallengeDialog) {
        var chTitle by remember { mutableStateOf("") }
        var chCategory by remember { mutableStateOf("Web") }
        var chPort by remember { mutableStateOf("80") }
        var chPoints by remember { mutableStateOf("100") }
        var chNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddChallengeDialog = false },
            title = { Text("Add Lab Challenge", color = KaliText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = chTitle,
                        onValueChange = { chTitle = it },
                        label = { Text("Challenge Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chCategory,
                        onValueChange = { chCategory = it },
                        label = { Text("Category (Web, Network, Forensics, Crypto)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = chPort,
                            onValueChange = { chPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = chPoints,
                            onValueChange = { chPoints = it },
                            label = { Text("Points") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = chNotes,
                        onValueChange = { chNotes = it },
                        label = { Text("Instructions / Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (chTitle.isNotBlank()) {
                            viewModel.repository.addCtfChallenge(
                                title = chTitle,
                                category = chCategory,
                                ip = targetIp,
                                port = chPort.toIntOrNull() ?: 80,
                                points = chPoints.toIntOrNull() ?: 100,
                                notes = chNotes
                            )
                            showAddChallengeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaliTertiary)
                ) {
                    Text("Add Challenge", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChallengeDialog = false }) {
                    Text("Cancel", color = KaliTextMuted)
                }
            },
            containerColor = KaliSurfaceDark
        )
    }
}

@Composable
private fun CtfChallengeCard(
    challenge: CtfChallenge,
    onSubmitFlag: (String) -> Unit
) {
    var flagInput by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                0.5.dp,
                if (challenge.isSolved) KaliSecondary else KaliBorder,
                RoundedCornerShape(10.dp)
            )
            .testTag("challenge_card_${challenge.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = challenge.title,
                        color = KaliText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${challenge.category} • ${challenge.points} pts",
                        color = KaliTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (challenge.isSolved) {
                    Surface(
                        color = KaliSecondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KaliSecondary)
                    ) {
                        Text(
                            text = "SOLVED ✓",
                            color = KaliSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Target: ${challenge.targetIp}:${challenge.targetPort}",
                color = KaliPromptColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            if (challenge.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = challenge.notes,
                    color = KaliTextMuted,
                    fontSize = 11.sp
                )
            }

            if (!challenge.isSolved) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = flagInput,
                        onValueChange = { flagInput = it },
                        placeholder = { Text("FLAG{...}", fontSize = 11.sp, color = KaliTextMuted) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = KaliText),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaliPrimary,
                            unfocusedBorderColor = KaliBorder,
                            focusedTextColor = KaliText,
                            unfocusedTextColor = KaliText
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { onSubmitFlag(flagInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = KaliPrimary),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("submit_flag_btn_${challenge.id}")
                    ) {
                        Text("Submit", color = Color(0xFF00354E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
