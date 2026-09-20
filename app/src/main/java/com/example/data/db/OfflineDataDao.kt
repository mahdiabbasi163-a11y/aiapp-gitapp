package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDataDao {

    // Error Codes Cache
    @Query("SELECT * FROM cached_error_codes")
    fun getAllErrorCodes(): Flow<List<ErrorCodeEntity>>

    @Query("SELECT COUNT(*) FROM cached_error_codes")
    suspend fun getErrorCodeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertErrorCodes(items: List<ErrorCodeEntity>)

    @Query("DELETE FROM cached_error_codes")
    suspend fun clearErrorCodes()

    // Spare Parts Cache
    @Query("SELECT * FROM cached_spare_parts")
    fun getAllSpareParts(): Flow<List<SparePartEntity>>

    @Query("SELECT COUNT(*) FROM cached_spare_parts")
    suspend fun getSparePartCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpareParts(items: List<SparePartEntity>)

    @Query("DELETE FROM cached_spare_parts")
    suspend fun clearSpareParts()

    // Common Problems Cache
    @Query("SELECT * FROM cached_common_problems")
    fun getAllCommonProblems(): Flow<List<CommonProblemEntity>>

    @Query("SELECT COUNT(*) FROM cached_common_problems")
    suspend fun getCommonProblemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommonProblems(items: List<CommonProblemEntity>)

    @Query("DELETE FROM cached_common_problems")
    suspend fun clearCommonProblems()

    // Technicians Cache
    @Query("SELECT * FROM cached_technicians")
    fun getAllTechnicians(): Flow<List<TechnicianEntity>>

    @Query("SELECT COUNT(*) FROM cached_technicians")
    suspend fun getTechnicianCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTechnicians(items: List<TechnicianEntity>)

    @Query("DELETE FROM cached_technicians")
    suspend fun clearTechnicians()

    @Transaction
    suspend fun updateErrorCodesAndProblems(
        errorCodes: List<ErrorCodeEntity>,
        commonProblems: List<CommonProblemEntity>
    ) {
        if (errorCodes.isNotEmpty()) {
            clearErrorCodes()
            insertErrorCodes(errorCodes)
        }
        if (commonProblems.isNotEmpty()) {
            clearCommonProblems()
            insertCommonProblems(commonProblems)
        }
    }

    // Atomic Sync Transaction - NEVER clear if incoming data is empty!
    @Transaction
    suspend fun updateAllOfflineData(
        errorCodes: List<ErrorCodeEntity>,
        spareParts: List<SparePartEntity>,
        commonProblems: List<CommonProblemEntity>,
        technicians: List<TechnicianEntity>
    ) {
        if (errorCodes.isNotEmpty()) {
            clearErrorCodes()
            insertErrorCodes(errorCodes)
        }
        if (spareParts.isNotEmpty()) {
            clearSpareParts()
            insertSpareParts(spareParts)
        }
        if (commonProblems.isNotEmpty()) {
            clearCommonProblems()
            insertCommonProblems(commonProblems)
        }
        if (technicians.isNotEmpty()) {
            clearTechnicians()
            insertTechnicians(technicians)
        }
    }

    @Transaction
    suspend fun clearAllOfflineCache() {
        clearErrorCodes()
        clearSpareParts()
        clearCommonProblems()
        clearTechnicians()
    }
}
