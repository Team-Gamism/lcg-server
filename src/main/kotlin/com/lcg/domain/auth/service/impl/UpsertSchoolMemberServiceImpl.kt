package com.lcg.domain.auth.service.impl

import com.lcg.domain.auth.client.SchoolAccount
import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.auth.service.UpsertSchoolMemberService
import com.lcg.domain.schoolIdentity.entity.SchoolIdentity
import com.lcg.domain.schoolIdentity.entity.SchoolIdentityProvider.DATAGSM
import com.lcg.domain.schoolIdentity.repository.SchoolIdentityRepository
import com.lcg.domain.user.entity.User
import com.lcg.domain.user.entity.UserProfile
import com.lcg.domain.user.entity.UserStatus
import com.lcg.domain.user.repository.UserProfileRepository
import com.lcg.domain.user.repository.UserRepository
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class UpsertSchoolMemberServiceImpl(
    private val identities: SchoolIdentityRepository,
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
) : UpsertSchoolMemberService {
    @Transactional(timeout = 5)
    override fun execute(account: SchoolAccount): AuthenticatedMember? {
        identities.lockSubject("DATAGSM:${account.providerUserId}")
        val existing = identities.findByProviderAndProviderUserId(DATAGSM, account.providerUserId)
        val now = Instant.now()
        if (!account.eligible) {
            // Commit revocation before the caller returns the school-membership error.
            existing?.apply {
                schoolEligible = false
                verifiedGrade = null
                verifiedAt = now
                studentNumber = null
                studentName = null
                updatedAt = now
                user.sessionVersion++
                user.updatedAt = now
            }
            return null
        }
        val grade = account.grade ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        val studentNumber = account.studentNumber ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        val studentName = account.studentName ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        val user = existing?.user ?: users.save(User())
        if (user.status != UserStatus.ACTIVE) throw ExpectedException(ApiErrorCode.FORBIDDEN)
        profiles.findById(user.id).orElseGet { profiles.save(UserProfile(user.id)) }
        val identity = existing ?: SchoolIdentity(user = user, provider = DATAGSM, providerUserId = account.providerUserId)
        identity.schoolEligible = true
        identity.verifiedGrade = grade
        identity.verifiedAt = now
        identity.studentNumber = studentNumber
        identity.studentName = studentName
        identity.updatedAt = now
        if (existing == null) identities.save(identity)
        return AuthenticatedMember(user.id, user.role, grade, user.sessionVersion, now)
    }
}
