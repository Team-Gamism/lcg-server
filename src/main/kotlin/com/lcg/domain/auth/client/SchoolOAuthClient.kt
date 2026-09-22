package com.lcg.domain.auth.client

interface SchoolOAuthClient {
    fun authorization(state: String): SchoolAuthorization
    fun authenticate(code: String, verifier: String): SchoolAccount
}

// Deliberately omit generated toString methods for values containing OAuth credentials.
class SchoolAuthorization(val url: String, val verifier: String)
class SchoolAccount(val providerUserId: String, val grade: Int?, val eligible: Boolean)
