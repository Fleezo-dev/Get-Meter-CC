package com.example.util

import com.example.data.database.DriverProfileEntity
import com.example.data.database.TripEntity
import com.example.util.SangamTariffCalculator.CommissionResult
import com.example.util.SangamTariffCalculator.DriverBalanceStatus
import com.example.util.SangamTariffCalculator.FareLineItem
import com.example.util.SangamTariffCalculator.SangamSettlementBreakdown
import com.example.util.SangamTariffConfig.SangamTripType
import com.example.util.SangamTariffConfig.VehicleCategory
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Immutable Financial Snapshot Mapper for GET TAXI Local Trip History.
 *
 * Persists the exact customer fare breakdown, approved CC rule/amount/range, and driver net
 * balance into [TripEntity] at trip completion time, and reconstructs [SangamSettlementBreakdown]
 * directly from saved values WITHOUT recalculating against live tariff configuration.
 */
object TripSnapshotMapper {

    const val CC_STATUS_CONFIRMED = "CONFIRMED"
    const val CC_STATUS_NEEDS_CONFIRMATION = "NEEDS_CONFIRMATION"
    const val CC_STATUS_NOT_SPECIFIED = "NOT_SPECIFIED"

    const val SETTLEMENT_PENDING = "PENDING"
    const val SETTLEMENT_CONFIRMED = "CONFIRMED"

    fun serializeLineItems(items: List<FareLineItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("label", item.label)
                put("detail", item.detail)
                put("amount", item.amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                put("isPassThrough", item.isPassThrough)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun deserializeLineItems(json: String): List<FareLineItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val result = mutableListOf<FareLineItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val amountStr = obj.optString("amount", "0.00")
                val amount = try {
                    BigDecimal(amountStr).setScale(2, RoundingMode.HALF_UP)
                } catch (_: Exception) {
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                }
                result.add(
                    FareLineItem(
                        label = obj.optString("label", "Fare Item"),
                        detail = obj.optString("detail", ""),
                        amount = amount,
                        isPassThrough = obj.optBoolean("isPassThrough", false)
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Stamps immutable financial, vehicle, trip-type, CC, and driver-balance snapshot fields
     * onto a [TripEntity] before persisting it to Room.
     */
    fun attachFinancialSnapshot(
        baseEntity: TripEntity,
        input: SangamTariffCalculator.TripCalculationInput,
        breakdown: SangamSettlementBreakdown,
        driverProfile: DriverProfileEntity? = null,
        settlementStatus: String = SETTLEMENT_PENDING
    ): TripEntity {
        val (ccStatus, ccRuleLabel, ccAmount, ccMin, ccMax) = when (val cc = breakdown.commissionResult) {
            is CommissionResult.Confirmed -> SnapshotCc(
                status = CC_STATUS_CONFIRMED,
                ruleLabel = cc.ruleLabel,
                amount = cc.amount.setScale(2, RoundingMode.HALF_UP).toDouble(),
                minAmount = cc.amount.setScale(2, RoundingMode.HALF_UP).toDouble(),
                maxAmount = cc.amount.setScale(2, RoundingMode.HALF_UP).toDouble()
            )
            is CommissionResult.NeedsConfirmation -> SnapshotCc(
                status = CC_STATUS_NEEDS_CONFIRMATION,
                ruleLabel = cc.reason,
                amount = 0.0,
                minAmount = cc.minPossibleAmount?.setScale(2, RoundingMode.HALF_UP)?.toDouble() ?: -1.0,
                maxAmount = cc.maxPossibleAmount?.setScale(2, RoundingMode.HALF_UP)?.toDouble() ?: -1.0
            )
            is CommissionResult.NotSpecified -> SnapshotCc(
                status = CC_STATUS_NOT_SPECIFIED,
                ruleLabel = cc.reason,
                amount = 0.0,
                minAmount = -1.0,
                maxAmount = -1.0
            )
        }

        val (driverNet, driverMin, driverMax) = when (val bal = breakdown.driverBalanceStatus) {
            is DriverBalanceStatus.Confirmed -> Triple(
                bal.amount.setScale(2, RoundingMode.HALF_UP).toDouble(),
                bal.amount.setScale(2, RoundingMode.HALF_UP).toDouble(),
                bal.amount.setScale(2, RoundingMode.HALF_UP).toDouble()
            )
            is DriverBalanceStatus.RangePendingConfirmation -> Triple(
                0.0,
                bal.minDriverBalance?.setScale(2, RoundingMode.HALF_UP)?.toDouble() ?: -1.0,
                bal.maxDriverBalance?.setScale(2, RoundingMode.HALF_UP)?.toDouble() ?: -1.0
            )
        }

        return baseEntity.copy(
            totalFare = breakdown.customerBillTotal.setScale(2, RoundingMode.HALF_UP).toDouble(),
            vehicleCategoryId = breakdown.vehicleCategory.id,
            sangamTripTypeId = breakdown.tripType.id,
            isInnovaCrysta = breakdown.isInnovaCrysta,
            isNightTrip = input.isNightTrip,
            over12Hours = input.over12Hours,
            driverIdSnapshot = driverProfile?.driverId ?: baseEntity.driverIdSnapshot,
            fareBreakdownJson = serializeLineItems(breakdown.lineItems),
            tariffSubtotal = breakdown.tariffFareBeforePassThrough.setScale(2, RoundingMode.HALF_UP).toDouble(),
            passThroughExtrasTotal = breakdown.passThroughExtrasTotal.setScale(2, RoundingMode.HALF_UP).toDouble(),
            ccStatus = ccStatus,
            ccRuleLabel = ccRuleLabel,
            ccAmount = ccAmount,
            ccMinAmount = ccMin,
            ccMaxAmount = ccMax,
            driverNetBalance = driverNet,
            driverBalanceMin = driverMin,
            driverBalanceMax = driverMax,
            settlementStatus = settlementStatus,
            // Ensure customer-facing invoice entity NEVER stores GPay number
            vendorContactNumber = "",
            vendorInvoiceDetails = ""
        )
    }

    /**
     * Reconstructs the EXACT historical [SangamSettlementBreakdown] directly from saved
     * [TripEntity] snapshot columns. NEVER calls [SangamTariffCalculator.calculateTripFareAndSettlement]
     * so historical invoices and vendor reports stay immutable even if tariffs change later.
     */
    fun toBreakdown(trip: TripEntity): SangamSettlementBreakdown {
        val category = VehicleCategory.fromId(trip.vehicleCategoryId)
        val tripType = SangamTripType.fromId(trip.sangamTripTypeId)

        val customerTotal = BigDecimal.valueOf(trip.totalFare).setScale(2, RoundingMode.HALF_UP)
        val passThrough = BigDecimal.valueOf(trip.passThroughExtrasTotal.coerceAtLeast(0.0))
            .setScale(2, RoundingMode.HALF_UP)
        val tariffSubtotal = if (trip.tariffSubtotal > 0.0 || trip.fareBreakdownJson.isNotBlank()) {
            BigDecimal.valueOf(trip.tariffSubtotal.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP)
        } else {
            customerTotal.subtract(passThrough).coerceAtLeast(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        }

        val savedItems = deserializeLineItems(trip.fareBreakdownJson).ifEmpty {
            listOf(
                FareLineItem(
                    label = "${tripType.displayName} Fare",
                    detail = "${if (trip.isInnovaCrysta) "INNOVA CRYSTA" else category.displayName} • ${String.format(java.util.Locale.US, "%.2f", trip.distanceKm)} km",
                    amount = customerTotal
                )
            )
        }

        val commissionResult: CommissionResult = when (trip.ccStatus) {
            CC_STATUS_CONFIRMED -> CommissionResult.Confirmed(
                ruleLabel = trip.ccRuleLabel.ifBlank { "Approved CC" },
                amount = BigDecimal.valueOf(trip.ccAmount.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP)
            )
            CC_STATUS_NEEDS_CONFIRMATION -> CommissionResult.NeedsConfirmation(
                reason = trip.ccRuleLabel.ifBlank { "CC confirmation required" },
                minPossibleAmount = if (trip.ccMinAmount >= 0.0) BigDecimal.valueOf(trip.ccMinAmount).setScale(2, RoundingMode.HALF_UP) else null,
                maxPossibleAmount = if (trip.ccMaxAmount >= 0.0) BigDecimal.valueOf(trip.ccMaxAmount).setScale(2, RoundingMode.HALF_UP) else null
            )
            CC_STATUS_NOT_SPECIFIED -> CommissionResult.NotSpecified(
                reason = trip.ccRuleLabel.ifBlank { "CC not specified for this trip" }
            )
            else -> CommissionResult.Confirmed(
                ruleLabel = trip.ccRuleLabel.ifBlank { "Saved CC" },
                amount = BigDecimal.valueOf(trip.ccAmount.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP)
            )
        }

        val driverBalanceStatus: DriverBalanceStatus = when (commissionResult) {
            is CommissionResult.Confirmed -> {
                val savedNet = if (trip.driverNetBalance > 0.0 || trip.ccAmount > 0.0 || trip.totalFare == 0.0) {
                    BigDecimal.valueOf(trip.driverNetBalance.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP)
                } else {
                    customerTotal.subtract(commissionResult.amount).coerceAtLeast(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP)
                }
                DriverBalanceStatus.Confirmed(savedNet)
            }
            is CommissionResult.NeedsConfirmation -> DriverBalanceStatus.RangePendingConfirmation(
                minDriverBalance = if (trip.driverBalanceMin >= 0.0) BigDecimal.valueOf(trip.driverBalanceMin).setScale(2, RoundingMode.HALF_UP) else null,
                maxDriverBalance = if (trip.driverBalanceMax >= 0.0) BigDecimal.valueOf(trip.driverBalanceMax).setScale(2, RoundingMode.HALF_UP) else null,
                reason = commissionResult.reason
            )
            is CommissionResult.NotSpecified -> DriverBalanceStatus.RangePendingConfirmation(
                minDriverBalance = null,
                maxDriverBalance = null,
                reason = commissionResult.reason
            )
        }

        return SangamSettlementBreakdown(
            vehicleCategory = category,
            tripType = tripType,
            isInnovaCrysta = trip.isInnovaCrysta,
            distanceKm = trip.distanceKm,
            tariffFareBeforePassThrough = tariffSubtotal,
            passThroughExtrasTotal = passThrough,
            customerBillTotal = customerTotal,
            lineItems = savedItems,
            commissionResult = commissionResult,
            driverBalanceStatus = driverBalanceStatus
        )
    }

    private data class SnapshotCc(
        val status: String,
        val ruleLabel: String,
        val amount: Double,
        val minAmount: Double,
        val maxAmount: Double
    )
}
