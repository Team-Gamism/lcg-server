package com.lcg.domain.riot.client

import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.Closeable
import java.io.IOException
import java.io.InterruptedIOException

class RiotHttpClient(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val accountApiBaseUrl: HttpUrl = apiBaseUrl("asia.api.riotgames.com"),
    private val platformApiBaseUrl: HttpUrl = apiBaseUrl("kr.api.riotgames.com"),
    private val mapper: JsonMapper = JsonMapper.builder().build(),
) : RiotApiClient, Closeable {
    override fun findProfile(riotId: RiotId): RiotProfile {
        val account = account(getJson(
            accountApiBaseUrl.withPath("riot", "account", "v1", "accounts", "by-riot-id", riotId.gameName, riotId.tagLine),
            notFoundCode = ApiErrorCode.NOT_FOUND,
        ) ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR))
        val summoner = getJson(
            platformApiBaseUrl.withPath("lol", "summoner", "v4", "summoners", "by-puuid", account.puuid),
            allowNotFound = true,
        )?.let(::summoner)
        if (summoner == null) {
            return RiotProfile(account, null, emptyList())
        }
        val entries = leagueEntries(getJson(
            platformApiBaseUrl.withPath("lol", "league", "v4", "entries", "by-puuid", account.puuid),
        ) ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR))
        return RiotProfile(account, summoner, entries)
    }

    override fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
        http.cache?.close()
    }

    private fun getJson(
        url: HttpUrl,
        allowNotFound: Boolean = false,
        notFoundCode: ApiErrorCode = ApiErrorCode.UPSTREAM_ERROR,
    ): JsonNode? = try {
        val request = Request.Builder()
            .url(url)
            .header("X-Riot-Token", apiKey)
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> response.readJson()
                response.code == 404 && allowNotFound -> null
                response.code == 404 -> throw ExpectedException(notFoundCode)
                response.code == 429 || response.code == 401 || response.code == 403 ->
                    throw ExpectedException(ApiErrorCode.SERVICE_UNAVAILABLE)
                else -> throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
            }
        }
    } catch (ex: ExpectedException) {
        throw ex
    } catch (_: InterruptedIOException) {
        throw ExpectedException(ApiErrorCode.UPSTREAM_TIMEOUT)
    } catch (_: IOException) {
        throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
    } catch (_: RuntimeException) {
        throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
    }

    private fun Response.readJson(): JsonNode {
        val body = body ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        return body.byteStream().use { stream ->
            mapper.readTree(stream) ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        }
    }

    private fun account(node: JsonNode): RiotAccount {
        node.requireObject()
        return RiotAccount(
            puuid = node.requiredText("puuid"),
            gameName = node.requiredText("gameName"),
            tagLine = node.requiredText("tagLine"),
        )
    }

    private fun summoner(node: JsonNode): RiotSummoner {
        node.requireObject()
        return RiotSummoner(
            id = node.requiredText("id"),
            profileIconId = node.optionalNonNegativeInt("profileIconId"),
            summonerLevel = node.optionalNonNegativeLong("summonerLevel"),
            revisionDate = node.optionalNonNegativeLong("revisionDate"),
        )
    }

    private fun leagueEntries(node: JsonNode): List<RiotLeagueEntry> {
        if (!node.isArray) {
            throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        }
        return node.toList().map(::leagueEntry)
    }

    private fun leagueEntry(node: JsonNode): RiotLeagueEntry {
        node.requireObject()
        return RiotLeagueEntry(
            queueType = node.requiredText("queueType"),
            tier = node.requiredText("tier"),
            rank = node.requiredText("rank"),
            leaguePoints = node.requiredNonNegativeInt("leaguePoints"),
            wins = node.requiredNonNegativeInt("wins"),
            losses = node.requiredNonNegativeInt("losses"),
            hotStreak = node.requiredBoolean("hotStreak"),
            veteran = node.requiredBoolean("veteran"),
            freshBlood = node.requiredBoolean("freshBlood"),
            inactive = node.requiredBoolean("inactive"),
        )
    }

    private fun JsonNode.requireObject() {
        if (!isObject) {
            throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        }
    }

    private fun JsonNode.requiredText(field: String): String = path(field).asString()
        .takeIf { it.isNotBlank() && it.none(Char::isISOControl) }
        ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)

    private fun JsonNode.requiredNonNegativeInt(field: String): Int = path(field).asString().toIntOrNull()
        ?.takeIf { it >= 0 }
        ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)

    private fun JsonNode.optionalNonNegativeInt(field: String): Int? {
        val value = path(field).asString()
        if (value.isBlank()) {
            return null
        }
        return value.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
    }

    private fun JsonNode.optionalNonNegativeLong(field: String): Long? {
        val value = path(field).asString()
        if (value.isBlank()) {
            return null
        }
        return value.toLongOrNull()?.takeIf { it >= 0 }
            ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
    }

    private fun JsonNode.requiredBoolean(field: String): Boolean = when (path(field).asString()) {
        "true" -> true
        "false" -> false
        else -> throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
    }

    private fun HttpUrl.withPath(vararg segments: String): HttpUrl = newBuilder().apply {
        segments.forEach(::addPathSegment)
    }.build()

    companion object {
        private fun apiBaseUrl(host: String): HttpUrl = HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .build()
    }
}
