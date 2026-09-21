package com.lcg.unit

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import com.lcg.domain.system.presentation.controller.SystemController
import com.lcg.domain.system.service.impl.QuerySystemStatusServiceImpl
import com.lcg.global.config.SecurityConfig
import com.lcg.global.config.SecurityProperties
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import com.lcg.global.exception.GlobalExceptionHandler
import com.lcg.global.exception.ProblemDetailFactory
import com.lcg.global.filter.RequestIdFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(
    controllers = [SystemController::class],
    properties = ["lcg.security.allowed-origins=http://localhost:3000"],
)
@ActiveProfiles("api-contract")
@EnableConfigurationProperties(SecurityProperties::class)
@Import(
    SecurityConfig::class,
    ProblemDetailFactory::class,
    GlobalExceptionHandler::class,
    RequestIdFilter::class,
    QuerySystemStatusServiceImpl::class,
    ContractTestController::class,
)
class ApiContractTest(@Autowired private val mvc: MockMvc) {
    @Test
    fun `ping is public and assigns a server-generated request id`() {
        val response = mvc.get("/api/v1/system/ping") {
            header("X-Request-ID", "untrusted-client-value")
        }.andExpect {
            status { isOk() }
            jsonPath("$.service") { value("lcg-server") }
        }.andReturn().response
        assertThat(response.getHeader("X-Request-ID")).matches("[a-f0-9-]{36}")
    }

    @Test
    fun `anonymous requests receive a problem without redirecting to login`() {
        val response = mvc.get("/api/v1/test/private").andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("UNAUTHENTICATED") }
            header { doesNotExist("Location") }
        }.andReturn().response
        assertThat(response.contentAsString).contains(response.getHeader("X-Request-ID"))
    }

    @Test
    fun `authenticated writes still require csrf`() {
        mvc.post("/api/v1/test/validate") {
            with(user("member"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"valid"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `valid csrf and input allow a protected write`() {
        mvc.post("/api/v1/test/validate") {
            with(user("member"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"valid"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `validation returns a sanitized problem`() {
        mvc.post("/api/v1/test/validate") {
            with(user("member"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
            jsonPath("$.errors") { doesNotExist() }
        }
    }

    @Test
    fun `malformed json never echoes its contents`() {
        val response = mvc.post("/api/v1/test/validate") {
            with(user("member"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = "private-oauth-token-not-json"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }.andReturn().response
        assertThat(response.contentAsString).doesNotContain("private-oauth-token")
    }

    @Test
    fun `unknown authenticated route is a 404 problem`() {
        mvc.get("/api/v1/does-not-exist") { with(user("member")) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `unexpected exceptions do not expose secrets or exception classes`() {
        val response = mvc.get("/api/v1/test/fail") { with(user("member")) }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
        }.andReturn().response
        assertThat(response.contentAsString).doesNotContain("private-school-token", "IllegalStateException")
    }

    @ParameterizedTest
    @EnumSource(ApiErrorCode::class, names = ["CONFLICT", "RATE_LIMITED", "UPSTREAM_ERROR", "SERVICE_UNAVAILABLE", "UPSTREAM_TIMEOUT"])
    fun `domain and upstream failures have distinct statuses`(code: ApiErrorCode) {
        mvc.get("/api/v1/test/errors/${code.name}") { with(user("member")) }.andExpect {
            status { isEqualTo(code.status.value()) }
            jsonPath("$.code") { value(code.name) }
        }
    }

    @Test
    fun `member cannot call an administrator action`() {
        mvc.get("/api/v1/test/admin") { with(user("member").roles("MEMBER")) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        mvc.get("/api/v1/test/admin") { with(user("admin").roles("ADMIN")) }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `cors permits only configured origins`() {
        mvc.options("/api/v1/test/private") {
            header("Origin", "http://localhost:3000")
            header("Access-Control-Request-Method", "POST")
        }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Origin", "http://localhost:3000") }
        }
        mvc.options("/api/v1/test/private") {
            header("Origin", "https://untrusted.example")
            header("Access-Control-Request-Method", "POST")
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist("Access-Control-Allow-Origin") }
        }
    }

    @Test
    fun `documentation is protected outside the local profile`() {
        mvc.get("/v3/api-docs").andExpect { status { isUnauthorized() } }
    }
}

@RestController
@Profile("api-contract")
class ContractTestController {
    @GetMapping("/api/v1/test/private")
    fun privateRoute() = mapOf("result" to "ok")

    @PostMapping("/api/v1/test/validate")
    fun validate(@Valid @RequestBody request: TestInput) = request

    @GetMapping("/api/v1/test/fail")
    fun fail(): Nothing = error("private-school-token")

    @GetMapping("/api/v1/test/errors/{code}")
    fun error(@PathVariable code: ApiErrorCode): Nothing = throw ExpectedException(code)

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/test/admin")
    fun admin() = mapOf("result" to "ok")
}

data class TestInput(@field:NotBlank val name: String)
