package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "taxi_meter_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        // Standard Refilled Default Values:
        // Local: 100 Base, 30 / km, 2 / min wait
        const val DEFAULT_BASE_FARE = 100.0
        const val DEFAULT_FARE_PER_KM = 30.0
        const val DEFAULT_WAIT_FARE_PER_MIN = 2.0
        const val DEFAULT_SPEED_THRESHOLD = 5.0
        const val DEFAULT_AUDIO_ENABLED = true
        const val DEFAULT_CURRENCY = "₹"

        // Hourly Rental: 350 / hr, 10 km included per hour, 20 / km extra
        const val DEFAULT_RENTAL_HOURLY_RATE = 350.0
        const val DEFAULT_RENTAL_INCLUDED_KM_PER_HOUR = 10.0
        const val DEFAULT_RENTAL_MIN_HOURS = 1.0
        const val DEFAULT_RENTAL_EXTRA_HR_RATE = 350.0
        const val DEFAULT_RENTAL_EXTRA_KM_RATE = 20.0

        // Outstation: 30 / km, 300 Driver Bata
        const val DEFAULT_OUTSTATION_FARE_PER_KM = 30.0
        const val DEFAULT_OUTSTATION_DRIVER_BATA = 300.0
        const val DEFAULT_OUTSTATION_MIN_FARE = 0.0
        const val DEFAULT_OUTSTATION_WAIT_RATE = 0.0
        const val DEFAULT_OUTSTATION_ADDITIONAL_CHARGES = 0.0

        // Local / Metered Tariff Keys
        val KEY_BASE_FARE = doublePreferencesKey("base_fare")
        val KEY_FARE_PER_KM = doublePreferencesKey("fare_per_km")
        val KEY_WAIT_FARE_PER_MIN = doublePreferencesKey("wait_fare_per_min")
        val KEY_SPEED_THRESHOLD = doublePreferencesKey("speed_threshold") // in km/h
        val KEY_AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val KEY_CURRENCY = stringPreferencesKey("currency")

        // Hourly Rental Tariff Keys
        val KEY_RENTAL_HOURLY_RATE = doublePreferencesKey("rental_hourly_rate")
        val KEY_RENTAL_INCLUDED_KM_PER_HOUR = doublePreferencesKey("rental_included_km_per_hour")
        val KEY_RENTAL_MIN_HOURS = doublePreferencesKey("rental_min_hours")
        val KEY_RENTAL_EXTRA_HR_RATE = doublePreferencesKey("rental_extra_hr_rate")
        val KEY_RENTAL_EXTRA_KM_RATE = doublePreferencesKey("rental_extra_km_rate")

        // Outstation Tariff Keys
        val KEY_OUTSTATION_FARE_PER_KM = doublePreferencesKey("outstation_fare_per_km")
        val KEY_OUTSTATION_MIN_FARE = doublePreferencesKey("outstation_min_fare")
        val KEY_OUTSTATION_WAIT_RATE = doublePreferencesKey("outstation_wait_rate")
        val KEY_OUTSTATION_DRIVER_BATA = doublePreferencesKey("outstation_driver_bata")
        val KEY_OUTSTATION_ADDITIONAL_CHARGES = doublePreferencesKey("outstation_additional_charges")

        // Distance Calibration Factor (Default 1.0 = no calibration)
        const val DEFAULT_DISTANCE_CALIBRATION_FACTOR = 1.0
        val KEY_DISTANCE_CALIBRATION_FACTOR = doublePreferencesKey("distance_calibration_factor")

        // Floating Speedometer Bubble Overlay (Default false = optional, never forced on drivers)
        const val DEFAULT_FLOATING_BUBBLE_ENABLED = false
        val KEY_FLOATING_BUBBLE_ENABLED = booleanPreferencesKey("floating_bubble_enabled")

        // Application Appearance Mode ("AUTO", "DAY", "NIGHT")
        const val APPEARANCE_AUTO = "AUTO"
        const val APPEARANCE_DAY = "DAY"
        const val APPEARANCE_NIGHT = "NIGHT"
        const val DEFAULT_APPEARANCE_MODE = APPEARANCE_AUTO
        val KEY_APPEARANCE_MODE = stringPreferencesKey("appearance_mode")

        // Independent Direct Meter Mode Default Tariffs (completely separate from OTP Meter tariffs)
        const val DEFAULT_DIRECT_LOCAL_BASE_FARE = 100.0
        const val DEFAULT_DIRECT_LOCAL_PER_KM_RATE = 30.0
        const val DEFAULT_DIRECT_HOURLY_BASE_FARE = 375.0
        const val DEFAULT_DIRECT_HOURLY_INCLUDED_KM = 10.0
        const val DEFAULT_DIRECT_HOURLY_EXTRA_KM_RATE = 20.0
        const val DEFAULT_DIRECT_OUTSTATION_BASE_FARE = 300.0
        const val DEFAULT_DIRECT_OUTSTATION_PER_KM_RATE = 30.0
        const val DEFAULT_DIRECT_WAITING_CHARGE_PER_MIN = 2.0

        val KEY_DIRECT_LOCAL_BASE_FARE = doublePreferencesKey("direct_local_base_fare")
        val KEY_DIRECT_LOCAL_PER_KM_RATE = doublePreferencesKey("direct_local_per_km_rate")
        val KEY_DIRECT_HOURLY_BASE_FARE = doublePreferencesKey("direct_hourly_base_fare")
        val KEY_DIRECT_HOURLY_INCLUDED_KM = doublePreferencesKey("direct_hourly_included_km")
        val KEY_DIRECT_HOURLY_EXTRA_KM_RATE = doublePreferencesKey("direct_hourly_extra_km_rate")
        val KEY_DIRECT_OUTSTATION_BASE_FARE = doublePreferencesKey("direct_outstation_base_fare")
        val KEY_DIRECT_OUTSTATION_PER_KM_RATE = doublePreferencesKey("direct_outstation_per_km_rate")
        val KEY_DIRECT_WAITING_CHARGE_PER_MIN = doublePreferencesKey("direct_waiting_charge_per_min")

        // Application Language (Default "en" = English, "ta" = Tamil)
        const val DEFAULT_APP_LANGUAGE = "en"
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

        // Voice Announcement Language (Default "en" = English, "ta" = Tamil)
        const val DEFAULT_VOICE_LANGUAGE = "en"
        val KEY_VOICE_LANGUAGE = stringPreferencesKey("voice_announcement_language")
    }

    init {
        com.example.util.AppLanguageManager.init(context)
    }

    // Application Language Flow
    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        val stored = preferences[KEY_APP_LANGUAGE]
        if (stored != null) {
            com.example.util.AppLanguageManager.setLanguageByCode(context, stored)
            stored
        } else {
            com.example.util.AppLanguageManager.getLanguage(context).code
        }
    }

    // Voice Announcement Language Flow
    val voiceAnnouncementLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_VOICE_LANGUAGE] ?: DEFAULT_VOICE_LANGUAGE
        if (raw == "ta") "ta" else "en"
    }

    // Floating Speedometer Bubble Flow (Optional, OFF by default)
    val floatingBubbleEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FLOATING_BUBBLE_ENABLED] ?: DEFAULT_FLOATING_BUBBLE_ENABLED
    }

    // Appearance Mode Flow ("AUTO", "DAY", "NIGHT")
    val appearanceMode: Flow<String> = context.dataStore.data.map { preferences ->
        when (preferences[KEY_APPEARANCE_MODE]) {
            APPEARANCE_DAY -> APPEARANCE_DAY
            APPEARANCE_NIGHT -> APPEARANCE_NIGHT
            else -> APPEARANCE_AUTO
        }
    }

    // Independent Direct Meter Tariff Flows
    val directLocalBaseFare: Flow<Double> = context.dataStore.data.map {
        it[KEY_DIRECT_LOCAL_BASE_FARE] ?: DEFAULT_DIRECT_LOCAL_BASE_FARE
    }
    val directLocalPerKmRate: Flow<Double> = context.dataStore.data.map {
        it[KEY_DIRECT_LOCAL_PER_KM_RATE] ?: DEFAULT_DIRECT_LOCAL_PER_KM_RATE
    }
    val directHourlyBaseFare: Flow<Double> = context.dataStore.data.map {
        it[KEY_DIRECT_HOURLY_BASE_FARE] ?: DEFAULT_DIRECT_HOURLY_BASE_FARE
    }
    val directHourlyIncludedKm: Flow<Double> = context.dataStore.data.map {
        it[KEY_DIRECT_HOURLY_INCLUDED_KM] ?: DEFAULT_DIRECT_HOURLY_INCLUDED_KM
    }
    val directHourlyExtraKmRate: Flow<Double> = context.dataStore.data.map {
        it[KEY_DIRECT_HOURLY_EXTRA_KM_RATE] ?: DEFAULT_DIRECT_HOURLY_EXTRA_KM_RATE
    }
    val directOutstationBaseFare: Flow<Double> = context.dataStore.data.map {
        it[KEY_DIRECT_OUTSTATION_BASE_FARE] ?: DEFAULT_DIRECT_OUTSTATION_BASE_FARE
    }
    val directOutstationPerKmRate: Flow<Double> = context.dataStore.data.map {
        it[KEY_DIRECT_OUTSTATION_PER_KM_RATE] ?: DEFAULT_DIRECT_OUTSTATION_PER_KM_RATE
    }
    val directWaitingChargePerMin: Flow<Double> = context.dataStore.data.map {
        it[KEY_DIRECT_WAITING_CHARGE_PER_MIN] ?: DEFAULT_DIRECT_WAITING_CHARGE_PER_MIN
    }

    val directMeterTariffSnapshot: Flow<com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot> =
        context.dataStore.data.map { prefs ->
            com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot(
                localBaseFare = prefs[KEY_DIRECT_LOCAL_BASE_FARE] ?: DEFAULT_DIRECT_LOCAL_BASE_FARE,
                localPerKmRate = prefs[KEY_DIRECT_LOCAL_PER_KM_RATE] ?: DEFAULT_DIRECT_LOCAL_PER_KM_RATE,
                hourlyBaseFare = prefs[KEY_DIRECT_HOURLY_BASE_FARE] ?: DEFAULT_DIRECT_HOURLY_BASE_FARE,
                hourlyIncludedKm = prefs[KEY_DIRECT_HOURLY_INCLUDED_KM] ?: DEFAULT_DIRECT_HOURLY_INCLUDED_KM,
                hourlyExtraKmRate = prefs[KEY_DIRECT_HOURLY_EXTRA_KM_RATE] ?: DEFAULT_DIRECT_HOURLY_EXTRA_KM_RATE,
                outstationBaseFare = prefs[KEY_DIRECT_OUTSTATION_BASE_FARE] ?: DEFAULT_DIRECT_OUTSTATION_BASE_FARE,
                outstationPerKmRate = prefs[KEY_DIRECT_OUTSTATION_PER_KM_RATE] ?: DEFAULT_DIRECT_OUTSTATION_PER_KM_RATE,
                waitingChargePerMin = prefs[KEY_DIRECT_WAITING_CHARGE_PER_MIN] ?: DEFAULT_DIRECT_WAITING_CHARGE_PER_MIN
            )
        }

    // Distance Calibration Flow
    val distanceCalibrationFactor: Flow<Double> = context.dataStore.data.map { preferences ->
        (preferences[KEY_DISTANCE_CALIBRATION_FACTOR] ?: DEFAULT_DISTANCE_CALIBRATION_FACTOR)
            .coerceIn(0.900, 1.150)
    }

    // Local / Metered Flows
    val baseFare: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_BASE_FARE] ?: DEFAULT_BASE_FARE
    }

    val farePerKm: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_FARE_PER_KM] ?: DEFAULT_FARE_PER_KM
    }

    val waitFarePerMin: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_WAIT_FARE_PER_MIN] ?: DEFAULT_WAIT_FARE_PER_MIN
    }

    val speedThreshold: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_SPEED_THRESHOLD] ?: DEFAULT_SPEED_THRESHOLD
    }

    val audioEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUDIO_ENABLED] ?: DEFAULT_AUDIO_ENABLED
    }

    val currency: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CURRENCY] ?: DEFAULT_CURRENCY
    }

    // Hourly Rental Flows
    val rentalHourlyRate: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_RENTAL_HOURLY_RATE] ?: DEFAULT_RENTAL_HOURLY_RATE
    }

    val rentalIncludedKmPerHour: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_RENTAL_INCLUDED_KM_PER_HOUR] ?: DEFAULT_RENTAL_INCLUDED_KM_PER_HOUR
    }

    val rentalMinHours: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_RENTAL_MIN_HOURS] ?: DEFAULT_RENTAL_MIN_HOURS
    }

    val rentalExtraHrRate: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_RENTAL_EXTRA_HR_RATE] ?: DEFAULT_RENTAL_EXTRA_HR_RATE
    }

    val rentalExtraKmRate: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_RENTAL_EXTRA_KM_RATE] ?: DEFAULT_RENTAL_EXTRA_KM_RATE
    }

    // Outstation Flows
    val outstationFarePerKm: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_OUTSTATION_FARE_PER_KM] ?: DEFAULT_OUTSTATION_FARE_PER_KM
    }

    val outstationMinFare: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_OUTSTATION_MIN_FARE] ?: DEFAULT_OUTSTATION_MIN_FARE
    }

    val outstationWaitRate: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_OUTSTATION_WAIT_RATE] ?: DEFAULT_OUTSTATION_WAIT_RATE
    }

    val outstationDriverBata: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_OUTSTATION_DRIVER_BATA] ?: DEFAULT_OUTSTATION_DRIVER_BATA
    }

    val outstationAdditionalCharges: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_OUTSTATION_ADDITIONAL_CHARGES] ?: DEFAULT_OUTSTATION_ADDITIONAL_CHARGES
    }

    // Tariff setters
    suspend fun updateBaseFare(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_BASE_FARE] = value else preferences.remove(KEY_BASE_FARE)
        }
    }

    suspend fun updateFarePerKm(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_FARE_PER_KM] = value else preferences.remove(KEY_FARE_PER_KM)
        }
    }

    suspend fun updateWaitFarePerMin(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_WAIT_FARE_PER_MIN] = value else preferences.remove(KEY_WAIT_FARE_PER_MIN)
        }
    }

    suspend fun updateSpeedThreshold(value: Double) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SPEED_THRESHOLD] = value
        }
    }

    suspend fun updateAudioEnabled(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUDIO_ENABLED] = value
        }
    }

    suspend fun updateFloatingBubbleEnabled(value: Boolean) {
        com.example.service.FloatingSpeedOverlayManager.setBubbleEnabled(context, value)
        context.dataStore.edit { preferences ->
            preferences[KEY_FLOATING_BUBBLE_ENABLED] = value
        }
    }

    suspend fun updateCurrency(value: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CURRENCY] = value
        }
    }

    suspend fun updateAppLanguage(languageCode: String) {
        val normalized = com.example.util.AppLanguage.fromCode(languageCode).code
        com.example.util.AppLanguageManager.setLanguageByCode(context, normalized)
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_LANGUAGE] = normalized
        }
    }

    suspend fun updateVoiceAnnouncementLanguage(languageCode: String) {
        val normalized = if (languageCode.trim().lowercase() == "ta") "ta" else "en"
        context.dataStore.edit { preferences ->
            preferences[KEY_VOICE_LANGUAGE] = normalized
        }
    }

    suspend fun updateRentalHourlyRate(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_RENTAL_HOURLY_RATE] = value else preferences.remove(KEY_RENTAL_HOURLY_RATE)
        }
    }

    suspend fun updateRentalIncludedKmPerHour(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) {
                preferences[KEY_RENTAL_INCLUDED_KM_PER_HOUR] = value
            } else {
                preferences.remove(KEY_RENTAL_INCLUDED_KM_PER_HOUR)
            }
        }
    }

    suspend fun updateRentalMinHours(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_RENTAL_MIN_HOURS] = value else preferences.remove(KEY_RENTAL_MIN_HOURS)
        }
    }

    suspend fun updateRentalExtraHrRate(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_RENTAL_EXTRA_HR_RATE] = value else preferences.remove(KEY_RENTAL_EXTRA_HR_RATE)
        }
    }

    suspend fun updateRentalExtraKmRate(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_RENTAL_EXTRA_KM_RATE] = value else preferences.remove(KEY_RENTAL_EXTRA_KM_RATE)
        }
    }

    suspend fun updateOutstationFarePerKm(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_OUTSTATION_FARE_PER_KM] = value else preferences.remove(KEY_OUTSTATION_FARE_PER_KM)
        }
    }

    suspend fun updateOutstationMinFare(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_OUTSTATION_MIN_FARE] = value else preferences.remove(KEY_OUTSTATION_MIN_FARE)
        }
    }

    suspend fun updateOutstationWaitRate(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_OUTSTATION_WAIT_RATE] = value else preferences.remove(KEY_OUTSTATION_WAIT_RATE)
        }
    }

    suspend fun updateOutstationDriverBata(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_OUTSTATION_DRIVER_BATA] = value else preferences.remove(KEY_OUTSTATION_DRIVER_BATA)
        }
    }

    suspend fun updateOutstationAdditionalCharges(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) preferences[KEY_OUTSTATION_ADDITIONAL_CHARGES] = value else preferences.remove(KEY_OUTSTATION_ADDITIONAL_CHARGES)
        }
    }

    suspend fun updateDistanceCalibrationFactor(value: Double?) {
        context.dataStore.edit { preferences ->
            if (value != null) {
                val clamped = value.coerceIn(0.900, 1.150)
                preferences[KEY_DISTANCE_CALIBRATION_FACTOR] = clamped
            } else {
                preferences.remove(KEY_DISTANCE_CALIBRATION_FACTOR)
            }
        }
    }

    suspend fun updateAppearanceMode(mode: String) {
        val normalized = when (mode.uppercase()) {
            APPEARANCE_DAY -> APPEARANCE_DAY
            APPEARANCE_NIGHT -> APPEARANCE_NIGHT
            else -> APPEARANCE_AUTO
        }
        context.dataStore.edit { preferences ->
            preferences[KEY_APPEARANCE_MODE] = normalized
        }
    }

    suspend fun saveDirectMeterSettings(snapshot: com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DIRECT_LOCAL_BASE_FARE] = snapshot.localBaseFare.coerceAtLeast(0.0)
            preferences[KEY_DIRECT_LOCAL_PER_KM_RATE] = snapshot.localPerKmRate.coerceAtLeast(0.0)
            preferences[KEY_DIRECT_HOURLY_BASE_FARE] = snapshot.hourlyBaseFare.coerceAtLeast(0.0)
            preferences[KEY_DIRECT_HOURLY_INCLUDED_KM] = snapshot.hourlyIncludedKm.coerceAtLeast(0.0)
            preferences[KEY_DIRECT_HOURLY_EXTRA_KM_RATE] = snapshot.hourlyExtraKmRate.coerceAtLeast(0.0)
            preferences[KEY_DIRECT_OUTSTATION_BASE_FARE] = snapshot.outstationBaseFare.coerceAtLeast(0.0)
            preferences[KEY_DIRECT_OUTSTATION_PER_KM_RATE] = snapshot.outstationPerKmRate.coerceAtLeast(0.0)
            preferences[KEY_DIRECT_WAITING_CHARGE_PER_MIN] = snapshot.waitingChargePerMin.coerceAtLeast(0.0)
        }
    }

    suspend fun resetDirectMeterDefaults() {
        saveDirectMeterSettings(com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot())
    }

    suspend fun resetToDefaultTariffs() {
        context.dataStore.edit { preferences ->
            preferences[KEY_BASE_FARE] = DEFAULT_BASE_FARE
            preferences[KEY_FARE_PER_KM] = DEFAULT_FARE_PER_KM
            preferences[KEY_WAIT_FARE_PER_MIN] = DEFAULT_WAIT_FARE_PER_MIN
            preferences[KEY_RENTAL_HOURLY_RATE] = DEFAULT_RENTAL_HOURLY_RATE
            preferences[KEY_RENTAL_INCLUDED_KM_PER_HOUR] = DEFAULT_RENTAL_INCLUDED_KM_PER_HOUR
            preferences[KEY_RENTAL_MIN_HOURS] = DEFAULT_RENTAL_MIN_HOURS
            preferences[KEY_RENTAL_EXTRA_HR_RATE] = DEFAULT_RENTAL_EXTRA_HR_RATE
            preferences[KEY_RENTAL_EXTRA_KM_RATE] = DEFAULT_RENTAL_EXTRA_KM_RATE
            preferences[KEY_OUTSTATION_FARE_PER_KM] = DEFAULT_OUTSTATION_FARE_PER_KM
            preferences[KEY_OUTSTATION_DRIVER_BATA] = DEFAULT_OUTSTATION_DRIVER_BATA
            preferences[KEY_OUTSTATION_MIN_FARE] = DEFAULT_OUTSTATION_MIN_FARE
            preferences[KEY_OUTSTATION_WAIT_RATE] = DEFAULT_OUTSTATION_WAIT_RATE
            preferences[KEY_OUTSTATION_ADDITIONAL_CHARGES] = DEFAULT_OUTSTATION_ADDITIONAL_CHARGES
            preferences[KEY_SPEED_THRESHOLD] = DEFAULT_SPEED_THRESHOLD
            preferences[KEY_CURRENCY] = DEFAULT_CURRENCY
            preferences[KEY_DISTANCE_CALIBRATION_FACTOR] = DEFAULT_DISTANCE_CALIBRATION_FACTOR
        }
    }
}
