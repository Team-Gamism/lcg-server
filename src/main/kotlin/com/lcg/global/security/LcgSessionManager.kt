package com.lcg.global.security

import com.lcg.domain.auth.model.AuthenticatedMember
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class LcgSessionManager(private val contexts: SecurityContextRepository) {
    fun login(member: AuthenticatedMember, request: HttpServletRequest, response: HttpServletResponse) {
        // A completely new session discards the previous ID, CSRF token and authentication.
        request.getSession(false)?.invalidate()
        val principal = LcgPrincipal(member.userId, member.sessionVersion, Instant.now())
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, listOf(SimpleGrantedAuthority("ROLE_${member.role.name}")),
        )
        SecurityContextHolder.setContext(context)
        contexts.saveContext(context, request, response)
    }

    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        SecurityContextLogoutHandler().apply { setSecurityContextRepository(contexts) }
            .logout(request, response, SecurityContextHolder.getContext().authentication)
    }
}
