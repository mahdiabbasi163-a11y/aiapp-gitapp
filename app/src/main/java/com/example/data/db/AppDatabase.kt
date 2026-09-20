package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        SavedErrorEntity::class,
        CustomErrorEntity::class,
        ErrorCodeEntity::class,
        SparePartEntity::class,
        CommonProblemEntity::class,
        TechnicianEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assistantDao(): AssistantDao
    abstract fun offlineDataDao(): OfflineDataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration 1 to 2 if needed
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cached_error_codes` (
                        `id` TEXT NOT NULL,
                        `code` TEXT,
                        `brand` TEXT,
                        `category` TEXT,
                        `title` TEXT,
                        `description` TEXT,
                        `causesRaw` TEXT,
                        `stepsRaw` TEXT,
                        `hazardLevel` TEXT,
                        `videoUrl` TEXT,
                        `isApproved` INTEGER,
                        `model` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cached_spare_parts` (
                        `id` TEXT NOT NULL,
                        `name` TEXT,
                        `brand` TEXT,
                        `price` REAL,
                        `stock` INTEGER,
                        `image` TEXT,
                        `imageUrl` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cached_common_problems` (
                        `id` TEXT NOT NULL,
                        `title` TEXT,
                        `brand` TEXT,
                        `category` TEXT,
                        `description` TEXT,
                        `causesRaw` TEXT,
                        `stepsRaw` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cached_technicians` (
                        `id` TEXT NOT NULL,
                        `name` TEXT,
                        `city` TEXT,
                        `isVerified` INTEGER,
                        `completedOrders` INTEGER,
                        `bio` TEXT,
                        `categoriesRaw` TEXT,
                        `rating` REAL,
                        `satisfactionRate` INTEGER,
                        `image` TEXT,
                        `imageUrl` TEXT,
                        `documentsRaw` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure schema consistency for version 4
            }
        }

        private fun buildRoomDb(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "nova_assistant_database"
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = try {
                    buildRoomDb(context)
                } catch (e: Throwable) {
                    android.util.Log.e("AppDatabase", "Failed to build database, wiping corrupt db", e)
                    try {
                        context.applicationContext.deleteDatabase("nova_assistant_database")
                    } catch (_: Throwable) {}
                    buildRoomDb(context)
                }
                INSTANCE = instance
                instance
            }
        }
    }
}
