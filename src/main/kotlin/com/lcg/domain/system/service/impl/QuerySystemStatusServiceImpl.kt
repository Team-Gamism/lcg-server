package com.lcg.domain.system.service.impl

import com.lcg.domain.system.presentation.data.response.SystemStatusResDto
import com.lcg.domain.system.service.QuerySystemStatusService
import org.springframework.stereotype.Service

@Service
class QuerySystemStatusServiceImpl : QuerySystemStatusService {
    override fun execute(): SystemStatusResDto =
        SystemStatusResDto(service = "lcg-server", status = "ok")
}
