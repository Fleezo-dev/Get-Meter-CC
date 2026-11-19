package com.example.util

import kotlin.math.max

object FareCalculator {

    data class HourlyRentalFareResult(
        val billableHours: Long,
        val hourlyRate: Double,
        val includedKmPerHour: Double,
        val totalIncludedKm: Double,
        val actualDistanceKm: Double,
        val extraDistanceKm: Double,
        val extraKmRate: Double,
        val hourlyCharge: Double,
        val extraDistanceCharge: Double,
        val totalFare: Double
    )

    data class LocalFareResult(
        val baseFare: Double,
        val distanceKm: Double,
        val farePerKm: Double,
        val distanceCharge: Double,
        val waitingSeconds: Long,
        val waitFarePerMin: Double,
        val waitingCharge: Double,
        val totalFare: Double
    )

    data class OutstationFareResult(
        val distanceKm: Double,
        val ratePerKm: Double,
        val distanceCharge: Double,
        val driverBata: Double,
        val waitingSeconds: Long,
        val waitRatePerHour: Double,
        val waitingCharge: Double,
        val additionalCharges: Double,
        val minFare: Double,
        val totalFare: Double
    )

    /**
     * Calculates billable hourly slabs based on the mandatory 5-minute grace rule (> 300s):
     * - First Hour: 00:00 through 01:05:00 (0 - 3900s) -> 1 billable hour
     * - Second Hour: 01:05:01 through 02:05:00 (3901 - 7500s) -> 2 billable hours
     * - Third Hour: 02:05:01 through 03:05:00 (7501 - 11100s) -> 3 billable hours
     */
    fun calculateBillableHours(durationSeconds: Long): Long {
        if (durationSeconds <= 0L) return 1L
        val hours = durationSeconds / 3600L
        val remSec = durationSeconds % 3600L

        return if (hours == 0L) {
            1L
        } else {
            if (remSec > 300L) {
                // Strictly greater than 5-minute grace window (> 300 seconds), jumps to next billable hour slab
                hours + 1L
            } else {
                // Within the 5-minute grace window (<= 300 seconds)
                hours
            }
        }
    }

    fun calculateHourlyRentalFare(
        durationSeconds: Long,
        distanceKm: Double,
        hourlyRate: Double = 350.0,
        includedKmPerHour: Double = 10.0,
        extraKmRate: Double = 20.0
    ): HourlyRentalFareResult {
        val billableHours = calculateBillableHours(durationSeconds)
        val safeIncludedKmPerHr = if (includedKmPerHour > 0.0) includedKmPerHour else 10.0
        val totalIncludedKm = billableHours * safeIncludedKmPerHr
        val actualDist = max(0.0, distanceKm)
        val extraDist = max(0.0, actualDist - totalIncludedKm)

        val hourlyCharge = billableHours * 350.0
        val extraDistCharge = extraDist * extraKmRate
        val totalFare = hourlyCharge + extraDistCharge

        return HourlyRentalFareResult(
            billableHours = billableHours,
            hourlyRate = 350.0,
            includedKmPerHour = safeIncludedKmPerHr,
            totalIncludedKm = totalIncludedKm,
            actualDistanceKm = actualDist,
            extraDistanceKm = extraDist,
            extraKmRate = extraKmRate,
            hourlyCharge = hourlyCharge,
            extraDistanceCharge = extraDistCharge,
            totalFare = totalFare
        )
    }

    fun calculateLocalFare(
        distanceKm: Double,
        waitingSeconds: Long,
        baseFare: Double,
        farePerKm: Double,
        waitFarePerMin: Double
    ): LocalFareResult {
        val actualDist = max(0.0, distanceKm)
        val distCharge = actualDist * farePerKm
        val waitMins = max(0L, waitingSeconds) / 60.0
        val waitCharge = waitMins * waitFarePerMin
        val total = baseFare + distCharge + waitCharge
        return LocalFareResult(
            baseFare = baseFare,
            distanceKm = actualDist,
            farePerKm = farePerKm,
            distanceCharge = distCharge,
            waitingSeconds = max(0L, waitingSeconds),
            waitFarePerMin = waitFarePerMin,
            waitingCharge = waitCharge,
            totalFare = total
        )
    }

    fun calculateOutstationFare(
        distanceKm: Double,
        waitingSeconds: Long,
        ratePerKm: Double,
        driverBata: Double,
        waitRatePerHour: Double,
        additionalCharges: Double,
        minFare: Double
    ): OutstationFareResult {
        val actualDist = max(0.0, distanceKm)
        val distCharge = actualDist * ratePerKm
        val waitHours = max(0L, waitingSeconds) / 3600.0
        val waitCharge = if (waitRatePerHour > 0.0) waitHours * waitRatePerHour else 0.0
        val rawTotal = distCharge + driverBata + waitCharge + additionalCharges
        val total = if (minFare > 0.0) rawTotal.coerceAtLeast(minFare) else rawTotal
        return OutstationFareResult(
            distanceKm = actualDist,
            ratePerKm = ratePerKm,
            distanceCharge = distCharge,
            driverBata = driverBata,
            waitingSeconds = max(0L, waitingSeconds),
            waitRatePerHour = waitRatePerHour,
            waitingCharge = waitCharge,
            additionalCharges = additionalCharges,
            minFare = minFare,
            totalFare = total
        )
    }
}
