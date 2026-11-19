package com.example.util

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Authoritative Tariff Configuration for GET TAXI.
 *
 * Vehicle classifications, fixed rate cards, hourly package tables, Direct Meter defaults,
 * and automatic commission rules are isolated here for verification and testability.
 */
object SangamTariffConfig {

    const val VENDOR_ID = "get_taxi"
    const val VENDOR_NAME = "GET TAXI"
    const val VENDOR_GPAY_NUMBER = "9043743777"
    const val CURRENCY_SYMBOL = "₹"

    const val METER_MODE_OTP = "OTP"
    const val METER_MODE_DIRECT = "DIRECT"

    const val EXTRA_LABEL_OUTSIDE_CITY = "Outside City Limits"
    const val EXTRA_LABEL_NIGHT_ALLOWANCE = "Night Allowance (11 PM–6 AM)"

    enum class VehicleCategory(
        val id: String,
        val displayName: String,
        val shortLabel: String,
        val exampleModels: String,
        val isVanOrTraveller: Boolean = false
    ) {
        MINI(
            id = "MINI",
            displayName = "MINI",
            shortLabel = "MINI",
            exampleModels = "Alto, WagonR, Santro, Swift"
        ),
        SEDAN(
            id = "SEDAN",
            displayName = "SEDAN",
            shortLabel = "SEDAN",
            exampleModels = "Dzire, Aura, Amaze, Etios"
        ),
        OMNI_EECO(
            id = "OMNI_EECO",
            displayName = "OMNI / EECO",
            shortLabel = "OMNI/EECO",
            exampleModels = "Maruti Omni, Maruti Eeco"
        ),
        SUV(
            id = "SUV",
            displayName = "SUV (Ertiga)",
            shortLabel = "SUV",
            exampleModels = "Ertiga, Rumion & similar 6–7 seaters"
        ),
        SUV_PLUS(
            id = "SUV_PLUS",
            displayName = "SUV+ (Rumion)",
            shortLabel = "SUV+",
            exampleModels = "Ertiga, Rumion, Xylo, Marazzo (Premium)"
        ),
        INNOVA(
            id = "INNOVA",
            displayName = "INNOVA",
            shortLabel = "INNOVA",
            exampleModels = "Innova, Innova Crysta, Innova Hycross"
        );

        fun supportedTripTypes(): List<SangamTripType> {
            return if (this == OMNI_EECO) {
                listOf(SangamTripType.HOURLY_RENTAL)
            } else {
                SangamTripType.entries
            }
        }

        companion object {
            fun fromId(id: String?): VehicleCategory {
                return entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: MINI
            }
        }
    }

    enum class SangamTripType(
        val id: String,
        val displayName: String,
        val shortLabel: String
    ) {
        LOCAL_NORMAL("LOCAL_NORMAL", "Local Normal", "Local"),
        LOCAL_MINIMUM_BILL("LOCAL_MINIMUM_BILL", "Local Min Bill", "Min Bill"),
        HOURLY_RENTAL("HOURLY_RENTAL", "Hourly Rental", "Hourly"),
        OUTSTATION_ONE_WAY("OUTSTATION_ONE_WAY", "Outstation One-Way", "One-Way"),
        OUTSTATION_ROUND_TRIP("OUTSTATION_ROUND_TRIP", "Outstation Round-Trip", "Round-Trip"),
        DAY_RENTAL("DAY_RENTAL", "Day Rental", "Day Rent");

        companion object {
            fun fromId(id: String?): SangamTripType {
                return entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
                    ?: LOCAL_NORMAL
            }
        }
    }

    // =========================================================================
    // DIRECT METER MODE (INDEPENDENT OF OTP TARIFF & VENDOR CC)
    // =========================================================================

    enum class DirectMeterCategory(
        val id: String,
        val displayName: String,
        val shortLabel: String
    ) {
        LOCAL("LOCAL", "Local", "Local"),
        HOURLY_RENTAL("HOURLY_RENTAL", "Hourly Rental", "Hourly"),
        OUTSTATION("OUTSTATION", "Outstation", "Outstation");

        companion object {
            fun fromId(id: String?): DirectMeterCategory {
                return entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: LOCAL
            }
        }
    }

    data class DirectMeterTariffSnapshot(
        val localBaseFare: Double = DEFAULT_DIRECT_LOCAL_BASE,
        val localPerKmRate: Double = DEFAULT_DIRECT_LOCAL_PER_KM,
        val hourlyBaseFare: Double = DEFAULT_DIRECT_HOURLY_BASE,
        val hourlyIncludedKm: Double = DEFAULT_DIRECT_HOURLY_INCLUDED_KM,
        val hourlyExtraKmRate: Double = DEFAULT_DIRECT_HOURLY_EXTRA_KM,
        val outstationBaseFare: Double = DEFAULT_DIRECT_OUTSTATION_BASE,
        val outstationPerKmRate: Double = DEFAULT_DIRECT_OUTSTATION_PER_KM,
        val waitingChargePerMin: Double = DEFAULT_DIRECT_WAITING_PER_MIN
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("localBaseFare", localBaseFare)
                put("localPerKmRate", localPerKmRate)
                put("hourlyBaseFare", hourlyBaseFare)
                put("hourlyIncludedKm", hourlyIncludedKm)
                put("hourlyExtraKmRate", hourlyExtraKmRate)
                put("outstationBaseFare", outstationBaseFare)
                put("outstationPerKmRate", outstationPerKmRate)
                put("waitingChargePerMin", waitingChargePerMin)
            }.toString()
        }

        companion object {
            const val DEFAULT_DIRECT_LOCAL_BASE = 100.0
            const val DEFAULT_DIRECT_LOCAL_PER_KM = 30.0
            const val DEFAULT_DIRECT_HOURLY_BASE = 375.0
            const val DEFAULT_DIRECT_HOURLY_INCLUDED_KM = 10.0
            const val DEFAULT_DIRECT_HOURLY_EXTRA_KM = 20.0
            const val DEFAULT_DIRECT_OUTSTATION_BASE = 300.0
            const val DEFAULT_DIRECT_OUTSTATION_PER_KM = 30.0
            const val DEFAULT_DIRECT_WAITING_PER_MIN = 2.0

            fun fromJson(json: String?): DirectMeterTariffSnapshot {
                if (json.isNullOrBlank()) return DirectMeterTariffSnapshot()
                return try {
                    val obj = JSONObject(json)
                    DirectMeterTariffSnapshot(
                        localBaseFare = obj.optDouble("localBaseFare", DEFAULT_DIRECT_LOCAL_BASE).coerceAtLeast(0.0),
                        localPerKmRate = obj.optDouble("localPerKmRate", DEFAULT_DIRECT_LOCAL_PER_KM).coerceAtLeast(0.0),
                        hourlyBaseFare = obj.optDouble("hourlyBaseFare", DEFAULT_DIRECT_HOURLY_BASE).coerceAtLeast(0.0),
                        hourlyIncludedKm = obj.optDouble("hourlyIncludedKm", DEFAULT_DIRECT_HOURLY_INCLUDED_KM).coerceAtLeast(0.0),
                        hourlyExtraKmRate = obj.optDouble("hourlyExtraKmRate", DEFAULT_DIRECT_HOURLY_EXTRA_KM).coerceAtLeast(0.0),
                        outstationBaseFare = obj.optDouble("outstationBaseFare", DEFAULT_DIRECT_OUTSTATION_BASE).coerceAtLeast(0.0),
                        outstationPerKmRate = obj.optDouble("outstationPerKmRate", DEFAULT_DIRECT_OUTSTATION_PER_KM).coerceAtLeast(0.0),
                        waitingChargePerMin = obj.optDouble("waitingChargePerMin", DEFAULT_DIRECT_WAITING_PER_MIN).coerceAtLeast(0.0)
                    )
                } catch (_: Exception) {
                    DirectMeterTariffSnapshot()
                }
            }
        }
    }

    // =========================================================================
    // ISOLATED CHART BOUNDARY RULES
    // =========================================================================

    enum class LocalFiveKmBoundaryPolicy {
        UNRESOLVED_FLAG,
        INCLUDE_IN_1_TO_5_KM_200,
        INCLUDE_IN_5_TO_6_KM_250
    }

    enum class Above25KmAnd800Rule {
        UNRESOLVED_NEEDS_CONFIRMATION,
        ALTERNATIVE_SINGLE_10_PERCENT,
        CUMULATIVE_STACKED
    }

    enum class CommissionBoundaryPolicy {
        UNRESOLVED_FLAG,
        USE_LOWER_SLAB,
        USE_UPPER_SLAB
    }

    data class LocalCommissionConfig(
        val sixToTenKmFixedAmount: BigDecimal? = null,
        val above25KmAnd800Rule: Above25KmAnd800Rule = Above25KmAnd800Rule.ALTERNATIVE_SINGLE_10_PERCENT,
        val fiveToSixKmPolicy: CommissionBoundaryPolicy = CommissionBoundaryPolicy.USE_LOWER_SLAB,
        val tenKmBoundaryPolicy: CommissionBoundaryPolicy = CommissionBoundaryPolicy.USE_LOWER_SLAB
    )

    data class TariffRulesConfig(
        val miniSedanFiveKmBoundaryPolicy: LocalFiveKmBoundaryPolicy = LocalFiveKmBoundaryPolicy.INCLUDE_IN_1_TO_5_KM_200,
        val localCommissionConfig: LocalCommissionConfig = LocalCommissionConfig()
    )

    val defaultRulesConfig = TariffRulesConfig()

    // =========================================================================
    // LOCAL TARIFF TABLES
    // =========================================================================

    data class LocalNormalRate(
        val initialCharge: BigDecimal,
        val perKmRate: BigDecimal
    )

    val localNormalRates: Map<VehicleCategory, LocalNormalRate> = mapOf(
        VehicleCategory.MINI to LocalNormalRate(BigDecimal("80"), BigDecimal("28")),
        VehicleCategory.SEDAN to LocalNormalRate(BigDecimal("80"), BigDecimal("28")),
        VehicleCategory.SUV to LocalNormalRate(BigDecimal("200"), BigDecimal("35")),
        VehicleCategory.SUV_PLUS to LocalNormalRate(BigDecimal("250"), BigDecimal("45")),
        VehicleCategory.INNOVA to LocalNormalRate(BigDecimal("250"), BigDecimal("50"))
    )

    // Manual-only additional charge options (NO automatic fixed ₹80 / ₹50 added!)
    val localOutsideCityCharge: BigDecimal = BigDecimal.ZERO
    val localNightCharge: BigDecimal = BigDecimal.ZERO
    val localWaitingRatePerMin: BigDecimal = BigDecimal("2") // Confirmed ₹2/min for Local Normal & Local Min Bill

    // Automatic CC slab thresholds
    val autoCcBillThreshold: BigDecimal = BigDecimal("1000.00")
    val autoCcOverThresholdPercent: BigDecimal = BigDecimal("0.10")

    data class LocalMinimumBillConfig(
        val firstSlabMaxKm: BigDecimal,
        val firstSlabAmount: BigDecimal,
        val secondSlabMaxKm: BigDecimal? = null,
        val secondSlabAmount: BigDecimal? = null,
        val aboveLimitInitial: BigDecimal,
        val aboveLimitPerKm: BigDecimal
    )

    val localMinimumBillRates: Map<VehicleCategory, LocalMinimumBillConfig> = mapOf(
        VehicleCategory.MINI to LocalMinimumBillConfig(
            firstSlabMaxKm = BigDecimal("5"),
            firstSlabAmount = BigDecimal("200"),
            secondSlabMaxKm = BigDecimal("6"),
            secondSlabAmount = BigDecimal("250"),
            aboveLimitInitial = BigDecimal("80"),
            aboveLimitPerKm = BigDecimal("28")
        ),
        VehicleCategory.SEDAN to LocalMinimumBillConfig(
            firstSlabMaxKm = BigDecimal("5"),
            firstSlabAmount = BigDecimal("200"),
            secondSlabMaxKm = BigDecimal("6"),
            secondSlabAmount = BigDecimal("250"),
            aboveLimitInitial = BigDecimal("80"),
            aboveLimitPerKm = BigDecimal("28")
        ),
        VehicleCategory.SUV to LocalMinimumBillConfig(
            firstSlabMaxKm = BigDecimal("7"),
            firstSlabAmount = BigDecimal("450"),
            aboveLimitInitial = BigDecimal("200"),
            aboveLimitPerKm = BigDecimal("35")
        ),
        VehicleCategory.SUV_PLUS to LocalMinimumBillConfig(
            firstSlabMaxKm = BigDecimal("10"),
            firstSlabAmount = BigDecimal("700"),
            aboveLimitInitial = BigDecimal("250"),
            aboveLimitPerKm = BigDecimal("45")
        ),
        VehicleCategory.INNOVA to LocalMinimumBillConfig(
            firstSlabMaxKm = BigDecimal("10"),
            firstSlabAmount = BigDecimal("800"),
            aboveLimitInitial = BigDecimal("250"),
            aboveLimitPerKm = BigDecimal("50")
        )
    )

    // =========================================================================
    // HOURLY RENTAL GET TAXI TARIFFS (COMMISSION ALREADY INCLUDED IN METER RATE)
    // =========================================================================

    data class HourlyVehicleTariff(
        val hourlyMeterRate: BigDecimal,
        val commissionPerHour: BigDecimal,
        val minHours: Int,
        val includedKmPerHour: Int = 10,
        val extraKmRate: BigDecimal,
        val package100KmFare: BigDecimal? = null,
        val package100KmDutyHours: Int = 10,
        val package100KmIncludedKm: Int = 100
    ) {
        val supports100KmPackage: Boolean
            get() = package100KmFare != null
    }

    val hourlyRatePerChargedHour: BigDecimal = BigDecimal("350")
    val hourlyIncludedKmPerHour: Int = 10
    val hourlyExtraKmRate: BigDecimal = BigDecimal("20")
    val hourlyWaitingPerMin: BigDecimal = BigDecimal.ZERO
    val hourlyDriverCcPerChargedHour: BigDecimal = BigDecimal("50")

    val hourlyVehicleTariffs: Map<VehicleCategory, HourlyVehicleTariff> = mapOf(
        VehicleCategory.MINI to HourlyVehicleTariff(
            hourlyMeterRate = BigDecimal("350"),
            commissionPerHour = BigDecimal("50"),
            minHours = 1,
            includedKmPerHour = 10,
            extraKmRate = BigDecimal("20"),
            package100KmFare = null
        ),
        VehicleCategory.SEDAN to HourlyVehicleTariff(
            hourlyMeterRate = BigDecimal("350"),
            commissionPerHour = BigDecimal("50"),
            minHours = 1,
            includedKmPerHour = 10,
            extraKmRate = BigDecimal("20"),
            package100KmFare = null
        ),
        // OMNI / Eeco: Existing ₹375 + Commission ₹75 = ₹450/hr (commission included — never added twice!)
        VehicleCategory.OMNI_EECO to HourlyVehicleTariff(
            hourlyMeterRate = BigDecimal("450"),
            commissionPerHour = BigDecimal("75"),
            minHours = 1,
            includedKmPerHour = 10,
            extraKmRate = BigDecimal("13"),
            package100KmFare = BigDecimal("3500"),
            package100KmDutyHours = 10,
            package100KmIncludedKm = 100
        ),
        // Ertiga / Rumion: Existing ₹450 + Commission ₹75 = ₹525/hr, Min 3h, 100 KM / 10h pkg ₹3,800, Extra ₹14/km
        VehicleCategory.SUV to HourlyVehicleTariff(
            hourlyMeterRate = BigDecimal("525"),
            commissionPerHour = BigDecimal("75"),
            minHours = 3,
            includedKmPerHour = 10,
            extraKmRate = BigDecimal("14"),
            package100KmFare = BigDecimal("3800"),
            package100KmDutyHours = 10,
            package100KmIncludedKm = 100
        ),
        VehicleCategory.SUV_PLUS to HourlyVehicleTariff(
            hourlyMeterRate = BigDecimal("525"),
            commissionPerHour = BigDecimal("75"),
            minHours = 3,
            includedKmPerHour = 10,
            extraKmRate = BigDecimal("14"),
            package100KmFare = BigDecimal("3800"),
            package100KmDutyHours = 10,
            package100KmIncludedKm = 100
        ),
        // Innova: Existing ₹500 + Commission ₹100 = ₹600/hr, Min 4h, 100 KM / 10h pkg ₹4,000, Extra ₹15/km
        VehicleCategory.INNOVA to HourlyVehicleTariff(
            hourlyMeterRate = BigDecimal("600"),
            commissionPerHour = BigDecimal("100"),
            minHours = 4,
            includedKmPerHour = 10,
            extraKmRate = BigDecimal("15"),
            package100KmFare = BigDecimal("4000"),
            package100KmDutyHours = 10,
            package100KmIncludedKm = 100
        )
    )

    fun hourlyTariffFor(category: VehicleCategory): HourlyVehicleTariff {
        return hourlyVehicleTariffs[category] ?: hourlyVehicleTariffs.getValue(VehicleCategory.MINI)
    }

    val hourlyPackagesUniform: Map<Int, BigDecimal> = (1..12).associateWith { hr ->
        hourlyRatePerChargedHour.multiply(BigDecimal(hr))
    }

    fun hourlyPackageTableFor(category: VehicleCategory): Map<Int, BigDecimal> {
        val tariff = hourlyTariffFor(category)
        return (tariff.minHours..12).associateWith { hr ->
            tariff.hourlyMeterRate.multiply(BigDecimal(hr))
        }
    }

    // =========================================================================
    // OUTSTATION GET TAXI TARIFFS
    // One-way: Base ₹300 + ₹30/km | Round-trip: Base ₹500 + ₹15/km
    // =========================================================================

    data class OutstationRate(
        val oneWayInitialCharge: BigDecimal,
        val roundTripInitialCharge: BigDecimal,
        val oneWayPerKm: BigDecimal,
        val roundTripPerKm: BigDecimal
    ) {
        fun initialChargeFor(isRoundTrip: Boolean): BigDecimal =
            if (isRoundTrip) roundTripInitialCharge else oneWayInitialCharge
    }

    val outstationRates: Map<VehicleCategory, OutstationRate> = mapOf(
        VehicleCategory.MINI to OutstationRate(BigDecimal("300"), BigDecimal("500"), BigDecimal("30"), BigDecimal("15")),
        VehicleCategory.SEDAN to OutstationRate(BigDecimal("300"), BigDecimal("500"), BigDecimal("30"), BigDecimal("15")),
        VehicleCategory.SUV to OutstationRate(BigDecimal("300"), BigDecimal("500"), BigDecimal("30"), BigDecimal("15")),
        VehicleCategory.SUV_PLUS to OutstationRate(BigDecimal("300"), BigDecimal("500"), BigDecimal("30"), BigDecimal("15")),
        VehicleCategory.INNOVA to OutstationRate(BigDecimal("300"), BigDecimal("500"), BigDecimal("30"), BigDecimal("15"))
    )

    val outstationOver12HoursDriverAllowance: BigDecimal = BigDecimal("400")
    val outstationCommissionPercent: BigDecimal = BigDecimal("0.10")

    // =========================================================================
    // DAY RENTAL TABLES
    // =========================================================================

    data class DayRentalRate(
        val baseAmount: BigDecimal,
        val perKmRate: BigDecimal,
        val after12HoursExtra: BigDecimal = BigDecimal.ZERO
    )

    val dayRentalRates: Map<VehicleCategory, DayRentalRate> = mapOf(
        VehicleCategory.MINI to DayRentalRate(BigDecimal("2000"), BigDecimal("10")),
        VehicleCategory.SEDAN to DayRentalRate(BigDecimal("2000"), BigDecimal("10")),
        VehicleCategory.SUV to DayRentalRate(BigDecimal("2300"), BigDecimal("13")),
        VehicleCategory.SUV_PLUS to DayRentalRate(BigDecimal("2400"), BigDecimal("14")),
        VehicleCategory.INNOVA to DayRentalRate(BigDecimal("2500"), BigDecimal("15"))
    )

    val innovaCrystaDayRentalRate = DayRentalRate(BigDecimal("3000"), BigDecimal("16"))
}
