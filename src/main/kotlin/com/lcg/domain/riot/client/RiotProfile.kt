package com.lcg.domain.riot.client

data class RiotProfile(
    val account: RiotAccount,
    val summoner: RiotSummoner?,
    val leagueEntries: List<RiotLeagueEntry>,
)

data class RiotAccount(
    val puuid: String,
    val gameName: String,
    val tagLine: String,
)

data class RiotSummoner(
    val id: String,
    val profileIconId: Int?,
    val summonerLevel: Long?,
    val revisionDate: Long?,
)

data class RiotLeagueEntry(
    val queueType: String,
    val tier: String,
    val rank: String,
    val leaguePoints: Int,
    val wins: Int,
    val losses: Int,
    val hotStreak: Boolean,
    val veteran: Boolean,
    val freshBlood: Boolean,
    val inactive: Boolean,
)
