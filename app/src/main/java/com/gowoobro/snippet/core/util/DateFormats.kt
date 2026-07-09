package com.gowoobro.snippet.core.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 서버 날짜/시간 포맷 처리 (02-data-api.md §2.4)
 *
 * - DTO는 날짜를 전부 String으로 보관하고 표시 시점에 파싱한다 (서버가 초/마이크로초 혼용).
 * - `createDate`, `startDate`, `endDate`, `returnDate`: ISO-8601 LocalDateTime (타임존 없음)
 * - `sessionDate`, `lastReadDate`: yyyy-MM-dd
 */
object DateFormats {

    /** ISO_LOCAL_DATE_TIME은 소수점 초(나노초)의 유무를 모두 허용한다. */
    private val serverDateTime: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** `2026-06-11T14:30:00` 또는 `2026-06-11T14:30:00.123456` → LocalDateTime (실패 시 null) */
    fun parseDateTime(value: String?): LocalDateTime? =
        value?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDateTime.parse(it, serverDateTime) }.getOrNull()
        }

    /** `2026-06-11` → LocalDate (실패 시 null) */
    fun parseDate(value: String?): LocalDate? =
        value?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    /** 서버 전송용 현재 시각 문자열 (ISO-8601 LocalDateTime) */
    fun nowDateTimeString(): String = LocalDateTime.now().format(serverDateTime)

    /** 서버 전송용 오늘 날짜 문자열 (yyyy-MM-dd) */
    fun todayString(): String = LocalDate.now().toString()
}
