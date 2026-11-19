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
import com.example.ui.theme.LocalGetTaxiColors
import com.example.util.PdfGenerator
import com.example.util.SangamTariffCalculator
import com.example.util.SangamTariffConfig
import com.example.util.ShareUtils
import com.example.util.UpiQrCodeGenerator
import com.example.viewmodel.MeterViewModel
import java.io.File
import java.util.Locale

private val SettleAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSettlementScreen(
    viewModel: MeterViewModel,
    onDoneClearAndReturnHome: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalGetTaxiColors.current
    val settlement by viewModel.currentSettlement.collectAsStateWithLifecycle()
    val driverProfile by viewModel.driverProfile.collectAsStateWithLifecycle()

    val isDirectSession = settlement?.breakdown?.isDirectMeter == true ||
        settlement?.tripEntity?.meterMode.equals(SangamTariffConfig.METER_MODE_DIRECT, ignoreCase = true)

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
                        text = if (isDirectSession) "DIRECT METER INVOICE" else "TRIP SETTLEMENT",
                        color = colors.textPrimary,
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
                            tint = colors.primaryAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background,
        bottomBar = {
            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border)
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
                            containerColor = colors.primaryAccent,
                            contentColor = colors.onPrimaryAccent
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
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            return@Scaffold
        }

        val trip = active.tripEntity
        val breakdown = active.breakdown
        val isDirectMeter = breakdown.isDirectMeter ||
            trip.meterMode.equals(SangamTariffConfig.METER_MODE_DIRECT, ignoreCase = true)
        val directCatLabel = (breakdown.directCategory
            ?: SangamTariffConfig.DirectMeterCategory.fromId(trip.directCategoryId)).displayName
        val categoryLabel = if (isDirectMeter) {
            "DIRECT METER"
        } else if (breakdown.isInnovaCrysta) {
            "INNOVA CRYSTA"
        } else {
            breakdown.vehicleCategory.displayName
        }
        val tripModeBadgeLabel = if (isDirectMeter) directCatLabel else breakdown.tripType.displayName

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
            // Trip History Persistence Status Banner
            if (active.saveError != null && active.persistedTripId == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (colors.isDark) Color(0xFF3F1212) else Color(0xFFFEF2F2)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, colors.redDanger),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_save_error_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = colors.redDanger, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TRIP SAVE FAILED — DATA PRESERVED",
                                color = colors.redDanger,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = active.saveError,
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Button(
                            onClick = { viewModel.retrySaveCurrentSettlement() },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("retry_save_trip_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RETRY SAVE TO TRIP HISTORY", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            } else if (active.persistedTripId != null) {
                Surface(
                    color = colors.greenSuccess.copy(alpha = if (colors.isDark) 0.14f else 0.10f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.greenSuccess.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_saved_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.greenSuccess, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAVED TO TRIP HISTORY • LOCAL & SYNC READY",
                            color = colors.greenSuccess,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }

            if (isDirectMeter) {
                Surface(
                    color = colors.yellowContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.yellowHighlight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_meter_invoice_only_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = colors.yellowText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIRECT METER • CUSTOMER INVOICE ONLY (NO VENDOR CC)",
                            color = colors.yellowText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // =================================================================
            // SECTION 1: CUSTOMER INVOICE (NO CC, NO COMMISSION, NO DRIVER BALANCE)
            // =================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.meterDisplayBg),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, colors.primaryAccent),
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
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("amount_to_collect_label")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = SangamTariffCalculator.formatRupees(breakdown.customerBillTotal),
                        color = if (colors.isDark) colors.yellowHighlight else colors.primaryAccent,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.testTag("settlement_total_amount")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusTag(text = categoryLabel, bg = colors.surfaceElevated, fg = colors.textPrimary)
                        StatusTag(text = tripModeBadgeLabel, bg = colors.yellowContainer, fg = colors.yellowText)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Bill ${trip.tripNumber} • $dateDisplay",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", breakdown.distanceKm)} km • ${trip.durationSeconds / 60} min",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!isDirectMeter && (breakdown.hasUnresolvedAmbiguity || breakdown.warnings.isNotEmpty())) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (colors.isDark) Color(0xFF2B2110) else Color(0xFFFFFBEB)),
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
                                text = "TARIFF NOTICE",
                                color = SettleAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                        }
                        breakdown.ambiguities.forEach { amb ->
                            Text(
                                text = "• ${amb.title}: ${amb.description}",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            if (amb.candidateOptions.isNotEmpty()) {
                                Text(
                                    text = "  Options: ${amb.candidateOptions.joinToString(" OR ")}",
                                    color = colors.yellowText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        breakdown.warnings.forEach { w ->
                            Text(text = "• $w", color = colors.textPrimary, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.border),
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
                                Text(
                                    text = item.label,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.detail,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = SangamTariffCalculator.formatRupees(item.amount),
                                color = if (item.isPassThrough) colors.textSecondary else colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    HorizontalDivider(color = colors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Final Payable", color = colors.primaryAccent, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(
                            text = SangamTariffCalculator.formatRupees(breakdown.customerBillTotal),
                            color = colors.primaryAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // CUSTOMER UPI QR CODE PAYMENT CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.border),
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
                        color = colors.yellowText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    if (qrUri != null) {
                        val modelData: Any = if (qrUri.startsWith("/")) File(qrUri) else qrUri
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, colors.yellowHighlight)
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
                            Text(text = "UPI ID: $upiId", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (generatedUpiBitmap != null) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, colors.yellowHighlight)
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
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Add your UPI ID or upload a static Payment QR image in Driver Profile to display a customer scan-and-pay QR code here.",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // CUSTOMER INFO & SHARE / BILL PDF (CUSTOMER-ONLY RECEIPT)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.border),
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
                            color = colors.yellowText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        TextButton(onClick = { showCustomerEditor = !showCustomerEditor }) {
                            Text(
                                text = if (showCustomerEditor) "Hide Details" else "Add Customer Info",
                                color = colors.primaryAccent,
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
                            border = BorderStroke(1.dp, colors.primaryAccent),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_bill_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CUSTOMER BILL PDF", color = colors.primaryAccent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val text = ShareUtils.buildSangamReceiptText(trip, breakdown, includeSettlementNote = false)
                                ShareUtils.shareText(context, text, "GET TAXI Customer Invoice")
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_bill_text_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHARE BILL", color = colors.textPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }

            // =================================================================
            // SECTION 2 & 3: OTP METER ONLY — DRIVER CC SETTLEMENT & VENDOR REPORT
            // Strictly hidden for Direct Meter trips!
            // =================================================================
            if (!isDirectMeter) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, colors.border),
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
                            color = colors.yellowText,
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
                                        Text("Approved CC Amount", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(comm.ruleLabel, color = colors.textSecondary, fontSize = 11.sp)
                                    }
                                    Text(
                                        text = SangamTariffCalculator.formatRupees(comm.amount),
                                        color = colors.primaryAccent,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.testTag("confirmed_commission_amount")
                                    )
                                }
                            }

                            is SangamTariffCalculator.CommissionResult.NeedsConfirmation -> {
                                StatusTag(text = "NEEDS CONFIRMATION", bg = colors.yellowContainer, fg = SettleAmber)
                                Text(text = comm.reason, color = colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                                if (comm.minPossibleAmount != null && comm.maxPossibleAmount != null) {
                                    Text(
                                        text = "Rate Range: ${SangamTariffCalculator.formatRupees(comm.minPossibleAmount)} – ${SangamTariffCalculator.formatRupees(comm.maxPossibleAmount)}",
                                        color = colors.yellowText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                comm.candidateLabels.forEach { opt ->
                                    Text(text = "• $opt", color = colors.textSecondary, fontSize = 11.sp)
                                }
                            }

                            is SangamTariffCalculator.CommissionResult.NotSpecified -> {
                                val tagLabel = if (breakdown.tripType == SangamTariffConfig.SangamTripType.DAY_RENTAL) {
                                    "VARIABLE / MANUAL CC"
                                } else {
                                    "NOT SPECIFIED IN CHART"
                                }
                                StatusTag(text = tagLabel, bg = colors.yellowContainer, fg = SettleAmber)
                                Text(text = comm.reason, color = colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }

                        if (breakdown.tripType == SangamTariffConfig.SangamTripType.DAY_RENTAL) {
                            var manualCcInput by remember(trip.tripNumber, trip.ccAmount, trip.ccStatus) {
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
                                    text = "DAY RENTAL CC (VARIABLE / MANUAL)",
                                    color = colors.yellowText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "No fixed percentage applies to Day Rental. Enter the applicable CC amount for this trip (Driver Settlement only; Customer Bill stays unchanged).",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = manualCcInput,
                                        onValueChange = { manualCcInput = it },
                                        label = { Text("CC Amount (₹)") },
                                        placeholder = { Text("e.g. 250") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = settleFieldColors(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("day_rental_manual_cc_input")
                                    )
                                    Button(
                                        onClick = {
                                            val parsed = manualCcInput.trim().toDoubleOrNull()
                                            if (parsed != null && parsed >= 0.0) {
                                                viewModel.updateManualDayRentalCc(trip.id, parsed)
                                                Toast.makeText(
                                                    context,
                                                    "Manual Day Rental CC saved (Customer Bill unchanged)",
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
                                            .testTag("save_day_rental_cc_button")
                                    ) {
                                        Text("SAVE CC", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = colors.border)

                        when (val bal = breakdown.driverBalanceStatus) {
                            is SangamTariffCalculator.DriverBalanceStatus.Confirmed -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Driver Balance (After CC)", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = SangamTariffCalculator.formatRupees(bal.amount),
                                        color = colors.greenSuccess,
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
                                        Text("Driver Balance (After CC)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        val rangeStr = if (bal.minDriverBalance != null && bal.maxDriverBalance != null) {
                                            "Est. ${SangamTariffCalculator.formatRupees(bal.minDriverBalance)} – ${SangamTariffCalculator.formatRupees(bal.maxDriverBalance)}"
                                        } else {
                                            "Pending CC confirmation"
                                        }
                                        Text(rangeStr, color = SettleAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    StatusTag(text = "PENDING", bg = colors.surfaceElevated, fg = SettleAmber)
                                }
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.meterDisplayBg),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, colors.primaryAccent),
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
                                bg = colors.surfaceElevated,
                                fg = if (ccTransferConfirmed) colors.greenSuccess else SettleAmber
                            )
                        }

                        Text(
                            text = "Transfer Driver CC via GPay",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceElevated, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("GET TAXI GPAY NUMBER", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = SangamTariffConfig.VENDOR_GPAY_NUMBER,
                                    color = if (colors.isDark) colors.yellowHighlight else colors.primaryAccent,
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
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
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
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
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
                                "Send ${SangamTariffCalculator.formatRupees(comm.amount)} to ${SangamTariffConfig.VENDOR_GPAY_NUMBER} via GPay and confirm manually. Separate from customer billing."
                            else ->
                                "Verify approved CC amount before transferring to GPay ${SangamTariffConfig.VENDOR_GPAY_NUMBER} and confirm manually."
                        }
                        Text(text = commInstruction, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }

                // =================================================================
                // SECTION 3: INTERNAL CC REPORT (READ-ONLY & SHAREABLE)
                // =================================================================
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, colors.border),
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
                            color = colors.yellowText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        HorizontalDivider(color = colors.border)
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
                                border = BorderStroke(1.dp, colors.primaryAccent),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_internal_cc_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("VENDOR PDF", color = colors.primaryAccent, fontWeight = FontWeight.Black, fontSize = 11.sp)
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
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_internal_cc_report_button")
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
private fun ReportRow(label: String, value: String) {
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
private fun settleFieldColors(): TextFieldColors {
    val colors = LocalGetTaxiColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primaryAccent,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.primaryAccent,
        unfocusedLabelColor = colors.textSecondary,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.primaryAccent
    )
}
