package com.lcg.domain.system.presentation.data.response

import io.swagger.v3.oas.annotations.media.Schema

data class SystemStatusResDto(
    @Schema(description = "서비스 이름", example = "lcg-server")
    val service: String,
    @Schema(description = "서버 상태", example = "ok")
    val status: String,
)
