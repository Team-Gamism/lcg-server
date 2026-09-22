package com.lcg.domain.auth.model

import com.lcg.domain.user.entity.UserRole
import java.time.Instant
import java.util.UUID

data class AuthenticatedMember(
    val userId: UUID,
    val role: UserRole,
    val grade: Int,
    val sessionVersion: Long,
    val verifiedAt: Instant,
)
