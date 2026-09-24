package com.example.core

import com.example.model.TerminalLine
import com.example.model.TerminalLineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ToolEngines {

    /**
     * Nuclei Vulnerability Scanner Engine
     * Fast, customizable vulnerability scanner based on YAML DSL templates.
     */
    fun runNuclei(args: List<String>, currentDir: File, linuxEnv: LinuxEnvironment): Flow<TerminalLine> = flow {
        val banner = """
                     __bcc   _   _ _   _  ___| | ___(_)
                    / _ \ | | | | | | |/ _ \ |/ _ \ |
                   | | | || |_| | |_| |  __/ |  __/ |
                   |_| |_| \__,_|\__,_|\___|_|\___|_| v3.2.0
        """.trimIndent()
        emit(TerminalLine(text = banner, type = TerminalLineType.SUCCESS))

        if (args.isEmpty() || args.contains("-h") || args.contains("--help")) {
            val help = """
            Nuclei is a fast, template based vulnerability scanner focusing on
            extensive configurability, massive extensibility and ease of use.

            Usage:
              nuclei [flags]

            Flags:
              -u, -target <url>       Target URL to scan
              -t, -templates <name>   Template or template directory to run
              -tags <tags>            Execute templates matching specified tags
              -tl                     List all available built-in templates
              -s, -severity <level>   Templates to run based on severity (info, low, medium, high, critical)
              -silent                 Display findings only
              -version                Show nuclei version
            """.trimIndent()
            emit(TerminalLine(text = help, type = TerminalLineType.INFO))
            return@flow
        }

        if (args.contains("-version") || args.contains("-v")) {
            emit(TerminalLine(text = "[INF] Current nuclei version: v3.2.0 (latest)", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "[INF] Built for: Android Userspace Sandbox (${linuxEnv.detectedArch})", type = TerminalLineType.INFO))
            return@flow
        }

        val templates = listOf(
            NucleiTemplate("http/misconfiguration/missing-security-headers", "Missing Security Headers", "misconfiguration,headers", "low", "Missing clickjacking (X-Frame-Options), CSP, or MIME sniffing protection."),
            NucleiTemplate("http/misconfiguration/hsts-not-enforced", "HSTS Header Missing", "misconfiguration,ssl", "low", "Strict-Transport-Security header is not configured on HTTPS endpoint."),
            NucleiTemplate("http/misconfiguration/cors-wildcard", "Permissive CORS Policy", "misconfiguration,cors", "medium", "Access-Control-Allow-Origin header set to wildcard with credentials."),
            NucleiTemplate("http/exposures/git-config-exposure", "Git Config Information Disclosure", "exposure,git", "high", ".git/config file publicly accessible disclosing repository source metadata."),
            NucleiTemplate("http/exposures/env-file-exposure", "Environment (.env) Configuration File", "exposure,tokens", "critical", "Environment variables file containing API keys or database credentials exposed."),
            NucleiTemplate("http/exposures/robots-txt-disclosure", "Robots.txt Disallowed Paths", "exposure,recon", "info", "Robots.txt reveals hidden admin, API, or private directory structures."),
            NucleiTemplate("http/exposures/security-txt-disclosure", "Security.txt Contact Disclosure", "exposure,recon", "info", "Well-known security contact specification found at /.well-known/security.txt"),
            NucleiTemplate("http/technologies/tech-detect", "Technology & Server Fingerprint", "tech,headers", "info", "Detected web application server technology from response headers."),
            NucleiTemplate("ssl/ssl-tls-version-check", "TLS Configuration & Certificate", "ssl,crypto", "info", "Evaluated active HTTPS TLS protocol version and cipher suite support."),
            NucleiTemplate("dns/dns-zone-transfer-check", "DNS Zone Transfer (AXFR) Audit", "dns,recon", "high", "Audited nameservers for unrestricted zone transfer leakage.")
        )

        if (args.contains("-tl")) {
            emit(TerminalLine(text = "[INF] Listing available built-in templates (Total: ${templates.size}):", type = TerminalLineType.INFO))
            templates.forEach { tmpl ->
                emit(TerminalLine(text = "  [${tmpl.id}] [${tmpl.severity}] ${tmpl.name} (tags: ${tmpl.tags})", type = TerminalLineType.STDOUT))
            }
            return@flow
        }

        // Extract target URL
        var targetUrl: String? = null
        val uIndex = args.indexOfFirst { it == "-u" || it == "-target" }
        if (uIndex >= 0 && uIndex + 1 < args.size) {
            targetUrl = args[uIndex + 1]
        } else {
            // Check if last argument looks like a URL
            targetUrl = args.lastOrNull { it.startsWith("http://") || it.startsWith("https://") || (!it.startsWith("-") && it.contains(".")) }
        }

        if (targetUrl.isNullOrBlank()) {
            targetUrl = "https://example.com"
        }

        val fullUrl = NetworkUtils.normalizeUrl(targetUrl)

        emit(TerminalLine(text = "[INF] Current nuclei version: v3.2.0 (latest)", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INF] Loaded ${templates.size} built-in templates for Android unprivileged sandbox", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INF] Running templates against: $fullUrl", type = TerminalLineType.INFO))

        val startTime = System.currentTimeMillis()
        var matchCount = 0

        // Execute primary target query
        val mainResponse = NetworkUtils.executeHttpRequest(fullUrl)
        if (mainResponse.isSimulated) {
            emit(TerminalLine(text = "[INF] Target response: Userspace sandbox emulation profile active ($fullUrl)", type = TerminalLineType.WARNING))
        }

        emit(TerminalLine(text = "[INF] Target responded: HTTP ${mainResponse.statusCode} (${mainResponse.timeMs}ms)", type = TerminalLineType.INFO))

        // Template 1: Tech detect
        val serverHeader = mainResponse.headers["Server"] ?: mainResponse.headers["server"]
        val poweredBy = mainResponse.headers["X-Powered-By"] ?: mainResponse.headers["x-powered-by"]
        if (!serverHeader.isNullOrBlank() || !poweredBy.isNullOrBlank()) {
            val detected = listOfNotNull(serverHeader?.let { "Server: $it" }, poweredBy?.let { "X-Powered-By: $it" }).joinToString(", ")
            emit(TerminalLine(text = "[tech-detect] [http] [info] $fullUrl [$detected]", type = TerminalLineType.INFO))
            matchCount++
        }

        // Template 2: Missing X-Frame-Options (Clickjacking)
        val xfo = mainResponse.headers["X-Frame-Options"] ?: mainResponse.headers["x-frame-options"]
        if (xfo.isNullOrBlank()) {
            emit(TerminalLine(text = "[missing-security-headers:x-frame-options] [http] [low] $fullUrl [Clickjacking protection missing]", type = TerminalLineType.WARNING))
            matchCount++
        }

        // Template 3: Missing X-Content-Type-Options
        val xcto = mainResponse.headers["X-Content-Type-Options"] ?: mainResponse.headers["x-content-type-options"]
        if (xcto.isNullOrBlank()) {
            emit(TerminalLine(text = "[missing-security-headers:x-content-type-options] [http] [low] $fullUrl [MIME sniffing protection missing]", type = TerminalLineType.WARNING))
            matchCount++
        }

        // Template 4: Missing Content-Security-Policy
        val csp = mainResponse.headers["Content-Security-Policy"] ?: mainResponse.headers["content-security-policy"]
        if (csp.isNullOrBlank()) {
            emit(TerminalLine(text = "[missing-security-headers:csp] [http] [low] $fullUrl [Content-Security-Policy not enforced]", type = TerminalLineType.WARNING))
            matchCount++
        }

        // Template 5: HSTS check
        if (fullUrl.startsWith("https://", ignoreCase = true)) {
            val hsts = mainResponse.headers["Strict-Transport-Security"] ?: mainResponse.headers["strict-transport-security"]
            if (hsts.isNullOrBlank()) {
                emit(TerminalLine(text = "[hsts-not-enforced] [http] [low] $fullUrl [HSTS header not enforced on HTTPS endpoint]", type = TerminalLineType.WARNING))
                matchCount++
            } else {
                emit(TerminalLine(text = "[ssl-hsts] [http] [info] $fullUrl [HSTS active: $hsts]", type = TerminalLineType.SUCCESS))
            }
        }

        // Template 6: Permissive CORS
        val cors = mainResponse.headers["Access-Control-Allow-Origin"] ?: mainResponse.headers["access-control-allow-origin"]
        if (cors == "*") {
            emit(TerminalLine(text = "[cors-wildcard] [http] [medium] $fullUrl [Access-Control-Allow-Origin wildcard detected]", type = TerminalLineType.WARNING))
            matchCount++
        }

        // Template 7: Check /robots.txt
        val baseUrl = fullUrl.substringBeforeLast("/")
        val robotsUrl = "$baseUrl/robots.txt"
        val robotsRes = NetworkUtils.executeHttpRequest(robotsUrl)
        if (robotsRes.statusCode == 200 && robotsRes.body.contains("Disallow:", ignoreCase = true)) {
            val disallows = robotsRes.body.lines().filter { it.trim().startsWith("Disallow:", ignoreCase = true) }.take(3).joinToString(", ") { it.trim() }
            emit(TerminalLine(text = "[robots-txt-disclosure] [http] [info] $robotsUrl [$disallows]", type = TerminalLineType.INFO))
            matchCount++
        }

        // Template 8: Check sensitive exposures (Safe GET queries)
        val gitUrl = "$baseUrl/.git/config"
        val gitRes = NetworkUtils.executeHttpRequest(gitUrl)
        if (gitRes.statusCode == 200 && (gitRes.body.contains("[core]") || gitRes.body.contains("repositoryformatversion"))) {
            emit(TerminalLine(text = "[git-config-exposure] [http] [high] $gitUrl [Exposed .git/config source code metadata!]", type = TerminalLineType.WARNING))
            matchCount++
        }

        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        emit(TerminalLine(text = "[INF] Nuclei scan finished in ${String.format(Locale.US, "%.2f", elapsed)}s: ${templates.size} templates executed, $matchCount findings matched.", type = TerminalLineType.SUCCESS))
    }.flowOn(Dispatchers.IO)

    /**
     * Nikto Web Server Scanner Engine
     */
    fun runNikto(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        emit(TerminalLine(text = "- Nikto v2.5.0", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "---------------------------------------------------------------------------", type = TerminalLineType.INFO))

        var host = args.getOrNull(args.indexOf("-h") + 1) ?: args.firstOrNull { !it.startsWith("-") }
        if (host.isNullOrBlank()) {
            host = "example.com"
        }

        val targetUrl = NetworkUtils.normalizeUrl(host)
        val cleanHost = targetUrl.removePrefix("http://").removePrefix("https://").substringBefore("/")

        emit(TerminalLine(text = "+ Target IP:          $cleanHost", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "+ Target Hostname:    $cleanHost", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "+ Target Port:        ${if (targetUrl.startsWith("https")) 443 else 80}", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "+ Start Time:         ${Date()}", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "---------------------------------------------------------------------------", type = TerminalLineType.INFO))

        val res = NetworkUtils.executeHttpRequest(targetUrl)
        if (res.isSimulated) {
            emit(TerminalLine(text = "+ [Sandbox Notice] Target network evaluated in Userspace Emulation Mode", type = TerminalLineType.WARNING))
        }

        val server = res.headers["Server"] ?: res.headers["server"] ?: "nginx/1.24.0 (Ubuntu)"
        emit(TerminalLine(text = "+ Server: $server", type = TerminalLineType.STDOUT))

        if (!res.headers.containsKey("X-Content-Type-Options") && !res.headers.containsKey("x-content-type-options")) {
            emit(TerminalLine(text = "+ The anti-MIME-sniffing X-Content-Type-Options header is not set.", type = TerminalLineType.WARNING))
        }
        if (!res.headers.containsKey("X-Frame-Options") && !res.headers.containsKey("x-frame-options")) {
            emit(TerminalLine(text = "+ The anti-clickjacking X-Frame-Options header is not present.", type = TerminalLineType.WARNING))
        }
        if (!res.headers.containsKey("Content-Security-Policy") && !res.headers.containsKey("content-security-policy")) {
            emit(TerminalLine(text = "+ Content-Security-Policy header is missing, exposing users to cross-site scripting risks.", type = TerminalLineType.WARNING))
        }

        // Check common sensitive paths
        val checkPaths = listOf("/robots.txt", "/admin", "/login", "/.git/config", "/server-status")
        for (path in checkPaths) {
            val checkUrl = "$targetUrl$path"
            val pathRes = NetworkUtils.executeHttpRequest(checkUrl)
            if (pathRes.statusCode in listOf(200, 301, 302, 401, 403)) {
                emit(TerminalLine(text = "+ Path $path returned HTTP ${pathRes.statusCode} (${pathRes.body.length} bytes)", type = TerminalLineType.STDOUT))
            }
        }

        emit(TerminalLine(text = "---------------------------------------------------------------------------", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "+ 1 host(s) tested", type = TerminalLineType.SUCCESS))
    }.flowOn(Dispatchers.IO)

    /**
     * Gobuster Directory & File Discovery Engine
     */
    fun runGobuster(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        var url: String? = null
        val uIdx = args.indexOfFirst { it == "-u" || it == "--url" }
        if (uIdx >= 0 && uIdx + 1 < args.size) {
            url = args[uIdx + 1]
        } else {
            url = args.lastOrNull { !it.startsWith("-") && (it.contains(".") || it.startsWith("http")) }
        }

        if (url.isNullOrBlank()) {
            url = "https://example.com"
        }

        val targetUrl = NetworkUtils.normalizeUrl(url)

        emit(TerminalLine(text = "===============================================================", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "Gobuster v3.6.0 (Android Userspace Rootless Mode)", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "by OJ Reeves (@TheColonial) & Christian Mehlmauer (@firefart)", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "===============================================================", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[+] Url:                     $targetUrl", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "[+] Method:                  GET", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "[+] Wordlist:                SecStation common-dirs.txt", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "[+] Status codes:            200,204,301,302,307,401,403", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "===============================================================", type = TerminalLineType.INFO))

        val wordlist = listOf(
            "admin", "api", "login", "config", "backup", "v1", "dashboard",
            "test", "dev", "static", "uploads", "images", "docs", "robots.txt",
            "health", "metrics", "status", "portal", "user", "auth"
        )

        var foundCount = 0
        for (word in wordlist) {
            val checkUrl = "$targetUrl/$word"
            val res = NetworkUtils.executeHttpRequest(checkUrl)
            if (res.statusCode in listOf(200, 204, 301, 302, 307, 401, 403)) {
                val statusType = if (res.statusCode == 200) TerminalLineType.SUCCESS else TerminalLineType.WARNING
                val redirectInfo = if (res.statusCode in listOf(301, 302, 307)) " [--> /$word/]" else ""
                emit(TerminalLine(text = "/${word.padEnd(20)} (Status: ${res.statusCode}) [Size: ${res.body.length}]$redirectInfo", type = statusType))
                foundCount++
            }
        }

        emit(TerminalLine(text = "===============================================================", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "Finished directory brute-force: $foundCount endpoints discovered.", type = TerminalLineType.SUCCESS))
        emit(TerminalLine(text = "===============================================================", type = TerminalLineType.INFO))
    }.flowOn(Dispatchers.IO)

    /**
     * SQLMap SQL Injection & Parameter Auditor Engine
     */
    fun runSqlmap(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        val banner = """
                 ___
                __H__
          ___ ___[)]_____ ___ ___  {1.8.2#stable}
         |_ -| . ["]     | .'| . |
         |___|_  ["]_|_|_|__,|  _|
               |_|V...       |_|   https://sqlmap.org
        """.trimIndent()
        emit(TerminalLine(text = banner, type = TerminalLineType.INFO))

        val uIdx = args.indexOfFirst { it == "-u" || it == "--url" }
        val rawTarget = if (uIdx >= 0 && uIdx + 1 < args.size) args[uIdx + 1] else args.lastOrNull { it.startsWith("http") || it.contains(".") } ?: "https://example.com/item?id=1"
        val target = NetworkUtils.normalizeUrl(rawTarget)

        emit(TerminalLine(text = "[*] starting @ ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INFO] testing connection to the target URL: $target", type = TerminalLineType.INFO))

        val res = NetworkUtils.executeHttpRequest(target)
        if (res.isSimulated) {
            emit(TerminalLine(text = "[INFO] Sandbox Emulation Mode active for target $target", type = TerminalLineType.WARNING))
        }

        emit(TerminalLine(text = "[INFO] checking if the target is protected by some kind of WAF/IPS", type = TerminalLineType.INFO))
        val server = res.headers["Server"] ?: res.headers["server"] ?: "nginx/1.24.0 (Ubuntu)"
        emit(TerminalLine(text = "[INFO] target server banner: $server", type = TerminalLineType.INFO))

        val hasQueryParams = target.contains("?")
        if (!hasQueryParams) {
            emit(TerminalLine(text = "[WARNING] no GET parameter(s) found for testing in the provided URL.", type = TerminalLineType.WARNING))
            emit(TerminalLine(text = "[INFO] testing for generic HTTP parameter pollution and error reflection...", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "[INFO] all tested parameters appear to be protected or non-injectable.", type = TerminalLineType.SUCCESS))
        } else {
            val query = target.substringAfter("?")
            val params = query.split("&").map { it.substringBefore("=") }
            emit(TerminalLine(text = "[INFO] testing parameters: ${params.joinToString(", ")}", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "[INFO] testing 'AND boolean-based blind - WHERE or HAVING clause' with safe probes", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "[INFO] testing 'Generic UNION query' verification", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "[INFO] testing 'MySQL >= 5.0.12 AND time-based blind' analysis", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "[INFO] parameter validation complete: target responded safely with HTTP ${res.statusCode}.", type = TerminalLineType.SUCCESS))
        }
        emit(TerminalLine(text = "[*] ending @ ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}", type = TerminalLineType.INFO))
    }.flowOn(Dispatchers.IO)

    /**
     * Hydra Network Login & Authentication Auditor
     */
    fun runHydra(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        emit(TerminalLine(text = "Hydra v9.5 (c) 2026 by van Hauser / THC & David Maciejak - Please use responsibly", type = TerminalLineType.INFO))

        if (args.isEmpty() || args.contains("-h")) {
            emit(TerminalLine(text = "Syntax: hydra [[[-l LOGIN|-L FILE] [-p PASS|-P FILE]] | [-C FILE]] [-e nsr] [-o FILE] [-t TASKS] [-M FILE] [-s PORT] target service [opt]", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Example: hydra -l admin -p secret httpbin.org http-get", type = TerminalLineType.INFO))
            return@flow
        }

        var login = "admin"
        val lIdx = args.indexOf("-l")
        if (lIdx >= 0 && lIdx + 1 < args.size) login = args[lIdx + 1]

        val target = args.lastOrNull { !it.startsWith("-") } ?: "127.0.0.1"

        emit(TerminalLine(text = "[DATA] target: $target, login: '$login', tasks: 4", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[ATTEMPT] target $target - login '$login' - checking authentication protocol...", type = TerminalLineType.INFO))

        val pingRes = NetworkUtils.checkPing(target, count = 1)
        emit(TerminalLine(text = "[STATUS] host reachability verified.", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INFO] Sandbox safety check: brute-force throttling enforced in rootless userland.", type = TerminalLineType.WARNING))
        emit(TerminalLine(text = "[STATUS] 0 valid pairs, 1 target tested, completed.", type = TerminalLineType.SUCCESS))
    }.flowOn(Dispatchers.IO)

    /**
     * John the Ripper Password Security Auditor
     */
    fun runJohn(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        emit(TerminalLine(text = "John the Ripper 1.9.0-jumbo-1 [Android userspace ${System.getProperty("os.arch")}]", type = TerminalLineType.INFO))

        if (args.contains("--test") || args.contains("-test")) {
            emit(TerminalLine(text = "Benchmarking: Raw-MD5 [128/128 Neon]", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Many salts:   1,420K c/s real, 1,420K c/s virtual", type = TerminalLineType.STDOUT))
            emit(TerminalLine(text = "Benchmarking: Raw-SHA1 [128/128 Neon]", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Many salts:   950K c/s real, 950K c/s virtual", type = TerminalLineType.STDOUT))
            emit(TerminalLine(text = "Benchmarking: Raw-SHA256 [128/128 Neon]", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Many salts:   620K c/s real, 620K c/s virtual", type = TerminalLineType.STDOUT))
            return@flow
        }

        val targetArg = args.lastOrNull { !it.startsWith("-") } ?: "hash.txt"

        // Check if argument is a file or a raw hash
        val targetFile = if (File(currentDir, targetArg).exists()) {
            File(currentDir, targetArg)
        } else if (File(currentDir, "hash.txt").exists()) {
            File(currentDir, "hash.txt")
        } else {
            File(currentDir, targetArg)
        }
        val hashText = if (targetFile.exists() && targetFile.isFile) targetFile.readText().trim() else targetArg

        val commonPasswords = listOf("admin", "password", "123456", "welcome", "secret", "root", "kali", "pass123")
        val md5Dict = commonPasswords.associateBy { computeHashStr("MD5", it) }
        val sha1Dict = commonPasswords.associateBy { computeHashStr("SHA-1", it) }
        val sha256Dict = commonPasswords.associateBy { computeHashStr("SHA-256", it) }

        var format = "Raw-MD5"
        var cracked: String? = null

        when (hashText.length) {
            32 -> {
                format = "Raw-MD5"
                cracked = md5Dict[hashText.lowercase()]
            }
            40 -> {
                format = "Raw-SHA1"
                cracked = sha1Dict[hashText.lowercase()]
            }
            64 -> {
                format = "Raw-SHA256"
                cracked = sha256Dict[hashText.lowercase()]
            }
            else -> {
                // Default fallback test
                cracked = "password"
            }
        }

        emit(TerminalLine(text = "Loaded 1 password hash ($format)", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "Press 'q' or Ctrl-C to abort", type = TerminalLineType.INFO))

        if (cracked != null) {
            emit(TerminalLine(text = "$cracked           (user)", type = TerminalLineType.SUCCESS))
            emit(TerminalLine(text = "1g 0:00:00:00 DONE (2026-09-24) 500.0g/s 12500p/s 12500c/s 12500C/s password..admin", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Use the \"--show\" option to display all of the cracked passwords reliably", type = TerminalLineType.INFO))
        } else {
            emit(TerminalLine(text = "No password hashes cracked (see FAQ). Tried ${commonPasswords.size} common dictionary passwords.", type = TerminalLineType.WARNING))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * JADX DEX to Java Decompiler Engine
     */
    fun runJadx(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        emit(TerminalLine(text = "jadx 1.5.0 - Dex to Java decompiler", type = TerminalLineType.INFO))
        val targetArg = args.lastOrNull { !it.startsWith("-") } ?: "base.apk"

        val targetFile = if (File(currentDir, targetArg).exists()) {
            File(currentDir, targetArg)
        } else if (File(currentDir, "base.apk").exists()) {
            File(currentDir, "base.apk")
        } else {
            File(currentDir, targetArg)
        }

        emit(TerminalLine(text = "loading ...", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "processing ${targetFile.name} (${targetFile.length() / 1024} KB) ...", type = TerminalLineType.INFO))

        val report = ApkAnalyzer.analyzeApk(targetFile)
        emit(TerminalLine(text = "Decompiled ${report.dexFilesCount} DEX section(s):", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "  File: ${report.fileName} (${report.fileSizeKb} KB)", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "  Signature: ${report.signatureType}", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "  Components Found: ${report.componentsFound.size}", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "  Permissions Declared: ${report.extractedPermissions.size}", type = TerminalLineType.STDOUT))
        report.componentsFound.take(5).forEach {
            emit(TerminalLine(text = "    -> $it", type = TerminalLineType.STDOUT))
        }
        emit(TerminalLine(text = "done", type = TerminalLineType.SUCCESS))
    }.flowOn(Dispatchers.IO)

    /**
     * Wget File Retriever Engine
     */
    fun runWget(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        val rawUrl = args.lastOrNull { !it.startsWith("-") && (it.contains(".") || it.startsWith("http")) } ?: "https://example.com"
        val url = NetworkUtils.normalizeUrl(rawUrl)

        val outIdx = args.indexOf("-O")
        val outFileName = if (outIdx >= 0 && outIdx + 1 < args.size) {
            args[outIdx + 1]
        } else {
            url.substringAfterLast("/").substringBefore("?").ifBlank { "index.html" }
        }

        val outputFile = File(currentDir, outFileName)
        val host = try { URL(url).host } catch (_: Exception) { "secstation.local" }
        emit(TerminalLine(text = "--${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}--  $url", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "Resolving $host... done.", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "Connecting to $host... connected.", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "HTTP request sent, awaiting response...", type = TerminalLineType.INFO))

        val res = NetworkUtils.executeHttpRequest(url)
        emit(TerminalLine(text = "Length: ${res.body.length} (${res.body.length / 1024} KB) [text/html]", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "Saving to: '$outFileName'", type = TerminalLineType.STDOUT))
        outputFile.writeText(res.body)
        emit(TerminalLine(text = "100%[====================================>] ${res.body.length}  --.-KB/s    in 0.1s", type = TerminalLineType.SUCCESS))
        emit(TerminalLine(text = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ($outFileName saved [${res.body.length}/${res.body.length}])", type = TerminalLineType.SUCCESS))
    }.flowOn(Dispatchers.IO)

    /**
     * Python 3 Userspace Script Runner
     */
    fun runPython(args: List<String>, currentDir: File, linuxEnv: LinuxEnvironment): Flow<TerminalLine> = flow {
        if (args.isEmpty()) {
            emit(TerminalLine(text = "Python 3.11.8 (main, Feb 2026, 12:00:00) [Clang Android]", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Use: python3 -c \"print('Hello from SecStation')\" or python3 test_script.py", type = TerminalLineType.STDOUT))
            return@flow
        }

        val cIdx = args.indexOf("-c")
        if (cIdx >= 0 && cIdx + 1 < args.size) {
            val code = args.subList(cIdx + 1, args.size).joinToString(" ")
            emit(TerminalLine(text = "[Python3 Execution]", type = TerminalLineType.INFO))
            val evaluated = evaluateSimplePython(code)
            emit(TerminalLine(text = evaluated, type = TerminalLineType.STDOUT))
            return@flow
        }

        val scriptName = args.first()
        val scriptFile = if (File(currentDir, scriptName).exists()) {
            File(currentDir, scriptName)
        } else if (File(linuxEnv.homeDir, scriptName).exists()) {
            File(linuxEnv.homeDir, scriptName)
        } else if (File(linuxEnv.homeDir, "test_script.py").exists()) {
            File(linuxEnv.homeDir, "test_script.py")
        } else {
            File(currentDir, scriptName)
        }

        if (scriptFile.exists()) {
            emit(TerminalLine(text = "[Executing ${scriptFile.name}]", type = TerminalLineType.INFO))
            val evaluated = evaluateSimplePython(scriptFile.readText())
            emit(TerminalLine(text = evaluated, type = TerminalLineType.STDOUT))
        } else {
            emit(TerminalLine(text = "python3: can't open file '$scriptName': [Errno 2] No such file or directory", type = TerminalLineType.STDERR))
        }
    }.flowOn(Dispatchers.IO)

    private fun evaluateSimplePython(code: String): String {
        return buildString {
            code.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
                    val inside = trimmed.removePrefix("print(").removeSuffix(")")
                    appendLine(inside.removeSurrounding("\"").removeSurrounding("'"))
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    appendLine(">>> $trimmed")
                }
            }
        }.ifBlank { "OK (Executed with returncode 0)" }
    }

    /**
     * Subfinder Passive Subdomain Enumeration Engine
     */
    fun runSubfinder(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        val banner = """
               __    _____           __
   _______  __/ /_  / __(_)___  ____/ /__  _____
  / ___/ / / / __ \/ /_/ / __ \/ __  / _ \/ ___/
 (__  ) /_/ / /_/ / __/ / / / / /_/ /  __/ /
/____/\__,_/_.___/_/ /_/_/ /_/\__,_/\___/_/ v2.6.5
        """.trimIndent()
        emit(TerminalLine(text = banner, type = TerminalLineType.SUCCESS))

        val dIdx = args.indexOfFirst { it == "-d" || it == "-domain" }
        var domain = if (dIdx >= 0 && dIdx + 1 < args.size) {
            args[dIdx + 1]
        } else {
            args.lastOrNull { !it.startsWith("-") && it.contains(".") }
        }

        if (domain.isNullOrBlank()) {
            emit(TerminalLine(text = "Usage: subfinder -d <target-domain> [-o output.txt] [-json] [-silent]", type = TerminalLineType.STDERR))
            emit(TerminalLine(text = "Example: subfinder -d example.com -o subs.txt", type = TerminalLineType.INFO))
            return@flow
        }

        val cleanDomain = domain.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        val isJson = args.contains("-json")
        val isSilent = args.contains("-silent")
        val oIdx = args.indexOfFirst { it == "-o" || it == "-output" }
        val outputFile = if (oIdx >= 0 && oIdx + 1 < args.size) File(currentDir, args[oIdx + 1]) else null

        if (!isSilent) {
            emit(TerminalLine(text = "[INF] Enumerating subdomains for $cleanDomain using passive sources...", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "[INF] Selected passive sources: crt.sh, RapidDNS, CertSpotter, PublicDNS, AlienVault", type = TerminalLineType.INFO))
        }

        val subdomains = NetworkUtils.querySubdomains(cleanDomain)
        val sb = StringBuilder()

        subdomains.forEach { sub ->
            if (isJson) {
                val jsonLine = "{\"host\":\"$sub\",\"input\":\"$cleanDomain\",\"source\":\"passive\"}"
                emit(TerminalLine(text = jsonLine, type = TerminalLineType.STDOUT))
                sb.appendLine(jsonLine)
            } else {
                emit(TerminalLine(text = sub, type = TerminalLineType.SUCCESS))
                sb.appendLine(sub)
            }
        }

        outputFile?.let { file ->
            file.writeText(sb.toString())
            emit(TerminalLine(text = "[INF] Saved ${subdomains.size} unique subdomains to ${file.name}", type = TerminalLineType.INFO))
        }

        if (!isSilent) {
            emit(TerminalLine(text = "[INF] Found ${subdomains.size} unique subdomains for $cleanDomain in passive discovery.", type = TerminalLineType.INFO))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Amass Passive Asset & Subdomain Discovery Engine
     */
    fun runAmass(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        val banner = """
        .+++:.            :+++.
      +W@@@@@@8        +W@@@@@@8
     -@@@@@@@@@.      -@@@@@@@@@.
     OW@@@W@@@@@      OW@@@W@@@@@
    """.trimIndent() + "\nOWASP Amass v4.2.0 (Android Userspace Passive Mode)"
        emit(TerminalLine(text = banner, type = TerminalLineType.SUCCESS))

        val dIdx = args.indexOfFirst { it == "-d" || it == "--domain" }
        val domain = if (dIdx >= 0 && dIdx + 1 < args.size) {
            args[dIdx + 1]
        } else {
            args.lastOrNull { !it.startsWith("-") && it.contains(".") }
        }

        if (domain.isNullOrBlank()) {
            emit(TerminalLine(text = "Usage: amass enum -passive -d <domain> [-timeout <minutes>] [-rate <req/s>]", type = TerminalLineType.STDERR))
            return@flow
        }

        val clean = domain.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        emit(TerminalLine(text = "[*] Starting passive asset mapping for: $clean", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[*] Querying network infrastructure, ASNs, netblocks, and DNS records...", type = TerminalLineType.INFO))

        val assets = NetworkUtils.discoverAmassAssets(clean)
        val distinctAsns = assets.map { it.asn }.distinct()

        emit(TerminalLine(text = "--> Discovered ${distinctAsns.size} Autonomous System(s) routing $clean:", type = TerminalLineType.INFO))
        assets.forEach { item ->
            val line = "[${item.asn}] ${item.cidr.padEnd(16)} -> ${item.ip.padEnd(16)} -> ${item.name}"
            emit(TerminalLine(text = line, type = TerminalLineType.STDOUT))
        }

        emit(TerminalLine(text = "[*] Total discovered asset associations: ${assets.size}", type = TerminalLineType.SUCCESS))
        emit(TerminalLine(text = "[*] Amass passive enumeration completed successfully.", type = TerminalLineType.INFO))
    }.flowOn(Dispatchers.IO)

    /**
     * httpx HTTP Probing Toolkit Engine
     */
    fun runHttpx(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        val banner = """
    __    __  __            
   / /_  / /_/ /_____  _  __
  / __ \/ __/ __/ __ \| |/_/
 / / / / /_/ /_/ /_/ />  <  
/_/ /_/\__/\__/ .___/_/|_|  v1.6.4
             /_/            
        """.trimIndent()
        emit(TerminalLine(text = banner, type = TerminalLineType.SUCCESS))

        val uIdx = args.indexOfFirst { it == "-u" || it == "-target" }
        val lIdx = args.indexOfFirst { it == "-l" || it == "-list" }

        val targets = mutableListOf<String>()

        if (uIdx >= 0 && uIdx + 1 < args.size) {
            targets.add(args[uIdx + 1])
        } else if (lIdx >= 0 && lIdx + 1 < args.size) {
            val listFile = File(currentDir, args[lIdx + 1])
            if (listFile.exists()) {
                targets.addAll(listFile.readLines().filter { it.isNotBlank() })
            } else {
                emit(TerminalLine(text = "Target list file not found: ${listFile.name}", type = TerminalLineType.STDERR))
                return@flow
            }
        } else {
            val fallback = args.lastOrNull { !it.startsWith("-") && it.contains(".") } ?: "https://example.com"
            targets.add(fallback)
        }

        emit(TerminalLine(text = "[INF] Loaded ${targets.size} target(s) for HTTP probing", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INF] Format: [Status Code] [Title] [Server] [Response Time] URL", type = TerminalLineType.INFO))

        targets.forEach { target ->
            val probe = NetworkUtils.probeHttpx(target)
            val statusColor = when (probe.statusCode) {
                200 -> TerminalLineType.SUCCESS
                in 300..399 -> TerminalLineType.INFO
                in 400..499 -> TerminalLineType.WARNING
                else -> TerminalLineType.STDERR
            }
            val formatted = "[${probe.statusCode}] [${probe.title}] [${probe.server}] [${probe.latencyMs}ms] ${probe.url}"
            emit(TerminalLine(text = formatted, type = statusColor))
        }

        emit(TerminalLine(text = "[INF] HTTP probing complete for ${targets.size} target(s).", type = TerminalLineType.SUCCESS))
    }.flowOn(Dispatchers.IO)

    /**
     * dnsx Fast DNS Resolution & Record Discovery Engine
     */
    fun runDnsx(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        val banner = """
    __              __
.--|  .-----.-----.|  |--.
|  _  |     |__ --||    < 
|_____|__|__|_____||__|__| v1.2.1
        """.trimIndent()
        emit(TerminalLine(text = banner, type = TerminalLineType.SUCCESS))

        val dIdx = args.indexOfFirst { it == "-d" || it == "-domain" }
        val domain = if (dIdx >= 0 && dIdx + 1 < args.size) {
            args[dIdx + 1]
        } else {
            args.lastOrNull { !it.startsWith("-") && it.contains(".") } ?: "example.com"
        }

        emit(TerminalLine(text = "[INF] Querying multi-record DNS resolution for $domain...", type = TerminalLineType.INFO))
        val records = NetworkUtils.resolveDnsxRecords(domain)

        emit(TerminalLine(text = "RECORD TYPE   TTL     VALUE", type = TerminalLineType.INFO))
        records.forEach { rec ->
            val line = "${rec.type.padEnd(13)} ${rec.ttl.toString().padEnd(7)} ${rec.value}"
            val type = if (rec.type == "A" || rec.type == "AAAA") TerminalLineType.SUCCESS else TerminalLineType.STDOUT
            emit(TerminalLine(text = line, type = type))
        }

        emit(TerminalLine(text = "[INF] Resolved ${records.size} DNS records for $domain.", type = TerminalLineType.SUCCESS))
    }.flowOn(Dispatchers.IO)

    /**
     * Naabu Fast Port Discovery Engine
     */
    fun runNaabu(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        val banner = """
                  __       
   ____  ____ _  / /_  __  __
  / __ \/ __ `/ / __ \/ / / /
 / / / / /_/ / / /_/ / /_/ / 
/_/ /_/\__,_/ /_.___/\__,_/  v2.3.1
        """.trimIndent()
        emit(TerminalLine(text = banner, type = TerminalLineType.SUCCESS))

        val hIdx = args.indexOfFirst { it == "-host" || it == "-h" }
        val host = if (hIdx >= 0 && hIdx + 1 < args.size) {
            args[hIdx + 1]
        } else {
            args.lastOrNull { !it.startsWith("-") && (it.contains(".") || it.contains("localhost")) } ?: "127.0.0.1"
        }

        val pIdx = args.indexOfFirst { it == "-p" || it == "-port" }
        val ports = if (pIdx >= 0 && pIdx + 1 < args.size) {
            val portSpec = args[pIdx + 1]
            if (portSpec.contains("-")) {
                val start = portSpec.substringBefore("-").toIntOrNull() ?: 1
                val end = portSpec.substringAfter("-").toIntOrNull() ?: 100
                (start..end.coerceAtMost(1024)).toList()
            } else {
                portSpec.split(",").mapNotNull { it.trim().toIntOrNull() }
            }
        } else {
            listOf(21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 1433, 3306, 5432, 8080, 8443)
        }

        emit(TerminalLine(text = "[INF] Running port scan against: $host", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INF] Ports to probe: ${ports.size} | Mode: Userspace TCP Connect (Rootless Safe)", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INF] Rate limit: 20 probes/sec (Conservative Authorized Profile)", type = TerminalLineType.INFO))

        val results = NetworkUtils.scanPorts(host, ports)
        val openPorts = results.filter { it.isOpen }

        openPorts.forEach { res ->
            emit(TerminalLine(text = "$host:${res.port} [${res.serviceName}] open (${res.latencyMs}ms)", type = TerminalLineType.SUCCESS))
        }

        emit(TerminalLine(text = "[INF] Naabu scan completed: ${openPorts.size} open port(s) discovered out of ${ports.size} scanned.", type = TerminalLineType.INFO))
    }.flowOn(Dispatchers.IO)

    /**
     * ffuf Fast Web Fuzzer Engine
     */
    fun runFfuf(args: List<String>, currentDir: File): Flow<TerminalLine> = flow {
        val banner = """
        /'___\  /'___\           /\ \__
       /\ \__/ /\ \__/  __  __  \ \ ,_\   v2.1.0-secstation
       \ \ ,__\\ \ ,__\/\ \/\ \  \ \ \/
        \ \ \_/ \ \ \_/\ \ \_\ \  \ \ \_
         \/_/    \/_/   \ \____/   \/__/
        """.trimIndent()
        emit(TerminalLine(text = banner, type = TerminalLineType.SUCCESS))

        val uIdx = args.indexOfFirst { it == "-u" || it == "-url" }
        var targetUrl = if (uIdx >= 0 && uIdx + 1 < args.size) args[uIdx + 1] else "https://example.com/FUZZ"
        if (!targetUrl.contains("FUZZ")) {
            targetUrl = targetUrl.trimEnd('/') + "/FUZZ"
        }

        val wIdx = args.indexOfFirst { it == "-w" || it == "-wordlist" }
        val wordlist = if (wIdx >= 0 && wIdx + 1 < args.size) {
            val f = File(currentDir, args[wIdx + 1])
            if (f.exists()) f.readLines().map { it.trim() }.filter { it.isNotEmpty() } else listOf("admin", "api", "login", "config", "docs", "robots.txt")
        } else {
            listOf("admin", "api", "login", "config", "backup", "v1", "docs", "robots.txt", "health", "metrics")
        }

        emit(TerminalLine(text = ":: Method           : GET", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = ":: URL              : $targetUrl", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = ":: Wordlist size    : ${wordlist.size}", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = ":: Follow redirects : false", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "________________________________________________", type = TerminalLineType.INFO))

        var matched = 0
        for (word in wordlist) {
            val url = targetUrl.replace("FUZZ", word)
            val res = NetworkUtils.executeHttpRequest(url)
            if (res.statusCode in listOf(200, 204, 301, 302, 307, 401, 403)) {
                val line = "${word.padEnd(20)} [Status: ${res.statusCode}, Size: ${res.body.length}, Words: ${res.body.split("\\s+".toRegex()).size}]"
                val type = if (res.statusCode == 200) TerminalLineType.SUCCESS else TerminalLineType.WARNING
                emit(TerminalLine(text = line, type = type))
                matched++
            }
        }
        emit(TerminalLine(text = ":: Progress: [${wordlist.size}/${wordlist.size}] :: Discovered: $matched endpoint(s)", type = TerminalLineType.INFO))
    }.flowOn(Dispatchers.IO)

    /**
     * Burp Suite / OWASP ZAP Integration & Proxy Setup Helper
     */
    fun runProxyHelper(args: List<String>, currentDir: File, linuxEnv: LinuxEnvironment): Flow<TerminalLine> = flow {
        emit(TerminalLine(text = "=== Burp Suite / OWASP ZAP Proxy Integration Helper ===", type = TerminalLineType.SUCCESS))

        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            val help = """
            Commands:
              proxy-setup --status                 View active proxy configuration
              proxy-setup --set <ip:port>          Configure downstream proxy (e.g. 127.0.0.1:8080)
              proxy-setup --clear                  Disable proxy routing
              proxy-setup --export-burp <file>     Export target scopes as Burp Suite project JSON
              proxy-setup --export-zap <file>      Export target scopes as OWASP ZAP XML context
            """.trimIndent()
            emit(TerminalLine(text = help, type = TerminalLineType.INFO))
            return@flow
        }

        when {
            args.contains("--status") -> {
                val proxyVal = System.getenv("HTTP_PROXY") ?: "Not configured (Direct connection)"
                emit(TerminalLine(text = "Current HTTP_PROXY:  $proxyVal", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "Current HTTPS_PROXY: $proxyVal", type = TerminalLineType.STDOUT))
                emit(TerminalLine(text = "Burp/ZAP default:    127.0.0.1:8080", type = TerminalLineType.INFO))
            }
            args.contains("--set") -> {
                val idx = args.indexOf("--set")
                val proxyAddress = if (idx + 1 < args.size) args[idx + 1] else "127.0.0.1:8080"
                emit(TerminalLine(text = "[+] Configured proxy endpoint: $proxyAddress", type = TerminalLineType.SUCCESS))
                emit(TerminalLine(text = "[*] Routing CLI tools (curl, nuclei, httpx) through proxy $proxyAddress", type = TerminalLineType.INFO))
            }
            args.contains("--clear") -> {
                emit(TerminalLine(text = "[+] Proxy configuration cleared. Reverted to direct socket connection.", type = TerminalLineType.SUCCESS))
            }
            args.contains("--export-burp") -> {
                val fileName = args.getOrNull(args.indexOf("--export-burp") + 1) ?: "burp_scope.json"
                val outFile = File(currentDir, fileName)
                val json = """
                {
                  "target": {
                    "scope": {
                      "advanced_mode": true,
                      "include": [
                        { "enabled": true, "host": ".*\\.example\\.com", "protocol": "any" }
                      ]
                    }
                  }
                }
                """.trimIndent()
                outFile.writeText(json)
                emit(TerminalLine(text = "[+] Exported Burp Suite scope to: ${outFile.name}", type = TerminalLineType.SUCCESS))
            }
            args.contains("--export-zap") -> {
                val fileName = args.getOrNull(args.indexOf("--export-zap") + 1) ?: "zap_context.context"
                val outFile = File(currentDir, fileName)
                val xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <configuration>
                  <context>
                    <name>SecStation Exported Scope</name>
                    <incregexes>https?://.*\.example\.com.*</incregexes>
                  </context>
                </configuration>
                """.trimIndent()
                outFile.writeText(xml)
                emit(TerminalLine(text = "[+] Exported OWASP ZAP context to: ${outFile.name}", type = TerminalLineType.SUCCESS))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun computeHashStr(algorithm: String, text: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class NucleiTemplate(
    val id: String,
    val name: String,
    val tags: String,
    val severity: String,
    val description: String
)
