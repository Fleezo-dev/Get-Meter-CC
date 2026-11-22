package com.example.util

import com.example.data.model.ExtraChargeItem
import com.example.util.SangamTariffCalculator.CommissionResult
import com.example.util.SangamTariffCalculator.DriverBalanceStatus
import com.example.util.SangamTariffCalculator.TripCalculationInput
import com.example.util.SangamTariffConfig.DirectMeterCategory
import com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot
import com.example.util.SangamTariffConfig.LocalFiveKmBoundaryPolicy
import com.example.util.SangamTariffConfig.SangamTripType
import com.example.util.SangamTariffConfig.VehicleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SangamTariffCalculatorTest {

    @Test
    fun `six vehicle categories exist including OMNI_EECO restricted to Hourly Rental`() {
        val categories = VehicleCategory.entries
        assertEquals(6, categories.size)
        assertEquals(
            listOf(
                VehicleCategory.MINI,
                VehicleCategory.SEDAN,
                VehicleCategory.OMNI_EECO,
                VehicleCategory.SUV,
                VehicleCategory.SUV_PLUS,
                VehicleCategory.INNOVA
            ),
            categories
        )
        assertEquals(listOf(SangamTripType.HOURLY_RENTAL), VehicleCategory.OMNI_EECO.supportedTripTypes())
        assertEquals(SangamTripType.entries, VehicleCategory.MINI.supportedTripTypes())
    }

    @Test
    fun `local normal rates compute exact amounts with automatic CC and manual-only extras`() {
        // MINI 4.0 km: 80 + 4*28 = 192.00; Automatic CC (0-5.00 km) = 25.00; Driver Net = 167.00
        val mini = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.MINI, distanceKm = 4.0)
        assertEquals(BigDecimal("192.00"), mini.customerBillTotal)
        assertTrue(mini.commissionResult is CommissionResult.Confirmed)
        assertEquals(BigDecimal("25.00"), (mini.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("167.00"), (mini.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // Setting legacy boolean flags WITHOUT manual amounts MUST add ₹0 (no automatic ₹80 / ₹50!)
        val sedanNoManual = SangamTariffCalculator.calculateLocalNormal(
            category = VehicleCategory.SEDAN,
            distanceKm = 12.0,
            isOutsideCity = true,
            isNightTrip = true
        )
        // 80 + 12*28 = 416.00; Automatic CC (>10-20 km) = 80.00
        assertEquals(BigDecimal("416.00"), sedanNoManual.customerBillTotal)
        assertEquals(BigDecimal("80.00"), (sedanNoManual.commissionResult as CommissionResult.Confirmed).amount)
        assertTrue(sedanNoManual.lineItems.none { it.label == SangamTariffConfig.EXTRA_LABEL_OUTSIDE_CITY })
        assertTrue(sedanNoManual.lineItems.none { it.label == SangamTariffConfig.EXTRA_LABEL_NIGHT_ALLOWANCE })

        // Manual amounts for Outside City Limits and Night Allowance (11 PM-6 AM) appear separately
        val sedanManual = SangamTariffCalculator.calculateLocalNormal(
            category = VehicleCategory.SEDAN,
            distanceKm = 12.0,
            outsideCityCharge = 120.0,
            nightAllowanceCharge = 75.0
        )
        // 416 + 120 + 75 = 611.00; CC (>10-20 km) = 80.00; Driver Net = 531.00
        assertEquals(BigDecimal("611.00"), sedanManual.customerBillTotal)
        assertTrue(
            sedanManual.lineItems.any {
                it.label == SangamTariffConfig.EXTRA_LABEL_OUTSIDE_CITY && it.amount == BigDecimal("120.00")
            }
        )
        assertTrue(
            sedanManual.lineItems.any {
                it.label == SangamTariffConfig.EXTRA_LABEL_NIGHT_ALLOWANCE && it.amount == BigDecimal("75.00")
            }
        )
        assertEquals(BigDecimal("80.00"), (sedanManual.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("531.00"), (sedanManual.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        val suv = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.SUV, distanceKm = 4.0)
        assertEquals(BigDecimal("340.00"), suv.customerBillTotal)

        val suvPlus = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.SUV_PLUS, distanceKm = 4.0)
        assertEquals(BigDecimal("430.00"), suvPlus.customerBillTotal)

        val innova = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.INNOVA, distanceKm = 4.0)
        assertEquals(BigDecimal("450.00"), innova.customerBillTotal)
    }

    @Test
    fun `local minimum bill slabs and optional 5 KM boundary policy`() {
        val under5 = SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.MINI, distanceKm = 3.5)
        assertEquals(BigDecimal("200.00"), under5.customerBillTotal)
        assertTrue(under5.ambiguities.isEmpty())

        // Default policy resolves 5.0 km deterministically to ₹200 without ambiguity
        val exact5Default = SangamTariffCalculator.calculateLocalMinimumBill(VehicleCategory.SEDAN, distanceKm = 5.0)
        assertEquals(BigDecimal("200.00"), exact5Default.customerBillTotal)
        assertFalse(exact5Default.hasUnresolvedAmbiguity)

        // Explicit UNRESOLVED_FLAG policy flags the overlap
        val exact5Flagged = SangamTariffCalculator.calculateLocalMinimumBill(
            category = VehicleCategory.SEDAN,
            distanceKm = 5.0,
            fiveKmPolicy = LocalFiveKmBoundaryPolicy.UNRESOLVED_FLAG
        )
        assertTrue(exact5Flagged.hasUnresolvedAmbiguity)
        assertEquals("MIN_BILL_5KM_OVERLAP", exact5Flagged.ambiguities.first().code)

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
    fun `automatic local CC covers every decimal GPS distance with zero gaps and replaces with 10 percent above 1000 bill`() {
        fun assertConfirmedCc(distanceKm: Double, bill: String, expectedCc: String) {
            val res = SangamTariffCalculator.calculateLocalCommission(distanceKm, BigDecimal(bill))
            assertTrue("Expected Confirmed CC for ${distanceKm}km / ₹$bill", res is CommissionResult.Confirmed)
            assertEquals("Wrong CC for ${distanceKm}km / ₹$bill", BigDecimal(expectedCc), (res as CommissionResult.Confirmed).amount)
        }

        // Fixed distance slabs when entire customer bill <= ₹1,000.00
        assertConfirmedCc(0.0, "100.00", "25.00")
        assertConfirmedCc(5.00, "200.00", "25.00")
        assertConfirmedCc(5.01, "250.00", "50.00")
        assertConfirmedCc(5.50, "250.00", "50.00")
        assertConfirmedCc(7.50, "300.00", "50.00")
        assertConfirmedCc(7.51, "320.00", "70.00")
        assertConfirmedCc(8.00, "350.00", "70.00")
        assertConfirmedCc(10.00, "400.00", "70.00")
        assertConfirmedCc(10.01, "450.00", "80.00")
        assertConfirmedCc(15.00, "1000.00", "80.00") // Exact ₹1,000.00 uses fixed slab
        assertConfirmedCc(20.00, "900.00", "80.00")
        assertConfirmedCc(20.01, "950.00", "100.00")
        assertConfirmedCc(30.00, "1000.00", "100.00") // Exact ₹1,000.00 uses fixed slab

        // Strictly > ₹1,000.00 entire customer bill -> 10% of ENTIRE customer bill (replaces fixed CC)
        assertConfirmedCc(4.00, "1000.10", "100.01")
        assertConfirmedCc(12.00, "1250.00", "125.00")
        assertConfirmedCc(30.00, "1500.00", "150.00")

        // End-to-end check: INNOVA 15 km = 250 + 750 = ₹1,000.00 -> fixed CC ₹80.00
        val exact1000 = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.INNOVA, distanceKm = 15.0)
        assertEquals(BigDecimal("1000.00"), exact1000.customerBillTotal)
        assertEquals(BigDecimal("80.00"), (exact1000.commissionResult as CommissionResult.Confirmed).amount)

        // Adding manual Outside City Limits ₹100 + Night Allowance ₹50 -> ENTIRE customer bill ₹1,150.00 (> ₹1,000)
        // CC MUST be 10% of ENTIRE bill = ₹115.00 (replaces ₹80 fixed CC), Driver Net = ₹1,035.00
        val over1000WithExtras = SangamTariffCalculator.calculateLocalNormal(
            category = VehicleCategory.INNOVA,
            distanceKm = 15.0,
            outsideCityCharge = 100.0,
            nightAllowanceCharge = 50.0
        )
        assertEquals(BigDecimal("1150.00"), over1000WithExtras.customerBillTotal)
        assertEquals(BigDecimal("115.00"), (over1000WithExtras.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("1035.00"), (over1000WithExtras.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
    }

    @Test
    fun `updated OTP hourly rental tariffs include commission once and enforce min hours and 100 KM packages`() {
        // 1. MINI & SEDAN preserved at ₹350/hr, min 1h, 10 km/hr, ₹20/extra km, ₹50/hr CC
        for (cat in listOf(VehicleCategory.MINI, VehicleCategory.SEDAN)) {
            val oneHr = SangamTariffCalculator.calculateHourlyRental(cat, packageHours = 1, actualDistanceKm = 8.0, waitingMinutes = 25.0)
            assertEquals(BigDecimal("350.00"), oneHr.customerBillTotal)
            assertEquals(BigDecimal("50.00"), (oneHr.commissionResult as CommissionResult.Confirmed).amount)
            assertEquals(BigDecimal("300.00"), (oneHr.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

            val twoHr = SangamTariffCalculator.calculateHourlyRental(cat, packageHours = 2, actualDistanceKm = 25.0)
            // 2*350 + 5*20 = 800.00; CC = 2*50 = 100.00; Driver Net = 700.00
            assertEquals(BigDecimal("800.00"), twoHr.customerBillTotal)
            assertEquals(BigDecimal("100.00"), (twoHr.commissionResult as CommissionResult.Confirmed).amount)
            assertEquals(BigDecimal("700.00"), (twoHr.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
        }

        // 2. OMNI / Eeco: ₹450/hr (commission ₹75/hr ALREADY included — NOT ₹525!), min 1h, extra KM ₹13
        val omni1h = SangamTariffCalculator.calculateHourlyRental(VehicleCategory.OMNI_EECO, packageHours = 1, actualDistanceKm = 15.0)
        // 450 + 5*13 = 515.00; CC = 75.00; Driver Net = 440.00
        assertEquals(BigDecimal("515.00"), omni1h.customerBillTotal)
        assertEquals(BigDecimal("75.00"), (omni1h.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("440.00"), (omni1h.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // OMNI / Eeco 100 KM / 10h Package: ₹3,500 + extra km @ ₹13/km
        val omniPkg = SangamTariffCalculator.calculateHourlyRental(
            category = VehicleCategory.OMNI_EECO,
            packageHours = 10,
            actualDistanceKm = 110.0,
            use100KmPackage = true
        )
        // 3500 + 10*13 = 3630.00; CC = 10*75 = 750.00; Driver Net = 2880.00
        assertEquals(BigDecimal("3630.00"), omniPkg.customerBillTotal)
        assertEquals(BigDecimal("750.00"), (omniPkg.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("2880.00"), (omniPkg.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // 3. Ertiga / Rumion (SUV & SUV_PLUS): ₹525/hr (incl. ₹75/hr CC), MIN 3 HOURS enforced, extra KM ₹14
        for (cat in listOf(VehicleCategory.SUV, VehicleCategory.SUV_PLUS)) {
            val min3h = SangamTariffCalculator.calculateHourlyRental(cat, packageHours = 1, actualDistanceKm = 25.0)
            // Enforces min 3h: 3*525 = 1575.00 (incl. 30 km); CC = 3*75 = 225.00; Driver Net = 1350.00
            assertEquals(BigDecimal("1575.00"), min3h.customerBillTotal)
            assertEquals(BigDecimal("225.00"), (min3h.commissionResult as CommissionResult.Confirmed).amount)
            assertEquals(BigDecimal("1350.00"), (min3h.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

            // 100 KM / 10h Package: ₹3,800 + extra km @ ₹14/km
            val pkg = SangamTariffCalculator.calculateHourlyRental(
                category = cat,
                packageHours = 10,
                actualDistanceKm = 105.0,
                use100KmPackage = true
            )
            // 3800 + 5*14 = 3870.00; CC = 10*75 = 750.00; Driver Net = 3120.00
            assertEquals(BigDecimal("3870.00"), pkg.customerBillTotal)
            assertEquals(BigDecimal("750.00"), (pkg.commissionResult as CommissionResult.Confirmed).amount)
            assertEquals(BigDecimal("3120.00"), (pkg.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
        }

        // 4. Innova: ₹600/hr (incl. ₹100/hr CC), MIN 4 HOURS enforced, extra KM ₹15
        val innovaMin4h = SangamTariffCalculator.calculateHourlyRental(VehicleCategory.INNOVA, packageHours = 2, actualDistanceKm = 45.0)
        // Enforces min 4h: 4*600 = 2400 (incl. 40 km) + 5*15 = 75 -> 2475.00; CC = 4*100 = 400.00; Driver Net = 2075.00
        assertEquals(BigDecimal("2475.00"), innovaMin4h.customerBillTotal)
        assertEquals(BigDecimal("400.00"), (innovaMin4h.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("2075.00"), (innovaMin4h.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // Innova 100 KM / 10h Package: ₹4,000 + extra km @ ₹15/km
        val innovaPkg = SangamTariffCalculator.calculateHourlyRental(
            category = VehicleCategory.INNOVA,
            packageHours = 10,
            actualDistanceKm = 120.0,
            use100KmPackage = true
        )
        // 4000 + 20*15 = 4300.00; CC = 10*100 = 1000.00; Driver Net = 3300.00
        assertEquals(BigDecimal("4300.00"), innovaPkg.customerBillTotal)
        assertEquals(BigDecimal("1000.00"), (innovaPkg.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("3300.00"), (innovaPkg.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
    }

    @Test
    fun `direct meter mode calculates independent Local, Hourly, and Outstation tariffs with zero vendor CC`() {
        // Default Local: ₹100 base + ₹30/km + ₹2/min waiting
        val directLocal = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                meterMode = SangamTariffConfig.METER_MODE_DIRECT,
                directCategory = DirectMeterCategory.LOCAL,
                distanceKm = 5.0,
                waitingMinutes = 10.0,
                outsideCityCharge = 50.0
            )
        )
        // 100 + 150 + 20 + 50 = 320.00
        assertTrue(directLocal.isDirectMeter)
        assertEquals(DirectMeterCategory.LOCAL, directLocal.directCategory)
        assertEquals(BigDecimal("320.00"), directLocal.customerBillTotal)
        assertTrue(directLocal.commissionResult is CommissionResult.NotSpecified)
        assertEquals(BigDecimal("320.00"), (directLocal.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
        assertFalse("Direct Meter must never flag CC ambiguity", directLocal.hasUnresolvedAmbiguity)

        // Default Hourly Rental: ₹375/hr, 10 km incl., ₹20/extra km (0 waiting)
        val directHourly = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                meterMode = SangamTariffConfig.METER_MODE_DIRECT,
                directCategory = DirectMeterCategory.HOURLY_RENTAL,
                distanceKm = 25.0,
                packageHours = 2,
                waitingMinutes = 30.0
            )
        )
        // 2*375 + 5*20 = 850.00
        assertTrue(directHourly.isDirectMeter)
        assertEquals(BigDecimal("850.00"), directHourly.customerBillTotal)
        assertTrue(directHourly.commissionResult is CommissionResult.NotSpecified)

        // Default Outstation: ₹300 base + ₹30/km (NO automatic waiting; waiting is manual via Additional Charges)
        val directOutstation = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                meterMode = SangamTariffConfig.METER_MODE_DIRECT,
                directCategory = DirectMeterCategory.OUTSTATION,
                distanceKm = 100.0,
                waitingMinutes = 15.0
            )
        )
        // 300 + 3000 = 3300.00 (15 min waiting ignored automatically on Outstation)
        assertTrue(directOutstation.isDirectMeter)
        assertEquals(BigDecimal("3300.00"), directOutstation.customerBillTotal)
        assertTrue(directOutstation.commissionResult is CommissionResult.NotSpecified)

        // Direct Meter Outstation with manual waiting entered via Additional Charges (e.g., ₹400)
        val directOutstationManualWait = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                meterMode = SangamTariffConfig.METER_MODE_DIRECT,
                directCategory = DirectMeterCategory.OUTSTATION,
                distanceKm = 100.0,
                waitingMinutes = 45.0,
                extraChargeItems = listOf(com.example.data.model.ExtraChargeItem(label = "Waiting Charge", amount = 400.0))
            )
        )
        // 300 + 3000 + 400 manual extra = 3700.00
        assertEquals(BigDecimal("3700.00"), directOutstationManualWait.customerBillTotal)

        // Custom Direct Meter snapshot updates Direct Meter WITHOUT changing OTP tariffs
        val customSnapshot = DirectMeterTariffSnapshot(
            localBaseFare = 150.0,
            localPerKmRate = 35.0,
            waitingChargePerMin = 3.0
        )
        val customDirect = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                meterMode = SangamTariffConfig.METER_MODE_DIRECT,
                directCategory = DirectMeterCategory.LOCAL,
                distanceKm = 4.0,
                waitingMinutes = 5.0,
                directTariffSnapshot = customSnapshot
            )
        )
        // 150 + 4*35 + 5*3 = 305.00
        assertEquals(BigDecimal("305.00"), customDirect.customerBillTotal)

        // OTP Local Normal remains untouched at ₹80 + ₹28/km
        val otpLocal = SangamTariffCalculator.calculateLocalNormal(VehicleCategory.MINI, distanceKm = 4.0)
        assertEquals(BigDecimal("192.00"), otpLocal.customerBillTotal)
    }

    @Test
    fun `outstation round trip uses 500 base plus 15 per km and one way uses 300 base plus 30 per km across supported categories`() {
        val outstationCategories = VehicleCategory.entries.filter {
            SangamTripType.OUTSTATION_ROUND_TRIP in it.supportedTripTypes()
        }
        assertEquals(5, outstationCategories.size)
        for (cat in outstationCategories) {
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
            // Tariff: 500 + (200 * 15) + 400 = 3900.00; Pass-through: 500.00 -> Total: 4400.00
            // Commission: 10% of 3900.00 = 390.00; Driver balance: 4010.00
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
        assertEquals(BigDecimal("2000.00"), noWaitRate.customerBillTotal)

        val manualWait = SangamTariffCalculator.calculateOutstation(
            category = VehicleCategory.SEDAN,
            isRoundTrip = true,
            distanceKm = 100.0,
            outstationWaitHours = 2.0,
            outstationWaitRatePerHour = 150.0
        )
        assertEquals(BigDecimal("2300.00"), manualWait.customerBillTotal)
        assertTrue(manualWait.lineItems.any { it.label == "Additional Waiting Hours" && it.amount == BigDecimal("300.00") })
    }

    @Test
    fun `day rental and innova crysta rates preserve missing commission flags`() {
        val innova = SangamTariffCalculator.calculateDayRental(VehicleCategory.INNOVA, distanceKm = 100.0, isInnovaCrysta = false)
        assertEquals(BigDecimal("4000.00"), innova.customerBillTotal)
        assertTrue(innova.commissionResult is CommissionResult.NotSpecified)

        val crysta = SangamTariffCalculator.calculateDayRental(VehicleCategory.INNOVA, distanceKm = 100.0, isInnovaCrysta = true)
        assertEquals(BigDecimal("4600.00"), crysta.customerBillTotal)
        assertTrue(crysta.isInnovaCrysta)
        assertTrue(crysta.commissionResult is CommissionResult.NotSpecified)
    }

    @Test
    fun `local normal waiting charge at 2 per min updates customer bill and respects 1000 bill CC threshold`() {
        // MINI 4.0 km + 10 min waiting: Base 80 + 112 + 20 = 212.00; Automatic CC (0-5 km) = 25.00; Driver Net = 187.00
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
        assertEquals(BigDecimal("25.00"), (miniWithWait.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("187.00"), (miniWithWait.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // INNOVA 11 km = 250 + 550 = 800 + 20 min wait (40) = 840.00 (<= 1000) -> fixed CC (>10-20 km) = 80.00
        val innovaUnder1000 = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.INNOVA,
                tripType = SangamTripType.LOCAL_NORMAL,
                distanceKm = 11.0,
                waitingMinutes = 20.0
            )
        )
        assertEquals(BigDecimal("840.00"), innovaUnder1000.customerBillTotal)
        assertEquals(BigDecimal("80.00"), (innovaUnder1000.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("760.00"), (innovaUnder1000.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // INNOVA 15 km = 1000.00 + 10 min wait (20.00) = 1020.00 (> 1000) -> 10% of ENTIRE customer bill = 102.00
        val innovaOver1000 = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.INNOVA,
                tripType = SangamTripType.LOCAL_NORMAL,
                distanceKm = 15.0,
                waitingMinutes = 10.0
            )
        )
        assertEquals(BigDecimal("1020.00"), innovaOver1000.customerBillTotal)
        assertEquals(BigDecimal("102.00"), (innovaOver1000.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("918.00"), (innovaOver1000.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // Pro-rata fractional minutes: 2.5 min (150 sec) = ₹5.00
        val fractional = SangamTariffCalculator.calculateLocalNormal(
            category = VehicleCategory.MINI,
            distanceKm = 2.0,
            waitingMinutes = 2.5
        )
        assertEquals(BigDecimal("141.00"), fractional.customerBillTotal)
        assertTrue(fractional.lineItems.any { it.label == "Waiting Charge" && it.amount == BigDecimal("5.00") })
    }

    @Test
    fun `local minimum bill waiting charge at 2 per min uses automatic decimal CC`() {
        // MINI 3.5 km min bill ₹200 + 7.5 min waiting (₹15) = ₹215.00; CC (0-5 km) = ₹25.00; Driver Net = ₹190.00
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
        assertEquals(BigDecimal("25.00"), (minBillWait.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("190.00"), (minBillWait.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)

        // INNOVA 10.0 km min bill ₹800 + 15 min waiting (₹30) = ₹830.00 (<= 1000); CC (>7.50-10.00 km) = ₹70.00
        val innovaMin = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.INNOVA,
                tripType = SangamTripType.LOCAL_MINIMUM_BILL,
                distanceKm = 10.0,
                waitingMinutes = 15.0
            )
        )
        assertEquals(BigDecimal("830.00"), innovaMin.customerBillTotal)
        assertEquals(BigDecimal("70.00"), (innovaMin.commissionResult as CommissionResult.Confirmed).amount)
        assertEquals(BigDecimal("760.00"), (innovaMin.driverBalanceStatus as DriverBalanceStatus.Confirmed).amount)
    }

    @Test
    fun `outstation ignores automatic waiting and displays manual Additional Waiting separately via extraChargeItems`() {
        val autoIgnored = SangamTariffCalculator.calculateTripFareAndSettlement(
            TripCalculationInput(
                vehicleCategory = VehicleCategory.SEDAN,
                tripType = SangamTripType.OUTSTATION_ONE_WAY,
                distanceKm = 100.0,
                waitingMinutes = 60.0
            )
        )
        assertEquals(BigDecimal("3300.00"), autoIgnored.customerBillTotal)
        assertTrue(autoIgnored.lineItems.none { it.label.contains("Waiting", ignoreCase = true) })

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
        assertEquals(BigDecimal("3000.00"), withoutManualCc.customerBillTotal)
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
