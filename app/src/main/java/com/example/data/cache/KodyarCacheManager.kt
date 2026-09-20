package com.example.data.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Clean In-Memory and Configurable Cache Manager for Kodyar.
 * 
 * Strict caching rules:
 * - ONLY Error Codes and Common Problems / Troubleshooting are cached on device.
 * - Everything else (Subscription status, Plans, Users, Technicians, Spare Parts, Orders) 
 *   is ALWAYS loaded directly and with high speed from the database / API.
 */
object KodyarCacheManager {

    // ONLY Error codes and problems are cached on the device
    val TTL_ERROR_CODES_MS: Long = TimeUnit.DAYS.toMillis(365) // Permanently cached on device (Error Codes)
    val TTL_COMMON_PROBLEMS_MS: Long = TimeUnit.DAYS.toMillis(365) // Permanently cached on device (Common Problems)
    val TTL_BRANDS_CATEGORIES_MS: Long = TimeUnit.DAYS.toMillis(365) // Permanently cached on device (Brands & Categories)
    
    // Everything else is NOT cached (0ms TTL = always fresh from database)
    val TTL_TECHNICIANS_MS: Long = 0L // Always fresh from Database / API
    val TTL_SUBSCRIPTION_PLANS_MS: Long = 0L // Always fresh from Database / API
    val TTL_CARD_INFO_MS: Long = 0L // Always fresh from Database / API
    val TTL_SPARE_PARTS_MS: Long = 0L // Always fresh from Database / API

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val ttlMillis: Long
    ) {
        fun isExpired(): Boolean {
            return (System.currentTimeMillis() - timestamp) > ttlMillis
        }
    }

    private val memoryStore = ConcurrentHashMap<String, CacheEntry<*>>()

    /**
     * Put an item into memory cache with custom TTL.
     */
    fun <T> put(key: String, data: T, ttlMillis: Long) {
        memoryStore[key] = CacheEntry(
            data = data,
            timestamp = System.currentTimeMillis(),
            ttlMillis = ttlMillis
        )
    }

    /**
     * Retrieve an item if present and not expired.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = memoryStore[key] as? CacheEntry<T> ?: return null
        if (entry.isExpired()) {
            memoryStore.remove(key)
            return null
        }
        return entry.data
    }

    /**
     * Check if a cache key is valid and fresh.
     */
    fun isValid(key: String): Boolean {
        val entry = memoryStore[key] ?: return false
        return !entry.isExpired()
    }

    /**
     * Invalidate specific key or parts of cache
     */
    fun invalidate(key: String) {
        memoryStore.remove(key)
    }

    /**
     * Invalidate spare parts cache on demand (e.g. after ordering a part or manual refresh)
     */
    fun invalidateSpareParts() {
        memoryStore.remove("spare_parts")
        memoryStore.remove("kodyar_database")
    }

    fun clearAll() {
        memoryStore.clear()
    }
}
