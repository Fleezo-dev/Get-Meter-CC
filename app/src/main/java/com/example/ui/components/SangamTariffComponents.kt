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
import com.example.ui.theme.LocalGetTaxiColors
import java.util.Locale

private val WarningAmber = Color(0xFFF59E0B)

@Composable
fun VehicleCategorySelectorCard(
    selectedCategory: VehicleCategory,
    isTripActive: Boolean,
    onSelectCategory: (VehicleCategory) -> Unit,
    selectedTripType: SangamTripType = SangamTripType.LOCAL_NORMAL
) {
    val colors = LocalGetTaxiColors.current
    val selectableCategories = SangamTariffConfig.selectableVehiclesForTripType(selectedTripType)
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.border),
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
                    color = colors.primaryAccent,
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
                selectableCategories.forEach { category ->
                    val selected = category == selectedCategory
                    Surface(
                        color = if (selected) colors.primaryAccent else colors.surfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (selected) colors.primaryAccent else colors.border),
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
                                color = if (selected) colors.onPrimaryAccent else colors.textPrimary,
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
                color = colors.textSecondary,
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
    val colors = LocalGetTaxiColors.current
    val supportedTypes = selectedCategory.supportedTripTypes()

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.border),
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
                    color = colors.primaryAccent,
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
                        color = if (selected) colors.primaryAccent else colors.surfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (selected) colors.primaryAccent else colors.border),
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
                                color = if (selected) colors.onPrimaryAccent else colors.textPrimary,
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
                    color = colors.surfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isInnovaCrysta) colors.primaryAccent else colors.border),
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
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Standard Innova ₹2,500+₹15/km vs Crysta ₹3,000+₹16/km",
                                color = colors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isInnovaCrysta,
                            enabled = !isTripActive,
                            onCheckedChange = onToggleInnovaCrysta,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimaryAccent,
                                checkedTrackColor = colors.primaryAccent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface
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
    use100KmPackage: Boolean = false,
    over12Hours: Boolean,
    onToggleOutsideCity: (Boolean) -> Unit,
    onToggleNightTrip: (Boolean) -> Unit,
    onSelectPackageHours: (Int) -> Unit,
    onToggle100KmPackage: (Boolean) -> Unit = {},
    onToggleOver12Hours: (Boolean) -> Unit
) {
    val colors = LocalGetTaxiColors.current
    val hourlyTariff = SangamTariffConfig.hourlyTariffFor(category)
    val effectiveHours = packageHours.coerceIn(hourlyTariff.minHours, 12)

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("sangam_rate_preview_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "GET TAXI ACTIVE TARIFF & MODIFIERS",
                color = colors.primaryAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            val rateSummary = remember(category, tripType, isInnovaCrysta, effectiveHours, use100KmPackage) {
                buildRateSummaryText(category, tripType, isInnovaCrysta, effectiveHours, use100KmPackage)
            }
            Text(
                text = rateSummary,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp
            )

            when (tripType) {
                SangamTripType.LOCAL_NORMAL, SangamTripType.LOCAL_MINIMUM_BILL -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Additional Charges (Outside City Limits, Night Allowance 11 PM–6 AM) are manual entry only via Extra Charges (+). Auto CC applied on settlement.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                SangamTripType.HOURLY_RENTAL -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (hourlyTariff.package100KmFare != null) {
                        ModifierToggleChip(
                            label = "100 KM / 10h Package (${SangamTariffCalculator.formatRupees(hourlyTariff.package100KmFare)} • +₹${hourlyTariff.extraKmRate.toInt()}/km)",
                            checked = use100KmPackage,
                            onToggle = { onToggle100KmPackage(!use100KmPackage) },
                            testTag = "chip_hourly_100km_package",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!use100KmPackage) {
                        val pkgAmt = hourlyTariff.hourlyMeterRate.multiply(java.math.BigDecimal(effectiveHours))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Package: ${effectiveHours}h / ${effectiveHours * 10} km (Min ${hourlyTariff.minHours}h)",
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Meter Fee: ${SangamTariffCalculator.formatRupees(pkgAmt)} (₹${hourlyTariff.hourlyMeterRate.toInt()}/hr incl. CC)",
                                    color = colors.yellowText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { if (effectiveHours > hourlyTariff.minHours) onSelectPackageHours(effectiveHours - 1) },
                                    enabled = effectiveHours > hourlyTariff.minHours,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("hourly_hours_minus")
                                ) {
                                    Text("−", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                }
                                Text(
                                    text = "${effectiveHours}h",
                                    color = colors.primaryAccent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                                OutlinedButton(
                                    onClick = { if (effectiveHours < 12) onSelectPackageHours(effectiveHours + 1) },
                                    enabled = effectiveHours < 12,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, colors.primaryAccent),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("hourly_hours_plus")
                                ) {
                                    Text("+", color = colors.primaryAccent, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                }
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
    val colors = LocalGetTaxiColors.current
    Surface(
        color = if (checked) colors.primaryAccent else colors.surfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (checked) colors.primaryAccent else colors.border),
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
                tint = if (checked) colors.onPrimaryAccent else colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (checked) colors.onPrimaryAccent else colors.textPrimary,
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
    packageHours: Int,
    use100KmPackage: Boolean = false
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
                VehicleCategory.OMNI_EECO ->
                    "OMNI / Eeco uses Hourly Rental & 100 KM package tariffs"
                VehicleCategory.SUV ->
                    "Min Bill: 1–7 KM ₹450 | >7 KM ₹200 + ₹35/km • Wait ₹$localWait/min"
                VehicleCategory.SUV_PLUS ->
                    "Min Bill: 1–10 KM ₹700 | >10 KM ₹250 + ₹45/km • Wait ₹$localWait/min"
                VehicleCategory.INNOVA ->
                    "Min Bill: 1–10 KM ₹800 | >10 KM ₹250 + ₹50/km • Wait ₹$localWait/min"
            }
        }
        SangamTripType.HOURLY_RENTAL -> {
            val ht = SangamTariffConfig.hourlyTariffFor(category)
            if (use100KmPackage && ht.package100KmFare != null) {
                "100 KM / 10h Duty Package: ₹${ht.package100KmFare.toInt()} • Extra ₹${ht.extraKmRate.toInt()}/km • 5m grace • ₹0 wait"
            } else {
                val hrs = packageHours.coerceIn(ht.minHours, 12)
                val amt = hrs * ht.hourlyMeterRate.toInt()
                "Hourly ${hrs}h (${hrs * 10} km, min ${ht.minHours}h): ₹$amt (₹${ht.hourlyMeterRate.toInt()}/hr incl. ₹${ht.commissionPerHour.toInt()} CC) • Extra ₹${ht.extraKmRate.toInt()}/km • 5m grace • ₹0 wait"
            }
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
    initialUse100KmPackage: Boolean = false,
    initialOver12Hours: Boolean,
    onDismiss: () -> Unit,
    onCalculateAndSettle: (
        input: SangamTariffCalculator.TripCalculationInput,
        pickup: String,
        drop: String,
        durationMin: Long
    ) -> Unit
) {
    val colors = LocalGetTaxiColors.current
    val hourlyTariff = SangamTariffConfig.hourlyTariffFor(initialCategory)

    var distanceInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("") }
    var localWaitMinInput by remember { mutableStateOf("") }
    var outstationWaitHoursInput by remember { mutableStateOf("") }
    var outstationWaitRateInput by remember { mutableStateOf("") }
    var hoursInput by remember { mutableIntStateOf(initialPackageHours.coerceIn(hourlyTariff.minHours, 12)) }
    var use100Km by remember { mutableStateOf(initialUse100KmPackage && hourlyTariff.package100KmFare != null) }
    var outsideCityAmountInput by remember { mutableStateOf("") }
    var nightAllowanceAmountInput by remember { mutableStateOf("") }
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
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colors.primaryAccent),
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
                            color = colors.primaryAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        val catText = if (initialCategory == VehicleCategory.INNOVA && initialInnovaCrysta && initialTripType == SangamTripType.DAY_RENTAL) {
                            "INNOVA CRYSTA"
                        } else initialCategory.displayName
                        Text(
                            text = "$catText • ${initialTripType.displayName}",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = colors.redDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = distanceInput,
                    onValueChange = { distanceInput = it; errorMsg = null },
                    label = { Text("Distance (KM)") },
                    placeholder = { Text("e.g. 12.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = themedFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("quick_calc_distance_input")
                )

                if (initialTripType == SangamTripType.HOURLY_RENTAL) {
                    if (hourlyTariff.package100KmFare != null) {
                        ModifierToggleChip(
                            label = "100 KM / 10h Package (₹${hourlyTariff.package100KmFare.toInt()})",
                            checked = use100Km,
                            onToggle = { use100Km = !use100Km },
                            testTag = "quick_calc_100km_package",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (!use100Km) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hours: ${hoursInput}h (${hoursInput * 10} km • ₹${hoursInput * hourlyTariff.hourlyMeterRate.toInt()})",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { if (hoursInput > hourlyTariff.minHours) hoursInput-- },
                                    modifier = Modifier.size(48.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("−", color = colors.textPrimary) }
                                OutlinedButton(
                                    onClick = { if (hoursInput < 12) hoursInput++ },
                                    modifier = Modifier.size(48.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("+", color = colors.primaryAccent) }
                            }
                        }
                    }
                    Text(
                        text = "Extra KM: ₹${hourlyTariff.extraKmRate.toInt()}/km • 5-min grace • Wait: ₹0",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                } else {
                    OutlinedTextField(
                        value = durationInput,
                        onValueChange = { durationInput = it },
                        label = { Text("Duration (Minutes, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = themedFieldColors(),
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
                        colors = themedFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("quick_calc_local_wait_input")
                    )
                }

                // Manual-amount-only named additional charges (ZERO auto charge!)
                Text(
                    text = "Manual Additional Charges (Optional — enter amount only if applicable)",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = outsideCityAmountInput,
                        onValueChange = { outsideCityAmountInput = it },
                        label = { Text("Outside City (₹)") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = themedFieldColors(),
                        modifier = Modifier.weight(1f).testTag("quick_calc_outside_city")
                    )
                    OutlinedTextField(
                        value = nightAllowanceAmountInput,
                        onValueChange = { nightAllowanceAmountInput = it },
                        label = { Text("Night 11PM–6AM (₹)") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = themedFieldColors(),
                        modifier = Modifier.weight(1f).testTag("quick_calc_night")
                    )
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
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = outstationWaitHoursInput,
                            onValueChange = { outstationWaitHoursInput = it },
                            label = { Text("Wait Hours") },
                            placeholder = { Text("e.g. 2") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = themedFieldColors(),
                            modifier = Modifier.weight(1f).testTag("quick_calc_outstation_wait_hours")
                        )
                        OutlinedTextField(
                            value = outstationWaitRateInput,
                            onValueChange = { outstationWaitRateInput = it },
                            label = { Text("Rate / Hr (₹)") },
                            placeholder = { Text("Manual") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = themedFieldColors(),
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
                        colors = themedFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = permitInput,
                        onValueChange = { permitInput = it },
                        label = { Text("Permit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = themedFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = parkingInput,
                        onValueChange = { parkingInput = it },
                        label = { Text("Park (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = themedFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = customExtrasInput,
                    onValueChange = { customExtrasInput = it },
                    label = { Text("Other Additional Charges (₹, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = themedFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("quick_calc_custom_extras_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = pickupInput,
                        onValueChange = { pickupInput = it },
                        label = { Text("From") },
                        colors = themedFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dropInput,
                        onValueChange = { dropInput = it },
                        label = { Text("To") },
                        colors = themedFieldColors(),
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
                        val outCityManual = outsideCityAmountInput.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val nightManual = nightAllowanceAmountInput.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
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
                            use100KmPackage = use100Km,
                            waitingMinutes = localWaitMins,
                            isInnovaCrysta = initialInnovaCrysta && initialCategory == VehicleCategory.INNOVA,
                            isOutsideCity = outCityManual > 0.0,
                            isNightTrip = nightManual > 0.0,
                            outsideCityCharge = outCityManual,
                            nightAllowanceCharge = nightManual,
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
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
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
    val colors = LocalGetTaxiColors.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colors.primaryAccent),
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
                        color = colors.primaryAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                ReferenceBlock(
                    title = "1. OTP Local Normal & Minimum Bill",
                    body = "• MINI / SEDAN: ₹80 + ₹28/km | Min: 1–5km ₹200, 5–6km ₹250, >6km ₹80+₹28/km\n" +
                        "• SUV: ₹200 + ₹35/km | Min: 1–7km ₹450, >7km ₹200+₹35/km\n" +
                        "• SUV+: ₹250 + ₹45/km | Min: 1–10km ₹700, >10km ₹250+₹45/km\n" +
                        "• INNOVA: ₹250 + ₹50/km | Min: 1–10km ₹800, >10km ₹250+₹50/km\n" +
                        "• Waiting Charge: ₹2/min\n" +
                        "• Additional Charges: Outside City Limits & Night Allowance (11 PM–6 AM) — Manual entry only"
                )
                ReferenceBlock(
                    title = "2. Automatic Local CC (Zero Gaps)",
                    body = "• 0 to 5.00 KM: ₹25\n" +
                        "• >5.00 to 7.50 KM: ₹50\n" +
                        "• >7.50 to 10.00 KM: ₹70\n" +
                        "• >10.00 to 20.00 KM: ₹80\n" +
                        "• >20.00 KM: ₹100\n" +
                        "• Total Customer Bill > ₹1,000: 10% of ENTIRE Customer Bill (replaces fixed distance CC)"
                )
                ReferenceBlock(
                    title = "3. OTP Hourly Rental & 100 KM / 10h Packages",
                    body = "• MINI / SEDAN: ₹350/hr (min 1h, 10 km/hr, ₹20/extra km, ₹50/hr CC)\n" +
                        "• OMNI / Eeco: ₹450/hr (incl. ₹75 CC) | 100 KM / 10h Package ₹3,500 • Extra ₹13/km\n" +
                        "• Ertiga / Rumion (SUV/SUV+): ₹525/hr (incl. ₹75 CC, min 3h) | 100 KM / 10h Package ₹3,800 • Extra ₹14/km\n" +
                        "• Innova: ₹600/hr (incl. ₹100 CC, min 4h) | 100 KM / 10h Package ₹4,000 • Extra ₹15/km\n" +
                        "• 5-minute grace period • Waiting Charges: ₹0"
                )
                ReferenceBlock(
                    title = "4. Direct Meter Mode (Independent, No OTP, No CC)",
                    body = "• Local: ₹100 Base • ₹30/km\n" +
                        "• Hourly Rental: ₹375/hr • 10 KM Included • ₹20/extra km\n" +
                        "• Outstation: ₹300 Base • ₹30/km\n" +
                        "• Default Waiting: ₹2/min • Editable independently in Direct Meter Settings"
                )
                ReferenceBlock(
                    title = "5. OTP Outstation & Day Rental",
                    body = "• Outstation One-Way: ₹300 base + ₹30/km (>12h +₹400, 10% CC, manual wait)\n" +
                        "• Outstation Round-Trip: ₹500 base + ₹15/km (>12h +₹400, 10% CC, manual wait)\n" +
                        "• Day Rental: MINI/SEDAN ₹2000+10/km | SUV ₹2300+13/km | SUV+ ₹2400+14/km | INNOVA ₹2500+15/km | CRYSTA ₹3000+16/km\n" +
                        "• Day Rental CC: Variable / Managed manually per trip"
                )
            }
        }
    }
}

@Composable
private fun ReferenceBlock(
    title: String,
    body: String,
    accentColor: Color? = null
) {
    val colors = LocalGetTaxiColors.current
    Surface(
        color = colors.surfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, color = accentColor ?: colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, color = colors.textPrimary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun themedFieldColors(): TextFieldColors {
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
