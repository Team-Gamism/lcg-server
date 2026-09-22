package com.lcg.domain.auth.service

import com.lcg.domain.auth.model.AuthenticatedMember

interface CompleteSchoolLoginService {
    fun execute(state: String?, browserSecret: String?, code: String?, error: String?): AuthenticatedMember
}
