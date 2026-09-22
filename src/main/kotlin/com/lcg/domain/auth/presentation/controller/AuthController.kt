package com.lcg.domain.auth.presentation.controller

import com.lcg.domain.auth.presentation.SchoolLoginCookies
import com.lcg.domain.auth.presentation.data.response.CsrfResDto
import com.lcg.domain.auth.service.CompleteSchoolLoginService
import com.lcg.domain.auth.service.LogoutAllService
import com.lcg.domain.auth.service.StartSchoolLoginService
import com.lcg.global.config.DataGsmProperties
import com.lcg.global.security.LcgPrincipal
import com.lcg.global.security.LcgSessionManager
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "학교 OAuth 및 LCG 세션")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val startLogin: StartSchoolLoginService,
    private val completeLogin: CompleteSchoolLoginService,
    private val logoutAll: LogoutAllService,
    private val cookies: SchoolLoginCookies,
    private val sessions: LcgSessionManager,
    private val properties: DataGsmProperties,
) {
    @Operation(summary = "DataGSM 로그인 시작", description = "브라우저 탐색으로 호출합니다. 로그인 후 이동 주소는 서버 설정으로 고정합니다.")
    @GetMapping("/school/login")
    fun login(response: HttpServletResponse) {
        val attempt = startLogin.execute()
        cookies.write(response, attempt.state, attempt.browserSecret)
        response.sendRedirect(attempt.authorizationUrl)
    }

    @Operation(summary = "DataGSM 로그인 콜백")
    @GetMapping("/school/callback")
    fun callback(
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val secret = cookies.read(request, state)
        cookies.clear(response, state)
        val member = completeLogin.execute(state, secret, code, error)
        sessions.login(member, request, response)
        response.sendRedirect(properties.successUri)
    }

    @Operation(summary = "CSRF 토큰 조회", description = "변경 요청의 헤더에 전달합니다. 로그인·로그아웃 후 새 토큰을 조회합니다.")
    @GetMapping("/csrf")
    fun csrf(token: CsrfToken) = CsrfResDto(token.headerName, token.token)

    @Operation(summary = "현재 LCG 세션 로그아웃")
    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        sessions.logout(request, response)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "모든 LCG 세션 로그아웃", description = "이전에 발급된 세션은 다음 요청부터 거부됩니다. DataGSM 전체 로그아웃과 별개입니다.")
    @PostMapping("/logout-all")
    fun logoutAll(
        @AuthenticationPrincipal principal: LcgPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        logoutAll.execute(principal.userId)
        sessions.logout(request, response)
        return ResponseEntity.noContent().build()
    }
}
