package com.lcg.domain.auth.service.impl

import com.lcg.domain.auth.client.SchoolOAuthClient
import com.lcg.domain.auth.repository.OAuthAttemptRepository
import com.lcg.domain.auth.service.SchoolLoginAttempt
import com.lcg.domain.auth.service.StartSchoolLoginService
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64

@Service
class StartSchoolLoginServiceImpl(
    private val client: SchoolOAuthClient,
    private val attempts: OAuthAttemptRepository,
) : StartSchoolLoginService {
    private val random = SecureRandom()

    override fun execute(): SchoolLoginAttempt {
        val state = randomToken()
        val browserSecret = randomToken()
        val authorization = client.authorization(state)
        attempts.save(state, browserSecret, authorization.verifier)
        return SchoolLoginAttempt(state, browserSecret, authorization.url)
    }

    private fun randomToken(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also(random::nextBytes))
}
