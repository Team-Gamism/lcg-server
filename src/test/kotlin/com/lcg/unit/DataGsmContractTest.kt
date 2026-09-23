package com.lcg.unit

import com.lcg.domain.auth.client.DataGsmSchoolOAuthClient
import com.lcg.global.config.DataGsmProperties
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import com.lcg.support.DataGsmStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import java.time.Duration
import kotlin.test.assertFailsWith

class DataGsmContractTest {
    private val stub = DataGsmStub()
    private val sdk = stub.client()
    private val beans = DefaultListableBeanFactory().apply { registerSingleton("sdk", sdk) }
    private val adapter = DataGsmSchoolOAuthClient(beans.getBeanProvider(DataGsmOAuthClient::class.java),
        DataGsmProperties(callbackUri = "http://localhost:8080/api/v1/auth/school/callback"))
    private val mapper = JsonMapper.builder().build()

    @AfterEach
    fun close() {
        sdk.close()
        stub.close()
    }

    @Test
    fun `SDK produces S256 and sends JSON verifier without a client secret`() {
        val auth = adapter.authorization("browser-state")
        val params = URI(auth.url).rawQuery.split('&').associate {
            val parts = it.split('=', limit = 2)
            parts[0] to URLDecoder.decode(parts[1], Charsets.UTF_8)
        }
        assertThat(params).containsEntry("state", "browser-state")
            .containsEntry("response_type", "code").containsEntry("scope", "datagsm:self_read")
            .containsEntry("code_challenge_method", "S256")
        assertThat(auth.verifier).matches("[A-Za-z0-9_-]{43}")
        assertThat(params["code_challenge"]).isEqualTo(Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(auth.verifier.toByteArray())))

        val code = stub.code()
        val account = adapter.authenticate(code, auth.verifier)
        assertThat(account.providerUserId).isEqualTo("101") // top-level ID, never student.id
        assertThat(account.grade).isEqualTo(2)
        assertThat(account.studentNumber).isEqualTo(2101)
        assertThat(account.studentName).isEqualTo("테스트 학생")
        assertThat(account.eligible).isTrue()
        val tokenRequest = stub.requests.first()
        val body = mapper.readTree(tokenRequest.body)
        assertThat(body.path("grant_type").asString()).isEqualTo("authorization_code")
        assertThat(body.path("code").asString()).isEqualTo(code)
        assertThat(body.path("code_verifier").asString()).isEqualTo(auth.verifier)
        assertThat(body.path("redirect_uri").asString()).isEqualTo(params["redirect_uri"])
        assertThat(body.path("client_id").asString()).isEqualTo("contract-client")
        assertThat(body.has("client_secret")).isFalse()
        assertThat(tokenRequest.headers.entries.first { it.key.equals("Content-Type", true) }.value.first())
            .startsWith("application/json")
        val userRequest = stub.requests.last()
        assertThat(userRequest.path).isEqualTo("/userinfo")
        assertThat(userRequest.headers.entries.first { it.key.equals("Authorization", true) }.value)
            .containsExactly("Bearer school-token-$code")
    }

    @ParameterizedTest
    @ValueSource(strings = ["GRADUATE", "WITHDRAWN", "UNKNOWN_ROLE"])
    fun `non-current or unknown student roles fail eligibility`(role: String) {
        val account = adapter.authenticate(stub.code(DataGsmStub.Reply(userBody = DataGsmStub.student(role = role))), "verifier")
        assertThat(account.eligible).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{\"id\":1,\"status\":\"ACTIVE\",\"objectType\":\"TEACHER\"}",
        "{\"id\":1,\"status\":\"PENDING\",\"objectType\":\"STUDENT\"}",
        "{\"id\":1,\"status\":\"ACTIVE\",\"objectType\":\"STUDENT\",\"student\":null}",
        "{\"id\":1,\"status\":\"ACTIVE\",\"isStudent\":true}",
    ])
    fun `missing or ineligible school data does not authorize a member`(body: String) {
        assertThat(adapter.authenticate(stub.code(DataGsmStub.Reply(userBody = body)), "verifier").eligible).isFalse()
    }

    @Test
    fun `missing leave status and invalid grade fail closed`() {
        listOf(DataGsmStub.student(leave = "null"), DataGsmStub.student(leave = "true"), DataGsmStub.student(grade = 4)).forEach {
            assertThat(adapter.authenticate(stub.code(DataGsmStub.Reply(userBody = it)), "verifier").eligible).isFalse()
        }
    }

    @Test
    fun `missing student name or student number fails closed`() {
        listOf(
            DataGsmStub.student(name = " "),
            DataGsmStub.student(studentNumber = 0),
        ).forEach {
            assertThat(adapter.authenticate(stub.code(DataGsmStub.Reply(userBody = it)), "verifier").eligible).isFalse()
        }
    }

    @Test
    fun `code rejection and upstream failures are sanitized`() {
        listOf(400 to ApiErrorCode.OAUTH_CODE_REJECTED, 500 to ApiErrorCode.UPSTREAM_ERROR, 429 to ApiErrorCode.SERVICE_UNAVAILABLE).forEach { (status, expected) ->
            val ex = assertFailsWith<ExpectedException> {
                adapter.authenticate(stub.code(DataGsmStub.Reply(tokenStatus = status, tokenBody = "private-school-token")), "verifier")
            }
            assertThat(ex.errorCode).isEqualTo(expected)
            assertThat(ex.message).doesNotContain("private-school-token")
            assertThat(ex.cause).isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "{}", "not-json", "{\"access_token\":\"token\",\"token_type\":\"MAC\",\"expires_in\":3600}"])
    fun `malformed tokens never proceed to userinfo`(body: String) {
        val ex = assertFailsWith<ExpectedException> {
            adapter.authenticate(stub.code(DataGsmStub.Reply(tokenBody = body)), "verifier")
        }
        assertThat(ex.errorCode).isEqualTo(ApiErrorCode.UPSTREAM_ERROR)
        assertThat(stub.requests).hasSize(1)
    }

    @Test
    fun `userinfo failures and invalid identities are not accepted`() {
        listOf(DataGsmStub.Reply(userStatus = 401), DataGsmStub.Reply(userBody = "null"),
            DataGsmStub.Reply(userBody = "{}"), DataGsmStub.Reply(userBody = DataGsmStub.student(subject = -1))).forEach { reply ->
            val ex = assertFailsWith<ExpectedException> { adapter.authenticate(stub.code(reply), "verifier") }
            assertThat(ex.errorCode).isEqualTo(ApiErrorCode.UPSTREAM_ERROR)
        }
    }

    @Test
    fun `bounded upstream timeout is sanitized and the code is not retried`() {
        stub.client(Duration.ofMillis(100)).use { slowSdk ->
            val factory = DefaultListableBeanFactory().apply { registerSingleton("sdk", slowSdk) }
            val timedAdapter = DataGsmSchoolOAuthClient(factory.getBeanProvider(DataGsmOAuthClient::class.java),
                DataGsmProperties(callbackUri = "http://localhost/callback"))
            val ex = assertFailsWith<ExpectedException> {
                timedAdapter.authenticate(stub.code(DataGsmStub.Reply(tokenDelayMillis = 500)), "verifier")
            }
            assertThat(ex.errorCode).isEqualTo(ApiErrorCode.UPSTREAM_TIMEOUT)
            assertThat(ex.cause).isNull()
            assertThat(stub.requests).hasSize(1)
        }
    }

    @Test
    fun `enabled configuration requires credentials and safe fixed redirects`() {
        assertFailsWith<IllegalArgumentException> { DataGsmProperties(enabled = true).validate() }
        listOf("http://remote.example/callback", "//attacker.example", "https://user:password@example.com", "https://example.com/#fragment").forEach { uri ->
            assertFailsWith<IllegalArgumentException> {
                DataGsmProperties(true, "id", "secret", uri, "https://lcg.example.com").validate()
            }
        }
        DataGsmProperties(true, "id", "secret", "https://api.lcg.example.com/callback", "https://lcg.example.com").validate()
        DataGsmProperties(true, "id", "secret", "http://localhost:8080/callback", "http://localhost:3000", false).validate()
    }
}
