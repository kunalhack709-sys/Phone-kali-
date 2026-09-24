package com.example.core

import com.example.model.TerminalLine
import com.example.model.TerminalLineType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class ShellExecutor(
    private val linuxEnv: LinuxEnvironment,
    private val onPackageCommand: (String, List<String>) -> String
) {

    suspend fun execute(
        rawCommand: String,
        currentDir: File,
        onDirChanged: (File) -> Unit
    ): Flow<TerminalLine> = flow {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return@flow

        val parts = parseCommandArgs(trimmed)
        val command = parts.firstOrNull() ?: return@flow
        val args = parts.drop(1)

        emit(TerminalLine(text = trimmed, type = TerminalLineType.STDIN))

        when (command.lowercase()) {
            "cd" -> {
                val targetPath = args.firstOrNull() ?: linuxEnv.homeDir.absolutePath
                val targetDir = resolveDirectory(targetPath, currentDir)
                if (targetDir.exists() && targetDir.isDirectory) {
                    onDirChanged(targetDir)
                } else {
                    emit(TerminalLine(text = "cd: no such file or directory: $targetPath", type = TerminalLineType.STDERR))
                }
            }
            "pwd" -> {
                emit(TerminalLine(text = currentDir.absolutePath, type = TerminalLineType.STDOUT))
            }
            "help" -> {
                emit(TerminalLine(text = getHelpText(), type = TerminalLineType.INFO))
            }
            "scope" -> {
                val scopeFile = File(linuxEnv.bugbountyDir, "SCOPE_NOTICE.txt")
                if (scopeFile.exists()) {
                    emit(TerminalLine(text = scopeFile.readText(), type = TerminalLineType.INFO))
                } else {
                    emit(TerminalLine(text = "Scope notice: Only test targets you have explicit authorization for.", type = TerminalLineType.WARNING))
                }
            }
            "motd" -> {
                val motdFile = File(linuxEnv.etcDir, "motd")
                if (motdFile.exists()) {
                    emit(TerminalLine(text = motdFile.readText(), type = TerminalLineType.INFO))
                }
            }
            "whois" -> {
                val host = args.firstOrNull()
                if (host.isNullOrBlank()) {
                    emit(TerminalLine(text = "Usage: whois <domain>", type = TerminalLineType.STDERR))
                } else {
                    emit(TerminalLine(text = "[*] Querying WHOIS directory for $host...", type = TerminalLineType.INFO))
                    val result = NetworkUtils.queryWhois(host)
                    emit(TerminalLine(text = result, type = TerminalLineType.STDOUT))
                }
            }
            "dig", "dns" -> {
                val host = args.firstOrNull()
                if (host.isNullOrBlank()) {
                    emit(TerminalLine(text = "Usage: dig <domain>", type = TerminalLineType.STDERR))
                } else {
                    emit(TerminalLine(text = "[*] Resolving DNS records for $host...", type = TerminalLineType.INFO))
                    val records = NetworkUtils.resolveDns(host)
                    records.forEach {
                        emit(TerminalLine(text = "${it.type.padEnd(12)} -> ${it.value}", type = TerminalLineType.STDOUT))
                    }
                }
            }
            "ping" -> {
                val host = args.firstOrNull()
                if (host.isNullOrBlank()) {
                    emit(TerminalLine(text = "Usage: ping <host>", type = TerminalLineType.STDERR))
                } else {
                    emit(TerminalLine(text = "[*] ICMP userspace reachability test: $host (CAP_NET_RAW fallback)", type = TerminalLineType.INFO))
                    val lines = NetworkUtils.checkPing(host, count = 3)
                    lines.forEach {
                        emit(TerminalLine(text = it, type = TerminalLineType.STDOUT))
                    }
                }
            }
            "nmap" -> {
                val host = args.lastOrNull { !it.startsWith("-") }
                if (host.isNullOrBlank()) {
                    emit(TerminalLine(text = "Usage: nmap [-sT] <host>", type = TerminalLineType.STDERR))
                    emit(TerminalLine(text = "[!] Notice: SYN scan (-sS) requires root. Using rootless TCP Connect mode.", type = TerminalLineType.WARNING))
                } else {
                    val isSynScan = args.any { it == "-sS" }
                    if (isSynScan) {
                        emit(TerminalLine(
                            text = "[!] SYN stealth scan (-sS) requires CAP_NET_RAW / root. Automatically falling back to rootless TCP connect scan (-sT).",
                            type = TerminalLineType.WARNING
                        ))
                    }
                    emit(TerminalLine(text = "Starting Nmap 7.94 (Userspace TCP Connect mode) at ${java.util.Date()}", type = TerminalLineType.INFO))
                    emit(TerminalLine(text = "Nmap scan report for $host", type = TerminalLineType.STDOUT))
                    emit(TerminalLine(text = "PORT     STATE SERVICE", type = TerminalLineType.INFO))
                    val scanResults = NetworkUtils.scanPorts(host)
                    scanResults.filter { it.isOpen }.forEach {
                        emit(TerminalLine(text = "${it.port}/tcp".padEnd(9) + "open  ${it.serviceName} (${it.latencyMs}ms)", type = TerminalLineType.SUCCESS))
                    }
                    val openCount = scanResults.count { it.isOpen }
                    val closedCount = scanResults.size - openCount
                    emit(TerminalLine(text = "Not shown: $closedCount closed ports", type = TerminalLineType.STDOUT))
                    emit(TerminalLine(text = "Nmap done: 1 IP address ($openCount open ports) scanned", type = TerminalLineType.INFO))
                }
            }
            "curl" -> {
                val url = args.lastOrNull { !it.startsWith("-") }
                if (url.isNullOrBlank()) {
                    emit(TerminalLine(text = "Usage: curl [options] <url>", type = TerminalLineType.STDERR))
                } else {
                    val fullUrl = NetworkUtils.normalizeUrl(url)
                    emit(TerminalLine(text = "[*] GET $fullUrl", type = TerminalLineType.INFO))
                    val res = NetworkUtils.executeHttpRequest(fullUrl)
                    if (res.isSimulated) {
                        emit(TerminalLine(text = "[Notice] Emulation Mode: Target sandbox response", type = TerminalLineType.WARNING))
                    }
                    if (args.contains("-i") || args.contains("-I")) {
                        emit(TerminalLine(text = "HTTP/1.1 ${res.statusCode}", type = TerminalLineType.INFO))
                        res.headers.forEach { (k, v) ->
                            emit(TerminalLine(text = "$k: $v", type = TerminalLineType.STDOUT))
                        }
                        emit(TerminalLine(text = "", type = TerminalLineType.STDOUT))
                    }
                    if (!args.contains("-I")) {
                        emit(TerminalLine(text = res.body.take(2000), type = TerminalLineType.STDOUT))
                        if (res.body.length > 2000) {
                            emit(TerminalLine(text = "\n[... truncated ${res.body.length - 2000} bytes ...]", type = TerminalLineType.INFO))
                        }
                    }
                }
            }
            "apktool" -> {
                if (args.isEmpty()) {
                    emit(TerminalLine(text = "Apktool v2.9.0 - Rootless APK Inspector\nUsage: apktool d <app.apk>", type = TerminalLineType.INFO))
                } else {
                    val apkPath = args.last()
                    var targetFile = resolveFile(apkPath, currentDir)
                    if (!targetFile.exists() && (apkPath == "base.apk" || apkPath.endsWith(".apk"))) {
                        if (File(linuxEnv.homeDir, "base.apk").exists()) {
                            targetFile = File(linuxEnv.homeDir, "base.apk")
                        } else if (File(linuxEnv.context.applicationInfo.sourceDir).exists()) {
                            targetFile = File(linuxEnv.context.applicationInfo.sourceDir)
                        }
                    }
                    emit(TerminalLine(text = "[*] Inspecting APK package: ${targetFile.name}...", type = TerminalLineType.INFO))
                    val report = ApkAnalyzer.analyzeApk(targetFile)
                    emit(TerminalLine(text = report.rawSummary, type = TerminalLineType.STDOUT))
                    if (report.securityFindings.isNotEmpty()) {
                        emit(TerminalLine(text = "Security Findings:", type = TerminalLineType.WARNING))
                        report.securityFindings.forEach {
                            emit(TerminalLine(text = " [${it.severity}] ${it.title}: ${it.description}", type = TerminalLineType.WARNING))
                        }
                    }
                }
            }
            "pkg", "apt" -> {
                val sub = args.firstOrNull() ?: "help"
                val subArgs = args.drop(1)
                val out = onPackageCommand(sub, subArgs)
                emit(TerminalLine(text = out, type = TerminalLineType.STDOUT))
            }
            "hashcheck" -> {
                val fileName = args.firstOrNull() ?: "hash.txt"
                var file = resolveFile(fileName, currentDir)
                if (!file.exists() && File(linuxEnv.homeDir, fileName).exists()) {
                    file = File(linuxEnv.homeDir, fileName)
                } else if (!file.exists() && File(linuxEnv.bugbountyDir, "SCOPE_NOTICE.txt").exists()) {
                    file = File(linuxEnv.bugbountyDir, "SCOPE_NOTICE.txt")
                }

                if (!file.exists() || file.isDirectory) {
                    emit(TerminalLine(text = "File not found: $fileName", type = TerminalLineType.STDERR))
                } else {
                    val bytes = file.readBytes()
                    emit(TerminalLine(text = "File: ${file.name} (${bytes.size} bytes)", type = TerminalLineType.INFO))
                    emit(TerminalLine(text = "MD5:    " + computeHash("MD5", bytes), type = TerminalLineType.STDOUT))
                    emit(TerminalLine(text = "SHA1:   " + computeHash("SHA-1", bytes), type = TerminalLineType.STDOUT))
                    emit(TerminalLine(text = "SHA256: " + computeHash("SHA-256", bytes), type = TerminalLineType.STDOUT))
                }
            }
            "nuclei" -> {
                ToolEngines.runNuclei(args, currentDir, linuxEnv).collect { emit(it) }
            }
            "nikto" -> {
                ToolEngines.runNikto(args, currentDir).collect { emit(it) }
            }
            "gobuster" -> {
                ToolEngines.runGobuster(args, currentDir).collect { emit(it) }
            }
            "sqlmap" -> {
                ToolEngines.runSqlmap(args, currentDir).collect { emit(it) }
            }
            "hydra" -> {
                ToolEngines.runHydra(args, currentDir).collect { emit(it) }
            }
            "john" -> {
                ToolEngines.runJohn(args, currentDir).collect { emit(it) }
            }
            "jadx" -> {
                ToolEngines.runJadx(args, currentDir).collect { emit(it) }
            }
            "wget" -> {
                ToolEngines.runWget(args, currentDir).collect { emit(it) }
            }
            "python3", "python" -> {
                ToolEngines.runPython(args, currentDir, linuxEnv).collect { emit(it) }
            }
            "nslookup", "host" -> {
                val host = args.firstOrNull()
                if (host.isNullOrBlank()) {
                    emit(TerminalLine(text = "Usage: $command <domain>", type = TerminalLineType.STDERR))
                } else {
                    emit(TerminalLine(text = "[*] Querying DNS for $host...", type = TerminalLineType.INFO))
                    val records = NetworkUtils.resolveDns(host)
                    records.forEach {
                        emit(TerminalLine(text = "${it.type.padEnd(12)} -> ${it.value}", type = TerminalLineType.STDOUT))
                    }
                }
            }
            "tcpdump" -> {
                emit(TerminalLine(text = "tcpdump: verbose output suppressed, use -v[v]... for full protocol decode", type = TerminalLineType.INFO))
                emit(TerminalLine(text = "[!] Rootless Sandbox Notice: AF_PACKET raw socket requires CAP_NET_RAW / root.", type = TerminalLineType.WARNING))
                emit(TerminalLine(text = "[*] Demonstrating userspace loopback packet capture (lo / 127.0.0.1)...", type = TerminalLineType.INFO))
                emit(TerminalLine(text = "12:00:01.102341 IP 127.0.0.1.53421 > 127.0.0.1.8080: Flags [S], seq 329182312, win 65535, length 0", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "12:00:01.102519 IP 127.0.0.1.8080 > 127.0.0.1.53421: Flags [S.], seq 412093812, ack 329182313, win 65535, length 0", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "12:00:01.102602 IP 127.0.0.1.53421 > 127.0.0.1.8080: Flags [.], ack 1, win 65535, length 0", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "3 packets captured, 3 packets received by filter, 0 packets dropped by kernel", type = TerminalLineType.SUCCESS))
            }
            "aircrack-ng" -> {
                emit(TerminalLine(text = "Aircrack-ng 1.7 (c) 2006-2026 Thomas d'Otreppe", type = TerminalLineType.INFO))
                emit(TerminalLine(text = "[!] Sandbox Notice: 802.11 monitor mode requires root & patched Wi-Fi kernel drivers.", type = TerminalLineType.WARNING))
                emit(TerminalLine(text = "[*] Use: 'aircrack-ng <capture.cap>' to audit recorded handshakes in userspace.", type = TerminalLineType.INFO))
            }
            "cleanup" -> {
                val freed = linuxEnv.cleanTempFiles()
                emit(TerminalLine(text = "Cleaned up temp directories. Freed ${freed / 1024} KB.", type = TerminalLineType.SUCCESS))
            }
            "env" -> {
                emit(TerminalLine(text = "PREFIX=${linuxEnv.usrDir.absolutePath}", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "HOME=${linuxEnv.homeDir.absolutePath}", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "TMPDIR=${linuxEnv.tmpDir.absolutePath}", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "PATH=${linuxEnv.binDir.absolutePath}:/system/bin", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "USER=kali", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "TERM=xterm-256color", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "KALI_ROOTLESS=1", type = TerminalLineType.STDOUT))
            }
            else -> {
                // Execute in system shell with our configured environment
                executeSystemShell(trimmed, currentDir).collect { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun executeSystemShell(command: String, currentDir: File): Flow<TerminalLine> = flow {
        var process: Process? = null
        try {
            val shPath = when {
                File("/system/bin/sh").exists() -> "/system/bin/sh"
                File("/bin/sh").exists() -> "/bin/sh"
                else -> "sh"
            }
            val pb = ProcessBuilder(shPath, "-c", command)
            pb.directory(currentDir)
            val env = pb.environment()
            env["PREFIX"] = linuxEnv.usrDir.absolutePath
            env["HOME"] = linuxEnv.homeDir.absolutePath
            env["TMPDIR"] = linuxEnv.tmpDir.absolutePath
            env["PATH"] = "${linuxEnv.binDir.absolutePath}:/system/bin:/system/xbin:${env["PATH"] ?: ""}"
            env["USER"] = "kali"
            env["TERM"] = "xterm-256color"
            env["KALI_ROOTLESS"] = "1"

            process = pb.start()

            val stdoutReader = process.inputStream.bufferedReader()
            val stderrReader = process.errorStream.bufferedReader()

            var line = stdoutReader.readLine()
            while (line != null) {
                emit(TerminalLine(text = line, type = TerminalLineType.STDOUT))
                line = stdoutReader.readLine()
            }

            var errLine = stderrReader.readLine()
            while (errLine != null) {
                emit(TerminalLine(text = errLine, type = TerminalLineType.STDERR))
                errLine = stderrReader.readLine()
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                emit(TerminalLine(text = "[Process exited with code $exitCode]", type = TerminalLineType.WARNING))
            }
        } catch (e: CancellationException) {
            process?.destroy()
            emit(TerminalLine(text = "^C (Terminated)", type = TerminalLineType.WARNING))
            throw e
        } catch (e: Exception) {
            emit(TerminalLine(text = "Shell error: ${e.localizedMessage}", type = TerminalLineType.STDERR))
        }
    }

    private fun resolveDirectory(path: String, currentDir: File): File {
        return when {
            path == "~" || path.startsWith("~/") -> {
                if (path == "~") linuxEnv.homeDir else File(linuxEnv.homeDir, path.removePrefix("~/"))
            }
            path.startsWith("/") -> File(path)
            else -> File(currentDir, path).canonicalFile
        }
    }

    private fun resolveFile(path: String, currentDir: File): File {
        return when {
            path.startsWith("~/") -> File(linuxEnv.homeDir, path.removePrefix("~/"))
            path.startsWith("/") -> File(path)
            else -> File(currentDir, path)
        }
    }

    private fun parseCommandArgs(cmd: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (ch in cmd) {
            if ((ch == '"' || ch == '\'') && !inQuotes) {
                inQuotes = true
                quoteChar = ch
            } else if (ch == quoteChar && inQuotes) {
                inQuotes = false
            } else if (ch == ' ' && !inQuotes) {
                if (sb.isNotEmpty()) {
                    result.add(sb.toString())
                    sb.clear()
                }
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
        return result
    }

    private fun computeHash(algorithm: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun tabComplete(prefix: String, currentDir: File): List<String> {
        val builtins = listOf(
            "help", "clear", "cd", "pwd", "ls", "scope", "motd", "whois",
            "dig", "ping", "nmap", "curl", "apktool", "pkg", "apt",
            "hashcheck", "cleanup", "env", "exit", "cat", "mkdir", "rm",
            "nuclei", "nikto", "gobuster", "sqlmap", "hydra", "john",
            "jadx", "wget", "python3", "nslookup", "host", "tcpdump", "aircrack-ng"
        )
        val matches = mutableListOf<String>()
        val trimmed = prefix.trim()

        if (!trimmed.contains(" ")) {
            // Complete command name
            matches.addAll(builtins.filter { it.startsWith(trimmed) })
        } else {
            // Complete file or directory name
            val lastArg = trimmed.substringAfterLast(" ")
            val searchDir = if (lastArg.startsWith("/")) File(lastArg).parentFile ?: currentDir else currentDir
            val query = lastArg.substringAfterLast("/")
            searchDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(query)) {
                    val completion = if (file.isDirectory) "${file.name}/" else file.name
                    matches.add(completion)
                }
            }
        }
        return matches.take(8)
    }

    private fun getHelpText(): String {
        return """
        Kali Linux Rootless Security Workstation Commands:
          nuclei -u <url>   Fast YAML-based vulnerability scanner
          nmap [-sT] <ip>   TCP connect port scanner
          nikto -h <host>   Web server security & misconfiguration scanner
          gobuster dir -u   Directory & path brute-forcing tool
          sqlmap -u <url>   SQL injection & parameter security auditor
          curl [-i] <url>   HTTP client & response header inspector
          wget <url>        Download files into working directory
          whois <domain>    Query WHOIS domain registration
          dig <domain>      Resolve DNS records (A, AAAA, MX, TXT)
          ping <host>       Userspace ICMP/TCP reachability test
          hydra -l <u> -p   Network login & authentication auditor
          john <hashfile>   John the Ripper password security auditor
          apktool d <apk>   Inspect APK manifest, permissions and risks
          jadx <file.apk>   Decompile DEX bytecode to Java structure
          hashcheck <file>  Calculate MD5, SHA1, and SHA256 checksums
          python3 [code]    Userspace Python 3 script runner & evaluator
          pkg [cmd]         Package manager (update, install, list, search)
          scope             Display authorized security testing scope
          motd              Display Kali Linux banner & system status
          cleanup           Free temporary cache & workspace storage
        """.trimIndent()
    }
}
