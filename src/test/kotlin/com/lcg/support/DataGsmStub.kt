package com.lcg.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient
import team.themoment.datagsm.sdk.oauth.http.OkHttpClientImpl
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class DataGsmStub : AutoCloseable {
    private val mapper = JsonMapper.builder().build()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val codes = ConcurrentHashMap<String, Reply>()
    private val tokens = ConcurrentHashMap<String, Reply>()
    val requests = CopyOnWriteArrayList<CapturedRequest>()
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/v1/oauth/token") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            requests.add(CapturedRequest(exchange.requestURI.path, exchange.requestHeaders.toMap(), body))
            val code = mapper.readTree(body).path("code").asString()
            val reply = codes.remove(code)
            if (reply == null) {
                respond(exchange, 400, """{"error":"invalid_grant"}""")
            } else {
                if (reply.tokenDelayMillis > 0) Thread.sleep(reply.tokenDelayMillis)
                tokens["school-token-$code"] = reply
                respond(exchange, reply.tokenStatus, reply.tokenBody ?: """{"access_token":"school-token-$code","token_type":"Bearer","expires_in":3600,"refresh_token":"private-refresh-token"}""")
            }
        }
        server.createContext("/userinfo") { exchange ->
            requests.add(CapturedRequest(exchange.requestURI.path, exchange.requestHeaders.toMap(), ""))
            val token = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")
            val reply = token?.let(tokens::get)
            respond(exchange, reply?.userStatus ?: 401, reply?.userBody ?: "{}")
        }
        server.start()
    }

    fun code(reply: Reply = Reply()): String = UUID.randomUUID().toString().also { codes[it] = reply }

    fun client(timeout: Duration = Duration.ofSeconds(2)): DataGsmOAuthClient = DataGsmOAuthClient.builder("contract-client", "contract-secret")
        .authorizationBaseUrl(baseUrl)
        .userInfoBaseUrl(baseUrl)
        .httpClient(OkHttpClientImpl(OkHttpClient.Builder()
            .callTimeout(timeout).followRedirects(false).retryOnConnectionFailure(false).build()))
        .build()

    override fun close() = server.stop(0)

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    data class Reply(
        val tokenStatus: Int = 200,
        val tokenBody: String? = null,
        val userStatus: Int = 200,
        val userBody: String = student(),
        val tokenDelayMillis: Long = 0,
    )

    data class CapturedRequest(val path: String, val headers: Map<String, List<String>>, val body: String)

    companion object {
        fun student(
            subject: Long = 101,
            grade: Int = 2,
            role: String = "GENERAL_STUDENT",
            leave: String = "false",
            studentNumber: Int = 2101,
            name: String = "테스트 학생",
        ) = """{"id":$subject,"email":"private@gsm.hs.kr","role":"ADMIN","status":"ACTIVE","objectType":"STUDENT","student":{"id":999,"name":"$name","studentNumber":$studentNumber,"grade":$grade,"role":"$role","isLeaveSchool":$leave}}"""
    }
}
