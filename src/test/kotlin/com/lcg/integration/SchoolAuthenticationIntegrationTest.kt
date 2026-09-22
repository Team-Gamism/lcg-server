package com.lcg.integration

import com.lcg.domain.auth.client.SchoolAccount
import com.lcg.domain.auth.repository.OAuthAttemptRepository
import com.lcg.domain.auth.service.UpsertSchoolMemberService
import com.lcg.global.redis.RedisKeys
import com.lcg.global.security.LcgPrincipal
import com.lcg.support.DataGsmStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
import org.springframework.session.SessionRepository
import org.springframework.session.Session
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient
import tools.jackson.databind.json.JsonMapper
import java.net.HttpCookie
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom

@Tag("integration")
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SchoolAuthenticationIntegrationTest.SdkConfiguration::class)
class SchoolAuthenticationIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var stub: DataGsmStub
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var redis: StringRedisTemplate
    @Autowired private lateinit var keys: RedisKeys
    @Autowired private lateinit var members: UpsertSchoolMemberService
    @Autowired private lateinit var attempts: OAuthAttemptRepository
    @Autowired private lateinit var sessions: SessionRepository<*>
    private val mapper = JsonMapper.builder().build()
    private val subjects = mutableSetOf<Long>()
    private val browsers = mutableListOf<Browser>()

    @AfterEach
    fun cleanUp() {
        browsers.forEach(Browser::close)
        subjects.forEach { subject ->
            val ids = jdbc.queryForList("SELECT user_id FROM school_identities WHERE provider='DATAGSM' AND provider_user_id=?", UUID::class.java, subject.toString())
            jdbc.update("DELETE FROM school_identities WHERE provider='DATAGSM' AND provider_user_id=?", subject.toString())
            ids.forEach { jdbc.update("DELETE FROM users WHERE id=?", it) }
        }
        val testKeys = redis.keys("lcg:$environment:*")
        if (testKeys.isNotEmpty()) redis.delete(testKeys)
    }

    @Test
    fun `login rotates session and supports csrf protected logout without exposing school data`() {
        val browser = browser()
        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401)
        val oldCsrf = csrf(browser)
        val oldCookie = browser.cookies.getValue("LCGSESSION")
        val attempt = start(browser)
        val cookie = attempt.response.headers().allValues("Set-Cookie").single { it.startsWith("LCG_OAUTH_") }
        assertThat(cookie).contains("HttpOnly", "SameSite=Lax", "Max-Age=300", "Path=/api/v1/auth/school")
        val verifierKey = keys.oauthAttempt(attempt.state)
        assertThat(redis.getExpire(verifierKey)).isBetween(1L, 300L)
        val callback = complete(browser, attempt.state, studentCode())
        assertThat(callback.statusCode()).isEqualTo(302)
        assertThat(callback.headers().firstValue("Location").orElseThrow()).isEqualTo("http://localhost:3000")
        assertThat(callback.headers().firstValue("Referrer-Policy").orElseThrow()).isEqualTo("no-referrer")
        assertThat(browser.cookies.getValue("LCGSESSION")).isNotEqualTo(oldCookie)
        assertThat(redis.hasKey(verifierKey)).isFalse()
        val me = browser.get("/api/v1/me")
        assertThat(me.statusCode()).isEqualTo(200)
        assertThat(mapper.readTree(me.body()).path("role").asString()).isEqualTo("MEMBER") // provider is ADMIN
        assertThat(me.body()).contains("\"grade\":2").doesNotContain("school-token", "refresh", "email", "student", "provider")
        assertThat(browser.get("/api/v1/me", cookieOverride = "LCGSESSION=$oldCookie").statusCode()).isEqualTo(401)
        assertThat(browser.post("/api/v1/auth/logout").statusCode()).isEqualTo(403)
        assertThat(browser.post("/api/v1/auth/logout", oldCsrf).statusCode()).isEqualTo(403)
        val loggedInCookie = browser.cookies.getValue("LCGSESSION")
        assertThat(browser.post("/api/v1/auth/logout", csrf(browser)).statusCode()).isEqualTo(204)
        assertThat(browser.get("/api/v1/me", cookieOverride = "LCGSESSION=$loggedInCookie").statusCode()).isEqualTo(401)
    }

    @Test
    fun `wrong browser cannot consume state and completed callbacks cannot be replayed`() {
        val owner = browser()
        val stranger = browser()
        val attempt = start(owner)
        val code = studentCode()
        assertProblem(complete(stranger, attempt.state, code), 400, "OAUTH_STATE_INVALID")
        assertThat(redis.hasKey(keys.oauthAttempt(attempt.state))).isTrue()
        val cookie = owner.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        assertThat(complete(owner, attempt.state, code).statusCode()).isEqualTo(302)
        assertProblem(owner.get("/api/v1/auth/school/callback?state=${attempt.state}&code=$code", cookie), 400, "OAUTH_STATE_INVALID")
    }

    @Test
    fun `multiple tabs retain independent login attempts and redirect is fixed`() {
        val browser = browser()
        val first = start(browser, "?returnUrl=https://attacker.example")
        val second = start(browser)
        assertThat(first.state).isNotEqualTo(second.state)
        val subject = subject()
        listOf(first, second).forEach { attempt ->
            val response = complete(browser, attempt.state, studentCode(subject))
            assertThat(response.statusCode()).isEqualTo(302)
            assertThat(response.headers().firstValue("Location").orElseThrow()).isEqualTo("http://localhost:3000")
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM school_identities WHERE provider_user_id=?", Long::class.java, subject.toString())).isEqualTo(1)
    }

    @Test
    fun `expired missing and denied callbacks do not issue sessions`() {
        val browser = browser()
        assertProblem(browser.get("/api/v1/auth/school/callback?code=missing-state"), 400, "OAUTH_STATE_INVALID")
        val expired = start(browser)
        redis.expire(keys.oauthAttempt(expired.state), Duration.ZERO)
        assertProblem(complete(browser, expired.state, "expired"), 400, "OAUTH_STATE_INVALID")
        val denied = start(browser)
        assertProblem(browser.get("/api/v1/auth/school/callback?state=${denied.state}&error=access_denied&error_description=private-value"), 403, "OAUTH_DENIED")
        assertThat(redis.hasKey(keys.oauthAttempt(denied.state))).isFalse()
        val missing = start(browser)
        assertProblem(browser.get("/api/v1/auth/school/callback?state=${missing.state}"), 400, "OAUTH_CODE_REJECTED")
        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401)
    }

    @Test
    fun `concurrent first logins create one member and one identity`() {
        val subject = subject()
        val countBefore = jdbc.queryForObject("SELECT count(*) FROM users", Long::class.java)!!
        val calls = (1..6).map { CompletableFuture.supplyAsync { members.execute(SchoolAccount(subject.toString(), 2, true))!! } }
        val ids = calls.map { it.join().userId }
        assertThat(ids.distinct()).hasSize(1)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long::class.java)).isEqualTo(countBefore + 1)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM school_identities WHERE provider_user_id=?", Long::class.java, subject.toString())).isEqualTo(1)
    }

    @Test
    fun `concurrent callbacks can consume a valid attempt only once`() {
        val browser = browser()
        val state = start(browser).state
        val secret = browser.cookies.getValue("LCG_OAUTH_$state")
        val calls = (1..8).map { CompletableFuture.supplyAsync {
            runCatching { attempts.consume(state, secret) }.isSuccess
        } }
        assertThat(calls.count { it.join() }).isEqualTo(1)
        assertThat(redis.hasKey(keys.oauthAttempt(state))).isFalse()
    }

    @Test
    fun `ineligible first login never creates a member`() {
        val subject = subject()
        val browser = browser()
        val code = stub.code(DataGsmStub.Reply(userBody = """{"id":$subject,"status":"ACTIVE","objectType":"TEACHER"}"""))
        assertProblem(complete(browser, start(browser).state, code), 403, "SCHOOL_MEMBERSHIP_REQUIRED")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM school_identities WHERE provider_user_id=?", Long::class.java, subject.toString())).isZero()
        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401)
    }

    @Test
    fun `logout all invalidates other browsers and later login works`() {
        val subject = subject()
        val first = login(subject)
        val second = login(subject)
        assertThat(first.post("/api/v1/auth/logout-all", csrf(first)).statusCode()).isEqualTo(204)
        assertThat(second.get("/api/v1/me").statusCode()).isEqualTo(401)
        assertThat(login(subject).get("/api/v1/me").statusCode()).isEqualTo(200)
    }

    @Test
    fun `suspension and withdrawal affect existing sessions and prevent new login`() {
        listOf("SUSPENDED", "WITHDRAWN").forEach { status ->
            val subject = subject()
            val browser = login(subject)
            val userId = mapper.readTree(browser.get("/api/v1/me").body()).path("id").asString()
            jdbc.update("UPDATE users SET status=? WHERE id=?", status, UUID.fromString(userId))
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(403)
            assertProblem(complete(browser, start(browser).state, studentCode(subject)), 403, "FORBIDDEN")
        }
    }

    @Test
    fun `school eligibility loss revokes existing sessions and grade refresh uses stable provider id`() {
        val subject = subject()
        val existing = login(subject)
        val firstId = mapper.readTree(existing.get("/api/v1/me").body()).path("id").asString()
        val other = browser()
        assertThat(complete(other, start(other).state, studentCode(subject, grade = 3)).statusCode()).isEqualTo(302)
        val updated = mapper.readTree(existing.get("/api/v1/me").body())
        assertThat(updated.path("id").asString()).isEqualTo(firstId)
        assertThat(updated.path("grade").asInt()).isEqualTo(3)
        val code = stub.code(DataGsmStub.Reply(userBody = DataGsmStub.student(subject, role = "GRADUATE")))
        assertProblem(complete(other, start(other).state, code), 403, "SCHOOL_MEMBERSHIP_REQUIRED")
        assertThat(existing.get("/api/v1/me").statusCode()).isEqualTo(401)
    }

    @Test
    fun `school verification and absolute session age require fresh login`() {
        val subject = subject()
        val browser = login(subject)
        jdbc.update("UPDATE school_identities SET verified_at=now()-interval '9 hours' WHERE provider_user_id=?", subject.toString())
        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401)
        val fresh = login(subject)
        val sessionId = String(Base64.getDecoder().decode(fresh.cookies.getValue("LCGSESSION")), Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        val repository = sessions as SessionRepository<Session>
        val session = repository.findById(sessionId)!!
        assertThat(session.maxInactiveInterval).isEqualTo(Duration.ofMinutes(30))
        val context = session.getAttribute<SecurityContextImpl>(SPRING_SECURITY_CONTEXT_KEY)!!
        val principal = context.authentication!!.principal as LcgPrincipal
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
            principal.copy(authenticatedAt = Instant.now().minus(Duration.ofHours(9))), null,
            listOf(SimpleGrantedAuthority("ROLE_MEMBER")),
        )))
        repository.save(session)
        assertThat(fresh.get("/api/v1/me").statusCode()).isEqualTo(401)
        assertThat(complete(fresh, start(fresh).state, studentCode(subject)).statusCode()).isEqualTo(302)
    }

    @Test
    fun `upstream errors never create a member or leak their body`() {
        val browser = browser()
        val attempt = start(browser)
        val code = stub.code(DataGsmStub.Reply(tokenStatus = 500, tokenBody = "private-school-token"))
        assertProblem(complete(browser, attempt.state, code), 502, "UPSTREAM_ERROR")
        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401)
        assertThat(redis.hasKey(keys.oauthAttempt(attempt.state))).isFalse()
    }

    private fun login(subject: Long = subject()): Browser = browser().also {
        assertThat(complete(it, start(it).state, studentCode(subject)).statusCode()).isEqualTo(302)
    }

    private fun browser() = Browser().also(browsers::add)
    private fun subject() = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L).also(subjects::add)
    private fun studentCode(subject: Long = subject(), grade: Int = 2) = stub.code(DataGsmStub.Reply(userBody = DataGsmStub.student(subject, grade)))
    private fun csrf(browser: Browser): String {
        val response = browser.get("/api/v1/auth/csrf")
        assertThat(response.statusCode()).isEqualTo(200)
        return mapper.readTree(response.body()).path("token").asString()
    }
    private fun start(browser: Browser, query: String = ""): Attempt {
        val response = browser.get("/api/v1/auth/school/login$query")
        assertThat(response.statusCode()).isEqualTo(302)
        val location = URI(response.headers().firstValue("Location").orElseThrow())
        val params = location.rawQuery.split('&').associate { part ->
            val pair = part.split('=', limit = 2)
            pair[0] to URLDecoder.decode(pair[1], Charsets.UTF_8)
        }
        return Attempt(params.getValue("state"), response)
    }
    private fun complete(browser: Browser, state: String, code: String) = browser.get("/api/v1/auth/school/callback?state=$state&code=$code")
    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status)
        assertThat(mapper.readTree(response.body()).path("code").asString()).isEqualTo(code)
        assertThat(response.body()).doesNotContain("private-school-token", "private-value", "refresh_token", "error_description")
    }

    private data class Attempt(val state: String, val response: HttpResponse<String>)
    private inner class Browser : AutoCloseable {
        val cookies = mutableMapOf<String, String>()
        private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
        fun get(path: String, cookieOverride: String? = null) = send(path, "GET", null, cookieOverride)
        fun post(path: String, csrf: String? = null) = send(path, "POST", csrf, null)
        private fun send(path: String, method: String, csrf: String?, cookieOverride: String?): HttpResponse<String> {
            val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody())
            val cookie = cookieOverride ?: cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (cookie.isNotEmpty()) request.header("Cookie", cookie)
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf)
            val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
            if (cookieOverride == null) response.headers().allValues("Set-Cookie").flatMap(HttpCookie::parse).forEach {
                if (it.maxAge == 0L) cookies.remove(it.name) else cookies[it.name] = it.value
            }
            return response
        }
        override fun close() = client.close()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SdkConfiguration {
        @Bean(destroyMethod = "close") fun dataGsmStub() = DataGsmStub()
        @Bean(destroyMethod = "close") fun sdk(stub: DataGsmStub): DataGsmOAuthClient = stub.client()
    }

    companion object {
        private val environment = "auth-${UUID.randomUUID().toString().take(8)}"
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("lcg.environment") { environment }
            registry.add("lcg.datagsm.enabled") { false }
            registry.add("lcg.datagsm.success-uri") { "http://localhost:3000" }
        }
    }
}
