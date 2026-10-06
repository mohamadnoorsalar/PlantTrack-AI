package com.example.util

import java.util.Calendar
import java.util.Date
import java.util.Locale

object JalaliDateHelper {

    private val persianMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    data class JalaliDate(
        val year: Int,
        val month: Int,
        val day: Int,
        val monthName: String
    )

    /**
     * Converts Gregorian year, month (1-12), and day (1-31) to Jalali (Solar Hijri).
     * Standard mathematical algorithm without external dependencies.
     */
    fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy = gYear - 1600
        var gm = gMonth - 1
        var gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        gDayNo += gDaysInMonth[gm] + gd
        if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            gDayNo++
        }

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        val jm: Int
        val jd: Int
        if (jDayNo < 186) {
            jm = 1 + jDayNo / 31
            jd = 1 + jDayNo % 31
        } else {
            jm = 7 + (jDayNo - 186) / 30
            jd = 1 + (jDayNo - 186) % 30
        }

        val monthName = persianMonths.getOrElse(jm - 1) { "" }
        return JalaliDate(year = jy, month = jm, day = jd, monthName = monthName)
    }

    /**
     * Formats a epoch timestamp into a beautiful Persian date string:
     * e.g. "۱۵ مهر ۱۴۰۵ - ساعت ۱۵:۲۷"
     */
    fun formatToPersianDateTime(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }

        val gYear = calendar.get(Calendar.YEAR)
        val gMonth = calendar.get(Calendar.MONTH) + 1
        val gDay = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val jDate = gregorianToJalali(gYear, gMonth, gDay)

        val hourStr = String.format(Locale.US, "%02d", hour)
        val minStr = String.format(Locale.US, "%02d", minute)

        val raw = "${jDate.day} ${jDate.monthName} ${jDate.year} - ساعت $hourStr:$minStr"
        return toPersianDigits(raw)
    }

    /**
     * Converts English numbers 0-9 to Persian digits ۰-۹.
     */
    fun toPersianDigits(input: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val sb = java.lang.StringBuilder()
        for (char in input) {
            if (char in '0'..'9') {
                sb.append(persianDigits[char - '0'])
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }
}
