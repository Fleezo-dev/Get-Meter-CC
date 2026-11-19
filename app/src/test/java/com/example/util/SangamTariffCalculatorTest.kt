package com.example.util

import com.example.data.model.ExtraChargeItem
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

    @Test
    fun `local normal waiting charge at 2 per min updates customer bill and stays completely separate from vendor CC`() {
        // MINI 4.0 km + 10 min waiting: Base 80 + 112 = 192; Waiting = 20; Total = 212; CC = 30; Driver Net = 182
        val miniWithWait = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.MINI,
                tripType = SangamTripType.LOCAL_NORMAL,
                distanceKm = 4.0,
                waitingMinutes = 10.0
            )
        )
        assertEquals(BigDecimal("212.00"), miniWithWait.customerBillTotal)
        assertTrue(miniWithWait.lineItems.any { it.label == "Waiting Charge" && it.amount == BigDecimal("20.00") })
        assertEquals(BigDecimal("30.00"), (miniWithWait.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("182.00"), (miniWithWait.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // Boundary Isolation: INNOVA 11 km = 250 + 550 = ₹800 base tariff (NOT >₹800).
        // Adding 20 min waiting (+₹40) brings customer bill to ₹840, but Vendor CC MUST remain ₹80 (10–25 km slab), NOT 10% (₹84)!
        val innovaBoundary = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.INNOVA,
                tripType = SangamTripType.LOCAL_NORMAL,
                distanceKm = 11.0,
                waitingMinutes = 20.0
            )
        )
        assertEquals(BigDecimal("840.00"), innovaBoundary.customerBillTotal)
        assertTrue(innovaBoundary.lineItems.any { it.label == "Waiting Charge" && it.amount == BigDecimal("40.00") })
        assertEquals(BigDecimal("80.00"), (innovaBoundary.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("760.00"), (innovaBoundary.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // Pro-rata fractional minutes: 2.5 min (150 sec) = ₹5.00
        val fractional = SangamTariffCalculator.calculateLocalNormal(
            category = VehicleCategory.MINI,
            distanceKm = 2.0,
            waitingMinutes = 2.5
        )
        // 80 + 56 + 5 = 141.00
        assertEquals(BigDecimal("141.00"), fractional.customerBillTotal)
        assertTrue(fractional.lineItems.any { it.label == "Waiting Charge" && it.amount == BigDecimal("5.00") })
    }

    @Test
    fun `local minimum bill waiting charge at 2 per min is separate line item and excluded from vendor CC`() {
        // MINI 3.5 km min bill ₹200 + 7.5 min waiting (₹15) = ₹215.00; CC = ₹30.00; Driver Net = ₹185.00
        val minBillWait = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.MINI,
                tripType = SangamTripType.LOCAL_MINIMUM_BILL,
                distanceKm = 3.5,
                waitingMinutes = 7.5
            )
        )
        assertEquals(BigDecimal("215.00"), minBillWait.customerBillTotal)
        assertTrue(minBillWait.lineItems.any { it.label == "Waiting Charge" && it.amount == BigDecimal("15.00") })
        assertEquals(BigDecimal("30.00"), (minBillWait.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("185.00"), (minBillWait.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // INNOVA 10.0 km min bill ₹800 + 15 min waiting (₹30) = ₹830.00; CC stays on base ₹800 (NOT >₹800 10%)
        val innovaMin = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.INNOVA,
                tripType = SangamTripType.LOCAL_MINIMUM_BILL,
                distanceKm = 10.0,
                waitingMinutes = 15.0,
                rulesConfig = SangamTariffConfig.TariffRulesConfig(
                    localCommissionConfig = LocalCommissionConfig(
                        tenKmBoundaryPolicy = CommissionBoundaryPolicy.USE_UPPER_SLAB
                    )
                )
            )
        )
        assertEquals(BigDecimal("830.00"), innovaMin.customerBillTotal)
        assertEquals(BigDecimal("80.00"), (innovaMin.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("750.00"), (innovaMin.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
    }

    @Test
    fun `outstation ignores automatic waiting and displays manual Additional Waiting separately via extraChargeItems`() {
        // Automatic waitingMinutes MUST be ignored for Outstation
        val autoIgnored = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.SEDAN,
                tripType = SangamTripType.OUTSTATION_ONE_WAY,
                distanceKm = 100.0,
                waitingMinutes = 60.0
            )
        )
        // One-Way: 300 + 100*30 = 3300.00 (zero auto waiting)
        assertEquals(BigDecimal("3300.00"), autoIgnored.customerBillTotal)
        assertTrue(autoIgnored.lineItems.none { it.label.contains("Waiting", ignoreCase = true) })

        // Manual Additional Waiting + Toll via extraChargeItems renders as separate line items
        val manualExtras = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.SEDAN,
                tripType = SangamTripType.OUTSTATION_ROUND_TRIP,
                distanceKm = 100.0,
                extraChargeItems = listOf(
                    ExtraChargeItem(label = "Additional Waiting (2.0 hr @ ₹150/hr)", amount = 300.0),
                    ExtraChargeItem(label = "Toll", amount = 120.0)
                )
            )
        )
        // Base 500 + 1500 = 2000; Extras = 300 + 120 = 420; Customer Bill = 2420.00; CC = 10% of 2000 = 200.00
        assertEquals(BigDecimal("2000.00"), manualExtras.tariffFareBeforePassThrough)
        assertEquals(BigDecimal("420.00"), manualExtras.passThroughExtrasTotal)
        assertEquals(BigDecimal("2420.00"), manualExtras.customerBillTotal)
        assertTrue(manualExtras.lineItems.any { it.label == "Additional Waiting" && it.detail == "2.0 hr @ ₹150/hr" && it.amount == BigDecimal("300.00") })
        assertTrue(manualExtras.lineItems.any { it.label == "Toll" && it.amount == BigDecimal("120.00") })
        assertEquals(BigDecimal("200.00"), (manualExtras.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("2220.00"), (manualExtras.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
    }

    @Test
    fun `day rental has no hardcoded fixed CC percentage and supports manual variable CC`() {
        val withoutManualCc = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.MINI,
                tripType = SangamTripType.DAY_RENTAL,
                distanceKm = 100.0
            )
        )
        assertEquals(BigDecimal("3000.00"), withoutManualCc.customerBillTotal) // 2000 + 100*10
        assertTrue(withoutManualCc.commissionResult is CommissionResult.NotSpecified)
        assertTrue(
            (withoutManualCc.commissionResult as CommissionResult.NotSpecified).reason.contains("variable", ignoreCase = true)
        )

        val withManualCc = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.MINI,
                tripType = SangamTripType.DAY_RENTAL,
                distanceKm = 100.0,
                manualDayRentalCcAmount = 250.0
            )
        )
        assertEquals(BigDecimal("3000.00"), withManualCc.customerBillTotal)
        assertTrue(withManualCc.commissionResult is CommissionResult.Confirmed)
        val confirmed = withManualCc.commissionResult as CommissionResult.Confirmed
        assertEquals("Day Rental CC (Manual)", confirmed.ruleLabel)
        assertEquals(BigDecimal("250.00"), confirmed.amount)
        assertEquals(BigDecimal("2750.00"), (withManualCc.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
    }
}
