package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
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
import com.example.model.LinuxFileItem
import com.example.ui.SecStationViewModel
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream

@Composable
fun FileManagerScreen(viewModel: SecStationViewModel) {
    val context = LocalContext.current
    val currentDir by viewModel.currentBrowserDir.collectAsState()
    val files by viewModel.browserFiles.collectAsState()
    val selectedFileContent by viewModel.selectedFileContent.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    // SAF Import Launcher
    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "imported_${System.currentTimeMillis()}.bin"
                val destFile = File(currentDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.refreshBrowserFiles()
                Toast.makeText(context, "Imported $fileName into userspace", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "SAF Import Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaliDarkBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("file_manager_screen")
    ) {
        // Top Location Bar
        Surface(
            color = KaliSurfaceDark,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Directory",
                    tint = KaliPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDisplayPath(currentDir.absolutePath, viewModel.linuxEnv.homeDir.absolutePath),
                    color = KaliText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                if (currentDir != viewModel.linuxEnv.homeDir && currentDir.parentFile != null) {
                    IconButton(
                        onClick = { viewModel.navigateBrowser(currentDir.parentFile!!) },
                        modifier = Modifier.size(28.dp).testTag("nav_up_btn")
                    ) {
                        Icon(Icons.Default.ArrowUpward, "Up", tint = KaliPrimary, modifier = Modifier.size(16.dp))
                    }
                }

                IconButton(
                    onClick = { viewModel.openInTerminal(currentDir) },
                    modifier = Modifier.size(28.dp).testTag("open_in_term_btn")
                ) {
                    Icon(Icons.Default.Terminal, "Open in Terminal", tint = KaliSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Actions & Search Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter files...", fontSize = 11.sp, color = KaliTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, "Search", tint = KaliPrimary, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).height(44.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = KaliText,
                    unfocusedTextColor = KaliText,
                    focusedBorderColor = KaliPrimary,
                    unfocusedBorderColor = KaliBorder
                ),
                singleLine = true
            )

            // New File button
            IconButton(
                onClick = { isCreatingFolder = false; showCreateDialog = true },
                modifier = Modifier.size(36.dp).background(KaliSurfaceDark, RoundedCornerShape(8.dp)).testTag("new_file_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, "New File", tint = KaliPrimary, modifier = Modifier.size(18.dp))
            }

            // New Folder button
            IconButton(
                onClick = { isCreatingFolder = true; showCreateDialog = true },
                modifier = Modifier.size(36.dp).background(KaliSurfaceDark, RoundedCornerShape(8.dp)).testTag("new_folder_btn")
            ) {
                Icon(Icons.Default.CreateNewFolder, "New Folder", tint = KaliSecondary, modifier = Modifier.size(18.dp))
            }

            // Import via SAF
            IconButton(
                onClick = { safLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.size(36.dp).background(KaliSurfaceDark, RoundedCornerShape(8.dp)).testTag("saf_import_btn")
            ) {
                Icon(Icons.Default.UploadFile, "Import SAF", tint = KaliTertiary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Files List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (filteredFiles.isEmpty()) {
                item {
                    Text(
                        text = "Directory is empty",
                        color = KaliTextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(filteredFiles, key = { it.absolutePath }) { fileItem ->
                    val fileObj = File(fileItem.absolutePath)
                    FileRowItem(
                        file = fileItem,
                        onOpen = {
                            if (fileItem.isDirectory) {
                                viewModel.navigateBrowser(fileObj)
                            } else {
                                viewModel.openFileInViewer(fileObj)
                            }
                        },
                        onDelete = { fileToDelete = fileObj }
                    )
                }
            }
        }
    }

    // New File/Folder Dialog
    if (showCreateDialog) {
        var inputName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (isCreatingFolder) "Create Directory" else "Create File", color = KaliText) },
            text = {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    placeholder = { Text(if (isCreatingFolder) "folder_name" else "script.sh") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputName.isNotBlank()) {
                            viewModel.createNewFile(inputName.trim(), isCreatingFolder)
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaliPrimary)
                ) {
                    Text("Create", color = Color(0xFF00354E))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = KaliTextMuted) }
            },
            containerColor = KaliSurfaceDark
        )
    }

    // Delete Confirmation Dialog
    if (fileToDelete != null) {
        val f = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete ${f.name}?", color = KaliRed) },
            text = { Text("This operation is irreversible.", color = KaliTextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFile(f)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaliRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel", color = KaliTextMuted) }
            },
            containerColor = KaliSurfaceDark
        )
    }

    // File Editor / Viewer Dialog
    if (selectedFileContent != null) {
        val (fileName, content) = selectedFileContent!!
        var editableContent by remember(fileName) { mutableStateOf(content) }

        AlertDialog(
            onDismissRequest = { viewModel.closeFileViewer() },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fileName, color = KaliText, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Button(
                        onClick = { viewModel.saveFileContent(fileName, editableContent) },
                        colors = ButtonDefaults.buttonColors(containerColor = KaliPrimary),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("save_file_btn")
                    ) {
                        Text("Save", color = Color(0xFF00354E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                OutlinedTextField(
                    value = editableContent,
                    onValueChange = { editableContent = it },
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = KaliText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaliPrimary,
                        unfocusedBorderColor = KaliBorder,
                        focusedTextColor = KaliText,
                        unfocusedTextColor = KaliText
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.closeFileViewer() }) {
                    Text("Close", color = KaliTextMuted)
                }
            },
            containerColor = KaliSurfaceDark
        )
    }
}

@Composable
private fun FileRowItem(
    file: LinuxFileItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = KaliSurfaceDark,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, KaliBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("file_item_${file.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = if (file.isDirectory) "Folder" else "File",
                tint = if (file.isDirectory) KaliPrimary else KaliTextMuted,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = KaliText,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "${file.permissions} • ${if (file.isDirectory) "DIR" else "${file.sizeBytes} B"}",
                    color = KaliTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp).testTag("delete_file_${file.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = KaliTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatDisplayPath(absPath: String, homePath: String): String {
    return when {
        absPath == homePath -> "~"
        absPath.startsWith(homePath) -> "~" + absPath.removePrefix(homePath)
        else -> absPath
    }
}
