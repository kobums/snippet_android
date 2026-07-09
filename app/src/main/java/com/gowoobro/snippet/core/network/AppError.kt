package com.gowoobro.snippet.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 에러 규약 (02-data-api.md §8).
 * 모든 Repository/UseCase 경계는 예외 대신 [AppResult]를 반환한다.
 */
sealed class AppError {
    /** 사용자 노출 메시지 */
    abstract val message: String

    /** 디버그용 원본 */
    abstract val details: String?

    /** 타임아웃 / 연결 실패 */
    data class NetworkError(
        override val message: String,
        override val details: String? = null,
    ) : AppError()

    /** HTTP 401, 403 */
    data class AuthError(
        override val message: String,
        override val details: String? = null,
    ) : AppError()

    /** HTTP 400~499 (401/403 제외) */
    data class ValidationError(
        override val message: String,
        override val details: String? = null,
    ) : AppError()

    /** HTTP 500+ */
    data class ServerError(
        val statusCode: Int?,
        override val message: String,
        override val details: String? = null,
    ) : AppError()

    /** 로컬 저장소 실패 */
    data class CacheError(
        override val message: String,
        override val details: String? = null,
    ) : AppError()

    data class UnknownError(
        override val message: String = MSG_UNKNOWN,
        override val details: String? = null,
    ) : AppError()

    companion object {
        const val MSG_TIMEOUT = "연결 시간이 초과되었습니다"
        const val MSG_OFFLINE = "네트워크 연결을 확인해주세요"
        const val MSG_AUTH = "인증에 실패했습니다"
        const val MSG_VALIDATION = "요청이 잘못되었습니다"
        const val MSG_SERVER = "서버 오류가 발생했습니다"
        const val MSG_UNKNOWN = "알 수 없는 오류가 발생했습니다"
    }
}

/**
 * Result 래퍼 규약: `Success(data) | Failure(AppError)`.
 * 예외는 [safeApiCall] 에서 전부 [AppError]로 매핑되어 레이어 경계를 넘지 않는다.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

/** 실패를 기본값으로 흡수 (추천, 목표, streak 등 조회성 API용 — §8.2) */
fun <T> AppResult<T>.getOrDefault(default: T): T = getOrNull() ?: default

/**
 * API 호출을 [AppResult]로 감싸고 예외를 [AppError]로 매핑한다.
 * 401 토큰 갱신은 이 매핑 **이전에** OkHttp Authenticator 레벨에서 처리된다.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: Throwable) {
    AppResult.Failure(e.toAppError())
}

fun Throwable.toAppError(): AppError = when (this) {
    is HttpException -> {
        val serverMessage = extractServerMessage(runCatching { response()?.errorBody()?.string() }.getOrNull())
        when (code()) {
            401, 403 -> AppError.AuthError(serverMessage ?: AppError.MSG_AUTH, message)
            in 400..499 -> AppError.ValidationError(serverMessage ?: AppError.MSG_VALIDATION, message)
            else -> AppError.ServerError(code(), serverMessage ?: AppError.MSG_SERVER, message)
        }
    }
    is SocketTimeoutException -> AppError.NetworkError(AppError.MSG_TIMEOUT, message)
    is UnknownHostException, is ConnectException -> AppError.NetworkError(AppError.MSG_OFFLINE, message)
    is IOException -> AppError.NetworkError(AppError.MSG_OFFLINE, message)
    else -> AppError.UnknownError(AppError.MSG_UNKNOWN, message)
}

/** 에러 바디 `{"message": "..."}` 에서 사용자 메시지 추출 (§2.3) */
fun extractServerMessage(errorBody: String?): String? {
    if (errorBody.isNullOrBlank()) return null
    return runCatching {
        Json.parseToJsonElement(errorBody).jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
