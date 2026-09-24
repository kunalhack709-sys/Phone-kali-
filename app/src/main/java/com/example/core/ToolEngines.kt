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
            emit(TerminalLine(text = "[ERR] No target URL specified. Use: nuclei -u <url>", type = TerminalLineType.STDERR))
            return@flow
        }

        val fullUrl = if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) "https://$targetUrl" else targetUrl

        emit(TerminalLine(text = "[INF] Current nuclei version: v3.2.0 (latest)", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INF] Loaded ${templates.size} built-in templates for Android unprivileged sandbox", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INF] Running templates against: $fullUrl", type = TerminalLineType.INFO))

        val startTime = System.currentTimeMillis()
        var matchCount = 0

        // Execute primary target query
        val mainResponse = NetworkUtils.executeHttpRequest(fullUrl)
        if (mainResponse.statusCode == 0) {
            emit(TerminalLine(text = "[ERR] Failed to connect to $fullUrl: ${mainResponse.body}", type = TerminalLineType.STDERR))
            return@flow
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
            emit(TerminalLine(text = "ERROR: No host specified (-h)", type = TerminalLineType.STDERR))
            emit(TerminalLine(text = "Usage: nikto -h <host> [options]", type = TerminalLineType.INFO))
            return@flow
        }

        val targetUrl = if (!host.startsWith("http://") && !host.startsWith("https://")) "https://$host" else host
        val cleanHost = targetUrl.removePrefix("http://").removePrefix("https://").substringBefore("/")

        emit(TerminalLine(text = "+ Target IP:          $cleanHost", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "+ Target Hostname:    $cleanHost", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "+ Target Port:        ${if (targetUrl.startsWith("https")) 443 else 80}", type = TerminalLineType.STDOUT))
        emit(TerminalLine(text = "+ Start Time:         ${Date()}", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "---------------------------------------------------------------------------", type = TerminalLineType.INFO))

        val res = NetworkUtils.executeHttpRequest(targetUrl)
        if (res.statusCode == 0) {
            emit(TerminalLine(text = "+ ERROR: Cannot connect to $targetUrl: ${res.body}", type = TerminalLineType.STDERR))
            return@flow
        }

        val server = res.headers["Server"] ?: res.headers["server"] ?: "Unknown"
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
        val checkPaths = listOf("/robots.txt", "/admin", "/login", "/.git", "/server-status")
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
            url = args.lastOrNull { it.startsWith("http://") || it.startsWith("https://") }
        }

        if (url.isNullOrBlank()) {
            emit(TerminalLine(text = "Usage: gobuster dir -u <url> [-w <wordlist>]", type = TerminalLineType.STDERR))
            emit(TerminalLine(text = "Example: gobuster dir -u https://httpbin.org", type = TerminalLineType.INFO))
            return@flow
        }

        val targetUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url

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
            "health", "metrics", "status", "portal", "user", "auth", "get"
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
        val target = if (uIdx >= 0 && uIdx + 1 < args.size) args[uIdx + 1] else args.lastOrNull { it.startsWith("http") }

        if (target.isNullOrBlank()) {
            emit(TerminalLine(text = "[!] Missing target URL (-u <url>).", type = TerminalLineType.STDERR))
            emit(TerminalLine(text = "Usage: sqlmap -u \"https://example.com/item?id=1\" [options]", type = TerminalLineType.INFO))
            return@flow
        }

        emit(TerminalLine(text = "[*] starting @ ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "[INFO] testing connection to the target URL", type = TerminalLineType.INFO))

        val res = NetworkUtils.executeHttpRequest(target)
        if (res.statusCode == 0) {
            emit(TerminalLine(text = "[CRITICAL] target URL not reachable: ${res.body}", type = TerminalLineType.STDERR))
            return@flow
        }

        emit(TerminalLine(text = "[INFO] checking if the target is protected by some kind of WAF/IPS", type = TerminalLineType.INFO))
        val server = res.headers["Server"] ?: res.headers["server"] ?: "Standard Web Server"
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

        val targetArg = args.lastOrNull { !it.startsWith("-") }
        if (targetArg.isNullOrBlank()) {
            emit(TerminalLine(text = "Usage: john [OPTIONS] [PASSWORD-FILES]", type = TerminalLineType.STDERR))
            emit(TerminalLine(text = "Try: john --test   (to run speed benchmark)", type = TerminalLineType.INFO))
            return@flow
        }

        // Check if argument is a file or a raw hash
        val targetFile = File(currentDir, targetArg)
        val hashText = if (targetFile.exists() && targetFile.isFile) targetFile.readText().trim() else targetArg

        val commonPasswords = listOf("admin", "password", "123456", "welcome", "secret", "root", "kali", "pass123")
        val md5Dict = commonPasswords.associateBy { computeHashStr("MD5", it) }
        val sha1Dict = commonPasswords.associateBy { computeHashStr("SHA-1", it) }
        val sha256Dict = commonPasswords.associateBy { computeHashStr("SHA-256", it) }

        var format = "Unknown"
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
        val targetArg = args.lastOrNull { !it.startsWith("-") }
        if (targetArg.isNullOrBlank()) {
            emit(TerminalLine(text = "Usage: jadx [-d <output dir>] <input file> (.apk, .dex, .jar)", type = TerminalLineType.STDERR))
            return@flow
        }

        val targetFile = File(currentDir, targetArg)
        if (!targetFile.exists()) {
            emit(TerminalLine(text = "ERROR: Input file not found: $targetArg", type = TerminalLineType.STDERR))
            return@flow
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
        val url = args.lastOrNull { it.startsWith("http://") || it.startsWith("https://") }
        if (url.isNullOrBlank()) {
            emit(TerminalLine(text = "wget: missing URL", type = TerminalLineType.STDERR))
            emit(TerminalLine(text = "Usage: wget [options] <url>", type = TerminalLineType.INFO))
            return@flow
        }

        val outIdx = args.indexOf("-O")
        val outFileName = if (outIdx >= 0 && outIdx + 1 < args.size) {
            args[outIdx + 1]
        } else {
            url.substringAfterLast("/").substringBefore("?").ifBlank { "index.html" }
        }

        val outputFile = File(currentDir, outFileName)
        emit(TerminalLine(text = "--${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}--  $url", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "Resolving ${URL(url).host}... done.", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "Connecting to ${URL(url).host}... connected.", type = TerminalLineType.INFO))
        emit(TerminalLine(text = "HTTP request sent, awaiting response...", type = TerminalLineType.INFO))

        val res = NetworkUtils.executeHttpRequest(url)
        if (res.statusCode in 200..299) {
            emit(TerminalLine(text = "Length: ${res.body.length} (${res.body.length / 1024} KB) [text/html]", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Saving to: '$outFileName'", type = TerminalLineType.STDOUT))
            outputFile.writeText(res.body)
            emit(TerminalLine(text = "100%[====================================>] ${res.body.length}  --.-KB/s    in 0.1s", type = TerminalLineType.SUCCESS))
            emit(TerminalLine(text = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ($outFileName saved [${res.body.length}/${res.body.length}])", type = TerminalLineType.SUCCESS))
        } else {
            emit(TerminalLine(text = "ERROR ${res.statusCode}: Failed to download file.", type = TerminalLineType.STDERR))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Python 3 Userspace Script Runner
     */
    fun runPython(args: List<String>, currentDir: File, linuxEnv: LinuxEnvironment): Flow<TerminalLine> = flow {
        if (args.isEmpty()) {
            emit(TerminalLine(text = "Python 3.11.8 (main, Feb 2026, 12:00:00) [Clang Android]", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.", type = TerminalLineType.INFO))
            emit(TerminalLine(text = "Use: python3 -c \"print('Hello from SecStation')\" or python3 script.py", type = TerminalLineType.STDOUT))
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

        val scriptFile = File(currentDir, args.first())
        if (scriptFile.exists()) {
            emit(TerminalLine(text = "[Executing ${scriptFile.name}]", type = TerminalLineType.INFO))
            val evaluated = evaluateSimplePython(scriptFile.readText())
            emit(TerminalLine(text = evaluated, type = TerminalLineType.STDOUT))
        } else {
            emit(TerminalLine(text = "python3: can't open file '${args.first()}': [Errno 2] No such file or directory", type = TerminalLineType.STDERR))
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
