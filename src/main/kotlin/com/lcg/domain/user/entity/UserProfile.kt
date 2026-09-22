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
@Table(name = "user_profiles")
class UserProfile(
    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "school_name", length = 64)
    var schoolName: String? = null,

    @Column(name = "riot_id", length = 22)
    var riotId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_position", length = 16)
    var primaryPosition: UserPosition? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_position", length = 16)
    var secondaryPosition: UserPosition? = null,

    @Column(length = 500)
    var introduction: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    fun update(
        riotId: String?,
        primaryPosition: UserPosition?,
        secondaryPosition: UserPosition?,
        introduction: String?,
        updatedAt: Instant,
    ) {
        this.riotId = riotId
        this.primaryPosition = primaryPosition
        this.secondaryPosition = secondaryPosition
        this.introduction = introduction
        this.updatedAt = updatedAt
    }

    fun updateSchoolName(schoolName: String, updatedAt: Instant) {
        this.schoolName = schoolName
        this.updatedAt = updatedAt
    }
}
