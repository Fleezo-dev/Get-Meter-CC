package com.example.util

import com.example.util.SangamTariffConfig.Above25KmAnd800Rule
import com.example.util.SangamTariffConfig.CommissionBoundaryPolicy
import com.example.util.SangamTariffConfig.LocalCommissionConfig
import com.example.util.SangamTariffConfig.LocalFiveKmBoundaryPolicy
import com.example.util.SangamTariffConfig.SangamTripType
import com.example.util.SangamTariffConfig.TariffRulesConfig
import com.example.util.SangamTariffConfig.VehicleCategory
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * Deterministic BigDecimal Fare & Settlement Calculator for GET TAXI.
 *
 * Implements the official GET TAXI tariff rules:
 *  - Local Normal & Local Minimum Bill
 *  - Hourly Rental: Uniform ₹350/billed hour (5-min grace), 10 km/hr included, ₹20/extra km, ₹0 waiting, ₹50/hr Driver CC
 *  - Outstation One-Way: ₹300 base + ₹30/km, >12h +₹400, 10% CC on tariff
 *  - Outstation Round-Trip: ₹500 base + ₹15/km, >12h +₹400, 10% CC on tariff
 *  - Day Rental: 24h tariff card (including Innova & Innova Crysta)
 *
 * All money values are rounded to 2 decimal places via RoundingMode.HALF_UP.
 */
object SangamTariffCalculator {

    private val CURRENCY_FORMAT = DecimalFormat("0.00")

    fun formatRupees(amount: BigDecimal): String {
        return "₹${CURRENCY_FORMAT.format(amount.setScale(2, RoundingMode.HALF_UP))}"
    }

    private fun money(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    private fun bd(value: Double): BigDecimal = BigDecimal.valueOf(value.coerceAtLeast(0.0))

    // =========================================================================
    // INPUT & BREAKDOWN MODELS
    // =========================================================================

    data class TripCalculationInput(
        val vehicleCategory: VehicleCategory = VehicleCategory.MINI,
        val tripType: SangamTripType = SangamTripType.LOCAL_NORMAL,
        val distanceKm: Double = 0.0,
        val packageHours: Int = 1,
        val waitingMinutes: Double = 0.0,
        val isInnovaCrysta: Boolean = false,
        val isOutsideCity: Boolean = false,
        val isNightTrip: Boolean = false,
        val over12Hours: Boolean = false,
        val tollCharge: Double = 0.0,
        val permitCharge: Double = 0.0,
        val parkingCharge: Double = 0.0,
        val customExtrasTotal: Double = 0.0,
        val rulesConfig: TariffRulesConfig = SangamTariffConfig.defaultRulesConfig,
        val outstationWaitHours: Double = 0.0,
        val outstationWaitRatePerHour: Double = 0.0
    )

    data class FareLineItem(
        val label: String,
        val detail: String,
        val amount: BigDecimal,
        val isPassThrough: Boolean = false
    )

    data class BoundaryAmbiguity(
        val code: String,
        val title: String,
        val description: String,
        val candidateOptions: List<String> = emptyList()
    )

    sealed class CommissionResult {
        data class Confirmed(
            val ruleLabel: String,
            val amount: BigDecimal
        ) : CommissionResult()

        data class NeedsConfirmation(
            val reason: String,
            val minPossibleAmount: BigDecimal? = null,
            val maxPossibleAmount: BigDecimal? = null,
            val candidateLabels: List<String> = emptyList()
        ) : CommissionResult()

        data class NotSpecified(
            val reason: String
        ) : CommissionResult()
    }

    sealed class DriverBalanceStatus {
        data class Confirmed(val amount: BigDecimal) : DriverBalanceStatus()
        data class RangePendingConfirmation(
            val minDriverBalance: BigDecimal? = null,
            val maxDriverBalance: BigDecimal? = null,
            val reason: String
        ) : DriverBalanceStatus()
    }

    data class SangamSettlementBreakdown(
        val vehicleCategory: VehicleCategory,
        val tripType: SangamTripType,
        val isInnovaCrysta: Boolean = false,
        val distanceKm: Double,
        val tariffFareBeforePassThrough: BigDecimal,
        val passThroughExtrasTotal: BigDecimal,
        val customerBillTotal: BigDecimal,
        val lineItems: List<FareLineItem>,
        val commissionResult: CommissionResult,
        val driverBalanceStatus: DriverBalanceStatus,
        val ambiguities: List<BoundaryAmbiguity> = emptyList(),
        val warnings: List<String> = emptyList()
    ) {
        val hasUnresolvedAmbiguity: Boolean
            get() = ambiguities.isNotEmpty() ||
                commissionResult is CommissionResult.NeedsConfirmation ||
                commissionResult is CommissionResult.NotSpecified
    }

    // =========================================================================
    // MASTER DISPATCHER
    // =========================================================================

    fun calculateTripFareAndSettlement(input: TripCalculationInput): SangamSettlementBreakdown {
        val category = input.vehicleCategory
        if (input.tripType !in category.supportedTripTypes()) {
            return SangamSettlementBreakdown(
                vehicleCategory = category,
                tripType = input.tripType,
                distanceKm = input.distanceKm,
                tariffFareBeforePassThrough = money(BigDecimal.ZERO),
                passThroughExtrasTotal = money(BigDecimal.ZERO),
                customerBillTotal = money(BigDecimal.ZERO),
                lineItems = emptyList(),
                commissionResult = CommissionResult.NotSpecified(
                    "${category.displayName} does not support ${input.tripType.displayName} in the GET TAXI tariff chart."
                ),
                driverBalanceStatus = DriverBalanceStatus.RangePendingConfirmation(
                    reason = "${category.displayName} does not support ${input.tripType.displayName}."
                ),
                warnings = listOf("${category.displayName} is restricted to its supported tariff modes.")
            )
        }

        return when (input.tripType) {
            SangamTripType.LOCAL_NORMAL -> calculateLocalNormal(
                category = category,
                distanceKm = input.distanceKm,
                isOutsideCity = input.isOutsideCity,
                isNightTrip = input.isNightTrip,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                commissionConfig = input.rulesConfig.localCommissionConfig
            )

            SangamTripType.LOCAL_MINIMUM_BILL -> calculateLocalMinimumBill(
                category = category,
                distanceKm = input.distanceKm,
                isOutsideCity = input.isOutsideCity,
                isNightTrip = input.isNightTrip,
                fiveKmPolicy = input.rulesConfig.miniSedanFiveKmBoundaryPolicy,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                commissionConfig = input.rulesConfig.localCommissionConfig
            )

            SangamTripType.HOURLY_RENTAL -> calculateHourlyRental(
                category = category,
                packageHours = input.packageHours,
                actualDistanceKm = input.distanceKm,
                waitingMinutes = 0.0, // Hourly Rental NEVER charges waiting time
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal
            )

            SangamTripType.OUTSTATION_ONE_WAY -> calculateOutstation(
                category = category,
                isRoundTrip = false,
                distanceKm = input.distanceKm,
                over12Hours = input.over12Hours,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                outstationWaitHours = input.outstationWaitHours,
                outstationWaitRatePerHour = input.outstationWaitRatePerHour
            )

            SangamTripType.OUTSTATION_ROUND_TRIP -> calculateOutstation(
                category = category,
                isRoundTrip = true,
                distanceKm = input.distanceKm,
                over12Hours = input.over12Hours,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                outstationWaitHours = input.outstationWaitHours,
                outstationWaitRatePerHour = input.outstationWaitRatePerHour
            )

            SangamTripType.DAY_RENTAL -> calculateDayRental(
                category = category,
                distanceKm = input.distanceKm,
                isInnovaCrysta = input.isInnovaCrysta,
                after12Hours = input.over12Hours,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal
            )
        }
    }

    // =========================================================================
    // 1. LOCAL NORMAL CALCULATION
    // =========================================================================

    fun calculateLocalNormal(
        category: VehicleCategory,
        distanceKm: Double,
        isOutsideCity: Boolean = false,
        isNightTrip: Boolean = false,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0,
        commissionConfig: LocalCommissionConfig = SangamTariffConfig.defaultRulesConfig.localCommissionConfig
    ): SangamSettlementBreakdown {
        val rate = SangamTariffConfig.localNormalRates[category]
            ?: return unsupportedBreakdown(category, SangamTripType.LOCAL_NORMAL, distanceKm)

        val distBd = bd(distanceKm)
        val distCharge = money(distBd.multiply(rate.perKmRate))
        val items = mutableListOf(
            FareLineItem(
                label = "Initial Charge",
                detail = "${category.displayName} Local Normal",
                amount = money(rate.initialCharge)
            ),
            FareLineItem(
                label = "Distance Charge",
                detail = "${String.format(java.util.Locale.US, "%.2f", distanceKm.coerceAtLeast(0.0))} km × ₹${rate.perKmRate.toInt()}/km",
                amount = distCharge
            )
        )

        var tariffSubtotal = rate.initialCharge.add(distCharge)
        if (isOutsideCity) {
            items.add(
                FareLineItem(
                    label = "Outside City Limits",
                    detail = "Local out-of-city extra",
                    amount = money(SangamTariffConfig.localOutsideCityCharge)
                )
            )
            tariffSubtotal = tariffSubtotal.add(SangamTariffConfig.localOutsideCityCharge)
        }
        if (isNightTrip) {
            items.add(
                FareLineItem(
                    label = "Night Charge (11 PM – 6 AM)",
                    detail = "Local night extra",
                    amount = money(SangamTariffConfig.localNightCharge)
                )
            )
            tariffSubtotal = tariffSubtotal.add(SangamTariffConfig.localNightCharge)
        }

        tariffSubtotal = money(tariffSubtotal)
        val (passThroughTotal, passItems) = buildPassThroughExtras(tollCharge, permitCharge, parkingCharge, customExtrasTotal)
        items.addAll(passItems)

        val customerTotal = money(tariffSubtotal.add(passThroughTotal))
        val commissionResult = calculateLocalCommission(distanceKm, tariffSubtotal, commissionConfig)
        val driverBalance = computeDriverBalance(customerTotal, commissionResult)

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = SangamTripType.LOCAL_NORMAL,
            distanceKm = distanceKm.coerceAtLeast(0.0),
            tariffFareBeforePassThrough = tariffSubtotal,
            passThroughExtrasTotal = passThroughTotal,
            customerBillTotal = customerTotal,
            lineItems = items,
            commissionResult = commissionResult,
            driverBalanceStatus = driverBalance
        )
    }

    // =========================================================================
    // 2. LOCAL MINIMUM BILL CALCULATION
    // =========================================================================

    fun calculateLocalMinimumBill(
        category: VehicleCategory,
        distanceKm: Double,
        isOutsideCity: Boolean = false,
        isNightTrip: Boolean = false,
        fiveKmPolicy: LocalFiveKmBoundaryPolicy = LocalFiveKmBoundaryPolicy.UNRESOLVED_FLAG,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0,
        commissionConfig: LocalCommissionConfig = SangamTariffConfig.defaultRulesConfig.localCommissionConfig
    ): SangamSettlementBreakdown {
        val config = SangamTariffConfig.localMinimumBillRates[category]
            ?: return unsupportedBreakdown(category, SangamTripType.LOCAL_MINIMUM_BILL, distanceKm)

        val distBd = bd(distanceKm)
        val items = mutableListOf<FareLineItem>()
        val ambiguities = mutableListOf<BoundaryAmbiguity>()
        var tariffSubtotal = BigDecimal.ZERO

        if (category == VehicleCategory.MINI || category == VehicleCategory.SEDAN) {
            val fiveKm = BigDecimal("5")
            val sixKm = BigDecimal("6")
            when {
                distBd.compareTo(fiveKm) < 0 -> {
                    tariffSubtotal = config.firstSlabAmount
                    items.add(FareLineItem("Minimum Bill (1–5 KM)", "${category.displayName} min slab", money(tariffSubtotal)))
                }
                distBd.compareTo(fiveKm) == 0 -> {
                    when (fiveKmPolicy) {
                        LocalFiveKmBoundaryPolicy.INCLUDE_IN_1_TO_5_KM_200 -> {
                            tariffSubtotal = config.firstSlabAmount
                            items.add(FareLineItem("Minimum Bill (Up to 5 KM)", "Configured policy ₹200", money(tariffSubtotal)))
                        }
                        LocalFiveKmBoundaryPolicy.INCLUDE_IN_5_TO_6_KM_250 -> {
                            tariffSubtotal = config.secondSlabAmount ?: BigDecimal("250")
                            items.add(FareLineItem("Minimum Bill (5 KM)", "Configured policy ₹250", money(tariffSubtotal)))
                        }
                        LocalFiveKmBoundaryPolicy.UNRESOLVED_FLAG -> {
                            tariffSubtotal = config.firstSlabAmount
                            items.add(FareLineItem("Minimum Bill (Exact 5.0 KM)", "Chart lists '1 to 5 KM ₹200' and '5 to 6 KM ₹250'", money(tariffSubtotal)))
                            ambiguities.add(
                                BoundaryAmbiguity(
                                    code = "MIN_BILL_5KM_OVERLAP",
                                    title = "Exact 5.0 KM Minimum Bill Overlap",
                                    description = "${category.displayName} chart lists '1 to 5 KM = ₹200' AND '5 to 6 KM = ₹250'. Provisional value ₹200 shown.",
                                    candidateOptions = listOf("₹200 (1–5 KM slab)", "₹250 (5–6 KM slab)")
                                )
                            )
                        }
                    }
                }
                distBd.compareTo(sixKm) <= 0 -> {
                    tariffSubtotal = config.secondSlabAmount ?: BigDecimal("250")
                    items.add(FareLineItem("Minimum Bill (5–6 KM)", "${category.displayName} slab", money(tariffSubtotal)))
                }
                else -> {
                    val distCharge = money(distBd.multiply(config.aboveLimitPerKm))
                    tariffSubtotal = config.aboveLimitInitial.add(distCharge)
                    items.add(FareLineItem("Above 6 KM Initial", "${category.displayName} base", money(config.aboveLimitInitial)))
                    items.add(
                        FareLineItem(
                            "Above 6 KM Distance",
                            "${String.format(java.util.Locale.US, "%.2f", distanceKm)} km × ₹${config.aboveLimitPerKm.toInt()}/km",
                            distCharge
                        )
                    )
                }
            }
        } else {
            // SUV (1–7 km ₹450), SUV+ (1–10 km ₹700), INNOVA (1–10 km ₹800)
            if (distBd <= config.firstSlabMaxKm) {
                tariffSubtotal = config.firstSlabAmount
                items.add(
                    FareLineItem(
                        "Minimum Bill (1–${config.firstSlabMaxKm.toInt()} KM)",
                        "${category.displayName} fixed min slab",
                        money(tariffSubtotal)
                    )
                )
            } else {
                val distCharge = money(distBd.multiply(config.aboveLimitPerKm))
                tariffSubtotal = config.aboveLimitInitial.add(distCharge)
                items.add(
                    FareLineItem(
                        "Above ${config.firstSlabMaxKm.toInt()} KM Initial",
                        "${category.displayName} initial charge",
                        money(config.aboveLimitInitial)
                    )
                )
                items.add(
                    FareLineItem(
                        "Above ${config.firstSlabMaxKm.toInt()} KM Distance",
                        "${String.format(java.util.Locale.US, "%.2f", distanceKm)} km × ₹${config.aboveLimitPerKm.toInt()}/km",
                        distCharge
                    )
                )
            }
        }

        if (isOutsideCity) {
            items.add(FareLineItem("Outside City Limits", "Local out-of-city extra", money(SangamTariffConfig.localOutsideCityCharge)))
            tariffSubtotal = tariffSubtotal.add(SangamTariffConfig.localOutsideCityCharge)
        }
        if (isNightTrip) {
            items.add(FareLineItem("Night Charge (11 PM – 6 AM)", "Local night extra", money(SangamTariffConfig.localNightCharge)))
            tariffSubtotal = tariffSubtotal.add(SangamTariffConfig.localNightCharge)
        }

        tariffSubtotal = money(tariffSubtotal)
        val (passThroughTotal, passItems) = buildPassThroughExtras(tollCharge, permitCharge, parkingCharge, customExtrasTotal)
        items.addAll(passItems)

        val customerTotal = money(tariffSubtotal.add(passThroughTotal))
        val commissionResult = calculateLocalCommission(distanceKm, tariffSubtotal, commissionConfig)
        val driverBalance = computeDriverBalance(customerTotal, commissionResult)

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = SangamTripType.LOCAL_MINIMUM_BILL,
            distanceKm = distanceKm.coerceAtLeast(0.0),
            tariffFareBeforePassThrough = tariffSubtotal,
            passThroughExtrasTotal = passThroughTotal,
            customerBillTotal = customerTotal,
            lineItems = items,
            commissionResult = commissionResult,
            driverBalanceStatus = driverBalance,
            ambiguities = ambiguities
        )
    }

    // =========================================================================
    // LOCAL COMMISSION RULES (Exact boundaries preserved)
    // =========================================================================

    fun calculateLocalCommission(
        distanceKm: Double,
        tariffSubtotalBeforePassThrough: BigDecimal,
        config: LocalCommissionConfig = SangamTariffConfig.defaultRulesConfig.localCommissionConfig
    ): CommissionResult {
        val dist = distanceKm.coerceAtLeast(0.0)
        val bill = money(tariffSubtotalBeforePassThrough)
        val eightHundred = BigDecimal("800.00")

        val isOver25Km = dist > 25.0
        val isOver800Bill = bill > eightHundred

        if (isOver25Km && isOver800Bill) {
            return when (config.above25KmAnd800Rule) {
                Above25KmAnd800Rule.ALTERNATIVE_SINGLE_10_PERCENT -> {
                    CommissionResult.Confirmed(
                        ruleLabel = "10% (>25 km / >₹800 Bill — single 10%)",
                        amount = money(bill.multiply(BigDecimal("0.10")))
                    )
                }
                Above25KmAnd800Rule.CUMULATIVE_STACKED -> {
                    CommissionResult.Confirmed(
                        ruleLabel = "20% (Cumulative >25 km 10% + >₹800 10%)",
                        amount = money(bill.multiply(BigDecimal("0.20")))
                    )
                }
                Above25KmAnd800Rule.UNRESOLVED_NEEDS_CONFIRMATION -> {
                    val single10 = money(bill.multiply(BigDecimal("0.10")))
                    val double20 = money(bill.multiply(BigDecimal("0.20")))
                    CommissionResult.NeedsConfirmation(
                        reason = "Both >25 KM (10%) and >₹800 bill (10%) conditions are true. Confirmation required on whether single 10% or cumulative 20% applies.",
                        minPossibleAmount = single10,
                        maxPossibleAmount = double20,
                        candidateLabels = listOf(
                            "Single 10% (${formatRupees(single10)})",
                            "Cumulative 20% (${formatRupees(double20)})"
                        )
                    )
                }
            }
        }

        if (isOver800Bill) {
            return CommissionResult.Confirmed(
                ruleLabel = "10% (Local Bill > ₹800)",
                amount = money(bill.multiply(BigDecimal("0.10")))
            )
        }

        if (isOver25Km) {
            return CommissionResult.Confirmed(
                ruleLabel = "10% (Local Distance > 25 KM)",
                amount = money(bill.multiply(BigDecimal("0.10")))
            )
        }

        return when {
            dist < 5.0 -> CommissionResult.Confirmed("Local 1–5 KM", money(BigDecimal("30")))
            dist == 5.0 -> when (config.fiveToSixKmPolicy) {
                CommissionBoundaryPolicy.USE_LOWER_SLAB ->
                    CommissionResult.Confirmed("Local 1–5 KM", money(BigDecimal("30")))
                CommissionBoundaryPolicy.USE_UPPER_SLAB ->
                    CommissionResult.NeedsConfirmation("5–6 KM Gap", BigDecimal("30.00"), BigDecimal("60.00"))
                CommissionBoundaryPolicy.UNRESOLVED_FLAG ->
                    CommissionResult.Confirmed("Local 1–5 KM", money(BigDecimal("30")))
            }
            dist > 5.0 && dist < 6.0 -> {
                CommissionResult.NeedsConfirmation(
                    reason = "Tariff chart skips 5.01–5.99 KM (lists 1–5 KM = ₹30 and 6–10 KM = ₹60–₹75).",
                    minPossibleAmount = BigDecimal("30.00"),
                    maxPossibleAmount = BigDecimal("75.00"),
                    candidateLabels = listOf("₹30 (1–5 KM slab)", "₹60–₹75 (6–10 KM slab)")
                )
            }
            dist >= 6.0 && dist < 10.0 -> {
                val fixed = config.sixToTenKmFixedAmount
                if (fixed != null) {
                    CommissionResult.Confirmed("Local 6–10 KM (Configured)", money(fixed))
                } else {
                    CommissionResult.NeedsConfirmation(
                        reason = "Local 6–10 KM commission is specified as a range (₹60 to ₹75).",
                        minPossibleAmount = BigDecimal("60.00"),
                        maxPossibleAmount = BigDecimal("75.00"),
                        candidateLabels = listOf("₹60.00", "₹75.00")
                    )
                }
            }
            dist == 10.0 -> when (config.tenKmBoundaryPolicy) {
                CommissionBoundaryPolicy.USE_LOWER_SLAB -> {
                    val amt = config.sixToTenKmFixedAmount ?: BigDecimal("75")
                    CommissionResult.Confirmed("Local 6–10 KM", money(amt))
                }
                CommissionBoundaryPolicy.USE_UPPER_SLAB ->
                    CommissionResult.Confirmed("Local 10–25 KM", money(BigDecimal("80")))
                CommissionBoundaryPolicy.UNRESOLVED_FLAG -> CommissionResult.NeedsConfirmation(
                    reason = "Exact 10.0 KM boundary overlaps 6–10 KM (₹60–₹75) and 10–25 KM (₹80).",
                    minPossibleAmount = BigDecimal("60.00"),
                    maxPossibleAmount = BigDecimal("80.00"),
                    candidateLabels = listOf("₹60–₹75 (6–10 KM)", "₹80 (10–25 KM)")
                )
            }
            else -> CommissionResult.Confirmed("Local 10–25 KM", money(BigDecimal("80")))
        }
    }

    // =========================================================================
    // 3. HOURLY RENTAL — NEW GET TAXI TARIFF (§2)
    // ₹350/billed hour (5-min grace), 10 km/hr free, ₹20/extra km, ₹0 waiting, ₹50/hr CC
    // =========================================================================

    fun calculateHourlyRental(
        category: VehicleCategory,
        packageHours: Int,
        actualDistanceKm: Double,
        waitingMinutes: Double = 0.0,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0
    ): SangamSettlementBreakdown {
        val hours = packageHours.coerceAtLeast(1)
        val packageAmount = money(
            SangamTariffConfig.hourlyRatePerChargedHour.multiply(BigDecimal(hours))
        )
        val includedKm = hours * SangamTariffConfig.hourlyIncludedKmPerHour
        val extraKm = (actualDistanceKm.coerceAtLeast(0.0) - includedKm.toDouble()).coerceAtLeast(0.0)
        val extraKmRate = SangamTariffConfig.hourlyExtraKmRate
        val extraKmCharge = money(bd(extraKm).multiply(extraKmRate))

        val items = mutableListOf(
            FareLineItem(
                label = "Hourly Rental (${hours}h / $includedKm km)",
                detail = "${category.displayName} • $hours billed hr × ₹${SangamTariffConfig.hourlyRatePerChargedHour.toInt()}/hr (5-min grace)",
                amount = packageAmount
            )
        )

        if (extraKm > 0.0) {
            items.add(
                FareLineItem(
                    label = "Extra Distance (>${includedKm} km)",
                    detail = "${String.format(java.util.Locale.US, "%.2f", extraKm)} km × ₹${extraKmRate.toInt()}/km",
                    amount = extraKmCharge
                )
            )
        }

        // REQUIREMENT: Hourly Rental NEVER charges waiting time.
        val tariffSubtotal = money(packageAmount.add(extraKmCharge))
        val (passThroughTotal, passItems) = buildPassThroughExtras(tollCharge, permitCharge, parkingCharge, customExtrasTotal)
        items.addAll(passItems)

        val customerTotal = money(tariffSubtotal.add(passThroughTotal))

        // Driver CC: ₹50 per billed hour (5-minute grace rule applied to hours). NO 10% commission.
        val ccAmount = money(
            SangamTariffConfig.hourlyDriverCcPerChargedHour.multiply(BigDecimal(hours))
        )
        val commissionResult = CommissionResult.Confirmed(
            ruleLabel = "Hourly Rental CC (₹${SangamTariffConfig.hourlyDriverCcPerChargedHour.toInt()} × $hours billed hr)",
            amount = ccAmount
        )
        val driverBalance = computeDriverBalance(customerTotal, commissionResult)

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = SangamTripType.HOURLY_RENTAL,
            distanceKm = actualDistanceKm.coerceAtLeast(0.0),
            tariffFareBeforePassThrough = tariffSubtotal,
            passThroughExtrasTotal = passThroughTotal,
            customerBillTotal = customerTotal,
            lineItems = items,
            commissionResult = commissionResult,
            driverBalanceStatus = driverBalance
        )
    }

    // =========================================================================
    // 4. OUTSTATION CALCULATION — NEW GET TAXI TARIFF (§3 & §4)
    // One-Way: ₹300 + ₹30/km | Round-Trip: ₹500 + ₹15/km | >12h +₹400 | 10% CC
    // =========================================================================

    fun calculateOutstation(
        category: VehicleCategory,
        isRoundTrip: Boolean,
        distanceKm: Double,
        over12Hours: Boolean = false,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0,
        outstationWaitHours: Double = 0.0,
        outstationWaitRatePerHour: Double = 0.0
    ): SangamSettlementBreakdown {
        val outRate = SangamTariffConfig.outstationRates[category]
            ?: return unsupportedBreakdown(
                category,
                if (isRoundTrip) SangamTripType.OUTSTATION_ROUND_TRIP else SangamTripType.OUTSTATION_ONE_WAY,
                distanceKm
            )

        val initialCharge = money(outRate.initialChargeFor(isRoundTrip))
        val perKmRate = if (isRoundTrip) outRate.roundTripPerKm else outRate.oneWayPerKm
        val distCharge = money(bd(distanceKm).multiply(perKmRate))

        val items = mutableListOf(
            FareLineItem(
                label = if (isRoundTrip) "Round-Trip Base Charge" else "One-Way Base Charge",
                detail = "${category.displayName} initial charge",
                amount = initialCharge
            ),
            FareLineItem(
                label = if (isRoundTrip) "Outstation Round-Trip Distance" else "Outstation One-Way Distance",
                detail = "${String.format(java.util.Locale.US, "%.2f", distanceKm.coerceAtLeast(0.0))} km × ₹${perKmRate.toInt()}/km",
                amount = distCharge
            )
        )

        var tariffSubtotal = initialCharge.add(distCharge)
        if (over12Hours) {
            val allowance = SangamTariffConfig.outstationOver12HoursDriverAllowance
            items.add(
                FareLineItem(
                    label = "Driver Allowance (>12 Hours)",
                    detail = "Above 12 hours allowance",
                    amount = money(allowance)
                )
            )
            tariffSubtotal = tariffSubtotal.add(allowance)
        }

        // Optional manual Outstation Additional Waiting Hours (ONLY when approved rate > 0)
        if (outstationWaitHours > 0.0 && outstationWaitRatePerHour > 0.0) {
            val waitAmt = money(bd(outstationWaitHours).multiply(bd(outstationWaitRatePerHour)))
            items.add(
                FareLineItem(
                    label = "Additional Waiting Hours",
                    detail = "${String.format(java.util.Locale.US, "%.1f", outstationWaitHours)} hr × ₹${String.format(java.util.Locale.US, "%.0f", outstationWaitRatePerHour)}/hr",
                    amount = waitAmt
                )
            )
            tariffSubtotal = tariffSubtotal.add(waitAmt)
        }

        tariffSubtotal = money(tariffSubtotal)
        val (passThroughTotal, passItems) = buildPassThroughExtras(tollCharge, permitCharge, parkingCharge, customExtrasTotal)
        items.addAll(passItems)

        val customerTotal = money(tariffSubtotal.add(passThroughTotal))

        // Outstation commission is 10% on tariff subtotal ONLY (excludes pass-through toll/permit/parking)
        val commAmt = money(tariffSubtotal.multiply(SangamTariffConfig.outstationCommissionPercent))
        val commissionResult = CommissionResult.Confirmed(
            ruleLabel = "10% Outstation Tariff (Excl. Toll/Permit/Parking)",
            amount = commAmt
        )
        val driverBalance = computeDriverBalance(customerTotal, commissionResult)

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = if (isRoundTrip) SangamTripType.OUTSTATION_ROUND_TRIP else SangamTripType.OUTSTATION_ONE_WAY,
            distanceKm = distanceKm.coerceAtLeast(0.0),
            tariffFareBeforePassThrough = tariffSubtotal,
            passThroughExtrasTotal = passThroughTotal,
            customerBillTotal = customerTotal,
            lineItems = items,
            commissionResult = commissionResult,
            driverBalanceStatus = driverBalance
        )
    }

    // =========================================================================
    // 5. DAY RENTAL CALCULATION
    // =========================================================================

    fun calculateDayRental(
        category: VehicleCategory,
        distanceKm: Double,
        isInnovaCrysta: Boolean = false,
        after12Hours: Boolean = false,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0
    ): SangamSettlementBreakdown {
        val useCrysta = category == VehicleCategory.INNOVA && isInnovaCrysta
        val rate = if (useCrysta) {
            SangamTariffConfig.innovaCrystaDayRentalRate
        } else {
            SangamTariffConfig.dayRentalRates[category]
        } ?: return unsupportedBreakdown(category, SangamTripType.DAY_RENTAL, distanceKm)

        val catLabel = if (useCrysta) "INNOVA CRYSTA" else category.displayName
        val distCharge = money(bd(distanceKm).multiply(rate.perKmRate))

        val items = mutableListOf(
            FareLineItem("Day Rental Base", catLabel, money(rate.baseAmount)),
            FareLineItem(
                "Day Rental Distance",
                "${String.format(java.util.Locale.US, "%.2f", distanceKm.coerceAtLeast(0.0))} km × ₹${rate.perKmRate.toInt()}/km",
                distCharge
            )
        )

        var tariffSubtotal = rate.baseAmount.add(distCharge)
        if (after12Hours && rate.after12HoursExtra > BigDecimal.ZERO) {
            items.add(
                FareLineItem(
                    "After 12 Hours Extra Charge",
                    "$catLabel (>12 hours)",
                    money(rate.after12HoursExtra)
                )
            )
            tariffSubtotal = tariffSubtotal.add(rate.after12HoursExtra)
        }

        tariffSubtotal = money(tariffSubtotal)
        val (passThroughTotal, passItems) = buildPassThroughExtras(tollCharge, permitCharge, parkingCharge, customExtrasTotal)
        items.addAll(passItems)

        val customerTotal = money(tariffSubtotal.add(passThroughTotal))

        val commissionResult = CommissionResult.NotSpecified(
            reason = "Day Rental commission rate for $catLabel is not specified in the GET TAXI tariff chart — confirmation required."
        )
        val driverBalance = computeDriverBalance(customerTotal, commissionResult)

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = SangamTripType.DAY_RENTAL,
            isInnovaCrysta = useCrysta,
            distanceKm = distanceKm.coerceAtLeast(0.0),
            tariffFareBeforePassThrough = tariffSubtotal,
            passThroughExtrasTotal = passThroughTotal,
            customerBillTotal = customerTotal,
            lineItems = items,
            commissionResult = commissionResult,
            driverBalanceStatus = driverBalance
        )
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun buildPassThroughExtras(
        tollCharge: Double,
        permitCharge: Double,
        parkingCharge: Double,
        customExtrasTotal: Double
    ): Pair<BigDecimal, List<FareLineItem>> {
        val passItems = mutableListOf<FareLineItem>()
        var total = BigDecimal.ZERO

        if (tollCharge > 0.0) {
            val amt = money(bd(tollCharge))
            total = total.add(amt)
            passItems.add(FareLineItem("Toll Charges", "Pass-through customer expense", amt, isPassThrough = true))
        }
        if (permitCharge > 0.0) {
            val amt = money(bd(permitCharge))
            total = total.add(amt)
            passItems.add(FareLineItem("Permit Charges", "Pass-through customer expense", amt, isPassThrough = true))
        }
        if (parkingCharge > 0.0) {
            val amt = money(bd(parkingCharge))
            total = total.add(amt)
            passItems.add(FareLineItem("Parking Charges", "Pass-through customer expense", amt, isPassThrough = true))
        }
        if (customExtrasTotal > 0.0) {
            val amt = money(bd(customExtrasTotal))
            total = total.add(amt)
            passItems.add(FareLineItem("Additional Charges", "Pass-through customer expense", amt, isPassThrough = true))
        }

        return money(total) to passItems
    }

    private fun computeDriverBalance(
        customerBillTotal: BigDecimal,
        commissionResult: CommissionResult
    ): DriverBalanceStatus {
        return when (commissionResult) {
            is CommissionResult.Confirmed -> {
                val net = customerBillTotal.subtract(commissionResult.amount).coerceAtLeast(BigDecimal.ZERO)
                DriverBalanceStatus.Confirmed(money(net))
            }
            is CommissionResult.NeedsConfirmation -> {
                val minBal = commissionResult.maxPossibleAmount?.let {
                    money(customerBillTotal.subtract(it).coerceAtLeast(BigDecimal.ZERO))
                }
                val maxBal = commissionResult.minPossibleAmount?.let {
                    money(customerBillTotal.subtract(it).coerceAtLeast(BigDecimal.ZERO))
                }
                DriverBalanceStatus.RangePendingConfirmation(
                    minDriverBalance = minBal,
                    maxDriverBalance = maxBal,
                    reason = commissionResult.reason
                )
            }
            is CommissionResult.NotSpecified -> {
                DriverBalanceStatus.RangePendingConfirmation(
                    minDriverBalance = null,
                    maxDriverBalance = null,
                    reason = commissionResult.reason
                )
            }
        }
    }

    private fun unsupportedBreakdown(
        category: VehicleCategory,
        tripType: SangamTripType,
        distanceKm: Double
    ): SangamSettlementBreakdown {
        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = tripType,
            distanceKm = distanceKm,
            tariffFareBeforePassThrough = money(BigDecimal.ZERO),
            passThroughExtrasTotal = money(BigDecimal.ZERO),
            customerBillTotal = money(BigDecimal.ZERO),
            lineItems = emptyList(),
            commissionResult = CommissionResult.NotSpecified(
                "${category.displayName} does not support ${tripType.displayName} in the GET TAXI tariff chart."
            ),
            driverBalanceStatus = DriverBalanceStatus.RangePendingConfirmation(
                reason = "${category.displayName} does not support ${tripType.displayName}."
            ),
            warnings = listOf("Unsupported vehicle/trip-type combination.")
        )
    }
}
