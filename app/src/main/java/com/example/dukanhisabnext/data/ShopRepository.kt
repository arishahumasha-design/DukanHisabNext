package com.example.dukanhisabnext.data

import com.example.dukanhisabnext.data.entity.ShopProfile

/** Reads and saves the single shop profile row (id = 1). */
class ShopRepository(db: AppDatabase) {
    private val dao = db.shopProfileDao()

    /** null until the owner has filled in "দোকানের তথ্য". */
    suspend fun get(): ShopProfile? = dao.get()

    /** Insert if missing, otherwise update the one row. */
    suspend fun save(profile: ShopProfile) = dao.save(profile.copy(id = ShopProfile.SINGLE_ROW_ID))
}
