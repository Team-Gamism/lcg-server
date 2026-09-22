package com.lcg.global.security

import com.lcg.domain.auth.service.QueryAuthenticatedMemberService
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import com.lcg.global.exception.ProblemDetailFactory
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.dao.DataAccessException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class SessionAccessFilter(
    private val members: QueryAuthenticatedMemberService,
    private val problems: ProblemDetailFactory,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? LcgPrincipal
        if (principal != null) {
            try {
                val member = members.execute(principal)
                val context = SecurityContextHolder.createEmptyContext()
                context.authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, listOf(SimpleGrantedAuthority("ROLE_${member.role.name}")),
                )
                SecurityContextHolder.setContext(context)
                request.setAttribute(MEMBER_ATTRIBUTE, member)
            } catch (ex: ExpectedException) {
                request.getSession(false)?.invalidate()
                SecurityContextHolder.clearContext()
                // An expired session must not prevent starting a new login or checking health.
                if (!PUBLIC_GETS.contains(request.requestURI) || request.method != "GET") {
                    problems.write(request, response, ex.errorCode)
                    return
                }
            } catch (_: DataAccessException) {
                problems.write(request, response, ApiErrorCode.SERVICE_UNAVAILABLE)
                return
            }
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val MEMBER_ATTRIBUTE = "lcg.authenticatedMember"
        val PUBLIC_GETS = setOf(
            "/api/v1/auth/school/login", "/api/v1/auth/school/callback", "/api/v1/auth/csrf",
            "/api/v1/system/ping", "/actuator/health/liveness", "/actuator/health/readiness",
        )
    }
}
