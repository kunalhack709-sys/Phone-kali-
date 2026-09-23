package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.model.SetupStep
import com.example.model.SetupStepStatus
import com.example.ui.SecStationViewModel
import com.example.ui.theme.*

@Composable
fun SetupScreen(
    viewModel: SecStationViewModel,
    onSetupFinished: () -> Unit
) {
    val steps by viewModel.setupSteps.collectAsState()
    val isBootstrapping by viewModel.isBootstrapping.collectAsState()
    val isBootstrapped by viewModel.isBootstrapped.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaliDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .testTag("setup_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Dragon / Terminal Logo Icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A))
                .border(2.dp, KaliPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "SecStation Terminal",
                tint = KaliPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SecStation Workstation",
            color = KaliText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Rootless Kali Linux Security Environment",
            color = KaliPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Security Notice Card
        Card(
            colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KaliBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security",
                        tint = KaliSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Android Userspace Isolation",
                        color = KaliSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This environment runs 100% in unprivileged userspace within the app sandbox. It does not require root, custom ROMs, or Magisk. Raw network packet injection (CAP_NET_RAW) is restricted by Android security policy.",
                    color = KaliTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Bootstrapping Environment...",
            color = KaliText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Steps List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(steps) { step ->
                StepItemView(step)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isBootstrapping) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                CircularProgressIndicator(
                    color = KaliPrimary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Initializing Linux filesystem and PATH...",
                    color = KaliTextMuted,
                    fontSize = 13.sp
                )
            }
        } else if (isBootstrapped) {
            Button(
                onClick = onSetupFinished,
                colors = ButtonDefaults.buttonColors(containerColor = KaliPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("launch_workstation_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Launch",
                    tint = Color(0xFF00354E)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Launch Security Workstation",
                    color = Color(0xFF00354E),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = { viewModel.startBootstrap() },
                colors = ButtonDefaults.buttonColors(containerColor = KaliRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry Bootstrap Setup", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StepItemView(step: SetupStep) {
    Surface(
        color = KaliSurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            when (step.status) {
                SetupStepStatus.RUNNING -> {
                    CircularProgressIndicator(
                        color = KaliPrimary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                SetupStepStatus.COMPLETED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = KaliSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                SetupStepStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Failed",
                        tint = KaliRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                SetupStepStatus.PENDING -> {
                    Icon(
                        imageVector = Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Pending",
                        tint = KaliTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.title,
                    color = KaliText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (step.details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = step.details,
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
