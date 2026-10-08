package com.graz.salatuk.util

import kotlin.math.*

/**
 * Prayer Times Calculator
 * Astronomical calculations matching Salatuk's approach
 * (decompiled prayer names: sobh, shorook, dohr, asr, maghreb, ichaa)
 */

data class PrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val fajrHours: Double = 0.0,
    val sunriseHours: Double = 0.0,
    val dhuhrHours: Double = 0.0,
    val asrHours: Double = 0.0,
    val maghribHours: Double = 0.0,
    val ishaHours: Double = 0.0,
)

enum class CalculationMethod(
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaMinutes: Int = 0
) {
    MOROCCO("Habous (Maroc)", 19.0, 17.0),
    MWL("Ligue Mondiale", 18.0, 17.0),
    ISNA("ISNA", 15.0, 15.0),
    EGYPT("Égypte", 19.5, 17.5),
    MAKKAH("Umm al-Qura", 18.5, 0.0, 90),
    KARACHI("Karachi", 18.0, 18.0),
    TEHRAN("Téhéran", 17.7, 14.0),
    TURKEY("Diyanet", 18.0, 17.0),
    FRANCE("UOIF (France)", 12.0, 12.0),
    GULF("Golfe", 19.5, 0.0, 90),
}

enum class AsrJuristic(val label: String, val factor: Int) {
    SHAFI("Shafi'i / Maliki", 1),
    HANAFI("Hanafi", 2),
}

class PrayerTimesCalculator(
    private val method: CalculationMethod = CalculationMethod.MOROCCO,
    private val asrJuristic: AsrJuristic = AsrJuristic.SHAFI
) {
    private fun dtr(d: Double) = d * PI / 180.0
    private fun rtd(r: Double) = r * 180.0 / PI

    private fun fixAngle(a: Double): Double {
        var v = a - 360.0 * floor(a / 360.0)
        if (v < 0) v += 360.0
        return v
    }

    private fun fixHour(a: Double): Double {
        var v = a - 24.0 * floor(a / 24.0)
        if (v < 0) v += 24.0
        return v
    }

    private data class SunPosition(val declination: Double, val equation: Double)

    private fun sunPosition(jd: Double): SunPosition {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(dtr(g)) + 0.020 * sin(dtr(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = rtd(atan2(cos(dtr(e)) * sin(dtr(l)), cos(dtr(l)))) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = rtd(asin(sin(dtr(e)) * sin(dtr(l))))
        return SunPosition(decl, eqt)
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun midDay(jd: Double): Double {
        val sun = sunPosition(jd)
        return fixHour(12.0 - sun.equation)
    }

    private fun sunAngleTime(angle: Double, jd: Double, lat: Double, afterNoon: Boolean): Double {
        val sun = sunPosition(jd)
        val decl = sun.declination
        val noon = midDay(jd)
        val cosHA = (sin(dtr(angle)) - sin(dtr(decl)) * sin(dtr(lat))) /
                (cos(dtr(decl)) * cos(dtr(lat)))
        if (cosHA > 1.0 || cosHA < -1.0) return Double.NaN
        val ha = rtd(acos(cosHA)) / 15.0
        return noon + if (afterNoon) ha else -ha
    }

    private fun asrTime(jd: Double, lat: Double): Double {
        val sun = sunPosition(jd)
        val decl = sun.declination
        val noon = midDay(jd)
        val shadowLen = asrJuristic.factor + tan(dtr(abs(lat - decl)))
        val angle = rtd(atan(1.0 / shadowLen))
        val cosHA = (sin(dtr(angle)) - sin(dtr(decl)) * sin(dtr(lat))) /
                (cos(dtr(decl)) * cos(dtr(lat)))
        if (cosHA > 1.0 || cosHA < -1.0) return Double.NaN
        val ha = rtd(acos(cosHA)) / 15.0
        return noon + ha
    }

    fun calculate(year: Int, month: Int, day: Int, lat: Double, lng: Double, timezone: Double): PrayerTimes {
        val jd = julianDate(year, month, day) - lng / (15.0 * 24.0)
        val diff = timezone - lng / 15.0

        val fajr = sunAngleTime(-method.fajrAngle, jd, lat, false) + diff
        val sunrise = sunAngleTime(-0.833, jd, lat, false) + diff
        val dhuhr = midDay(jd) + diff
        val asr = asrTime(jd, lat) + diff
        val sunset = sunAngleTime(-0.833, jd, lat, true) + diff
        val isha = if (method.ishaMinutes > 0) {
            sunset + method.ishaMinutes / 60.0
        } else {
            sunAngleTime(-method.ishaAngle, jd, lat, true) + diff
        }

        return PrayerTimes(
            fajr = formatTime(fajr),
            sunrise = formatTime(sunrise),
            dhuhr = formatTime(dhuhr),
            asr = formatTime(asr),
            maghrib = formatTime(sunset),
            isha = formatTime(isha),
            fajrHours = fixHour(fajr),
            sunriseHours = fixHour(sunrise),
            dhuhrHours = fixHour(dhuhr),
            asrHours = fixHour(asr),
            maghribHours = fixHour(sunset),
            ishaHours = fixHour(isha),
        )
    }

    private fun formatTime(hours: Double): String {
        if (hours.isNaN()) return "--:--"
        val h = fixHour(hours + 0.5 / 60.0)
        val hh = h.toInt()
        val mm = ((h - hh) * 60).toInt()
        return "%02d:%02d".format(hh, mm)
    }

    companion object {
        fun qiblaDirection(lat: Double, lng: Double): Double {
            val kaabaLat = 21.4225
            val kaabaLng = 39.8262
            val dLng = (kaabaLng - lng) * PI / 180.0
            val latR = lat * PI / 180.0
            val kLatR = kaabaLat * PI / 180.0
            val y = sin(dLng)
            val x = cos(latR) * tan(kLatR) - sin(latR) * cos(dLng)
            var q = atan2(y, x) * 180.0 / PI
            return (q + 360.0) % 360.0
        }
    }
}
