package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActiveTripDisplayMode
import com.example.data.model.ExtraChargeItem
import com.example.data.model.TripStatus
import com.example.service.LocationTrackingService
import com.example.ui.components.ActiveMeterDashboardBackground
import com.example.ui.components.BatteryOptimizationCard
import com.example.ui.components.CreatorSignatureBanner
import com.example.ui.components.LiveSpeedBubble
import com.example.ui.components.LogoStyleCustomizerDialog
import com.example.ui.components.QuickFareCalculatorDialog
import com.example.ui.components.SangamModifiersAndRatePreviewCard
import com.example.ui.components.SangamTripTypeSelectorCard
import com.example.ui.components.TripMapView
import com.example.ui.components.VehicleCategorySelectorCard
import com.example.ui.components.VendorLogoBadge
import com.example.ui.theme.LocalGetTaxiColors
import com.example.data.preferences.OtpValidationResult
import com.example.util.SangamTariffConfig
import com.example.util.SangamTariffConfig.SangamTripType
import com.example.util.appStrings
import com.example.viewmodel.MeterViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private val HudAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMeterScreen(
    viewModel: MeterViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSettlement: () -> Unit,
    onNavigateToTariffDetails: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToDirectMeterSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = appStrings()
    val colors = LocalGetTaxiColors.current
    val scope = rememberCoroutineScope()

    val tripState by viewModel.tripState.collectAsStateWithLifecycle()
    val selectedMeterMode by viewModel.selectedMeterMode.collectAsStateWithLifecycle()
    val selectedDirectCategory by viewModel.selectedDirectCategory.collectAsStateWithLifecycle()
    val selectedVehicle by viewModel.selectedVehicleCategory.collectAsStateWithLifecycle()
    val selectedTripType by viewModel.selectedSangamTripType.collectAsStateWithLifecycle()
    val isInnovaCrysta by viewModel.isInnovaCrysta.collectAsStateWithLifecycle()
    val isOutsideCity by viewModel.isOutsideCity.collectAsStateWithLifecycle()
    val isNightTrip by viewModel.isNightTrip.collectAsStateWithLifecycle()
    val packageHours by viewModel.packageHours.collectAsStateWithLifecycle()
    val use100KmPackage by viewModel.use100KmPackage.collectAsStateWithLifecycle()
    val over12Hours by viewModel.over12Hours.collectAsStateWithLifecycle()
    val activeTripDisplayMode by viewModel.activeTripDisplayMode.collectAsStateWithLifecycle()
    val hasBackup by viewModel.hasActiveSessionBackup.collectAsStateWithLifecycle()
    val validationMessage by viewModel.tariffValidationMessage.collectAsStateWithLifecycle()
    val connectivityStatus by viewModel.connectivityStatus.collectAsStateWithLifecycle()

    var showQuickCalculatorDialog by remember { mutableStateOf(false) }
    var showExtraChargesDialog by remember { mutableStateOf(false) }
    var showLogoStyleDialog by remember { mutableStateOf(false) }
    var showGapAdjustDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    var otpErrorMsg by remember { mutableStateOf<String?>(null) }
    var logoTapCount by remember { mutableIntStateOf(0) }
    var lastLogoTapMs by remember { mutableLongStateOf(0L) }
    var showTopMenu by remember { mutableStateOf(false) }

    val isTripActive = tripState.status == TripStatus.RUNNING || tripState.status == TripStatus.PAUSED

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            LocationTrackingService.refreshOverlay(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkForActiveBackup()
        viewModel.loadDriverProfile()

        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            LocationTrackingService.refreshOverlay(context)
        } else {
            locationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                val now = System.currentTimeMillis()
                                if (now - lastLogoTapMs > 2500L) {
                                    logoTapCount = 1
                                } else {
                                    logoTapCount += 1
                                }
                                lastLogoTapMs = now
                                if (logoTapCount >= 5) {
                                    logoTapCount = 0
                                    onNavigateToTariffDetails()
                                }
                            }
                            .testTag("brand_header_title")
                    ) {
                        VendorLogoBadge(
                            vendorId = SangamTariffConfig.VENDOR_ID,
                            vendorName = SangamTariffConfig.VENDOR_NAME,
                            height = 40.dp,
                            testTag = "header_vendor_logo_badge"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier
                            .testTag("trip_history_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Trip History",
                            tint = colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = onNavigateToAbout,
                        modifier = Modifier
                            .testTag("tariff_chart_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = "Habeeb Digital Solutions (HDS)",
                            tint = colors.primaryAccent
                        )
                    }

                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier
                            .testTag("driver_profile_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = strings.profile,
                            tint = colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .testTag("settings_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.settings,
                            tint = colors.textSecondary
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showTopMenu = true },
                            modifier = Modifier
                                .testTag("top_overflow_menu_button")
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = colors.textPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Direct Meter Settings") },
                                onClick = {
                                    showTopMenu = false
                                    onNavigateToDirectMeterSettings()
                                },
                                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Trip History") },
                                onClick = {
                                    showTopMenu = false
                                    onNavigateToHistory()
                                },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Logo Color & Font") },
                                onClick = {
                                    showTopMenu = false
                                    showLogoStyleDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.aboutUs) },
                                onClick = {
                                    showTopMenu = false
                                    onNavigateToAbout()
                                },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        if (isTripActive) {
            // Unified Active Live Meter Dashboard — Fare and Map switch in-place in the exact same rectangular area
            BoxWithConstraints(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                val minHeightForFill = if (tripState.needsDistanceVerification) 820.dp else 690.dp
                val isCompactViewport = maxHeight < minHeightForFill
                val containerModifier = if (isCompactViewport) {
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 12.dp)
                } else {
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 10.dp)
                }

                Column(
                    modifier = containerModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Compact GPS + Connected-First Status & Occupied/Paused Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (tripState.latitude != null) colors.greenSuccess else HudAmber,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (tripState.latitude != null) strings.gpsSignalStrong else strings.acquiringGps,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val netBadge = if (connectivityStatus.isNetworkConnected) {
                                    "Online (${connectivityStatus.transportLabel})"
                                } else {
                                    "Local Storage Ready"
                                }
                                Text(
                                    text = "GET TAXI – Smart Taxi Meter • $netBadge",
                                    color = colors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            color = when (tripState.status) {
                                TripStatus.RUNNING -> colors.primaryAccent
                                TripStatus.PAUSED -> colors.yellowContainer
                                else -> colors.surface
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                when (tripState.status) {
                                    TripStatus.RUNNING -> colors.primaryAccent
                                    TripStatus.PAUSED -> HudAmber
                                    else -> colors.border
                                }
                            ),
                            modifier = Modifier.testTag("trip_status_badge")
                        ) {
                            Text(
                                text = when (tripState.status) {
                                    TripStatus.RUNNING -> strings.occupied.uppercase(Locale.US)
                                    TripStatus.PAUSED -> strings.paused.uppercase(Locale.US)
                                    else -> strings.vacant.uppercase(Locale.US)
                                },
                                color = when (tripState.status) {
                                    TripStatus.RUNNING -> colors.onPrimaryAccent
                                    TripStatus.PAUSED -> HudAmber
                                    else -> colors.textPrimary
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // 2. Auditable GPS Interruption Gap Banner (only shown if GPS gap occurred)
                    if (tripState.needsDistanceVerification) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.yellowContainer),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HudAmber),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gps_gap_verification_banner")
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "GPS Interruption Gap Detected (${tripState.unverifiedGapSeconds}s)",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Saved GPS distance preserved without fabricating missing km. Straight-line ref: ${String.format(Locale.US, "%.2f", tripState.unverifiedGapStraightLineKm)} km.",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { viewModel.dismissGapWarning() },
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Text("Keep Recorded KM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { showGapAdjustDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Text("Verify / Add KM", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Upper Live Driving Video Dashboard — absorbs available screen height dynamically
                    val videoModifier = if (isCompactViewport) {
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("active_meter_video_dashboard")
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 210.dp)
                            .testTag("active_meter_video_dashboard")
                    }
                    ActiveMeterDashboardBackground(
                        distanceKm = tripState.distanceKm,
                        durationSeconds = tripState.durationSeconds,
                        speedKmH = tripState.speedKmH,
                        isPaused = tripState.status == TripStatus.PAUSED,
                        isDirectMeter = tripState.meterMode == SangamTariffConfig.METER_MODE_DIRECT,
                        modifier = videoModifier
                    )

                    // 4. In-Place Fare / Map Switcher (switches content in the exact same rectangular slot below)
                    val isMapSelected = activeTripDisplayMode == ActiveTripDisplayMode.MAP_FIRST
                    Surface(
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_meter_view_switcher")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { viewModel.showMeterDetails() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isMapSelected) colors.primaryAccent else colors.surfaceElevated,
                                    contentColor = if (!isMapSelected) colors.onPrimaryAccent else colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("active_meter_tab_fare")
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("FARE", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                            }

                            Button(
                                onClick = { viewModel.showMapDisplay() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMapSelected) colors.primaryAccent else colors.surfaceElevated,
                                    contentColor = if (isMapSelected) colors.onPrimaryAccent else colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("active_meter_tab_map")
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("MAP", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                            }
                        }
                    }

                    // 5. Shared Rectangular Area — Fare Panel and Live Map switch strictly in-place
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(238.dp)
                            .testTag("active_meter_shared_panel_slot")
                    ) {
                        if (isMapSelected) {
                            // Map view inside the exact fare-panel area: no fare panel, no telemetry overlays, no fullscreen jump
                            TripMapView(
                                tripState = tripState,
                                isNavigationConsoleMode = false,
                                isExpanded = false,
                                onToggleExpand = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("active_trip_inline_map")
                            )
                        } else {
                            CommercialMeterGaugeCard(
                                tripState = tripState,
                                activeTripType = SangamTripType.fromId(tripState.sangamTripTypeId),
                                onOpenExtras = { showExtraChargesDialog = true },
                                fillSlotHeight = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // 6. Active Trip Mode / Category Pill
                    val isDirectActive = tripState.meterMode == SangamTariffConfig.METER_MODE_DIRECT
                    val activeModeTitle = if (isDirectActive) {
                        val dCat = SangamTariffConfig.DirectMeterCategory.fromId(tripState.directCategoryId)
                        "DIRECT METER • ${dCat.displayName}"
                    } else {
                        val activeCat = SangamTariffConfig.VehicleCategory.fromId(tripState.vehicleCategoryId)
                        val activeMode = SangamTripType.fromId(tripState.sangamTripTypeId)
                        val activeCatLabel = if (tripState.isInnovaCrysta) "INNOVA CRYSTA" else activeCat.displayName
                        "$activeCatLabel • ${activeMode.displayName}"
                    }
                    Surface(
                        color = colors.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_trip_mode_badge")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalTaxi,
                                    contentDescription = null,
                                    tint = colors.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeModeTitle,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                text = if (isDirectActive) "NO CC" else "TRIP ACTIVE",
                                color = colors.yellowText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // 7. Ergonomic Bottom Pause / Resume & End & Settle Controls
                    MeterActionControls(
                        status = tripState.status,
                        isDirectMode = isDirectActive,
                        onStartDirectTrip = { viewModel.startDirectMeterTrip(selectedDirectCategory) },
                        onStartTrip = { viewModel.startTrip() },
                        onOpenStartWithOtp = {
                            otpInput = ""
                            otpErrorMsg = null
                            showOtpDialog = true
                        },
                        onOpenParkedCalculator = { showQuickCalculatorDialog = true },
                        onPauseTrip = { viewModel.pauseTrip() },
                        onResumeTrip = { viewModel.resumeTrip() },
                        onEndTrip = { viewModel.completeTripForSettlement { onNavigateToSettlement() } }
                    )

                    // 8. Compact Bottom Creator Signature
                    CreatorSignatureBanner(testTag = "home_creator_credit_banner")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                if (!isTripActive) {
                    BatteryOptimizationCard()

                    if (hasBackup && tripState.status == TripStatus.IDLE) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.yellowContainer),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, colors.yellowHighlight),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = strings.unfinishedRideDetected,
                                    color = colors.yellowText,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = strings.unfinishedRideDesc,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.discardBackup() },
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Text(strings.discard, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { viewModel.recoverTrip() },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                                        modifier = Modifier.weight(1.4f).height(48.dp)
                                    ) {
                                        Text(strings.resumeRide, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }

                    if (validationMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.yellowContainer),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HudAmber),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(validationMessage!!, color = colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.clearValidationMessage() }) {
                                    Icon(Icons.Default.Close, contentDescription = strings.close, tint = HudAmber)
                                }
                            }
                        }
                    }
                }

                // GPS + Connected-First Status & Taxi Meter Vacant/Occupied Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (tripState.latitude != null) colors.greenSuccess else HudAmber,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (tripState.latitude != null) strings.gpsSignalStrong else strings.acquiringGps,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val netBadge = if (connectivityStatus.isNetworkConnected) {
                                "Online (${connectivityStatus.transportLabel})"
                            } else {
                                "Local Storage Ready"
                            }
                            Text(
                                text = "GET TAXI – Smart Taxi Meter • $netBadge",
                                color = colors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        color = when (tripState.status) {
                            TripStatus.RUNNING -> colors.primaryAccent
                            TripStatus.PAUSED -> colors.yellowContainer
                            else -> colors.surface
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.dp,
                            when (tripState.status) {
                                TripStatus.RUNNING -> colors.primaryAccent
                                TripStatus.PAUSED -> HudAmber
                                else -> colors.border
                            }
                        ),
                        modifier = Modifier.testTag("trip_status_badge")
                    ) {
                        Text(
                            text = when (tripState.status) {
                                TripStatus.RUNNING -> strings.occupied.uppercase(Locale.US)
                                TripStatus.PAUSED -> strings.paused.uppercase(Locale.US)
                                else -> strings.vacant.uppercase(Locale.US)
                            },
                            color = when (tripState.status) {
                                TripStatus.RUNNING -> colors.onPrimaryAccent
                                TripStatus.PAUSED -> HudAmber
                                else -> colors.textPrimary
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // Explicit Auditable GPS Interruption Gap Banner (Never fabricates unmeasured distance!)
                if (isTripActive && tripState.needsDistanceVerification) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.yellowContainer),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, HudAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("gps_gap_verification_banner")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "GPS Interruption Gap Detected (${tripState.unverifiedGapSeconds}s)",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Saved GPS distance preserved without fabricating missing km. Straight-line ref: ${String.format(Locale.US, "%.2f", tripState.unverifiedGapStraightLineKm)} km.",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { viewModel.dismissGapWarning() },
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text("Keep Recorded KM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { showGapAdjustDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text("Verify / Add KM", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                TripMapView(
                    tripState = tripState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(vertical = 4.dp)
                )

                // COMMERCIAL METER FARE GAUGE HUD
                CommercialMeterGaugeCard(
                    tripState = tripState,
                    activeTripType = selectedTripType,
                    onOpenExtras = { showExtraChargesDialog = true }
                )

                    // OTP Meter vs Direct Meter Independent Mode Switcher
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("meter_mode_switcher_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isOtpTab = selectedMeterMode == SangamTariffConfig.METER_MODE_OTP
                            Button(
                                onClick = { viewModel.selectMeterMode(SangamTariffConfig.METER_MODE_OTP) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isOtpTab) colors.primaryAccent else colors.surfaceElevated,
                                    contentColor = if (isOtpTab) colors.onPrimaryAccent else colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mode_tab_otp")
                            ) {
                                Text("OTP METER", fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                            Button(
                                onClick = { viewModel.selectMeterMode(SangamTariffConfig.METER_MODE_DIRECT) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isOtpTab) colors.primaryAccent else colors.surfaceElevated,
                                    contentColor = if (!isOtpTab) colors.onPrimaryAccent else colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mode_tab_direct")
                            ) {
                                Text("DIRECT METER", fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    if (selectedMeterMode == SangamTariffConfig.METER_MODE_DIRECT) {
                        DirectMeterSelectorCard(
                            selectedCategory = selectedDirectCategory,
                            onSelectCategory = { viewModel.selectDirectCategory(it) },
                            onOpenDirectSettings = onNavigateToDirectMeterSettings
                        )
                    } else {
                        VehicleCategorySelectorCard(
                            selectedCategory = selectedVehicle,
                            selectedTripType = selectedTripType,
                            isTripActive = false,
                            onSelectCategory = { viewModel.selectVehicleCategory(it) }
                        )

                        SangamTripTypeSelectorCard(
                            selectedCategory = selectedVehicle,
                            selectedTripType = selectedTripType,
                            isInnovaCrysta = isInnovaCrysta,
                            isTripActive = false,
                            onSelectTripType = { viewModel.setSangamTripType(it) },
                            onToggleInnovaCrysta = { viewModel.setInnovaCrysta(it) }
                        )

                        SangamModifiersAndRatePreviewCard(
                            category = selectedVehicle,
                            tripType = selectedTripType,
                            isInnovaCrysta = isInnovaCrysta,
                            isOutsideCity = isOutsideCity,
                            isNightTrip = isNightTrip,
                            packageHours = packageHours,
                            use100KmPackage = use100KmPackage,
                            over12Hours = over12Hours,
                            onToggleOutsideCity = { viewModel.setOutsideCity(it) },
                            onToggleNightTrip = { viewModel.setNightTrip(it) },
                            onSelectPackageHours = { viewModel.setPackageHours(it) },
                            onToggle100KmPackage = { viewModel.setUse100KmPackage(it) },
                            onToggleOver12Hours = { viewModel.setOver12Hours(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // PRIMARY START / DIRECT START / START WITH OTP METER ACTIONS
                    MeterActionControls(
                        status = tripState.status,
                        isDirectMode = selectedMeterMode == SangamTariffConfig.METER_MODE_DIRECT,
                        onStartDirectTrip = { viewModel.startDirectMeterTrip(selectedDirectCategory) },
                        onStartTrip = { viewModel.startTrip() },
                        onOpenStartWithOtp = {
                            otpInput = ""
                            otpErrorMsg = null
                            showOtpDialog = true
                        },
                        onOpenParkedCalculator = { showQuickCalculatorDialog = true },
                        onPauseTrip = { viewModel.pauseTrip() },
                        onResumeTrip = { viewModel.resumeTrip() },
                        onEndTrip = { viewModel.completeTripForSettlement { onNavigateToSettlement() } }
                    )

                Spacer(modifier = Modifier.height(14.dp))
                CreatorSignatureBanner(testTag = "home_creator_credit_banner")
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showLogoStyleDialog) {
        LogoStyleCustomizerDialog(
            vendorId = SangamTariffConfig.VENDOR_ID,
            vendorName = SangamTariffConfig.VENDOR_NAME,
            onDismiss = { showLogoStyleDialog = false }
        )
    }

    if (showOtpDialog) {
        StartWithOtpDialog(
            otpInput = otpInput,
            errorMsg = otpErrorMsg,
            onOtpChange = { next ->
                if (next.length <= 4 && next.all { it.isDigit() }) {
                    otpInput = next
                    otpErrorMsg = null
                }
            },
            onDismiss = { showOtpDialog = false },
            onVerifyAndStart = {
                scope.launch {
                    when (val res = viewModel.validateStartWithOtp(otpInput)) {
                        is OtpValidationResult.Success -> {
                            showOtpDialog = false
                            otpInput = ""
                            otpErrorMsg = null
                            viewModel.startTrip()
                        }
                        is OtpValidationResult.Failure -> {
                            otpErrorMsg = res.message
                        }
                    }
                }
            }
        )
    }

    if (showGapAdjustDialog) {
        var gapKmInput by remember {
            mutableStateOf(String.format(Locale.US, "%.2f", tripState.unverifiedGapStraightLineKm))
        }
        AlertDialog(
            onDismissRequest = { showGapAdjustDialog = false },
            title = { Text("Verify Interrupted GPS Distance", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter verified road distance (KM) to add for the ${tripState.unverifiedGapSeconds}s GPS interruption gap. Recorded GPS distance (${String.format(Locale.US, "%.2f", tripState.distanceKm)} km) will be preserved.",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = gapKmInput,
                        onValueChange = { gapKmInput = it },
                        label = { Text("Additional Verified KM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val addKm = gapKmInput.trim().toDoubleOrNull() ?: 0.0
                    if (addKm > 0.0) {
                        viewModel.applyVerifiedGapAdjustment(addKm)
                    } else {
                        viewModel.dismissGapWarning()
                    }
                    showGapAdjustDialog = false
                }) {
                    Text("Apply Verified KM")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGapAdjustDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showQuickCalculatorDialog) {
        QuickFareCalculatorDialog(
            initialCategory = selectedVehicle,
            initialTripType = selectedTripType,
            initialInnovaCrysta = isInnovaCrysta,
            initialOutsideCity = isOutsideCity,
            initialNightTrip = isNightTrip,
            initialPackageHours = packageHours,
            initialUse100KmPackage = use100KmPackage,
            initialOver12Hours = over12Hours,
            onDismiss = { showQuickCalculatorDialog = false },
            onCalculateAndSettle = { input, pickup, drop, durMin ->
                showQuickCalculatorDialog = false
                viewModel.calculateQuickSettlement(
                    input = input,
                    pickupAddress = pickup,
                    dropAddress = drop,
                    durationMinutes = durMin
                ) {
                    onNavigateToSettlement()
                }
            }
        )
    }

    if (showExtraChargesDialog) {
        ActiveExtraChargesDialog(
            currency = tripState.currency,
            currentItems = tripState.extraChargeItems,
            onAddCharge = { label, amount -> viewModel.addExtraCharge(label, amount) },
            onRemoveCharge = { id -> viewModel.removeExtraCharge(id) },
            onDismiss = { showExtraChargesDialog = false }
        )
    }
}

@Composable
private fun DirectMeterSelectorCard(
    selectedCategory: SangamTariffConfig.DirectMeterCategory,
    onSelectCategory: (SangamTariffConfig.DirectMeterCategory) -> Unit,
    onOpenDirectSettings: () -> Unit
) {
    val colors = LocalGetTaxiColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("direct_meter_selector_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DIRECT METER MODE (NO OTP • NO CC)",
                        color = colors.primaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Customer Invoice only • Separate from OTP Meter",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                TextButton(
                    onClick = onOpenDirectSettings,
                    modifier = Modifier.testTag("open_direct_settings_button")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Direct Settings", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rates", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SangamTariffConfig.DirectMeterCategory.entries.forEach { cat ->
                    val selected = cat == selectedCategory
                    Surface(
                        color = if (selected) colors.primaryAccent else colors.surfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (selected) colors.primaryAccent else colors.border),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectCategory(cat) }
                            .testTag("direct_cat_${cat.id.lowercase(Locale.US)}")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                            Text(
                                text = cat.displayName,
                                color = if (selected) colors.onPrimaryAccent else colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            val preview = when (selectedCategory) {
                SangamTariffConfig.DirectMeterCategory.LOCAL ->
                    "Default Local: ₹100 Base + ₹30/km • Wait ₹2/min (editable in Direct Meter Settings)"
                SangamTariffConfig.DirectMeterCategory.HOURLY_RENTAL ->
                    "Default Hourly: ₹375/hr • 10 KM Included • ₹20/extra km (editable in Direct Meter Settings)"
                SangamTariffConfig.DirectMeterCategory.OUTSTATION ->
                    "Default Outstation: ₹300 Base + ₹30/km • Wait ₹2/min (editable in Direct Meter Settings)"
            }
            Text(
                text = preview,
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun CommercialMeterGaugeCard(
    tripState: com.example.data.model.TripState,
    activeTripType: SangamTripType,
    onOpenExtras: () -> Unit,
    fillSlotHeight: Boolean = false,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
) {
    val colors = LocalGetTaxiColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.meterDisplayBg),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.5.dp, if (tripState.hasUnresolvedBoundaryFlag) HudAmber else colors.primaryAccent),
        modifier = modifier
            .testTag("meter_details_fare_card")
    ) {
        Box(modifier = if (fillSlotHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (fillSlotHeight) Arrangement.Center else Arrangement.Top,
                modifier = if (fillSlotHeight) {
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                }
            ) {
                Text(
                    text = if (tripState.meterMode == SangamTariffConfig.METER_MODE_DIRECT) "DIRECT METER FARE" else "LIVE METER FARE",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.6.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = tripState.currency,
                        color = colors.primaryAccent,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f", tripState.currentFare),
                        color = colors.primaryAccent,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.testTag("fare_text")
                    )
                }

                if (tripState.extraChargeItems.isNotEmpty()) {
                    Surface(
                        color = colors.surfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable { onOpenExtras() }
                            .testTag("live_extra_charges_summary_pill")
                    ) {
                        Text(
                            text = "Extras +${tripState.currency}${String.format(Locale.US, "%.0f", tripState.liveExtraChargesTotal)}",
                            color = colors.yellowText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile(
                        label = "DISTANCE",
                        value = "${String.format(Locale.US, "%.2f", tripState.distanceKm)} km",
                        modifier = Modifier.weight(1f)
                    )
                    val isDirect = tripState.meterMode == SangamTariffConfig.METER_MODE_DIRECT
                    val isDirectHourly = isDirect && SangamTariffConfig.DirectMeterCategory.fromId(tripState.directCategoryId) == SangamTariffConfig.DirectMeterCategory.HOURLY_RENTAL
                    val middleLabel = when {
                        isDirectHourly -> "HOURS"
                        isDirect -> "WAIT"
                        activeTripType == SangamTripType.HOURLY_RENTAL -> if (tripState.use100KmPackage) "PKG" else "PACKAGE"
                        else -> "WAIT"
                    }
                    val middleVal = when {
                        isDirectHourly -> "${tripState.billableHours}h"
                        isDirect -> {
                            val wMin = tripState.waitingSeconds / 60
                            val wSec = tripState.waitingSeconds % 60
                            String.format(Locale.US, "%dm %02ds", wMin, wSec)
                        }
                        activeTripType == SangamTripType.HOURLY_RENTAL -> if (tripState.use100KmPackage) "100km" else "${tripState.packageHours}h"
                        activeTripType == SangamTripType.LOCAL_NORMAL || activeTripType == SangamTripType.LOCAL_MINIMUM_BILL -> {
                            val wMin = tripState.waitingSeconds / 60
                            val wSec = tripState.waitingSeconds % 60
                            String.format(Locale.US, "%dm %02ds", wMin, wSec)
                        }
                        else -> "Manual"
                    }
                    MetricTile(
                        label = middleLabel,
                        value = middleVal,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("waiting_time_tile")
                    )
                    val dMin = tripState.durationSeconds / 60
                    val dSec = tripState.durationSeconds % 60
                    MetricTile(
                        label = "DURATION",
                        value = if (dMin > 0L) "${dMin}m ${String.format(Locale.US, "%02d", dSec)}s" else "${dSec}s",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            LiveSpeedBubble(
                speedKmH = tripState.speedKmH,
                visible = tripState.status == TripStatus.RUNNING || tripState.status == TripStatus.PAUSED,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )

            IconButton(
                onClick = onOpenExtras,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.border, CircleShape)
                    .testTag("active_meter_extra_charges_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddRoad,
                    contentDescription = "Extra Charges",
                    tint = colors.primaryAccent
                )
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalGetTaxiColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(colors.surfaceElevated, RoundedCornerShape(14.dp))
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MeterActionControls(
    status: TripStatus,
    isDirectMode: Boolean,
    onStartDirectTrip: () -> Unit,
    onStartTrip: () -> Unit,
    onOpenStartWithOtp: () -> Unit,
    onOpenParkedCalculator: () -> Unit,
    onPauseTrip: () -> Unit,
    onResumeTrip: () -> Unit,
    onEndTrip: () -> Unit
) {
    val colors = LocalGetTaxiColors.current
    AnimatedContent(targetState = status, label = "MeterActionControls") { target ->
        when (target) {
            TripStatus.IDLE, TripStatus.FINISHED -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isDirectMode) {
                        Button(
                            onClick = onStartDirectTrip,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("direct_start_meter_button")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DIRECT START METER", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    } else {
                        Button(
                            onClick = onStartTrip,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("start_trip_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("START METER", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }

                        OutlinedButton(
                            onClick = onOpenStartWithOtp,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, colors.primaryAccent),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.primaryAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_with_otp_button")
                        ) {
                            Icon(Icons.Default.Pin, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("START WITH OTP", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                        }

                        OutlinedButton(
                            onClick = onOpenParkedCalculator,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.textPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("parked_calculator_button")
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CALCULATE BILL (PARKED)", fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            TripStatus.RUNNING -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onPauseTrip,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, colors.primaryAccent),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("pause_trip_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, tint = colors.primaryAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PAUSE", color = colors.primaryAccent, fontWeight = FontWeight.Black)
                    }

                    Button(
                        onClick = onEndTrip,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(56.dp)
                            .testTag("stop_trip_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("END & SETTLE", fontWeight = FontWeight.Black)
                    }
                }
            }

            TripStatus.PAUSED -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onResumeTrip,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.greenSuccess, contentColor = Color.White),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(56.dp)
                            .testTag("resume_trip_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESUME", fontWeight = FontWeight.Black)
                    }

                    Button(
                        onClick = onEndTrip,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(56.dp)
                            .testTag("stop_trip_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("END & SETTLE", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveExtraChargesDialog(
    currency: String,
    currentItems: List<ExtraChargeItem>,
    onAddCharge: (String, Double) -> Unit,
    onRemoveCharge: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalGetTaxiColors.current
    var labelInput by remember { mutableStateOf(SangamTariffConfig.EXTRA_LABEL_OUTSIDE_CITY) }
    var amountInput by remember { mutableStateOf("") }
    var isOutstationWaitMode by remember { mutableStateOf(false) }
    var waitHoursInput by remember { mutableStateOf("") }
    var waitRateInput by remember { mutableStateOf("") }
    val namedManualPresets = listOf(
        SangamTariffConfig.EXTRA_LABEL_OUTSIDE_CITY,
        SangamTariffConfig.EXTRA_LABEL_NIGHT_ALLOWANCE
    )
    val otherPresets = listOf("Toll", "Permit", "Parking")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, colors.primaryAccent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("extra_charges_dialog")
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
                    Text("ADDITIONAL CHARGES", color = colors.primaryAccent, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                Text(
                    text = "Manual entry only — no automatic charge is added unless you enter an amount.",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    namedManualPresets.forEach { preset ->
                        FilterChip(
                            selected = !isOutstationWaitMode && labelInput == preset,
                            onClick = {
                                isOutstationWaitMode = false
                                labelInput = preset
                            },
                            label = { Text(preset, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("manual_extra_preset_${preset.take(8).lowercase(Locale.US)}")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        otherPresets.forEach { preset ->
                            FilterChip(
                                selected = !isOutstationWaitMode && labelInput == preset,
                                onClick = {
                                    isOutstationWaitMode = false
                                    labelInput = preset
                                },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                    FilterChip(
                        selected = isOutstationWaitMode,
                        onClick = {
                            isOutstationWaitMode = true
                            labelInput = "Additional Waiting Hours"
                        },
                        label = { Text("Outstation Waiting Hrs", fontSize = 11.sp) },
                        modifier = Modifier.testTag("outstation_wait_preset_chip")
                    )
                }

                if (isOutstationWaitMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = waitHoursInput,
                            onValueChange = { hrs ->
                                waitHoursInput = hrs
                                val h = hrs.trim().toDoubleOrNull()
                                val r = waitRateInput.trim().toDoubleOrNull()
                                if (h != null && r != null && h > 0.0 && r > 0.0) {
                                    amountInput = String.format(Locale.US, "%.2f", h * r)
                                }
                            },
                            label = { Text("Wait Hours") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("outstation_wait_hours_input")
                        )
                        OutlinedTextField(
                            value = waitRateInput,
                            onValueChange = { rate ->
                                waitRateInput = rate
                                val h = waitHoursInput.trim().toDoubleOrNull()
                                val r = rate.trim().toDoubleOrNull()
                                if (h != null && r != null && h > 0.0 && r > 0.0) {
                                    amountInput = String.format(Locale.US, "%.2f", h * r)
                                }
                            },
                            label = { Text("Rate/Hr ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("outstation_wait_rate_input")
                        )
                    }
                }

                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text("Charge Label") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Manual Amount ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("extra_charge_amount_input")
                )

                Button(
                    onClick = {
                        val amt = amountInput.trim().toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            val finalLabel = if (isOutstationWaitMode) {
                                val h = waitHoursInput.trim().toDoubleOrNull()
                                val r = waitRateInput.trim().toDoubleOrNull()
                                when {
                                    h != null && r != null && h > 0.0 && r > 0.0 ->
                                        "Additional Waiting (${String.format(Locale.US, "%.1f", h)} hr @ $currency${String.format(Locale.US, "%.0f", r)}/hr)"
                                    h != null && h > 0.0 ->
                                        "Additional Waiting (${String.format(Locale.US, "%.1f", h)} hr)"
                                    else -> labelInput.ifBlank { "Additional Waiting Hours" }
                                }
                            } else {
                                labelInput.ifBlank { "Extra" }
                            }
                            onAddCharge(finalLabel, amt)
                            amountInput = ""
                            waitHoursInput = ""
                            waitRateInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_extra_charge_submit_button")
                ) {
                    Text("ADD CHARGE", fontWeight = FontWeight.Black)
                }

                if (currentItems.isNotEmpty()) {
                    HorizontalDivider(color = colors.border)
                    currentItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${item.label}: $currency${String.format(Locale.US, "%.2f", item.amount)}",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemoveCharge(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = HudAmber)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartWithOtpDialog(
    otpInput: String,
    errorMsg: String?,
    onOtpChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onVerifyAndStart: () -> Unit
) {
    val colors = LocalGetTaxiColors.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, colors.primaryAccent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("otp_start_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "START WITH OTP",
                        color = colors.primaryAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                Text(
                    text = "Enter your authorized 4-digit trip start PIN to start the OTP meter.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = otpInput,
                    onValueChange = onOtpChange,
                    label = { Text("4-Digit OTP") },
                    placeholder = { Text("••••") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
                        .fillMaxWidth()
                        .testTag("otp_pin_input")
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg,
                        color = colors.redDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("otp_error_text")
                    )
                }

                Button(
                    onClick = onVerifyAndStart,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent, contentColor = colors.onPrimaryAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("otp_verify_start_button")
                ) {
                    Text("VERIFY & START METER", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}

