package com.lcg.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: UserStatus = UserStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var role: UserRole = UserRole.MEMBER,

    @Column(name = "session_version", nullable = false)
    var sessionVersion: Long = 0,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
)

enum class UserStatus { ACTIVE, SUSPENDED, WITHDRAWN }
enum class UserRole { MEMBER, ADMIN }
