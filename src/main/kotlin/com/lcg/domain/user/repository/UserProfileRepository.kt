package com.lcg.domain.user.repository

import com.lcg.domain.user.entity.UserProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfile, UUID> {
}
