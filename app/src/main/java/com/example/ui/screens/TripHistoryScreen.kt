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
import com.example.util.PdfGenerator
import com.example.util.SangamTariffCalculator
import com.example.util.SangamTariffConfig
import com.example.util.ShareUtils
import com.example.util.TripSnapshotMapper
import com.example.viewmodel.MeterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistBlack = Color(0xFF090A0C)
private val HistCharcoal = Color(0xFF14171C)
private val HistCard = Color(0xFF1E222A)
private val HistBorder = Color(0xFF2D333E)
private val HistYellow = Color(0xFFFFC107)
private val HistWhite = Color(0xFFFFFFFF)
private val HistMuted = Color(0xFF9CA3AF)
private val HistGreen = Color(0xFF22C55E)
private val HistAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripHistoryScreen(
    viewModel: MeterViewModel,
    onNavigateBack: () -> Unit,
    onOpenTripDetail: (Int) -> Unit
) {
    val trips by viewModel.tripHistory.collectAsStateWithLifecycle()

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TRIP HISTORY",
                            color = HistWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "OFFLINE LOCAL RECORDS (${trips.size})",
                            color = HistMuted,
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
                            tint = HistYellow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HistBlack)
            )
        },
        containerColor = HistCharcoal
    ) { innerPadding ->
        if (trips.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
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
                        tint = HistMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No Completed Trips Yet",
                        color = HistWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Every completed trip is automatically saved offline on this device so you can recover Customer Bills and Vendor CC Reports anytime.",
                        color = HistMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .testTag("trip_history_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trips, key = { it.id }) { trip ->
                    TripHistoryCard(
                        trip = trip,
                        onClick = { onOpenTripDetail(trip.id) }
                    )
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
    val breakdown = remember(trip) { TripSnapshotMapper.toBreakdown(trip) }
    val dateLabel = remember(trip.endTime, trip.invoiceDateText, trip.invoiceTimeText) {
        if (trip.invoiceDateText.isNotBlank()) {
            if (trip.invoiceTimeText.isNotBlank()) "${trip.invoiceDateText}, ${trip.invoiceTimeText}" else trip.invoiceDateText
        } else {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
                .format(Date(trip.endTime.takeIf { it > 0L } ?: trip.startTime))
        }
    }
    val categoryLabel = if (trip.isInnovaCrysta) "INNOVA CRYSTA" else breakdown.vehicleCategory.displayName
    val isConfirmed = trip.settlementStatus.equals(TripSnapshotMapper.SETTLEMENT_CONFIRMED, ignoreCase = true)

    Card(
        colors = CardDefaults.cardColors(containerColor = HistCard),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HistBorder),
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
                    Text(
                        text = trip.tripNumber.ifBlank { "Trip #${trip.id}" },
                        color = HistYellow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateLabel,
                        color = HistMuted,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = if (isConfirmed) Color(0xFF0E291A) else Color(0xFF2B2110),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isConfirmed) HistGreen else HistAmber)
                ) {
                    Text(
                        text = if (isConfirmed) "CC CONFIRMED" else "CC PENDING",
                        color = if (isConfirmed) HistGreen else HistAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$categoryLabel • ${breakdown.tripType.displayName}",
                    color = HistWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${String.format(Locale.US, "%.2f", trip.distanceKm)} km • ${trip.durationSeconds / 60}m",
                    color = HistMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (trip.driverName.isNotBlank()) {
                Text(
                    text = "Driver: ${trip.driverName}",
                    color = HistMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = HistBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HistoryAmountColumn(
                    label = "CUSTOMER BILL",
                    value = SangamTariffCalculator.formatRupees(breakdown.customerBillTotal),
                    valueColor = HistYellow,
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
                    valueColor = HistGreen,
                    modifier = Modifier.weight(1f)
                )
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
    Column(modifier = modifier) {
        Text(text = label, color = HistMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
    val tripFlow = remember(tripId) { viewModel.getHistoricalTripFlow(tripId) }
    val trip by tripFlow.collectAsStateWithLifecycle(initialValue = null)

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = trip?.tripNumber?.ifBlank { "TRIP DETAILS" } ?: "TRIP DETAILS",
                        color = HistWhite,
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
                            tint = HistYellow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HistBlack)
            )
        },
        containerColor = HistCharcoal
    ) { innerPadding ->
        val record = trip
        if (record == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading saved trip...", color = HistMuted, fontSize = 13.sp)
            }
            return@Scaffold
        }

        // Reconstruct EXACT original financial snapshot (NEVER recalculate with current tariff config)
        val breakdown = remember(record) { TripSnapshotMapper.toBreakdown(record) }
        val categoryLabel = if (record.isInnovaCrysta) "INNOVA CRYSTA" else breakdown.vehicleCategory.displayName
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
            // SECTION 1: SAVED CUSTOMER BILL (NO CC / NO COMMISSION / NO GPAY 9043743777)
            Card(
                colors = CardDefaults.cardColors(containerColor = HistBlack),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.5.dp, HistYellow),
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
                        color = HistMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = SangamTariffCalculator.formatRupees(breakdown.customerBillTotal),
                        color = HistYellow,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "$categoryLabel • ${breakdown.tripType.displayName}",
                        color = HistWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bill ${record.tripNumber} • $dateDisplay",
                        color = HistMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", record.distanceKm)} km • ${record.durationSeconds / 60} min",
                        color = HistWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = HistCard),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HistBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SAVED CUSTOMER FARE BREAKDOWN",
                        color = HistYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    HorizontalDivider(color = HistBorder)
                    breakdown.lineItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.label, color = HistWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (item.detail.isNotBlank()) {
                                    Text(item.detail, color = HistMuted, fontSize = 11.sp)
                                }
                            }
                            Text(
                                text = SangamTariffCalculator.formatRupees(item.amount),
                                color = HistWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    HorizontalDivider(color = HistBorder)
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
                            border = BorderStroke(1.dp, HistYellow),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("history_customer_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = HistYellow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CUSTOMER PDF", color = HistYellow, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val text = ShareUtils.buildSangamReceiptText(record, breakdown, includeSettlementNote = false)
                                ShareUtils.shareText(context, text, "GET TAXI Customer Invoice")
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HistBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("history_customer_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = HistWhite, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHARE BILL", color = HistWhite, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }

            // SECTION 2: SAVED VENDOR / DRIVER CC SHARE REPORT (IMMUTABLE SNAPSHOT)
            Card(
                colors = CardDefaults.cardColors(containerColor = HistCard),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HistBorder),
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
                            color = HistYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (isConfirmed) "CONFIRMED" else "PENDING",
                            color = if (isConfirmed) HistGreen else HistAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    HorizontalDivider(color = HistBorder)

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
                                .background(HistBlack, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "UPDATE MANUAL DAY RENTAL CC",
                                color = HistYellow,
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
                                        focusedBorderColor = HistYellow,
                                        unfocusedBorderColor = HistBorder,
                                        focusedLabelColor = HistYellow,
                                        unfocusedLabelColor = HistMuted,
                                        focusedTextColor = HistWhite,
                                        unfocusedTextColor = HistWhite,
                                        cursorColor = HistYellow
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
                                        containerColor = HistYellow,
                                        contentColor = HistBlack
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
                            border = BorderStroke(1.dp, HistYellow),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = HistYellow, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("COPY GPAY", color = HistYellow, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        if (!isConfirmed) {
                            Button(
                                onClick = {
                                    viewModel.markTripSettlementStatus(record.id, TripSnapshotMapper.SETTLEMENT_CONFIRMED)
                                    Toast.makeText(context, "CC marked as confirmed", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = HistGreen, contentColor = HistBlack),
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
                            border = BorderStroke(1.dp, HistYellow),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("history_vendor_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = HistYellow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VENDOR PDF", color = HistYellow, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val text = ShareUtils.buildInternalCcReportText(record, breakdown, record.driverIdSnapshot)
                                ShareUtils.shareText(context, text, "GET TAXI Vendor Share Report")
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HistBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("history_vendor_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = HistWhite, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHARE REPORT", color = HistWhite, fontWeight = FontWeight.Black, fontSize = 11.sp)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, color = HistMuted, fontSize = 12.sp, modifier = Modifier.weight(0.9f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = HistWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
    }
}
