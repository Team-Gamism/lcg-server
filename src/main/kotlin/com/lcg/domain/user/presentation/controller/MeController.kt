package com.lcg.domain.user.presentation.controller

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.user.presentation.data.request.UpdateMyProfileReqDto
import com.lcg.domain.user.service.QueryMyProfileService
import com.lcg.domain.user.service.UpdateMyProfileService
import com.lcg.global.security.SessionAccessFilter
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Me")
@RestController
class MeController(
    private val queryMyProfile: QueryMyProfileService,
    private val updateMyProfile: UpdateMyProfileService,
) {
    @Operation(summary = "내 프로필 조회")
    @GetMapping("/api/v1/me")
    fun me(@RequestAttribute(SessionAccessFilter.MEMBER_ATTRIBUTE) member: AuthenticatedMember) = queryMyProfile.execute(member)

    @Operation(summary = "내 프로필 수정")
    @PatchMapping("/api/v1/me")
    fun update(
        @RequestAttribute(SessionAccessFilter.MEMBER_ATTRIBUTE) member: AuthenticatedMember,
        @Valid @RequestBody request: UpdateMyProfileReqDto,
    ) = updateMyProfile.execute(member, request)
}
