package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.TripEntity
import com.example.ui.theme.LocalGetTaxiColors
import com.example.util.PdfGenerator
import com.example.util.SangamTariffCalculator
import com.example.util.SangamTariffConfig
import com.example.util.ShareUtils
import com.example.util.TripSnapshotMapper
import com.example.viewmodel.MeterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripHistoryScreen(
    viewModel: MeterViewModel,
    onNavigateBack: () -> Unit,
    onOpenTripDetail: (Int) -> Unit
) {
    val colors = LocalGetTaxiColors.current
    val trips by viewModel.tripHistory.collectAsStateWithLifecycle()
    var modeFilter by remember { mutableStateOf("ALL") } // ALL, OTP, DIRECT

    val filteredTrips = remember(trips, modeFilter) {
        when (modeFilter) {
            SangamTariffConfig.METER_MODE_OTP -> trips.filter {
                !it.meterMode.equals(SangamTariffConfig.METER_MODE_DIRECT, ignoreCase = true)
            }
            SangamTariffConfig.METER_MODE_DIRECT -> trips.filter {
                it.meterMode.equals(SangamTariffConfig.METER_MODE_DIRECT, ignoreCase = true)
            }
            else -> trips
        }
    }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TRIP HISTORY",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "LOCAL & SYNC-READY RECORDS (${filteredTrips.size})",
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("trip_history_back_button")
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
        ) {
            // Mode Filter Chips (All / OTP Meter / Direct Meter)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All (${trips.size})",
                    SangamTariffConfig.METER_MODE_OTP to "OTP Meter",
                    SangamTariffConfig.METER_MODE_DIRECT to "Direct Meter"
                ).forEach { (key, label) ->
                    val selected = modeFilter == key
                    FilterChip(
                        selected = selected,
                        onClick = { modeFilter = key },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primaryAccent,
                            selectedLabelColor = colors.onPrimaryAccent,
                            containerColor = colors.surface,
                            labelColor = colors.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = colors.border,
                            selectedBorderColor = colors.primaryAccent
                        ),
                        modifier = Modifier.testTag("history_filter_${key.lowercase(Locale.US)}")
                    )
                }
            }

            if (filteredTrips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (trips.isEmpty()) "No Completed Trips Yet" else "No Matching Trips",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Every completed OTP and Direct Meter trip is automatically saved on this device so you can access Customer Invoices and OTP Vendor CC Reports anytime.",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("trip_history_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTrips, key = { it.id }) { trip ->
                        TripHistoryCard(
                            trip = trip,
                            onClick = { onOpenTripDetail(trip.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripHistoryCard(
    trip: TripEntity,
    onClick: () -> Unit
) {
    val colors = LocalGetTaxiColors.current
    val breakdown = remember(trip) { TripSnapshotMapper.toBreakdown(trip) }
    val isDirectMeter = breakdown.isDirectMeter ||
        trip.meterMode.equals(SangamTariffConfig.METER_MODE_DIRECT, ignoreCase = true)
    val dateLabel = remember(trip.endTime, trip.invoiceDateText, trip.invoiceTimeText) {
        if (trip.invoiceDateText.isNotBlank()) {
            if (trip.invoiceTimeText.isNotBlank()) "${trip.invoiceDateText}, ${trip.invoiceTimeText}" else trip.invoiceDateText
        } else {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
                .format(Date(trip.endTime.takeIf { it > 0L } ?: trip.startTime))
        }
    }
    val directCatLabel = (breakdown.directCategory
        ?: SangamTariffConfig.DirectMeterCategory.fromId(trip.directCategoryId)).displayName
    val categoryLabel = if (isDirectMeter) {
        "DIRECT METER"
    } else if (trip.isInnovaCrysta) {
        "INNOVA CRYSTA"
    } else {
        breakdown.vehicleCategory.displayName
    }
    val tripModeLabel = if (isDirectMeter) directCatLabel else breakdown.tripType.displayName
    val isConfirmed = trip.settlementStatus.equals(TripSnapshotMapper.SETTLEMENT_CONFIRMED, ignoreCase = true)

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_trip_card_${trip.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = trip.tripNumber.ifBlank { "Trip #${trip.id}" },
                            color = colors.primaryAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = if (isDirectMeter) colors.yellowContainer else colors.surfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isDirectMeter) colors.yellowHighlight else colors.border)
                        ) {
                            Text(
                                text = if (isDirectMeter) "DIRECT" else "OTP",
                                color = if (isDirectMeter) colors.yellowText else colors.textSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = dateLabel,
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                if (isDirectMeter) {
                    Surface(
                        color = colors.yellowContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.yellowHighlight)
                    ) {
                        Text(
                            text = "INVOICE ONLY",
                            color = colors.yellowText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        color = if (isConfirmed) colors.greenSuccess.copy(alpha = 0.14f) else HistAmber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isConfirmed) colors.greenSuccess else HistAmber)
                    ) {
                        Text(
                            text = if (isConfirmed) "CC CONFIRMED" else "CC PENDING",
                            color = if (isConfirmed) colors.greenSuccess else HistAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$categoryLabel • $tripModeLabel",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${String.format(Locale.US, "%.2f", trip.distanceKm)} km • ${trip.durationSeconds / 60}m",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (trip.driverName.isNotBlank()) {
                Text(
                    text = "Driver: ${trip.driverName}",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = colors.border)

            if (isDirectMeter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HistoryAmountColumn(
                        label = "CUSTOMER BILL",
                        value = SangamTariffCalculator.formatRupees(breakdown.customerBillTotal),
                        valueColor = colors.primaryAccent,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryAmountColumn(
                        label = "BILLING MODE",
                        value = "Direct • No CC",
                        valueColor = colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HistoryAmountColumn(
                        label = "CUSTOMER BILL",
                        value = SangamTariffCalculator.formatRupees(breakdown.customerBillTotal),
                        valueColor = colors.primaryAccent,
                        modifier = Modifier.weight(1f)
                    )
                    val ccValueText = when (val cc = breakdown.commissionResult) {
                        is SangamTariffCalculator.CommissionResult.Confirmed -> SangamTariffCalculator.formatRupees(cc.amount)
                        is SangamTariffCalculator.CommissionResult.NeedsConfirmation -> "Confirm"
                        is SangamTariffCalculator.CommissionResult.NotSpecified -> {
                            if (breakdown.tripType == SangamTariffConfig.SangamTripType.DAY_RENTAL) "Manual" else "N/A"
                        }
                    }
                    HistoryAmountColumn(
                        label = "APPROVED CC",
                        value = ccValueText,
                        valueColor = HistAmber,
                        modifier = Modifier.weight(1f)
                    )
                    val netValueText = when (val bal = breakdown.driverBalanceStatus) {
                        is SangamTariffCalculator.DriverBalanceStatus.Confirmed -> SangamTariffCalculator.formatRupees(bal.amount)
                        is SangamTariffCalculator.DriverBalanceStatus.RangePendingConfirmation -> "Pending"
                    }
                    HistoryAmountColumn(
                        label = "DRIVER NET",
                        value = netValueText,
                        valueColor = colors.greenSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryAmountColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalGetTaxiColors.current
    Column(modifier = modifier) {
        Text(text = label, color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripHistoryDetailScreen(
    tripId: Int,
    viewModel: MeterViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalGetTaxiColors.current
    val tripFlow = remember(tripId) { viewModel.getHistoricalTripFlow(tripId) }
    val trip by tripFlow.collectAsStateWithLifecycle(initialValue = null)

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = trip?.tripNumber?.ifBlank { "TRIP DETAILS" } ?: "TRIP DETAILS",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Trip History",
                            tint = colors.primaryAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        val record = trip
        if (record == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading saved trip...", color = colors.textSecondary, fontSize = 13.sp)
            }
            return@Scaffold
        }

        // Reconstruct EXACT original financial snapshot (NEVER recalculate with current tariff config)
        val breakdown = remember(record) { TripSnapshotMapper.toBreakdown(record) }
        val isDirectMeter = breakdown.isDirectMeter ||
            record.meterMode.equals(SangamTariffConfig.METER_MODE_DIRECT, ignoreCase = true)
        val directCatLabel = (breakdown.directCategory
            ?: SangamTariffConfig.DirectMeterCategory.fromId(record.directCategoryId)).displayName
        val categoryLabel = if (isDirectMeter) {
            "DIRECT METER"
        } else if (record.isInnovaCrysta) {
            "INNOVA CRYSTA"
        } else {
            breakdown.vehicleCategory.displayName
        }
        val modeSubtitle = if (isDirectMeter) directCatLabel else breakdown.tripType.displayName

        val dateDisplay = remember(record.endTime, record.invoiceDateText, record.invoiceTimeText) {
            if (record.invoiceDateText.isNotBlank()) {
                if (record.invoiceTimeText.isNotBlank()) "${record.invoiceDateText}, ${record.invoiceTimeText}" else record.invoiceDateText
            } else {
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
                    .format(Date(record.endTime.takeIf { it > 0L } ?: record.startTime))
            }
        }
        val isConfirmed = record.settlementStatus.equals(TripSnapshotMapper.SETTLEMENT_CONFIRMED, ignoreCase = true)

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isDirectMeter) {
                Surface(
                    color = colors.yellowContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.yellowHighlight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = colors.yellowText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIRECT METER TRIP • CUSTOMER INVOICE ONLY (NO VENDOR CC)",
                            color = colors.yellowText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // SECTION 1: SAVED CUSTOMER BILL (NO CC / NO COMMISSION / NO GPAY 9043743777)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.meterDisplayBg),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.5.dp, colors.primaryAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_detail_customer_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "AMOUNT TO COLLECT FROM CUSTOMER",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = SangamTariffCalculator.formatRupees(breakdown.customerBillTotal),
                        color = if (colors.isDark) colors.yellowHighlight else colors.primaryAccent,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "$categoryLabel • $modeSubtitle",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bill ${record.tripNumber} • $dateDisplay",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", record.distanceKm)} km • ${record.durationSeconds / 60} min",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SAVED CUSTOMER FARE BREAKDOWN",
                        color = colors.yellowText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    HorizontalDivider(color = colors.border)
                    breakdown.lineItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.label, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (item.detail.isNotBlank()) {
                                    Text(item.detail, color = colors.textSecondary, fontSize = 11.sp)
                                }
                            }
                            Text(
                                text = SangamTariffCalculator.formatRupees(item.amount),
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    HorizontalDivider(color = colors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val file = PdfGenerator.generateSangamBillPdf(context, record, breakdown)
                                ShareUtils.sharePdf(context, file, "GET TAXI Customer Bill ${record.tripNumber}")
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.primaryAccent),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("history_customer_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CUSTOMER PDF", color = colors.primaryAccent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val text = ShareUtils.buildSangamReceiptText(record, breakdown, includeSettlementNote = false)
                                ShareUtils.shareText(context, text, "GET TAXI Customer Invoice")
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("history_customer_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHARE BILL", color = colors.textPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }

            // SECTION 2: SAVED VENDOR / DRIVER CC SHARE REPORT (OTP METER ONLY)
            if (!isDirectMeter) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_detail_vendor_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SAVED VENDOR / DRIVER CC REPORT",
                                color = colors.yellowText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = if (isConfirmed) "CONFIRMED" else "PENDING",
                                color = if (isConfirmed) colors.greenSuccess else HistAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        HorizontalDivider(color = colors.border)

                        DetailRow("Driver", record.driverName.ifBlank { "GET TAXI Driver" })
                        DetailRow("Vehicle", if (record.vehicleNumber.isNotBlank()) "$categoryLabel • ${record.vehicleNumber}" else categoryLabel)
                        DetailRow("Trip Type", breakdown.tripType.displayName)
                        DetailRow("Customer Bill", SangamTariffCalculator.formatRupees(breakdown.customerBillTotal))

                        when (val cc = breakdown.commissionResult) {
                            is SangamTariffCalculator.CommissionResult.Confirmed -> {
                                DetailRow("Saved CC Rule", cc.ruleLabel)
                                DetailRow("Saved CC Amount", SangamTariffCalculator.formatRupees(cc.amount))
                            }
                            is SangamTariffCalculator.CommissionResult.NeedsConfirmation -> {
                                DetailRow("Saved CC Rule", cc.reason)
                                DetailRow("Saved CC Amount", "Needs Confirmation")
                            }
                            is SangamTariffCalculator.CommissionResult.NotSpecified -> {
                                DetailRow("Saved CC Rule", cc.reason)
                                DetailRow(
                                    "Saved CC Amount",
                                    if (breakdown.tripType == SangamTariffConfig.SangamTripType.DAY_RENTAL) "Variable / Manual" else "Not Specified"
                                )
                            }
                        }

                        val netBal = when (val bal = breakdown.driverBalanceStatus) {
                            is SangamTariffCalculator.DriverBalanceStatus.Confirmed -> SangamTariffCalculator.formatRupees(bal.amount)
                            is SangamTariffCalculator.DriverBalanceStatus.RangePendingConfirmation -> "Pending Confirmation"
                        }
                        DetailRow("Saved Driver Net Balance", netBal)
                        DetailRow("GET TAXI GPay", SangamTariffConfig.VENDOR_GPAY_NUMBER)

                        if (breakdown.tripType == SangamTariffConfig.SangamTripType.DAY_RENTAL) {
                            var historyManualCc by remember(record.id, record.ccAmount, record.ccStatus) {
                                mutableStateOf(
                                    if (breakdown.commissionResult is SangamTariffCalculator.CommissionResult.Confirmed) {
                                        String.format(Locale.US, "%.2f", breakdown.commissionResult.amount.toDouble())
                                    } else {
                                        ""
                                    }
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surfaceElevated, RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "UPDATE MANUAL DAY RENTAL CC",
                                    color = colors.yellowText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = historyManualCc,
                                        onValueChange = { historyManualCc = it },
                                        label = { Text("CC Amount (₹)") },
                                        placeholder = { Text("e.g. 250") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.primaryAccent,
                                            unfocusedBorderColor = colors.border,
                                            focusedLabelColor = colors.primaryAccent,
                                            unfocusedLabelColor = colors.textSecondary,
                                            focusedTextColor = colors.textPrimary,
                                            unfocusedTextColor = colors.textPrimary,
                                            cursorColor = colors.primaryAccent
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("history_day_rental_cc_input")
                                    )
                                    Button(
                                        onClick = {
                                            val parsed = historyManualCc.trim().toDoubleOrNull()
                                            if (parsed != null && parsed >= 0.0) {
                                                viewModel.updateManualDayRentalCc(record.id, parsed)
                                                Toast.makeText(
                                                    context,
                                                    "Manual Day Rental CC saved",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Enter a valid CC amount (₹0 or more)",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryAccent,
                                            contentColor = colors.onPrimaryAccent
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(52.dp)
                                            .testTag("history_save_day_rental_cc_button")
                                    ) {
                                        Text("SAVE CC", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("GET TAXI GPay", SangamTariffConfig.VENDOR_GPAY_NUMBER))
                                    Toast.makeText(context, "Copied ${SangamTariffConfig.VENDOR_GPAY_NUMBER}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.primaryAccent),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COPY GPAY", color = colors.primaryAccent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                            if (!isConfirmed) {
                                Button(
                                    onClick = {
                                        viewModel.markTripSettlementStatus(record.id, TripSnapshotMapper.SETTLEMENT_CONFIRMED)
                                        Toast.makeText(context, "CC marked as confirmed", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.greenSuccess, contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("MARK CC PAID", fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val file = PdfGenerator.generateInternalCcReportPdf(context, record, breakdown, record.driverIdSnapshot)
                                    ShareUtils.sharePdf(context, file, "GET TAXI Vendor Share Report ${record.tripNumber}")
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colors.primaryAccent),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("history_vendor_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("VENDOR PDF", color = colors.primaryAccent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val text = ShareUtils.buildInternalCcReportText(record, breakdown, record.driverIdSnapshot)
                                    ShareUtils.shareText(context, text, "GET TAXI Vendor Share Report")
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("history_vendor_share_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SHARE REPORT", color = colors.textPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = LocalGetTaxiColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(0.9f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
    }
}
