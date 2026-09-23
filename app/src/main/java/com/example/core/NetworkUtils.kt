package com.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

data class DnsRecord(val type: String, val value: String)
data class PortScanResult(val port: Int, val isOpen: Boolean, val serviceName: String, val latencyMs: Long)
data class HttpResponseData(val statusCode: Int, val headers: Map<String, String>, val body: String, val timeMs: Long)

object NetworkUtils {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun resolveDns(domain: String): List<DnsRecord> = withContext(Dispatchers.IO) {
        val records = mutableListOf<DnsRecord>()
        try {
            val cleanDomain = domain.trim().removePrefix("http://").removePrefix("https://").split("/").first().split(":").first()
            val addresses = InetAddress.getAllByName(cleanDomain)
            for (addr in addresses) {
                val type = if (addr.address.size == 4) "A (IPv4)" else "AAAA (IPv6)"
                records.add(DnsRecord(type, addr.hostAddress ?: ""))
            }
        } catch (e: Exception) {
            records.add(DnsRecord("ERROR", e.localizedMessage ?: "Resolution failed"))
        }
        records
    }

    suspend fun queryWhois(queryDomain: String): String = withContext(Dispatchers.IO) {
        val clean = queryDomain.trim().removePrefix("http://").removePrefix("https://").split("/").first().split(":").first()
        try {
            // First query whois.iana.org
            var server = "whois.iana.org"
            var result = doWhoisQuery(server, clean)

            // Look for "refer:" or "whois:" line to redirect to the authoritative whois server
            val referLine = result.lines().firstOrNull { it.startsWith("refer:", ignoreCase = true) || it.startsWith("whois:", ignoreCase = true) }
            if (referLine != null) {
                val nextServer = referLine.substringAfter(":").trim()
                if (nextServer.isNotEmpty()) {
                    val detailedResult = doWhoisQuery(nextServer, clean)
                    if (detailedResult.isNotBlank()) {
                        return@withContext detailedResult
                    }
                }
            }
            result
        } catch (e: Exception) {
            "WHOIS query failed: ${e.localizedMessage}\nNote: Make sure network connectivity is active."
        }
    }

    private fun doWhoisQuery(server: String, domain: String): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(server, 43), 6000)
            socket.soTimeout = 6000
            val out = PrintWriter(socket.getOutputStream(), true)
            val `in` = BufferedReader(InputStreamReader(socket.getInputStream()))
            out.println(domain)
            val sb = java.lang.StringBuilder()
            var line: String? = `in`.readLine()
            while (line != null) {
                sb.append(line).append("\n")
                line = `in`.readLine()
            }
            return sb.toString()
        }
    }

    suspend fun checkPing(host: String, count: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").split("/").first().split(":").first()
        val results = mutableListOf<String>()
        results.add("PING $cleanHost: 56 data bytes")

        var received = 0
        for (i in 1..count) {
            val start = System.currentTimeMillis()
            var isOk = false
            try {
                // Try unprivileged socket connection to common port 80/443 or InetAddress reachability
                val inet = InetAddress.getByName(cleanHost)
                isOk = inet.isReachable(2000)
                if (!isOk) {
                    // Fallback to TCP handshake test
                    Socket().use { sock ->
                        sock.connect(InetSocketAddress(inet, 80), 2000)
                        isOk = true
                    }
                }
            } catch (e: Exception) {
                // Try HTTPS port 443
                try {
                    Socket().use { sock ->
                        sock.connect(InetSocketAddress(cleanHost, 443), 2000)
                        isOk = true
                    }
                } catch (_: Exception) {
                    isOk = false
                }
            }

            val elapsed = System.currentTimeMillis() - start
            if (isOk) {
                received++
                results.add("64 bytes from $cleanHost: icmp_seq=$i time=${elapsed}ms")
            } else {
                results.add("Request timeout for icmp_seq $i (${elapsed}ms)")
            }
        }
        results.add("--- $cleanHost ping statistics ---")
        results.add("$count packets transmitted, $received received, ${(100 - (received * 100 / count))}% packet loss")
        results
    }

    suspend fun scanPorts(
        host: String,
        ports: List<Int> = listOf(21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 1433, 3306, 3389, 5432, 8080, 8443),
        timeoutMs: Int = 1200
    ): List<PortScanResult> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").split("/").first().split(":").first()
        val serviceMap = mapOf(
            21 to "FTP",
            22 to "SSH",
            23 to "Telnet",
            25 to "SMTP",
            53 to "DNS",
            80 to "HTTP",
            110 to "POP3",
            143 to "IMAP",
            443 to "HTTPS",
            445 to "SMB",
            1433 to "MSSQL",
            3306 to "MySQL",
            3389 to "RDP",
            5432 to "PostgreSQL",
            8080 to "HTTP-Proxy",
            8443 to "HTTPS-Alt"
        )

        ports.map { port ->
            val start = System.currentTimeMillis()
            var open = false
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(cleanHost, port), timeoutMs)
                    open = true
                }
            } catch (_: Exception) {
                open = false
            }
            val elapsed = System.currentTimeMillis() - start
            PortScanResult(
                port = port,
                isOpen = open,
                serviceName = serviceMap[port] ?: "Unknown",
                latencyMs = elapsed
            )
        }
    }

    suspend fun executeHttpRequest(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): HttpResponseData = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            if (method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true)) {
                reqBuilder.method(method, (body ?: "").toRequestBody())
            } else {
                reqBuilder.method(method, null)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                val headerMap = response.headers.names().associateWith { response.headers[it] ?: "" }
                val responseBody = response.body?.string() ?: ""
                HttpResponseData(
                    statusCode = response.code,
                    headers = headerMap,
                    body = responseBody.take(10000), // Protect against huge payloads
                    timeMs = elapsed
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            HttpResponseData(
                statusCode = 0,
                headers = emptyMap(),
                body = "Request failed: ${e.localizedMessage}",
                timeMs = elapsed
            )
        }
    }

    fun getLocalInterfaces(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        list.add(Pair(nif.displayName ?: nif.name, addr.hostAddress ?: ""))
                    }
                }
            }
        } catch (_: Exception) {}
        if (list.isEmpty()) {
            list.add(Pair("lo (Loopback)", "127.0.0.1"))
        }
        return list
    }
}
