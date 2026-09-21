package com.lcg.global.exception

import org.springframework.http.HttpStatus

class ExpectedException(
    override val message: String,
    val statusCode: HttpStatus,
    val errorCode: ApiErrorCode,
) : RuntimeException(message) {
    constructor(errorCode: ApiErrorCode) : this(
        message = errorCode.defaultMessage,
        statusCode = errorCode.status,
        errorCode = errorCode,
    )
}
