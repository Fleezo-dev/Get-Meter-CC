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
import com.example.util.SangamTariffConfig
import com.example.util.SangamTariffConfig.VehicleCategory

private val TdBlack = Color(0xFF090A0C)
private val TdCharcoal = Color(0xFF14171C)
private val TdCard = Color(0xFF1E222A)
private val TdBorder = Color(0xFF2D333E)
private val TdYellow = Color(0xFFFFC107)
private val TdWhite = Color(0xFFFFFFFF)
private val TdMuted = Color(0xFF9CA3AF)
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

    val outstationRef = SangamTariffConfig.outstationRates[VehicleCategory.MINI]
    val hourlyTableSummary = SangamTariffConfig.hourlyPackagesUniform.entries.joinToString("  •  ") { (hr, amt) ->
        "${hr}h: ₹${amt.toInt()}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GET TAXI TARIFF DETAILS",
                        color = TdYellow,
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
                            tint = TdYellow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TdBlack)
            )
        },
        containerColor = TdCharcoal
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
                title = "HOURLY RENTAL TARIFF (UNIFORM ALL VEHICLES)",
                lines = listOf(
                    "Rate: ₹${SangamTariffConfig.hourlyRatePerChargedHour.toInt()} per billed hour",
                    "Included Distance: ${SangamTariffConfig.hourlyIncludedKmPerHour} km per billed hour",
                    "Extra Distance Rate: ₹${SangamTariffConfig.hourlyExtraKmRate.toInt()} per km",
                    "Grace Period: 5 minutes after each completed hour block",
                    "Waiting Charges: ₹${SangamTariffConfig.hourlyWaitingPerMin.toInt()} (Zero waiting time charged)",
                    "Driver CC: ₹${SangamTariffConfig.hourlyDriverCcPerChargedHour.toInt()} per billed hour (No 10% CC)",
                    "Packages (1h–12h): $hourlyTableSummary"
                )
            )

            TariffDetailCard(
                title = "OUTSTATION TARIFF (ALL 5 CATEGORIES)",
                lines = listOf(
                    "One-Way: ₹${outstationRef?.oneWayInitialCharge?.toInt() ?: 300} base + ₹${outstationRef?.oneWayPerKm?.toInt() ?: 30}/km",
                    "Round-Trip: ₹${outstationRef?.roundTripInitialCharge?.toInt() ?: 500} base + ₹${outstationRef?.roundTripPerKm?.toInt() ?: 15}/km",
                    "Driver Allowance (>12 Hours): ₹${SangamTariffConfig.outstationOver12HoursDriverAllowance.toInt()}",
                    "Company Commission (CC): ${(SangamTariffConfig.outstationCommissionPercent.toDouble() * 100).toInt()}% on tariff subtotal (excl. toll/permit/parking)",
                    "Waiting Time: No automatic waiting fee charged on outstation trips"
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
                    "Modifiers: Outside City +₹${SangamTariffConfig.localOutsideCityCharge.toInt()} | Night (11 PM–6 AM) +₹${SangamTariffConfig.localNightCharge.toInt()}",
                    "Local CC Slabs: 1–5 KM ₹30 | 6–10 KM ₹60–₹75 | 10–25 KM ₹80 | >25 KM or >₹800 Bill 10%"
                )
            )

            TariffDetailCard(
                title = "DAY RENTAL TARIFF",
                lines = VehicleCategory.entries.mapNotNull { cat ->
                    val r = SangamTariffConfig.dayRentalRates[cat] ?: return@mapNotNull null
                    "${cat.displayName}: ₹${r.baseAmount.toInt()} + ₹${r.perKmRate.toInt()}/km"
                } + listOf(
                    "Innova Crysta: ₹${SangamTariffConfig.innovaCrystaDayRentalRate.baseAmount.toInt()} + ₹${SangamTariffConfig.innovaCrystaDayRentalRate.perKmRate.toInt()}/km"
                )
            )

            TariffDetailCard(
                title = "DRIVER CC PAYMENT REFERENCE",
                accentColor = TdAmber,
                lines = listOf(
                    "Official GET TAXI GPay Number: ${SangamTariffConfig.VENDOR_GPAY_NUMBER}",
                    "Driver CC is shown strictly in Driver Settlement & Internal CC Report (hidden from Customer Invoices)."
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
    accentColor: Color = TdYellow
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TdCard),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, TdBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            HorizontalDivider(color = TdBorder)
            lines.forEach { line ->
                Text(
                    text = "• $line",
                    color = TdWhite,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
