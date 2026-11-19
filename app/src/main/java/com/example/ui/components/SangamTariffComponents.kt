package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ExtraChargeItem
import com.example.util.SangamTariffCalculator
import com.example.util.SangamTariffConfig
import com.example.util.SangamTariffConfig.SangamTripType
import com.example.util.SangamTariffConfig.VehicleCategory
import java.util.Locale

private val MeterBlack = Color(0xFF090A0C)
private val MeterCharcoal = Color(0xFF14171C)
private val MeterCharcoalCard = Color(0xFF1E222A)
private val MeterBorder = Color(0xFF2D333E)
private val TaxiYellow = Color(0xFFFFC107)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF9CA3AF)
private val WarningAmber = Color(0xFFF59E0B)

@Composable
fun VehicleCategorySelectorCard(
    selectedCategory: VehicleCategory,
    isTripActive: Boolean,
    onSelectCategory: (VehicleCategory) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MeterCharcoalCard),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MeterBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("vehicle_category_selector_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VEHICLE CATEGORY",
                    color = TaxiYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                if (isTripActive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked during active trip",
                            tint = WarningAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LOCKED",
                            color = WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VehicleCategory.entries.forEach { category ->
                    val selected = category == selectedCategory
                    Surface(
                        color = if (selected) TaxiYellow else MeterBlack,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (selected) TaxiYellow else MeterBorder),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !isTripActive) { onSelectCategory(category) }
                            .testTag("vehicle_cat_${category.id.lowercase(Locale.US)}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = category.shortLabel,
                                color = if (selected) MeterBlack else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${selectedCategory.displayName}: ${selectedCategory.exampleModels}",
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SangamTripTypeSelectorCard(
    selectedCategory: VehicleCategory,
    selectedTripType: SangamTripType,
    isInnovaCrysta: Boolean,
    isTripActive: Boolean,
    onSelectTripType: (SangamTripType) -> Unit,
    onToggleInnovaCrysta: (Boolean) -> Unit
) {
    val supportedTypes = selectedCategory.supportedTripTypes()

    Card(
        colors = CardDefaults.cardColors(containerColor = MeterCharcoalCard),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MeterBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("sangam_trip_type_selector_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRIP TARIFF MODE",
                    color = TaxiYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                supportedTypes.forEach { tripType ->
                    val selected = tripType == selectedTripType
                    Surface(
                        color = if (selected) TaxiYellow else MeterBlack,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (selected) TaxiYellow else MeterBorder),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !isTripActive) { onSelectTripType(tripType) }
                            .testTag("trip_type_${tripType.id.lowercase(Locale.US)}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = tripType.displayName,
                                color = if (selected) MeterBlack else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (selectedCategory == VehicleCategory.INNOVA && selectedTripType == SangamTripType.DAY_RENTAL) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MeterBlack,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isInnovaCrysta) TaxiYellow else MeterBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Innova Crysta Day Rental Rate",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Standard Innova ₹2,500+₹15/km vs Crysta ₹3,000+₹16/km",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isInnovaCrysta,
                            enabled = !isTripActive,
                            onCheckedChange = onToggleInnovaCrysta,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MeterBlack,
                                checkedTrackColor = TaxiYellow,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = MeterCharcoalCard
                            ),
                            modifier = Modifier.testTag("toggle_innova_crysta")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SangamModifiersAndRatePreviewCard(
    category: VehicleCategory,
    tripType: SangamTripType,
    isInnovaCrysta: Boolean,
    isOutsideCity: Boolean,
    isNightTrip: Boolean,
    packageHours: Int,
    over12Hours: Boolean,
    onToggleOutsideCity: (Boolean) -> Unit,
    onToggleNightTrip: (Boolean) -> Unit,
    onSelectPackageHours: (Int) -> Unit,
    onToggleOver12Hours: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MeterCharcoalCard),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MeterBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("sangam_rate_preview_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "GET TAXI ACTIVE TARIFF & MODIFIERS",
                color = TaxiYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            val rateSummary = remember(category, tripType, isInnovaCrysta, packageHours) {
                buildRateSummaryText(category, tripType, isInnovaCrysta, packageHours)
            }
            Text(
                text = rateSummary,
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp
            )

            when (tripType) {
                SangamTripType.LOCAL_NORMAL, SangamTripType.LOCAL_MINIMUM_BILL -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModifierToggleChip(
                            label = "Outside City +₹80",
                            checked = isOutsideCity,
                            onToggle = { onToggleOutsideCity(!isOutsideCity) },
                            testTag = "chip_outside_city",
                            modifier = Modifier.weight(1f)
                        )
                        ModifierToggleChip(
                            label = "Night 11–6 +₹50",
                            checked = isNightTrip,
                            onToggle = { onToggleNightTrip(!isNightTrip) },
                            testTag = "chip_night_charge",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                SangamTripType.HOURLY_RENTAL -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    val pkgAmt = SangamTariffConfig.hourlyPackageTableFor(category)?.get(packageHours)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Package: ${packageHours}h / ${packageHours * 10} km",
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Chart Fee: ${pkgAmt?.let { SangamTariffCalculator.formatRupees(it) } ?: "N/A"}",
                                color = TaxiYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { if (packageHours > 1) onSelectPackageHours(packageHours - 1) },
                                enabled = packageHours > 1,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MeterBorder),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("hourly_hours_minus")
                            ) {
                                Text("−", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                            Text(
                                text = "${packageHours}h",
                                color = TaxiYellow,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            OutlinedButton(
                                onClick = { if (packageHours < 12) onSelectPackageHours(packageHours + 1) },
                                enabled = packageHours < 12,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, TaxiYellow),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("hourly_hours_plus")
                            ) {
                                Text("+", color = TaxiYellow, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                SangamTripType.OUTSTATION_ONE_WAY, SangamTripType.OUTSTATION_ROUND_TRIP -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    ModifierToggleChip(
                        label = "Driver Allowance (>12 Hours) +₹400",
                        checked = over12Hours,
                        onToggle = { onToggleOver12Hours(!over12Hours) },
                        testTag = "chip_over_12_hours",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SangamTripType.DAY_RENTAL -> Unit
            }
        }
    }
}

@Composable
private fun ModifierToggleChip(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (checked) TaxiYellow else MeterBlack,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (checked) TaxiYellow else MeterBorder),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (checked) MeterBlack else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (checked) MeterBlack else TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildRateSummaryText(
    category: VehicleCategory,
    tripType: SangamTripType,
    isInnovaCrysta: Boolean,
    packageHours: Int
): String {
    val localWait = SangamTariffConfig.localWaitingRatePerMin.toInt()
    return when (tripType) {
        SangamTripType.LOCAL_NORMAL -> {
            val rate = SangamTariffConfig.localNormalRates[category]
            if (rate != null) {
                "Local Normal: ₹${rate.initialCharge.toInt()} initial + ₹${rate.perKmRate.toInt()}/km • Wait ₹$localWait/min"
            } else "Not applicable for ${category.displayName}"
        }
        SangamTripType.LOCAL_MINIMUM_BILL -> {
            when (category) {
                VehicleCategory.MINI, VehicleCategory.SEDAN ->
                    "Min Bill: 1–5 KM ₹200 | 5–6 KM ₹250 | >6 KM ₹80 + ₹28/km • Wait ₹$localWait/min"
                VehicleCategory.SUV ->
                    "Min Bill: 1–7 KM ₹450 | >7 KM ₹200 + ₹35/km • Wait ₹$localWait/min"
                VehicleCategory.SUV_PLUS ->
                    "Min Bill: 1–10 KM ₹700 | >10 KM ₹250 + ₹45/km • Wait ₹$localWait/min"
                VehicleCategory.INNOVA ->
                    "Min Bill: 1–10 KM ₹800 | >10 KM ₹250 + ₹50/km • Wait ₹$localWait/min"
            }
        }
        SangamTripType.HOURLY_RENTAL -> {
            val hrs = packageHours.coerceIn(1, 12)
            val amt = hrs * SangamTariffConfig.hourlyRatePerChargedHour.toInt()
            "Hourly ${hrs}h (${hrs * 10} km): ₹$amt • Extra ₹20/km • 5m grace • ₹0 wait • ₹50/hr CC"
        }
        SangamTripType.OUTSTATION_ONE_WAY -> {
            val r = SangamTariffConfig.outstationRates[category]
            if (r != null) {
                "One-Way: ₹${r.oneWayInitialCharge.toInt()} base + ₹${r.oneWayPerKm.toInt()}/km • >12h ₹400 • Manual wait • 10% CC"
            } else "Not applicable"
        }
        SangamTripType.OUTSTATION_ROUND_TRIP -> {
            val r = SangamTariffConfig.outstationRates[category]
            if (r != null) {
                "Round-Trip: ₹${r.roundTripInitialCharge.toInt()} base + ₹${r.roundTripPerKm.toInt()}/km • >12h ₹400 • Manual wait • 10% CC"
            } else "Not applicable"
        }
        SangamTripType.DAY_RENTAL -> {
            val r = if (category == VehicleCategory.INNOVA && isInnovaCrysta) {
                SangamTariffConfig.innovaCrystaDayRentalRate
            } else {
                SangamTariffConfig.dayRentalRates[category]
            }
            if (r != null) {
                val extra12 = if (r.after12HoursExtra.toInt() > 0) " • >12h +₹${r.after12HoursExtra.toInt()}" else ""
                "Day Rental: ₹${r.baseAmount.toInt()} + ₹${r.perKmRate.toInt()}/km$extra12 • CC: Variable (Manual)"
            } else "Not available"
        }
    }
}

@Composable
fun QuickFareCalculatorDialog(
    initialCategory: VehicleCategory,
    initialTripType: SangamTripType,
    initialInnovaCrysta: Boolean,
    initialOutsideCity: Boolean,
    initialNightTrip: Boolean,
    initialPackageHours: Int,
    initialOver12Hours: Boolean,
    onDismiss: () -> Unit,
    onCalculateAndSettle: (
        input: SangamTariffCalculator.TripCalculationInput,
        pickup: String,
        drop: String,
        durationMin: Long
    ) -> Unit
) {
    var distanceInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("") }
    var localWaitMinInput by remember { mutableStateOf("") }
    var outstationWaitHoursInput by remember { mutableStateOf("") }
    var outstationWaitRateInput by remember { mutableStateOf("") }
    var hoursInput by remember { mutableIntStateOf(initialPackageHours.coerceIn(1, 12)) }
    var outsideCity by remember { mutableStateOf(initialOutsideCity) }
    var nightTrip by remember { mutableStateOf(initialNightTrip) }
    var over12Hrs by remember { mutableStateOf(initialOver12Hours) }
    var tollInput by remember { mutableStateOf("") }
    var permitInput by remember { mutableStateOf("") }
    var parkingInput by remember { mutableStateOf("") }
    var customExtrasInput by remember { mutableStateOf("") }
    var pickupInput by remember { mutableStateOf("") }
    var dropInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MeterCharcoal),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, TaxiYellow),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .testTag("quick_fare_calculator_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PARKED FARE CALCULATOR",
                            color = TaxiYellow,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        val catText = if (initialCategory == VehicleCategory.INNOVA && initialInnovaCrysta && initialTripType == SangamTripType.DAY_RENTAL) {
                            "INNOVA CRYSTA"
                        } else initialCategory.displayName
                        Text(
                            text = "$catText • ${initialTripType.displayName}",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = distanceInput,
                    onValueChange = { distanceInput = it; errorMsg = null },
                    label = { Text("Distance (KM)") },
                    placeholder = { Text("e.g. 12.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = darkFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("quick_calc_distance_input")
                )

                if (initialTripType == SangamTripType.HOURLY_RENTAL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Package Hours: ${hoursInput}h (${hoursInput * 10} km • ₹${hoursInput * 350})",
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { if (hoursInput > 1) hoursInput-- },
                                modifier = Modifier.size(48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("−", color = TextWhite) }
                            OutlinedButton(
                                onClick = { if (hoursInput < 12) hoursInput++ },
                                modifier = Modifier.size(48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("+", color = TaxiYellow) }
                        }
                    }
                    Text(
                        text = "Extra KM: ₹20/km • 5-min grace • Waiting charge: ₹0",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                } else {
                    OutlinedTextField(
                        value = durationInput,
                        onValueChange = { durationInput = it },
                        label = { Text("Duration (Minutes, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = darkFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (initialTripType == SangamTripType.LOCAL_NORMAL || initialTripType == SangamTripType.LOCAL_MINIMUM_BILL) {
                    OutlinedTextField(
                        value = localWaitMinInput,
                        onValueChange = { localWaitMinInput = it },
                        label = { Text("Local Waiting (Minutes, ₹2/min, optional)") },
                        placeholder = { Text("e.g. 10") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = darkFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("quick_calc_local_wait_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ModifierToggleChip(
                            label = "Outside City +₹80",
                            checked = outsideCity,
                            onToggle = { outsideCity = !outsideCity },
                            testTag = "quick_calc_outside_city",
                            modifier = Modifier.weight(1f)
                        )
                        ModifierToggleChip(
                            label = "Night +₹50",
                            checked = nightTrip,
                            onToggle = { nightTrip = !nightTrip },
                            testTag = "quick_calc_night",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (initialTripType == SangamTripType.OUTSTATION_ONE_WAY ||
                    initialTripType == SangamTripType.OUTSTATION_ROUND_TRIP
                ) {
                    ModifierToggleChip(
                        label = ">12 Hours Allowance +₹400",
                        checked = over12Hrs,
                        onToggle = { over12Hrs = !over12Hrs },
                        testTag = "quick_calc_over_12",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Manual Outstation Waiting (optional — no auto-waiting)",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = outstationWaitHoursInput,
                            onValueChange = { outstationWaitHoursInput = it },
                            label = { Text("Wait Hours") },
                            placeholder = { Text("e.g. 2") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = darkFieldColors(),
                            modifier = Modifier.weight(1f).testTag("quick_calc_outstation_wait_hours")
                        )
                        OutlinedTextField(
                            value = outstationWaitRateInput,
                            onValueChange = { outstationWaitRateInput = it },
                            label = { Text("Rate / Hr (₹)") },
                            placeholder = { Text("Manual") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = darkFieldColors(),
                            modifier = Modifier.weight(1f).testTag("quick_calc_outstation_wait_rate")
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = tollInput,
                        onValueChange = { tollInput = it },
                        label = { Text("Toll (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = darkFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = permitInput,
                        onValueChange = { permitInput = it },
                        label = { Text("Permit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = darkFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = parkingInput,
                        onValueChange = { parkingInput = it },
                        label = { Text("Park (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = darkFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = customExtrasInput,
                    onValueChange = { customExtrasInput = it },
                    label = { Text("Additional Approved Charges (₹, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = darkFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("quick_calc_custom_extras_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = pickupInput,
                        onValueChange = { pickupInput = it },
                        label = { Text("From") },
                        colors = darkFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dropInput,
                        onValueChange = { dropInput = it },
                        label = { Text("To") },
                        colors = darkFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val dist = distanceInput.trim().toDoubleOrNull()
                        if (dist == null || dist < 0.0) {
                            errorMsg = "Enter valid distance in KM"
                            return@Button
                        }
                        val localWaitMins = if (
                            initialTripType == SangamTripType.LOCAL_NORMAL ||
                            initialTripType == SangamTripType.LOCAL_MINIMUM_BILL
                        ) {
                            localWaitMinInput.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        } else {
                            0.0
                        }
                        val outWaitHrs = outstationWaitHoursInput.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val outWaitRate = outstationWaitRateInput.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val customExtraVal = customExtrasInput.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val itemizedExtras = mutableListOf<ExtraChargeItem>()
                        if ((initialTripType == SangamTripType.OUTSTATION_ONE_WAY ||
                                initialTripType == SangamTripType.OUTSTATION_ROUND_TRIP) &&
                            outWaitHrs > 0.0 && outWaitRate > 0.0
                        ) {
                            val waitTotal = outWaitHrs * outWaitRate
                            itemizedExtras.add(
                                ExtraChargeItem(
                                    label = "Additional Waiting (${String.format(Locale.US, "%.1f", outWaitHrs)} hr @ ₹${String.format(Locale.US, "%.0f", outWaitRate)}/hr)",
                                    amount = waitTotal
                                )
                            )
                        }
                        if (customExtraVal > 0.0 && itemizedExtras.isNotEmpty()) {
                            itemizedExtras.add(
                                ExtraChargeItem(
                                    label = "Additional Charges",
                                    amount = customExtraVal
                                )
                            )
                        }

                        val input = SangamTariffCalculator.TripCalculationInput(
                            vehicleCategory = initialCategory,
                            tripType = initialTripType,
                            distanceKm = dist,
                            packageHours = hoursInput,
                            waitingMinutes = localWaitMins,
                            isInnovaCrysta = initialInnovaCrysta && initialCategory == VehicleCategory.INNOVA,
                            isOutsideCity = outsideCity,
                            isNightTrip = nightTrip,
                            over12Hours = over12Hrs,
                            tollCharge = tollInput.trim().toDoubleOrNull() ?: 0.0,
                            permitCharge = permitInput.trim().toDoubleOrNull() ?: 0.0,
                            parkingCharge = parkingInput.trim().toDoubleOrNull() ?: 0.0,
                            customExtrasTotal = if (itemizedExtras.isEmpty()) customExtraVal else 0.0,
                            extraChargeItems = itemizedExtras
                        )
                        val durMin = durationInput.trim().toLongOrNull() ?: 0L
                        onCalculateAndSettle(input, pickupInput.trim(), dropInput.trim(), durMin)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = MeterBlack),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("quick_calc_submit_button")
                ) {
                    Text("CALCULATE & SETTLE", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun SangamTariffChartDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MeterCharcoal),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, TaxiYellow),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .testTag("sangam_tariff_chart_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GET TAXI TARIFF DETAILS",
                        color = TaxiYellow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                ReferenceBlock(
                    title = "1. Local Normal & Minimum Bill",
                    body = "• MINI / SEDAN: ₹80 + ₹28/km | Min: 1–5km ₹200, 5–6km ₹250, >6km ₹80+₹28/km\n" +
                        "• SUV: ₹200 + ₹35/km | Min: 1–7km ₹450, >7km ₹200+₹35/km\n" +
                        "• SUV+: ₹250 + ₹45/km | Min: 1–10km ₹700, >10km ₹250+₹45/km\n" +
                        "• INNOVA: ₹250 + ₹50/km | Min: 1–10km ₹800, >10km ₹250+₹50/km\n" +
                        "• Waiting Charge: ₹2/min (separate line item; excluded from Vendor CC)\n" +
                        "• Extras: Outside city ₹80 | Night (11PM–6AM) ₹50"
                )
                ReferenceBlock(
                    title = "2. Local Commission",
                    body = "1–5 KM: ₹30 | 6–10 KM: ₹60–₹75 | 10–25 KM: ₹80 | >25 KM: 10% | >₹800 bill: 10% (base tariff excl. waiting)"
                )
                ReferenceBlock(
                    title = "3. Hourly Rental (GET TAXI Uniform Tariff)",
                    body = "• ₹350 per billed hour across MINI, SEDAN, SUV, SUV+, INNOVA\n" +
                        "• 10 km free per billed hour • Extra Distance: ₹20/km\n" +
                        "• 5-minute grace period per block • Waiting Charges: ₹0\n" +
                        "• Driver CC: ₹50 per billed hour (no 10% commission)"
                )
                ReferenceBlock(
                    title = "4. Outstation & Day Rental",
                    body = "• Outstation One-Way: ₹300 base + ₹30/km (>12h +₹400, 10% CC, manual waiting only)\n" +
                        "• Outstation Round-Trip: ₹500 base + ₹15/km (>12h +₹400, 10% CC, manual waiting only)\n" +
                        "• Day Rental: MINI/SEDAN ₹2000+10/km | SUV ₹2300+13/km | SUV+ ₹2400+14/km | INNOVA ₹2500+15/km | CRYSTA ₹3000+16/km\n" +
                        "• Day Rental CC: Variable / Managed manually per trip (no fixed %)"
                )
                ReferenceBlock(
                    title = "5. Rules Requiring Confirmation / Manual Management",
                    body = "1) Exact 5.0 KM MINI/SEDAN min-bill overlap (₹200 vs ₹250)\n" +
                        "2) Local commission 6–10 KM range (₹60–₹75) & 5–6 / 10 KM boundaries\n" +
                        "3) >25 KM (10%) vs >₹800 bill (10%) alternative vs cumulative\n" +
                        "4) Outstation Additional Waiting (manual entry only) & Day Rental CC (variable/manual per trip)",
                    accentColor = WarningAmber
                )
            }
        }
    }
}

@Composable
private fun ReferenceBlock(
    title: String,
    body: String,
    accentColor: Color = TaxiYellow
) {
    Surface(
        color = MeterBlack,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MeterBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, color = TextWhite, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TaxiYellow,
    unfocusedBorderColor = MeterBorder,
    focusedLabelColor = TaxiYellow,
    unfocusedLabelColor = TextMuted,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    cursorColor = TaxiYellow
)
