package com.lcg.domain.auth.client

import com.lcg.global.config.DataGsmProperties
import com.lcg.global.exception.ApiErrorCode
import com.lcg.global.exception.ExpectedException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException
import team.themoment.datagsm.sdk.oauth.model.AccountObjectType
import team.themoment.datagsm.sdk.oauth.model.AccountStatus
import team.themoment.datagsm.sdk.oauth.model.Student
import team.themoment.datagsm.sdk.oauth.model.StudentRole
import java.io.InterruptedIOException

@Component
class DataGsmSchoolOAuthClient(
    private val clients: ObjectProvider<DataGsmOAuthClient>,
    private val properties: DataGsmProperties,
) : SchoolOAuthClient {
    override fun authorization(state: String): SchoolAuthorization {
        val builder = client().createAuthorizationUrl(properties.callbackUri)
            .scope("datagsm:self_read")
            .state(state)
            .enablePkce()
        return SchoolAuthorization(builder.build(), builder.codeVerifier)
    }

    override fun authenticate(code: String, verifier: String): SchoolAccount {
        val client = client()
        val token = call(tokenExchange = true) {
            client.exchangeCodeForToken(code, properties.callbackUri, verifier)
        } ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        val accessToken = token.accessToken
        if (accessToken.isNullOrBlank() || !"Bearer".equals(token.tokenType, ignoreCase = true) ||
            token.expiresIn == null || token.expiresIn <= 0 || accessToken.any { it.isWhitespace() }) {
            throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        }
        val info = call { client.getUserInfo(accessToken) }
            ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        val subject = info.id?.takeIf { it > 0 } ?: throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
        val student = info.student
        val grade = student?.grade?.takeIf { it in 1..3 }
        val schoolName = student?.let(::schoolName)
        val eligible = info.status == AccountStatus.ACTIVE && info.objectType == AccountObjectType.STUDENT &&
            student != null && student.getIsLeaveSchool() == false && grade != null &&
            schoolName != null && student.role in
            setOf(
                StudentRole.GENERAL_STUDENT,
                StudentRole.STUDENT_COUNCIL,
                StudentRole.DORMITORY_MANAGER,
            )
        return SchoolAccount(subject.toString(), grade, eligible, schoolName)
    }

    private fun client(): DataGsmOAuthClient = clients.ifAvailable
        ?: throw ExpectedException(ApiErrorCode.SERVICE_UNAVAILABLE)

    private fun schoolName(student: Student): String? {
        val studentNumber = student.studentNumber?.takeIf { it in 1000..9999 } ?: return null
        val name = student.name?.trim()?.takeIf {
            it.length in 2..50 && it.none(Char::isISOControl)
        } ?: return null
        return "$studentNumber $name"
    }

    private fun <T> call(tokenExchange: Boolean = false, action: () -> T): T = try {
        action()
    } catch (ex: DataGsmException) {
        val error = when {
            generateSequence<Throwable>(ex) { it.cause }.any { it is InterruptedIOException } -> ApiErrorCode.UPSTREAM_TIMEOUT
            tokenExchange && ex.statusCode == 400 -> ApiErrorCode.OAUTH_CODE_REJECTED
            ex.statusCode == 429 -> ApiErrorCode.SERVICE_UNAVAILABLE
            else -> ApiErrorCode.UPSTREAM_ERROR
        }
        // SDK exceptions may include the raw response. Do not log or attach them.
        throw ExpectedException(error)
    } catch (_: RuntimeException) {
        throw ExpectedException(ApiErrorCode.UPSTREAM_ERROR)
    }
}
