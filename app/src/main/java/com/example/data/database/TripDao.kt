package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY endTime DESC, startTime DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: Int): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    fun getTripByIdFlow(id: Int): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE tripNumber = :tripNumber LIMIT 1")
    suspend fun getTripByTripNumber(tripNumber: String): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    /**
     * Persists a completed trip idempotently. If a trip with the same non-blank [TripEntity.tripNumber]
     * already exists, returns its existing ID without creating a duplicate or overwriting immutable
     * original financial fields.
     */
    @Transaction
    suspend fun saveCompletedTripIdempotent(trip: TripEntity): Long {
        if (trip.tripNumber.isNotBlank()) {
            val existing = getTripByTripNumber(trip.tripNumber)
            if (existing != null) {
                return existing.id.toLong()
            }
        }
        return insertTrip(trip)
    }

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query(
        "UPDATE trips SET customerName = :name, customerMobile = :mobile, customerAddress = :address WHERE id = :tripId"
    )
    suspend fun updateTripCustomerInfo(tripId: Int, name: String, mobile: String, address: String)

    @Query("UPDATE trips SET settlementStatus = :status WHERE id = :tripId")
    suspend fun updateTripSettlementStatus(tripId: Int, status: String)

    @Query(
        """
        UPDATE trips
        SET ccStatus = :ccStatus,
            ccRuleLabel = :ccRuleLabel,
            ccAmount = :ccAmount,
            ccMinAmount = :ccAmount,
            ccMaxAmount = :ccAmount,
            driverNetBalance = :driverNetBalance,
            driverBalanceMin = :driverNetBalance,
            driverBalanceMax = :driverNetBalance
        WHERE id = :tripId
        """
    )
    suspend fun updateTripManualCc(
        tripId: Int,
        ccStatus: String,
        ccRuleLabel: String,
        ccAmount: Double,
        driverNetBalance: Double
    )

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripById(id: Int)

    @Query("SELECT COUNT(*) FROM trips WHERE startTime >= :startOfDay AND startTime < :endOfDay")
    suspend fun getDailyTripCount(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT * FROM active_trip LIMIT 1")
    suspend fun getActiveTrip(): ActiveTripRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveTrip(activeTrip: ActiveTripRecord)

    @Query("DELETE FROM active_trip")
    suspend fun clearActiveTrip()

    // Driver Profile queries
    @Query("SELECT * FROM driver_profile WHERE id = 1 LIMIT 1")
    fun getDriverProfileFlow(): Flow<DriverProfileEntity?>

    @Query("SELECT * FROM driver_profile WHERE id = 1 LIMIT 1")
    suspend fun getDriverProfile(): DriverProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDriverProfile(profile: DriverProfileEntity)

    // Multi-Vendor Profile queries
    @Query("SELECT * FROM vendor_profiles ORDER BY isOriginalGetTaxi DESC, sortOrder ASC, vendorName ASC")
    fun getAllVendorsFlow(): Flow<List<VendorProfileEntity>>

    @Query("SELECT * FROM vendor_profiles WHERE isActive = 1 ORDER BY isOriginalGetTaxi DESC, sortOrder ASC, vendorName ASC")
    fun getActiveVendorsFlow(): Flow<List<VendorProfileEntity>>

    @Query("SELECT * FROM vendor_profiles ORDER BY isOriginalGetTaxi DESC, sortOrder ASC, vendorName ASC")
    suspend fun getAllVendors(): List<VendorProfileEntity>

    @Query("SELECT * FROM vendor_profiles WHERE isActive = 1 ORDER BY isOriginalGetTaxi DESC, sortOrder ASC, vendorName ASC")
    suspend fun getActiveVendors(): List<VendorProfileEntity>

    @Query("SELECT * FROM vendor_profiles WHERE vendorId = :vendorId LIMIT 1")
    suspend fun getVendorById(vendorId: String): VendorProfileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVendorsIgnore(vendors: List<VendorProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVendor(vendor: VendorProfileEntity)

    @Update
    suspend fun updateVendor(vendor: VendorProfileEntity)

    @Query("UPDATE vendor_profiles SET isActive = :isActive WHERE vendorId = :vendorId AND isOriginalGetTaxi = 0")
    suspend fun setVendorActiveStatus(vendorId: String, isActive: Boolean)
}
