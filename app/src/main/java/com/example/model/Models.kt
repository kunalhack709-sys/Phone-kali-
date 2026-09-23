package com.example.model

enum class ToolCategory(val displayName: String) {
    RECON("Reconnaissance"),
    WEB("Web Security"),
    NETWORK("Network Analysis"),
    ANDROID("Android Security"),
    PROGRAMMING("Programming"),
    FORENSICS("Forensics & Crypto"),
    AUDITING("System & Audit")
}

enum class CompatibilityStatus(val badge: String, val label: String) {
    COMPATIBLE("✓", "Works without root"),
    LIMITED("△", "Works with limitations"),
    UNSUPPORTED("✗", "Requires root / unsupported")
}

data class SecurityTool(
    val id: String,
    val name: String,
    val binary: String,
    val category: ToolCategory,
    val description: String,
    val compatibility: CompatibilityStatus,
    val limitationReason: String? = null,
    val rootlessAlternative: String? = null,
    val defaultArgs: String = "",
    val suggestedArgs: List<String> = emptyList(),
    val isInstalled: Boolean = true
)

data class PackageItem(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val category: String,
    val sizeBytes: Long,
    val isInstalled: Boolean,
    val compatibility: CompatibilityStatus,
    val dependencies: List<String> = emptyList()
)

enum class TerminalLineType {
    PROMPT,
    STDIN,
    STDOUT,
    STDERR,
    INFO,
    WARNING,
    SUCCESS
}

data class TerminalLine(
    val id: Long = System.nanoTime(),
    val text: String,
    val type: TerminalLineType = TerminalLineType.STDOUT,
    val timestamp: Long = System.currentTimeMillis()
)

data class BugBountyTarget(
    val id: String,
    val programName: String,
    val targetAsset: String,
    val inScope: String,
    val outOfScope: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class FindingSeverity(val label: String, val colorHex: Long) {
    INFO("Info", 0xFF38BDF8),
    LOW("Low", 0xFF10B981),
    MEDIUM("Medium", 0xFFF59E0B),
    HIGH("High", 0xFFF97316),
    CRITICAL("Critical", 0xFFEF4444)
}

data class BugBountyFinding(
    val id: String,
    val targetId: String,
    val title: String,
    val severity: FindingSeverity,
    val cvssScore: Double,
    val endpoint: String,
    val description: String,
    val stepsToReproduce: String,
    val remediation: String = "",
    val status: String = "Open",
    val timestamp: Long = System.currentTimeMillis()
)

data class CtfChallenge(
    val id: String,
    val title: String,
    val category: String,
    val targetIp: String,
    val targetPort: Int = 80,
    val points: Int = 100,
    val flag: String = "",
    val isSolved: Boolean = false,
    val notes: String = "",
    val commands: String = ""
)

data class LinuxFileItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val permissions: String,
    val lastModified: Long
)

data class SystemResourceInfo(
    val totalRamMb: Long,
    val availRamMb: Long,
    val usedRamMb: Long,
    val storageTotalMb: Long,
    val storageAvailMb: Long,
    val architecture: String,
    val osVersion: String,
    val kernelVersion: String,
    val activeTasksCount: Int
)

data class SetupStep(
    val title: String,
    val status: SetupStepStatus,
    val details: String = ""
)

enum class SetupStepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

enum class TerminalTheme(val displayName: String) {
    KALI("Kali Dragon"),
    MATRIX("Matrix Green"),
    MONOKAI("Monokai Dark"),
    CYBERPUNK("Cyberpunk Neon")
}
