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
import com.example.model.CompatibilityStatus
import com.example.model.PackageItem
import com.example.ui.SecStationViewModel
import com.example.ui.components.CompatibilityBadge
import com.example.ui.theme.*

@Composable
fun PackagesScreen(viewModel: SecStationViewModel) {
    val context = LocalContext.current
    val packages by viewModel.repository.packages.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") } // all, installed, available
    var selectedPackageToConfirm by remember { mutableStateOf<PackageItem?>(null) }

    val filtered = remember(packages, searchQuery, filterMode) {
        packages.filter { pkg ->
            val matchesFilter = when (filterMode) {
                "installed" -> pkg.isInstalled
                "available" -> !pkg.isInstalled
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                pkg.name.contains(searchQuery, ignoreCase = true) ||
                pkg.description.contains(searchQuery, ignoreCase = true) ||
                pkg.category.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaliDarkBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("packages_screen")
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "APT Package Manager",
                    color = KaliText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Userspace repository: kali-rolling [rootless]",
                    color = KaliTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        viewModel.repository.installPackageByName("update")
                        Toast.makeText(context, "Package lists updated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaliSurfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("pkg_update_btn")
                ) {
                    Icon(Icons.Default.Refresh, "Update", tint = KaliPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Update", color = KaliPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search packages (nmap, python, sqlmap)...", color = KaliTextMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, "Search", tint = KaliPrimary, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("package_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = KaliText,
                unfocusedTextColor = KaliText,
                focusedBorderColor = KaliPrimary,
                unfocusedBorderColor = KaliBorder
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Tabs
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All (${packages.size})", "installed" to "Installed (${packages.count { it.isInstalled }})", "available" to "Available (${packages.count { !it.isInstalled }})").forEach { (mode, label) ->
                FilterChip(
                    selected = filterMode == mode,
                    onClick = { filterMode = mode },
                    label = { Text(label, fontSize = 12.sp) },
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

        // Packages List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(filtered, key = { it.id }) { pkg ->
                PackageCardItem(
                    pkg = pkg,
                    onToggle = { selectedPackageToConfirm = pkg }
                )
            }
        }
    }

    // Confirmation Dialog
    if (selectedPackageToConfirm != null) {
        val p = selectedPackageToConfirm!!
        AlertDialog(
            onDismissRequest = { selectedPackageToConfirm = null },
            title = {
                Text(
                    text = if (p.isInstalled) "Uninstall ${p.name}?" else "Install ${p.name}?",
                    color = KaliText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = p.description,
                        color = KaliTextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Version: ${p.version} | Size: ${p.sizeBytes / 1024 / 1024} MB",
                        color = KaliText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Category: ${p.category}",
                        color = KaliTextMuted,
                        fontSize = 12.sp
                    )
                    if (p.compatibility != CompatibilityStatus.COMPATIBLE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Notice: This package has Android no-root limitations (${p.compatibility.label}).",
                            color = if (p.compatibility == CompatibilityStatus.UNSUPPORTED) KaliRed else KaliYellow,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val didInstall = viewModel.repository.toggleInstallPackage(p.id)
                        Toast.makeText(
                            context,
                            if (didInstall) "${p.name} installed into \$PREFIX/bin" else "${p.name} removed",
                            Toast.LENGTH_SHORT
                        ).show()
                        selectedPackageToConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (p.isInstalled) KaliRed else KaliPrimary
                    ),
                    modifier = Modifier.testTag("confirm_package_action_btn")
                ) {
                    Text(
                        text = if (p.isInstalled) "Confirm Uninstall" else "Confirm Install",
                        color = if (p.isInstalled) Color.White else Color(0xFF00354E),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPackageToConfirm = null }) {
                    Text("Cancel", color = KaliTextMuted)
                }
            },
            containerColor = KaliSurfaceDark
        )
    }
}

@Composable
private fun PackageCardItem(
    pkg: PackageItem,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KaliSurfaceDark),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, KaliBorder, RoundedCornerShape(10.dp))
            .testTag("package_card_${pkg.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pkg.name,
                        color = KaliText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "v${pkg.version}",
                        color = KaliTextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CompatibilityBadge(status = pkg.compatibility, showLabel = false)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pkg.description,
                    color = KaliTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${pkg.category} • ${(pkg.sizeBytes / 1024 / 1024)} MB",
                    color = KaliPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (pkg.isInstalled) {
                OutlinedButton(
                    onClick = onToggle,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaliRed),
                    modifier = Modifier.testTag("uninstall_btn_${pkg.name}")
                ) {
                    Text("Remove", fontSize = 11.sp)
                }
            } else {
                Button(
                    onClick = onToggle,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaliPrimary),
                    modifier = Modifier.testTag("install_btn_${pkg.name}")
                ) {
                    Text("Install", fontSize = 11.sp, color = Color(0xFF00354E), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
