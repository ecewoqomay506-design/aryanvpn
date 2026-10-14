package com.aryan.vpn.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a sing-box config (schema per https://sing-box.sagernet.org/configuration/ , 1.9/1.10 style).
 * Validate with Libbox.checkConfig() before starting.
 */
object ConfigBuilder {
    fun build(p: VlessProfile, dns: List<String>, mixedPort: Int? = null): String {
        require(dns.isNotEmpty()) { "No DNS servers configured" }
        val path = p.wsPath.substringBefore("?ed=")
        val ed = p.wsPath.substringAfter("?ed=", "").toIntOrNull()
        val transport = JSONObject().put("type", "ws").put("path", path).apply {
            p.wsHost?.let { put("headers", JSONObject().put("Host", it)) }
            if (ed != null) { put("max_early_data", ed); put("early_data_header_name", "Sec-WebSocket-Protocol") }
        }
        val tls = JSONObject().put("enabled", true).put("insecure", false).apply {
            p.sni?.let { put("server_name", it) }
            if (p.alpn.isNotEmpty()) put("alpn", JSONArray(p.alpn))
            p.fingerprint?.let { put("utls", JSONObject().put("enabled", true).put("fingerprint", it)) }
        }
        val proxy = JSONObject().put("type", "vless").put("tag", "proxy")
            .put("server", p.connectionHost).put("server_port", p.port).put("uuid", p.uuid)
            .put("tls", tls).put("transport", transport)
        val dnsServers = JSONArray()
        dns.forEachIndexed { i, s ->
            dnsServers.put(JSONObject().put("tag", "dns-$i").put("address", "udp://${if (s.contains(':')) "[$s]" else s}").put("detour", "proxy"))
        }
        dnsServers.put(JSONObject().put("tag", "dns-local").put("address", "local").put("detour", "direct"))
        val cfg = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("dns", JSONObject().put("servers", dnsServers).put("final", "dns-0")
                .put("strategy", "prefer_ipv4"))
            .put("inbounds", JSONArray().put(if (mixedPort != null)
                JSONObject().put("type", "mixed").put("tag", "probe-in").put("listen", "127.0.0.1").put("listen_port", mixedPort)
            else JSONObject().put("type", "tun").put("tag", "tun-in")
                .put("inet4_address", "172.19.0.1/30").put("inet6_address", "fdfe:dcba:9876::1/126")
                .put("mtu", 9000).put("auto_route", true).put("strict_route", true)
                .put("stack", "mixed").put("sniff", true)))
            .put("outbounds", JSONArray().put(proxy)
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "dns").put("tag", "dns-out")))
            .put("route", JSONObject().put("auto_detect_interface", false).put("final", "proxy")
                .put("rules", JSONArray()
                    .put(JSONObject().put("protocol", "dns").put("outbound", "dns-out"))
                    .put(JSONObject().put("ip_is_private", true).put("outbound", "direct"))))
        return cfg.toString(2)
    }
}
