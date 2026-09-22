package com.lcg.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("lcg.security")
data class SecurityProperties(
    val allowedOrigins: List<String> = emptyList(),
    val publicDocs: Boolean = false,
)
