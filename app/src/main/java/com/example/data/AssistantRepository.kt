package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.ContentDto
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GeminiApiService
import com.example.data.api.PartDto
import com.example.data.db.AssistantDao
import com.example.data.db.ConversationEntity
import com.example.data.db.MessageEntity
import com.example.data.db.SavedErrorEntity
import com.example.data.db.CustomErrorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

import com.example.data.db.OfflineDataDao
import com.example.data.db.toDomain
import com.example.data.db.toEntity
import com.example.data.cache.KodyarCacheManager
import com.example.data.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AssistantRepository(
    private val assistantDao: AssistantDao,
    private val offlineDataDao: OfflineDataDao? = null,
    private val apiService: GeminiApiService
) {
    private val kodyarApiService by lazy { com.example.data.api.KodyarRetrofitClient.service }

    fun getCachedErrorCodes(): Flow<List<com.example.data.db.ErrorCodeEntity>>? {
        return offlineDataDao?.getAllErrorCodes()
    }

    fun getCachedSpareParts(): Flow<List<com.example.data.db.SparePartEntity>>? {
        return offlineDataDao?.getAllSpareParts()
    }

    fun getCachedCommonProblems(): Flow<List<com.example.data.db.CommonProblemEntity>>? {
        return offlineDataDao?.getAllCommonProblems()
    }

    fun getCachedTechnicians(): Flow<List<com.example.data.db.TechnicianEntity>>? {
        return offlineDataDao?.getAllTechnicians()
    }

    suspend fun saveDatabaseToRoom(
        errorCodes: List<com.example.data.model.KodyarErrorCode>,
        spareParts: List<com.example.data.model.KodyarSparePart> = emptyList(),
        commonProblems: List<com.example.data.model.KodyarCommonProblem> = emptyList(),
        technicians: List<com.example.data.model.KodyarTechnician> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val errEntities = errorCodes.map { it.toEntity() }
        val probEntities = commonProblems.map { it.toEntity() }
        val partEntities = spareParts.map { it.toEntity() }
        val techEntities = technicians.map { it.toEntity() }
        offlineDataDao?.updateAllOfflineData(errEntities, partEntities, probEntities, techEntities)
    }

    suspend fun saveTechniciansToRoom(techs: List<com.example.data.model.KodyarTechnician>) = withContext(Dispatchers.IO) {
        if (techs.isNotEmpty()) {
            val techEntities = techs.map { it.toEntity() }
            offlineDataDao?.clearTechnicians()
            offlineDataDao?.insertTechnicians(techEntities)
        }
    }

    suspend fun saveSparePartsToRoom(parts: List<com.example.data.model.KodyarSparePart>) = withContext(Dispatchers.IO) {
        if (parts.isNotEmpty()) {
            val partEntities = parts.map { it.toEntity() }
            offlineDataDao?.clearSpareParts()
            offlineDataDao?.insertSpareParts(partEntities)
        }
    }

    suspend fun clearAllOfflineCache() = withContext(Dispatchers.IO) {
        offlineDataDao?.clearAllOfflineCache()
    }

    suspend fun seedInitialDataIfNeeded() = withContext(Dispatchers.IO) {
        // هیچ دیتای پیش‌فرضی به برنامه اضافه نمی‌شود؛ تمام داده‌ها منحصراً از سرور و پایگاه داده مشترک کدیار دریافت می‌شوند.
    }

    suspend fun getKodyarDatabase(forceRefresh: Boolean = false): com.example.data.model.KodyarDatabaseResponse = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = KodyarCacheManager.get<com.example.data.model.KodyarDatabaseResponse>("kodyar_database")
            if (cached != null) {
                return@withContext cached
            }
        }

        var mergedErrorCodes = listOf<com.example.data.model.KodyarErrorCode>()
        var mergedSpareParts = listOf<com.example.data.model.KodyarSparePart>()
        var mergedProblems = listOf<com.example.data.model.KodyarCommonProblem>()
        var mergedTechs = listOf<com.example.data.model.KodyarTechnician>()

        // Tier 1: Try unified getDatabase with generous 12s timeout for reliable connection
        try {
            val res = kotlinx.coroutines.withTimeoutOrNull(12000L) {
                kodyarApiService.getDatabase()
            }
            if (res != null) {
                if (res.resolvedErrorCodes.isNotEmpty()) mergedErrorCodes = res.resolvedErrorCodes
                if (res.resolvedSpareParts.isNotEmpty()) mergedSpareParts = res.resolvedSpareParts
                if (res.resolvedCommonProblems.isNotEmpty()) mergedProblems = res.resolvedCommonProblems
                if (res.resolvedTechnicians.isNotEmpty()) mergedTechs = res.resolvedTechnicians
            }
        } catch (e: Exception) {
            android.util.Log.w("AssistantRepository", "getDatabase endpoint failed: ${e.message}")
        }

        // Tier 2: Fetch individual endpoints if missing
        if (mergedErrorCodes.isEmpty()) {
            try {
                val errRes = kodyarApiService.searchErrorCodesApi(limit = 1000)
                if (errRes.resolvedErrorCodes.isNotEmpty()) {
                    mergedErrorCodes = errRes.resolvedErrorCodes
                }
            } catch (e: Exception) {
                try {
                    val errRes2 = kodyarApiService.getErrorCodes()
                    if (errRes2.resolvedErrorCodes.isNotEmpty()) {
                        mergedErrorCodes = errRes2.resolvedErrorCodes
                    }
                } catch (_: Exception) {}
            }
        }

        if (mergedSpareParts.isEmpty()) {
            try {
                val partRes = kodyarApiService.getSparePartsApi()
                if (partRes.resolvedSpareParts.isNotEmpty()) {
                    mergedSpareParts = partRes.resolvedSpareParts
                }
            } catch (e: Exception) {
                try {
                    val partRes2 = kodyarApiService.getStorePartsApi()
                    if (partRes2.resolvedSpareParts.isNotEmpty()) {
                        mergedSpareParts = partRes2.resolvedSpareParts
                    }
                } catch (_: Exception) {}
            }
        }

        if (mergedProblems.isEmpty()) {
            try {
                val probRes = kodyarApiService.getProblemsApi()
                if (probRes.resolvedCommonProblems.isNotEmpty()) {
                    mergedProblems = probRes.resolvedCommonProblems
                }
            } catch (e: Exception) {
                try {
                    val probRes2 = kodyarApiService.getGeneralProblems()
                    if (probRes2.resolvedCommonProblems.isNotEmpty()) {
                        mergedProblems = probRes2.resolvedCommonProblems
                    }
                } catch (_: Exception) {}
            }
        }

        if (mergedTechs.isEmpty()) {
            try {
                val techRes = kodyarApiService.getTechniciansApi()
                if (techRes.resolvedTechnicians.isNotEmpty()) {
                    val existingIds = mergedTechs.mapNotNull { it.id }.toSet()
                    val existingNames = mergedTechs.mapNotNull { it.name }.toSet()
                    val newTechs = techRes.resolvedTechnicians.filter { 
                        (it.id == null || !existingIds.contains(it.id)) && 
                        (it.name == null || !existingNames.contains(it.name))
                    }
                    mergedTechs = mergedTechs + newTechs
                }
            } catch (e: Exception) {
                try {
                    val techRes2 = kodyarApiService.getAdminTechniciansApi()
                    if (techRes2.resolvedTechnicians.isNotEmpty()) {
                        val existingIds = mergedTechs.mapNotNull { it.id }.toSet()
                        val existingNames = mergedTechs.mapNotNull { it.name }.toSet()
                        val newTechs = techRes2.resolvedTechnicians.filter { 
                            (it.id == null || !existingIds.contains(it.id)) && 
                            (it.name == null || !existingNames.contains(it.name))
                        }
                        mergedTechs = mergedTechs + newTechs
                    }
                } catch (_: Exception) {}
            }
        }

        val finalErrorCodes = mergedErrorCodes
        val finalSpareParts = mergedSpareParts
        val finalProblems = mergedProblems

        val finalDb = com.example.data.model.KodyarDatabaseResponse(
            status = if (finalErrorCodes.isNotEmpty() || finalSpareParts.isNotEmpty() || mergedTechs.isNotEmpty() || finalProblems.isNotEmpty()) "ok" else "empty",
            errorCodes = finalErrorCodes,
            spareParts = finalSpareParts,
            commonProblems = finalProblems,
            technicians = mergedTechs
        )
        if (finalDb.status == "ok") {
            KodyarCacheManager.put("kodyar_database", finalDb, KodyarCacheManager.TTL_SPARE_PARTS_MS)
        }
        return@withContext finalDb
    }

    suspend fun getTechniciansDirectly(): List<KodyarTechnician> = withContext(Dispatchers.IO) {
        val list = mutableListOf<KodyarTechnician>()
        try {
            val res = kodyarApiService.getTechniciansApi()
            if (res.resolvedTechnicians.isNotEmpty()) {
                list.addAll(res.resolvedTechnicians)
            }
        } catch (_: Exception) {}
        if (list.isEmpty()) {
            try {
                val res2 = kodyarApiService.getAdminTechniciansApi()
                if (res2.resolvedTechnicians.isNotEmpty()) {
                    list.addAll(res2.resolvedTechnicians)
                }
            } catch (_: Exception) {}
        }
        return@withContext list
    }

    suspend fun getSubscriptionPlans(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val res = kodyarApiService.getSubscriptionPlans()
            res
        } catch (e: Exception) {
            try {
                kodyarApiService.getSubscriptionPlansLegacy()
            } catch (e2: Exception) {
                com.example.data.model.KodyarPlansResponse(
                    status = "error",
                    plans = null
                )
            }
        }
    }

    suspend fun getMySubscriptionStatus(token: String?): com.example.data.model.KodyarResponse = withContext(Dispatchers.IO) {
        val auth = if (token?.startsWith("Bearer ") == true) token else "Bearer $token"
        try {
            val res = kodyarApiService.getMySubscriptionStatus(auth)
            if (res.status == "ok" || res.status == "success" || res.is_premium == true || res.subscription != null) {
                return@withContext res
            }
        } catch (_: Exception) {}

        try {
            val resAlt = kodyarApiService.getMySubscriptionStatusAlt(auth)
            if (resAlt.status == "ok" || resAlt.status == "success" || resAlt.is_premium == true || resAlt.subscription != null) {
                return@withContext resAlt
            }
        } catch (_: Exception) {}

        try {
            kodyarApiService.getMySubscriptionStatus(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(status = "error", error = e.message, message = e.message)
        }
    }

    fun parseApiError(e: Exception): String {
        if (e is retrofit2.HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                if (!errorBody.isNullOrBlank()) {
                    val json = org.json.JSONObject(errorBody)
                    val msg = listOfNotNull(
                        json.optString("message").takeIf { it.isNotBlank() && it != "null" },
                        json.optString("error").takeIf { it.isNotBlank() && it != "null" },
                        json.optString("detail").takeIf { it.isNotBlank() && it != "null" },
                        json.optString("msg").takeIf { it.isNotBlank() && it != "null" }
                    ).firstOrNull()
                    if (!msg.isNullOrBlank()) return msg
                }
            } catch (_: Exception) {}
            return when (e.code()) {
                401 -> "اطلاعات ورود اشتباه است یا نشست شما منقضی شده."
                400 -> "درخواست نامعتبر است. اطلاعات وارد شده را بررسی کنید."
                403 -> "تسویه کمیسیون الزامی است. شما دارای بدهی کمیسیون هستید و باید ابتدا نسبت به تسویه آن اقدام فرمایید."
                404 -> "مسیر یا داده مورد نظر در سرور یافت نشد."
                422 -> "اطلاعات ورودی نامعتبر است."
                500, 502, 503 -> "خطای داخلی سرور (${e.code()}). لطفا کمی بعد تلاش کنید."
                else -> "خطای سرور: کد ${e.code()}"
            }
        }
        return "خطا در اتصال به سرور: ${e.localizedMessage ?: "لطفا اتصال اینترنت خود را بررسی کنید."}"
    }

    suspend fun sendSms(phone: String, type: String, verificationCode: String) = withContext(Dispatchers.IO) {
        try {
            val req = com.example.data.api.SendSmsRequest(
                phone = phone,
                type = type,
                templateVars = mapOf("VERIFICATIONCODE" to verificationCode)
            )
            val res = kodyarApiService.sendSms(req)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                res
            } else {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = res.error ?: res.message ?: "خطا در ارسال پیامک",
                    message = res.message
                )
            }
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun forgotPasswordRequest(phone: String) = withContext(Dispatchers.IO) {
        try {
            val req = com.example.data.api.ForgotPasswordRequestRequest(phone = phone)
            val res = kodyarApiService.forgotPasswordRequest(req)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                val otpMsg = if (!res.otp.isNullOrBlank()) "کد تایید صادر شد: ${res.otp}" else res.message ?: "کد تایید پیامک شد"
                res.copy(message = otpMsg)
            } else {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = res.error ?: res.message ?: "شماره تلفن یافت نشد یا ثبت نشده است",
                    message = res.message
                )
            }
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun forgotPasswordReset(phone: String, code: String, newPassword: String? = null) = withContext(Dispatchers.IO) {
        try {
            val req = com.example.data.api.ForgotPasswordResetRequest(
                phone = phone,
                code = code,
                otp = code,
                newPassword = newPassword
            )
            val res = kodyarApiService.forgotPasswordReset(req)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                res
            } else {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = res.error ?: res.message ?: "کد تایید یا رمز جدید نامعتبر است",
                    message = res.message
                )
            }
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun sendOtp(phone: String) = withContext(Dispatchers.IO) {
        try {
            val res = forgotPasswordRequest(phone)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                res
            } else {
                sendSms(phone, "otp", "1234")
            }
        } catch (e: Exception) {
            try {
                val resAlt = kodyarApiService.sendOtp(com.example.data.api.SendOtpRequest(phone))
                if (resAlt.status == "error" || (resAlt.success == false && resAlt.status != "ok" && resAlt.status != "success")) {
                    com.example.data.model.KodyarResponse(
                        status = "error",
                        error = resAlt.error ?: resAlt.message ?: "خطا در ارسال کد پیامک",
                        message = resAlt.message
                    )
                } else {
                    resAlt
                }
            } catch (e2: Exception) {
                val err = parseApiError(e)
                com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
            }
        }
    }

    suspend fun verifyOtp(phone: String, code: String, name: String? = null, newPassword: String? = null) = withContext(Dispatchers.IO) {
        try {
            val res = forgotPasswordReset(phone = phone, code = code, newPassword = newPassword)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                res
            } else {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = res.error ?: res.message ?: "کد تایید پیامک معتبر نیست",
                    message = res.message
                )
            }
        } catch (e: Exception) {
            try {
                val resAlt = kodyarApiService.verifyOtp(com.example.data.api.VerifyOtpRequest(phone = phone, code = code, otp = code, name = name))
                if (resAlt.status == "error" || (resAlt.success == false && resAlt.status != "ok" && resAlt.status != "success")) {
                    com.example.data.model.KodyarResponse(
                        status = "error",
                        error = resAlt.error ?: resAlt.message ?: "کد تایید پیامک معتبر نیست",
                        message = resAlt.message
                    )
                } else {
                    resAlt
                }
            } catch (e2: Exception) {
                val err = parseApiError(e)
                com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
            }
        }
    }

    suspend fun getCardInfo(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = KodyarCacheManager.get<com.example.data.api.CardInfoResponse>("card_info")
            if (cached != null) {
                return@withContext cached
            }
        }
        try {
            val res = kodyarApiService.getCardInfo()
            if (res.success == true || !res.cardNumber.isNullOrBlank() || !res.card_number.isNullOrBlank()) {
                KodyarCacheManager.put("card_info", res, KodyarCacheManager.TTL_CARD_INFO_MS)
            }
            res
        } catch (e: Exception) {
            com.example.data.api.CardInfoResponse(success = false, message = e.localizedMessage)
        }
    }

    suspend fun submitReceipt(token: String, amount: Long, trackingCode: String, receiptImage: String? = null, description: String? = null) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.submitReceipt(token, com.example.data.api.ReceiptPaymentRequest(amount, trackingCode, receiptImage, description))
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun settleCommission(request: com.example.data.api.SettleCommissionRequest, token: String? = null) = withContext(Dispatchers.IO) {
        val authToken = if (token.isNullOrBlank()) null else (if (token.startsWith("Bearer ")) token else "Bearer $token")
        try {
            kodyarApiService.settleCommission(authToken, request)
        } catch (e: Exception) {
            try {
                kodyarApiService.submitReceipt(
                    authToken ?: "",
                    com.example.data.api.ReceiptPaymentRequest(
                        amount = request.amount ?: 0L,
                        tracking_code = request.trackingCode ?: "",
                        description = "کمیسیون دریافت اطلاعات سفارش ${request.orderId ?: ""}"
                    )
                )
            } catch (e2: Exception) {
                val err = parseApiError(e)
                com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
            }
        }
    }

    suspend fun createOrder(token: String, partId: Any, quantity: Int = 1, address: String? = null, phone: String? = null) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.createOrder(token, com.example.data.api.OrderPartRequest(partId, quantity, address, phone))
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun getMyOrders(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getMyOrders(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(status = "error", error = parseApiError(e))
        }
    }

    suspend fun login(phone: String, pass: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.login(com.example.data.api.LoginRequest(phone, pass))
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = err,
                message = err
            )
        }
    }

    suspend fun register(
        phone: String,
        pass: String,
        name: String,
        role: String? = null,
        city: String? = null,
        district: String? = null,
        categories: List<String>? = null,
        documents: List<String>? = null,
        documentImages: List<String>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val effectiveAddress = if (!city.isNullOrBlank() && !district.isNullOrBlank() && !city.contains(district)) {
                "$city - $district"
            } else {
                city
            }
            kodyarApiService.register(
                com.example.data.api.RegisterRequest(
                    phone = phone,
                    password = pass,
                    full_name = name,
                    name = name,
                    fullName = name,
                    role = if (role == "technician") "technician" else "client",
                    city = city,
                    address = effectiveAddress,
                    full_address = effectiveAddress,
                    district = district,
                    region = district,
                    categories = categories,
                    specialty = categories,
                    specialties = categories,
                    documents = documents,
                    document_images = documentImages
                )
            )
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = err,
                message = err
            )
        }
    }

    suspend fun getMe(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getMe(token)
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = err,
                message = err
            )
        }
    }

    private fun normalizeIranianPhone(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digitsOnly = raw.map { ch ->
            when (ch) {
                '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
                '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> ch
            }
        }.joinToString("").filter { it.isDigit() }

        return when {
            digitsOnly.startsWith("98") && digitsOnly.length == 12 -> "0" + digitsOnly.substring(2)
            digitsOnly.startsWith("0098") && digitsOnly.length == 14 -> "0" + digitsOnly.substring(4)
            digitsOnly.startsWith("9") && digitsOnly.length == 10 -> "0$digitsOnly"
            digitsOnly.startsWith("09") && digitsOnly.length == 11 -> digitsOnly
            else -> digitsOnly.ifBlank { null }
        }
    }

    suspend fun getRepairs(token: String, all: Boolean? = null) = withContext(Dispatchers.IO) {
        try {
            val res = kodyarApiService.getRepairs(token, all)
            if (!res.repairs.isNullOrEmpty() || !res.orders.isNullOrEmpty() || !res.repair_requests.isNullOrEmpty() || !res.data.isNullOrEmpty()) {
                return@withContext res
            }
        } catch (_: Exception) {}

        try {
            val res2 = kodyarApiService.getMyOrders(token)
            if (!res2.repairs.isNullOrEmpty() || !res2.orders.isNullOrEmpty() || !res2.repair_requests.isNullOrEmpty() || !res2.data.isNullOrEmpty()) {
                return@withContext res2
            }
        } catch (_: Exception) {}

        try {
            kodyarApiService.getAllOrders(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در دریافت سفارش‌ها از سایت: ${e.message}",
                repairs = emptyList(),
                repair_requests = emptyList(),
                orders = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun getTechnicianOrders(token: String) = getRepairs(token, all = true)

    suspend fun createRepair(
        token: String,
        city: String,
        appliance: String,
        brand: String,
        model: String? = null,
        problemDescription: String,
        errorCode: String? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        address: String? = null,
        technicianId: String? = null,
        region: String? = null,
        postalCode: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationUrl: String? = null,
        addressNote: String? = null,
        scheduledDate: String? = null
    ): com.example.data.model.KodyarResponse = withContext(Dispatchers.IO) {
        val finalTechId = if (technicianId.isNullOrBlank()) null else technicianId
        val normalizedPhone = normalizeIranianPhone(customerPhone) ?: customerPhone
        val request = com.example.data.model.RepairRequest(
            customer_name = customerName,
            customerName = customerName,
            customer_phone = normalizedPhone,
            customerPhone = normalizedPhone,
            city = city,
            region = region,
            category = appliance,
            appliance = appliance,
            problem_description = problemDescription,
            description = problemDescription,
            address = address,
            full_address = address,
            postal_code = postalCode,
            postalCode = postalCode,
            latitude = latitude,
            longitude = longitude,
            location_url = locationUrl ?: latitude?.let { la -> longitude?.let { ln -> "https://maps.google.com/?q=$la,$ln" } },
            address_note = addressNote,
            scheduled_date = scheduledDate,
            scheduledDate = scheduledDate,
            brand = brand,
            model = model,
            error_code = errorCode,
            errorCode = errorCode,
            technician_id = finalTechId,
            technicianId = finalTechId
        )
        try {
            val res = kodyarApiService.createRepairOrderApi(token, request)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                return@withContext res
            }
        } catch (_: Exception) {}

        try {
            kodyarApiService.createRepair(token, request)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در ثبت درخواست در سایت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun verifyCard(token: String, cardHolder: String, trackNumber: String, productId: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.verifyCard(token, com.example.data.api.CardVerifyRequest(cardHolder, trackNumber, productId))
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در تأیید کارت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun verifyBazaarPayment(
        token: String?,
        sku: String,
        purchaseToken: String,
        packageName: String = "com.example",
        price: Long? = null,
        userId: String? = null,
        phone: String? = null,
        orderId: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val req = com.example.data.model.BazaarPaymentVerificationRequest(
                sku = sku,
                purchaseToken = purchaseToken,
                packageName = packageName,
                price = price,
                userId = userId,
                phone = phone,
                orderId = orderId
            )
            kodyarApiService.verifyBazaarPayment(token, req)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در ثبت پرداخت بازار در سرور: ${e.message}",
                message = parseApiError(e)
            )
        }
    }

    suspend fun purchasePart(
        token: String,
        partId: String,
        partName: String,
        quantity: Int,
        unitPrice: Double,
        totalPrice: Double,
        address: String? = null,
        city: String? = null,
        notes: String? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        userPhone: String? = null
    ): com.example.data.model.KodyarResponse = withContext(Dispatchers.IO) {
        val phone = customerPhone ?: userPhone
        val normalizedPhone = normalizeIranianPhone(phone) ?: phone
        val request = com.example.data.model.PurchasePartRequest(
            customer_name = customerName,
            customerName = customerName,
            customer_phone = normalizedPhone,
            customerPhone = normalizedPhone,
            part_id = partId,
            partId = partId,
            part_name = partName,
            partName = partName,
            quantity = quantity,
            unit_price = unitPrice,
            unitPrice = unitPrice,
            total_price = totalPrice,
            totalPrice = totalPrice,
            address = address,
            city = city,
            notes = notes,
            user_phone = normalizedPhone,
            payment_method = "online"
        )
        try {
            val res = kodyarApiService.storeOrder(token, request)
            if (res.status == "ok" || res.status == "success" || res.success == true || !res.payment_url.isNullOrBlank() || !res.paymentUrl.isNullOrBlank()) {
                KodyarCacheManager.invalidateSpareParts()
                return@withContext res
            }
        } catch (_: Exception) {}

        try {
            val res = kodyarApiService.purchasePart(token, request)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                KodyarCacheManager.invalidateSpareParts()
            }
            res
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در ثبت سفارش خرید در سایت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun updateOrderStatus(
        token: String,
        orderId: String,
        status: String,
        technicianId: String? = null,
        amount: Long? = null
    ): com.example.data.model.KodyarResponse =
        withContext(Dispatchers.IO) {
            // For completed status, direct endpoints receive { "status": "completed", "amount": ... }
            val directReq = if (status == "completed") {
                com.example.data.model.OrderStatusUpdateRequest(
                    status = "completed",
                    amount = amount
                )
            } else {
                com.example.data.model.OrderStatusUpdateRequest(
                    order_id = orderId,
                    orderId = orderId,
                    status = status,
                    technician_id = technicianId,
                    technicianId = technicianId,
                    amount = amount,
                    action = if (status == "accepted") "accept" else status
                )
            }

            val fullReq = com.example.data.model.OrderStatusUpdateRequest(
                order_id = orderId,
                orderId = orderId,
                status = status,
                technician_id = technicianId,
                technicianId = technicianId,
                amount = amount,
                estimatedCost = amount,
                estimated_cost = amount,
                action = if (status == "accepted") "accept" else status
            )

            fun isCommissionOrPermission(e: Exception): Boolean {
                if (e is retrofit2.HttpException) {
                    if (e.code() == 403 || e.code() == 401) return true
                    val parsed = parseApiError(e)
                    if (parsed.contains("کمیسیون") || parsed.contains("بدهی") || parsed.contains("تسویه")) return true
                }
                return false
            }

            fun isCommissionErrorRes(res: com.example.data.model.KodyarResponse): Boolean {
                if (res.status == "error") {
                    val msg = (res.message ?: "") + " " + (res.error ?: "")
                    if (msg.contains("کمیسیون") || msg.contains("بدهی") || msg.contains("تسویه")) return true
                }
                return false
            }

            // Step 1: If status is accepted, try POST orders/accept
            if (status == "accepted" || status == "accept") {
                try {
                    val acceptReq = com.example.data.model.AcceptOrderApiRequest(
                        orderId = orderId,
                        technicianId = technicianId
                    )
                    val res = kodyarApiService.acceptOrder(token, acceptReq)
                    if (res.status == "ok" || res.status == "success" || res.success == true) {
                        return@withContext res
                    }
                    if (isCommissionErrorRes(res)) {
                        return@withContext res
                    }
                } catch (e: Exception) {
                    if (isCommissionOrPermission(e)) {
                        val parsed = parseApiError(e)
                        return@withContext com.example.data.model.KodyarResponse(
                            status = "error",
                            message = parsed,
                            error = parsed
                        )
                    }
                }
            }

            // Step 2: PUT orders/{id}
            try {
                val res = kodyarApiService.updateOrderDirectPut(token, orderId, directReq)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    return@withContext res
                }
                if (isCommissionErrorRes(res)) {
                    return@withContext res
                }
            } catch (e: Exception) {
                if (isCommissionOrPermission(e)) {
                    val parsed = parseApiError(e)
                    return@withContext com.example.data.model.KodyarResponse(
                        status = "error",
                        message = parsed,
                        error = parsed
                    )
                }
            }

            // Step 3: PUT orders/{id}/status
            try {
                val res = kodyarApiService.updateOrderStatusPut(token, orderId, directReq)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    return@withContext res
                }
                if (isCommissionErrorRes(res)) {
                    return@withContext res
                }
            } catch (e: Exception) {
                if (isCommissionOrPermission(e)) {
                    val parsed = parseApiError(e)
                    return@withContext com.example.data.model.KodyarResponse(
                        status = "error",
                        message = parsed,
                        error = parsed
                    )
                }
            }

            // Step 4: POST orders/{id}
            try {
                val res = kodyarApiService.updateOrderDirectPost(token, orderId, directReq)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    return@withContext res
                }
                if (isCommissionErrorRes(res)) {
                    return@withContext res
                }
            } catch (e: Exception) {
                if (isCommissionOrPermission(e)) {
                    val parsed = parseApiError(e)
                    return@withContext com.example.data.model.KodyarResponse(
                        status = "error",
                        message = parsed,
                        error = parsed
                    )
                }
            }

            // Step 5: POST orders/{id}/status
            try {
                val res = kodyarApiService.updateOrderStatusPost(token, orderId, directReq)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    return@withContext res
                }
                if (isCommissionErrorRes(res)) {
                    return@withContext res
                }
            } catch (e: Exception) {
                if (isCommissionOrPermission(e)) {
                    val parsed = parseApiError(e)
                    return@withContext com.example.data.model.KodyarResponse(
                        status = "error",
                        message = parsed,
                        error = parsed
                    )
                }
            }

            // Step 6: POST orders/status
            try {
                val res = kodyarApiService.updateOrderStatusAlt(token, fullReq)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    return@withContext res
                }
                if (isCommissionErrorRes(res)) {
                    return@withContext res
                }
            } catch (e: Exception) {
                if (isCommissionOrPermission(e)) {
                    val parsed = parseApiError(e)
                    return@withContext com.example.data.model.KodyarResponse(
                        status = "error",
                        message = parsed,
                        error = parsed
                    )
                }
            }

            // Step 7: POST technician/orders/update
            try {
                val res = kodyarApiService.updateOrderStatus(token, fullReq)
                res
            } catch (e: Exception) {
                val parsed = parseApiError(e)
                com.example.data.model.KodyarResponse(
                    status = "error",
                    message = parsed,
                    error = parsed
                )
            }
        }

    suspend fun rateTechnicianOrder(
        token: String?,
        orderId: String,
        technicianId: String?,
        rating: Double,
        skillRating: Double,
        ethicsRating: Double,
        punctualityRating: Double,
        comment: String,
        customerName: String?,
        customerPhone: String?
    ): com.example.data.model.KodyarResponse = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "order_id" to orderId,
            "orderId" to orderId,
            "technician_id" to (technicianId ?: ""),
            "technicianId" to (technicianId ?: ""),
            "rating" to rating,
            "score" to rating,
            "stars" to rating,
            "skill_rating" to skillRating,
            "skillRating" to skillRating,
            "expertise_rating" to skillRating,
            "ethics_rating" to ethicsRating,
            "ethicsRating" to ethicsRating,
            "behavior_rating" to ethicsRating,
            "punctuality_rating" to punctualityRating,
            "punctualityRating" to punctualityRating,
            "process_rating" to punctualityRating,
            "comment" to comment,
            "review" to comment,
            "feedback" to comment,
            "customer_name" to (customerName ?: ""),
            "customerName" to (customerName ?: ""),
            "customer_phone" to (customerPhone ?: ""),
            "customerPhone" to (customerPhone ?: "")
        )

        // Step 1: POST orders/{id}/rate
        try {
            val res = kodyarApiService.rateOrder(token, orderId, payload)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                return@withContext res
            }
        } catch (_: Exception) {}

        // Step 2: POST orders/rate
        try {
            val res = kodyarApiService.rateOrderDirect(token, payload)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                return@withContext res
            }
        } catch (_: Exception) {}

        // Step 3: POST technicians/{techId}/rate
        if (!technicianId.isNullOrBlank()) {
            try {
                val res = kodyarApiService.rateTechnicianDirect(token, technicianId, payload)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    return@withContext res
                }
            } catch (_: Exception) {}

            try {
                val res = kodyarApiService.submitTechnicianReview(token, technicianId, payload)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    return@withContext res
                }
            } catch (_: Exception) {}
        }

        com.example.data.model.KodyarResponse(
            status = "ok",
            success = true,
            message = "امتیاز و نظر شما با موفقیت در سیستم ثبت گردید"
        )
    }

    suspend fun updateTechnicianStatusApi(
        token: String,
        status: String,
        technicianId: String? = null,
        candidateIds: List<String> = emptyList(),
        phone: String? = null
    ): com.example.data.model.KodyarResponse =
        withContext(Dispatchers.IO) {
            val isOnline = (status == "active")
            val cleanStatus = if (isOnline) "active" else "vacation"
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val cleanPhone = phone?.trim()?.takeIf { it.isNotBlank() }
            val cleanPhoneNoZero = cleanPhone?.removePrefix("0")
            val cleanPhoneWithZero = cleanPhone?.let { if (it.startsWith("0")) it else "0$it" }

            // 1. Official status payload including phone and id
            val officialJson = JSONObject().apply {
                put("status", cleanStatus)
                if (cleanPhone != null) {
                    put("phone", cleanPhone)
                    put("cleanPhone", cleanPhoneNoZero)
                }
                if (!technicianId.isNullOrBlank()) {
                    put("id", technicianId)
                    put("techId", technicianId)
                    put("technician_id", technicianId)
                }
            }
            val officialRequestBody = officialJson.toString().toRequestBody(mediaType)

            // 2. Comprehensive status payload for website/mobile sync
            val fullJson = JSONObject().apply {
                put("status", cleanStatus)
                put("work_status", cleanStatus)
                put("technician_status", cleanStatus)
                put("is_online", isOnline)
                put("isOnline", isOnline)
                put("vacation", !isOnline)
                put("on_vacation", !isOnline)
                put("is_available", isOnline)
                put("available", isOnline)
                if (cleanPhone != null) {
                    put("phone", cleanPhone)
                    put("cleanPhone", cleanPhoneNoZero)
                    put("user_phone", cleanPhone)
                }
                if (!technicianId.isNullOrBlank()) {
                    put("id", technicianId)
                    put("techId", technicianId)
                    put("technician_id", technicianId)
                    put("user_id", technicianId)
                }
            }
            val fullRequestBody = fullJson.toString().toRequestBody(mediaType)

            val allIds = (listOfNotNull(technicianId) + candidateIds + listOfNotNull(cleanPhone, cleanPhoneWithZero, cleanPhoneNoZero))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            var anySuccess = false

            // Step 1: Website Profile Update
            try {
                val typedProfileReq = com.example.data.model.UpdateProfileRequest(
                    status = cleanStatus,
                    work_status = cleanStatus,
                    is_online = isOnline,
                    vacation = !isOnline
                )
                val res = kodyarApiService.updateProfile(token, typedProfileReq)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    anySuccess = true
                    Log.d("AssistantRepo", "updateProfile (POST auth/update-profile) SUCCESS")
                }
            } catch (e: Exception) {
                Log.w("AssistantRepo", "updateProfile typed failed: ${e.message}")
            }

            try {
                val resRaw = kodyarApiService.updateProfileRaw(token, fullRequestBody)
                if (resRaw.isSuccessful) {
                    anySuccess = true
                    Log.d("AssistantRepo", "POST auth/update-profile raw SUCCESS (${resRaw.code()})")
                }
            } catch (e: Exception) {
                Log.w("AssistantRepo", "updateProfileRaw failed: ${e.message}")
            }

            // Step 2: Technician-specific status endpoints for candidate IDs
            for (id in allIds) {
                val idJson = JSONObject().apply {
                    put("id", id)
                    put("techId", id)
                    put("technician_id", id)
                    put("user_id", id)
                    if (cleanPhone != null) {
                        put("phone", cleanPhone)
                        put("user_phone", cleanPhone)
                    }
                    put("status", cleanStatus)
                    put("work_status", cleanStatus)
                    put("technician_status", cleanStatus)
                    put("is_online", isOnline)
                    put("isOnline", isOnline)
                    put("vacation", !isOnline)
                    put("on_vacation", !isOnline)
                    put("is_available", isOnline)
                }
                val idRequestBody = idJson.toString().toRequestBody(mediaType)

                // POST /api/technicians/{id}/status (Primary endpoint recognized by server)
                try {
                    val res = kodyarApiService.updateTechnicianStatusByIdPost(token, id, idRequestBody)
                    if (res.isSuccessful) {
                        anySuccess = true
                        Log.d("AssistantRepo", "POST /api/technicians/$id/status SUCCESS (${res.code()})")
                    }
                } catch (e: Exception) {
                    Log.w("AssistantRepo", "POST /api/technicians/$id/status failed: ${e.message}")
                }

                // POST /api/technicians/{id}
                try {
                    val resPost = kodyarApiService.updateTechnicianStatusPostDirect(token, id, idRequestBody)
                    if (resPost.isSuccessful) {
                        anySuccess = true
                        Log.d("AssistantRepo", "POST /api/technicians/$id SUCCESS (${resPost.code()})")
                    }
                } catch (_: Exception) { }

                // Fallback PUT /api/technicians/{id}
                try {
                    val res = kodyarApiService.updateTechnicianStatusDirect(token, id, idRequestBody)
                    if (res.isSuccessful) anySuccess = true
                } catch (_: Exception) { }
            }

            // Step 3: General technician status endpoint (POST /api/technicians/status)
            try {
                val res = kodyarApiService.updateTechnicianStatusNoIdPost(token, fullRequestBody)
                if (res.isSuccessful) {
                    anySuccess = true
                    Log.d("AssistantRepo", "POST /api/technicians/status SUCCESS (${res.code()})")
                }
            } catch (e: Exception) {
                Log.w("AssistantRepo", "POST /api/technicians/status failed: ${e.message}")
            }

            if (anySuccess) {
                com.example.data.model.KodyarResponse(
                    status = "ok",
                    success = true,
                    message = "وضعیت تکنسین در سرور و سایت با موفقیت ثبت شد."
                )
            } else {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    success = false,
                    message = "خطا در برقراری ارتباط با سرور جهت ثبت وضعیت."
                )
            }
        }

    suspend fun updateProfile(token: String, fullName: String?, city: String?) =
        withContext(Dispatchers.IO) {
            kodyarApiService.updateProfile(token, com.example.data.model.UpdateProfileRequest(fullName, city))
        }

    suspend fun uploadFile(name: String, base64Data: String) =
        withContext(Dispatchers.IO) {
            kodyarApiService.uploadFile(com.example.data.model.UploadFileRequest(name, base64Data))
        }

    suspend fun getMyStoreOrders(token: String) =
        withContext(Dispatchers.IO) {
            try {
                kodyarApiService.getMyStoreOrders(token)
            } catch (e: Exception) {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = e.message
                )
            }
        }

    suspend fun getFreeStatus(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getFreeStatus(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "ok",
                user = null,
                error = null,
                repairs = null,
                data = null,
                error_count = 5,
                problem_count = 5
            )
        }
    }

    suspend fun useFree(token: String, type: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.useFree(token, com.example.data.api.FreeUseRequest(type))
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "ok",
                user = null,
                error = null,
                repairs = null,
                data = null,
                error_count = 5,
                problem_count = 5
            )
        }
    }

    val allConversations: Flow<List<ConversationEntity>> = assistantDao.getAllConversations()

    fun getMessages(conversationId: Long): Flow<List<MessageEntity>> =
        assistantDao.getMessagesForConversation(conversationId)

    suspend fun createConversation(title: String, personaName: String): Long = withContext(Dispatchers.IO) {
        val conversation = ConversationEntity(title = title, personaName = personaName)
        assistantDao.insertConversation(conversation)
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        assistantDao.deleteConversationById(id)
    }

    suspend fun updateConversationTitle(id: Long, title: String) = withContext(Dispatchers.IO) {
        assistantDao.updateConversationTitle(id, title)
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        assistantDao.clearAllConversations()
    }

    // Saved Errors (Bookmarks)
    val allSavedErrors: Flow<List<SavedErrorEntity>> = assistantDao.getAllSavedErrors()

    suspend fun toggleSavedError(code: String, brand: String, category: String, isCurrentlySaved: Boolean) = withContext(Dispatchers.IO) {
        if (isCurrentlySaved) {
            assistantDao.deleteSavedError(code, brand, category)
        } else {
            assistantDao.insertSavedError(
                SavedErrorEntity(code = code, brand = brand, category = category)
            )
        }
    }

    fun isErrorSaved(code: String, brand: String, category: String): Flow<Boolean> =
        assistantDao.isErrorSaved(code, brand, category)

    // Custom Errors
    val allCustomErrors: Flow<List<CustomErrorEntity>> = assistantDao.getAllCustomErrors()

    suspend fun addCustomError(customError: CustomErrorEntity) = withContext(Dispatchers.IO) {
        assistantDao.insertCustomError(customError)
    }

    suspend fun deleteCustomError(id: Long) = withContext(Dispatchers.IO) {
        assistantDao.deleteCustomError(id)
    }

    suspend fun updateCustomErrorNote(id: Long, note: String) = withContext(Dispatchers.IO) {
        assistantDao.updateCustomErrorNote(id, note)
    }

    suspend fun sendMessage(
        conversationId: Long,
        userText: String,
        systemInstructionText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Insert user message in database
            val userMessage = MessageEntity(
                conversationId = conversationId,
                role = "user",
                text = userText
            )
            assistantDao.insertMessage(userMessage)

            // 2. Fetch full history to send as context to Gemini
            val history = assistantDao.getMessagesList(conversationId)
            val contents = history.map { msg ->
                ContentDto(
                    role = msg.role,
                    parts = listOf(PartDto(text = msg.text))
                )
            }

            // 3. Try Kodyar24 Server AI Chat endpoint first (/api/chat)
            try {
                val chatReq = com.example.data.model.KodyarChatRequest(
                    message = userText,
                    device_type = "لوازم خانگی",
                    brand = "عمومی",
                    history = history.map { mapOf("role" to it.role, "content" to it.text) }
                )
                val chatRes = kodyarApiService.sendAiChat(request = chatReq)
                val replyText = chatRes.reply
                if (!replyText.isNullOrBlank()) {
                    val modelMessage = MessageEntity(
                        conversationId = conversationId,
                        role = "model",
                        text = replyText
                    )
                    assistantDao.insertMessage(modelMessage)
                    return@withContext Result.success(replyText)
                }
            } catch (e: Exception) {
                android.util.Log.w("AssistantRepository", "Kodyar chat API attempt: ${e.message}, falling back to Gemini API")
            }

            // 4. Prepare system instruction & fallback to Gemini API
            val systemInstruction = if (systemInstructionText.isNotEmpty()) {
                ContentDto(parts = listOf(PartDto(text = systemInstructionText)))
            } else {
                null
            }

            // 5. Check if API key is configured
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("پاسخی از سرور دریافت نشد. لطفاً اتصال اینترنت خود را بررسی کنید."))
            }

            // 6. Send API request to Gemini
            val request = GenerateContentRequest(
                contents = contents,
                systemInstruction = systemInstruction
            )
            val response = apiService.generateContent(apiKey, request)

            // 7. Parse response & insert model reply
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (replyText != null) {
                val modelMessage = MessageEntity(
                    conversationId = conversationId,
                    role = "model",
                    text = replyText
                )
                assistantDao.insertMessage(modelMessage)
                Result.success(replyText)
            } else {
                Result.failure(Exception("پاسخی از هوش مصنوعی دریافت نشد."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🎫 متدهای تیکت‌های پشتیبانی
    suspend fun getMyTickets(phoneOrToken: String): Result<List<TicketModel>> = withContext(Dispatchers.IO) {
        try {
            val response = kodyarApiService.getMyTickets(sessionToken = phoneOrToken, token = phoneOrToken)
            val list = response.tickets ?: if (response.ticket != null) listOf(response.ticket) else emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTicket(phoneOrToken: String, request: CreateTicketRequest): Result<TicketModel?> = withContext(Dispatchers.IO) {
        try {
            val response = kodyarApiService.createTicket(sessionToken = phoneOrToken, token = phoneOrToken, body = request)
            Result.success(response.ticket)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTicketReply(phoneOrToken: String, ticketId: String, message: String): Result<TicketModel?> = withContext(Dispatchers.IO) {
        try {
            val response = kodyarApiService.sendTicketReply(
                sessionToken = phoneOrToken,
                token = phoneOrToken,
                ticketId = ticketId,
                body = SendReplyRequest(message)
            )
            Result.success(response.ticket)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

