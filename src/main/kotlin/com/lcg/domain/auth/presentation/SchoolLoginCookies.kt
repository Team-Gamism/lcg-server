package com.lcg.domain.auth.presentation

import com.lcg.domain.auth.repository.OAuthAttemptRepository
import com.lcg.global.config.DataGsmProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SchoolLoginCookies(private val properties: DataGsmProperties) {
    fun write(response: HttpServletResponse, state: String, secret: String) =
        writeCookie(response, state, secret, OAuthAttemptRepository.TTL)

    fun read(request: HttpServletRequest, state: String?): String? {
        if (state == null || !OAuthAttemptRepository.TOKEN.matches(state)) return null
        return request.cookies?.filter { it.name == name(state) }?.singleOrNull()?.value
    }

    fun clear(response: HttpServletResponse, state: String?) {
        if (state != null && OAuthAttemptRepository.TOKEN.matches(state)) writeCookie(response, state, "", Duration.ZERO)
    }

    private fun writeCookie(response: HttpServletResponse, state: String, value: String, ttl: Duration) {
        val cookie = ResponseCookie.from(name(state), value)
            .path("/api/v1/auth/school")
            .httpOnly(true)
            .secure(properties.secureCookies)
            .sameSite("Lax")
            .maxAge(ttl)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    private fun name(state: String) = "LCG_OAUTH_$state"
}
