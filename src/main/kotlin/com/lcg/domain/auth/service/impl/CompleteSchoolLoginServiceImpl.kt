package com.lcg.domain.auth.service.impl

import com.lcg.domain.auth.client.SchoolOAuthClient
import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.auth.repository.OAuthAttemptRepository
import com.lcg.domain.auth.service.CompleteSchoolLoginService
import com.lcg.domain.auth.service.UpsertSchoolMemberService
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import org.springframework.stereotype.Service

@Service
class CompleteSchoolLoginServiceImpl(
    private val attempts: OAuthAttemptRepository,
    private val client: SchoolOAuthClient,
    private val upsertMember: UpsertSchoolMemberService,
) : CompleteSchoolLoginService {
    override fun execute(state: String?, browserSecret: String?, code: String?, error: String?): AuthenticatedMember {
        val verifier = attempts.consume(state, browserSecret)
        if (error != null) throw ExpectedException(ApiErrorCode.OAUTH_DENIED)
        if (code.isNullOrBlank() || code.length > 2048 || code.any { it.isISOControl() }) {
            throw ExpectedException(ApiErrorCode.OAUTH_CODE_REJECTED)
        }
        val account = client.authenticate(code, verifier)
        return upsertMember.execute(account) ?: throw ExpectedException(ApiErrorCode.SCHOOL_MEMBERSHIP_REQUIRED)
    }
}
