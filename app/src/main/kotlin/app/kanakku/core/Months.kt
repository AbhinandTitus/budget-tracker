package app.kanakku.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Calendar-month arithmetic in the device's time zone. */
object Months {
    fun start(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Long =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun end(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Long = start(month.plusMonths(1), zone)

    fun of(millis: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

    fun dateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /**
     * Salary credited in the last days of a month pays for the month that follows.
     * Returns the instant the credit should be filed under.
     */
    fun salaryEffective(occurredAt: Long, shiftLate: Boolean, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (!shiftLate) return occurredAt
        val date = dateOf(occurredAt, zone)
        return if (date.dayOfMonth >= LATE_SALARY_DAY) start(YearMonth.from(date).plusMonths(1), zone) else occurredAt
    }

    const val LATE_SALARY_DAY = 25
}
