package com.lcg.domain.user.service

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.user.presentation.data.request.UpdateMyProfileReqDto
import com.lcg.domain.user.presentation.data.response.MeProfileResDto

interface UpdateMyProfileService {
    fun execute(member: AuthenticatedMember, request: UpdateMyProfileReqDto): MeProfileResDto
}
