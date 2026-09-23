package com.lcg.domain.schoolIdentity.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import com.lcg.domain.user.entity.User
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "school_identities")
class SchoolIdentity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val provider: SchoolIdentityProvider,

    @Column(name = "provider_user_id", nullable = false, length = 255)
    val providerUserId: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,

    @Column(name = "school_eligible", nullable = false)
    var schoolEligible: Boolean = false,

    @Column(name = "verified_grade")
    var verifiedGrade: Int? = null,

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,

    @Column(name = "student_number")
    var studentNumber: Int? = null,

    @Column(name = "student_name", length = 50)
    var studentName: String? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
)

enum class SchoolIdentityProvider {
    DATAGSM,
}
