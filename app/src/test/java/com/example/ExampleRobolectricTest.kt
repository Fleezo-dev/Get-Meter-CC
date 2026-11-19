package com.example

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.TripDatabase
import com.example.data.database.TripEntity
import com.example.data.model.ActiveTripDisplayMode
import com.example.data.model.TripStatus
import com.example.service.LocationTrackingService
import com.example.util.PdfGenerator
import com.example.util.ShareUtils
import com.example.util.VendorLogoRenderer
import com.example.viewmodel.MeterViewModel
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GET TAXI", appName)
    }

    @Test
    fun `verify trip number format`() {
        val tripNum = LocationTrackingService.generateTripNumber(1700000000000L)
        assertTrue("Trip number should start with GTM-", tripNum.startsWith("GTM-"))
    }

    @Test
    fun `verify vendor statement text content and fare integrity`() {
        val trip = TripEntity(
            id = 101,
            tripNumber = "GTM-20261005-143000",
            startTime = 1700000000000L,
            endTime = 1700000900000L,
            distanceKm = 10.0,
            durationSeconds = 900L,
            waitingSeconds = 120L,
            totalFare = 364.0,
            startLatitude = 12.9716,
            startLongitude = 77.5946,
            endLatitude = 12.9352,
            endLongitude = 77.6245,
            customerName = "John Passenger",
            customerMobile = "9876543210",
            customerAddress = "MG Road, Bangalore",
            driverName = "Basheer Driver",
            driverMobile = "9123456780",
            vehicleNumber = "KA 01 AB 9999",
            vehicleModel = "Toyota Etios",
            vehicleType = "Sedan Taxi",
            baseFare = 80.0,
            farePerKm = 28.0,
            waitFarePerMin = 2.0,
            currency = "₹"
        )

        val statement = ShareUtils.buildVendorStatementText(trip)
        assertTrue(statement.contains("GET TAXI"))
        assertTrue(statement.contains("GTM-20261005-143000"))
        assertTrue(statement.contains("Basheer Driver"))
        assertTrue(statement.contains("John Passenger"))
        assertTrue(statement.contains("10.00 km"))
        assertTrue(statement.contains("₹364.00"))
    }

    @Test
    fun `verify customer receipt text with skipped customer info`() {
        val trip = TripEntity(
            id = 102,
            tripNumber = "GTM-20261005-143001",
            startTime = 1700000000000L,
            endTime = 1700000900000L,
            distanceKm = 5.0,
            durationSeconds = 600L,
            waitingSeconds = 0L,
            totalFare = 220.0,
            startLatitude = 12.9716,
            startLongitude = 77.5946,
            endLatitude = 12.9352,
            endLongitude = 77.6245,
            customerName = "",
            customerMobile = "",
            customerAddress = "",
            baseFare = 80.0,
            farePerKm = 28.0,
            waitFarePerMin = 2.0,
            currency = "₹"
        )

        val receipt = ShareUtils.buildCustomerReceiptText(trip)
        assertTrue(receipt.contains("GET TAXI"))
        assertTrue(receipt.contains("GTM-20261005-143001"))
        assertTrue(receipt.contains("5.00 km"))
        assertTrue(receipt.contains("₹220.00"))
    }

    @Test
    fun `verify room database insert and retrieve trip`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = TripDatabase.getDatabase(context)
        val dao = db.tripDao()

        val testTrip = TripEntity(
            tripNumber = "GTM-20261005-TEST01",
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 600000,
            distanceKm = 8.5,
            durationSeconds = 600L,
            waitingSeconds = 60L,
            totalFare = 318.0,
            startLatitude = 11.0168,
            startLongitude = 76.9558,
            endLatitude = 11.0200,
            endLongitude = 76.9600,
            customerName = "Ramesh Kumar",
            customerMobile = "9043743777",
            customerAddress = "RS Puram, Coimbatore",
            driverName = "Get Taxi – Basheer",
            driverMobile = "9043743777",
            vehicleNumber = "TN 38 AB 1234",
            vehicleModel = "Sedan Taxi",
            baseFare = 80.0,
            farePerKm = 28.0,
            waitFarePerMin = 2.0,
            currency = "₹"
        )

        val id = dao.insertTrip(testTrip)
        assertTrue("Inserted ID should be > 0", id > 0)

        val fetched = dao.getTripById(id.toInt())
        assertNotNull(fetched)
        assertEquals("GTM-20261005-TEST01", fetched?.tripNumber)
        assertEquals("Ramesh Kumar", fetched?.customerName)
        assertEquals("9043743777", fetched?.customerMobile)
        assertEquals("RS Puram, Coimbatore", fetched?.customerAddress)
        assertEquals(318.0, fetched?.totalFare ?: 0.0, 0.01)
    }

    @Test
    fun `verify Trip OTP enforcement flow and session locking`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val otpManager = com.example.data.preferences.TripOtpManager(context)
        otpManager.destroyTripAccessSession()
        otpManager.clearConsumedHistory()

        assertFalse("Trip must not be authorized before entering OTP", otpManager.isSessionActive())

        val generatedOtp = otpManager.generateNewOtp()
        assertEquals(4, generatedOtp.length)

        val invalidResult = otpManager.validateAndAuthorizeTrip("0000_fake".take(4))
        if ("0000_fake".take(4) != generatedOtp) {
            assertTrue("Wrong OTP must fail", invalidResult is com.example.data.preferences.OtpValidationResult.Failure)
        }

        val validResult = otpManager.validateAndAuthorizeTrip(generatedOtp)
        assertTrue("Valid OTP must succeed", validResult is com.example.data.preferences.OtpValidationResult.Success)
        assertTrue("Trip access must now be granted", otpManager.isSessionActive())

        otpManager.destroyTripAccessSession()
        assertFalse("Trip session must be inactive after consumption", otpManager.isSessionActive())

        val reuseResult = otpManager.validateAndAuthorizeTrip(generatedOtp)
        assertTrue("Reusing consumed OTP must be rejected", reuseResult is com.example.data.preferences.OtpValidationResult.Failure)
    }

    @Test
    fun `verify offline UPI QR code URI and bitmap generation`() {
        val uri = com.example.util.UpiQrCodeGenerator.buildUpiUri(
            upiId = "9043743777@ybl",
            driverName = "Basheer Driver",
            fareAmount = 450.50,
            currencyCode = "INR"
        )
        assertTrue("URI must start with upi://pay?", uri.startsWith("upi://pay?"))
        assertTrue("URI must include pa=9043743777", uri.contains("9043743777"))
        assertTrue("URI must include am=450.50", uri.contains("am=450.50"))
        assertTrue("URI must include cu=INR", uri.contains("cu=INR"))

        val qrBitmap = com.example.util.UpiQrCodeGenerator.generateQrCodeBitmap(uri, sizePx = 300)
        assertNotNull("Generated QR bitmap must not be null", qrBitmap)
        assertEquals(300, qrBitmap.width)
        assertEquals(300, qrBitmap.height)
    }

    @Test
    fun `verify bilingual English and Tamil language switching and persistence`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.util.AppLanguageManager.init(context)

        com.example.util.AppLanguageManager.setLanguageByCode(context, "en")
        assertEquals(com.example.util.AppLanguage.ENGLISH, com.example.util.AppLanguageManager.currentLanguage.value)
        val enStrings = com.example.util.AppLanguageManager.currentStrings
        assertEquals("DEVICE STATUS", enStrings.deviceStatus)
        assertEquals("Vacant", enStrings.vacant)
        assertEquals("Occupied", enStrings.occupied)
        assertEquals("Start Trip", enStrings.startTrip)
        assertEquals("End Trip", enStrings.endTrip)
        assertEquals("No recorded trips yet.", enStrings.noRecordedTripsYet)

        com.example.util.AppLanguageManager.setLanguageByCode(context, "ta")
        assertEquals(com.example.util.AppLanguage.TAMIL, com.example.util.AppLanguageManager.currentLanguage.value)
        assertEquals("ta", com.example.util.AppLanguageManager.getLanguage(context).code)

        val taStrings = com.example.util.AppLanguageManager.currentStrings
        assertEquals("சாதன நிலை", taStrings.deviceStatus)
        assertEquals("காலியாக உள்ளது", taStrings.vacant)
        assertEquals("பயணத்தில் உள்ளது", taStrings.occupied)

        com.example.util.AppLanguageManager.setLanguageByCode(context, "en")
        assertEquals("en", com.example.util.AppLanguageManager.getLanguage(context).code)
    }

    @Test
    fun `verify all 20 vendor SVG assets exist in Android assets and render bitmaps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetFiles = context.assets.list("vendor_logos")?.toSet() ?: emptySet()
        assertEquals(20, VendorLogoRenderer.TWENTY_VENDOR_NAMES.size)

        VendorLogoRenderer.TWENTY_VENDOR_NAMES.forEach { vendorName ->
            val slug = VendorLogoRenderer.slugForVendorName(vendorName)
            val expectedFile = "vendor_logo_${slug}.svg"
            assertTrue("Missing SVG asset $expectedFile for $vendorName", assetFiles.contains(expectedFile))

            val bmp = VendorLogoRenderer.renderVendorLogoBitmap(context, slug, vendorName, 300, 114)
            assertNotNull("Rendered bitmap must not be null for $vendorName", bmp)
            assertEquals(300, bmp.width)
            assertEquals(114, bmp.height)
        }

        // Verify original GET TAXI header logo drawable remains untouched and renders properly
        val getTaxiBmp = VendorLogoRenderer.renderVendorLogoBitmap(context, "get_taxi", "GET TAXI", 300, 114)
        assertNotNull(getTaxiBmp)
    }

    @Test
    fun `verify MainActivity Keep Screen Awake flag applied in foreground and released on stop`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()

        assertTrue(
            "MainActivity must apply FLAG_KEEP_SCREEN_ON while visible in foreground",
            activity.isScreenAwakeFlagApplied()
        )
        val flagsOnResume = activity.window.attributes.flags
        assertTrue((flagsOnResume and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0)

        controller.pause().stop()
        assertFalse(
            "MainActivity must release FLAG_KEEP_SCREEN_ON when stopped",
            activity.isScreenAwakeFlagApplied()
        )
        controller.destroy()
    }

    @Test
    fun `verify Active Meter Dashboard display mode on trip start and Map toggle without resetting trip`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = MeterViewModel(app)

        // Initially METER_DETAILS (dark-charcoal Active Meter Dashboard)
        assertEquals(ActiveTripDisplayMode.METER_DETAILS, viewModel.activeTripDisplayMode.value)

        // Switch to Map and back to Meter Details
        viewModel.showMapDisplay()
        assertEquals(ActiveTripDisplayMode.MAP_FIRST, viewModel.activeTripDisplayMode.value)

        viewModel.showMeterDetails()
        assertEquals(ActiveTripDisplayMode.METER_DETAILS, viewModel.activeTripDisplayMode.value)

        // Starting a trip automatically opens Active Meter Dashboard (METER_DETAILS)
        viewModel.showMapDisplay()
        viewModel.startTrip()
        assertEquals(ActiveTripDisplayMode.METER_DETAILS, viewModel.activeTripDisplayMode.value)
    }

    @Test
    fun `verify per-trip vendor selection, active-trip lock, and historical invoice immutability`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = TripDatabase.getDatabase(context)
        val dao = db.tripDao()

        // Ensure GET TAXI + 20 vendors are seeded
        dao.insertVendorsIgnore(VendorLogoRenderer.buildInitialVendorEntities())
        val allVendors = dao.getAllVendors()
        assertTrue("Database must contain GET TAXI + 20 vendors (>= 21)", allVendors.size >= 21)

        val helloCity = dao.getVendorById("hello_city")
        assertNotNull("Hello City vendor must exist", helloCity)
        assertEquals("Hello City", helloCity?.vendorName)

        // 1. Complete a trip under "Hello City" with initial contact details
        val trip1 = TripEntity(
            tripNumber = "GTM-20261103-HC0001",
            startTime = 1700000000000L,
            endTime = 1700000600000L,
            distanceKm = 6.5,
            durationSeconds = 600L,
            waitingSeconds = 0L,
            totalFare = 295.0,
            startLatitude = 11.0168,
            startLongitude = 76.9558,
            endLatitude = 11.0250,
            endLongitude = 76.9650,
            baseFare = 100.0,
            farePerKm = 30.0,
            waitFarePerMin = 2.0,
            currency = "₹",
            vendorId = "hello_city",
            vendorName = "Hello City",
            vendorLogoAsset = "assets/vendor_logos/vendor_logo_hello_city.svg",
            vendorContactNumber = "0422-2221111",
            vendorBusinessAddress = "Avinashi Road, Coimbatore",
            vendorGstin = "33AAACH1111A1Z5",
            vendorInvoiceNumber = "HC-20261103-HC0001"
        )
        val trip1Id = dao.insertTrip(trip1).toInt()

        // 2. Later edit "Hello City" vendor profile in Vendor Management
        val updatedHelloCity = helloCity!!.copy(
            contactNumber = "0422-9999999",
            businessAddress = "New Trichy Road Office, Coimbatore",
            gstin = "33NEWGST9999Z1"
        )
        dao.upsertVendor(updatedHelloCity)

        // 3. Verify historical Trip 1 snapshot & generated invoice remain completely unchanged
        val savedTrip1 = dao.getTripById(trip1Id)!!
        assertEquals("Hello City", savedTrip1.vendorName)
        assertEquals("0422-2221111", savedTrip1.vendorContactNumber)
        assertEquals("Avinashi Road, Coimbatore", savedTrip1.vendorBusinessAddress)
        assertEquals("33AAACH1111A1Z5", savedTrip1.vendorGstin)

        val meta1 = PdfGenerator.resolveVendorHeaderMetadata(savedTrip1)
        assertTrue(meta1.displayedBusinessLines.any { it.contains("Avinashi Road, Coimbatore") })
        assertTrue(meta1.displayedBusinessLines.any { it.contains("0422-2221111") })
        assertFalse(meta1.displayedBusinessLines.any { it.contains("0422-9999999") })

        val pdfFile = PdfGenerator.generateCustomerInvoicePdf(context, savedTrip1)
        assertTrue("Generated vendor PDF file must exist and be non-empty", pdfFile.exists() && pdfFile.length() > 0L)
        assertTrue(pdfFile.name.startsWith("HELLO_CITY_Customer_Invoice_"))
    }

    @Test
    fun `verify customer bill excludes CC and GPay while vendor report includes saved CC and GPay`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = TripDatabase.getDatabase(app)
        val initialTripCount = db.tripDao().getAllTrips().first().size

        val viewModel = MeterViewModel(app)
        assertEquals("get_taxi", viewModel.selectedVendor.value.vendorId)
        assertEquals("GET TAXI", viewModel.selectedVendor.value.vendorName)
        assertEquals("", viewModel.selectedVendor.value.contactNumber)

        var ready = false
        viewModel.calculateQuickSettlement(
            input = com.example.util.SangamTariffCalculator.TripCalculationInput(
                vehicleCategory = com.example.util.SangamTariffConfig.VehicleCategory.MINI,
                tripType = com.example.util.SangamTariffConfig.SangamTripType.LOCAL_NORMAL,
                distanceKm = 4.0,
                outsideCityCharge = 80.0
            ),
            pickupAddress = "RS Puram",
            dropAddress = "Gandhipuram",
            durationMinutes = 20L
        ) {
            ready = true
        }
        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(60) {
            mainLooper.idle()
            if (ready && viewModel.currentSettlement.value != null) return@repeat
            Thread.sleep(50)
        }
        mainLooper.idle()
        assertTrue("Quick settlement callback must complete", ready)

        val settlement = viewModel.currentSettlement.value
        assertNotNull("Settlement must be populated", settlement)
        assertTrue("Completed trip must be persisted in Room with id > 0", (settlement!!.persistedTripId ?: 0L) > 0L)
        assertEquals(null, settlement.saveError)
        assertEquals("GET TAXI", settlement.tripEntity.vendorName)
        assertEquals("", settlement.tripEntity.vendorContactNumber)
        // MINI Local Normal 4km = 80 + 112 + 80 (manual Outside City Limits) = 272.00; Automatic CC (0-5km) = 25.00; Driver Net = 247.00
        assertEquals(272.0, settlement.breakdown.customerBillTotal.toDouble(), 0.01)

        // 1. Customer Invoice text & PDF MUST NEVER contain CC, Commission, Driver Balance, or 9043743777
        val customerPdf = PdfGenerator.generateSangamBillPdf(app, settlement.tripEntity, settlement.breakdown)
        assertTrue("Customer PDF must exist", customerPdf.exists() && customerPdf.length() > 0L)
        val customerText = ShareUtils.buildSangamReceiptText(settlement.tripEntity, settlement.breakdown, includeSettlementNote = false)
        assertTrue(customerText.contains("GET TAXI"))
        assertTrue(customerText.contains("₹272.00"))
        assertTrue(customerText.contains("Outside City Limits"))
        assertFalse("Customer text must NOT contain GPay 9043743777", customerText.contains("9043743777"))
        assertFalse("Customer text must NOT contain CC", customerText.contains("CC"))
        assertFalse("Customer text must NOT contain Commission", customerText.contains("Commission", ignoreCase = true))
        assertFalse("Customer text must NOT contain Driver Balance", customerText.contains("Driver Balance", ignoreCase = true))
        assertFalse("Customer text must NOT contain Sangam", customerText.contains("Sangam", ignoreCase = true))

        // 2. Vendor / Driver Share Report MUST contain Customer Bill, Approved CC, Driver Balance, and GPay 9043743777
        val vendorPdf = PdfGenerator.generateInternalCcReportPdf(app, settlement.tripEntity, settlement.breakdown, "DRV-01")
        assertTrue("Vendor Report PDF must exist", vendorPdf.exists() && vendorPdf.length() > 0L)
        val vendorText = ShareUtils.buildInternalCcReportText(settlement.tripEntity, settlement.breakdown, "DRV-01")
        assertTrue(vendorText.contains("₹272.00"))
        assertTrue(vendorText.contains("₹25.00"))
        assertTrue(vendorText.contains("₹247.00"))
        assertTrue(vendorText.contains("9043743777"))
        assertFalse("Vendor report must NOT contain Sangam", vendorText.contains("Sangam", ignoreCase = true))

        // 3. End trip & clear resets active settlement UI WITHOUT deleting saved Room trip history
        val persistedId = settlement.tripEntity.id
        viewModel.endTripAndClearSettlement()
        repeat(10) {
            mainLooper.idle()
            if (viewModel.currentSettlement.value == null) return@repeat
            Thread.sleep(25)
        }
        mainLooper.idle()
        assertEquals(null, viewModel.currentSettlement.value)

        // 4. Simulate app restart by querying DB directly and creating a fresh MeterViewModel
        val tripsAfterClear = db.tripDao().getAllTrips().first()
        assertEquals(initialTripCount + 1, tripsAfterClear.size)
        val recoveredTrip = db.tripDao().getTripById(persistedId)
        assertNotNull("Saved trip must survive End Trip & Clear and restart", recoveredTrip)
        assertEquals(272.0, recoveredTrip!!.totalFare, 0.01)
        assertEquals(25.0, recoveredTrip.ccAmount, 0.01)
        assertEquals(247.0, recoveredTrip.driverNetBalance, 0.01)

        // 5. Deduplication: saving same tripNumber again returns same ID without inserting a duplicate
        val duplicateSaveId = db.tripDao().saveCompletedTripIdempotent(recoveredTrip.copy(id = 0))
        assertEquals(persistedId.toLong(), duplicateSaveId)
        assertEquals(initialTripCount + 1, db.tripDao().getAllTrips().first().size)

        // 6. Historical snapshot reconstruction preserves exact saved CC & Driver Net Balance without recalculation
        val customHistoricalTrip = recoveredTrip.copy(
            totalFare = 999.0,
            ccStatus = com.example.util.TripSnapshotMapper.CC_STATUS_CONFIRMED,
            ccRuleLabel = "Historical Approved CC",
            ccAmount = 123.0,
            driverNetBalance = 876.0
        )
        val restoredBreakdown = com.example.util.TripSnapshotMapper.toBreakdown(customHistoricalTrip)
        assertEquals(java.math.BigDecimal("999.00"), restoredBreakdown.customerBillTotal)
        assertEquals(
            java.math.BigDecimal("123.00"),
            (restoredBreakdown.commissionResult as com.example.util.SangamTariffCalculator.CommissionResult.Confirmed).amount
        )
        assertEquals(
            java.math.BigDecimal("876.00"),
            (restoredBreakdown.driverBalanceStatus as com.example.util.SangamTariffCalculator.DriverBalanceStatus.Confirmed).amount
        )
    }

    @Test
    fun `verify local waiting 2 per min shows in Customer Invoice while CC stays separate and hidden`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = MeterViewModel(app)

        var ready = false
        viewModel.calculateQuickSettlement(
            input = com.example.util.SangamTariffCalculator.TripCalculationInput(
                vehicleCategory = com.example.util.SangamTariffConfig.VehicleCategory.MINI,
                tripType = com.example.util.SangamTariffConfig.SangamTripType.LOCAL_NORMAL,
                distanceKm = 4.0,
                waitingMinutes = 10.0
            ),
            pickupAddress = "RS Puram",
            dropAddress = "Race Course",
            durationMinutes = 25L
        ) { ready = true }

        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(60) {
            mainLooper.idle()
            if (ready && viewModel.currentSettlement.value != null) return@repeat
            Thread.sleep(50)
        }
        mainLooper.idle()
        assertTrue(ready)

        val settlement = viewModel.currentSettlement.value!!
        // 80 + 112 + 20 (10 min * ₹2) = 212.00; Automatic CC (0-5 km) = 25.00; Driver Net = 187.00
        assertEquals(212.0, settlement.breakdown.customerBillTotal.toDouble(), 0.01)
        assertEquals(2.0, settlement.tripEntity.waitFarePerMin, 0.01)
        assertEquals(600L, settlement.tripEntity.waitingSeconds)
        assertEquals(25.0, settlement.tripEntity.ccAmount, 0.01)
        assertEquals(187.0, settlement.tripEntity.driverNetBalance, 0.01)

        val customerText = ShareUtils.buildSangamReceiptText(settlement.tripEntity, settlement.breakdown, includeSettlementNote = false)
        assertTrue("Customer receipt must include Waiting Charge line item", customerText.contains("Waiting Charge"))
        assertTrue("Customer receipt must show ₹20.00 waiting charge", customerText.contains("₹20.00"))
        assertTrue("Customer receipt must show ₹212.00 total", customerText.contains("₹212.00"))
        assertFalse("Customer receipt must NOT contain GPay", customerText.contains("9043743777"))
        assertFalse("Customer receipt must NOT contain CC", customerText.contains("CC"))

        val customerPdf = PdfGenerator.generateSangamBillPdf(app, settlement.tripEntity, settlement.breakdown)
        assertTrue(customerPdf.exists() && customerPdf.length() > 0L)

        val vendorText = ShareUtils.buildInternalCcReportText(settlement.tripEntity, settlement.breakdown, "DRV-02")
        assertTrue(vendorText.contains("₹212.00"))
        assertTrue(vendorText.contains("₹25.00"))
        assertTrue(vendorText.contains("₹187.00"))
        assertTrue(vendorText.contains("9043743777"))

        viewModel.endTripAndClearSettlement()
    }

    @Test
    fun `verify Outstation manual Additional Waiting persists in Room and recovers accurately in history`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = TripDatabase.getDatabase(app)
        val viewModel = MeterViewModel(app)

        var ready = false
        viewModel.calculateQuickSettlement(
            input = com.example.util.SangamTariffCalculator.TripCalculationInput(
                vehicleCategory = com.example.util.SangamTariffConfig.VehicleCategory.SEDAN,
                tripType = com.example.util.SangamTariffConfig.SangamTripType.OUTSTATION_ROUND_TRIP,
                distanceKm = 100.0,
                extraChargeItems = listOf(
                    com.example.data.model.ExtraChargeItem(
                        label = "Additional Waiting (2.0 hr @ ₹150/hr)",
                        amount = 300.0
                    )
                )
            ),
            pickupAddress = "Coimbatore",
            dropAddress = "Ooty",
            durationMinutes = 360L
        ) { ready = true }

        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(60) {
            mainLooper.idle()
            if (ready && viewModel.currentSettlement.value != null) return@repeat
            Thread.sleep(50)
        }
        mainLooper.idle()
        assertTrue(ready)

        val settlement = viewModel.currentSettlement.value!!
        val savedId = settlement.tripEntity.id
        assertTrue(savedId > 0)
        assertEquals(2300.0, settlement.breakdown.customerBillTotal.toDouble(), 0.01)

        viewModel.endTripAndClearSettlement()

        val persisted = db.tripDao().getTripById(savedId)!!
        val restored = com.example.util.TripSnapshotMapper.toBreakdown(persisted)
        assertEquals(java.math.BigDecimal("2300.00"), restored.customerBillTotal)
        assertTrue(
            "Historical snapshot must preserve separate Additional Waiting line item",
            restored.lineItems.any { it.label == "Additional Waiting" && it.amount == java.math.BigDecimal("300.00") }
        )
        val histCustomerText = ShareUtils.buildSangamReceiptText(persisted, restored, includeSettlementNote = false)
        assertTrue(histCustomerText.contains("Additional Waiting"))
        assertTrue(histCustomerText.contains("₹300.00"))
        assertFalse(histCustomerText.contains("9043743777"))
    }

    @Test
    fun `verify Day Rental manual CC updates Room and Vendor Report without altering Customer Invoice`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = TripDatabase.getDatabase(app)
        val viewModel = MeterViewModel(app)

        var ready = false
        viewModel.calculateQuickSettlement(
            input = com.example.util.SangamTariffCalculator.TripCalculationInput(
                vehicleCategory = com.example.util.SangamTariffConfig.VehicleCategory.INNOVA,
                tripType = com.example.util.SangamTariffConfig.SangamTripType.DAY_RENTAL,
                distanceKm = 100.0
            ),
            pickupAddress = "Coimbatore",
            dropAddress = "Pollachi",
            durationMinutes = 600L
        ) { ready = true }

        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(60) {
            mainLooper.idle()
            if (ready && viewModel.currentSettlement.value != null) return@repeat
            Thread.sleep(50)
        }
        mainLooper.idle()
        assertTrue(ready)

        val initialSettlement = viewModel.currentSettlement.value!!
        val tripId = initialSettlement.tripEntity.id
        assertEquals(4000.0, initialSettlement.tripEntity.totalFare, 0.01)
        assertEquals(com.example.util.TripSnapshotMapper.CC_STATUS_NOT_SPECIFIED, initialSettlement.tripEntity.ccStatus)

        // Driver enters manual variable Day Rental CC = ₹350.00
        viewModel.updateManualDayRentalCc(tripId, 350.0)
        repeat(30) {
            mainLooper.idle()
            val check = db.tripDao().getTripById(tripId)
            if (check?.ccStatus == com.example.util.TripSnapshotMapper.CC_STATUS_CONFIRMED) return@repeat
            Thread.sleep(40)
        }
        mainLooper.idle()

        val updatedRecord = db.tripDao().getTripById(tripId)!!
        assertEquals(4000.0, updatedRecord.totalFare, 0.01) // Customer bill unchanged
        assertEquals(com.example.util.TripSnapshotMapper.CC_STATUS_CONFIRMED, updatedRecord.ccStatus)
        assertEquals("Day Rental CC (Manual)", updatedRecord.ccRuleLabel)
        assertEquals(350.0, updatedRecord.ccAmount, 0.01)
        assertEquals(3650.0, updatedRecord.driverNetBalance, 0.01)

        val restoredBreakdown = com.example.util.TripSnapshotMapper.toBreakdown(updatedRecord)
        val customerInvoice = ShareUtils.buildSangamReceiptText(updatedRecord, restoredBreakdown, includeSettlementNote = false)
        assertTrue(customerInvoice.contains("₹4000.00"))
        assertFalse("Customer Invoice must NOT expose manual Day Rental CC", customerInvoice.contains("350.00"))
        assertFalse("Customer Invoice must NOT expose GPay", customerInvoice.contains("9043743777"))

        val vendorReport = ShareUtils.buildInternalCcReportText(updatedRecord, restoredBreakdown, "DRV-09")
        assertTrue(vendorReport.contains("₹4000.00"))
        assertTrue(vendorReport.contains("₹350.00"))
        assertTrue(vendorReport.contains("₹3650.00"))
        assertTrue(vendorReport.contains("9043743777"))

        viewModel.endTripAndClearSettlement()
    }

    @Test
    fun `verify ActiveTripRecord v13 preserves waitingSeconds, GPS anchor, gap metadata, Direct Meter snapshot, and manual extras`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = TripDatabase.getDatabase(context)
        val dao = db.tripDao()

        val directSnapJson = com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot(
            localBaseFare = 120.0,
            localPerKmRate = 32.0,
            waitingChargePerMin = 2.5
        ).toJson()

        val activeRecord = com.example.data.database.ActiveTripRecord(
            tripNumber = "GTM-20261118-REC01",
            startTime = 1700000000000L,
            distanceKm = 6.2,
            durationSeconds = 900L,
            waitingSeconds = 300L,
            currentFare = 460.0,
            isPaused = true,
            baseFare = 200.0,
            farePerKm = 35.0,
            waitFarePerMin = 2.0,
            currency = "₹",
            isOutOfCity = true,
            pickupAddress = "Peelamedu",
            serviceType = "LOCAL",
            vehicleCategoryId = com.example.util.SangamTariffConfig.VehicleCategory.SUV.id,
            sangamTripTypeId = com.example.util.SangamTariffConfig.SangamTripType.LOCAL_MINIMUM_BILL.id,
            isInnovaCrysta = false,
            isNightTrip = true,
            over12Hours = false,
            packageHours = 3,
            meterMode = com.example.util.SangamTariffConfig.METER_MODE_OTP,
            directCategoryId = com.example.util.SangamTariffConfig.DirectMeterCategory.LOCAL.id,
            directTariffSnapshotJson = directSnapJson,
            use100KmPackage = true,
            lastAcceptedLat = 11.0168,
            lastAcceptedLng = 76.9558,
            lastAcceptedLocationTimeMs = 1700000890000L,
            isWaitingActive = true,
            unverifiedGapSeconds = 65L,
            unverifiedGapStraightLineKm = 0.85,
            needsDistanceVerification = true
        )
        dao.saveActiveTrip(activeRecord)

        val restored = dao.getActiveTrip()
        assertNotNull(restored)
        assertEquals(300L, restored!!.waitingSeconds)
        assertEquals("SUV", restored.vehicleCategoryId)
        assertEquals("LOCAL_MINIMUM_BILL", restored.sangamTripTypeId)
        assertTrue(restored.use100KmPackage)
        assertEquals(11.0168, restored.lastAcceptedLat, 0.0001)
        assertEquals(76.9558, restored.lastAcceptedLng, 0.0001)
        assertEquals(1700000890000L, restored.lastAcceptedLocationTimeMs)
        assertTrue(restored.isWaitingActive)
        assertEquals(65L, restored.unverifiedGapSeconds)
        assertEquals(0.85, restored.unverifiedGapStraightLineKm, 0.001)
        assertTrue(restored.needsDistanceVerification)

        // Verify buildSangamInput adds ZERO automatic Outside City/Night charges when no manual amount is entered
        // SUV Min Bill 6.2km = ₹450 + 5.0 min wait (₹10) = ₹460.00
        val state = com.example.data.model.TripState(
            status = TripStatus.PAUSED,
            distanceKm = restored.distanceKm,
            durationSeconds = restored.durationSeconds,
            waitingSeconds = restored.waitingSeconds,
            vehicleCategoryId = restored.vehicleCategoryId,
            sangamTripTypeId = restored.sangamTripTypeId,
            isOutOfCity = restored.isOutOfCity,
            isNightTrip = restored.isNightTrip
        )
        val calcInput = LocationTrackingService.buildSangamInput(state)
        assertEquals(5.0, calcInput.waitingMinutes, 0.001)
        val breakdown = com.example.util.SangamTariffCalculator.calculateTripFareAndSettlement(calcInput)
        assertEquals(java.math.BigDecimal("460.00"), breakdown.customerBillTotal)

        dao.clearActiveTrip()
    }

    @Test
    fun `verify Direct Meter Room persistence, filter separation, and Customer-Invoice-Only isolation`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = TripDatabase.getDatabase(context)
        val dao = db.tripDao()

        val snapshot = com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot()
        val breakdown = com.example.util.SangamTariffCalculator.calculateDirectMeterFare(
            directCategory = com.example.util.SangamTariffConfig.DirectMeterCategory.LOCAL,
            distanceKm = 6.0,
            waitingMinutes = 5.0,
            tariffSnapshot = snapshot,
            outsideCityCharge = 40.0
        )
        // 100 + 6*30 + 5*2 + 40 = 330.00
        assertEquals(java.math.BigDecimal("330.00"), breakdown.customerBillTotal)

        val directInput = com.example.util.SangamTariffCalculator.TripCalculationInput(
            meterMode = com.example.util.SangamTariffConfig.METER_MODE_DIRECT,
            directCategory = com.example.util.SangamTariffConfig.DirectMeterCategory.LOCAL,
            distanceKm = 6.0,
            waitingMinutes = 5.0,
            directTariffSnapshot = snapshot,
            outsideCityCharge = 40.0
        )
        val directTrip = com.example.util.TripSnapshotMapper.attachFinancialSnapshot(
            baseEntity = TripEntity(
                tripNumber = "GTM-DIRECT-TEST-001",
                startTime = 1700010000000L,
                endTime = 1700011200000L,
                distanceKm = 6.0,
                durationSeconds = 1200L,
                waitingSeconds = 300L,
                totalFare = 330.0,
                startLatitude = 11.0168,
                startLongitude = 76.9558,
                endLatitude = 11.0400,
                endLongitude = 76.9800,
                meterMode = com.example.util.SangamTariffConfig.METER_MODE_DIRECT,
                directCategoryId = com.example.util.SangamTariffConfig.DirectMeterCategory.LOCAL.id,
                directTariffSnapshotJson = snapshot.toJson()
            ),
            input = directInput,
            breakdown = breakdown
        )
        val insertedId = dao.saveCompletedTripIdempotent(directTrip).toInt()
        assertTrue(insertedId > 0)

        val directList = dao.getTripsByMeterMode(com.example.util.SangamTariffConfig.METER_MODE_DIRECT).first()
        assertTrue(directList.any { it.tripNumber == "GTM-DIRECT-TEST-001" })

        val savedDirect = dao.getTripById(insertedId)!!
        assertEquals(com.example.util.SangamTariffConfig.METER_MODE_DIRECT, savedDirect.meterMode)
        val restoredBreakdown = com.example.util.TripSnapshotMapper.toBreakdown(savedDirect)
        assertTrue(restoredBreakdown.isDirectMeter)
        assertEquals(java.math.BigDecimal("330.00"), restoredBreakdown.customerBillTotal)

        // Even if includeSettlementNote = true is accidentally passed, Direct Meter MUST NEVER include CC or GPay 9043743777
        val directReceiptText = ShareUtils.buildSangamReceiptText(savedDirect, restoredBreakdown, includeSettlementNote = true)
        assertTrue(directReceiptText.contains("Direct Meter", ignoreCase = true))
        assertTrue(directReceiptText.contains("₹330.00"))
        assertFalse("Direct Meter receipt must NEVER include GPay 9043743777", directReceiptText.contains("9043743777"))
        assertFalse("Direct Meter receipt must NEVER include CC", directReceiptText.contains("CC"))
        assertFalse("Direct Meter receipt must NEVER include Commission", directReceiptText.contains("Commission", ignoreCase = true))

        val directPdf = PdfGenerator.generateSangamBillPdf(context, savedDirect, restoredBreakdown)
        assertTrue("Direct Meter Customer Invoice PDF must be generated", directPdf.exists() && directPdf.length() > 0L)
    }

    @Test
    fun `verify SettingsRepository floating bubble defaults OFF, appearance mode and Direct Meter settings persist`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.preferences.SettingsRepository(context)

        // Floating bubble MUST be optional (false by default)
        assertFalse(com.example.data.preferences.SettingsRepository.DEFAULT_FLOATING_BUBBLE_ENABLED)
        assertFalse(repo.floatingBubbleEnabled.first())

        // Appearance mode round-trip
        repo.updateAppearanceMode(com.example.data.preferences.SettingsRepository.APPEARANCE_DAY)
        assertEquals(com.example.data.preferences.SettingsRepository.APPEARANCE_DAY, repo.appearanceMode.first())
        repo.updateAppearanceMode(com.example.data.preferences.SettingsRepository.APPEARANCE_NIGHT)
        assertEquals(com.example.data.preferences.SettingsRepository.APPEARANCE_NIGHT, repo.appearanceMode.first())
        repo.updateAppearanceMode(com.example.data.preferences.SettingsRepository.APPEARANCE_AUTO)
        assertEquals(com.example.data.preferences.SettingsRepository.APPEARANCE_AUTO, repo.appearanceMode.first())

        // Direct Meter settings round-trip + reset to exact defaults
        val customDirect = com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot(
            localBaseFare = 125.0,
            localPerKmRate = 34.0,
            hourlyBaseFare = 400.0,
            hourlyIncludedKm = 12.0,
            hourlyExtraKmRate = 22.0,
            outstationBaseFare = 350.0,
            outstationPerKmRate = 32.0,
            waitingChargePerMin = 3.0
        )
        repo.saveDirectMeterSettings(customDirect)
        val readBack = repo.directMeterTariffSnapshot.first()
        assertEquals(125.0, readBack.localBaseFare, 0.001)
        assertEquals(400.0, readBack.hourlyBaseFare, 0.001)
        assertEquals(3.0, readBack.waitingChargePerMin, 0.001)

        repo.resetDirectMeterDefaults()
        val resetBack = repo.directMeterTariffSnapshot.first()
        assertEquals(100.0, resetBack.localBaseFare, 0.001)
        assertEquals(30.0, resetBack.localPerKmRate, 0.001)
        assertEquals(375.0, resetBack.hourlyBaseFare, 0.001)
        assertEquals(10.0, resetBack.hourlyIncludedKm, 0.001)
        assertEquals(20.0, resetBack.hourlyExtraKmRate, 0.001)
        assertEquals(300.0, resetBack.outstationBaseFare, 0.001)
        assertEquals(30.0, resetBack.outstationPerKmRate, 0.001)
        assertEquals(2.0, resetBack.waitingChargePerMin, 0.001)
    }
}

