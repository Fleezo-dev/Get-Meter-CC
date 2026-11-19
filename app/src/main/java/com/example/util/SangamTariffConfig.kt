package com.example.util

import java.math.BigDecimal

/**
 * Authoritative Tariff Configuration for GET TAXI.
 *
 * Vehicle classifications, fixed rate cards, hourly package tables, and
 * commission rules are isolated here for verification and testability.
 */
object SangamTariffConfig {

    const val VENDOR_ID = "get_taxi"
    const val VENDOR_NAME = "GET TAXI"
    const val VENDOR_GPAY_NUMBER = "9043743777"
    const val CURRENCY_SYMBOL = "₹"

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
        SUV(
            id = "SUV",
            displayName = "SUV",
            shortLabel = "SUV",
            exampleModels = "Ertiga & similar 6–7 seaters"
        ),
        SUV_PLUS(
            id = "SUV_PLUS",
            displayName = "SUV+",
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
            return SangamTripType.entries
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
    // ISOLATED UNRESOLVED CHART RULES
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
        val above25KmAnd800Rule: Above25KmAnd800Rule = Above25KmAnd800Rule.UNRESOLVED_NEEDS_CONFIRMATION,
        val fiveToSixKmPolicy: CommissionBoundaryPolicy = CommissionBoundaryPolicy.UNRESOLVED_FLAG,
        val tenKmBoundaryPolicy: CommissionBoundaryPolicy = CommissionBoundaryPolicy.UNRESOLVED_FLAG
    )

    data class TariffRulesConfig(
        val miniSedanFiveKmBoundaryPolicy: LocalFiveKmBoundaryPolicy = LocalFiveKmBoundaryPolicy.UNRESOLVED_FLAG,
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

    val localOutsideCityCharge: BigDecimal = BigDecimal("80")
    val localNightCharge: BigDecimal = BigDecimal("50") // 11 PM – 6 AM

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
    // HOURLY RENTAL GET TAXI TARIFF (§2)
    // ₹350 per charged hour, 10 km free per charged hour, ₹20 per extra km, 5-min grace, zero waiting, ₹50 CC/hr
    // =========================================================================

    val hourlyRatePerChargedHour: BigDecimal = BigDecimal("350")
    val hourlyIncludedKmPerHour: Int = 10
    val hourlyExtraKmRate: BigDecimal = BigDecimal("20")
    val hourlyWaitingPerMin: BigDecimal = BigDecimal.ZERO
    val hourlyDriverCcPerChargedHour: BigDecimal = BigDecimal("50")

    val hourlyPackagesUniform: Map<Int, BigDecimal> = mapOf(
        1 to BigDecimal("350"),
        2 to BigDecimal("700"),
        3 to BigDecimal("1050"),
        4 to BigDecimal("1400"),
        5 to BigDecimal("1750"),
        6 to BigDecimal("2100"),
        7 to BigDecimal("2450"),
        8 to BigDecimal("2800"),
        9 to BigDecimal("3150"),
        10 to BigDecimal("3500"),
        11 to BigDecimal("3850"),
        12 to BigDecimal("4200")
    )

    fun hourlyPackageTableFor(category: VehicleCategory): Map<Int, BigDecimal>? {
        return hourlyPackagesUniform
    }

    // =========================================================================
    // OUTSTATION GET TAXI TARIFFS (§3, §4)
    // One-way: Base ₹300 + ₹30/km
    // Round-trip: Base ₹500 + ₹15/km
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
