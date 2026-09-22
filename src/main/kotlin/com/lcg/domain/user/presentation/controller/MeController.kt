package com.lcg.domain.user.presentation.controller

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.user.presentation.data.response.MeResDto
import com.lcg.global.security.SessionAccessFilter
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Me")
@RestController
class MeController {
    @Operation(summary = "내 회원 정보 조회")
    @GetMapping("/api/v1/me")
    fun me(@RequestAttribute(SessionAccessFilter.MEMBER_ATTRIBUTE) member: AuthenticatedMember) = MeResDto.of(member)
}
