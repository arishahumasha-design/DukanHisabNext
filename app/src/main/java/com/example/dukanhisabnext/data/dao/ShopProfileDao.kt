package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.dukanhisabnext.data.entity.ShopProfile
import kotlinx.coroutines.flow.Flow

// A DAO ("Data Access Object") is the door to the database: each function is one query.
// "suspend" = runs in the background once. "Flow" = a live stream that re-emits
// whenever the data changes, so the screen updates by itself.
@Dao
interface ShopProfileDao {
    /** null until the user fills in the shop profile. */
    @Query("SELECT * FROM shop_profile WHERE id = ${ShopProfile.SINGLE_ROW_ID}")
    fun observe(): Flow<ShopProfile?>

    /** The shop profile once (for a receipt, or to fill the form). null until it is filled in. */
    @Query("SELECT * FROM shop_profile WHERE id = ${ShopProfile.SINGLE_ROW_ID}")
    suspend fun get(): ShopProfile?

    /** Upsert = insert if missing, otherwise update. */
    @Upsert
    suspend fun save(profile: ShopProfile)
}
