package com.lcg.domain.system.presentation.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import com.lcg.domain.system.presentation.data.response.SystemStatusResDto
import com.lcg.domain.system.service.QuerySystemStatusService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "시스템", description = "서버 상태 확인 API")
@RestController
@RequestMapping("/api/v1/system")
class SystemController(
    private val querySystemStatusService: QuerySystemStatusService,
) {
    @Operation(summary = "서버 응답 확인")
    @ApiResponse(responseCode = "200", description = "서버가 요청을 처리할 수 있음")
    @GetMapping("/ping")
    fun ping(): ResponseEntity<SystemStatusResDto> =
        ResponseEntity.ok(querySystemStatusService.execute())
}
