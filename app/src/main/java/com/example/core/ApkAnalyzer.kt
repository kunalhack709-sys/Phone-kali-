package com.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class ApkSecurityReport(
    val fileName: String,
    val fileSizeKb: Long,
    val isZipValid: Boolean,
    val dexFilesCount: Int,
    val hasSignature: Boolean,
    val signatureType: String,
    val extractedPermissions: List<String>,
    val componentsFound: List<String>,
    val securityFindings: List<ApkSecurityFinding>,
    val rawSummary: String
)

data class ApkSecurityFinding(
    val title: String,
    val severity: String,
    val description: String,
    val recommendation: String
)

object ApkAnalyzer {

    suspend fun analyzeApk(apkFile: File): ApkSecurityReport = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return@withContext ApkSecurityReport(
                fileName = apkFile.name,
                fileSizeKb = 0,
                isZipValid = false,
                dexFilesCount = 0,
                hasSignature = false,
                signatureType = "None",
                extractedPermissions = emptyList(),
                componentsFound = emptyList(),
                securityFindings = listOf(
                    ApkSecurityFinding(
                        title = "File Missing or Empty",
                        severity = "High",
                        description = "The specified APK file does not exist or has 0 bytes.",
                        recommendation = "Provide a valid APK file path."
                    )
                ),
                rawSummary = "Error: File not found or empty."
            )
        }

        val sizeKb = apkFile.length() / 1024
        val permissions = mutableListOf<String>()
        val components = mutableListOf<String>()
        val findings = mutableListOf<ApkSecurityFinding>()
        var dexCount = 0
        var hasSignature = false
        var sigType = "None"

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (name.endsWith(".dex")) {
                        dexCount++
                    }

                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                        hasSignature = true
                        sigType = name.substringAfterLast(".")
                    }

                    if (name == "AndroidManifest.xml") {
                        zip.getInputStream(entry).use { stream ->
                            val strings = extractStringsFromBinaryXml(stream)
                            for (str in strings) {
                                if (str.startsWith("android.permission.")) {
                                    if (!permissions.contains(str)) permissions.add(str)
                                } else if (str.contains("Activity") || str.contains("Service") || str.contains("Receiver")) {
                                    if (!components.contains(str) && str.length < 80) components.add(str)
                                }

                                if (str.contains("debuggable") && strings.any { it == "true" }) {
                                    findings.add(
                                        ApkSecurityFinding(
                                            title = "Debuggable Flag Enabled",
                                            severity = "High",
                                            description = "The APK manifest may have android:debuggable set to true, allowing debugger attachment and memory dumping.",
                                            recommendation = "Ensure android:debuggable='false' in production release builds."
                                        )
                                    )
                                }

                                if (str.contains("allowBackup") && strings.any { it == "true" }) {
                                    findings.add(
                                        ApkSecurityFinding(
                                            title = "Application Backup Enabled",
                                            severity = "Medium",
                                            description = "android:allowBackup='true' allows extracting private app sandbox data via adb backup.",
                                            recommendation = "Set android:allowBackup='false' or configure restrictive dataExtractionRules."
                                        )
                                    )
                                }

                                if (str.contains("usesCleartextTraffic")) {
                                    findings.add(
                                        ApkSecurityFinding(
                                            title = "Cleartext Traffic Permitted",
                                            severity = "Medium",
                                            description = "App permits unencrypted HTTP transmission, susceptible to network eavesdropping and MITM.",
                                            recommendation = "Enforce HTTPS with Network Security Config."
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Signature verification check
            if (!hasSignature) {
                findings.add(
                    ApkSecurityFinding(
                        title = "Unsigned APK",
                        severity = "Critical",
                        description = "No META-INF signature certificates were detected. The APK will be rejected by Android package installer.",
                        recommendation = "Sign with apksigner using valid v2/v3 signing schemes."
                    )
                )
            }

            // Dangerous permissions check
            val dangerousPerms = listOf(
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.RECORD_AUDIO",
                "android.permission.CAMERA",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_CONTACTS",
                "android.permission.READ_SMS"
            )
            val foundDangerous = permissions.filter { it in dangerousPerms }
            if (foundDangerous.isNotEmpty()) {
                findings.add(
                    ApkSecurityFinding(
                        title = "High-Privilege Android Permissions Requested",
                        severity = "Low",
                        description = "Requests runtime permissions: ${foundDangerous.joinToString(", ")}",
                        recommendation = "Audit whether all dangerous permissions adhere to the principle of least privilege."
                    )
                )
            }

            val summary = buildString {
                appendLine("=== APK INSPECTION REPORT ===")
                appendLine("Target: ${apkFile.name} (${sizeKb} KB)")
                appendLine("DEX Files: $dexCount | Signed: $hasSignature ($sigType)")
                appendLine("Permissions Declared: ${permissions.size}")
                appendLine("Security Warnings: ${findings.size}")
            }

            ApkSecurityReport(
                fileName = apkFile.name,
                fileSizeKb = sizeKb,
                isZipValid = true,
                dexFilesCount = dexCount,
                hasSignature = hasSignature,
                signatureType = sigType,
                extractedPermissions = permissions,
                componentsFound = components.take(15),
                securityFindings = findings,
                rawSummary = summary
            )
        } catch (e: Exception) {
            ApkSecurityReport(
                fileName = apkFile.name,
                fileSizeKb = sizeKb,
                isZipValid = false,
                dexFilesCount = 0,
                hasSignature = false,
                signatureType = "Error",
                extractedPermissions = emptyList(),
                componentsFound = emptyList(),
                securityFindings = listOf(
                    ApkSecurityFinding(
                        title = "ZIP Parsing Error",
                        severity = "High",
                        description = "Unable to unpack APK: ${e.localizedMessage}",
                        recommendation = "Verify the file is an undamaged Android APK package."
                    )
                ),
                rawSummary = "Error parsing APK: ${e.localizedMessage}"
            )
        }
    }

    private fun extractStringsFromBinaryXml(input: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val bytes = input.readBytes()
        var i = 0
        while (i < bytes.size - 4) {
            // Find printable ASCII / UTF-8 strings
            if (bytes[i] in 32..126 && bytes[i + 1] in 32..126) {
                val start = i
                while (i < bytes.size && bytes[i] in 32..126) {
                    i++
                }
                val length = i - start
                if (length >= 4) {
                    val s = String(bytes, start, length)
                    if (s.any { it.isLetter() }) {
                        strings.add(s)
                    }
                }
            } else {
                i++
            }
        }
        return strings
    }
}
