package com.lcg.domain.auth.repository

import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import com.lcg.global.redis.RedisKeys
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64

@Repository
class OAuthAttemptRepository(private val redis: StringRedisTemplate, private val keys: RedisKeys) {
    fun save(state: String, browserSecret: String, verifier: String) {
        val saved = redis.opsForValue().setIfAbsent(key(state), "${hash(browserSecret)}:$verifier", TTL)
        if (saved != true) throw ExpectedException(ApiErrorCode.SERVICE_UNAVAILABLE)
    }

    fun consume(state: String?, browserSecret: String?): String {
        if (state == null || browserSecret == null || !TOKEN.matches(state) || !TOKEN.matches(browserSecret)) {
            throw ExpectedException(ApiErrorCode.OAUTH_STATE_INVALID)
        }
        // Check browser binding and delete together; a wrong browser must not consume a valid attempt.
        return redis.execute(CONSUME, listOf(key(state)), hash(browserSecret))
            ?: throw ExpectedException(ApiErrorCode.OAUTH_STATE_INVALID)
    }

    private fun key(state: String) = keys.oauthAttempt(state)
    private fun hash(value: String) = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII)))

    companion object {
        val TTL: Duration = Duration.ofMinutes(5)
        val TOKEN = Regex("[A-Za-z0-9_-]{43}")
        private val CONSUME = DefaultRedisScript(
            """
            local value = redis.call('GET', KEYS[1])
            if not value or string.sub(value, 1, 43) ~= ARGV[1] then return nil end
            redis.call('DEL', KEYS[1])
            return string.sub(value, 45)
            """.trimIndent(),
            String::class.java,
        )
    }
}
