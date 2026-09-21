package com.lcg.global.exception

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.lcg.global.filter.RequestIdFilter
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class ProblemDetailFactory(private val jsonMapper: JsonMapper) {
    fun create(
        request: HttpServletRequest,
        status: HttpStatusCode,
        errorCode: ApiErrorCode = errorCodeFor(status),
        detail: String = errorCode.defaultMessage,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail).apply {
        title = HttpStatus.resolve(status.value())?.reasonPhrase ?: "Request failed"
        setProperty("code", errorCode.name)
        setProperty("traceId", request.getAttribute(RequestIdFilter.ATTRIBUTE))
    }

    fun write(request: HttpServletRequest, response: HttpServletResponse, errorCode: ApiErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        jsonMapper.writeValue(response.outputStream, create(request, errorCode.status, errorCode))
    }

    private fun errorCodeFor(status: HttpStatusCode): ApiErrorCode =
        ApiErrorCode.entries.firstOrNull { it.status.value() == status.value() }
            ?: if (status.is4xxClientError) ApiErrorCode.INVALID_REQUEST else ApiErrorCode.INTERNAL_ERROR
}
