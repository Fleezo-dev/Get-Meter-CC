package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalGetTaxiColors
import com.example.util.SangamTariffConfig
import com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot
import com.example.util.SangamTariffConfig.VehicleCategory

private val TdAmber = Color(0xFFF59E0B)

/**
 * Hidden tariff reference screen opened only by 5 consecutive taps on the GET TAXI logo.
 * Displays live tariff figures directly from SangamTariffConfig with zero Sangam branding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TariffDetailsScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)
    val colors = LocalGetTaxiColors.current
    val outstationRef = SangamTariffConfig.outstationRates[VehicleCategory.MINI]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GET TAXI TARIFF DETAILS",
                        color = colors.primaryAccent,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.testTag("tariff_details_title")
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("tariff_details_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.primaryAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("tariff_details_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TariffDetailCard(
                title = "ACTIVE VEHICLE CATEGORIES (${VehicleCategory.entries.size})",
                lines = VehicleCategory.entries.map { cat ->
                    "${cat.displayName}: ${cat.exampleModels}"
                }
            )

            TariffDetailCard(
                title = "OTP HOURLY RENTAL TARIFF (COMMISSION INCLUDED IN METER RATE)",
                lines = listOf(
                    "MINI / SEDAN: ₹350/hr (Min 1h, 10 km/hr, Extra ₹20/km, CC ₹50/hr included)",
                    "OMNI / Eeco: ₹450/hr (Incl. ₹75 CC) • 100 KM / 10h Package ₹3,500 • Extra ₹13/km",
                    "Ertiga / Rumion (SUV/SUV+): ₹525/hr (Incl. ₹75 CC, Min 3h) • 100 KM / 10h Package ₹3,800 • Extra ₹14/km",
                    "Innova: ₹600/hr (Incl. ₹100 CC, Min 4h) • 100 KM / 10h Package ₹4,000 • Extra ₹15/km",
                    "Grace Period: 5 minutes after each completed hour block • Waiting Charge: ₹0",
                    "Hourly commission is already included in the hourly meter rate (never added twice)."
                )
            )

            TariffDetailCard(
                title = "DIRECT METER MODE (NO OTP • CUSTOMER INVOICE ONLY)",
                lines = listOf(
                    "Local Default: Base ₹${DirectMeterTariffSnapshot.DEFAULT_DIRECT_LOCAL_BASE.toInt()} + ₹${DirectMeterTariffSnapshot.DEFAULT_DIRECT_LOCAL_PER_KM.toInt()}/km",
                    "Hourly Rental Default: ₹${DirectMeterTariffSnapshot.DEFAULT_DIRECT_HOURLY_BASE.toInt()}/hr (Includes ${DirectMeterTariffSnapshot.DEFAULT_DIRECT_HOURLY_INCLUDED_KM.toInt()} km/hr, Extra ₹${DirectMeterTariffSnapshot.DEFAULT_DIRECT_HOURLY_EXTRA_KM.toInt()}/km)",
                    "Outstation Default: Base ₹${DirectMeterTariffSnapshot.DEFAULT_DIRECT_OUTSTATION_BASE.toInt()} + ₹${DirectMeterTariffSnapshot.DEFAULT_DIRECT_OUTSTATION_PER_KM.toInt()}/km",
                    "Default Waiting Charge: ₹${DirectMeterTariffSnapshot.DEFAULT_DIRECT_WAITING_PER_MIN.toInt()}/min (Local & Outstation)",
                    "Fully editable independently in Settings → Direct Meter Settings. Zero Vendor CC & zero GPay settlement."
                )
            )

            TariffDetailCard(
                title = "AUTOMATIC LOCAL CC RULES (OTP METER ONLY — ZERO GAPS)",
                accentColor = TdAmber,
                lines = listOf(
                    "0 – 5.00 KM: ₹25",
                    "> 5.00 – 7.50 KM: ₹50",
                    "> 7.50 – 10.00 KM: ₹70",
                    "> 10.00 – 20.00 KM: ₹80",
                    "> 20.00 KM: ₹100",
                    "Entire Customer Bill > ₹1,000 (including additional charges): 10% of ENTIRE customer bill (replaces fixed distance CC)."
                )
            )

            TariffDetailCard(
                title = "OUTSTATION TARIFF (OTP METER)",
                lines = listOf(
                    "One-Way: ₹${outstationRef?.oneWayInitialCharge?.toInt() ?: 300} base + ₹${outstationRef?.oneWayPerKm?.toInt() ?: 30}/km",
                    "Round-Trip: ₹${outstationRef?.roundTripInitialCharge?.toInt() ?: 500} base + ₹${outstationRef?.roundTripPerKm?.toInt() ?: 15}/km",
                    "Driver Allowance (>12 Hours): ₹${SangamTariffConfig.outstationOver12HoursDriverAllowance.toInt()}",
                    "Company Commission (CC): ${(SangamTariffConfig.outstationCommissionPercent.toDouble() * 100).toInt()}% on tariff subtotal (or 10% of total if > ₹1,000)",
                    "Additional Waiting: Manual entry only via Extra Charges (no automatic waiting timer)"
                )
            )

            TariffDetailCard(
                title = "LOCAL NORMAL & LOCAL MINIMUM BILL",
                lines = VehicleCategory.entries.mapNotNull { cat ->
                    val normal = SangamTariffConfig.localNormalRates[cat]
                    val min = SangamTariffConfig.localMinimumBillRates[cat]
                    if (normal == null || min == null) null else {
                        val minDesc = if (min.secondSlabAmount != null && min.secondSlabMaxKm != null) {
                            "Min 1–${min.firstSlabMaxKm}km ₹${min.firstSlabAmount.toInt()}, ${min.firstSlabMaxKm}–${min.secondSlabMaxKm}km ₹${min.secondSlabAmount.toInt()}"
                        } else {
                            "Min 1–${min.firstSlabMaxKm}km ₹${min.firstSlabAmount.toInt()}"
                        }
                        "${cat.displayName}: ₹${normal.initialCharge.toInt()} + ₹${normal.perKmRate.toInt()}/km | $minDesc"
                    }
                } + listOf(
                    "Waiting Charge: ₹${SangamTariffConfig.localWaitingRatePerMin.toInt()}/min (Local Normal & Local Min Bill)",
                    "Additional Charges: Outside City Limits & Night Allowance (11 PM–6 AM) are MANUAL amount entry only (no automatic ₹80/₹50)."
                )
            )

            TariffDetailCard(
                title = "DAY RENTAL TARIFF",
                lines = VehicleCategory.entries.mapNotNull { cat ->
                    val r = SangamTariffConfig.dayRentalRates[cat] ?: return@mapNotNull null
                    "${cat.displayName}: ₹${r.baseAmount.toInt()} + ₹${r.perKmRate.toInt()}/km"
                } + listOf(
                    "Innova Crysta: ₹${SangamTariffConfig.innovaCrystaDayRentalRate.baseAmount.toInt()} + ₹${SangamTariffConfig.innovaCrystaDayRentalRate.perKmRate.toInt()}/km",
                    "Company Commission (CC): Variable / Managed manually per trip (no fixed percentage)"
                )
            )

            TariffDetailCard(
                title = "DRIVER CC PAYMENT REFERENCE (OTP METER ONLY)",
                accentColor = TdAmber,
                lines = listOf(
                    "Official GET TAXI GPay Number: ${SangamTariffConfig.VENDOR_GPAY_NUMBER}",
                    "Driver CC is shown strictly in OTP Driver Settlement & Internal CC Report (hidden from Customer Invoices and Direct Meter)."
                )
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TariffDetailCard(
    title: String,
    lines: List<String>,
    accentColor: Color? = null
) {
    val colors = LocalGetTaxiColors.current
    val resolvedAccent = accentColor ?: colors.yellowText
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = resolvedAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            HorizontalDivider(color = colors.border)
            lines.forEach { line ->
                Text(
                    text = "• $line",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
