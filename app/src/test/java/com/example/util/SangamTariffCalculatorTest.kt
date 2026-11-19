package com.example.util

import com.example.util.SangamTariffCalculator.CommissionResult
import com.example.util.SangamTariffCalculator.DriverBalanceStatus
import com.example.util.SangamTariffCalculator.TripCalculationInput
import com.example.util.SangamTariffConfig.Above25KmAnd800Rule
import com.example.util.SangamTariffConfig.CommissionBoundaryPolicy
import com.example.util.SangamTariffConfig.LocalCommissionConfig
import com.example.util.SangamTariffConfig.LocalFiveKmBoundaryPolicy
import com.example.util.SangamTariffConfig.SangamTripType
import com.example.util.SangamTariffConfig.VehicleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SangamTariffCalculatorTest {

    @Test
    fun `only 5 vehicle categories exist with no Tempo or Urbania`() {
        val categories = VehicleCategory.entries
        assertEquals(5, categories.size)
        assertEquals(
            listOf(
                VehicleCategory.MINI,
                VehicleCategory.SEDAN,
                VehicleCategory.SUV,
                VehicleCategory.SUV_PLUS,
                VehicleCategory.INNOVA
            ),
            categories
        )
    }

    @Test
    fun `local normal rates compute exact amounts for MINI SEDAN SUV SUV_PLUS INNOVA`() {
        val mini = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.MINI, distanceKm = 4.0)
        assertEquals(BigDecimal("192.00"), mini.customerBillTotal) // 80 + 4*28 = 192
        assertTrue(mini.commissionResult is CommissionResult.Confirmed)
        assertEquals(BigDecimal("30.00"), (mini.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("162.00"), (mini.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        val sedan = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.SEDAN, distanceKm = 12.0, isOutsideCity = true, isNightTrip = true)
        // 80 + 12*28 + 80 + 50 = 546
        assertEquals(BigDecimal("546.00"), sedan.customerBillTotal)
        assertEquals(BigDecimal("80.00"), (sedan.commissionResult as CommissionResult.Confirmed).amount)

        val suv = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.SUV, distanceKm = 4.0)
        assertEquals(BigDecimal("340.00"), suv.customerBillTotal)

        val suvPlus = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.SUV_PLUS, distanceKm = 4.0)
        assertEquals(BigDecimal("430.00"), suvPlus.customerBillTotal)

        val innova = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.INNOVA, distanceKm = 4.0)
        assertEquals(BigDecimal("450.00"), innova.customerBillTotal)
    }

    @Test
    fun `local minimum bill slabs and exact 5 KM boundary flag`() {
        val under5 = SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.MINI, distanceKm = 3.5)
        assertEquals(BigDecimal("200.00"), under5.customerBillTotal)
        assertTrue(under5.ambiguities.isEmpty())

        val exact5 = SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.SEDAN, distanceKm = 5.0)
        assertTrue(exact5.hasUnresolvedAmbiguity)
        assertEquals("MIN_BILL_5KM_OVERLAP", exact5.ambiguities.first().code)

        val exact5As250 = SangamTariffCalculator.calculateLocalMinimumBill(
            category = VehicleCategory.SEDAN,
            distanceKm = 5.0,
            fiveKmPolicy = LocalFiveKmBoundaryPolicy.INCLUDE_IN_5_TO_6_KM_250
        )
        assertEquals(BigDecimal("250.00"), exact5As250.customerBillTotal)
        assertTrue(exact5As250.ambiguities.isEmpty())

        assertEquals(BigDecimal("250.00"), SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.MINI, 5.6).customerBillTotal)
        assertEquals(BigDecimal("276.00"), SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.MINI, 7.0).customerBillTotal)

        assertEquals(BigDecimal("450.00"), SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.SUV, 7.0).customerBillTotal)
        assertEquals(BigDecimal("480.00"), SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.SUV, 8.0).customerBillTotal)
        assertEquals(BigDecimal("700.00"), SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.SUV_PLUS, 10.0).customerBillTotal)
        assertEquals(BigDecimal("745.00"), SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.SUV_PLUS, 11.0).customerBillTotal)
        assertEquals(BigDecimal("800.00"), SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.INNOVA, 10.0).customerBillTotal)
        assertEquals(BigDecimal("850.00"), SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.INNOVA, 12.0).customerBillTotal)
    }

    @Test
    fun `local commission isolates 6-10 km range, 5-6 km gap, 10 km overlap, and above-25-km vs above-800-bill ambiguity`() {
        val eightKm = SangamTariffCalculator.calculateLocalCommission(8.0, BigDecimal("304.00"))
        assertTrue(eightKm is CommissionResult.NeedsConfirmation)
        eightKm as CommissionResult.NeedsConfirmation
        assertEquals(BigDecimal("60.00"), eightKm.minPossibleAmount)
        assertEquals(BigDecimal("75.00"), eightKm.maxPossibleAmount)

        assertTrue(SangamTariffCalculator.calculateLocalCommission(5.5, BigDecimal("250.00")) is CommissionResult.NeedsConfirmation)
        assertTrue(SangamTariffCalculator.calculateLocalCommission(10.0, BigDecimal("360.00")) is CommissionResult.NeedsConfirmation)
        assertTrue(SangamTariffCalculator.calculateLocalCommission(30.0, BigDecimal("1000.00")) is CommissionResult.NeedsConfirmation)

        val altOnly = SangamTariffCalculator.calculateLocalCommission(
            30.0,
            BigDecimal("1000.00"),
            LocalCommissionConfig(
                above25KmAnd800Rule = Above25KmAnd800Rule.ALTERNATIVE_SINGLE_10_PERCENT,
                fiveToSixKmPolicy = CommissionBoundaryPolicy.USE_UPPER_SLAB,
                tenKmBoundaryPolicy = CommissionBoundaryPolicy.USE_UPPER_SLAB
            )
        )
        assertTrue(altOnly is CommissionResult.Confirmed)
        assertEquals(BigDecimal("100.00"), (altOnly as CommissionResult.Confirmed).amount)
    }

    @Test
    fun `new GET TAXI uniform hourly rental tariff 350 per hr, 10 km included, 20 extra km, 0 waiting, 50 per hr CC`() {
        // Uniform ₹350/hr across all 5 categories
        for (cat in VehicleCategory.entries) {
            val oneHr = SangamTariffCalculator.calculateHourlyRental(cat, packageHours = 1, actualDistanceKm = 8.0, waitingMinutes = 25.0)
            assertEquals("1h bill for ${cat.displayName}", BigDecimal("350.00"), oneHr.customerBillTotal)
            assertEquals("1h CC for ${cat.displayName}", BigDecimal("50.00"), (oneHr.commissionResult as CommissionResult.Confirmed).amount)
            assertEquals("1h driver net for ${cat.displayName}", BigDecimal("300.00"), (oneHr.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

            val twoHr = SangamTariffCalculator.calculateHourlyRental(cat, packageHours = 2, actualDistanceKm = 25.0, waitingMinutes = 30.0)
            // 2h * 350 = 700, included = 20km, extra = 5km * 20 = 100, waiting = 0 -> total = 800.00
            // CC = 2 * 50 = 100.00 (NOT 10% = 80.00), driver net = 700.00
            assertEquals("2h+extra bill for ${cat.displayName}", BigDecimal("800.00"), twoHr.customerBillTotal)
            assertEquals("2h CC for ${cat.displayName}", BigDecimal("100.00"), (twoHr.commissionResult as CommissionResult.Confirmed).amount)
            assertEquals("2h driver net for ${cat.displayName}", BigDecimal("700.00"), (twoHr.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
        }
    }

    @Test
    fun `outstation round trip uses 500 base plus 15 per km and one way uses 300 base plus 30 per km across all 5 categories`() {
        for (cat in VehicleCategory.entries) {
            // Round-Trip: ₹500 base + ₹15/km
            val rt = SangamTariffCalculator.calculateOutstation(
                category = cat,
                isRoundTrip = true,
                distanceKm = 200.0,
                over12Hours = true,
                tollCharge = 250.0,
                permitCharge = 150.0,
                parkingCharge = 100.0
            )
            // Tariff: 500 + (200 * 15) + 400 = 3900.00
            // Pass-through: 500.00 -> Customer total: 4400.00
            // Commission: 10% of 3900.00 = 390.00
            // Driver balance: 4400.00 - 390.00 = 4010.00
            assertEquals("${cat.displayName} RT tariff", BigDecimal("3900.00"), rt.tariffFareBeforePassThrough)
            assertEquals("${cat.displayName} RT pass-through", BigDecimal("500.00"), rt.passThroughExtrasTotal)
            assertEquals("${cat.displayName} RT total", BigDecimal("4400.00"), rt.customerBillTotal)
            assertEquals("${cat.displayName} RT CC", BigDecimal("390.00"), (rt.commissionResult as CommissionResult.Confirmed).amount)
            assertEquals("${cat.displayName} RT driver net", BigDecimal("4010.00"), (rt.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

            // One-Way: ₹300 base + ₹30/km
            val ow = SangamTariffCalculator.calculateOutstation(
                category = cat,
                isRoundTrip = false,
                distanceKm = 100.0,
                over12Hours = false
            )
            // Tariff: 300 + (100 * 30) = 3300.00, CC: 330.00, Driver Net: 2970.00
            assertEquals("${cat.displayName} OW total", BigDecimal("3300.00"), ow.customerBillTotal)
            assertEquals("${cat.displayName} OW CC", BigDecimal("330.00"), (ow.commissionResult as CommissionResult.Confirmed).amount)
            assertEquals("${cat.displayName} OW driver net", BigDecimal("2970.00"), (ow.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
        }
    }

    @Test
    fun `outstation does not auto-charge waiting but supports manual additional waiting hours when rate is explicitly provided`() {
        val noWaitRate = SangamTariffCalculator.calculateOutstation(
            category = VehicleCategory.SEDAN,
            isRoundTrip = true,
            distanceKm = 100.0,
            outstationWaitHours = 3.0,
            outstationWaitRatePerHour = 0.0
        )
        // 500 + 1500 = 2000.00 (no invented hourly waiting charge)
        assertEquals(BigDecimal("2000.00"), noWaitRate.customerBillTotal)

        val manualWait = SangamTariffCalculator.calculateOutstation(
            category = VehicleCategory.SEDAN,
            isRoundTrip = true,
            distanceKm = 100.0,
            outstationWaitHours = 2.0,
            outstationWaitRatePerHour = 150.0
        )
        // 500 + 1500 + 300 = 2300.00 with separate line item
        assertEquals(BigDecimal("2300.00"), manualWait.customerBillTotal)
        assertTrue(manualWait.lineItems.any { it.label == "Additional Waiting Hours" && it.amount == BigDecimal("300.00") })
    }

    @Test
    fun `day rental and innova crysta rates preserve missing commission flags`() {
        val innova = SangamTariffCalculator.calculateDayRental(VehicleCategory.INNOVA, distanceKm = 100.0, isInnovaCrysta = false)
        assertEquals(BigDecimal("4000.00"), innova.customerBillTotal) // 2500 + 1500
        assertTrue(innova.commissionResult is CommissionResult.NotSpecified)

        val crysta = SangamTariffCalculator.calculateDayRental(VehicleCategory.INNOVA, distanceKm = 100.0, isInnovaCrysta = true)
        assertEquals(BigDecimal("4600.00"), crysta.customerBillTotal) // 3000 + 1600
        assertTrue(crysta.isInnovaCrysta)
        assertTrue(crysta.commissionResult is CommissionResult.NotSpecified)
    }
}
