package com.lcg.domain.user.service

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.user.presentation.data.response.MeProfileResDto

interface QueryMyProfileService {
    fun execute(member: AuthenticatedMember): MeProfileResDto
}
