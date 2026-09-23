package com.lcg.domain.user.presentation.data.response

import com.lcg.domain.auth.model.AuthenticatedMember
import com.lcg.domain.schoolIdentity.entity.SchoolIdentity
import com.lcg.domain.user.entity.UserPosition
import com.lcg.domain.user.entity.UserProfile
import com.lcg.domain.user.entity.UserRole
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "현재 회원의 프로필. 토큰·이메일·성별·기숙사 등은 포함하지 않습니다.")
data class MeProfileResDto(
    val id: UUID,
    val role: UserRole,
    val grade: Int,
    @Schema(description = "DataGSM에서 갱신하는 학번과 이름")
    val name: String?,
    @Schema(description = "사용자가 입력한 Riot ID. 아직 Riot 계정 소유를 검증하지 않았습니다.")
    val riotId: String?,
    val primaryPosition: UserPosition?,
    val secondaryPosition: UserPosition?,
    val introduction: String?,
) {
    companion object {
        fun of(member: AuthenticatedMember, profile: UserProfile, identity: SchoolIdentity) = MeProfileResDto(
            id = member.userId,
            role = member.role,
            grade = member.grade,
            name = identity.studentNumber?.let { number ->
                identity.studentName?.let { studentName -> "$number $studentName" }
            },
            riotId = profile.riotId,
            primaryPosition = profile.primaryPosition,
            secondaryPosition = profile.secondaryPosition,
            introduction = profile.introduction,
        )
    }
}
