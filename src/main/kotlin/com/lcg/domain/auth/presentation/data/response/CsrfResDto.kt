package com.lcg.domain.auth.presentation.data.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "변경 요청에 사용할 CSRF 헤더와 토큰. 로그인 후 다시 조회합니다.")
data class CsrfResDto(val headerName: String, val token: String)
