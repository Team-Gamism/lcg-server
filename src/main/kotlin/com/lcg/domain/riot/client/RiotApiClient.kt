package com.lcg.domain.riot.client

interface RiotApiClient {
    fun findProfile(riotId: RiotId): RiotProfile
}

data class RiotId(
    val gameName: String,
    val tagLine: String,
) {
    init {
        require(gameName.isNotBlank() && gameName.none(Char::isISOControl)) { "Riot game name is invalid" }
        require(tagLine.isNotBlank() && tagLine.none(Char::isISOControl)) { "Riot tag line is invalid" }
    }

    companion object {
        fun parse(value: String): RiotId {
            val parts = value.split('#')
            require(parts.size == 2) { "Riot ID must contain one # separator" }
            return RiotId(parts[0], parts[1])
        }
    }
}
