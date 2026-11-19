package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.DriverProfileEntity
import com.example.data.database.TripDatabase
import com.example.data.database.TripEntity
import com.example.data.database.VendorProfileEntity
import com.example.data.model.ActiveTripDisplayMode
import com.example.data.model.ExtraChargeItem
import com.example.data.model.ServiceType
import com.example.data.model.TripState
import com.example.data.model.TripStatus
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.TripRepository
import com.example.service.LocationTrackingService
import com.example.util.SangamTariffCalculator
import com.example.util.SangamTariffConfig
import com.example.util.SangamTariffConfig.SangamTripType
import com.example.util.SangamTariffConfig.VehicleCategory
import com.example.util.VendorLogoRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeterViewModel(application: Application) : AndroidViewModel(application) {

    private val tripRepository: TripRepository
    private val settingsRepository: SettingsRepository
    private val tripOtpManager: com.example.data.preferences.TripOtpManager

    val tripState: StateFlow<TripState> = LocationTrackingService.tripState

    val allTrips: StateFlow<List<TripEntity>>
    val tripHistory: StateFlow<List<TripEntity>>
        get() = allTrips
    val allVendors: StateFlow<List<VendorProfileEntity>>
    val activeVendors: StateFlow<List<VendorProfileEntity>>

    private val _selectedServiceType = MutableStateFlow(ServiceType.LOCAL)
    val selectedServiceType: StateFlow<ServiceType> = _selectedServiceType.asStateFlow()

    // Sangam Vehicle & Tariff Selection
    private val _selectedVehicleCategory = MutableStateFlow(VehicleCategory.MINI)
    val selectedVehicleCategory: StateFlow<VehicleCategory> = _selectedVehicleCategory.asStateFlow()

    private val _selectedSangamTripType = MutableStateFlow(SangamTripType.LOCAL_NORMAL)
    val selectedSangamTripType: StateFlow<SangamTripType> = _selectedSangamTripType.asStateFlow()

    private val _isInnovaCrysta = MutableStateFlow(false)
    val isInnovaCrysta: StateFlow<Boolean> = _isInnovaCrysta.asStateFlow()

    private val _isOutsideCity = MutableStateFlow(false)
    val isOutsideCity: StateFlow<Boolean> = _isOutsideCity.asStateFlow()

    private val _isNightTrip = MutableStateFlow(false)
    val isNightTrip: StateFlow<Boolean> = _isNightTrip.asStateFlow()

    private val _packageHours = MutableStateFlow(1)
    val packageHours: StateFlow<Int> = _packageHours.asStateFlow()

    private val _use100KmPackage = MutableStateFlow(false)
    val use100KmPackage: StateFlow<Boolean> = _use100KmPackage.asStateFlow()

    private val _over12Hours = MutableStateFlow(false)
    val over12Hours: StateFlow<Boolean> = _over12Hours.asStateFlow()

    // Direct Meter Mode selection (completely independent of OTP Meter)
    private val _selectedMeterMode = MutableStateFlow(SangamTariffConfig.METER_MODE_OTP)
    val selectedMeterMode: StateFlow<String> = _selectedMeterMode.asStateFlow()

    private val _selectedDirectCategory = MutableStateFlow(SangamTariffConfig.DirectMeterCategory.LOCAL)
    val selectedDirectCategory: StateFlow<SangamTariffConfig.DirectMeterCategory> = _selectedDirectCategory.asStateFlow()

    // Live network & GPS connectivity status
    private val connectivityMonitor = com.example.util.ConnectivityMonitor(application)
    val connectivityStatus: StateFlow<com.example.util.ConnectivityStatus> = connectivityMonitor.status

    // Exclusive GET TAXI vendor (no multi-vendor switching in v1)
    private val _selectedVendor = MutableStateFlow(VendorLogoRenderer.defaultGetTaxiProfile())
    val selectedVendor: StateFlow<VendorProfileEntity> = _selectedVendor.asStateFlow()

    // Active completed-trip settlement state (automatically persisted in local Room history)
    private val _manualSettlement = MutableStateFlow<LocationTrackingService.TransientCompletedTrip?>(null)
    val currentSettlement: StateFlow<LocationTrackingService.TransientCompletedTrip?> = combine(
        _manualSettlement,
        LocationTrackingService.lastCompletedTrip
    ) { manual, serviceTrip ->
        manual ?: serviceTrip
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _activeTripDisplayMode = MutableStateFlow(ActiveTripDisplayMode.METER_DETAILS)
    val activeTripDisplayMode: StateFlow<ActiveTripDisplayMode> = _activeTripDisplayMode.asStateFlow()

    private val _hasActiveSessionBackup = MutableStateFlow(false)
    val hasActiveSessionBackup: StateFlow<Boolean> = _hasActiveSessionBackup.asStateFlow()

    private val _driverProfile = MutableStateFlow<DriverProfileEntity?>(null)
    val driverProfile: StateFlow<DriverProfileEntity?> = _driverProfile.asStateFlow()

    private val _tariffValidationMessage = MutableStateFlow<String?>(null)
    val tariffValidationMessage: StateFlow<String?> = _tariffValidationMessage.asStateFlow()

    init {
        val database = TripDatabase.getDatabase(application)
        tripRepository = TripRepository(database.tripDao())
        settingsRepository = SettingsRepository(application)
        tripOtpManager = com.example.data.preferences.TripOtpManager(application)

        allTrips = tripRepository.allTrips
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allVendors = tripRepository.allVendors
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                listOf(VendorLogoRenderer.defaultGetTaxiProfile())
            )

        activeVendors = tripRepository.activeVendors
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                listOf(VendorLogoRenderer.defaultGetTaxiProfile())
            )

        checkForActiveBackup()
        loadDriverProfile()
        connectivityMonitor.start()
    }

    override fun onCleared() {
        super.onCleared()
        connectivityMonitor.stop()
    }

    fun selectMeterMode(mode: String) {
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) return
        _selectedMeterMode.value = if (mode == SangamTariffConfig.METER_MODE_DIRECT) {
            SangamTariffConfig.METER_MODE_DIRECT
        } else {
            SangamTariffConfig.METER_MODE_OTP
        }
    }

    fun selectDirectCategory(category: SangamTariffConfig.DirectMeterCategory) {
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) return
        _selectedDirectCategory.value = category
    }

    fun setUse100KmPackage(enabled: Boolean) {
        val category = _selectedVehicleCategory.value
        val hasPackage = SangamTariffConfig.hourlyTariffFor(category).package100KmFare != null
        val effective = enabled && hasPackage
        _use100KmPackage.value = effective
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) {
            LocationTrackingService.updateActiveModifiers(use100KmPackage = effective)
        }
    }

    fun selectVehicleCategory(category: VehicleCategory) {
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) return
        _selectedVehicleCategory.value = category
        val supported = category.supportedTripTypes()
        if (_selectedSangamTripType.value !in supported) {
            setSangamTripType(supported.first())
        }
        if (category != VehicleCategory.INNOVA) {
            _isInnovaCrysta.value = false
        }
        val hourlyTariff = SangamTariffConfig.hourlyTariffFor(category)
        if (_packageHours.value < hourlyTariff.minHours) {
            _packageHours.value = hourlyTariff.minHours
        }
        if (hourlyTariff.package100KmFare == null) {
            _use100KmPackage.value = false
        }
        clearValidationMessage()
    }

    fun setSangamTripType(tripType: SangamTripType) {
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) return
        val category = _selectedVehicleCategory.value
        val validType = if (tripType in category.supportedTripTypes()) {
            tripType
        } else {
            category.supportedTripTypes().first()
        }
        _selectedSangamTripType.value = validType
        _selectedServiceType.value = when (validType) {
            SangamTripType.LOCAL_NORMAL, SangamTripType.LOCAL_MINIMUM_BILL -> ServiceType.LOCAL
            SangamTripType.HOURLY_RENTAL -> ServiceType.RENTAL
            SangamTripType.OUTSTATION_ONE_WAY, SangamTripType.OUTSTATION_ROUND_TRIP, SangamTripType.DAY_RENTAL -> ServiceType.OUTSTATION
        }
        clearValidationMessage()
    }

    fun setServiceType(type: ServiceType) {
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) return
        _selectedServiceType.value = type
        val mapped = when (type) {
            ServiceType.LOCAL -> SangamTripType.LOCAL_NORMAL
            ServiceType.RENTAL -> SangamTripType.HOURLY_RENTAL
            ServiceType.OUTSTATION -> SangamTripType.OUTSTATION_ONE_WAY
        }
        if (mapped in _selectedVehicleCategory.value.supportedTripTypes()) {
            _selectedSangamTripType.value = mapped
        }
        clearValidationMessage()
    }

    fun setInnovaCrysta(enabled: Boolean) {
        if (tripState.value.status == TripStatus.RUNNING || tripState.value.status == TripStatus.PAUSED) return
        _isInnovaCrysta.value = enabled
    }

    fun setOutsideCity(enabled: Boolean) {
        _isOutsideCity.value = enabled
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) {
            LocationTrackingService.updateActiveModifiers(isOutsideCity = enabled)
        }
    }

    fun setNightTrip(enabled: Boolean) {
        _isNightTrip.value = enabled
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) {
            LocationTrackingService.updateActiveModifiers(isNightTrip = enabled)
        }
    }

    fun setPackageHours(hours: Int) {
        val clamped = hours.coerceIn(1, 12)
        _packageHours.value = clamped
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) {
            LocationTrackingService.updateActiveModifiers(packageHours = clamped)
        }
    }

    fun setOver12Hours(enabled: Boolean) {
        _over12Hours.value = enabled
        val status = tripState.value.status
        if (status == TripStatus.RUNNING || status == TripStatus.PAUSED) {
            LocationTrackingService.updateActiveModifiers(over12Hours = enabled)
        }
    }

    fun showMeterDetails() {
        _activeTripDisplayMode.value = ActiveTripDisplayMode.METER_DETAILS
    }

    fun showMapDisplay() {
        _activeTripDisplayMode.value = ActiveTripDisplayMode.MAP_FIRST
    }

    fun clearValidationMessage() {
        _tariffValidationMessage.value = null
    }

    fun loadDriverProfile() {
        viewModelScope.launch {
            _driverProfile.value = tripRepository.getDriverProfile()
        }
    }

    fun saveDriverProfile(profile: DriverProfileEntity, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            tripRepository.saveDriverProfile(profile)
            _driverProfile.value = profile
            onSaved()
        }
    }

    fun checkForActiveBackup() {
        viewModelScope.launch {
            val backup = tripRepository.getActiveTrip()
            val liveStatus = tripState.value.status
            _hasActiveSessionBackup.value = backup != null && liveStatus == TripStatus.IDLE
            if (backup != null) {
                _selectedMeterMode.value = backup.meterMode.ifBlank { SangamTariffConfig.METER_MODE_OTP }
                _selectedDirectCategory.value = SangamTariffConfig.DirectMeterCategory.fromId(backup.directCategoryId)
                _selectedServiceType.value = ServiceType.fromId(backup.serviceType)
                _selectedVehicleCategory.value = VehicleCategory.fromId(backup.vehicleCategoryId)
                _selectedSangamTripType.value = SangamTripType.fromId(backup.sangamTripTypeId)
                _isInnovaCrysta.value = backup.isInnovaCrysta
                _isOutsideCity.value = backup.isOutOfCity
                _isNightTrip.value = backup.isNightTrip
                _over12Hours.value = backup.over12Hours
                _packageHours.value = backup.packageHours.coerceIn(1, 12)
                _use100KmPackage.value = backup.use100KmPackage
                // Seamless active-trip recovery if process restarted while a trip was running/paused
                if (liveStatus == TripStatus.IDLE && backup.startTime > 0L) {
                    Log.d("MeterViewModel", "Auto-recovering interrupted active trip ${backup.tripNumber} (${backup.distanceKm} km)")
                    recoverTrip()
                }
            }
            Log.d("MeterViewModel", "Checked active session backup: ${backup != null}")
        }
    }

    fun startTrip() {
        _activeTripDisplayMode.value = ActiveTripDisplayMode.METER_DETAILS
        _manualSettlement.value = null

        viewModelScope.launch {
            val category = _selectedVehicleCategory.value
            val tripType = _selectedSangamTripType.value
            val isCrysta = _isInnovaCrysta.value && category == VehicleCategory.INNOVA
            val outside = _isOutsideCity.value
            val night = _isNightTrip.value
            val hourlyTariff = SangamTariffConfig.hourlyTariffFor(category)
            val use100Km = _use100KmPackage.value &&
                tripType == SangamTripType.HOURLY_RENTAL &&
                hourlyTariff.package100KmFare != null
            val pkgHrs = if (tripType == SangamTripType.HOURLY_RENTAL) {
                if (use100Km) hourlyTariff.package100KmDutyHours else _packageHours.value.coerceIn(hourlyTariff.minHours, 12)
            } else {
                _packageHours.value.coerceIn(1, 12)
            }
            val over12 = _over12Hours.value

            val currencyVal = settingsRepository.currency.first()
            val thresholdVal = settingsRepository.speedThreshold.first()
            val calFactor = settingsRepository.distanceCalibrationFactor.first()

            val localRate = SangamTariffConfig.localNormalRates[category]
            val outRate = SangamTariffConfig.outstationRates[category]
            val dayRate = if (isCrysta) SangamTariffConfig.innovaCrystaDayRentalRate else SangamTariffConfig.dayRentalRates[category]

            val baseVal = when (tripType) {
                SangamTripType.LOCAL_NORMAL, SangamTripType.LOCAL_MINIMUM_BILL -> localRate?.initialCharge?.toDouble() ?: 80.0
                SangamTripType.HOURLY_RENTAL -> {
                    if (use100Km && hourlyTariff.package100KmFare != null) {
                        hourlyTariff.package100KmFare.toDouble()
                    } else {
                        hourlyTariff.hourlyMeterRate.multiply(java.math.BigDecimal(pkgHrs)).toDouble()
                    }
                }
                SangamTripType.OUTSTATION_ONE_WAY -> outRate?.oneWayInitialCharge?.toDouble() ?: 300.0
                SangamTripType.OUTSTATION_ROUND_TRIP -> outRate?.roundTripInitialCharge?.toDouble() ?: 500.0
                SangamTripType.DAY_RENTAL -> dayRate?.baseAmount?.toDouble() ?: 2000.0
            }
            val perKmVal = when (tripType) {
                SangamTripType.LOCAL_NORMAL, SangamTripType.LOCAL_MINIMUM_BILL -> localRate?.perKmRate?.toDouble() ?: 28.0
                SangamTripType.HOURLY_RENTAL -> hourlyTariff.extraKmRate.toDouble()
                SangamTripType.OUTSTATION_ONE_WAY -> outRate?.oneWayPerKm?.toDouble() ?: 30.0
                SangamTripType.OUTSTATION_ROUND_TRIP -> outRate?.roundTripPerKm?.toDouble() ?: 15.0
                SangamTripType.DAY_RENTAL -> dayRate?.perKmRate?.toDouble() ?: 10.0
            }

            val mappedServiceType = when (tripType) {
                SangamTripType.LOCAL_NORMAL, SangamTripType.LOCAL_MINIMUM_BILL -> ServiceType.LOCAL.id
                SangamTripType.HOURLY_RENTAL -> ServiceType.RENTAL.id
                SangamTripType.OUTSTATION_ONE_WAY, SangamTripType.OUTSTATION_ROUND_TRIP, SangamTripType.DAY_RENTAL -> ServiceType.OUTSTATION.id
            }

            _tariffValidationMessage.value = null

            val waitRateVal = if (
                tripType == SangamTripType.LOCAL_NORMAL ||
                tripType == SangamTripType.LOCAL_MINIMUM_BILL
            ) {
                SangamTariffConfig.localWaitingRatePerMin.toDouble()
            } else {
                0.0
            }

            LocationTrackingService.startTrip(
                context = getApplication(),
                baseFare = baseVal,
                farePerKm = perKmVal,
                waitFarePerMin = waitRateVal,
                currency = currencyVal,
                speedThreshold = thresholdVal,
                serviceType = mappedServiceType,
                vehicleCategoryId = category.id,
                sangamTripTypeId = tripType.id,
                isInnovaCrysta = isCrysta,
                isOutOfCity = outside,
                isNightTrip = night,
                packageHours = pkgHrs,
                use100KmPackage = use100Km,
                over12Hours = over12,
                meterMode = SangamTariffConfig.METER_MODE_OTP,
                rentalHourlyRate = hourlyTariff.hourlyMeterRate.toDouble(),
                rentalIncludedKmPerHour = SangamTariffConfig.hourlyIncludedKmPerHour.toDouble(),
                rentalMinHours = hourlyTariff.minHours.toDouble(),
                rentalExtraHrRate = 0.0,
                rentalExtraKmRate = perKmVal,
                outstationRatePerKm = perKmVal,
                outstationMinFare = 0.0,
                outstationWaitRate = 0.0,
                outstationDriverBata = if (over12 && (tripType == SangamTripType.OUTSTATION_ONE_WAY || tripType == SangamTripType.OUTSTATION_ROUND_TRIP)) {
                    SangamTariffConfig.outstationOver12HoursDriverAllowance.toDouble()
                } else {
                    0.0
                },
                outstationAdditionalCharges = 0.0,
                calibrationFactor = calFactor,
                vendorId = SangamTariffConfig.VENDOR_ID,
                vendorName = SangamTariffConfig.VENDOR_NAME,
                vendorContactNumber = ""
            )
            _hasActiveSessionBackup.value = false
        }
    }

    /**
     * Starts an independent Direct Meter trip (NO OTP, NO vehicle selection, NO vendor CC/commission).
     * Locks an immutable snapshot of the user's Direct Meter Settings at trip start.
     */
    fun startDirectMeterTrip(directCategory: SangamTariffConfig.DirectMeterCategory = _selectedDirectCategory.value) {
        _selectedMeterMode.value = SangamTariffConfig.METER_MODE_DIRECT
        _selectedDirectCategory.value = directCategory
        _activeTripDisplayMode.value = ActiveTripDisplayMode.METER_DETAILS
        _manualSettlement.value = null

        viewModelScope.launch {
            val currencyVal = settingsRepository.currency.first()
            val thresholdVal = settingsRepository.speedThreshold.first()
            val calFactor = settingsRepository.distanceCalibrationFactor.first()
            val directSnapshot = settingsRepository.directMeterTariffSnapshot.first()
            val snapshotJson = directSnapshot.toJson()

            val (mappedServiceType, baseVal, perKmVal, waitRateVal) = when (directCategory) {
                SangamTariffConfig.DirectMeterCategory.LOCAL -> listOf(
                    ServiceType.LOCAL.id,
                    directSnapshot.localBaseFare,
                    directSnapshot.localPerKmRate,
                    directSnapshot.waitingChargePerMin
                )
                SangamTariffConfig.DirectMeterCategory.HOURLY_RENTAL -> listOf(
                    ServiceType.RENTAL.id,
                    directSnapshot.hourlyBaseFare,
                    directSnapshot.hourlyExtraKmRate,
                    0.0
                )
                SangamTariffConfig.DirectMeterCategory.OUTSTATION -> listOf(
                    ServiceType.OUTSTATION.id,
                    directSnapshot.outstationBaseFare,
                    directSnapshot.outstationPerKmRate,
                    directSnapshot.waitingChargePerMin
                )
            }

            LocationTrackingService.startTrip(
                context = getApplication(),
                baseFare = baseVal as Double,
                farePerKm = perKmVal as Double,
                waitFarePerMin = waitRateVal as Double,
                currency = currencyVal,
                speedThreshold = thresholdVal,
                serviceType = mappedServiceType as String,
                vehicleCategoryId = VehicleCategory.MINI.id,
                sangamTripTypeId = when (directCategory) {
                    SangamTariffConfig.DirectMeterCategory.LOCAL -> SangamTripType.LOCAL_NORMAL.id
                    SangamTariffConfig.DirectMeterCategory.HOURLY_RENTAL -> SangamTripType.HOURLY_RENTAL.id
                    SangamTariffConfig.DirectMeterCategory.OUTSTATION -> SangamTripType.OUTSTATION_ONE_WAY.id
                },
                meterMode = SangamTariffConfig.METER_MODE_DIRECT,
                directCategoryId = directCategory.id,
                directTariffSnapshotJson = snapshotJson,
                rentalHourlyRate = directSnapshot.hourlyBaseFare,
                rentalIncludedKmPerHour = directSnapshot.hourlyIncludedKm,
                rentalMinHours = 1.0,
                rentalExtraKmRate = directSnapshot.hourlyExtraKmRate,
                outstationRatePerKm = directSnapshot.outstationPerKmRate,
                calibrationFactor = calFactor,
                vendorId = SangamTariffConfig.VENDOR_ID,
                vendorName = SangamTariffConfig.VENDOR_NAME,
                vendorContactNumber = ""
            )
            _hasActiveSessionBackup.value = false
        }
    }

    fun applyVerifiedGapAdjustment(additionalKm: Double) {
        LocationTrackingService.applyVerifiedGapAdjustment(getApplication(), additionalKm)
    }

    fun dismissGapWarning() {
        LocationTrackingService.dismissGapWarning(getApplication())
    }

    fun pauseTrip() {
        LocationTrackingService.pauseTrip(getApplication())
    }

    fun resumeTrip() {
        LocationTrackingService.resumeTrip(getApplication())
    }

    fun addExtraCharge(label: String, amount: Double) {
        LocationTrackingService.addExtraCharge(getApplication(), label, amount)
    }

    fun removeExtraCharge(chargeId: String) {
        LocationTrackingService.removeExtraCharge(getApplication(), chargeId)
    }

    fun clearExtraCharges() {
        LocationTrackingService.clearExtraCharges(getApplication())
    }

    /**
     * Completes an active GPS trip, persists its immutable financial snapshot into local Room
     * storage before clearing active session data, and transitions to Settlement.
     */
    fun completeTripForSettlement(onReady: () -> Unit) {
        val currentState = tripState.value
        val fallbackInput = LocationTrackingService.buildSangamInput(currentState)
        val fallbackBreakdown = LocationTrackingService.calculateBreakdownForState(currentState)

        LocationTrackingService.stopTrip(getApplication())
        _activeTripDisplayMode.value = ActiveTripDisplayMode.MAP_FIRST

        viewModelScope.launch {
            // Wait briefly for service coroutine to persist and publish completed trip
            repeat(12) {
                if (LocationTrackingService.lastCompletedTrip.value != null) {
                    _hasActiveSessionBackup.value = LocationTrackingService.lastCompletedTrip.value?.saveError != null
                    onReady()
                    return@launch
                }
                delay(40)
            }
            // Fallback build & local Room save if service didn't publish in time
            if (LocationTrackingService.lastCompletedTrip.value == null) {
                buildAndPersistSettlement(
                    input = fallbackInput,
                    breakdown = fallbackBreakdown,
                    distanceKm = currentState.distanceKm,
                    durationSeconds = currentState.durationSeconds,
                    waitingSeconds = currentState.waitingSeconds,
                    pickup = currentState.pickupAddress.ifBlank { "GPS Location" },
                    drop = currentState.dropAddress.ifBlank { "GPS Location" },
                    extraItems = currentState.extraChargeItems,
                    existingTripNumber = currentState.tripNumber,
                    meterMode = currentState.meterMode,
                    directCategoryId = currentState.directCategoryId,
                    directTariffSnapshotJson = currentState.directTariffSnapshotJson
                )
            }
            onReady()
        }
    }

    /**
     * Calculates an instant fare & settlement for parked or manual billing.
     * Note: Outside City Limits and Night Allowance are manual-amount-only (never auto-charged).
     */
    fun calculateQuickSettlement(
        input: SangamTariffCalculator.TripCalculationInput,
        pickupAddress: String = "Local Trip",
        dropAddress: String = "Drop Point",
        durationMinutes: Long = 0L,
        onReady: () -> Unit
    ) {
        viewModelScope.launch {
            val breakdown = SangamTariffCalculator.calculateTripFareAndSettlement(input)
            val extras = mutableListOf<ExtraChargeItem>()
            if (input.outsideCityCharge > 0.0) {
                extras.add(ExtraChargeItem(label = SangamTariffConfig.EXTRA_LABEL_OUTSIDE_CITY, amount = input.outsideCityCharge))
            }
            if (input.nightAllowanceCharge > 0.0) {
                extras.add(ExtraChargeItem(label = SangamTariffConfig.EXTRA_LABEL_NIGHT_ALLOWANCE, amount = input.nightAllowanceCharge))
            }
            if (input.tollCharge > 0.0) extras.add(ExtraChargeItem(label = "Toll", amount = input.tollCharge))
            if (input.permitCharge > 0.0) extras.add(ExtraChargeItem(label = "Permit", amount = input.permitCharge))
            if (input.parkingCharge > 0.0) extras.add(ExtraChargeItem(label = "Parking", amount = input.parkingCharge))
            if (input.extraChargeItems.isNotEmpty()) {
                extras.addAll(input.extraChargeItems.filter { it.amount > 0.0 })
            } else if (input.customExtrasTotal > 0.0) {
                extras.add(ExtraChargeItem(label = "Extra Charges", amount = input.customExtrasTotal))
            }

            val durationSec = when {
                durationMinutes > 0L -> durationMinutes * 60L
                input.tripType == SangamTripType.HOURLY_RENTAL -> input.packageHours * 3600L
                else -> 0L
            }

            buildAndPersistSettlement(
                input = input,
                breakdown = breakdown,
                distanceKm = input.distanceKm,
                durationSeconds = durationSec,
                waitingSeconds = (input.waitingMinutes * 60.0).toLong(),
                pickup = pickupAddress.ifBlank { "Parked Fare Entry" },
                drop = dropAddress.ifBlank { "Destination" },
                extraItems = extras
            )
            onReady()
        }
    }

    private suspend fun buildAndPersistSettlement(
        input: SangamTariffCalculator.TripCalculationInput,
        breakdown: SangamTariffCalculator.SangamSettlementBreakdown,
        distanceKm: Double,
        durationSeconds: Long,
        waitingSeconds: Long,
        pickup: String,
        drop: String,
        extraItems: List<ExtraChargeItem>,
        existingTripNumber: String = "",
        meterMode: String = SangamTariffConfig.METER_MODE_OTP,
        directCategoryId: String = SangamTariffConfig.DirectMeterCategory.LOCAL.id,
        directTariffSnapshotJson: String = ""
    ) {
        val now = System.currentTimeMillis()
        val profile = tripRepository.getDriverProfile()
        val tripNumber = existingTripNumber.ifBlank {
            tripRepository.generateDeterministicTripNumber(now)
        }
        val dateText = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(now))
        val timeText = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))

        val isDirect = meterMode == SangamTariffConfig.METER_MODE_DIRECT || breakdown.isDirectMeter
        val mappedMode = if (isDirect) {
            when (SangamTariffConfig.DirectMeterCategory.fromId(directCategoryId)) {
                SangamTariffConfig.DirectMeterCategory.LOCAL -> ServiceType.LOCAL.id
                SangamTariffConfig.DirectMeterCategory.HOURLY_RENTAL -> ServiceType.RENTAL.id
                SangamTariffConfig.DirectMeterCategory.OUTSTATION -> ServiceType.OUTSTATION.id
            }
        } else {
            when (input.tripType) {
                SangamTripType.LOCAL_NORMAL, SangamTripType.LOCAL_MINIMUM_BILL -> ServiceType.LOCAL.id
                SangamTripType.HOURLY_RENTAL -> ServiceType.RENTAL.id
                SangamTripType.OUTSTATION_ONE_WAY, SangamTripType.OUTSTATION_ROUND_TRIP, SangamTripType.DAY_RENTAL -> ServiceType.OUTSTATION.id
            }
        }

        val vehicleDisplay = if (isDirect) {
            "Direct Meter • ${SangamTariffConfig.DirectMeterCategory.fromId(directCategoryId).displayName}"
        } else if (breakdown.isInnovaCrysta) {
            "INNOVA CRYSTA"
        } else {
            breakdown.vehicleCategory.displayName
        }
        val extrasTotal = ExtraChargeItem.totalAmount(extraItems)

        val baseEntity = TripEntity(
            id = 0,
            tripNumber = tripNumber,
            startTime = now - (durationSeconds * 1000L),
            endTime = now,
            distanceKm = distanceKm,
            durationSeconds = durationSeconds,
            waitingSeconds = waitingSeconds,
            totalFare = breakdown.customerBillTotal.toDouble(),
            startLatitude = 0.0,
            startLongitude = 0.0,
            endLatitude = 0.0,
            endLongitude = 0.0,
            driverName = profile?.driverName ?: "",
            driverMobile = profile?.driverMobile ?: "",
            vehicleNumber = profile?.vehicleNumber ?: "",
            vehicleModel = profile?.vehicleModel ?: "",
            vehicleType = vehicleDisplay,
            pickupAddress = pickup,
            dropAddress = drop,
            isOutOfCity = input.isOutsideCity,
            outOfCitySurcharge = 0.0,
            baseFare = breakdown.lineItems.firstOrNull()?.amount?.toDouble() ?: 0.0,
            farePerKm = 0.0,
            waitFarePerMin = if (
                input.tripType == SangamTripType.LOCAL_NORMAL ||
                input.tripType == SangamTripType.LOCAL_MINIMUM_BILL
            ) {
                SangamTariffConfig.localWaitingRatePerMin.toDouble()
            } else {
                0.0
            },
            currency = SangamTariffConfig.CURRENCY_SYMBOL,
            serviceType = mappedMode,
            rentalBaseHours = input.packageHours.toDouble(),
            rentalBaseKm = SangamTariffConfig.hourlyIncludedKmPerHour.toDouble(),
            rentalBaseFare = SangamTariffConfig.hourlyTariffFor(input.vehicleCategory).hourlyMeterRate
                .multiply(java.math.BigDecimal(input.packageHours.coerceAtLeast(1))).toDouble(),
            billableHours = input.packageHours.toLong(),
            outstationDriverBata = if (input.over12Hours) SangamTariffConfig.outstationOver12HoursDriverAllowance.toDouble() else 0.0,
            outstationAdditionalCharges = extrasTotal,
            extraChargesJson = ExtraChargeItem.serialize(extraItems),
            invoiceTimestamp = now,
            invoiceDateText = dateText,
            invoiceTimeText = timeText,
            vendorId = SangamTariffConfig.VENDOR_ID,
            vendorName = SangamTariffConfig.VENDOR_NAME,
            vendorLogoAsset = "drawable/get_taxi_header_logo",
            vendorContactNumber = "",
            vendorBusinessAddress = "No. 286, Diwan Bahadur Road, R.S. Puram, Coimbatore",
            vendorInvoiceDetails = "",
            vendorInvoiceNumber = tripNumber,
            meterMode = if (isDirect) SangamTariffConfig.METER_MODE_DIRECT else SangamTariffConfig.METER_MODE_OTP,
            directCategoryId = directCategoryId,
            directTariffSnapshotJson = directTariffSnapshotJson,
            use100KmPackage = input.use100KmPackage,
            syncStatus = "PENDING_SYNC"
        )

        val snapshotEntity = com.example.util.TripSnapshotMapper.attachFinancialSnapshot(
            baseEntity = baseEntity,
            input = input,
            breakdown = breakdown,
            driverProfile = profile
        )

        // Transactional save-before-clear: save to Room first, ONLY clear active_trip on confirmed save.
        try {
            val savedId = tripRepository.saveCompletedTripIdempotent(snapshotEntity)
            if (savedId > 0L) {
                val persisted = snapshotEntity.copy(id = savedId.toInt())
                _manualSettlement.value = LocationTrackingService.TransientCompletedTrip(
                    tripEntity = persisted,
                    calculationInput = input,
                    breakdown = breakdown,
                    persistedTripId = savedId,
                    saveError = null
                )
                tripRepository.clearActiveTrip()
                _hasActiveSessionBackup.value = false
            } else {
                _manualSettlement.value = LocationTrackingService.TransientCompletedTrip(
                    tripEntity = snapshotEntity,
                    calculationInput = input,
                    breakdown = breakdown,
                    persistedTripId = null,
                    saveError = "Local database returned invalid trip ID. Tap Retry Save."
                )
            }
        } catch (e: Exception) {
            Log.e("MeterViewModel", "Failed to save completed trip $tripNumber locally", e)
            _manualSettlement.value = LocationTrackingService.TransientCompletedTrip(
                tripEntity = snapshotEntity,
                calculationInput = input,
                breakdown = breakdown,
                persistedTripId = null,
                saveError = e.message ?: "Failed to save trip to local database. Tap Retry Save."
            )
        }
    }

    /**
     * Retries saving a completed trip whose initial Room persistence failed.
     */
    fun retrySaveCurrentSettlement() {
        val current = currentSettlement.value ?: return
        if (current.persistedTripId != null && current.saveError == null) return
        viewModelScope.launch {
            try {
                val savedId = tripRepository.saveCompletedTripIdempotent(current.tripEntity)
                if (savedId > 0L) {
                    val persisted = current.tripEntity.copy(id = savedId.toInt())
                    _manualSettlement.value = current.copy(
                        tripEntity = persisted,
                        persistedTripId = savedId,
                        saveError = null
                    )
                    tripRepository.clearActiveTrip()
                    _hasActiveSessionBackup.value = false
                } else {
                    _manualSettlement.value = current.copy(
                        saveError = "Could not confirm local save. Please try again."
                    )
                }
            } catch (e: Exception) {
                _manualSettlement.value = current.copy(
                    saveError = e.message ?: "Retry save failed. Trip data is still preserved."
                )
            }
        }
    }

    fun updateSettlementCustomerInfo(name: String, mobile: String, address: String) {
        val current = currentSettlement.value ?: return
        val cleanName = name.trim()
        val cleanMobile = mobile.trim()
        val cleanAddress = address.trim()
        val updatedEntity = current.tripEntity.copy(
            customerName = cleanName,
            customerMobile = cleanMobile,
            customerAddress = cleanAddress
        )
        _manualSettlement.value = current.copy(tripEntity = updatedEntity)
        if (updatedEntity.id > 0) {
            viewModelScope.launch {
                try {
                    tripRepository.updateTripCustomerInfo(updatedEntity.id, cleanName, cleanMobile, cleanAddress)
                } catch (e: Exception) {
                    Log.e("MeterViewModel", "Failed updating customer info for trip ${updatedEntity.id}", e)
                }
            }
        }
    }

    fun markCurrentSettlementCcConfirmed() {
        val current = currentSettlement.value ?: return
        val updatedEntity = current.tripEntity.copy(
            settlementStatus = com.example.util.TripSnapshotMapper.SETTLEMENT_CONFIRMED
        )
        _manualSettlement.value = current.copy(tripEntity = updatedEntity)
        if (updatedEntity.id > 0) {
            markTripSettlementStatus(updatedEntity.id, com.example.util.TripSnapshotMapper.SETTLEMENT_CONFIRMED)
        }
    }

    fun markTripSettlementStatus(tripId: Int, status: String) {
        if (tripId <= 0) return
        viewModelScope.launch {
            try {
                tripRepository.updateTripSettlementStatus(tripId, status)
            } catch (e: Exception) {
                Log.e("MeterViewModel", "Failed updating settlement status for trip $tripId", e)
            }
        }
    }

    /**
     * Manually sets or updates the variable Day Rental Company Commission (CC) for a trip.
     * Persists CC & recomputed Driver Net Balance in Room WITHOUT altering Customer Invoice fare or line items.
     */
    fun updateManualDayRentalCc(tripId: Int, manualCcAmount: Double) {
        if (manualCcAmount < 0.0 || manualCcAmount.isNaN() || manualCcAmount.isInfinite()) return
        val ccBd = java.math.BigDecimal.valueOf(manualCcAmount)
            .setScale(2, java.math.RoundingMode.HALF_UP)
        val ruleLabel = "Day Rental CC (Manual)"

        val current = currentSettlement.value
        if (current != null && (tripId <= 0 || current.tripEntity.id == tripId)) {
            val customerTotalBd = current.breakdown.customerBillTotal
                .setScale(2, java.math.RoundingMode.HALF_UP)
            val netDriverBd = customerTotalBd.subtract(ccBd)
                .coerceAtLeast(java.math.BigDecimal.ZERO)
                .setScale(2, java.math.RoundingMode.HALF_UP)

            val updatedBreakdown = current.breakdown.copy(
                commissionResult = SangamTariffCalculator.CommissionResult.Confirmed(
                    ruleLabel = ruleLabel,
                    amount = ccBd
                ),
                driverBalanceStatus = SangamTariffCalculator.DriverBalanceStatus.Confirmed(netDriverBd)
            )
            val updatedEntity = current.tripEntity.copy(
                ccStatus = com.example.util.TripSnapshotMapper.CC_STATUS_CONFIRMED,
                ccRuleLabel = ruleLabel,
                ccAmount = ccBd.toDouble(),
                ccMinAmount = ccBd.toDouble(),
                ccMaxAmount = ccBd.toDouble(),
                driverNetBalance = netDriverBd.toDouble(),
                driverBalanceMin = netDriverBd.toDouble(),
                driverBalanceMax = netDriverBd.toDouble()
            )
            _manualSettlement.value = current.copy(
                tripEntity = updatedEntity,
                breakdown = updatedBreakdown
            )
        }

        if (tripId > 0) {
            viewModelScope.launch {
                try {
                    val savedTrip = tripRepository.getTripById(tripId) ?: return@launch
                    val customerTotalBd = java.math.BigDecimal.valueOf(savedTrip.totalFare)
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                    val netDriverBd = customerTotalBd.subtract(ccBd)
                        .coerceAtLeast(java.math.BigDecimal.ZERO)
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                    tripRepository.updateTripManualCc(
                        tripId = tripId,
                        ccStatus = com.example.util.TripSnapshotMapper.CC_STATUS_CONFIRMED,
                        ccRuleLabel = ruleLabel,
                        ccAmount = ccBd.toDouble(),
                        driverNetBalance = netDriverBd.toDouble()
                    )
                } catch (e: Exception) {
                    Log.e("MeterViewModel", "Failed updating manual Day Rental CC for trip $tripId", e)
                }
            }
        }
    }

    fun getHistoricalTripFlow(tripId: Int): kotlinx.coroutines.flow.Flow<TripEntity?> {
        return tripRepository.getTripByIdFlow(tripId)
    }

    suspend fun getHistoricalTripById(tripId: Int): TripEntity? {
        return tripRepository.getTripById(tripId)
    }

    /**
     * Clears active settlement screen state and resets meter/modifiers for the next trip.
     * NEVER deletes any saved completed trip from the local Room `trips` history table.
     */
    fun endTripAndClearSettlement() {
        val pendingSaveError = currentSettlement.value?.saveError != null && currentSettlement.value?.persistedTripId == null
        _manualSettlement.value = null
        LocationTrackingService.clearLastCompletedTrip()
        _isOutsideCity.value = false
        _isNightTrip.value = false
        _over12Hours.value = false
        _packageHours.value = 1
        _use100KmPackage.value = false
        _activeTripDisplayMode.value = ActiveTripDisplayMode.MAP_FIRST
        if (!pendingSaveError) {
            viewModelScope.launch {
                tripRepository.clearActiveTrip()
                _hasActiveSessionBackup.value = false
            }
        }
    }

    fun recoverTrip() {
        _activeTripDisplayMode.value = ActiveTripDisplayMode.MAP_FIRST
        LocationTrackingService.recoveryTrip(getApplication())
        _hasActiveSessionBackup.value = false
    }

    fun discardBackup() {
        viewModelScope.launch {
            tripRepository.clearActiveTrip()
            _hasActiveSessionBackup.value = false
        }
    }

    /**
     * Validates one of the 8 fixed offline start OTP codes before starting a trip via START WITH OTP.
     */
    suspend fun validateStartWithOtp(otpInput: String): com.example.data.preferences.OtpValidationResult {
        return tripOtpManager.validateFixedStartOtp(otpInput)
    }
}
