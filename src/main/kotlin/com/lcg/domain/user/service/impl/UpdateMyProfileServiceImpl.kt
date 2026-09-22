package com.lcg.domain.user.service.impl

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.user.entity.UserPosition
import com.lcg.domain.user.presentation.data.request.UpdateMyProfileReqDto
import com.lcg.domain.user.presentation.data.response.MeProfileResDto
import com.lcg.domain.user.repository.UserProfileRepository
import com.lcg.domain.user.service.UpdateMyProfileService
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer
import java.time.Instant

@Service
class UpdateMyProfileServiceImpl(
    private val profiles: UserProfileRepository,
) : UpdateMyProfileService {
    @Transactional
    override fun execute(member: AuthenticatedMember, request: UpdateMyProfileReqDto): MeProfileResDto {
        validatePositions(request.primaryPosition, request.secondaryPosition)
        val profile = profiles.findById(member.userId)
            .orElseThrow { ExpectedException(ApiErrorCode.NOT_FOUND) }
        profile.update(
            riotId = request.riotId?.let(::normalizeRiotId),
            primaryPosition = request.primaryPosition,
            secondaryPosition = request.secondaryPosition,
            introduction = request.introduction?.trim()?.takeIf(String::isNotEmpty),
            updatedAt = Instant.now(),
        )
        return MeProfileResDto.of(member, profile)
    }

    private fun normalizeRiotId(rawRiotId: String): String {
        val riotId = Normalizer.normalize(rawRiotId.trim(), Normalizer.Form.NFKC)
        val parts = riotId.split('#')
        if (parts.size != 2 || parts[0].length !in 3..16 || parts[1].length !in 3..5 ||
            parts.any(String::isBlank) || riotId.any(Char::isISOControl)) {
            throw ExpectedException(ApiErrorCode.INVALID_REQUEST)
        }
        return riotId
    }

    private fun validatePositions(primary: UserPosition?, secondary: UserPosition?) {
        if (secondary != null && (primary == null || primary == secondary)) {
            throw ExpectedException(ApiErrorCode.INVALID_REQUEST)
        }
    }
}
