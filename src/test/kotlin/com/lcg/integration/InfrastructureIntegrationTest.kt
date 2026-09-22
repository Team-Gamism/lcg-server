package com.lcg.integration

import jakarta.persistence.EntityManager
import com.lcg.domain.user.entity.User
import com.lcg.domain.user.repository.UserRepository
import com.lcg.global.redis.RedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Tag("integration")
@ActiveProfiles("local")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["lcg.environment=integration", "lcg.datagsm.enabled=false"],
)
class InfrastructureIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired private lateinit var flyway: Flyway
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var redis: StringRedisTemplate
    @Autowired private lateinit var redisKeys: RedisKeys

    @Test
    fun `migration validates and repeated startup has nothing to apply`() {
        flyway.validate()
        assertThat(flyway.info().current().version.version).isEqualTo("2")
        assertThat(flyway.migrate().migrationsExecuted).isZero()
    }

    @Test
    fun `V2 upgrades existing V1 users without granting school eligibility`() {
        val schema = "auth_migration_${UUID.randomUUID().toString().replace("-", "") }"
        val userId = UUID.randomUUID()
        try {
            val v1 = Flyway.configure().dataSource(jdbc.dataSource!!).schemas(schema)
                .target(MigrationVersion.fromVersion("1")).load()
            v1.migrate()
            jdbc.update("INSERT INTO $schema.users (id, created_at, updated_at) VALUES (?, now(), now())", userId)
            jdbc.update("INSERT INTO $schema.school_identities (id, user_id, provider, provider_user_id, created_at, updated_at) VALUES (?, ?, 'DATAGSM', 'migration-test', now(), now())", UUID.randomUUID(), userId)
            val v2 = Flyway.configure().dataSource(jdbc.dataSource!!).schemas(schema).load()
            assertThat(v2.migrate().migrationsExecuted).isEqualTo(1)
            v2.validate()
            val row = jdbc.queryForMap("SELECT status, role, session_version FROM $schema.users WHERE id=?", userId)
            assertThat(row).containsEntry("status", "ACTIVE").containsEntry("role", "MEMBER").containsEntry("session_version", 0L)
            assertThat(jdbc.queryForObject("SELECT school_eligible FROM $schema.school_identities WHERE user_id=?", Boolean::class.java, userId)).isFalse()
        } finally {
            // Only the unique schema created by this test is removed.
            jdbc.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
        }
    }

    @Test
    @Transactional
    fun `postgres persists a user with UTC time and an optimistic version`() {
        val createdAt = Instant.parse("2026-09-21T00:00:00Z")
        val saved = users.saveAndFlush(User(createdAt = createdAt))
        entityManager.clear()
        val loaded = users.findById(saved.id).orElseThrow()
        assertThat(loaded.createdAt).isEqualTo(createdAt)
        assertThat(loaded.updatedAt).isEqualTo(createdAt)
        assertThat(loaded.version).isZero()
    }

    @Test
    @Transactional
    fun `the same school identity cannot belong to two users`() {
        val first = users.saveAndFlush(User())
        val second = users.saveAndFlush(User())
        val subject = UUID.randomUUID().toString()
        insertIdentity(first.id, subject)
        assertThatThrownBy { insertIdentity(second.id, subject) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    @Transactional
    fun `school identity requires an existing user`() {
        assertThatThrownBy { insertIdentity(UUID.randomUUID(), UUID.randomUUID().toString()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `redis values are namespaced and expire`() {
        val key = redisKeys.cache("probe", UUID.randomUUID().toString())
        assertThat(key).startsWith("lcg:integration:cache:v1:")
        try {
            redis.opsForValue().set(key, "ok", Duration.ofSeconds(30))
            assertThat(redis.opsForValue().get(key)).isEqualTo("ok")
            assertThat(redis.getExpire(key)).isBetween(1L, 30L)
        } finally {
            redis.delete(key)
        }
    }

    @Test
    fun `server exposes probes without storage details and generates local OpenAPI`() {
        HttpClient.newHttpClient().use { client ->
            listOf("/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
                val response = get(client, path)
                assertThat(response.statusCode()).isEqualTo(200)
                assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components", "jdbc", "redis")
            }
            val api = get(client, "/v3/api-docs")
            assertThat(api.statusCode()).isEqualTo(200)
            assertThat(api.body()).contains("/api/v1/system/ping")
            assertThat(get(client, "/api/v1/private").statusCode()).isEqualTo(401)
            assertThat(get(client, "/api/v1/auth/school/login").statusCode()).isEqualTo(503)
            // ContractTestController must never be discovered by the production component scan.
            assertThat(api.body()).doesNotContain("/api/v1/test/")
        }
    }

    private fun get(client: HttpClient, path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().timeout(Duration.ofSeconds(10)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun insertIdentity(userId: UUID, subject: String) {
        jdbc.update(
            "INSERT INTO school_identities (id, user_id, provider, provider_user_id, created_at, updated_at) VALUES (?, ?, 'DATAGSM', ?, now(), now())",
            UUID.randomUUID(), userId, subject,
        )
    }
}
