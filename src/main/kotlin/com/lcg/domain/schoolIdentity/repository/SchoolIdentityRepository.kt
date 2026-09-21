package com.lcg.domain.schoolIdentity.repository

import com.lcg.domain.schoolIdentity.entity.SchoolIdentity
import com.lcg.domain.schoolIdentity.entity.SchoolIdentityProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SchoolIdentityRepository : JpaRepository<SchoolIdentity, UUID> {
    fun existsByProviderAndProviderUserId(provider: SchoolIdentityProvider, providerUserId: String): Boolean
}
