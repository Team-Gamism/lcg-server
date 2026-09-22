package com.lcg.domain.auth.service

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.global.security.LcgPrincipal

interface QueryAuthenticatedMemberService {
    fun execute(principal: LcgPrincipal): AuthenticatedMember
}
