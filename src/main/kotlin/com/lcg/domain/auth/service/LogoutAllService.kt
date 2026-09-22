package com.lcg.domain.auth.service

import java.util.UUID

interface LogoutAllService {
    fun execute(userId: UUID)
}
