package com.lcg.domain.user.presentation.data.request

import com.lcg.domain.user.entity.UserPosition
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "내 프로필 수정 요청. Riot ID와 포지션, 소개는 프로필 전체 상태를 기준으로 함께 전송합니다.")
data class UpdateMyProfileReqDto(
    @field:Size(max = 22)
    @field:Schema(example = "GameName#0000")
    val riotId: String? = null,

    val primaryPosition: UserPosition? = null,

    val secondaryPosition: UserPosition? = null,

    @field:Size(max = 500)
    val introduction: String? = null,
)
