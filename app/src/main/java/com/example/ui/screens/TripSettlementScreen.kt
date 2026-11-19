package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.VendorLogoBadge
import com.example.util.PdfGenerator
import com.example.util.SangamTariffCalculator
import com.example.util.SangamTariffConfig
import com.example.util.ShareUtils
import com.example.util.UpiQrCodeGenerator
import com.example.viewmodel.MeterViewModel
import java.io.File
import java.util.Locale

private val SettleBlack = Color(0xFF090A0C)
private val SettleCharcoal = Color(0xFF14171C)
private val SettleCard = Color(0xFF1E222A)
private val SettleBorder = Color(0xFF2D333E)
private val SettleYellow = Color(0xFFFFC107)
private val SettleWhite = Color(0xFFFFFFFF)
private val SettleMuted = Color(0xFF9CA3AF)
private val SettleAmber = Color(0xFFF59E0B)
private val SettleGreen = Color(0xFF22C55E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSettlementScreen(
    viewModel: MeterViewModel,
    onDoneClearAndReturnHome: () -> Unit
) {
    val context = LocalContext.current
    val settlement by viewModel.currentSettlement.collectAsStateWithLifecycle()
    val driverProfile by viewModel.driverProfile.collectAsStateWithLifecycle()

    var showCustomerEditor by remember { mutableStateOf(false) }
    var ccTransferConfirmed by remember { mutableStateOf(false) }
    var custName by remember(settlement?.tripEntity?.tripNumber) {
        mutableStateOf(settlement?.tripEntity?.customerName ?: "")
    }
    var custMobile by remember(settlement?.tripEntity?.tripNumber) {
        mutableStateOf(settlement?.tripEntity?.customerMobile ?: "")
    }
    var custAddress by remember(settlement?.tripEntity?.tripNumber) {
        mutableStateOf(settlement?.tripEntity?.customerAddress ?: "")
    }

    BackHandler {
        viewModel.endTripAndClearSettlement()
        onDoneClearAndReturnHome()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TRIP SETTLEMENT",
                        color = SettleWhite,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 0.8.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.endTripAndClearSettlement()
                            onDoneClearAndReturnHome()
                        },
                        modifier = Modifier.testTag("settlement_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back and Clear",
                            tint = SettleYellow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettleCharcoal)
            )
        },
        containerColor = SettleCharcoal,
        bottomBar = {
            Surface(
                color = SettleBlack,
                border = BorderStroke(1.dp, SettleBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.endTripAndClearSettlement()
                            onDoneClearAndReturnHome()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SettleYellow,
                            contentColor = SettleBlack
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("end_trip_clear_button")
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "END TRIP & CLEAR",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val active = settlement
        if (active == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active settlement session.",
                    color = SettleMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            return@Scaffold
        }

        val trip = active.tripEntity
        val breakdown = active.breakdown
        val categoryLabel = if (breakdown.isInnovaCrysta) "INNOVA CRYSTA" else breakdown.vehicleCategory.displayName
        val cleanDriverId = driverProfile?.driverId?.trim().orEmpty()
        val resolvedDriverName = driverProfile?.driverName?.takeIf { it.isNotBlank() }
            ?: trip.driverName.ifBlank { "GET TAXI Driver" }
        val resolvedDriverMobile = driverProfile?.driverMobile?.takeIf { it.isNotBlank() }
            ?: trip.driverMobile
        val resolvedVehicleReg = driverProfile?.vehicleNumber?.takeIf { it.isNotBlank() }
            ?: trip.vehicleNumber

        val dateDisplay = remember(trip.endTime, trip.invoiceDateText, trip.invoiceTimeText) {
            if (trip.invoiceDateText.isNotBlank()) {
                if (trip.invoiceTimeText.isNotBlank()) "${trip.invoiceDateText}, ${trip.invoiceTimeText}" else trip.invoiceDateText
            } else {
                java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
                    .format(java.util.Date(trip.endTime.takeIf { it > 0L } ?: System.currentTimeMillis()))
            }
        }

        val qrUri = driverProfile?.qrCodeUri?.takeIf { it.isNotBlank() }
        val upiId = driverProfile?.upiId?.trim().orEmpty()
        val generatedUpiBitmap: Bitmap? = remember(upiId, resolvedDriverName, breakdown.customerBillTotal, trip.tripNumber) {
            if (qrUri == null && upiId.isNotBlank()) {
                runCatching {
                    val uri = UpiQrCodeGenerator.buildUpiUri(
                        upiId = upiId,
                        driverName = resolvedDriverName,
                        fareAmount = breakdown.customerBillTotal.toDouble(),
                        transactionNote = "GET TAXI Bill ${trip.tripNumber}"
                    )
                    UpiQrCodeGenerator.generateQrCodeBitmap(uri, sizePx = 420)
                }.getOrNull()
            } else {
                null
            }
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Local Trip History Persistence Status Banner
            if (active.saveError != null && active.persistedTripId == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3F1212)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_save_error_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOCAL SAVE FAILED — TRIP DATA PRESERVED",
                                color = Color(0xFFF87171),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = active.saveError,
                            color = SettleWhite,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Button(
                            onClick = { viewModel.retrySaveCurrentSettlement() },
                            colors = ButtonDefaults.buttonColors(containerColor = SettleYellow, contentColor = SettleBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("retry_save_trip_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RETRY SAVE TO LOCAL HISTORY", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            } else if (active.persistedTripId != null) {
                Surface(
                    color = Color(0xFF0E291A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SettleGreen.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_saved_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SettleGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAVED TO LOCAL TRIP HISTORY (OFFLINE)",
                            color = SettleGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }

            // =================================================================
            // SECTION 1: CUSTOMER INVOICE (NO CC, NO COMMISSION, NO DRIVER BALANCE)
            // =================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = SettleBlack),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, SettleYellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settlement_total_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AMOUNT TO COLLECT FROM CUSTOMER",
                        color = SettleMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("amount_to_collect_label")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = SangamTariffCalculator.formatRupees(breakdown.customerBillTotal),
                        color = SettleYellow,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.testTag("settlement_total_amount")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusTag(text = categoryLabel, bg = SettleCard, fg = SettleWhite)
                        StatusTag(text = breakdown.tripType.displayName, bg = SettleCard, fg = SettleYellow)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Bill ${trip.tripNumber} • $dateDisplay",
                        color = SettleMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", breakdown.distanceKm)} km • ${trip.durationSeconds / 60} min",
                        color = SettleWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (breakdown.hasUnresolvedAmbiguity || breakdown.warnings.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2110)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, SettleAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settlement_ambiguity_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SettleAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CONFIRMATION REQUIRED (TARIFF RULE)",
                                color = SettleAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                        }
                        breakdown.ambiguities.forEach { amb ->
                            Text(
                                text = "• ${amb.title}: ${amb.description}",
                                color = SettleWhite,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            if (amb.candidateOptions.isNotEmpty()) {
                                Text(
                                    text = "  Options: ${amb.candidateOptions.joinToString(" OR ")}",
                                    color = SettleYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        breakdown.warnings.forEach { w ->
                            Text(text = "• $w", color = SettleWhite, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SettleCard),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SettleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settlement_line_items_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "CUSTOMER FARE BREAKDOWN",
                        color = SettleYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    HorizontalDivider(color = SettleBorder)
                    breakdown.lineItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    color = SettleWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.detail,
                                    color = SettleMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = SangamTariffCalculator.formatRupees(item.amount),
                                color = if (item.isPassThrough) SettleMuted else SettleWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    HorizontalDivider(color = SettleBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Final Payable", color = SettleYellow, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(
                            text = SangamTariffCalculator.formatRupees(breakdown.customerBillTotal),
                            color = SettleYellow,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // CUSTOMER UPI QR CODE PAYMENT CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = SettleCard),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SettleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_upi_qr_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SCAN TO PAY CUSTOMER FARE (UPI QR)",
                        color = SettleYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    if (qrUri != null) {
                        val modelData: Any = if (qrUri.startsWith("/")) File(qrUri) else qrUri
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, SettleYellow)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(modelData)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Customer UPI Payment QR Code",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(180.dp)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        if (upiId.isNotBlank()) {
                            Text(text = "UPI ID: $upiId", color = SettleMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (generatedUpiBitmap != null) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, SettleYellow)
                        ) {
                            Image(
                                bitmap = generatedUpiBitmap.asImageBitmap(),
                                contentDescription = "Generated UPI Payment QR Code",
                                modifier = Modifier
                                    .size(180.dp)
                                    .padding(10.dp)
                            )
                        }
                        Text(
                            text = "UPI ID: $upiId • ${SangamTariffCalculator.formatRupees(breakdown.customerBillTotal)}",
                            color = SettleWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Add your UPI ID or upload a static Payment QR image in Driver Profile to display a customer scan-and-pay QR code here.",
                            color = SettleMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // CUSTOMER INFO & SHARE / BILL PDF (CUSTOMER-ONLY RECEIPT)
            Card(
                colors = CardDefaults.cardColors(containerColor = SettleCard),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SettleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CUSTOMER BILL & RECEIPT",
                            color = SettleYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        TextButton(onClick = { showCustomerEditor = !showCustomerEditor }) {
                            Text(
                                text = if (showCustomerEditor) "Hide Details" else "Add Customer Info",
                                color = SettleYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (showCustomerEditor) {
                        OutlinedTextField(
                            value = custName,
                            onValueChange = {
                                custName = it
                                viewModel.updateSettlementCustomerInfo(it, custMobile, custAddress)
                            },
                            label = { Text("Customer Name") },
                            colors = settleFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = custMobile,
                            onValueChange = {
                                custMobile = it
                                viewModel.updateSettlementCustomerInfo(custName, it, custAddress)
                            },
                            label = { Text("Customer Mobile") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = settleFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = custAddress,
                            onValueChange = {
                                custAddress = it
                                viewModel.updateSettlementCustomerInfo(custName, custMobile, it)
                            },
                            label = { Text("Address / Notes") },
                            colors = settleFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val file = PdfGenerator.generateSangamBillPdf(context, trip, breakdown)
                                ShareUtils.sharePdf(context, file, "GET TAXI Customer Bill ${trip.tripNumber}")
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, SettleYellow),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_bill_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = SettleYellow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CUSTOMER BILL PDF", color = SettleYellow, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val text = ShareUtils.buildSangamReceiptText(trip, breakdown, includeSettlementNote = false)
                                ShareUtils.shareText(context, text, "GET TAXI Customer Invoice")
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, SettleBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_bill_text_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = SettleWhite, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHARE BILL", color = SettleWhite, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }

            // =================================================================
            // SECTION 2: DRIVER CC SETTLEMENT (STRICTLY DRIVER-ONLY)
            // =================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = SettleCard),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SettleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settlement_commission_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "DRIVER CC SETTLEMENT (DRIVER ONLY)",
                        color = SettleYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    when (val comm = breakdown.commissionResult) {
                        is SangamTariffCalculator.CommissionResult.Confirmed -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Approved CC Amount", color = SettleWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(comm.ruleLabel, color = SettleMuted, fontSize = 11.sp)
                                }
                                Text(
                                    text = SangamTariffCalculator.formatRupees(comm.amount),
                                    color = SettleYellow,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.testTag("confirmed_commission_amount")
                                )
                            }
                        }

                        is SangamTariffCalculator.CommissionResult.NeedsConfirmation -> {
                            StatusTag(text = "NEEDS CONFIRMATION", bg = Color(0xFF451A03), fg = SettleAmber)
                            Text(text = comm.reason, color = SettleWhite, fontSize = 12.sp, lineHeight = 16.sp)
                            if (comm.minPossibleAmount != null && comm.maxPossibleAmount != null) {
                                Text(
                                    text = "Rate Range: ${SangamTariffCalculator.formatRupees(comm.minPossibleAmount)} – ${SangamTariffCalculator.formatRupees(comm.maxPossibleAmount)}",
                                    color = SettleYellow,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            comm.candidateLabels.forEach { opt ->
                                Text(text = "• $opt", color = SettleMuted, fontSize = 11.sp)
                            }
                        }

                        is SangamTariffCalculator.CommissionResult.NotSpecified -> {
                            StatusTag(text = "NOT SPECIFIED IN CHART", bg = Color(0xFF451A03), fg = SettleAmber)
                            Text(text = comm.reason, color = SettleWhite, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }

                    HorizontalDivider(color = SettleBorder)

                    when (val bal = breakdown.driverBalanceStatus) {
                        is SangamTariffCalculator.DriverBalanceStatus.Confirmed -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Driver Balance (After CC)", color = SettleWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = SangamTariffCalculator.formatRupees(bal.amount),
                                    color = SettleGreen,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.testTag("confirmed_driver_balance")
                                )
                            }
                        }

                        is SangamTariffCalculator.DriverBalanceStatus.RangePendingConfirmation -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Driver Balance (After CC)", color = SettleWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    val rangeStr = if (bal.minDriverBalance != null && bal.maxDriverBalance != null) {
                                        "Est. ${SangamTariffCalculator.formatRupees(bal.minDriverBalance)} – ${SangamTariffCalculator.formatRupees(bal.maxDriverBalance)}"
                                    } else {
                                        "Pending CC confirmation"
                                    }
                                    Text(rangeStr, color = SettleAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                StatusTag(text = "PENDING", bg = SettleBlack, fg = SettleAmber)
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SettleBlack),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SettleYellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("get_taxi_gpay_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VendorLogoBadge(
                            vendorId = SangamTariffConfig.VENDOR_ID,
                            vendorName = SangamTariffConfig.VENDOR_NAME,
                            height = 34.dp
                        )
                        StatusTag(
                            text = if (ccTransferConfirmed) "MANUAL CONFIRMATION NOTED" else "MANUAL GPAY TRANSFER",
                            bg = SettleCard,
                            fg = if (ccTransferConfirmed) SettleGreen else SettleAmber
                        )
                    }

                    Text(
                        text = "Transfer Driver CC via GPay",
                        color = SettleWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SettleCard, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("GET TAXI GPAY NUMBER", color = SettleMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = SangamTariffConfig.VENDOR_GPAY_NUMBER,
                                color = SettleYellow,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.testTag("gpay_number_text")
                            )
                        }
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("GET TAXI GPay", SangamTariffConfig.VENDOR_GPAY_NUMBER))
                                Toast.makeText(context, "Copied ${SangamTariffConfig.VENDOR_GPAY_NUMBER}", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SettleYellow, contentColor = SettleBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("copy_gpay_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("COPY", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("GET TAXI GPay", SangamTariffConfig.VENDOR_GPAY_NUMBER))
                            ccTransferConfirmed = true
                            viewModel.markCurrentSettlementCcConfirmed()
                            Toast.makeText(
                                context,
                                "GPay ${SangamTariffConfig.VENDOR_GPAY_NUMBER} copied. CC confirmation recorded.",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SettleYellow, contentColor = SettleBlack),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pay_cc_button")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay CC", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }

                    val commInstruction = when (val comm = breakdown.commissionResult) {
                        is SangamTariffCalculator.CommissionResult.Confirmed ->
                            "Send ${SangamTariffCalculator.formatRupees(comm.amount)} to ${SangamTariffConfig.VENDOR_GPAY_NUMBER} via GPay and confirm manually. Separate from customer billing and Baseware."
                        else ->
                            "Verify approved CC amount before transferring to GPay ${SangamTariffConfig.VENDOR_GPAY_NUMBER} and confirm manually."
                    }
                    Text(text = commInstruction, color = SettleMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }

            // =================================================================
            // SECTION 3: INTERNAL CC REPORT (READ-ONLY & SHAREABLE)
            // =================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = SettleCard),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SettleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("internal_cc_report_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "VENDOR / DRIVER SHARE REPORT (READ-ONLY)",
                        color = SettleYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    HorizontalDivider(color = SettleBorder)
                    val driverIdLine = buildString {
                        if (cleanDriverId.isNotBlank()) append("ID: $cleanDriverId")
                        if (resolvedDriverMobile.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append("Mob: $resolvedDriverMobile")
                        }
                    }.ifBlank { "Registered Driver" }
                    val vehicleSummary = if (resolvedVehicleReg.isNotBlank()) "$categoryLabel • $resolvedVehicleReg" else categoryLabel

                    ReportRow("Driver Name", resolvedDriverName)
                    ReportRow("Driver ID / Mobile", driverIdLine)
                    ReportRow("Vehicle", vehicleSummary)
                    ReportRow("Trip Type", breakdown.tripType.displayName)
                    ReportRow("Bill / Report ID", trip.tripNumber)
                    ReportRow("Date & Time", dateDisplay)
                    ReportRow("Distance & Duration", "${String.format(Locale.US, "%.2f", breakdown.distanceKm)} km • ${trip.durationSeconds / 60} min")
                    ReportRow("Customer Bill Amount", SangamTariffCalculator.formatRupees(breakdown.customerBillTotal))

                    when (val comm = breakdown.commissionResult) {
                        is SangamTariffCalculator.CommissionResult.Confirmed -> {
                            ReportRow("Approved CC Rule", comm.ruleLabel)
                            ReportRow("Approved CC Amount", SangamTariffCalculator.formatRupees(comm.amount))
                        }
                        is SangamTariffCalculator.CommissionResult.NeedsConfirmation -> {
                            ReportRow("Approved CC Rule", comm.reason)
                            ReportRow("Approved CC Amount", "Needs Confirmation")
                        }
                        is SangamTariffCalculator.CommissionResult.NotSpecified -> {
                            ReportRow("Approved CC Rule", comm.reason)
                            ReportRow("Approved CC Amount", "Not Specified")
                        }
                    }

                    val balLabel = when (val bal = breakdown.driverBalanceStatus) {
                        is SangamTariffCalculator.DriverBalanceStatus.Confirmed -> SangamTariffCalculator.formatRupees(bal.amount)
                        is SangamTariffCalculator.DriverBalanceStatus.RangePendingConfirmation -> "Pending Confirmation"
                    }
                    ReportRow("Driver Net Balance", balLabel)
                    ReportRow("GET TAXI GPay", SangamTariffConfig.VENDOR_GPAY_NUMBER)

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val enrichedTrip = trip.copy(
                                    driverName = resolvedDriverName,
                                    driverMobile = resolvedDriverMobile,
                                    vehicleNumber = resolvedVehicleReg
                                )
                                val file = PdfGenerator.generateInternalCcReportPdf(context, enrichedTrip, breakdown, cleanDriverId)
                                ShareUtils.sharePdf(context, file, "GET TAXI Vendor Share Report ${trip.tripNumber}")
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, SettleYellow),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_internal_cc_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = SettleYellow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VENDOR PDF", color = SettleYellow, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val enrichedTrip = trip.copy(
                                    driverName = resolvedDriverName,
                                    driverMobile = resolvedDriverMobile,
                                    vehicleNumber = resolvedVehicleReg
                                )
                                val text = ShareUtils.buildInternalCcReportText(enrichedTrip, breakdown, cleanDriverId)
                                ShareUtils.shareText(context, text, "GET TAXI Vendor Share Report")
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, SettleBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_internal_cc_report_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = SettleWhite, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHARE REPORT", color = SettleWhite, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, color = SettleMuted, fontSize = 12.sp, modifier = Modifier.weight(0.9f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = SettleWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun StatusTag(text: String, bg: Color, fg: Color) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun settleFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SettleYellow,
    unfocusedBorderColor = SettleBorder,
    focusedLabelColor = SettleYellow,
    unfocusedLabelColor = SettleMuted,
    focusedTextColor = SettleWhite,
    unfocusedTextColor = SettleWhite,
    cursorColor = SettleYellow
)
