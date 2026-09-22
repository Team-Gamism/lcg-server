package com.lcg.domain.auth.service.impl

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.auth.service.QueryAuthenticatedMemberService
import com.lcg.domain.schoolIdentity.entity.SchoolIdentityProvider.DATAGSM
import com.lcg.domain.schoolIdentity.repository.SchoolIdentityRepository
import com.lcg.domain.user.entity.UserStatus
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import com.lcg.global.security.LcgPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class QueryAuthenticatedMemberServiceImpl(private val identities: SchoolIdentityRepository) : QueryAuthenticatedMemberService {
    @Transactional(readOnly = true)
    override fun execute(principal: LcgPrincipal): AuthenticatedMember {
        val now = Instant.now()
        val identity = identities.findByUserIdAndProvider(principal.userId, DATAGSM)
            ?: throw ExpectedException(ApiErrorCode.UNAUTHENTICATED)
        val user = identity.user
        if (user.status != UserStatus.ACTIVE) throw ExpectedException(ApiErrorCode.FORBIDDEN)
        val verifiedAt = identity.verifiedAt
        if (principal.sessionVersion != user.sessionVersion || !identity.schoolEligible ||
            verifiedAt == null || identity.verifiedGrade == null ||
            !principal.authenticatedAt.plus(MAX_AGE).isAfter(now) || !verifiedAt.plus(MAX_AGE).isAfter(now)) {
            throw ExpectedException(ApiErrorCode.UNAUTHENTICATED)
        }
        return AuthenticatedMember(user.id, user.role, identity.verifiedGrade!!, user.sessionVersion, verifiedAt)
    }

    companion object {
        val MAX_AGE: Duration = Duration.ofHours(8)
    }
}
