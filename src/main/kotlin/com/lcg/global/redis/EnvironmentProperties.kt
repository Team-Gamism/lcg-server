package com.lcg.global.redis

import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("lcg")
data class EnvironmentProperties(
    @field:Pattern(regexp = "[a-z][a-z0-9-]{0,31}")
    val environment: String,
)
