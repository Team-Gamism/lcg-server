package com.lcg.domain.user.presentation.data.response

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.user.entity.UserRole
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "현재 LCG 회원. 학교 원본 식별자와 개인정보는 포함하지 않습니다.")
data class MeResDto(val id: UUID, val role: UserRole, val grade: Int) {
    companion object {
        fun of(member: AuthenticatedMember) = MeResDto(member.userId, member.role, member.grade)
    }
}
