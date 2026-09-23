package com.lcg.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("lcg.riot")
class RiotApiProperties(
    val enabled: Boolean = false,
    val apiKey: String = "",
) {
    fun validate() {
        require(apiKey.isNotBlank() && apiKey.none(Char::isWhitespace)) {
            "RIOT_API_KEY is required when Riot API is enabled"
        }
    }
}
