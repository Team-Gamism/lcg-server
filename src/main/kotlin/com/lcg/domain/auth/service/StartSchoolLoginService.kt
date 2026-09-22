package com.lcg.domain.auth.service

interface StartSchoolLoginService {
    fun execute(): SchoolLoginAttempt
}

class SchoolLoginAttempt(val state: String, val browserSecret: String, val authorizationUrl: String)
