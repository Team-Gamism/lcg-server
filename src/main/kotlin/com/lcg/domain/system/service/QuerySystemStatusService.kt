package com.lcg.domain.system.service

import com.lcg.domain.system.presentation.data.response.SystemStatusResDto

interface QuerySystemStatusService {
    fun execute(): SystemStatusResDto
}
