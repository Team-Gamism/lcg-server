package com.lcg.global.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler(
    private val problemDetailFactory: ProblemDetailFactory,
) : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val servletRequest = (request as ServletWebRequest).request
        val problem = problemDetailFactory.create(servletRequest, statusCode)
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request)
    }

    @ExceptionHandler(ExpectedException::class)
    fun handleExpectedException(ex: ExpectedException, request: HttpServletRequest): ProblemDetail =
        problemDetailFactory.create(request, ex.statusCode, ex.errorCode, ex.message)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(request: HttpServletRequest): ProblemDetail =
        problemDetailFactory.create(request, ApiErrorCode.FORBIDDEN.status, ApiErrorCode.FORBIDDEN)

    @ExceptionHandler(Exception::class)
    fun handleInternalException(ex: Exception, request: HttpServletRequest): ProblemDetail {
        log.error("처리되지 않은 예외 발생: exceptionType={}", ex.javaClass.name)
        return problemDetailFactory.create(
            request,
            ApiErrorCode.INTERNAL_ERROR.status,
            ApiErrorCode.INTERNAL_ERROR,
        )
    }
}
