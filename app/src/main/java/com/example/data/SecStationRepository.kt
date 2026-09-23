package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.core.ApkAnalyzer
import com.example.core.CompatibilityChecker
import com.example.core.LinuxEnvironment
import com.example.core.ShellExecutor
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ActiveSessionState(
    val id: String,
    val title: String,
    val cwd: File,
    val lines: List<TerminalLine>,
    val history: List<String>,
    val isRunning: Boolean = false
)

class SecStationRepository(private val context: Context) {

    val linuxEnv = LinuxEnvironment(context)

    private val _sessions = MutableStateFlow<List<ActiveSessionState>>(emptyList())
    val sessions: StateFlow<List<ActiveSessionState>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String>("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _packages = MutableStateFlow<List<PackageItem>>(emptyList())
    val packages: StateFlow<List<PackageItem>> = _packages.asStateFlow()

    private val _tools = MutableStateFlow<List<SecurityTool>>(emptyList())
    val tools: StateFlow<List<SecurityTool>> = _tools.asStateFlow()

    private val _targets = MutableStateFlow<List<BugBountyTarget>>(emptyList())
    val targets: StateFlow<List<BugBountyTarget>> = _targets.asStateFlow()

    private val _findings = MutableStateFlow<List<BugBountyFinding>>(emptyList())
    val findings: StateFlow<List<BugBountyFinding>> = _findings.asStateFlow()

    private val _ctfChallenges = MutableStateFlow<List<CtfChallenge>>(emptyList())
    val ctfChallenges: StateFlow<List<CtfChallenge>> = _ctfChallenges.asStateFlow()

    private val _systemMetrics = MutableStateFlow(getSystemMetrics())
    val systemMetrics: StateFlow<SystemResourceInfo> = _systemMetrics.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<String>>(emptyList())
    val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

    val shellExecutor = ShellExecutor(
        linuxEnv = linuxEnv,
        onPackageCommand = { cmd, args -> handlePkgCli(cmd, args) }
    )

    init {
        initDefaultPackages()
        initDefaultTools()
        initDefaultWorkspaces()
        logDiagnostic("SecStation Repository initialized. ABI: ${linuxEnv.detectedArch}")
    }

    fun initInitialSession() {
        if (_sessions.value.isEmpty()) {
            val initialId = UUID.randomUUID().toString()
            val initialLines = listOf(
                TerminalLine(text = "Kali GNU/Linux Rolling [Rootless Android Sandbox]", type = TerminalLineType.INFO),
                TerminalLine(text = "Storage: /data/data/${context.packageName}/files", type = TerminalLineType.INFO),
                TerminalLine(text = "Type 'help' or 'motd' for available commands.", type = TerminalLineType.INFO),
                TerminalLine(text = "Scope reminder: Test authorized targets only.", type = TerminalLineType.WARNING)
            )
            val session = ActiveSessionState(
                id = initialId,
                title = "Terminal 1",
                cwd = linuxEnv.homeDir,
                lines = initialLines,
                history = listOf("help", "whois example.com", "nmap 127.0.0.1", "pkg list")
            )
            _sessions.value = listOf(session)
            _activeSessionId.value = initialId
        }
    }

    fun createNewSession(): String {
        val newId = UUID.randomUUID().toString()
        val index = _sessions.value.size + 1
        val newSession = ActiveSessionState(
            id = newId,
            title = "Terminal $index",
            cwd = linuxEnv.homeDir,
            lines = listOf(
                TerminalLine(text = "Session $index started at ${java.util.Date()}", type = TerminalLineType.INFO)
            ),
            history = emptyList()
        )
        _sessions.value = _sessions.value + newSession
        _activeSessionId.value = newId
        logDiagnostic("New terminal session created: $newId")
        return newId
    }

    fun closeSession(sessionId: String) {
        val current = _sessions.value
        if (current.size <= 1) return // Keep at least one session
        val updated = current.filterNot { it.id == sessionId }
        _sessions.value = updated
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = updated.first().id
        }
        logDiagnostic("Closed session: $sessionId")
    }

    fun setActiveSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun appendLineToSession(sessionId: String, line: TerminalLine) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(lines = session.lines + line)
            } else session
        }
    }

    fun clearSession(sessionId: String) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(lines = emptyList())
            } else session
        }
    }

    fun updateSessionCwd(sessionId: String, newCwd: File) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(cwd = newCwd)
            } else session
        }
    }

    fun addSessionHistory(sessionId: String, command: String) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(history = session.history + command)
            } else session
        }
    }

    fun setSessionRunning(sessionId: String, running: Boolean) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(isRunning = running)
            } else session
        }
    }

    fun getSystemMetrics(): SystemResourceInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val usedRamMb = totalRamMb - availRamMb

        val statFs = StatFs(context.filesDir.absolutePath)
        val storageTotalMb = (statFs.blockCountLong * statFs.blockSizeLong) / (1024 * 1024)
        val storageAvailMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)

        return SystemResourceInfo(
            totalRamMb = totalRamMb,
            availRamMb = availRamMb,
            usedRamMb = usedRamMb,
            storageTotalMb = storageTotalMb,
            storageAvailMb = storageAvailMb,
            architecture = linuxEnv.detectedArch,
            osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            kernelVersion = System.getProperty("os.version") ?: "Linux",
            activeTasksCount = _sessions.value.count { it.isRunning }
        )
    }

    fun refreshMetrics() {
        _systemMetrics.value = getSystemMetrics()
    }

    fun logDiagnostic(entry: String) {
        val timestamped = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $entry"
        _diagnosticLogs.value = listOf(timestamped) + _diagnosticLogs.value.take(200)
    }

    // Packages Management
    private fun initDefaultPackages() {
        _packages.value = listOf(
            PackageItem("nmap", "nmap", "7.94-1", "Network exploration tool and security / port scanner (Rootless TCP Connect mode)", "Network", 5_800_000, true, CompatibilityStatus.LIMITED),
            PackageItem("curl", "curl", "8.5.0", "Command line tool for transferring data with URLs", "Web", 1_200_000, true, CompatibilityStatus.COMPATIBLE),
            PackageItem("wget", "wget", "1.21.4", "Tool for retrieving files using HTTP, HTTPS, FTP", "Web", 890_000, true, CompatibilityStatus.COMPATIBLE),
            PackageItem("dnsutils", "dnsutils", "9.18.21", "DNS utilities including dig, nslookup, and host", "Recon", 950_000, true, CompatibilityStatus.COMPATIBLE),
            PackageItem("whois", "whois", "5.5.20", "Intelligent client for the WHOIS directory service", "Recon", 320_000, true, CompatibilityStatus.COMPATIBLE),
            PackageItem("apktool", "apktool", "2.9.0", "Tool for reverse engineering Android apk files", "Android", 14_500_000, true, CompatibilityStatus.COMPATIBLE),
            PackageItem("jadx", "jadx", "1.5.0", "Dex to Java decompiler and DEX analyzer", "Android", 22_000_000, false, CompatibilityStatus.COMPATIBLE),
            PackageItem("sqlmap", "sqlmap", "1.8.2", "Automatic SQL injection and database takeover tool", "Web", 8_400_000, false, CompatibilityStatus.COMPATIBLE),
            PackageItem("nikto", "nikto", "2.5.0", "Web server scanner for dangerous files and vulnerabilities", "Web", 4_100_000, false, CompatibilityStatus.COMPATIBLE),
            PackageItem("gobuster", "gobuster", "3.6.0", "Directory/file, DNS and VHost busting tool", "Web", 6_200_000, false, CompatibilityStatus.COMPATIBLE),
            PackageItem("hydra", "hydra", "9.5", "Fast network authentication auditor", "Auditing", 3_900_000, false, CompatibilityStatus.COMPATIBLE),
            PackageItem("tcpdump", "tcpdump", "4.99.4", "Packet analyzer (Requires CAP_NET_RAW / root for raw promiscuous capture)", "Network", 2_100_000, false, CompatibilityStatus.UNSUPPORTED),
            PackageItem("aircrack-ng", "aircrack-ng", "1.7", "Wireless security auditing suite (Requires monitor mode & root)", "Wireless", 7_800_000, false, CompatibilityStatus.UNSUPPORTED),
            PackageItem("john", "john", "1.9.0-jumbo", "John the Ripper password security auditor", "Forensics", 12_000_000, false, CompatibilityStatus.COMPATIBLE),
            PackageItem("python3", "python3", "3.11.8", "Python programming language userspace interpreter", "Programming", 18_000_000, true, CompatibilityStatus.COMPATIBLE)
        )
    }

    fun toggleInstallPackage(pkgId: String): Boolean {
        var didInstall = false
        _packages.value = _packages.value.map { pkg ->
            if (pkg.id == pkgId) {
                val newStatus = !pkg.isInstalled
                didInstall = newStatus
                logDiagnostic("Package ${pkg.name} ${if (newStatus) "installed" else "removed"}")
                pkg.copy(isInstalled = newStatus)
            } else pkg
        }
        return didInstall
    }

    fun installPackageByName(name: String): String {
        val pkg = _packages.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
        return if (pkg != null) {
            if (pkg.isInstalled) {
                "Package '${pkg.name}' is already installed (version ${pkg.version})."
            } else {
                _packages.value = _packages.value.map { if (it.id == pkg.id) it.copy(isInstalled = true) else it }
                logDiagnostic("Installed package ${pkg.name} via CLI")
                "Successfully installed ${pkg.name} (${pkg.version}) into \$PREFIX/bin."
            }
        } else {
            "E: Unable to locate package $name. Run 'pkg list' to view available packages."
        }
    }

    private fun handlePkgCli(cmd: String, args: List<String>): String {
        return when (cmd.lowercase()) {
            "update" -> {
                logDiagnostic("Updated package repository indexes")
                "Hit:1 https://kali.download/kali kali-rolling InRelease\nReading package lists... Done\nAll packages are up to date."
            }
            "upgrade" -> {
                "Calculating upgrade... Done\n0 upgraded, 0 newly installed, 0 to remove."
            }
            "install" -> {
                val target = args.firstOrNull()
                if (target.isNullOrBlank()) {
                    "Usage: pkg install <package-name>"
                } else {
                    installPackageByName(target)
                }
            }
            "remove", "uninstall" -> {
                val target = args.firstOrNull()
                val pkg = _packages.value.firstOrNull { it.name.equals(target, ignoreCase = true) }
                if (pkg != null) {
                    _packages.value = _packages.value.map { if (it.id == pkg.id) it.copy(isInstalled = false) else it }
                    "Removed ${pkg.name}."
                } else {
                    "Package not installed: $target"
                }
            }
            "list" -> {
                val sb = StringBuilder("=== Available Kali Packages (Rootless Userspace) ===\n")
                _packages.value.forEach {
                    val status = if (it.isInstalled) "[installed]" else "[available]"
                    sb.appendLine("${it.name.padEnd(14)} ${it.version.padEnd(10)} ${it.compatibility.badge} $status - ${it.description}")
                }
                sb.toString()
            }
            "search" -> {
                val query = args.firstOrNull() ?: ""
                val matches = _packages.value.filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
                buildString {
                    appendLine("Search results for '$query':")
                    matches.forEach { appendLine("  ${it.name} - ${it.description}") }
                }
            }
            else -> "Usage: pkg [update | upgrade | install <pkg> | remove <pkg> | list | search <query>]"
        }
    }

    // Default Tools
    private fun initDefaultTools() {
        _tools.value = listOf(
            SecurityTool(
                id = "tool_nmap",
                name = "Nmap Scanner",
                binary = "nmap",
                category = ToolCategory.NETWORK,
                description = "Network exploration & TCP connect port scanner. Scans open services on target IP/domain.",
                compatibility = CompatibilityStatus.LIMITED,
                limitationReason = "SYN stealth scan (-sS) requires CAP_NET_RAW / root. TCP connect mode (-sT) works without root.",
                rootlessAlternative = "Runs in unprivileged TCP Connect mode (-sT -Pn).",
                defaultArgs = "-sT -Pn 127.0.0.1",
                suggestedArgs = listOf("-sT -Pn 127.0.0.1", "-sT -Pn -p 80,443 scanme.nmap.org", "-sT -Pn 192.168.1.1")
            ),
            SecurityTool(
                id = "tool_whois",
                name = "WHOIS Client",
                binary = "whois",
                category = ToolCategory.RECON,
                description = "Look up domain ownership, registrar info, and nameservers directly from IANA/TLD registry.",
                compatibility = CompatibilityStatus.COMPATIBLE,
                defaultArgs = "example.com",
                suggestedArgs = listOf("google.com", "example.com", "github.com")
            ),
            SecurityTool(
                id = "tool_dig",
                name = "DNS Inspector (dig)",
                binary = "dig",
                category = ToolCategory.RECON,
                description = "Query DNS nameservers for A, AAAA, MX, and TXT records.",
                compatibility = CompatibilityStatus.COMPATIBLE,
                defaultArgs = "example.com",
                suggestedArgs = listOf("example.com", "cloudflare.com", "wikipedia.org")
            ),
            SecurityTool(
                id = "tool_curl",
                name = "cURL HTTP Inspector",
                binary = "curl",
                category = ToolCategory.WEB,
                description = "Command-line HTTP/HTTPS client to inspect server headers, SSL/TLS, and API responses.",
                compatibility = CompatibilityStatus.COMPATIBLE,
                defaultArgs = "-i https://httpbin.org/get",
                suggestedArgs = listOf("-i https://httpbin.org/get", "-I https://example.com", "-i https://httpbin.org/headers")
            ),
            SecurityTool(
                id = "tool_ping",
                name = "Ping ICMP Utility",
                binary = "ping",
                category = ToolCategory.NETWORK,
                description = "Test reachability and latency to a remote host.",
                compatibility = CompatibilityStatus.LIMITED,
                limitationReason = "Standard raw ICMP sockets are restricted on unrooted Android. Uses unprivileged fallback.",
                rootlessAlternative = "Unprivileged ICMP socket and TCP handshake latency fallback.",
                defaultArgs = "1.1.1.1",
                suggestedArgs = listOf("8.8.8.8", "1.1.1.1", "google.com")
            ),
            SecurityTool(
                id = "tool_apktool",
                name = "APK Inspector",
                binary = "apktool",
                category = ToolCategory.ANDROID,
                description = "Reverse engineer Android APKs, inspect AndroidManifest.xml, permissions, and security risks.",
                compatibility = CompatibilityStatus.COMPATIBLE,
                defaultArgs = "d base.apk",
                suggestedArgs = listOf("d app-release.apk")
            ),
            SecurityTool(
                id = "tool_hashcheck",
                name = "Hash Calculator",
                binary = "hashcheck",
                category = ToolCategory.FORENSICS,
                description = "Calculate MD5, SHA-1, and SHA-256 cryptographic hashes for files.",
                compatibility = CompatibilityStatus.COMPATIBLE,
                defaultArgs = "~/bugbounty/SCOPE_NOTICE.txt",
                suggestedArgs = listOf("~/bugbounty/SCOPE_NOTICE.txt")
            ),
            SecurityTool(
                id = "tool_tcpdump",
                name = "tcpdump Sniffer",
                binary = "tcpdump",
                category = ToolCategory.NETWORK,
                description = "Promiscuous packet capture utility.",
                compatibility = CompatibilityStatus.UNSUPPORTED,
                limitationReason = "AF_PACKET raw socket requires CAP_NET_RAW / root.",
                rootlessAlternative = "Use VpnService-based local packet capture or userspace HTTP Inspector.",
                defaultArgs = "-i any",
                suggestedArgs = listOf("-i any -n")
            ),
            SecurityTool(
                id = "tool_aircrack",
                name = "Aircrack-ng",
                binary = "aircrack-ng",
                category = ToolCategory.NETWORK,
                description = "802.11 wireless security auditing suite.",
                compatibility = CompatibilityStatus.UNSUPPORTED,
                limitationReason = "Requires kernel driver nl80211 monitor mode and raw frame injection.",
                rootlessAlternative = "Run on an external Linux machine with a supported USB Wi-Fi adapter.",
                defaultArgs = "--help",
                suggestedArgs = listOf("--help")
            )
        )
    }

    // Bug Bounty Workspace Operations
    private fun initDefaultWorkspaces() {
        _targets.value = listOf(
            BugBountyTarget(
                id = "target_1",
                programName = "Acme Corp Bug Bounty",
                targetAsset = "*.acmecorp.example",
                inScope = "*.acmecorp.example, api.acmecorp.example",
                outOfScope = "blog.acmecorp.example, third-party CDNs, physical/social engineering",
                notes = "Responsible disclosure program. Testing allowed on staging and test endpoints."
            )
        )

        _findings.value = listOf(
            BugBountyFinding(
                id = "finding_1",
                targetId = "target_1",
                title = "Missing Security Headers & Information Disclosure",
                severity = FindingSeverity.LOW,
                cvssScore = 3.1,
                endpoint = "https://api.acmecorp.example/v1/status",
                description = "Server response headers disclose backend technology and missing X-Content-Type-Options.",
                stepsToReproduce = "1. Execute: curl -i https://api.acmecorp.example/v1/status\n2. Observe Server header disclosing internal framework versions.",
                remediation = "Configure reverse proxy to strip identifying headers and set Strict-Transport-Security."
            )
        )

        _ctfChallenges.value = listOf(
            CtfChallenge(
                id = "ctf_1",
                title = "Header Secret",
                category = "Web",
                targetIp = "10.10.10.42",
                targetPort = 8080,
                points = 100,
                flag = "FLAG{h3ad3r_1nsp3ct10n_s3cur3}",
                isSolved = false,
                notes = "Inspect HTTP response headers for secret challenge authorization token.",
                commands = "curl -i http://10.10.10.42:8080/"
            ),
            CtfChallenge(
                id = "ctf_2",
                title = "Hidden Port Service",
                category = "Network",
                targetIp = "10.10.10.42",
                targetPort = 3389,
                points = 150,
                flag = "FLAG{p0rt_sc4nn1ng_pr0}",
                isSolved = false,
                notes = "Scan TCP ports 8000-8500 using rootless nmap.",
                commands = "nmap -sT -Pn 10.10.10.42"
            )
        )
    }

    fun addTarget(program: String, asset: String, inScope: String, outScope: String, notes: String) {
        val newTarget = BugBountyTarget(
            id = UUID.randomUUID().toString(),
            programName = program,
            targetAsset = asset,
            inScope = inScope,
            outOfScope = outScope,
            notes = notes
        )
        _targets.value = listOf(newTarget) + _targets.value
        logDiagnostic("Added new bug bounty target: $program")
    }

    fun addFinding(
        targetId: String,
        title: String,
        severity: FindingSeverity,
        cvss: Double,
        endpoint: String,
        desc: String,
        steps: String,
        remediation: String
    ) {
        val newFinding = BugBountyFinding(
            id = UUID.randomUUID().toString(),
            targetId = targetId,
            title = title,
            severity = severity,
            cvssScore = cvss,
            endpoint = endpoint,
            description = desc,
            stepsToReproduce = steps,
            remediation = remediation
        )
        _findings.value = listOf(newFinding) + _findings.value
        logDiagnostic("Recorded finding: $title [${severity.label}]")
    }

    fun addCtfChallenge(title: String, category: String, ip: String, port: Int, points: Int, notes: String) {
        val ch = CtfChallenge(
            id = UUID.randomUUID().toString(),
            title = title,
            category = category,
            targetIp = ip,
            targetPort = port,
            points = points,
            notes = notes,
            commands = "nmap -sT -Pn $ip"
        )
        _ctfChallenges.value = listOf(ch) + _ctfChallenges.value
        logDiagnostic("Added CTF challenge: $title")
    }

    fun solveCtfChallenge(id: String, submittedFlag: String): Boolean {
        var correct = false
        _ctfChallenges.value = _ctfChallenges.value.map { ch ->
            if (ch.id == id) {
                if (ch.flag.isBlank() || ch.flag.trim() == submittedFlag.trim()) {
                    correct = true
                    logDiagnostic("CTF Challenge solved: ${ch.title}!")
                    ch.copy(isSolved = true)
                } else ch
            } else ch
        }
        return correct
    }

    fun generateBugBountyReport(targetId: String): String {
        val target = _targets.value.firstOrNull { it.id == targetId } ?: return "Target not found."
        val targetFindings = _findings.value.filter { it.targetId == targetId }

        return buildString {
            appendLine("# Vulnerability Assessment & Bug Bounty Report")
            appendLine("Generated by SecStation Rootless Workstation on ${java.util.Date()}\n")
            appendLine("## Program Information")
            appendLine("- **Program:** ${target.programName}")
            appendLine("- **Target Asset:** `${target.targetAsset}`")
            appendLine("- **In-Scope:** ${target.inScope}")
            appendLine("- **Scope Policy Confirmed:** Yes\n")
            appendLine("## Summary of Findings")
            appendLine("Total Vulnerabilities Identified: ${targetFindings.size}\n")
            targetFindings.forEachIndexed { i, f ->
                appendLine("### ${i + 1}. ${f.title}")
                appendLine("- **Severity:** ${f.severity.label} (CVSS: ${f.cvssScore})")
                appendLine("- **Vulnerable Endpoint:** `${f.endpoint}`")
                appendLine("- **Status:** ${f.status}\n")
                appendLine("#### Description")
                appendLine(f.description)
                appendLine("\n#### Steps to Reproduce")
                appendLine(f.stepsToReproduce)
                appendLine("\n#### Remediation")
                appendLine(f.remediation.ifBlank { "Apply standard security hardening." })
                appendLine("\n---\n")
            }
        }
    }
}
