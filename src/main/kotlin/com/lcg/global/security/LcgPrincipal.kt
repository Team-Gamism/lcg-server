package com.lcg.global.security

import java.io.Serializable
import java.security.Principal
import java.time.Instant
import java.util.UUID

data class LcgPrincipal(
    val userId: UUID,
    val sessionVersion: Long,
    val authenticatedAt: Instant,
) : Principal, Serializable {
    override fun getName(): String = userId.toString()
}
