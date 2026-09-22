package com.lcg.global.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties("lcg.datagsm")
class DataGsmProperties(
    val enabled: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val callbackUri: String = "",
    val successUri: String = "",
    val secureCookies: Boolean = true,
) {
    fun validate() {
        require(clientId.isNotBlank()) { "DATAGSM_CLIENT_ID is required when DataGSM is enabled" }
        // SDK 1.6.0 requires this when constructing its client, even with PKCE.
        require(clientSecret.isNotBlank()) { "DATAGSM_CLIENT_SECRET is required when DataGSM is enabled" }
        require(validUri(callbackUri) && validUri(successUri)) {
            "DataGSM callback and success URIs must be absolute HTTPS URLs (HTTP loopback only with local cookies)"
        }
    }

    private fun validUri(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.scheme == "https" || (!secureCookies && uri.scheme == "http" &&
                uri.host in setOf("localhost", "127.0.0.1", "[::1]")))
    }.getOrDefault(false)
}
