package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LocalGetTaxiColors
import com.example.util.SangamTariffConfig
import com.example.viewmodel.SettingsViewModel
import java.util.Locale

/**
 * Independent Direct Meter Settings page.
 *
 * Completely decoupled from OTP Meter tariffs. Any edits here affect ONLY new Direct Meter trips
 * (active or past trips remain locked to their immutable start-time tariff snapshot).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectMeterSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val colors = LocalGetTaxiColors.current
    val currentSnapshot by viewModel.directMeterTariffSnapshot.collectAsStateWithLifecycle()

    var localBaseText by remember(currentSnapshot) { mutableStateOf(formatRateInput(currentSnapshot.localBaseFare)) }
    var localPerKmText by remember(currentSnapshot) { mutableStateOf(formatRateInput(currentSnapshot.localPerKmRate)) }
    var hourlyBaseText by remember(currentSnapshot) { mutableStateOf(formatRateInput(currentSnapshot.hourlyBaseFare)) }
    var hourlyIncludedKmText by remember(currentSnapshot) { mutableStateOf(formatRateInput(currentSnapshot.hourlyIncludedKm)) }
    var hourlyExtraKmText by remember(currentSnapshot) { mutableStateOf(formatRateInput(currentSnapshot.hourlyExtraKmRate)) }
    var outstationBaseText by remember(currentSnapshot) { mutableStateOf(formatRateInput(currentSnapshot.outstationBaseFare)) }
    var outstationPerKmText by remember(currentSnapshot) { mutableStateOf(formatRateInput(currentSnapshot.outstationPerKmRate)) }
    var waitingPerMinText by remember(currentSnapshot) { mutableStateOf(formatRateInput(currentSnapshot.waitingChargePerMin)) }
    var savedToast by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Direct Meter Settings",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Independent from OTP Meter tariffs • No CC",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("direct_settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Notice banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.yellowContainer)
                    .border(1.dp, colors.yellowHighlight.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Direct Meter Info",
                    tint = colors.yellowText,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Direct Start Meter Tariffs",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Used only when starting Direct Meter without OTP. Generates Customer Invoice only — no CC or vendor settlement.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Local Direct Meter Card
            DirectTariffSectionCard(
                title = "LOCAL DIRECT METER",
                subtitle = "Default: Base ₹100 • Extra ₹30 / KM"
            ) {
                DirectRateField(
                    label = "Local Base Fare (₹)",
                    value = localBaseText,
                    onValueChange = { localBaseText = it; savedToast = null },
                    testTag = "direct_local_base_input"
                )
                DirectRateField(
                    label = "Local Per KM Rate (₹ / km)",
                    value = localPerKmText,
                    onValueChange = { localPerKmText = it; savedToast = null },
                    testTag = "direct_local_km_input"
                )
            }

            // Hourly Rental Direct Meter Card
            DirectTariffSectionCard(
                title = "HOURLY RENTAL DIRECT METER",
                subtitle = "Default: Base ₹375/hr • 10 KM Included • Extra ₹20 / KM"
            ) {
                DirectRateField(
                    label = "Hourly Base Fare (₹ / hr)",
                    value = hourlyBaseText,
                    onValueChange = { hourlyBaseText = it; savedToast = null },
                    testTag = "direct_hourly_base_input"
                )
                DirectRateField(
                    label = "Included KM per Hour (km)",
                    value = hourlyIncludedKmText,
                    onValueChange = { hourlyIncludedKmText = it; savedToast = null },
                    testTag = "direct_hourly_included_km_input"
                )
                DirectRateField(
                    label = "Hourly Extra KM Rate (₹ / km)",
                    value = hourlyExtraKmText,
                    onValueChange = { hourlyExtraKmText = it; savedToast = null },
                    testTag = "direct_hourly_extra_km_input"
                )
            }

            // Outstation Direct Meter Card
            DirectTariffSectionCard(
                title = "OUTSTATION DIRECT METER",
                subtitle = "Default: Base ₹300 • Extra ₹30 / KM"
            ) {
                DirectRateField(
                    label = "Outstation Base Fare (₹)",
                    value = outstationBaseText,
                    onValueChange = { outstationBaseText = it; savedToast = null },
                    testTag = "direct_outstation_base_input"
                )
                DirectRateField(
                    label = "Outstation Per KM Rate (₹ / km)",
                    value = outstationPerKmText,
                    onValueChange = { outstationPerKmText = it; savedToast = null },
                    testTag = "direct_outstation_km_input"
                )
            }

            // Waiting Charge Card
            DirectTariffSectionCard(
                title = "DIRECT METER WAITING CHARGE",
                subtitle = "Default: ₹2 / minute when stationary (Local & Outstation)"
            ) {
                DirectRateField(
                    label = "Waiting Charge (₹ / min)",
                    value = waitingPerMinText,
                    onValueChange = { waitingPerMinText = it; savedToast = null },
                    testTag = "direct_waiting_rate_input"
                )
            }

            if (savedToast != null) {
                Text(
                    text = savedToast!!,
                    color = colors.greenSuccess,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.resetDirectMeterDefaults()
                        savedToast = "Direct Meter defaults restored."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("direct_settings_reset_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Defaults", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Defaults", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val updated = SangamTariffConfig.DirectMeterTariffSnapshot(
                            localBaseFare = localBaseText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: currentSnapshot.localBaseFare,
                            localPerKmRate = localPerKmText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: currentSnapshot.localPerKmRate,
                            hourlyBaseFare = hourlyBaseText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: currentSnapshot.hourlyBaseFare,
                            hourlyIncludedKm = hourlyIncludedKmText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: currentSnapshot.hourlyIncludedKm,
                            hourlyExtraKmRate = hourlyExtraKmText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: currentSnapshot.hourlyExtraKmRate,
                            outstationBaseFare = outstationBaseText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: currentSnapshot.outstationBaseFare,
                            outstationPerKmRate = outstationPerKmText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: currentSnapshot.outstationPerKmRate,
                            waitingChargePerMin = waitingPerMinText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: currentSnapshot.waitingChargePerMin
                        )
                        viewModel.saveDirectMeterSettings(updated)
                        savedToast = "Direct Meter tariffs saved!"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("direct_settings_save_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryAccent,
                        contentColor = colors.onPrimaryAccent
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Tariffs", fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DirectTariffSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalGetTaxiColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = colors.primaryAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp
        )
        Text(
            text = subtitle,
            color = colors.textSecondary,
            fontSize = 11.sp
        )
        content()
    }
}

@Composable
private fun DirectRateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String
) {
    val colors = LocalGetTaxiColors.current
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it == '.' }
            if (filtered.count { it == '.' } <= 1) {
                onValueChange(filtered)
            }
        },
        label = { Text(label, color = colors.textSecondary, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = colors.primaryAccent,
            unfocusedBorderColor = colors.border,
            focusedContainerColor = colors.surfaceElevated,
            unfocusedContainerColor = colors.surfaceElevated
        )
    )
}

private fun formatRateInput(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}
