package com.lcg.domain.auth.service

import com.lcg.domain.auth.client.SchoolAccount
import com.lcg.domain.auth.model.AuthenticatedMember

interface UpsertSchoolMemberService {
    fun execute(account: SchoolAccount): AuthenticatedMember?
}
