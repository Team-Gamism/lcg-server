package com.lcg.domain.schoolIdentity.repository

import com.lcg.domain.schoolIdentity.entity.SchoolIdentity
import com.lcg.domain.schoolIdentity.entity.SchoolIdentityProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SchoolIdentityRepository : JpaRepository<SchoolIdentity, UUID> {
    fun existsByProviderAndProviderUserId(provider: SchoolIdentityProvider, providerUserId: String): Boolean

    @EntityGraph(attributePaths = ["user"])
    fun findByProviderAndProviderUserId(provider: SchoolIdentityProvider, providerUserId: String): SchoolIdentity?

    @EntityGraph(attributePaths = ["user"])
    fun findByUserIdAndProvider(userId: UUID, provider: SchoolIdentityProvider): SchoolIdentity?

    // Serialize first logins, including the case where no identity row exists yet.
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:subject, 0))", nativeQuery = true)
    fun lockSubject(subject: String): Int
}
