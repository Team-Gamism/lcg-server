package com.lcg.domain.user.service.impl

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.user.presentation.data.response.MeProfileResDto
import com.lcg.domain.user.repository.UserProfileRepository
import com.lcg.domain.user.service.QueryMyProfileService
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QueryMyProfileServiceImpl(
    private val profiles: UserProfileRepository,
) : QueryMyProfileService {
    @Transactional(readOnly = true)
    override fun execute(member: AuthenticatedMember): MeProfileResDto {
        val profile = profiles.findById(member.userId)
            .orElseThrow { ExpectedException(ApiErrorCode.NOT_FOUND) }
        return MeProfileResDto.of(member, profile)
    }
}
