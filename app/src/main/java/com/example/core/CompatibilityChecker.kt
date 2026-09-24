package com.example.core

import com.example.model.CompatibilityStatus

data class ToolCapabilityReport(
    val toolName: String,
    val status: CompatibilityStatus,
    val title: String,
    val technicalReason: String,
    val rootlessAlternative: String,
    val recommendedCommand: String
)

object CompatibilityChecker {

    fun isRootAvailable(): Boolean {
        // Strict unrooted checks
        return false
    }

    fun isCapNetRawAvailable(): Boolean {
        // In unrooted Android userspace, CAP_NET_RAW is not granted to third-party app processes
        return false
    }

    fun isWifiMonitorModeAvailable(): Boolean {
        // Promiscuous / 802.11 monitor mode requires root and customized Wi-Fi firmware
        return false
    }

    fun checkTool(toolName: String): ToolCapabilityReport = getCompatibilityReport(toolName)

    fun getCompatibilityReport(toolName: String): ToolCapabilityReport {
        return when (toolName.lowercase().trim()) {
            "nmap" -> ToolCapabilityReport(
                toolName = "Nmap Network Scanner",
                status = CompatibilityStatus.LIMITED,
                title = "TCP Connect Mode (-sT) Supported",
                technicalReason = "SYN Stealth scan (-sS), OS fingerprinting (-O), and raw ICMP ping require CAP_NET_RAW / root. Unprivileged userspace cannot construct raw IP packets.",
                rootlessAlternative = "Use unprivileged TCP Connect scan: 'nmap -sT -Pn -p 80,443,8080 <target>' or the built-in Port Scanner.",
                recommendedCommand = "nmap -sT -Pn -p 80,443,22,21,8080 "
            )
            "tcpdump" -> ToolCapabilityReport(
                toolName = "tcpdump Packet Analyzer",
                status = CompatibilityStatus.UNSUPPORTED,
                title = "Raw Promiscuous Sniffing Unavailable",
                technicalReason = "Opening an AF_PACKET raw socket requires CAP_NET_RAW privileges. Android security policy isolates applications from intercepting external network traffic.",
                rootlessAlternative = "Use VpnService-based local packet inspection, HTTP/HTTPS proxies (e.g., mitmproxy / Burp on authorized workstation), or the built-in HTTP Inspector.",
                recommendedCommand = "echo 'tcpdump requires CAP_NET_RAW; use HTTP Inspector for userspace capture'"
            )
            "aircrack-ng" -> ToolCapabilityReport(
                toolName = "Aircrack-ng Wireless Suite",
                status = CompatibilityStatus.UNSUPPORTED,
                title = "Monitor Mode & Frame Injection Unavailable",
                technicalReason = "Requires nl80211 monitor mode and raw 802.11 frame injection, which are blocked by standard Android Wi-Fi HAL drivers and lack of root permissions.",
                rootlessAlternative = "Use an external Kali Linux machine with a supported USB Wi-Fi adapter (e.g. Alfa AWUS036ACH) in an authorized lab.",
                recommendedCommand = "echo 'Wi-Fi monitor mode requires kernel driver access and root'"
            )
            "ping" -> ToolCapabilityReport(
                toolName = "Ping ICMP Utility",
                status = CompatibilityStatus.LIMITED,
                title = "Userspace Fallback Active",
                technicalReason = "Traditional raw ICMP sockets are restricted. SecStation utilizes unprivileged ICMP socket fallback and TCP connection latency measurement.",
                rootlessAlternative = "Unprivileged ICMP echo or TCP connection ping to port 80/443.",
                recommendedCommand = "ping "
            )
            "traceroute" -> ToolCapabilityReport(
                toolName = "Traceroute",
                status = CompatibilityStatus.LIMITED,
                title = "UDP / TCP Probe Mode",
                technicalReason = "Standard raw ICMP TTL hop decrement inspection can be filtered by carrier/OS. Unprivileged UDP/TCP probing is used.",
                rootlessAlternative = "Traceroute using TCP/UDP high-port probing.",
                recommendedCommand = "traceroute "
            )
            "nuclei" -> ToolCapabilityReport(
                toolName = "Nuclei Vulnerability Scanner",
                status = CompatibilityStatus.COMPATIBLE,
                title = "Fully Compatible (YAML DSL Engine)",
                technicalReason = "Executes template matching against HTTP headers, status codes, SSL handshakes, and response bodies entirely in unprivileged Android userspace.",
                rootlessAlternative = "Runs full template checks without elevated permissions.",
                recommendedCommand = "nuclei -u https://example.com"
            )
            "hydra" -> ToolCapabilityReport(
                toolName = "THC Hydra Network Auditor",
                status = CompatibilityStatus.COMPATIBLE,
                title = "Application Layer Auth Auditor",
                technicalReason = "Uses standard TCP socket connections to test authentication services. Rate-limited in unprivileged userspace to prevent socket exhaustion.",
                rootlessAlternative = "Unprivileged TCP authentication probing.",
                recommendedCommand = "hydra -l admin -p secret "
            )
            "john", "john the ripper" -> ToolCapabilityReport(
                toolName = "John the Ripper Password Auditor",
                status = CompatibilityStatus.COMPATIBLE,
                title = "Userspace Crypto Auditor",
                technicalReason = "Performs CPU-based cryptographic hashing and dictionary matching purely in userspace memory.",
                rootlessAlternative = "Direct local multi-threaded CPU hashing.",
                recommendedCommand = "john --test"
            )
            "apktool", "jadx" -> ToolCapabilityReport(
                toolName = "APK Reverse Engineering Tools",
                status = CompatibilityStatus.COMPATIBLE,
                title = "Fully Compatible",
                technicalReason = "Bytecode parsing, AndroidManifest.xml decoding, resource extraction, and DEX inspection run purely in Java userspace within app memory.",
                rootlessAlternative = "Direct local bytecode and manifest analysis.",
                recommendedCommand = "apktool d app.apk"
            )
            "curl", "wget", "http" -> ToolCapabilityReport(
                toolName = "HTTP Clients & Inspectors",
                status = CompatibilityStatus.COMPATIBLE,
                title = "Fully Compatible",
                technicalReason = "Standard HTTP/HTTPS operations run within Android's INTERNET permission without requiring elevated privileges.",
                rootlessAlternative = "Full HTTP/1.1 and HTTP/2 request/response analysis.",
                recommendedCommand = "curl -i -s "
            )
            "sqlmap", "nikto", "gobuster" -> ToolCapabilityReport(
                toolName = "Web Security Assessment Tools",
                status = CompatibilityStatus.COMPATIBLE,
                title = "Userspace Application-Layer Testing",
                technicalReason = "Web vulnerability testing operates over regular TCP/HTTP sockets. No low-level kernel capabilities required.",
                rootlessAlternative = "Pure application layer testing with rate-limiting respect.",
                recommendedCommand = "nikto -h "
            )
            "whois", "dig", "dns" -> ToolCapabilityReport(
                toolName = "Reconnaissance & DNS Tools",
                status = CompatibilityStatus.COMPATIBLE,
                title = "Fully Compatible",
                technicalReason = "Uses standard UDP/TCP port 53 DNS protocol and TCP port 43 WHOIS queries.",
                rootlessAlternative = "Standard resolver queries.",
                recommendedCommand = "dig "
            )
            else -> ToolCapabilityReport(
                toolName = toolName,
                status = CompatibilityStatus.COMPATIBLE,
                title = "Userspace Utility",
                technicalReason = "Runs inside the application's isolated sandbox storage.",
                rootlessAlternative = "Standard execution.",
                recommendedCommand = "$toolName "
            )
        }
    }
}
