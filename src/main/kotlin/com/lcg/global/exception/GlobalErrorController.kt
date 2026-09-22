package com.lcg.global.exception

import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Hidden
@RestController
class GlobalErrorController(
    private val problemDetailFactory: ProblemDetailFactory,
) : ErrorController {
    @RequestMapping("/error")
    fun error(request: HttpServletRequest): ProblemDetail {
        val status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int ?: 500
        return problemDetailFactory.create(request, HttpStatusCode.valueOf(status))
    }
}
