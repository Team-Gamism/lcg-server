package com.lcg.unit

import com.lcg.domain.riot.client.RiotHttpClient
import com.lcg.domain.riot.client.RiotId
import com.lcg.global.config.RiotApiProperties
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertFailsWith

class RiotApiContractTest {
    private val stub = RiotApiStub()
    private val client = RiotHttpClient(
        http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(2)).build(),
        apiKey = "private-riot-api-key",
        accountApiBaseUrl = stub.baseUrl,
        platformApiBaseUrl = stub.baseUrl,
    )

    @AfterEach
    fun close() {
        client.close()
        stub.close()
    }

    @Test
    fun `Riot ID resolves account summoner and ranked entries with an API key header`() {
        stub.reply("/riot/account/v1/accounts/by-riot-id/Player Name/KR1", body = account())
        stub.reply("/lol/summoner/v4/summoners/by-puuid/player-puuid", body = summoner())
        stub.reply("/lol/league/v4/entries/by-puuid/player-puuid", body = entries())

        val profile = client.findProfile(RiotId("Player Name", "KR1"))

        assertThat(profile.account.gameName).isEqualTo("Player Name")
        assertThat(profile.account.tagLine).isEqualTo("KR1")
        assertThat(profile.summoner?.profileIconId).isEqualTo(4567)
        assertThat(profile.summoner?.summonerLevel).isEqualTo(123)
        val entry = profile.leagueEntries.single()
        assertThat(entry.queueType).isEqualTo("RANKED_SOLO_5x5")
        assertThat(entry.tier).isEqualTo("EMERALD")
        assertThat(entry.rank).isEqualTo("III")
        assertThat(entry.leaguePoints).isEqualTo(52)
        assertThat(entry.wins).isEqualTo(100)
        assertThat(entry.losses).isEqualTo(90)
        assertThat(stub.requests.map { it.path }).containsExactly(
            "/riot/account/v1/accounts/by-riot-id/Player Name/KR1",
            "/lol/summoner/v4/summoners/by-puuid/player-puuid",
            "/lol/league/v4/entries/by-puuid/player-puuid",
        )
        assertThat(stub.requests.flatMap { it.headers["x-riot-token"].orEmpty() }).containsOnly("private-riot-api-key")
    }

    @Test
    fun `an account with no League profile is returned without ranked entries`() {
        stub.reply("/riot/account/v1/accounts/by-riot-id/Player/KR1", body = account())
        stub.reply("/lol/summoner/v4/summoners/by-puuid/player-puuid", status = 404)

        val profile = client.findProfile(RiotId("Player", "KR1"))

        assertThat(profile.summoner).isNull()
        assertThat(profile.leagueEntries).isEmpty()
        assertThat(stub.requests).hasSize(2)
    }

    @Test
    fun `unknown Riot ID maps to a sanitized not found error`() {
        stub.reply("/riot/account/v1/accounts/by-riot-id/Unknown/KR1", status = 404, body = "private-upstream-body")

        val ex = assertFailsWith<ExpectedException> {
            client.findProfile(RiotId("Unknown", "KR1"))
        }

        assertThat(ex.errorCode).isEqualTo(ApiErrorCode.NOT_FOUND)
        assertThat(ex.message).doesNotContain("private-upstream-body", "private-riot-api-key")
    }

    @Test
    fun `rate limits and malformed upstream responses are sanitized`() {
        stub.reply("/riot/account/v1/accounts/by-riot-id/RateLimited/KR1", status = 429, body = "private-upstream-body")
        val rateLimited = assertFailsWith<ExpectedException> {
            client.findProfile(RiotId("RateLimited", "KR1"))
        }
        assertThat(rateLimited.errorCode).isEqualTo(ApiErrorCode.SERVICE_UNAVAILABLE)
        assertThat(rateLimited.message).doesNotContain("private-upstream-body", "private-riot-api-key")

        stub.reply("/riot/account/v1/accounts/by-riot-id/Malformed/KR1", body = "not-json")
        val malformed = assertFailsWith<ExpectedException> {
            client.findProfile(RiotId("Malformed", "KR1"))
        }
        assertThat(malformed.errorCode).isEqualTo(ApiErrorCode.UPSTREAM_ERROR)
    }

    @Test
    fun `upstream timeouts are sanitized without retries`() {
        stub.reply(
            "/riot/account/v1/accounts/by-riot-id/Slow/KR1",
            body = account(),
            delayMillis = 500,
        )
        RiotHttpClient(
            http = OkHttpClient.Builder().callTimeout(Duration.ofMillis(100)).build(),
            apiKey = "private-riot-api-key",
            accountApiBaseUrl = stub.baseUrl,
            platformApiBaseUrl = stub.baseUrl,
        ).use { timeoutClient ->
            val ex = assertFailsWith<ExpectedException> {
                timeoutClient.findProfile(RiotId("Slow", "KR1"))
            }
            assertThat(ex.errorCode).isEqualTo(ApiErrorCode.UPSTREAM_TIMEOUT)
        }
        assertThat(stub.requests).hasSize(1)
    }

    @Test
    fun `Riot configuration and IDs reject invalid values`() {
        assertFailsWith<IllegalArgumentException> { RiotApiProperties(enabled = true).validate() }
        assertFailsWith<IllegalArgumentException> { RiotApiProperties(enabled = true, apiKey = "has whitespace").validate() }
        RiotApiProperties(enabled = true, apiKey = "valid-api-key").validate()

        assertFailsWith<IllegalArgumentException> { RiotId.parse("missing-separator") }
        assertFailsWith<IllegalArgumentException> { RiotId("Player", "bad\nline") }
        assertThat(RiotId.parse("Player Name#KR1")).isEqualTo(RiotId("Player Name", "KR1"))
    }

    private fun account() = """{"puuid":"player-puuid","gameName":"Player Name","tagLine":"KR1"}"""

    private fun summoner() = """{"id":"encrypted-summoner-id","profileIconId":4567,"summonerLevel":123,"revisionDate":1700000000000}"""

    private fun entries() = """[
        {"queueType":"RANKED_SOLO_5x5","tier":"EMERALD","rank":"III","leaguePoints":52,"wins":100,"losses":90,"hotStreak":true,"veteran":false,"freshBlood":false,"inactive":false}
    ]"""
}

private class RiotApiStub : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val replies = mutableMapOf<String, Reply>()
    val requests = CopyOnWriteArrayList<CapturedRequest>()
    val baseUrl: HttpUrl get() = HttpUrl.Builder()
        .scheme("http")
        .host("127.0.0.1")
        .port(server.address.port)
        .build()

    init {
        server.createContext("/") { exchange ->
            requests.add(CapturedRequest(exchange.requestURI.path, exchange.requestHeaders.toMap().mapKeys { it.key.lowercase() }))
            val reply = synchronized(replies) { replies.remove(exchange.requestURI.path) } ?: Reply(status = 404)
            if (reply.delayMillis > 0) {
                Thread.sleep(reply.delayMillis)
            }
            respond(exchange, reply.status, reply.body)
        }
        server.start()
    }

    fun reply(path: String, status: Int = 200, body: String = "{}", delayMillis: Long = 0) {
        synchronized(replies) {
            replies[path] = Reply(status, body, delayMillis)
        }
    }

    override fun close() = server.stop(0)

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private data class Reply(val status: Int, val body: String = "{}", val delayMillis: Long = 0)

    data class CapturedRequest(val path: String, val headers: Map<String, List<String>>)
}
