package com.example.dukanhisabnext.data

import androidx.room.withTransaction
import com.example.dukanhisabnext.data.entity.ShopProfile
import kotlinx.coroutines.flow.Flow

/**
 * Reads and saves the single shop profile row (id = 1). The Settings owner card, the "দোকানের
 * তথ্য" screen and the receipt header all use this same one row.
 */
class ShopRepository(private val db: AppDatabase) {
    private val dao = db.shopProfileDao()

    /** null until the owner has filled in something. */
    suspend fun get(): ShopProfile? = dao.get()

    /** The profile as a live stream: Settings updates by itself after "দোকানের তথ্য" is saved. */
    fun observe(): Flow<ShopProfile?> = dao.observe()

    /** Insert if missing, otherwise update the one row. */
    suspend fun save(profile: ShopProfile) = dao.save(profile.copy(id = ShopProfile.SINGLE_ROW_ID))

    /**
     * Changes only the owner name and the mobile, keeping the shop name and address.
     * If there is no row yet (the owner is filled in before any shop), one is created with an
     * empty shop name, which counts as "no shop".
     */
    suspend fun updateOwner(ownerName: String?, phone: String?) {
        db.withTransaction {
            val current = dao.get() ?: ShopProfile(name = "")
            dao.save(current.copy(ownerName = ownerName, phone = phone))
        }
    }
}
