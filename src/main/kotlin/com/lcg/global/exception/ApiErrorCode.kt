package com.lcg.global.exception

import org.springframework.http.HttpStatus

enum class ApiErrorCode(val status: HttpStatus, val defaultMessage: String) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이나 입력값을 확인해 주세요."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없거나 보안 검증에 실패했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "현재 상태에서는 요청을 처리할 수 없습니다."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "요청 크기가 제한을 초과했습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청 처리 중 오류가 발생했습니다."),
    UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 응답을 처리할 수 없습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "현재 서비스를 사용할 수 없습니다."),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "외부 서비스 응답이 지연되고 있습니다."),
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "로그인 요청이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요."),
    OAUTH_CODE_REJECTED(HttpStatus.BAD_REQUEST, "학교 인증 코드가 유효하지 않습니다. 다시 로그인해 주세요."),
    OAUTH_DENIED(HttpStatus.FORBIDDEN, "학교 로그인이 취소되거나 거절되었습니다."),
    SCHOOL_MEMBERSHIP_REQUIRED(HttpStatus.FORBIDDEN, "현재 재학 중인 활성 학교 계정만 이용할 수 있습니다."),
}
