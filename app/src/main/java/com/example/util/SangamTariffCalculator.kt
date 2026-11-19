package com.example.util

import com.example.data.model.ExtraChargeItem
import com.example.util.SangamTariffConfig.DirectMeterCategory
import com.example.util.SangamTariffConfig.DirectMeterTariffSnapshot
import com.example.util.SangamTariffConfig.LocalCommissionConfig
import com.example.util.SangamTariffConfig.LocalFiveKmBoundaryPolicy
import com.example.util.SangamTariffConfig.SangamTripType
import com.example.util.SangamTariffConfig.TariffRulesConfig
import com.example.util.SangamTariffConfig.VehicleCategory
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Deterministic BigDecimal Fare & Settlement Calculator for GET TAXI.
 *
 * Implements:
 *  - OTP Meter Local Normal & Local Minimum Bill with Automatic decimal-distance CC slabs
 *    (0–5.00km ₹25, >5.00–7.50km ₹50, >7.50–10.00km ₹70, >10.00–20.00km ₹80, >20.00km ₹100;
 *     if entire customer bill > ₹1,000.00 -> 10% of entire customer bill replaces fixed CC)
 *  - OTP Meter Hourly Rental:
 *      MINI/SEDAN: ₹350/hr (min 1h, ₹20/extra km, ₹50/hr CC)
 *      OMNI/EECO:  ₹450/hr (min 1h, ₹75/hr CC included, 100km/10h pkg ₹3,500, ₹13/extra km)
 *      ERTIGA/RUMION (SUV/SUV+): ₹525/hr (min 3h, ₹75/hr CC included, 100km/10h pkg ₹3,800, ₹14/extra km)
 *      INNOVA:     ₹600/hr (min 4h, ₹100/hr CC included, 100km/10h pkg ₹4,000, ₹15/extra km)
 *  - OTP Meter Outstation One-Way (₹300 + ₹30/km) & Round-Trip (₹500 + ₹15/km)
 *  - OTP Meter Day Rental (24h tariff card, manual/variable CC)
 *  - Independent Direct Meter Mode (Local, Hourly Rental, Outstation — NO OTP, NO CC, NO Vendor Settlement)
 *  - Manual-amount-only named Additional Charges (Outside City Limits, Night Allowance 11 PM–6 AM)
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
        val use100KmPackage: Boolean = false,
        val waitingMinutes: Double = 0.0,
        val isInnovaCrysta: Boolean = false,
        val isOutsideCity: Boolean = false,
        val isNightTrip: Boolean = false,
        val outsideCityCharge: Double = 0.0,
        val nightAllowanceCharge: Double = 0.0,
        val over12Hours: Boolean = false,
        val tollCharge: Double = 0.0,
        val permitCharge: Double = 0.0,
        val parkingCharge: Double = 0.0,
        val customExtrasTotal: Double = 0.0,
        val extraChargeItems: List<ExtraChargeItem> = emptyList(),
        val rulesConfig: TariffRulesConfig = SangamTariffConfig.defaultRulesConfig,
        val outstationWaitHours: Double = 0.0,
        val outstationWaitRatePerHour: Double = 0.0,
        val manualDayRentalCcAmount: Double? = null,
        // Independent Direct Meter Mode fields
        val meterMode: String = SangamTariffConfig.METER_MODE_OTP,
        val directCategory: DirectMeterCategory = DirectMeterCategory.LOCAL,
        val directTariffSnapshot: DirectMeterTariffSnapshot = DirectMeterTariffSnapshot()
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
        val warnings: List<String> = emptyList(),
        val meterMode: String = SangamTariffConfig.METER_MODE_OTP,
        val directCategory: DirectMeterCategory? = null
    ) {
        val isDirectMeter: Boolean
            get() = meterMode.equals(SangamTariffConfig.METER_MODE_DIRECT, ignoreCase = true)

        val hasUnresolvedAmbiguity: Boolean
            get() = !isDirectMeter && (
                ambiguities.isNotEmpty() ||
                    commissionResult is CommissionResult.NeedsConfirmation ||
                    commissionResult is CommissionResult.NotSpecified
                )
    }

    // =========================================================================
    // MASTER DISPATCHER
    // =========================================================================

    fun calculateTripFareAndSettlement(input: TripCalculationInput): SangamSettlementBreakdown {
        if (input.meterMode.equals(SangamTariffConfig.METER_MODE_DIRECT, ignoreCase = true)) {
            return calculateDirectMeterFare(
                directCategory = input.directCategory,
                distanceKm = input.distanceKm,
                billedHours = input.packageHours,
                waitingMinutes = input.waitingMinutes,
                tariffSnapshot = input.directTariffSnapshot,
                outsideCityCharge = input.outsideCityCharge,
                nightAllowanceCharge = input.nightAllowanceCharge,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                extraChargeItems = input.extraChargeItems
            )
        }

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
                waitingMinutes = input.waitingMinutes,
                isOutsideCity = input.isOutsideCity,
                isNightTrip = input.isNightTrip,
                outsideCityCharge = input.outsideCityCharge,
                nightAllowanceCharge = input.nightAllowanceCharge,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                extraChargeItems = input.extraChargeItems,
                commissionConfig = input.rulesConfig.localCommissionConfig
            )

            SangamTripType.LOCAL_MINIMUM_BILL -> calculateLocalMinimumBill(
                category = category,
                distanceKm = input.distanceKm,
                waitingMinutes = input.waitingMinutes,
                isOutsideCity = input.isOutsideCity,
                isNightTrip = input.isNightTrip,
                outsideCityCharge = input.outsideCityCharge,
                nightAllowanceCharge = input.nightAllowanceCharge,
                fiveKmPolicy = input.rulesConfig.miniSedanFiveKmBoundaryPolicy,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                extraChargeItems = input.extraChargeItems,
                commissionConfig = input.rulesConfig.localCommissionConfig
            )

            SangamTripType.HOURLY_RENTAL -> calculateHourlyRental(
                category = category,
                packageHours = input.packageHours,
                actualDistanceKm = input.distanceKm,
                use100KmPackage = input.use100KmPackage,
                waitingMinutes = 0.0, // Hourly Rental NEVER charges waiting time
                outsideCityCharge = input.outsideCityCharge,
                nightAllowanceCharge = input.nightAllowanceCharge,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                extraChargeItems = input.extraChargeItems
            )

            SangamTripType.OUTSTATION_ONE_WAY -> calculateOutstation(
                category = category,
                isRoundTrip = false,
                distanceKm = input.distanceKm,
                over12Hours = input.over12Hours,
                outsideCityCharge = input.outsideCityCharge,
                nightAllowanceCharge = input.nightAllowanceCharge,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                extraChargeItems = input.extraChargeItems,
                outstationWaitHours = input.outstationWaitHours,
                outstationWaitRatePerHour = input.outstationWaitRatePerHour
            )

            SangamTripType.OUTSTATION_ROUND_TRIP -> calculateOutstation(
                category = category,
                isRoundTrip = true,
                distanceKm = input.distanceKm,
                over12Hours = input.over12Hours,
                outsideCityCharge = input.outsideCityCharge,
                nightAllowanceCharge = input.nightAllowanceCharge,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                extraChargeItems = input.extraChargeItems,
                outstationWaitHours = input.outstationWaitHours,
                outstationWaitRatePerHour = input.outstationWaitRatePerHour
            )

            SangamTripType.DAY_RENTAL -> calculateDayRental(
                category = category,
                distanceKm = input.distanceKm,
                isInnovaCrysta = input.isInnovaCrysta,
                after12Hours = input.over12Hours,
                outsideCityCharge = input.outsideCityCharge,
                nightAllowanceCharge = input.nightAllowanceCharge,
                tollCharge = input.tollCharge,
                permitCharge = input.permitCharge,
                parkingCharge = input.parkingCharge,
                customExtrasTotal = input.customExtrasTotal,
                extraChargeItems = input.extraChargeItems,
                manualCcAmount = input.manualDayRentalCcAmount
            )
        }
    }

    // =========================================================================
    // 0. INDEPENDENT DIRECT METER MODE (NO OTP, NO CC, NO VENDOR SETTLEMENT)
    // =========================================================================

    fun calculateDirectMeterFare(
        directCategory: DirectMeterCategory,
        distanceKm: Double,
        billedHours: Int = 1,
        waitingMinutes: Double = 0.0,
        tariffSnapshot: DirectMeterTariffSnapshot = DirectMeterTariffSnapshot(),
        outsideCityCharge: Double = 0.0,
        nightAllowanceCharge: Double = 0.0,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0,
        extraChargeItems: List<ExtraChargeItem> = emptyList()
    ): SangamSettlementBreakdown {
        val safeDistance = distanceKm.coerceAtLeast(0.0)
        val items = mutableListOf<FareLineItem>()
        var coreTariffSubtotal = BigDecimal.ZERO
        val waitRateBd = bd(tariffSnapshot.waitingChargePerMin)

        val mappedTripType = when (directCategory) {
            DirectMeterCategory.LOCAL -> SangamTripType.LOCAL_NORMAL
            DirectMeterCategory.HOURLY_RENTAL -> SangamTripType.HOURLY_RENTAL
            DirectMeterCategory.OUTSTATION -> SangamTripType.OUTSTATION_ONE_WAY
        }

        when (directCategory) {
            DirectMeterCategory.LOCAL -> {
                val baseAmt = money(bd(tariffSnapshot.localBaseFare))
                val perKmBd = bd(tariffSnapshot.localPerKmRate)
                val distAmt = money(bd(safeDistance).multiply(perKmBd))
                items.add(FareLineItem("Base Fare", "Direct Meter Local", baseAmt))
                items.add(
                    FareLineItem(
                        "Distance Charge",
                        "${String.format(Locale.US, "%.2f", safeDistance)} km × ₹${formatRateNumber(tariffSnapshot.localPerKmRate)}/km",
                        distAmt
                    )
                )
                coreTariffSubtotal = baseAmt.add(distAmt)

                val waitAmt = calculateCustomWaitingCharge(waitingMinutes, waitRateBd)
                if (waitAmt > BigDecimal.ZERO) {
                    items.add(
                        FareLineItem(
                            "Waiting Charge",
                            formatCustomWaitingDetail(waitingMinutes, tariffSnapshot.waitingChargePerMin),
                            waitAmt
                        )
                    )
                    coreTariffSubtotal = coreTariffSubtotal.add(waitAmt)
                }
            }

            DirectMeterCategory.HOURLY_RENTAL -> {
                val hours = billedHours.coerceAtLeast(1)
                val hourlyRateBd = bd(tariffSnapshot.hourlyBaseFare)
                val hourlyCharge = money(hourlyRateBd.multiply(BigDecimal(hours)))
                val includedKmTotal = tariffSnapshot.hourlyIncludedKm * hours
                val extraKm = (safeDistance - includedKmTotal).coerceAtLeast(0.0)
                val extraKmCharge = money(bd(extraKm).multiply(bd(tariffSnapshot.hourlyExtraKmRate)))

                items.add(
                    FareLineItem(
                        "Hourly Rental (${hours}h / ${formatRateNumber(includedKmTotal)} km)",
                        "$hours billed hr × ₹${formatRateNumber(tariffSnapshot.hourlyBaseFare)}/hr (5-min grace)",
                        hourlyCharge
                    )
                )
                if (extraKm > 0.0) {
                    items.add(
                        FareLineItem(
                            "Extra Distance (>${formatRateNumber(includedKmTotal)} km)",
                            "${String.format(Locale.US, "%.2f", extraKm)} km × ₹${formatRateNumber(tariffSnapshot.hourlyExtraKmRate)}/km",
                            extraKmCharge
                        )
                    )
                }
                coreTariffSubtotal = hourlyCharge.add(extraKmCharge)
            }

            DirectMeterCategory.OUTSTATION -> {
                val baseAmt = money(bd(tariffSnapshot.outstationBaseFare))
                val perKmBd = bd(tariffSnapshot.outstationPerKmRate)
                val distAmt = money(bd(safeDistance).multiply(perKmBd))
                items.add(FareLineItem("Outstation Base Fare", "Direct Meter Outstation", baseAmt))
                items.add(
                    FareLineItem(
                        "Outstation Distance Charge",
                        "${String.format(Locale.US, "%.2f", safeDistance)} km × ₹${formatRateNumber(tariffSnapshot.outstationPerKmRate)}/km",
                        distAmt
                    )
                )
                coreTariffSubtotal = baseAmt.add(distAmt)

                val waitAmt = calculateCustomWaitingCharge(waitingMinutes, waitRateBd)
                if (waitAmt > BigDecimal.ZERO) {
                    items.add(
                        FareLineItem(
                            "Waiting Charge",
                            formatCustomWaitingDetail(waitingMinutes, tariffSnapshot.waitingChargePerMin),
                            waitAmt
                        )
                    )
                    coreTariffSubtotal = coreTariffSubtotal.add(waitAmt)
                }
            }
        }

        coreTariffSubtotal = money(coreTariffSubtotal)
        val (additionalTotal, additionalItems) = buildAdditionalAndPassThroughExtras(
            outsideCityCharge = outsideCityCharge,
            nightAllowanceCharge = nightAllowanceCharge,
            tollCharge = tollCharge,
            permitCharge = permitCharge,
            parkingCharge = parkingCharge,
            customExtrasTotal = customExtrasTotal,
            extraChargeItems = extraChargeItems
        )
        items.addAll(additionalItems)

        val customerTotal = money(coreTariffSubtotal.add(additionalTotal))

        // Direct Meter NEVER calculates vendor CC or vendor settlement
        return SangamSettlementBreakdown(
            vehicleCategory = VehicleCategory.MINI,
            tripType = mappedTripType,
            distanceKm = safeDistance,
            tariffFareBeforePassThrough = coreTariffSubtotal,
            passThroughExtrasTotal = additionalTotal,
            customerBillTotal = customerTotal,
            lineItems = items,
            commissionResult = CommissionResult.NotSpecified("Direct Meter — No Vendor Commission"),
            driverBalanceStatus = DriverBalanceStatus.Confirmed(customerTotal),
            meterMode = SangamTariffConfig.METER_MODE_DIRECT,
            directCategory = directCategory
        )
    }

    // =========================================================================
    // 1. LOCAL NORMAL CALCULATION
    // =========================================================================

    fun calculateLocalNormal(
        category: VehicleCategory,
        distanceKm: Double,
        isOutsideCity: Boolean = false,
        isNightTrip: Boolean = false,
        outsideCityCharge: Double = 0.0,
        nightAllowanceCharge: Double = 0.0,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0,
        commissionConfig: LocalCommissionConfig = SangamTariffConfig.defaultRulesConfig.localCommissionConfig,
        waitingMinutes: Double = 0.0,
        extraChargeItems: List<ExtraChargeItem> = emptyList()
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
                detail = "${String.format(Locale.US, "%.2f", distanceKm.coerceAtLeast(0.0))} km × ₹${rate.perKmRate.toInt()}/km",
                amount = distCharge
            )
        )

        val baseAndDistance = money(rate.initialCharge.add(distCharge))

        // Local Normal waiting charge is ₹2/min (pro-rata fractional minutes), shown separately.
        val waitingCharge = calculateLocalWaitingCharge(waitingMinutes)
        if (waitingCharge > BigDecimal.ZERO) {
            items.add(
                FareLineItem(
                    label = "Waiting Charge",
                    detail = formatWaitingMinutesDetail(waitingMinutes),
                    amount = waitingCharge
                )
            )
        }

        val tariffSubtotalWithWaiting = money(baseAndDistance.add(waitingCharge))

        // Additional charges: Outside City Limits & Night Allowance (11 PM–6 AM) are MANUAL amount ONLY!
        val (additionalTotal, additionalItems) = buildAdditionalAndPassThroughExtras(
            outsideCityCharge = outsideCityCharge,
            nightAllowanceCharge = nightAllowanceCharge,
            tollCharge = tollCharge,
            permitCharge = permitCharge,
            parkingCharge = parkingCharge,
            customExtrasTotal = customExtrasTotal,
            extraChargeItems = extraChargeItems
        )
        items.addAll(additionalItems)

        val customerTotal = money(tariffSubtotalWithWaiting.add(additionalTotal))
        // Automatic CC evaluates actual decimal GPS distance AND entire customer bill (> ₹1,000 -> 10% of entire bill)
        val commissionResult = calculateLocalCommission(distanceKm, customerTotal, commissionConfig)
        val driverBalance = computeDriverBalance(customerTotal, commissionResult)

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = SangamTripType.LOCAL_NORMAL,
            distanceKm = distanceKm.coerceAtLeast(0.0),
            tariffFareBeforePassThrough = tariffSubtotalWithWaiting,
            passThroughExtrasTotal = additionalTotal,
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
        outsideCityCharge: Double = 0.0,
        nightAllowanceCharge: Double = 0.0,
        fiveKmPolicy: LocalFiveKmBoundaryPolicy = LocalFiveKmBoundaryPolicy.INCLUDE_IN_1_TO_5_KM_200,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0,
        commissionConfig: LocalCommissionConfig = SangamTariffConfig.defaultRulesConfig.localCommissionConfig,
        waitingMinutes: Double = 0.0,
        extraChargeItems: List<ExtraChargeItem> = emptyList()
    ): SangamSettlementBreakdown {
        val config = SangamTariffConfig.localMinimumBillRates[category]
            ?: return unsupportedBreakdown(category, SangamTripType.LOCAL_MINIMUM_BILL, distanceKm)

        val distBd = bd(distanceKm)
        val items = mutableListOf<FareLineItem>()
        val ambiguities = mutableListOf<BoundaryAmbiguity>()
        var tariffSubtotal: BigDecimal

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
                            items.add(FareLineItem("Minimum Bill (Up to 5 KM)", "${category.displayName} min slab ₹200", money(tariffSubtotal)))
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
                            "${String.format(Locale.US, "%.2f", distanceKm)} km × ₹${config.aboveLimitPerKm.toInt()}/km",
                            distCharge
                        )
                    )
                }
            }
        } else {
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
                        "${String.format(Locale.US, "%.2f", distanceKm)} km × ₹${config.aboveLimitPerKm.toInt()}/km",
                        distCharge
                    )
                )
            }
        }

        tariffSubtotal = money(tariffSubtotal)

        val waitingCharge = calculateLocalWaitingCharge(waitingMinutes)
        if (waitingCharge > BigDecimal.ZERO) {
            items.add(
                FareLineItem(
                    label = "Waiting Charge",
                    detail = formatWaitingMinutesDetail(waitingMinutes),
                    amount = waitingCharge
                )
            )
        }

        val tariffSubtotalWithWaiting = money(tariffSubtotal.add(waitingCharge))
        val (additionalTotal, additionalItems) = buildAdditionalAndPassThroughExtras(
            outsideCityCharge = outsideCityCharge,
            nightAllowanceCharge = nightAllowanceCharge,
            tollCharge = tollCharge,
            permitCharge = permitCharge,
            parkingCharge = parkingCharge,
            customExtrasTotal = customExtrasTotal,
            extraChargeItems = extraChargeItems
        )
        items.addAll(additionalItems)

        val customerTotal = money(tariffSubtotalWithWaiting.add(additionalTotal))
        val commissionResult = calculateLocalCommission(distanceKm, customerTotal, commissionConfig)
        val driverBalance = computeDriverBalance(customerTotal, commissionResult)

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = SangamTripType.LOCAL_MINIMUM_BILL,
            distanceKm = distanceKm.coerceAtLeast(0.0),
            tariffFareBeforePassThrough = tariffSubtotalWithWaiting,
            passThroughExtrasTotal = additionalTotal,
            customerBillTotal = customerTotal,
            lineItems = items,
            commissionResult = commissionResult,
            driverBalanceStatus = driverBalance,
            ambiguities = ambiguities
        )
    }

    // =========================================================================
    // AUTOMATIC LOCAL COMMISSION RULES (NO GAPS, NO OVERLAPS, NO MANUAL CONFIRMATION)
    //
    //  - If ENTIRE Customer Bill > ₹1,000.00 -> 10% of ENTIRE Customer Bill (replaces fixed CC)
    //  - Else (Customer Bill <= ₹1,000.00), using actual decimal GPS distance:
    //      0.00 to 5.00 KM    -> ₹25
    //      >5.00 to 7.50 KM   -> ₹50
    //      >7.50 to 10.00 KM  -> ₹70
    //      >10.00 to 20.00 KM -> ₹80
    //      >20.00 KM          -> ₹100
    // =========================================================================

    fun calculateLocalCommission(
        distanceKm: Double,
        entireCustomerBill: BigDecimal,
        @Suppress("UNUSED_PARAMETER")
        config: LocalCommissionConfig = SangamTariffConfig.defaultRulesConfig.localCommissionConfig
    ): CommissionResult {
        val distBd = BigDecimal.valueOf(distanceKm.coerceAtLeast(0.0))
        val bill = money(entireCustomerBill.coerceAtLeast(BigDecimal.ZERO))

        if (bill > SangamTariffConfig.autoCcBillThreshold) {
            return CommissionResult.Confirmed(
                ruleLabel = "10% (Customer Bill > ₹1,000)",
                amount = money(bill.multiply(SangamTariffConfig.autoCcOverThresholdPercent))
            )
        }

        return when {
            distBd <= BigDecimal("5.00") -> CommissionResult.Confirmed("Local CC (0–5.00 KM)", money(BigDecimal("25")))
            distBd <= BigDecimal("7.50") -> CommissionResult.Confirmed("Local CC (>5.00–7.50 KM)", money(BigDecimal("50")))
            distBd <= BigDecimal("10.00") -> CommissionResult.Confirmed("Local CC (>7.50–10.00 KM)", money(BigDecimal("70")))
            distBd <= BigDecimal("20.00") -> CommissionResult.Confirmed("Local CC (>10.00–20.00 KM)", money(BigDecimal("80")))
            else -> CommissionResult.Confirmed("Local CC (>20.00 KM)", money(BigDecimal("100")))
        }
    }

    // =========================================================================
    // 3. HOURLY RENTAL — UPDATED GET TAXI OTP TARIFFS
    //    Commission is ALREADY INCLUDED in the hourly meter rate — NEVER added twice!
    //    100 KM / 10h duty packages supported for OMNI/Eeco, Ertiga/Rumion, Innova.
    // =========================================================================

    fun calculateHourlyRental(
        category: VehicleCategory,
        packageHours: Int,
        actualDistanceKm: Double,
        use100KmPackage: Boolean = false,
        @Suppress("UNUSED_PARAMETER") waitingMinutes: Double = 0.0,
        outsideCityCharge: Double = 0.0,
        nightAllowanceCharge: Double = 0.0,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0,
        extraChargeItems: List<ExtraChargeItem> = emptyList()
    ): SangamSettlementBreakdown {
        val tariff = SangamTariffConfig.hourlyTariffFor(category)
        val safeDistance = actualDistanceKm.coerceAtLeast(0.0)
        val items = mutableListOf<FareLineItem>()

        val tariffSubtotal: BigDecimal
        val ccAmount: BigDecimal
        val ccRuleLabel: String

        if (use100KmPackage && tariff.package100KmFare != null) {
            val dutyHours = tariff.package100KmDutyHours // 10 hours duty
            val includedKm = tariff.package100KmIncludedKm // 100 km
            val pkgBase = money(tariff.package100KmFare)
            val extraHours = (packageHours - dutyHours).coerceAtLeast(0)
            val extraHoursCharge = money(tariff.hourlyMeterRate.multiply(BigDecimal(extraHours)))
            val extraKm = (safeDistance - includedKm.toDouble()).coerceAtLeast(0.0)
            val extraKmCharge = money(bd(extraKm).multiply(tariff.extraKmRate))

            items.add(
                FareLineItem(
                    label = "100 KM Package (${dutyHours}h Duty)",
                    detail = "${category.displayName} • $includedKm km / ${dutyHours}h package",
                    amount = pkgBase
                )
            )
            if (extraHours > 0) {
                items.add(
                    FareLineItem(
                        label = "Extra Duty Hours (>${dutyHours}h)",
                        detail = "$extraHours hr × ₹${tariff.hourlyMeterRate.toInt()}/hr",
                        amount = extraHoursCharge
                    )
                )
            }
            if (extraKm > 0.0) {
                items.add(
                    FareLineItem(
                        label = "Extra Distance (>${includedKm} km)",
                        detail = "${String.format(Locale.US, "%.2f", extraKm)} km × ₹${tariff.extraKmRate.toInt()}/km",
                        amount = extraKmCharge
                    )
                )
            }

            tariffSubtotal = money(pkgBase.add(extraHoursCharge).add(extraKmCharge))
            val totalBilledHours = dutyHours + extraHours
            ccAmount = money(tariff.commissionPerHour.multiply(BigDecimal(totalBilledHours)))
            ccRuleLabel = "100 KM Package CC (Included ₹${tariff.commissionPerHour.toInt()}/hr × ${totalBilledHours}h)"
        } else {
            val hours = packageHours.coerceAtLeast(tariff.minHours)
            // Hourly meter rate ALREADY includes commission — NEVER add commission twice!
            val packageAmount = money(tariff.hourlyMeterRate.multiply(BigDecimal(hours)))
            val includedKm = hours * tariff.includedKmPerHour
            val extraKm = (safeDistance - includedKm.toDouble()).coerceAtLeast(0.0)
            val extraKmCharge = money(bd(extraKm).multiply(tariff.extraKmRate))

            val minNote = if (tariff.minHours > 1) " • Min ${tariff.minHours}h" else ""
            items.add(
                FareLineItem(
                    label = "Hourly Rental (${hours}h / $includedKm km)",
                    detail = "${category.displayName} • $hours billed hr × ₹${tariff.hourlyMeterRate.toInt()}/hr (5-min grace$minNote)",
                    amount = packageAmount
                )
            )

            if (extraKm > 0.0) {
                items.add(
                    FareLineItem(
                        label = "Extra Distance (>${includedKm} km)",
                        detail = "${String.format(Locale.US, "%.2f", extraKm)} km × ₹${tariff.extraKmRate.toInt()}/km",
                        amount = extraKmCharge
                    )
                )
            }

            // Hourly Rental NEVER charges waiting time.
            tariffSubtotal = money(packageAmount.add(extraKmCharge))
            ccAmount = money(tariff.commissionPerHour.multiply(BigDecimal(hours)))
            ccRuleLabel = "Hourly Rental CC (₹${tariff.commissionPerHour.toInt()}/hr × $hours billed hr — incl. in meter rate)"
        }

        val (additionalTotal, additionalItems) = buildAdditionalAndPassThroughExtras(
            outsideCityCharge = outsideCityCharge,
            nightAllowanceCharge = nightAllowanceCharge,
            tollCharge = tollCharge,
            permitCharge = permitCharge,
            parkingCharge = parkingCharge,
            customExtrasTotal = customExtrasTotal,
            extraChargeItems = extraChargeItems
        )
        items.addAll(additionalItems)

        val customerTotal = money(tariffSubtotal.add(additionalTotal))
        val commissionResult = CommissionResult.Confirmed(
            ruleLabel = ccRuleLabel,
            amount = ccAmount
        )
        val driverBalance = computeDriverBalance(customerTotal, commissionResult)

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = SangamTripType.HOURLY_RENTAL,
            distanceKm = safeDistance,
            tariffFareBeforePassThrough = tariffSubtotal,
            passThroughExtrasTotal = additionalTotal,
            customerBillTotal = customerTotal,
            lineItems = items,
            commissionResult = commissionResult,
            driverBalanceStatus = driverBalance
        )
    }

    // =========================================================================
    // 4. OUTSTATION CALCULATION
    // One-Way: ₹300 + ₹30/km | Round-Trip: ₹500 + ₹15/km | >12h +₹400 | 10% CC
    // =========================================================================

    fun calculateOutstation(
        category: VehicleCategory,
        isRoundTrip: Boolean,
        distanceKm: Double,
        over12Hours: Boolean = false,
        outsideCityCharge: Double = 0.0,
        nightAllowanceCharge: Double = 0.0,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0,
        outstationWaitHours: Double = 0.0,
        outstationWaitRatePerHour: Double = 0.0,
        extraChargeItems: List<ExtraChargeItem> = emptyList()
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
                detail = "${String.format(Locale.US, "%.2f", distanceKm.coerceAtLeast(0.0))} km × ₹${perKmRate.toInt()}/km",
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

        if (outstationWaitHours > 0.0 && outstationWaitRatePerHour > 0.0) {
            val waitAmt = money(bd(outstationWaitHours).multiply(bd(outstationWaitRatePerHour)))
            items.add(
                FareLineItem(
                    label = "Additional Waiting Hours",
                    detail = "${String.format(Locale.US, "%.1f", outstationWaitHours)} hr × ₹${String.format(Locale.US, "%.0f", outstationWaitRatePerHour)}/hr",
                    amount = waitAmt
                )
            )
            tariffSubtotal = tariffSubtotal.add(waitAmt)
        }

        tariffSubtotal = money(tariffSubtotal)
        val (additionalTotal, additionalItems) = buildAdditionalAndPassThroughExtras(
            outsideCityCharge = outsideCityCharge,
            nightAllowanceCharge = nightAllowanceCharge,
            tollCharge = tollCharge,
            permitCharge = permitCharge,
            parkingCharge = parkingCharge,
            customExtrasTotal = customExtrasTotal,
            extraChargeItems = extraChargeItems
        )
        items.addAll(additionalItems)

        val customerTotal = money(tariffSubtotal.add(additionalTotal))
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
            passThroughExtrasTotal = additionalTotal,
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
        outsideCityCharge: Double = 0.0,
        nightAllowanceCharge: Double = 0.0,
        tollCharge: Double = 0.0,
        permitCharge: Double = 0.0,
        parkingCharge: Double = 0.0,
        customExtrasTotal: Double = 0.0,
        extraChargeItems: List<ExtraChargeItem> = emptyList(),
        manualCcAmount: Double? = null
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
                "${String.format(Locale.US, "%.2f", distanceKm.coerceAtLeast(0.0))} km × ₹${rate.perKmRate.toInt()}/km",
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
        val (additionalTotal, additionalItems) = buildAdditionalAndPassThroughExtras(
            outsideCityCharge = outsideCityCharge,
            nightAllowanceCharge = nightAllowanceCharge,
            tollCharge = tollCharge,
            permitCharge = permitCharge,
            parkingCharge = parkingCharge,
            customExtrasTotal = customExtrasTotal,
            extraChargeItems = extraChargeItems
        )
        items.addAll(additionalItems)

        val customerTotal = money(tariffSubtotal.add(additionalTotal))

        val commissionResult = if (manualCcAmount != null && manualCcAmount >= 0.0) {
            CommissionResult.Confirmed(
                ruleLabel = "Day Rental CC (Manual)",
                amount = money(bd(manualCcAmount))
            )
        } else {
            CommissionResult.NotSpecified(
                reason = "Day Rental CC for $catLabel is variable and managed manually per trip (no fixed percentage)."
            )
        }
        val driverBalance = computeDriverBalance(customerTotal, commissionResult)

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = SangamTripType.DAY_RENTAL,
            isInnovaCrysta = useCrysta,
            distanceKm = distanceKm.coerceAtLeast(0.0),
            tariffFareBeforePassThrough = tariffSubtotal,
            passThroughExtrasTotal = additionalTotal,
            customerBillTotal = customerTotal,
            lineItems = items,
            commissionResult = commissionResult,
            driverBalanceStatus = driverBalance
        )
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun formatRateNumber(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
    }

    private fun calculateLocalWaitingCharge(waitingMinutes: Double): BigDecimal {
        return calculateCustomWaitingCharge(waitingMinutes, SangamTariffConfig.localWaitingRatePerMin)
    }

    private fun calculateCustomWaitingCharge(waitingMinutes: Double, ratePerMin: BigDecimal): BigDecimal {
        val safeMinutes = waitingMinutes.coerceAtLeast(0.0)
        if (safeMinutes <= 0.0 || ratePerMin <= BigDecimal.ZERO) return money(BigDecimal.ZERO)
        return money(bd(safeMinutes).multiply(ratePerMin))
    }

    private fun formatWaitingMinutesDetail(waitingMinutes: Double): String {
        return formatCustomWaitingDetail(waitingMinutes, SangamTariffConfig.localWaitingRatePerMin.toDouble())
    }

    private fun formatCustomWaitingDetail(waitingMinutes: Double, ratePerMin: Double): String {
        val safeMinutes = waitingMinutes.coerceAtLeast(0.0)
        val totalSeconds = (safeMinutes * 60.0).roundToLong()
        val mins = totalSeconds / 60L
        val secs = totalSeconds % 60L
        val rateStr = formatRateNumber(ratePerMin)
        return if (secs == 0L) {
            "$mins min × ₹$rateStr/min"
        } else {
            "${mins}m ${secs}s (${String.format(Locale.US, "%.2f", safeMinutes)} min) × ₹$rateStr/min"
        }
    }

    private fun buildAdditionalAndPassThroughExtras(
        outsideCityCharge: Double = 0.0,
        nightAllowanceCharge: Double = 0.0,
        tollCharge: Double,
        permitCharge: Double,
        parkingCharge: Double,
        customExtrasTotal: Double,
        extraChargeItems: List<ExtraChargeItem> = emptyList()
    ): Pair<BigDecimal, List<FareLineItem>> {
        val passItems = mutableListOf<FareLineItem>()
        var total = BigDecimal.ZERO

        if (outsideCityCharge > 0.0) {
            val amt = money(bd(outsideCityCharge))
            total = total.add(amt)
            passItems.add(
                FareLineItem(
                    label = SangamTariffConfig.EXTRA_LABEL_OUTSIDE_CITY,
                    detail = "Manual additional charge",
                    amount = amt,
                    isPassThrough = true
                )
            )
        }
        if (nightAllowanceCharge > 0.0) {
            val amt = money(bd(nightAllowanceCharge))
            total = total.add(amt)
            passItems.add(
                FareLineItem(
                    label = SangamTariffConfig.EXTRA_LABEL_NIGHT_ALLOWANCE,
                    detail = "Manual additional charge",
                    amount = amt,
                    isPassThrough = true
                )
            )
        }
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

        val validExtraItems = extraChargeItems.filter { it.amount > 0.0 }
        if (validExtraItems.isNotEmpty()) {
            validExtraItems.forEach { item ->
                val amt = money(bd(item.amount))
                total = total.add(amt)
                val cleanLabel = item.label.trim()
                val isWaitingItem = cleanLabel.startsWith("Additional Waiting", ignoreCase = true) ||
                    cleanLabel.contains("Waiting", ignoreCase = true)
                val isNamedAdditional = cleanLabel.equals(SangamTariffConfig.EXTRA_LABEL_OUTSIDE_CITY, ignoreCase = true) ||
                    cleanLabel.equals(SangamTariffConfig.EXTRA_LABEL_NIGHT_ALLOWANCE, ignoreCase = true) ||
                    cleanLabel.contains("Outside City", ignoreCase = true) ||
                    cleanLabel.contains("Night Allowance", ignoreCase = true)
                if (isWaitingItem) {
                    val detailText = cleanLabel
                        .replace(Regex("(?i)^Additional\\s+Waiting\\s*"), "")
                        .trim()
                        .removePrefix("(")
                        .removeSuffix(")")
                        .trim()
                        .ifBlank { "Manual additional waiting" }
                    passItems.add(
                        FareLineItem(
                            label = "Additional Waiting",
                            detail = detailText,
                            amount = amt,
                            isPassThrough = true
                        )
                    )
                } else {
                    passItems.add(
                        FareLineItem(
                            label = cleanLabel.ifBlank { "Additional Charge" },
                            detail = if (isNamedAdditional) "Manual additional charge" else "Pass-through customer expense",
                            amount = amt,
                            isPassThrough = true
                        )
                    )
                }
            }
        } else if (customExtrasTotal > 0.0) {
            val amt = money(bd(customExtrasTotal))
            total = total.add(amt)
            passItems.add(FareLineItem("Additional Charges", "Pass-through customer expense", amt, isPassThrough = true))
        }

        return Pair(money(total), passItems)
    }

    private fun computeDriverBalance(
        customerBillTotal: BigDecimal,
        commissionResult: CommissionResult
    ): DriverBalanceStatus {
        return when (commissionResult) {
            is CommissionResult.Confirmed -> {
                val bal = customerBillTotal.subtract(commissionResult.amount).coerceAtLeast(BigDecimal.ZERO)
                DriverBalanceStatus.Confirmed(money(bal))
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
            distanceKm = distanceKm.coerceAtLeast(0.0),
            tariffFareBeforePassThrough = money(BigDecimal.ZERO),
            passThroughExtrasTotal = money(BigDecimal.ZERO),
            customerBillTotal = money(BigDecimal.ZERO),
            lineItems = emptyList(),
            commissionResult = CommissionResult.NotSpecified(
                "${tripType.displayName} is not configured for ${category.displayName}."
            ),
            driverBalanceStatus = DriverBalanceStatus.RangePendingConfirmation(
                reason = "Rate not configured."
            ),
            warnings = listOf("Rate not configured for ${category.displayName} ${tripType.displayName}.")
        )
    }
}
