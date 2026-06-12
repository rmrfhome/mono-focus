package com.monofocus.app.domain

import java.time.ZonedDateTime

internal const val PAUSE_FIFTEEN_MINUTES_MILLIS = 15L * 60L * 1_000L

internal fun pauseForFifteenMinutesFrom(nowEpochMillis: Long): Long =
    nowEpochMillis + PAUSE_FIFTEEN_MINUTES_MILLIS

internal fun pauseUntilTomorrowFrom(now: ZonedDateTime): Long =
    now.toLocalDate()
        .plusDays(1)
        .atStartOfDay(now.zone)
        .toInstant()
        .toEpochMilli()

internal fun activePauseUntil(
    pausedUntilEpochMillis: Long,
    nowEpochMillis: Long,
): Long =
    if (pausedUntilEpochMillis > nowEpochMillis) pausedUntilEpochMillis else 0L

internal fun isPauseActive(
    pausedUntilEpochMillis: Long,
    nowEpochMillis: Long,
): Boolean =
    activePauseUntil(
        pausedUntilEpochMillis = pausedUntilEpochMillis,
        nowEpochMillis = nowEpochMillis,
    ) > 0L
