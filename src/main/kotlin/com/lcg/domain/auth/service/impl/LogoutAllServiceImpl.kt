package com.lcg.domain.auth.service.impl

import com.lcg.domain.auth.service.LogoutAllService
import com.lcg.domain.user.repository.UserRepository
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class LogoutAllServiceImpl(private val users: UserRepository) : LogoutAllService {
    @Transactional
    override fun execute(userId: UUID) {
        val user = users.findForUpdate(userId) ?: throw ExpectedException(ApiErrorCode.UNAUTHENTICATED)
        user.sessionVersion++
        user.updatedAt = Instant.now()
    }
}
