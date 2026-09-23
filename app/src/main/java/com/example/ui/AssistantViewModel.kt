package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AssistantRepository
import com.example.data.db.ConversationEntity
import com.example.data.db.MessageEntity
import com.example.data.db.SavedErrorEntity
import com.example.data.db.CustomErrorEntity
import com.example.data.db.toDomain
import com.example.data.utils.NetworkMonitor
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun normalizePersian(input: String?): String {
    if (input == null) return ""
    return input.trim()
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("ة", "ه")
        .replace("آ", "ا")
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("ؤ", "و")
        .replace("ئ", "ی")
        .replace("‌", "") // نیم‌فاصله
        .replace("\\s+".toRegex(), " ")
}

private fun normalizeDigitsAndTrim(input: String?): String {
    if (input == null) return ""
    return input.trim()
        .replace('۰', '0')
        .replace('۱', '1')
        .replace('۲', '2')
        .replace('۳', '3')
        .replace('۴', '4')
        .replace('۵', '5')
        .replace('۶', '6')
        .replace('۷', '7')
        .replace('۸', '8')
        .replace('۹', '9')
        .replace('٠', '0')
        .replace('١', '1')
        .replace('٢', '2')
        .replace('٣', '3')
        .replace('٤', '4')
        .replace('٥', '5')
        .replace('٦', '6')
        .replace('٧', '7')
        .replace('٨', '8')
        .replace('٩', '9')
}

private fun normalizePhone(input: String?): String {
    var p = normalizeDigitsAndTrim(input).replace(" ", "").replace("-", "")
    if (p.startsWith("+98")) {
        p = "0" + p.substring(3)
    } else if (p.startsWith("0098")) {
        p = "0" + p.substring(4)
    } else if (p.startsWith("98") && p.length > 10) {
        p = "0" + p.substring(2)
    } else if (p.length == 10 && p.startsWith("9")) {
        p = "0$p"
    }
    return p
}

private fun hashPassword(pass: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(pass.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

class AssistantViewModel(
    private val repository: AssistantRepository,
    private val context: Context
) : ViewModel() {

    private val networkMonitor = NetworkMonitor(context)
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = networkMonitor.isCurrentlyOnline()
    )

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasLoadedFromNetwork = false

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("kodyar_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var _cachedEncryptedPrefs: SharedPreferences? = null

    private val encryptedPrefs: SharedPreferences
        get() {
            _cachedEncryptedPrefs?.let { return it }
            synchronized(this) {
                _cachedEncryptedPrefs?.let { return it }
                val prefs = try {
                    val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    androidx.security.crypto.EncryptedSharedPreferences.create(
                        context,
                        "secure_kodyar_prefs",
                        masterKey,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e: Exception) {
                    Log.e("AssistantViewModel", "Failed to create EncryptedSharedPreferences, using fallback", e)
                    context.getSharedPreferences("secure_kodyar_prefs_fallback", Context.MODE_PRIVATE)
                }
                _cachedEncryptedPrefs = prefs
                return prefs
            }
        }

    private val moshi by lazy {
        Moshi.Builder()
            .add(KodyarCityAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
    private val databaseAdapter by lazy { moshi.adapter(KodyarDatabaseResponse::class.java) }

    private val techniciansAdapter by lazy {
        moshi.adapter<List<KodyarTechnician>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, KodyarTechnician::class.java)
        )
    }

    private val sparePartsAdapter by lazy {
        moshi.adapter<List<KodyarSparePart>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, KodyarSparePart::class.java)
        )
    }

    // --- Live Web Update Notification State ---
    private val _appUpdateNotification = MutableStateFlow<AppUpdateNotification?>(null)
    val appUpdateNotification: StateFlow<AppUpdateNotification?> = _appUpdateNotification.asStateFlow()

    fun dismissUpdateNotification() {
        _appUpdateNotification.value = null
    }

    // --- Version & Bazaar Update Dialog State ---
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _appUpdateInfo = MutableStateFlow(AppUpdateInfo())
    val appUpdateInfo: StateFlow<AppUpdateInfo> = _appUpdateInfo.asStateFlow()

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun checkAppVersion(response: KodyarDatabaseResponse? = null) {
        val currentCode = com.example.BuildConfig.VERSION_CODE
        val currentVersionName = com.example.BuildConfig.VERSION_NAME
        val rawServerCode = response?.latestVersionCode ?: currentCode
        val serverVersionCode = rawServerCode.coerceAtLeast(currentCode)
        val serverVersionName = if (response?.latestVersionName != null && response.latestVersionName.isNotBlank()) response.latestVersionName else currentVersionName
        val notes = response?.updateNotes
        val isForce = response?.isForceUpdate ?: false

        _appUpdateInfo.value = _appUpdateInfo.value.copy(
            latestVersionCode = serverVersionCode,
            latestVersionName = serverVersionName,
            isForceUpdate = isForce,
            notes = if (!notes.isNullOrEmpty()) notes else _appUpdateInfo.value.notes
        )

        // If installed app version is less than server latest version code (e.g. user running version 32 or earlier)
        if (currentCode < serverVersionCode) {
            _showUpdateDialog.value = true
        }
    }

    // --- Theme Settings ---
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        sharedPrefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    // --- Disclaimer & Terms of Use State ---
    private val _isDisclaimerAccepted = MutableStateFlow(sharedPrefs.getBoolean("disclaimer_accepted", false))
    val isDisclaimerAccepted: StateFlow<Boolean> = _isDisclaimerAccepted.asStateFlow()

    // --- Technician Online / Vacation Status ---
    private val _isTechnicianOnline = MutableStateFlow(sharedPrefs.getBoolean("technician_online_status", true))
    val isTechnicianOnline: StateFlow<Boolean> = _isTechnicianOnline.asStateFlow()
    private val _isTechStatusUpdating = MutableStateFlow(false)
    val isTechStatusUpdating: StateFlow<Boolean> = _isTechStatusUpdating.asStateFlow()

    private fun getLocalOrderStatusOverrides(): Map<String, String> {
        val json = sharedPrefs.getString("local_order_status_overrides", null) ?: return emptyMap()
        return try {
            val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            moshi.adapter<Map<String, String>>(type).fromJson(json) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveLocalOrderStatusOverride(orderId: String, status: String) {
        if (orderId.isBlank()) return
        val current = getLocalOrderStatusOverrides().toMutableMap()
        current[orderId] = status
        try {
            val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            val json = moshi.adapter<Map<String, String>>(type).toJson(current)
            sharedPrefs.edit().putString("local_order_status_overrides", json).apply()
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Error saving local order override", e)
        }
    }

    // سفارش‌هایی که فیش کارت‌به‌کارت آن‌ها ثبت شده اما هنوز توسط مدیر تایید نشده است
    val pendingCommissionOrderIds: StateFlow<Set<String>> get() = _pendingCommissionOrderIds.asStateFlow()
    private val _pendingCommissionOrderIds = MutableStateFlow<Set<String>>(emptySet())

    private fun loadPendingCommissionOrders() {
        val saved = sharedPrefs.getStringSet("pending_commission_orders", emptySet()) ?: emptySet()
        _pendingCommissionOrderIds.value = saved
    }

    fun markOrderCommissionPending(orderId: String) {
        if (orderId.isBlank()) return
        val updated = _pendingCommissionOrderIds.value.toMutableSet().apply { add(orderId) }
        _pendingCommissionOrderIds.value = updated
        sharedPrefs.edit().putStringSet("pending_commission_orders", updated).apply()
    }

    fun removeOrderCommissionPending(orderId: String) {
        if (orderId.isBlank()) return
        val updated = _pendingCommissionOrderIds.value.toMutableSet().apply { remove(orderId) }
        _pendingCommissionOrderIds.value = updated
        sharedPrefs.edit().putStringSet("pending_commission_orders", updated).apply()
    }

    fun isOrderCommissionPendingApproval(orderId: String): Boolean {
        return _pendingCommissionOrderIds.value.contains(orderId)
    }

    // --- New Order Popup Alert for Active Technicians ---
    private val _newOrderAlert = MutableStateFlow<KodyarRepairOrder?>(null)
    val newOrderAlert: StateFlow<KodyarRepairOrder?> = _newOrderAlert.asStateFlow()
    private val knownOrderIds = mutableSetOf<String>()
    private var hasInitializedOrderIds = false
    private var orderPollingJob: kotlinx.coroutines.Job? = null

    fun dismissNewOrderAlert() {
        _newOrderAlert.value = null
    }

    fun playOrderAlertSound() {
        try {
            val alertTone = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = android.media.RingtoneManager.getRingtone(context, alertTone)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Error playing notification alert sound", e)
        }
    }

    fun syncTechnicianStatusFromServer(user: KodyarUser, techList: List<KodyarTechnician> = _liveTechnicians.value) {
        val isTech = user.role == "technician" || user.role == "tech" || user.role == "repairman" || user.isTechnicianUser
        if (!isTech) return
        if (_isTechStatusUpdating.value) return

        // 1. Check if user object directly indicates vacation from server
        var onVacation = user.isVacation

        // 2. Also check if the technician appears in live techList (from /api/technicians)
        val matchedTech = techList.firstOrNull { tech ->
            (tech.id?.isNotBlank() == true && (tech.id == user.id || tech.user_id == user.id)) ||
            (tech.phone?.isNotBlank() == true && tech.phone == user.phone) ||
            (tech.name?.isNotBlank() == true && tech.name == user.full_name)
        }

        if (matchedTech != null) {
            if (matchedTech.isVacation) {
                onVacation = true
            } else if (matchedTech.status == "active" || matchedTech.work_status == "active") {
                onVacation = false
            }
        }

        val isOnline = !onVacation
        if (_isTechnicianOnline.value != isOnline) {
            _isTechnicianOnline.value = isOnline
            sharedPrefs.edit().putBoolean("technician_online_status", isOnline).apply()
            Log.d("AssistantViewModel", "Synced technician online status with server: isOnline=$isOnline (vacation=$onVacation)")
        }
    }

    fun toggleTechnicianStatus(onResult: (Boolean, String?) -> Unit) {
        val current = _currentUser.value
        val token = getSessionToken()
            ?.takeIf { it.isNotBlank() }
            ?: current?.id
            ?: getSavedAuthPhone()
        val newStatus = !_isTechnicianOnline.value
        val statusString = if (newStatus) "active" else "vacation"

        _isTechStatusUpdating.value = true
        _isTechnicianOnline.value = newStatus
        sharedPrefs.edit().putBoolean("technician_online_status", newStatus).apply()

        // 1. Immediately update local currentUser state
        if (current != null) {
            val updated = current.copy(
                status = statusString,
                work_status = statusString,
                technician_status = statusString,
                is_online = newStatus,
                vacation = !newStatus,
                on_vacation = !newStatus
            )
            _currentUser.value = updated
            saveUserToCache(updated)
        }

        // 2. Immediately update this technician in _liveTechnicians for app-wide & customer views
        val currentTechs = _liveTechnicians.value.map { tech ->
            val isMatch = (tech.id?.isNotBlank() == true && (tech.id == current?.id || tech.user_id == current?.id)) ||
                          (!tech.phone.isNullOrBlank() && tech.phone == current?.phone) ||
                          (!tech.name.isNullOrBlank() && tech.name == current?.full_name)
            if (isMatch) {
                tech.copy(
                    status = statusString,
                    work_status = statusString,
                    is_online = newStatus,
                    vacation = !newStatus,
                    on_vacation = !newStatus
                )
            } else {
                tech
            }
        }
        _liveTechnicians.value = currentTechs

        // Instantly refresh orders to filter out or bring back regional unassigned orders
        loadRepairs(silent = true)

        viewModelScope.launch {
            var serverSuccess = false
            var serverError: String? = null
            try {
                val matchedTech = _liveTechnicians.value.find { tech ->
                    (tech.id?.isNotBlank() == true && (tech.id == current?.id || tech.user_id == current?.id)) ||
                    (!tech.phone.isNullOrBlank() && tech.phone == current?.phone) ||
                    (!tech.name.isNullOrBlank() && tech.name == current?.full_name)
                }
                val techId = matchedTech?.id?.takeIf { it.isNotBlank() } ?: current?.id
                val userPhone = current?.phone ?: matchedTech?.phone ?: getSavedAuthPhone()
                val candidateIds = listOfNotNull(
                    matchedTech?.id,
                    matchedTech?.user_id,
                    current?.id,
                    userPhone,
                    matchedTech?.phone
                ).filter { it.isNotBlank() }.distinct()

                val res = repository.updateTechnicianStatusApi(
                    token = token,
                    status = statusString,
                    technicianId = techId,
                    candidateIds = candidateIds,
                    phone = userPhone
                )
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    serverSuccess = true
                    Log.d("AssistantViewModel", "Technician status successfully updated on server: $statusString")
                } else {
                    serverError = res.message ?: res.error
                }
            } catch (e: Exception) {
                serverError = e.message
                Log.e("AssistantViewModel", "Error updating technician status on server", e)
            } finally {
                _isTechStatusUpdating.value = false
                onResult(serverSuccess, serverError)
                syncAllLiveAppData(isInitial = false)
            }
        }
    }

    fun startOrderPolling() {
        if (orderPollingJob?.isActive == true) return
        orderPollingJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                val user = _currentUser.value
                val isTech = user?.isTechnicianUser == true || user?.role == "technician" || user?.role == "tech" || user?.role == "repairman"
                if (isTech && _isTechnicianOnline.value && getSessionToken() != null) {
                    loadRepairs(silent = true)
                }
            }
        }
    }

    fun stopOrderPolling() {
        orderPollingJob?.cancel()
        orderPollingJob = null
    }

    fun acceptDisclaimer() {
        _isDisclaimerAccepted.value = true
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val user = _currentUser.value
        val userId = user?.id ?: "guest_${System.currentTimeMillis()}"
        val userPhone = user?.phone ?: "مهمان"
        val appVersion = com.example.BuildConfig.VERSION_NAME
        val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"

        sharedPrefs.edit()
            .putBoolean("disclaimer_accepted", true)
            .putString("disclaimer_accepted_at", dateStr)
            .putString("disclaimer_user_id", userId)
            .putString("disclaimer_user_phone", userPhone)
            .putString("disclaimer_app_version", appVersion)
            .putString("disclaimer_device_info", deviceInfo)
            .apply()
    }

    companion object {
        private val PROVINCE_FAMILIES = listOf(
            ProvinceFamily("اراک", listOf("اراک", "مرکزی", "استان مرکزی"), listOf("فرمهین", "فراهان", "ساوه", "خمین", "محلات", "شازند", "تفرش", "دلیجان", "زرندیه", "کمیجان", "آشتیان", "خنداب", "مامونیه", "غرق آباد", "میلاجرد", "ساروق", "نراق")),
            ProvinceFamily("تهران", listOf("تهران", "استان تهران"), listOf("ری", "شهر ری", "شمیرانات", "شمیران", "تجریش", "اسلامشهر", "شهریار", "دماوند", "ورامین", "پاکدشت", "رباط کریم", "قدس", "شهر قدس", "ملارد", "پردیس", "بهارستان", "قرچک", "فیروزکوه", "بومهن", "رودهن", "لواسان", "اندیشه", "صفادشت", "کهریزک", "حسن آباد")),
            ProvinceFamily("مشهد", listOf("مشهد", "خراسان", "خراسان رضوی"), listOf("نیشابور", "سبزوار", "تربت حیدریه", "قوچان", "چناران", "کاشمر", "تربت جام", "تایباد", "سرخس", "گناباد", "فریمان", "بینالود", "طرقبه", "شاندیز", "خواف", "بردسکن", "درگز", "کلات", "باخرز", "خلیل آباد")),
            ProvinceFamily("اصفهان", listOf("اصفهان", "استان اصفهان"), listOf("کاشان", "خمینی شهر", "نجف آباد", "شاهین شهر", "فولادشهر", "لنجان", "شهرضا", "مبارکه", "فلاورجان", "آران و بیدگل", "زرین شهر", "گلپایگان", "سمیرم", "خوانسار", "تیران", "داران", "نطنز", "اردستان", "نائین")),
            ProvinceFamily("شیراز", listOf("شیراز", "فارس", "استان فارس"), listOf("مرودشت", "کازرون", "جهرم", "لار", "لارستان", "فسا", "داراب", "فیروزآباد", "ممسنی", "نورآباد", "آباده", "اقلید", "سپیدان", "استهبان", "نی ریز", "لامرد", "کوار")),
            ProvinceFamily("تبریز", listOf("تبریز", "آذربایجان شرقی", "آذربایجان"), listOf("مراغه", "مرند", "میانه", "اهر", "بناب", "سراب", "آذرشهر", "اسکو", "شبستر", "عجب شیر", "ملکان", "هریس", "بستان آباد", "کلیبر", "جلفا", "سهند")),
            ProvinceFamily("اهواز", listOf("اهواز", "خوزستان", "استان خوزستان"), listOf("آبادان", "دزفول", "خرمشهر", "ماهشهر", "بندر ماهشهر", "ایذه", "بهبهان", "شوشتر", "شوش", "امیدیه", "مسجد سلیمان", "رامهرمز", "اندیمشک", "شادگان", "هندیجان", "سوسنگرد", "دشت آزادگان")),
            ProvinceFamily("کرج", listOf("کرج", "البرز", "استان البرز"), listOf("فردیس", "ساوجبلاغ", "نظرآباد", "هشتگرد", "طالقان", "اشتهارد", "کمال شهر", "محمدشهر", "ماهدشت", "گرمدره", "چهارباغ")),
            ProvinceFamily("قم", listOf("قم", "استان قم"), listOf("کهک", "جعفریه", "سلفچگان", "قنوات", "دستجرد")),
            ProvinceFamily("رشت", listOf("رشت", "گیلان", "استان گیلان"), listOf("انزلی", "بندر انزلی", "لاهیجان", "لنگرود", "فومن", "رودسر", "تالش", "هشتپر", "صومعه سرا", "آستارا", "آستانه اشرفیه", "رودبار", "منجیل", "لوشان", "ماسوله", "ماسال", "شفت", "سیاهکل", "رضوانشهر")),
            ProvinceFamily("ساری", listOf("ساری", "مازندران", "استان مازندران"), listOf("بابل", "آمل", "قائم شهر", "قائمشهر", "تنکابن", "شهسوار", "چالوس", "نوشهر", "بابلسر", "رامسر", "محمودآباد", "نور", "نکا", "بهشهر", "فریدونکنار", "جویبار", "سوادکوه", "زیرآب", "پل سفید", "کلاردشت", "عباس آباد", "رویان")),
            ProvinceFamily("کرمانشاه", listOf("کرمانشاه", "استان کرمانشاه"), listOf("اسلام آباد غرب", "کنگاور", "سنقر", "جوانرود", "صحنه", "هرسین", "سرپل ذهاب", "پاوه", "روانسر", "گیلانغرب", "قصر شیرین", "تازه آباد")),
            ProvinceFamily("ارومیه", listOf("ارومیه", "آذربایجان غربی"), listOf("خوی", "بوکان", "مهاباد", "میاندوآب", "سلماس", "پیرانشهر", "نقده", "تکاب", "ماکو", "سردشت", "شاهین دژ", "اشنویه", "قره ضیاءالدین", "سیه چشمه")),
            ProvinceFamily("یزد", listOf("یزد", "استان یزد"), listOf("میبد", "اردکان", "مهریز", "بافق", "ابرکوه", "تفت", "اشکذر", "هرات", "مروست", "بهاباد")),
            ProvinceFamily("کرمان", listOf("کرمان", "استان کرمان"), listOf("رفسنجان", "سیرجان", "جیرفت", "بم", "زرند", "کهنوج", "شهر بابک", "بافت", "بردسیر", "عنبرآباد", "منوجان", "راور")),
            ProvinceFamily("همدان", listOf("همدان", "استان همدان"), listOf("ملایر", "نهاوند", "تویسرکان", "کبودرآهنگ", "بهار", "رزن", "فامنین", "لالجین", "مریانج", "قروه درجزین")),
            ProvinceFamily("خرم آباد", listOf("خرم آباد", "لرستان", "استان لرستان"), listOf("بروجرد", "دورود", "الیگودرز", "کوهدشت", "ازنا", "پلدختر", "الشتر", "سلسله", "نورآباد", "دلفان", "چگنی")),
            ProvinceFamily("قزوین", listOf("قزوین", "استان قزوین"), listOf("الوند", "البرز قزوین", "تاکستان", "بوئین زهرا", "آبیک", "محمدیه", "محمودآباد نمونه", "اقبالیه", "شریفیه", "ضیاءآباد")),
            ProvinceFamily("زنجان", listOf("زنجان", "استان زنجان"), listOf("ابهر", "خرمدره", "قیدار", "خدابنده", "طارم", "آب بر", "ماهنشان", "ایجرود", "زرین آباد", "سلطانیه")),
            ProvinceFamily("سمنان", listOf("سمنان", "استان سمنان"), listOf("شاهرود", "دامغان", "گرمسار", "مهدی شهر", "سنگسر", "سرخه", "آرادان", "میامی", "بسطام", "شهمیرزاد")),
            ProvinceFamily("گرگان", listOf("گرگان", "گلستان", "استان گلستان"), listOf("گنبد کاووس", "گنبد", "علی آباد کتول", "بندر ترکمن", "آق قلا", "کلاله", "آزادشهر", "کردکوی", "مینودشت", "گالیکش", "بندر گز", "رامیان", "مراوه تپه", "گمیشان")),
            ProvinceFamily("بوشهر", listOf("بوشهر", "استان بوشهر"), listOf("برازجان", "دشتستان", "گناوه", "بندر گناوه", "کنگان", "بندر کنگان", "عسلویه", "خورموج", "دشتی", "جم", "دیلم", "بندر دیلم", "اهرم", "تنگستان", "دیر", "بندر دیر")),
            ProvinceFamily("بندر عباس", listOf("بندر عباس", "بندرعباس", "هرمزگان", "استان هرمزگان"), listOf("قشم", "کیش", "میناب", "بندرلنگه", "لنگه", "رودان", "بستک", "حاجی آباد", "جاسک", "بندر خمیر", "پارسیان", "گاوبندی", "سیریک", "بشاگرد")),
            ProvinceFamily("زاهدان", listOf("زاهدان", "سیستان و بلوچستان", "سیستان", "بلوچستان"), listOf("زابل", "ایرانشهر", "چابهار", "بندر چابهار", "سراوان", "خاش", "نیک شهر", "کنارک", "راسک", "سرباز", "میرجاوه", "زهک", "هیرمند", "قصرقند")),
            ProvinceFamily("سنندج", listOf("سنندج", "کردستان", "استان کردستان"), listOf("سقز", "مریوان", "بانه", "قروه", "کامیاران", "بیجار", "دیواندره", "دهگلان", "سروآباد")),
            ProvinceFamily("اردبیل", listOf("اردبیل", "استان اردبیل"), listOf("پارس آباد", "مشگین شهر", "خلخال", "گرمی", "نمین", "بیله سوار", "کوثر", "گیوی", "سرعین", "نیر", "اصلاندوز")),
            ProvinceFamily("شهرکرد", listOf("شهرکرد", "چهارمحال و بختیاری", "چهارمحال"), listOf("بروجن", "فارسان", "لردگان", "فرخ شهر", "سامان", "بن", "کیار", "شلمزار", "کوهرنگ", "چلگرد", "اردل", "خانمیرزا")),
            ProvinceFamily("ایلام", listOf("ایلام", "استان ایلام"), listOf("دهلران", "ایوان", "آبدانان", "مهران", "دره شهر", "چرداول", "سرابله", "بدره", "ملکشاهی", "سیروان")),
            ProvinceFamily("یاسوج", listOf("یاسوج", "کهگیلویه و بویراحمد", "کهگیلویه"), listOf("دوگنبدان", "گچساران", "دهدشت", "لیکک", "بهمئی", "چرام", "لنده", "سی سخت", "دنا", "باشت", "مارگون")),
            ProvinceFamily("بجنورد", listOf("بجنورد", "خراسان شمالی"), listOf("شیروان", "اسفراین", "گرمه", "جاجرم", "آشخانه", "مانه و سملقان", "فاروج", "راز و جرگلان")),
            ProvinceFamily("بیرجند", listOf("بیرجند", "خراسان جنوبی"), listOf("قائنات", "قائن", "فردوس", "طبس", "نهبندان", "سرایان", "سربیشه", "بشرویه", "درمیان", "اسدیه", "خوسف", "زیرکوه"))
        )
    }

    private data class ProvinceFamily(
        val family: String,
        val aliases: List<String>,
        val regions: List<String>
    )

    fun areCitiesCompatible(cityA: String?, cityB: String?): Boolean {
        fun cleanCityNorm(input: String?): String {
            var s = normalizePersian(input)
            val prefixes = listOf("شهرستان ", "شهر ", "استان ", "بخش ", "حومه ", "منطقه ", "روستای ", "روستا ", "شهرک ")
            for (p in prefixes) {
                if (s.startsWith(p)) {
                    s = s.removePrefix(p).trim()
                }
            }
            return s
        }

        val normA = normalizePersian(cityA)
        val normB = normalizePersian(cityB)
        if (normA.isEmpty() || normB.isEmpty()) return true
        if (normA == "همه" || normB == "همه" || normA == "all" || normB == "all") return true
        
        val cleanA = cleanCityNorm(cityA)
        val cleanB = cleanCityNorm(cityB)
        
        // Direct match or substring match on raw or cleaned names
        if (normA == normB || cleanA == cleanB ||
            normA.contains(normB, ignoreCase = true) || normB.contains(normA, ignoreCase = true) ||
            cleanA.contains(cleanB, ignoreCase = true) || cleanB.contains(cleanA, ignoreCase = true)) {
            return true
        }
        
        // 1. Dynamic match using server-provided center/regions structure
        val centers = _liveCitiesStructured.value
        if (centers.isNotEmpty()) {
            fun centerNameFor(cityNorm: String, cityClean: String): String? {
                for (center in centers) {
                    val centerName = normalizePersian(
                        center.name ?: center.title ?: center.city ?: center.cityName ?: center.name_fa ?: center.nameFarsi
                    )
                    val centerClean = cleanCityNorm(centerName)
                    if (centerName.isEmpty()) continue
                    if (centerName == cityNorm || centerClean == cityClean || cityNorm.contains(centerName) || centerName.contains(cityNorm)) return centerName
                    val regionMatch = center.regions?.any { 
                        val rNorm = normalizePersian(it)
                        val rClean = cleanCityNorm(rNorm)
                        rNorm == cityNorm || rClean == cityClean || cityNorm.contains(rNorm) || rNorm.contains(cityNorm)
                    } == true
                    if (regionMatch) return centerName
                }
                return null
            }
            val centerA = centerNameFor(normA, cleanA)
            val centerB = centerNameFor(normB, cleanB)
            if (centerA != null && centerB != null && centerA == centerB) {
                return true
            }
        }
        
        // 2. Built-in full province families dictionary (synced with server)
        fun findFamilies(textNorm: String, textClean: String): Set<String> {
            val result = mutableSetOf<String>()
            for (pf in PROVINCE_FAMILIES) {
                val fNorm = normalizePersian(pf.family)
                val fClean = cleanCityNorm(fNorm)
                val isFamilyMatch = textNorm.contains(fNorm) || fNorm.contains(textNorm) ||
                                   textClean.contains(fClean) || fClean.contains(textClean)
                val isAliasMatch = pf.aliases.any { 
                    val aNorm = normalizePersian(it)
                    val aClean = cleanCityNorm(aNorm)
                    textNorm.contains(aNorm) || aNorm.contains(textNorm) ||
                    textClean.contains(aClean) || aClean.contains(textClean)
                }
                val isRegionMatch = pf.regions.any { 
                    val rNorm = normalizePersian(it)
                    val rClean = cleanCityNorm(rNorm)
                    textNorm.contains(rNorm) || rNorm.contains(textNorm) ||
                    textClean.contains(rClean) || rClean.contains(textClean)
                }
                if (isFamilyMatch || isAliasMatch || isRegionMatch) {
                    result.add(fNorm)
                }
            }
            return result
        }

        val famA = findFamilies(normA, cleanA)
        val famB = findFamilies(normB, cleanB)
        if (famA.isNotEmpty() && famB.isNotEmpty()) {
            if (famA.any { famB.contains(it) }) {
                return true
            }
        }
        
        return false
    }

    // --- Database states dynamically loaded from shared Codyar database ---
    private val _liveErrorCodes = MutableStateFlow<List<KodyarErrorCode>>(emptyList())
    val liveErrorCodes: StateFlow<List<KodyarErrorCode>> = _liveErrorCodes.asStateFlow()

    private val _liveSpareParts = MutableStateFlow<List<KodyarSparePart>>(emptyList())
    val liveSpareParts: StateFlow<List<KodyarSparePart>> = _liveSpareParts.asStateFlow()

    private val _storeSearchQuery = MutableStateFlow("")
    val storeSearchQuery: StateFlow<String> = _storeSearchQuery.asStateFlow()

    fun setStoreSearchQuery(query: String) {
        _storeSearchQuery.value = query
    }

    fun clearStoreSearchQuery() {
        _storeSearchQuery.value = ""
    }

    private val _liveTechnicians = MutableStateFlow<List<KodyarTechnician>>(emptyList())
    val liveTechnicians: StateFlow<List<KodyarTechnician>> = _liveTechnicians.asStateFlow()

    private val _liveCommonProblems = MutableStateFlow<List<KodyarCommonProblem>>(emptyList())
    val liveCommonProblems: StateFlow<List<KodyarCommonProblem>> = _liveCommonProblems.asStateFlow()

    private val _liveCategories = MutableStateFlow<List<String>>(listOf("همه"))
    val liveCategories: StateFlow<List<String>> = _liveCategories.asStateFlow()

    private val _liveBrands = MutableStateFlow<List<String>>(listOf("همه"))
    val liveBrands: StateFlow<List<String>> = _liveBrands.asStateFlow()

    private val _liveCities = MutableStateFlow<List<String>>(listOf("همه"))
    val liveCities: StateFlow<List<String>> = _liveCities.asStateFlow()

    private val _liveCitiesStructured = MutableStateFlow<List<KodyarCity>>(emptyList())
    val liveCitiesStructured: StateFlow<List<KodyarCity>> = _liveCitiesStructured.asStateFlow()

    private val _isDatabaseLoading = MutableStateFlow(false)
    val isDatabaseLoading: StateFlow<Boolean> = _isDatabaseLoading.asStateFlow()

    private val _isTechniciansLoading = MutableStateFlow(false)
    val isTechniciansLoading: StateFlow<Boolean> = _isTechniciansLoading.asStateFlow()

    private val _isSparePartsLoading = MutableStateFlow(false)
    val isSparePartsLoading: StateFlow<Boolean> = _isSparePartsLoading.asStateFlow()

    private val _isLiveDataSyncing = MutableStateFlow(false)
    val isLiveDataSyncing: StateFlow<Boolean> = _isLiveDataSyncing.asStateFlow()

    // --- Referral / Invite Code States ---
    private val _appliedReferralCode = MutableStateFlow<String?>(null)
    val appliedReferralCode: StateFlow<String?> = _appliedReferralCode.asStateFlow()

    private val _referralDiscountPercent = MutableStateFlow(0)
    val referralDiscountPercent: StateFlow<Int> = _referralDiscountPercent.asStateFlow()

    fun applyReferralCode(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.length < 4) return false
        _appliedReferralCode.value = trimmed
        _referralDiscountPercent.value = 25 // Default 25%
        return true
    }

    fun removeReferralCode() {
        _appliedReferralCode.value = null
        _referralDiscountPercent.value = 0
    }

    // --- Search states ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedBrand = MutableStateFlow("همه")
    val selectedBrand: StateFlow<String> = _selectedBrand.asStateFlow()

    private val _selectedCategory = MutableStateFlow("همه")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _modelQuery = MutableStateFlow("")
    val modelQuery: StateFlow<String> = _modelQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<KodyarErrorCode>>(emptyList())
    val searchResults: StateFlow<List<KodyarErrorCode>> = _searchResults.asStateFlow()

    private val _showOnlySaved = MutableStateFlow(false)
    val showOnlySaved: StateFlow<Boolean> = _showOnlySaved.asStateFlow()

    // --- Bookmarked / Saved Errors ---
    val savedErrors: StateFlow<List<SavedErrorEntity>> = repository.allSavedErrors
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setShowOnlySaved(onlySaved: Boolean) {
        _showOnlySaved.value = onlySaved
        updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
    }

    // --- Auth states ---
    private val _currentUser = MutableStateFlow<KodyarUser?>(null)
    val currentUser: StateFlow<KodyarUser?> = kotlinx.coroutines.flow.combine(_currentUser, _liveTechnicians) { user, techList ->
        if (user == null) return@combine null

        val isTech = user.role == "technician" || user.role == "tech" || user.role == "repairman" || user.isTechnicianUser
        val isSuspended = user.isSuspended
        val verifiedInList = if (isTech && !isSuspended) isTechnicianVerifiedInList(user, techList) else false
        val finalApproved = if (isTech) (!isSuspended && (user.isApprovedUser || verifiedInList)) else true

        var resolvedUser = if (isTech && isSuspended) {
            user.copy(
                is_approved = false,
                approval_status = "suspended",
                is_verified = false,
                isVerified = false,
                status = "suspended"
            )
        } else if (isTech && finalApproved && !user.isApprovedUser) {
            user.copy(
                is_approved = true,
                approval_status = "approved",
                is_verified = true,
                isVerified = true,
                status = "approved"
            )
        } else {
            user
        }

        if (sharedPrefs.getBoolean("bazaar_premium_active", false)) {
            val sku = sharedPrefs.getString("bazaar_premium_sku", "") ?: ""
            val bazaarExp = calculateExpiryDateForSku(sku)
            val currentSub = resolvedUser.subscription
            val mergedSub = if (currentSub != null && currentSub.is_premium) {
                currentSub
            } else {
                KodyarSubscription(
                    is_premium = true,
                    expiry_date = bazaarExp
                )
            }
            resolvedUser = resolvedUser.copy(subscription = mergedSub)
        }

        resolvedUser
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // --- Subscription Plans state ---
    private val _subscriptionPlans = MutableStateFlow<List<KodyarSubscriptionPlan>>(emptyList())
    val subscriptionPlans: StateFlow<List<KodyarSubscriptionPlan>> = _subscriptionPlans.asStateFlow()

    private val _isPlansLoading = MutableStateFlow(false)
    val isPlansLoading: StateFlow<Boolean> = _isPlansLoading.asStateFlow()

    // --- Cart states ---
    private val _cart = MutableStateFlow<List<String>>(emptyList())
    val cart: StateFlow<List<String>> = _cart.asStateFlow()

    private val _cartQty = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cartQty: StateFlow<Map<String, Int>> = _cartQty.asStateFlow()

    private val _isPurchaseLoading = MutableStateFlow(false)
    val isPurchaseLoading: StateFlow<Boolean> = _isPurchaseLoading.asStateFlow()

    private val _purchaseSuccess = MutableStateFlow(false)
    val purchaseSuccess: StateFlow<Boolean> = _purchaseSuccess.asStateFlow()

    // --- Repair state ---
    private val _repairOrders = MutableStateFlow<List<KodyarRepairOrder>>(emptyList())
    val repairOrders: StateFlow<List<KodyarRepairOrder>> = _repairOrders.asStateFlow()

    private val _isRepairsLoading = MutableStateFlow(false)
    val isRepairsLoading: StateFlow<Boolean> = _isRepairsLoading.asStateFlow()

    // --- Part Purchase state ---
    private val _partPurchases = MutableStateFlow<List<PartPurchaseOrder>>(emptyList())
    val partPurchases: StateFlow<List<PartPurchaseOrder>> = _partPurchases.asStateFlow()

    private val partPurchasesAdapter by lazy {
        moshi.adapter<List<PartPurchaseOrder>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, PartPurchaseOrder::class.java)
        )
    }

    fun loadPartPurchases() {
        val json = sharedPrefs.getString("part_purchases_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val list = partPurchasesAdapter.fromJson(json)
                if (list != null) {
                    _partPurchases.value = list
                }
            } catch (e: java.lang.Exception) {
                Log.e("AssistantViewModel", "Error parsing part purchases", e)
            }
        }
    }

    fun savePartPurchase(order: PartPurchaseOrder) {
        val currentList = _partPurchases.value.toMutableList()
        currentList.add(0, order) // Add at top
        _partPurchases.value = currentList
        try {
            val json = partPurchasesAdapter.toJson(currentList)
            sharedPrefs.edit().putString("part_purchases_json", json).apply()
        } catch (e: java.lang.Exception) {
            Log.e("AssistantViewModel", "Error saving part purchases", e)
        }
    }

    private fun getCurrentPersianDate(): String {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val pYear = year - 621
        return "$pYear/$month/$day"
    }

    private fun calculateExpiryDateForSku(sku: String): String {
        val calendar = java.util.Calendar.getInstance()
        val daysToAdd = when (sku) {
            "ir.golden.com" -> 30
            "ir.silver.com" -> 90
            "ir.almas.com" -> 180
            "ir.12-month.com" -> 365
            else -> 30
        }
        calendar.add(java.util.Calendar.DAY_OF_MONTH, daysToAdd)
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return "$year-${String.format("%02d", month)}-${String.format("%02d", day)}"
    }

    // --- Sub / Verification state ---
    private val _isCardVerifyLoading = MutableStateFlow(false)
    val isCardVerifyLoading: StateFlow<Boolean> = _isCardVerifyLoading.asStateFlow()

    private val _cardVerifySuccess = MutableStateFlow(false)
    val cardVerifySuccess: StateFlow<Boolean> = _cardVerifySuccess.asStateFlow()

    // --- Usage counts ---
    private val _freeErrorCount = MutableStateFlow(0)
    val freeErrorCount: StateFlow<Int> = _freeErrorCount.asStateFlow()

    private val _freeProblemCount = MutableStateFlow(0)
    val freeProblemCount: StateFlow<Int> = _freeProblemCount.asStateFlow()

    private val _bankCardInfo = MutableStateFlow<com.example.data.api.CardInfoResponse?>(
        com.example.data.api.CardInfoResponse(
            success = true,
            cardNumber = "6104-3389-6112-6667",
            card_number = "6104-3389-6112-6667",
            cardHolder = "مهدی عباسی (کدیار۲۴)",
            card_holder = "مهدی عباسی (کدیار۲۴)",
            bankName = "بانک ملت",
            bank_name = "بانک ملت"
        )
    )
    val bankCardInfo: StateFlow<com.example.data.api.CardInfoResponse?> = _bankCardInfo.asStateFlow()

    fun loadBankCardInfo() {
        viewModelScope.launch {
            try {
                val res = repository.getCardInfo()
                if (res.success == true || !res.cardNumber.isNullOrBlank() || !res.card_number.isNullOrBlank()) {
                    _bankCardInfo.value = res
                }
            } catch (_: Exception) {}
        }
    }

    init {
        loadPendingCommissionOrders()
        observeRoomDatabase()

        // Immediate fast local cache load and offline seed in background (Instant startup - zero UI blocking)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Pre-warm encrypted storage on IO thread so main thread is never blocked
                val dummy = encryptedPrefs
            } catch (_: Exception) {}
            repository.seedInitialDataIfNeeded()
            loadPersistedCart()
            loadCachedDatabase()
        }

        _isDatabaseLoading.value = false
        _isSparePartsLoading.value = false

        // Coordinated instant live data sync on app launch
        syncAllLiveAppData(isInitial = true)

        setupNetworkCallback()
        viewModelScope.launch {
            networkMonitor.isOnline.drop(1).collect { online ->
                if (online) {
                    Log.d("AssistantViewModel", "Network state online. Refreshing dynamic data from server...")
                    syncAllLiveAppData(isInitial = false)
                }
            }
        }
        viewModelScope.launch {
            savedErrors.collect {
                if (_showOnlySaved.value) {
                    updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
                }
            }
        }
    }

    private fun observeRoomDatabase() {
        repository.getCachedErrorCodes()?.let { flow ->
            viewModelScope.launch(Dispatchers.IO) {
                flow.collect { entities ->
                    if (entities.isNotEmpty()) {
                        val domainList = entities.map { it.toDomain() }
                        _liveErrorCodes.value = domainList

                        val categories = listOf("همه") + domainList.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct()
                        val brands = listOf("همه") + domainList.mapNotNull { it.brand }.filter { it.isNotBlank() }.distinct()
                        if (categories.size > 1) _liveCategories.value = categories
                        if (brands.size > 1) _liveBrands.value = brands
                        updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
                        _isDatabaseLoading.value = false
                    }
                }
            }
        }

        repository.getCachedCommonProblems()?.let { flow ->
            viewModelScope.launch(Dispatchers.IO) {
                flow.collect { entities ->
                    if (entities.isNotEmpty()) {
                        _liveCommonProblems.value = entities.map { it.toDomain() }
                    }
                }
            }
        }

        repository.getCachedTechnicians()?.let { flow ->
            viewModelScope.launch(Dispatchers.IO) {
                flow.collect { entities ->
                    if (entities.isNotEmpty()) {
                        _liveTechnicians.value = entities.map { it.toDomain() }
                        _isTechniciansLoading.value = false
                    }
                }
            }
        }

        repository.getCachedSpareParts()?.let { flow ->
            viewModelScope.launch(Dispatchers.IO) {
                flow.collect { entities ->
                    if (entities.isNotEmpty()) {
                        _liveSpareParts.value = entities.map { it.toDomain() }
                        _isSparePartsLoading.value = false
                    }
                }
            }
        }
    }

    fun fetchSubscriptionPlans() {
        viewModelScope.launch {
            _isPlansLoading.value = true
            try {
                val response = repository.getSubscriptionPlans()
                if ((response.status == "ok" || response.status == "success") && response.plans != null) {
                    _subscriptionPlans.value = response.plans
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to fetch subscription plans: ${e.message}")
            } finally {
                _isPlansLoading.value = false
            }
        }
    }

    private fun loadCachedDatabase() {
        try {
            // 1. Instant loading of unified cached database
            val cachedJson = sharedPrefs.getString("cached_kodyar_database", null)
            if (!cachedJson.isNullOrEmpty()) {
                if (cachedJson.contains("err_samsung_wm_5c") || cachedJson.contains("prob_wm_1") || cachedJson.contains("part_water_filter_side")) {
                    // Purge legacy seeded default data from local storage
                    sharedPrefs.edit().remove("cached_kodyar_database").apply()
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.clearAllOfflineCache()
                    }
                } else {
                    val response = databaseAdapter.fromJson(cachedJson)
                    if (response != null) {
                        if (response.resolvedErrorCodes.isNotEmpty()) {
                            _liveErrorCodes.value = response.resolvedErrorCodes
                        }
                        if (response.resolvedCommonProblems.isNotEmpty()) {
                            _liveCommonProblems.value = response.resolvedCommonProblems
                        }
                        if (response.resolvedSpareParts.isNotEmpty()) {
                            _liveSpareParts.value = response.resolvedSpareParts
                        }
                        if (response.resolvedTechnicians.isNotEmpty()) {
                            _liveTechnicians.value = enrichTechniciansWithReviews(response.resolvedTechnicians)
                        }

                        val dynamicCats = (response.resolvedCategoriesList + response.resolvedErrorCodes.mapNotNull { it.resolvedCategory }).filter { it.isNotBlank() }.distinct()
                        if (dynamicCats.isNotEmpty()) {
                            _liveCategories.value = listOf("همه") + dynamicCats
                        }

                        val dynamicBrands = (response.resolvedBrandsList + response.resolvedErrorCodes.mapNotNull { it.brand }).filter { it.isNotBlank() }.distinct()
                        if (dynamicBrands.isNotEmpty()) {
                            _liveBrands.value = listOf("همه") + dynamicBrands
                        }
                        val parsedCities = response.resolvedCitiesList.flatMap { 
                            listOfNotNull(it.name, it.title, it.city, it.cityName, it.name_fa, it.nameFarsi, it.slug)
                        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                        if (parsedCities.isNotEmpty()) {
                            _liveCities.value = listOf("همه") + parsedCities
                            _liveCitiesStructured.value = response.resolvedCitiesList
                        }

                        updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
                    }
                }
            }

            // 2. Instant load of dedicated cached technicians
            val cachedTechsJson = sharedPrefs.getString("cached_technicians_json", null)
            if (!cachedTechsJson.isNullOrEmpty()) {
                try {
                    val techs = techniciansAdapter.fromJson(cachedTechsJson)
                    val realTechs = techs?.filter { !it.id.isNullOrBlank() && !it.id.startsWith("tech_seed") }
                    if (!realTechs.isNullOrEmpty()) {
                        _liveTechnicians.value = enrichTechniciansWithReviews(realTechs)
                    } else {
                        _liveTechnicians.value = emptyList()
                        sharedPrefs.edit().remove("cached_technicians_json").apply()
                    }
                } catch (e: Exception) {
                    Log.e("AssistantViewModel", "Error parsing cached technicians", e)
                }
            }

            // 3. Instant load of dedicated cached store spare parts
            val cachedPartsJson = sharedPrefs.getString("cached_spare_parts_json", null)
            if (!cachedPartsJson.isNullOrEmpty()) {
                try {
                    val parts = sparePartsAdapter.fromJson(cachedPartsJson)
                    val realParts = parts?.filter { !it.id.isNullOrBlank() && !it.id.startsWith("part_seed") }
                    if (!realParts.isNullOrEmpty()) {
                        _liveSpareParts.value = realParts
                    } else {
                        _liveSpareParts.value = emptyList()
                        sharedPrefs.edit().remove("cached_spare_parts_json").apply()
                    }
                } catch (e: Exception) {
                    Log.e("AssistantViewModel", "Error parsing cached spare parts", e)
                }
            }

            _isDatabaseLoading.value = false
            _isTechniciansLoading.value = false
            _isSparePartsLoading.value = false
            Log.d("AssistantViewModel", "Instant local data loaded for technicians, store parts, and database.")
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to load cached database.", e)
        }
        ensureDefaultFilters()
    }

    private fun ensureDefaultFilters() {
        if (_liveCategories.value.isEmpty()) {
            _liveCategories.value = listOf("همه")
        }
        if (_liveBrands.value.isEmpty()) {
            _liveBrands.value = listOf("همه")
        }
        if (_liveCities.value.isEmpty()) {
            _liveCities.value = listOf("همه")
        }
        updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
    }

    @Volatile
    private var cachedSessionToken: String? = null

    private fun saveSessionToken(token: String) {
        cachedSessionToken = token
        sharedPrefs.edit().putString("session_token", token).apply()
        com.example.data.api.KodyarRetrofitClient.setAuthToken(token)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                encryptedPrefs.edit().putString("session_token", token).apply()
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to save encrypted token", e)
            }
        }
    }

    private fun clearSessionToken() {
        cachedSessionToken = null
        sharedPrefs.edit().remove("session_token").apply()
        com.example.data.api.KodyarRetrofitClient.setAuthToken(null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                encryptedPrefs.edit().remove("session_token").apply()
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to clear encrypted token", e)
            }
        }
    }

    // --- Session & Auth functions ---
    fun getSavedAuthPhone(): String {
        return sharedPrefs.getString("saved_auth_phone", "") ?: ""
    }

    fun getSavedAuthPassword(): String {
        val fast = sharedPrefs.getString("saved_auth_password", "") ?: ""
        if (fast.isNotBlank()) return fast
        val encrypted = try {
            encryptedPrefs.getString("saved_auth_password", null)
        } catch (e: Exception) {
            null
        }
        if (!encrypted.isNullOrBlank()) return encrypted
        return ""
    }

    fun saveAuthCredentials(phone: String, pass: String) {
        val cleanPhone = normalizePhone(phone)
        val cleanPass = normalizeDigitsAndTrim(pass)
        if (cleanPhone.isNotBlank()) {
            sharedPrefs.edit().putString("saved_auth_phone", cleanPhone).apply()
        }
        if (cleanPass.isNotBlank()) {
            sharedPrefs.edit().putString("saved_auth_password", cleanPass).apply()
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    encryptedPrefs.edit().putString("saved_auth_password", cleanPass).apply()
                } catch (e: Exception) {
                    Log.e("AssistantViewModel", "Failed to save encrypted pass", e)
                }
            }
        }
    }

    fun getSessionToken(): String? {
        cachedSessionToken?.let { return it }
        val fastToken = sharedPrefs.getString("session_token", null)
        if (!fastToken.isNullOrBlank()) {
            cachedSessionToken = fastToken
            com.example.data.api.KodyarRetrofitClient.setAuthToken(fastToken)
            return fastToken
        }
        val token = try {
            encryptedPrefs.getString("session_token", null)
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to read encrypted token", e)
            null
        }
        if (!token.isNullOrBlank()) {
            cachedSessionToken = token
            com.example.data.api.KodyarRetrofitClient.setAuthToken(token)
            return token
        }
        return null
    }

    private fun checkSavedSession() {
        val token = getSessionToken()
        if (token != null) {
            val cached = getCachedUser()
            if (cached != null) {
                _currentUser.value = cached
            }
            loadCurrentUser(token)
        }
    }

    private fun saveUserToCache(user: KodyarUser) {
        val cached = getCachedUser()
        val persistentCity = if (!user.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${user.phone}", null) else null
        val userCity = user.resolvedCity
        val finalCity = if (userCity.isNotBlank()) userCity else (cached?.resolvedCity ?: persistentCity)
        val finalRole = if (!user.role.isNullOrBlank()) user.role else (cached?.role ?: "customer")
        val finalCategories = if (!user.categories.isNullOrEmpty()) user.categories else cached?.categories
        val isTechUser = finalRole == "technician" || finalRole == "tech" || finalRole == "repairman"
        val isSuspended = user.isSuspended
        val finalIsApproved = if (isTechUser) (!isSuspended && user.isApprovedUser) else true
        val finalApprovalStatus = if (isTechUser) {
            if (isSuspended) "suspended" else (if (finalIsApproved) "approved" else (user.approval_status ?: "pending"))
        } else {
            "approved"
        }

        val editor = sharedPrefs.edit()
            .putString("cached_user_id", user.id)
            .putString("cached_user_name", user.full_name)
            .putString("cached_user_phone", user.phone)
            .putBoolean("cached_user_premium", user.subscription?.is_premium ?: false)
            .putString("cached_user_expiry", user.subscription?.expiry_date)
            .putString("cached_user_role", finalRole)
            .putString("cached_user_city", finalCity)
            .putString("cached_user_categories", finalCategories?.joinToString(","))
            .putBoolean("cached_user_is_approved", finalIsApproved)
            .putString("cached_user_approval_status", finalApprovalStatus)

        if (!finalCity.isNullOrBlank() && !user.phone.isNullOrBlank()) {
            editor.putString("persistent_city_${user.phone}", finalCity)
        }
        editor.apply()
    }

    fun updateUserCityLocally(user: KodyarUser) {
        _currentUser.value = user
        saveUserToCache(user)
    }

    fun setPremiumUserLocally(sku: String) {
        val isPremium = sku.isNotEmpty()
        sharedPrefs.edit()
            .putBoolean("bazaar_premium_active", isPremium)
            .putString("bazaar_premium_sku", sku)
            .apply()

        val user = _currentUser.value
        if (user != null) {
            val updatedUser = user.copy(
                subscription = KodyarSubscription(
                    is_premium = isPremium,
                    expiry_date = if (isPremium) calculateExpiryDateForSku(sku) else ""
                )
            )
            _currentUser.value = updatedUser
            saveUserToCache(updatedUser)
        } else {
            // Trigger flow update
            _currentUser.value = null
        }
    }

    fun syncBazaarPurchaseToServer(sku: String, purchaseToken: String, orderId: String? = null) {
        setPremiumUserLocally(sku)
        val token = getSessionToken()
        val user = _currentUser.value
        val appPkg = context.packageName ?: "com.example"

        viewModelScope.launch {
            try {
                Log.d("AssistantViewModel", "Sending Bazaar purchase verification to server: sku=$sku")
                val response = repository.verifyBazaarPayment(
                    token = token,
                    sku = sku,
                    purchaseToken = purchaseToken,
                    packageName = appPkg,
                    userId = user?.id,
                    phone = user?.phone,
                    orderId = orderId
                )

                if (response.status == "ok" || response.status == "success" || response.success == true) {
                    Log.d("AssistantViewModel", "Bazaar purchase verified and recorded in server database successfully.")
                    if (token != null) {
                        loadCurrentUser(token)
                    }
                } else {
                    Log.w("AssistantViewModel", "Server returned non-ok for Bazaar purchase verification: ${response.error ?: response.message}")
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to sync Bazaar purchase to server database", e)
            }
        }
    }

    private fun getCachedUser(): KodyarUser? {
        val id = sharedPrefs.getString("cached_user_id", null) ?: return null
        val name = sharedPrefs.getString("cached_user_name", "") ?: ""
        val phone = sharedPrefs.getString("cached_user_phone", "") ?: ""
        val isPremium = sharedPrefs.getBoolean("cached_user_premium", false)
        val expiry = sharedPrefs.getString("cached_user_expiry", null)
        val role = sharedPrefs.getString("cached_user_role", "customer") ?: "customer"
        val city = sharedPrefs.getString("cached_user_city", null)
        val catsString = sharedPrefs.getString("cached_user_categories", null)
        val categories = if (!catsString.isNullOrEmpty()) catsString.split(",") else null
        val isTechUser = role == "technician" || role == "tech" || role == "repairman"
        val isApproved = if (isTechUser) {
            sharedPrefs.getBoolean("cached_user_is_approved", false)
        } else {
            true
        }
        val defaultApprovalStatus = if (isTechUser) "pending" else "approved"
        val approvalStatus = sharedPrefs.getString("cached_user_approval_status", defaultApprovalStatus) ?: defaultApprovalStatus
        val isSuspended = approvalStatus.lowercase() in listOf("suspended", "blocked", "banned", "معلق", "مسدود", "غیرفعال", "رد شده")
        val finalIsApproved = if (isSuspended) false else (if (isTechUser) isApproved else true)
        val finalStatus = if (isSuspended) "suspended" else (if (isTechUser) (if (finalIsApproved) "approved" else "pending") else "active")

        return KodyarUser(
            id = id,
            full_name = name,
            phone = phone,
            subscription = KodyarSubscription(is_premium = isPremium, expiry_date = expiry),
            role = role,
            city = city,
            categories = categories,
            is_approved = finalIsApproved,
            approval_status = if (isSuspended) "suspended" else (if (isTechUser) (if (finalIsApproved) "approved" else "pending") else "approved"),
            is_verified = if (isSuspended) false else (if (isTechUser) finalIsApproved else true),
            isVerified = if (isSuspended) false else (if (isTechUser) finalIsApproved else true),
            status = finalStatus
        )
    }

    private fun clearUserCache() {
        sharedPrefs.edit()
            .remove("cached_user_id")
            .remove("cached_user_name")
            .remove("cached_user_phone")
            .remove("cached_user_premium")
            .remove("cached_user_expiry")
            .remove("cached_user_role")
            .remove("cached_user_city")
            .remove("cached_user_categories")
            .remove("cached_user_is_approved")
            .remove("cached_user_approval_status")
            .apply()
    }

    fun getUniqueErrorCodesViewed(): Set<String> {
        return sharedPrefs.getStringSet("viewed_error_codes", emptySet()) ?: emptySet()
    }

    fun recordErrorCodeView(codeKey: String) {
        val current = getUniqueErrorCodesViewed().toMutableSet()
        current.add(codeKey)
        sharedPrefs.edit().putStringSet("viewed_error_codes", current).apply()
    }

    fun getUniqueProblemsViewed(): Set<String> {
        return sharedPrefs.getStringSet("viewed_problems", emptySet()) ?: emptySet()
    }

    fun recordProblemView(problemKey: String) {
        val current = getUniqueProblemsViewed().toMutableSet()
        current.add(problemKey)
        sharedPrefs.edit().putStringSet("viewed_problems", current).apply()
    }

    private fun isPositiveSubString(str: String?): Boolean {
        if (str.isNullOrBlank()) return false
        val s = str.trim().lowercase()
        return s == "active" || s == "فعال" || s == "true" || s == "1" || s == "valid" || s == "completed" || s == "موفق" || s.startsWith("sub_") || s.contains("ماه")
    }

    private fun extractSubscription(
        user: KodyarUser?,
        responseSub: KodyarSubscription? = null,
        dedicatedSub: KodyarResponse? = null
    ): KodyarSubscription {
        // 1. Check dedicated subscription response from server endpoint
        if (dedicatedSub != null) {
            val dSub = dedicatedSub.subscription
            val dIsPrem = dedicatedSub.is_premium == true || dedicatedSub.is_active == true || dedicatedSub.isActive == true ||
                    dSub?.is_premium == true || dSub?.is_active == true || dSub?.isActive == true || dSub?.active == true ||
                    isPositiveSubString(dSub?.status) || isPositiveSubString(dedicatedSub.status)
            val dExp = dedicatedSub.expiry_date ?: dedicatedSub.expires_at ?: dSub?.expiry_date ?: dSub?.expiryDate ?: dSub?.expires_at ?: dSub?.expire_at ?: dSub?.end_date ?: dSub?.subscription_expiry
            val dPlan = dSub?.plan ?: dSub?.plan_name ?: dedicatedSub.user?.plan
            if (dIsPrem || (!dExp.isNullOrBlank() && !dPlan.isNullOrBlank())) {
                return KodyarSubscription(
                    is_premium = true,
                    expiry_date = dExp,
                    plan = dPlan,
                    plan_name = dSub?.plan_name ?: dSub?.plan
                )
            }
        }

        // 2. Check user.subscription
        val uSub = user?.subscription
        if (uSub != null) {
            val isPrem = uSub.is_premium || uSub.is_active == true || uSub.isActive == true || uSub.active == true ||
                    isPositiveSubString(uSub.status)
            val exp = uSub.expiry_date ?: uSub.expiryDate ?: uSub.expires_at ?: uSub.expire_at ?: uSub.end_date ?: uSub.subscription_expiry
            val plan = uSub.plan ?: uSub.plan_name
            if (isPrem || (!exp.isNullOrBlank() && !plan.isNullOrBlank())) {
                return uSub.copy(is_premium = true, expiry_date = exp, plan = plan, plan_name = uSub.plan_name ?: plan)
            }
        }

        // 3. Check response.subscription
        if (responseSub != null) {
            val isPrem = responseSub.is_premium || responseSub.is_active == true || responseSub.isActive == true || responseSub.active == true ||
                    isPositiveSubString(responseSub.status)
            val exp = responseSub.expiry_date ?: responseSub.expiryDate ?: responseSub.expires_at ?: responseSub.expire_at ?: responseSub.end_date ?: responseSub.subscription_expiry
            val plan = responseSub.plan ?: responseSub.plan_name
            if (isPrem || (!exp.isNullOrBlank() && !plan.isNullOrBlank())) {
                return responseSub.copy(is_premium = true, expiry_date = exp, plan = plan, plan_name = responseSub.plan_name ?: plan)
            }
        }

        // 4. Check user direct fields
        if (user != null) {
            val userIsPrem = user.is_premium == true || user.has_active_subscription == true || user.hasActiveSubscription == true ||
                    user.is_active == true || user.isActive == true || 
                    user.is_vip == true || user.vip == true || user.has_subscription == true || user.hasSubscription == true ||
                    isPositiveSubString(user.subscription_status)
            val userExp = user.expiry_date ?: user.expiryDate ?: user.subscription_expiry ?: user.expires_at ?: user.expire_at ?: user.end_date
            val userPlan = user.plan ?: user.plan_name
            if (userIsPrem || (!userExp.isNullOrBlank() && !userPlan.isNullOrBlank())) {
                return KodyarSubscription(
                    is_premium = true,
                    expiry_date = userExp,
                    plan = userPlan,
                    plan_name = user.plan_name ?: user.plan
                )
            }
        }

        return KodyarSubscription(is_premium = false, expiry_date = null)
    }

    fun isTechnicianVerifiedInList(user: KodyarUser?, techList: List<KodyarTechnician>): Boolean {
        if (user == null || techList.isEmpty()) return false
        val userPhone = normalizePhone(user.phone)
        val userName = (user.full_name ?: "").trim()
        val userId = (user.id ?: "").trim()

        return techList.any { tech ->
            val tPhone = normalizePhone(tech.phone)
            val tName = tech.resolvedName.trim()
            val tId = (tech.id ?: "").trim()

            val matchesPhone = userPhone.isNotBlank() && tPhone.isNotBlank() && (userPhone == tPhone || userPhone.removePrefix("0") == tPhone.removePrefix("0"))
            val matchesId = userId.isNotBlank() && tId.isNotBlank() && userId == tId
            val matchesName = userName.isNotBlank() && tName.isNotBlank() && userName == tName

            (matchesPhone || matchesId || matchesName) && tech.resolvedIsVerified
        }
    }

    private fun checkAndSyncCurrentUserApprovalWithTechList(techList: List<KodyarTechnician>) {
        val user = _currentUser.value ?: return
        val isTech = user.role == "technician" || user.role == "tech" || user.role == "repairman" || user.isTechnicianUser
        if (!isTech) return
        if (user.isSuspended) return

        if (!user.isApprovedUser && isTechnicianVerifiedInList(user, techList)) {
            val updated = user.copy(
                is_approved = true,
                approval_status = "approved",
                is_verified = true,
                isVerified = true,
                status = if (user.isVacation) "vacation" else "approved"
            )
            _currentUser.value = updated
            saveUserToCache(updated)
            Log.d("AssistantViewModel", "Synced technician approval with verified server technicians list successfully.")
        }
        syncTechnicianStatusFromServer(_currentUser.value ?: user, techList)
    }

    fun loadCurrentUser(token: String) {
        viewModelScope.launch {
            try {
                // High-speed parallel fetch of user profile and dedicated subscription status directly from database
                val meDeferred = async { repository.getMe(token) }
                val subStatusDeferred = async { 
                    try { repository.getMySubscriptionStatus(token) } catch (_: Exception) { null } 
                }
                val response = meDeferred.await()
                val subStatusRes = subStatusDeferred.await()

                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val cached = getCachedUser()
                    val persistentCity = if (!response.user.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${response.user.phone}", null) else null
                    val sub = extractSubscription(response.user, response.subscription, subStatusRes)
                    val isTech = response.user.role == "technician" || response.user.role == "tech" || response.user.role == "repairman"
                    val isSuspended = response.user.isSuspended
                    val isApproved = if (isTech) (!isSuspended && response.user.isApprovedUser) else true
                    // Check against verified technicians list from server (only if not suspended)
                    val finalApproved = if (isSuspended) false else (isApproved || isTechnicianVerifiedInList(response.user, _liveTechnicians.value))
                    val rawStatus = (response.user.status ?: "").trim().lowercase()
                    val isRawVacation = response.user.isVacation || rawStatus == "vacation" || rawStatus == "on_leave" || rawStatus.contains("مرخصی")
                    val finalStatus = if (isSuspended) "suspended" else if (isRawVacation) "vacation" else if (rawStatus == "active") "active" else (if (isTech) (if (finalApproved) "approved" else "pending") else (response.user.status ?: "active"))
                    val mergedUser = response.user.copy(
                        subscription = sub,
                        is_premium = sub.is_premium,
                        has_active_subscription = sub.is_premium,
                        city = if (!response.user.resolvedCity.isNullOrBlank()) response.user.resolvedCity else (cached?.resolvedCity ?: persistentCity),
                        role = if (!response.user.role.isNullOrBlank()) response.user.role else (cached?.role ?: "customer"),
                        categories = if (!response.user.categories.isNullOrEmpty()) response.user.categories else cached?.categories,
                        is_approved = if (isTech) finalApproved else true,
                        approval_status = if (isSuspended) "suspended" else (if (isTech) (if (finalApproved) "approved" else "pending") else "approved"),
                        is_verified = if (isSuspended) false else (if (isTech) finalApproved else (response.user.is_verified ?: true)),
                        isVerified = if (isSuspended) false else (if (isTech) finalApproved else (response.user.isVerified ?: true)),
                        status = finalStatus
                    )
                    _currentUser.value = mergedUser
                    saveUserToCache(mergedUser)
                    syncTechnicianStatusFromServer(mergedUser, _liveTechnicians.value)
                    loadFreeStatus()
                    loadRepairs()
                    loadPartPurchases()
                } else if (response.status == "error") {
                    val cached = getCachedUser()
                    if (cached != null) {
                        _currentUser.value = cached
                    } else {
                        logout()
                    }
                } else {
                    logout()
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error fetching user", e)
                val cached = getCachedUser()
                if (cached != null) {
                    _currentUser.value = cached
                }
            }
        }
    }

    fun login(phone: String, pass: String, preferredRole: String? = null, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        val cleanPass = normalizeDigitsAndTrim(pass)
        saveAuthCredentials(cleanPhone, cleanPass)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                var response = repository.login(cleanPhone, cleanPass)
                if (response.status != "ok" && response.status != "success") {
                    val altPhone = if (cleanPhone.startsWith("0")) cleanPhone.removePrefix("0") else "0$cleanPhone"
                    val response2 = repository.login(altPhone, cleanPass)
                    if (response2.status == "ok" || response2.status == "success") {
                        response = response2
                    }
                }

                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val cached = getCachedUser()
                    val persistentCity = if (!response.user.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${response.user.phone}", null) else null
                    val sub = extractSubscription(response.user, response.subscription)
                    val rawRole = (response.user.role ?: "").trim().lowercase()
                    val effectiveRole = when {
                        !preferredRole.isNullOrBlank() -> preferredRole.trim().lowercase()
                        rawRole in listOf("technician", "tech", "repairman") -> "technician"
                        rawRole.isNotBlank() -> rawRole
                        cached?.role?.isNotBlank() == true -> cached.role
                        else -> "customer"
                    }
                    val isTech = effectiveRole == "technician" || effectiveRole == "tech" || effectiveRole == "repairman"
                    val isSuspended = response.user.isSuspended
                    val isApproved = if (isTech) (!isSuspended && response.user.isApprovedUser) else true
                    val finalApproved = if (isSuspended) false else (isApproved || isTechnicianVerifiedInList(response.user, _liveTechnicians.value))
                    val finalRole = if (isTech) "technician" else "customer"
                    val userCats = if (!response.user.categories.isNullOrEmpty()) response.user.categories else response.user.specialty
                    val finalCategories = if (!userCats.isNullOrEmpty()) userCats else cached?.categories

                    val rawStatus = (response.user.status ?: "").trim().lowercase()
                    val isRawVacation = response.user.isVacation || rawStatus == "vacation" || rawStatus == "on_leave" || rawStatus.contains("مرخصی")
                    val finalStatus = if (isSuspended) "suspended" else if (isRawVacation) "vacation" else if (rawStatus == "active") "active" else (if (isTech) (if (finalApproved) "approved" else "pending") else (response.user.status ?: "active"))

                    val mergedUser = response.user.copy(
                        subscription = sub,
                        is_premium = sub.is_premium,
                        has_active_subscription = sub.is_premium,
                        phone = if (!response.user.phone.isNullOrBlank()) response.user.phone else cleanPhone,
                        city = if (!response.user.resolvedCity.isNullOrBlank()) response.user.resolvedCity else (cached?.resolvedCity ?: persistentCity),
                        role = finalRole,
                        categories = finalCategories,
                        is_approved = if (isTech) finalApproved else true,
                        approval_status = if (isSuspended) "suspended" else (if (isTech) (if (finalApproved) "approved" else "pending") else "approved"),
                        is_verified = if (isSuspended) false else (if (isTech) finalApproved else (response.user.is_verified ?: true)),
                        isVerified = if (isSuspended) false else (if (isTech) finalApproved else (response.user.isVerified ?: true)),
                        status = finalStatus
                    )
                    val tokenToSave = response.token ?: response.session_token ?: response.user.id
                    _currentUser.value = mergedUser
                    saveUserToCache(mergedUser)
                    syncTechnicianStatusFromServer(mergedUser, _liveTechnicians.value)
                    saveSessionToken(tokenToSave)
                    sharedPrefs.edit()
                        .putString("local_user_pass_${cleanPhone}", hashPassword(cleanPass))
                        .putString("local_user_pass_${cleanPhone.removePrefix("0")}", hashPassword(cleanPass))
                        .putString("local_user_role_${cleanPhone}", finalRole)
                        .putString("local_user_role_${cleanPhone.removePrefix("0")}", finalRole)
                        .apply()
                    // Instantly sync all live data from database (subscription, orders, parts, technicians, etc.)
                    syncAllLiveAppData(isInitial = false)
                    onResult(true, null)
                } else {
                    // Check local credentials fallback
                    val savedPass = sharedPrefs.getString("local_user_pass_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_pass_${cleanPhone.removePrefix("0")}", null)
                    if (savedPass != null && savedPass == hashPassword(cleanPass)) {
                        val savedName = sharedPrefs.getString("local_user_name_${cleanPhone}", null)
                            ?: sharedPrefs.getString("local_user_name_${cleanPhone.removePrefix("0")}", "کاربر کدیار")
                        val savedRole = sharedPrefs.getString("local_user_role_${cleanPhone}", null)
                            ?: sharedPrefs.getString("local_user_role_${cleanPhone.removePrefix("0")}", "customer")
                        val effectiveRole = if (!preferredRole.isNullOrBlank()) preferredRole else (savedRole ?: "customer")
                        val savedCity = sharedPrefs.getString("local_user_city_${cleanPhone}", null)
                            ?: sharedPrefs.getString("local_user_city_${cleanPhone.removePrefix("0")}", "تهران")
                        val savedCatsStr = sharedPrefs.getString("local_user_cats_${cleanPhone}", null)
                            ?: sharedPrefs.getString("local_user_cats_${cleanPhone.removePrefix("0")}", null)
                        val savedCats = if (!savedCatsStr.isNullOrEmpty()) savedCatsStr.split(",") else null

                        val isTechUser = effectiveRole == "technician"
                        val localUser = KodyarUser(
                            id = "user_${cleanPhone}",
                            full_name = savedName ?: "کاربر کدیار",
                            phone = cleanPhone,
                            role = effectiveRole,
                            city = savedCity,
                            categories = savedCats,
                            is_approved = if (isTechUser) false else true,
                            approval_status = if (isTechUser) "pending" else "approved",
                            is_verified = if (isTechUser) false else true,
                            isVerified = if (isTechUser) false else true
                        )
                        _currentUser.value = localUser
                        saveUserToCache(localUser)
                        saveSessionToken(localUser.id)
                        syncAllLiveAppData(isInitial = false)
                        onResult(true, null)
                    } else {
                        _authError.value = response.error ?: "نام کاربری یا رمز عبور اشتباه است"
                        onResult(false, _authError.value)
                    }
                }
            } catch (e: Exception) {
                // Check local credentials fallback on network error
                val savedPass = sharedPrefs.getString("local_user_pass_${cleanPhone}", null)
                    ?: sharedPrefs.getString("local_user_pass_${cleanPhone.removePrefix("0")}", null)
                if (savedPass != null && savedPass == hashPassword(cleanPass)) {
                    val savedName = sharedPrefs.getString("local_user_name_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_name_${cleanPhone.removePrefix("0")}", "کاربر کدیار")
                    val savedRole = sharedPrefs.getString("local_user_role_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_role_${cleanPhone.removePrefix("0")}", "customer")
                    val effectiveRole = if (!preferredRole.isNullOrBlank()) preferredRole else (savedRole ?: "customer")
                    val savedCity = sharedPrefs.getString("local_user_city_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_city_${cleanPhone.removePrefix("0")}", "تهران")
                    val savedCatsStr = sharedPrefs.getString("local_user_cats_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_cats_${cleanPhone.removePrefix("0")}", null)
                    val savedCats = if (!savedCatsStr.isNullOrEmpty()) savedCatsStr.split(",") else null

                    val isTechUser = effectiveRole == "technician"
                    val localUser = KodyarUser(
                        id = "user_${cleanPhone}",
                        full_name = savedName ?: "کاربر کدیار",
                        phone = cleanPhone,
                        role = effectiveRole,
                        city = savedCity,
                        categories = savedCats,
                        is_approved = if (isTechUser) false else true,
                        approval_status = if (isTechUser) "pending" else "approved",
                        is_verified = if (isTechUser) false else true,
                        isVerified = if (isTechUser) false else true
                    )
                    _currentUser.value = localUser
                    saveUserToCache(localUser)
                    saveSessionToken(localUser.id)
                    loadFreeStatus()
                    loadRepairs()
                    onResult(true, null)
                } else {
                    _authError.value = "خطای اتصال به سرور: ${e.message}"
                    onResult(false, _authError.value)
                }
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun sendSms(phone: String, type: String, verificationCode: String, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        viewModelScope.launch {
            _isAuthLoading.value = true
            try {
                val res = repository.sendSms(cleanPhone, type, verificationCode)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    onResult(true, res.message ?: "پیامک با موفقیت ارسال شد")
                } else {
                    onResult(false, res.error ?: res.message ?: "خطا در ارسال پیامک")
                }
            } catch (e: Exception) {
                onResult(false, "خطا در ارسال پیامک: ${e.localizedMessage}")
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun forgotPasswordRequest(phone: String, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val res = repository.forgotPasswordRequest(cleanPhone)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    onResult(true, res.message ?: "کد بازیابی رمز عبور پیامک شد")
                } else {
                    onResult(false, res.error ?: res.message ?: "شماره تلفن یافت نشد یا ثبت نشده است")
                }
            } catch (e: Exception) {
                onResult(false, "خطا در برقراری ارتباط: ${e.localizedMessage}")
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun forgotPasswordReset(phone: String, code: String, newPassword: String? = null, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        val cleanCode = normalizeDigitsAndTrim(code)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val res = repository.forgotPasswordReset(cleanPhone, cleanCode, newPassword)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    onResult(true, res.message ?: "رمز عبور جدید با موفقیت ثبت شد")
                } else {
                    onResult(false, res.error ?: res.message ?: "کد تایید یا رمز عبور نامعتبر است")
                }
            } catch (e: Exception) {
                onResult(false, "خطا در ثبت رمز جدید: ${e.localizedMessage}")
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun sendOtp(phone: String, onResult: (Boolean, String?, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val res = repository.sendOtp(cleanPhone)
                if (res.status == "ok" || res.status == "success" || res.message?.contains("ارسال") == true) {
                    val directOtp = res.otp?.takeIf { it.isNotBlank() }
                        ?: com.example.data.utils.SmsOtpHelper.extractOtp(res.message ?: "")
                    onResult(true, res.message ?: "کد تایید با موفقیت پیامک شد", directOtp)
                } else {
                    onResult(false, res.error ?: res.message ?: "خطا در ارسال کد پیامک", null)
                }
            } catch (e: Exception) {
                onResult(false, "خطا در برقراری ارتباط: ${e.localizedMessage}", null)
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun sendOtp(phone: String, onResult: (Boolean, String?) -> Unit) {
        sendOtp(phone) { success, msg, _ -> onResult(success, msg) }
    }

    fun verifyOtp(phone: String, code: String, newPassword: String? = null, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        val cleanCode = normalizeDigitsAndTrim(code)
        if (!newPassword.isNullOrBlank()) {
            saveAuthCredentials(cleanPhone, newPassword)
        }
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val res = repository.verifyOtp(cleanPhone, cleanCode, newPassword = newPassword)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    if (res.user != null || !res.token.isNullOrBlank() || !res.session_token.isNullOrBlank()) {
                        val isTech = res.user?.role == "technician" || res.user?.role == "tech" || res.user?.role == "repairman"
                        val isApproved = if (isTech) res.user?.isApprovedUser == true else true
                        val user = res.user?.copy(
                            phone = cleanPhone,
                            is_verified = true,
                            isVerified = true,
                            is_approved = if (isTech) isApproved else true,
                            approval_status = if (isTech) (if (isApproved) "approved" else "pending") else "approved"
                        ) ?: KodyarUser(
                            id = "user_${cleanPhone}",
                            full_name = "کاربر کدیار",
                            phone = cleanPhone,
                            role = "customer",
                            is_verified = true,
                            isVerified = true,
                            is_approved = true,
                            approval_status = "approved"
                        )
                        _currentUser.value = user
                        saveUserToCache(user)
                        val token = res.token ?: res.session_token ?: user.id
                        saveSessionToken(token)
                        syncAllLiveAppData(isInitial = false)
                    }
                    onResult(true, res.message ?: "کد تایید شد و تغییرات اعمال گردید.")
                } else {
                    val errMsg = res.error ?: res.message ?: "کد تایید وارد شده نادرست یا منقضی شده است"
                    _authError.value = errMsg
                    onResult(false, errMsg)
                }
            } catch (e: Exception) {
                onResult(false, "خطا در تایید کد: ${e.localizedMessage}")
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun register(
        phone: String,
        pass: String,
        name: String,
        role: String = "customer",
        city: String? = null,
        categories: List<String>? = null,
        district: String? = null,
        documents: List<String>? = null,
        documentImages: List<String>? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        val cleanPhone = normalizePhone(phone)
        val cleanPass = normalizeDigitsAndTrim(pass)
        saveAuthCredentials(cleanPhone, cleanPass)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val uploadedUrls = mutableListOf<String>()
                if (!documentImages.isNullOrEmpty()) {
                    for ((idx, imgData) in documentImages.withIndex()) {
                        if (imgData.isNotBlank()) {
                            if (imgData.startsWith("http://") || imgData.startsWith("https://")) {
                                uploadedUrls.add(imgData)
                            } else {
                                try {
                                    val uploadRes = repository.uploadFile("doc_${cleanPhone}_${idx}.jpg", imgData)
                                    val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
                                    val finalUrl = uploadRes.url ?: (if (uploadRes.id != null) "$baseUrl/uploads/${uploadRes.id}" else null)
                                    if (finalUrl != null) {
                                        uploadedUrls.add(finalUrl)
                                    }
                                } catch (e: Exception) {
                                    Log.e("AssistantViewModel", "Failed to upload document file: ${e.message}")
                                }
                            }
                        }
                    }
                }
                val finalDocImages = if (uploadedUrls.isNotEmpty()) uploadedUrls else documentImages
                val response = repository.register(
                    phone = cleanPhone,
                    pass = cleanPass,
                    name = name,
                    role = role,
                    city = city,
                    district = district,
                    categories = categories,
                    documents = documents,
                    documentImages = finalDocImages
                )
                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val sub = extractSubscription(response.user, response.subscription)
                    val userWithSub = response.user.copy(subscription = sub)
                    val finalUser = if (role == "technician") {
                        userWithSub.copy(
                            role = "technician",
                            city = city,
                            categories = categories,
                            district = district,
                            is_approved = false,
                            approval_status = "pending",
                            uploaded_documents = documents ?: emptyList()
                        )
                    } else {
                        userWithSub.copy(role = "customer", city = city)
                    }
                    val tokenToSave = response.token ?: response.session_token ?: response.user.id
                    _currentUser.value = finalUser
                    saveUserToCache(finalUser)
                    saveSessionToken(tokenToSave)
                    sharedPrefs.edit()
                        .putString("local_user_pass_${cleanPhone}", hashPassword(cleanPass))
                        .putString("local_user_pass_${cleanPhone.removePrefix("0")}", hashPassword(cleanPass))
                        .putString("local_user_name_${cleanPhone}", name)
                        .putString("local_user_name_${cleanPhone.removePrefix("0")}", name)
                        .putString("local_user_role_${cleanPhone}", role)
                        .putString("local_user_role_${cleanPhone.removePrefix("0")}", role)
                        .putString("local_user_city_${cleanPhone}", city ?: "")
                        .putString("local_user_city_${cleanPhone.removePrefix("0")}", city ?: "")
                        .putString("local_user_cats_${cleanPhone}", categories?.joinToString(",") ?: "")
                        .putString("local_user_cats_${cleanPhone.removePrefix("0")}", categories?.joinToString(",") ?: "")
                        .apply()
                    
                    if (role == "technician") {
                        val techAvatar = documentImages?.firstOrNull { it.isNotBlank() }
                            ?: documents?.firstOrNull { it.isNotBlank() }
                            ?: finalUser.resolvedAvatarUrl
                        val newTech = KodyarTechnician(
                            id = finalUser.id,
                            name = finalUser.full_name,
                            city = city ?: "",
                            isVerified = false,
                            completedOrders = 0,
                            bio = "تکنسین متخصص لوازم خانگی کدیار۲۴",
                            categories = categories ?: emptyList<String>(),
                            rating = 5.0,
                            satisfactionRate = 100,
                            image = techAvatar
                        )
                        _liveTechnicians.value = _liveTechnicians.value + newTech
                    }
                    
                    syncAllLiveAppData(isInitial = false)
                    onResult(true, null)
                } else {
                    val errMsg = response.error ?: response.message ?: "خطا در ثبت‌نام. شماره تلفن ممکن است قبلاً ثبت شده باشد."
                    _authError.value = errMsg
                    onResult(false, errMsg)
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "خطا در اتصال به سرور"
                _authError.value = errMsg
                onResult(false, errMsg)
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun checkTechnicianApprovalStatus(onResult: (Boolean, String) -> Unit) {
        val token = getSessionToken()
        val user = _currentUser.value
        viewModelScope.launch {
            try {
                // Fetch both user profile and live server technicians list
                val techListDeferred = async {
                    try { repository.getTechniciansDirectly() } catch (_: Exception) { emptyList() }
                }
                val meDeferred = async {
                    if (!token.isNullOrBlank()) {
                        try { repository.getMe(token) } catch (_: Exception) { null }
                    } else null
                }

                val directTechs = techListDeferred.await()
                if (directTechs.isNotEmpty()) {
                    val currentList = _liveTechnicians.value.toMutableList()
                    for (t in directTechs) {
                        val idx = currentList.indexOfFirst { it.id == t.id || (!it.name.isNullOrBlank() && it.name == t.name) }
                        if (idx >= 0) currentList[idx] = t else currentList.add(t)
                    }
                    _liveTechnicians.value = currentList
                }

                val response = meDeferred.await()
                val targetUser = response?.user ?: user
                val isSuspended = targetUser?.isSuspended == true
                val isApprovedOnServer = targetUser?.isApprovedUser == true
                val isApprovedInTechList = !isSuspended && isTechnicianVerifiedInList(targetUser, _liveTechnicians.value)
                val finalApproved = !isSuspended && (isApprovedOnServer || isApprovedInTechList)

                if (targetUser != null) {
                    val sub = if (response != null) extractSubscription(targetUser, response.subscription) else targetUser.subscription
                    val cached = getCachedUser()
                    val persistentCity = if (!targetUser.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${targetUser.phone}", null) else null

                    val updatedUser = targetUser.copy(
                        subscription = sub,
                        city = if (!targetUser.city.isNullOrBlank()) targetUser.city else (cached?.city ?: persistentCity),
                        role = if (!targetUser.role.isNullOrBlank()) targetUser.role else (cached?.role ?: "technician"),
                        categories = if (!targetUser.categories.isNullOrEmpty()) targetUser.categories else cached?.categories,
                        is_approved = finalApproved,
                        approval_status = if (isSuspended) "suspended" else (if (finalApproved) "approved" else "pending"),
                        is_verified = finalApproved,
                        isVerified = finalApproved,
                        status = if (isSuspended) "suspended" else if (targetUser.isVacation || targetUser.status == "vacation") "vacation" else (if (finalApproved) "approved" else (targetUser.status ?: "pending"))
                    )
                    _currentUser.value = updatedUser
                    saveUserToCache(updatedUser)
                }

                if (isSuspended) {
                    onResult(false, "⛔ حساب کاربری شما توسط مدیریت سایت کدیار تعلیق گردیده است. جهت بررسی با پشتیبانی تماس بگیرید.")
                } else if (finalApproved) {
                    onResult(true, "✅ تبریک! حساب شما توسط مدیریت سایت کدیار تایید شده است.")
                } else {
                    onResult(false, "⏳ مدارک و حساب شما هنوز توسط مدیریت سایت کدیار تایید نشده است. لطفاً منتظر بمانید.")
                }
            } catch (e: Exception) {
                val curr = _currentUser.value
                val isSusp = curr?.isSuspended == true
                val approved = !isSusp && (curr?.isApprovedUser == true || isTechnicianVerifiedInList(curr, _liveTechnicians.value))
                val msg = if (isSusp) {
                    "⛔ حساب کاربری شما توسط مدیریت تعلیق گردیده است."
                } else if (approved) {
                    "✅ تبریک! حساب شما توسط مدیریت سایت کدیار تایید شده است."
                } else {
                    "⏳ وضعیت حساب شما در انتظار تایید مدیریت است."
                }
                onResult(approved, msg)
            }
        }
    }

    fun updateTechnicianApprovalStatus(isApproved: Boolean) {
        val user = _currentUser.value ?: return
        val updatedUser = user.copy(
            is_approved = isApproved,
            approval_status = if (isApproved) "approved" else "pending"
        )
        _currentUser.value = updatedUser
        saveUserToCache(updatedUser)
    }

    fun uploadTechnicianDocuments(docs: List<String>) {
        val user = _currentUser.value ?: return
        val currentDocs = (user.uploaded_documents ?: emptyList()) + docs
        val updatedUser = user.copy(
            uploaded_documents = currentDocs.distinct()
        )
        _currentUser.value = updatedUser
        saveUserToCache(updatedUser)
    }

    fun logout() {
        val lastPhone = _currentUser.value?.phone
        _currentUser.value = null
        clearSessionToken()
        clearUserCache()
        sharedPrefs.edit()
            .remove("bazaar_premium_active")
            .remove("bazaar_premium_sku")
            .remove("local_order_status_overrides")
            .remove("pending_commission_orders")
            .apply()
        _pendingCommissionOrderIds.value = emptySet()
        _repairOrders.value = emptyList()
        _freeErrorCount.value = 0
        _freeProblemCount.value = 0
        if (!lastPhone.isNullOrBlank() && getSavedAuthPhone().isBlank()) {
            sharedPrefs.edit().putString("saved_auth_phone", lastPhone).apply()
        }
    }

    // --- Load Kodyar Database ---
    fun loadKodyarDatabase() {
        viewModelScope.launch {
            _isDatabaseLoading.value = false
            _isLiveDataSyncing.value = true
            _isSparePartsLoading.value = _liveSpareParts.value.isEmpty()
            try {
                val response = try {
                    repository.getKodyarDatabase(forceRefresh = true)
                } catch (e: Exception) {
                    Log.w("AssistantViewModel", "Failed to fetch live database from server", e)
                    null
                }
                if (response != null) {
                    checkForDatabaseUpdates(response)
                    checkAppVersion(response)
                    if (response.resolvedErrorCodes.isNotEmpty()) {
                        _liveErrorCodes.value = response.resolvedErrorCodes
                    }
                    if (response.resolvedSpareParts.isNotEmpty()) {
                        _liveSpareParts.value = response.resolvedSpareParts
                    }
                    
                    val incomingTechs = response.resolvedTechnicians
                    if (incomingTechs.isNotEmpty()) {
                        _liveTechnicians.value = incomingTechs
                    }

                    // Automatically synchronize current user approval status if they exist in verified technicians list
                    checkAndSyncCurrentUserApprovalWithTechList(_liveTechnicians.value)

                    if (response.resolvedCommonProblems.isNotEmpty()) {
                        _liveCommonProblems.value = response.resolvedCommonProblems
                    }

                    val dynamicCats = (response.resolvedCategoriesList + response.resolvedErrorCodes.mapNotNull { it.resolvedCategory }).filter { it.isNotBlank() }.distinct()
                    if (dynamicCats.isNotEmpty()) {
                        _liveCategories.value = listOf("همه") + dynamicCats
                    }

                    val dynamicBrands = (response.resolvedBrandsList + response.resolvedErrorCodes.mapNotNull { it.brand }).filter { it.isNotBlank() }.distinct()
                    if (dynamicBrands.isNotEmpty()) {
                        _liveBrands.value = listOf("همه") + dynamicBrands
                    }
                    val parsedCities = response.resolvedCitiesList.flatMap { 
                        listOfNotNull(it.name, it.title, it.city, it.cityName, it.name_fa, it.nameFarsi, it.slug)
                    }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    if (parsedCities.isNotEmpty()) {
                        _liveCities.value = listOf("همه") + parsedCities
                        _liveCitiesStructured.value = response.resolvedCitiesList
                    }

                    // Trigger initial search results
                    updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)

                    hasLoadedFromNetwork = true

                    // Save to Room Database as Single Source of Truth
                    try {
                        if (response.resolvedErrorCodes.isNotEmpty() || response.resolvedSpareParts.isNotEmpty() || response.resolvedCommonProblems.isNotEmpty() || response.resolvedTechnicians.isNotEmpty()) {
                            repository.saveDatabaseToRoom(
                                errorCodes = response.resolvedErrorCodes,
                                spareParts = response.resolvedSpareParts,
                                commonProblems = response.resolvedCommonProblems,
                                technicians = response.resolvedTechnicians.ifEmpty { _liveTechnicians.value }
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("AssistantViewModel", "Failed to save data to Room DB", e)
                    }

                    // Save complete database response to local cache (never save empty)
                    if (response.resolvedErrorCodes.isNotEmpty() || response.resolvedSpareParts.isNotEmpty()) {
                        try {
                            val json = databaseAdapter.toJson(response)
                            sharedPrefs.edit().putString("cached_kodyar_database", json).apply()
                        } catch (e: Exception) {
                            Log.e("AssistantViewModel", "Failed to cache database response.", e)
                        }
                    }
                }

                // Dedicated persistent cache for technicians
                if (_liveTechnicians.value.isNotEmpty()) {
                    try {
                        val techJson = techniciansAdapter.toJson(_liveTechnicians.value)
                        sharedPrefs.edit().putString("cached_technicians_json", techJson).apply()
                    } catch (e: Exception) {
                        Log.e("AssistantViewModel", "Failed to cache technicians.", e)
                    }
                }

                // Dedicated persistent cache for spare parts
                if (_liveSpareParts.value.isNotEmpty()) {
                    try {
                        val partsJson = sparePartsAdapter.toJson(_liveSpareParts.value)
                        sharedPrefs.edit().putString("cached_spare_parts_json", partsJson).apply()
                    } catch (e: Exception) {
                        Log.e("AssistantViewModel", "Failed to cache spare parts.", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error loading kodyar database from API.", e)
            } finally {
                ensureDefaultFilters()
                _isDatabaseLoading.value = false
                _isSparePartsLoading.value = false
                _isLiveDataSyncing.value = false
            }
        }
    }

    fun refreshTechnicians() {
        viewModelScope.launch {
            _isTechniciansLoading.value = _liveTechnicians.value.isEmpty()
            try {
                val techRes = repository.getTechniciansDirectly()
                if (techRes.isNotEmpty()) {
                    _liveTechnicians.value = techRes

                    // Cache to SharedPreferences
                    try {
                        val techJson = techniciansAdapter.toJson(techRes)
                        sharedPrefs.edit().putString("cached_technicians_json", techJson).apply()
                    } catch (_: Exception) {}

                    // Persist to Room
                    try {
                        repository.saveTechniciansToRoom(techRes)
                    } catch (_: Exception) {}

                    // Synchronize current technician approval and vacation status with latest server data
                    checkAndSyncCurrentUserApprovalWithTechList(techRes)
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to refresh technicians: ${e.message}")
            } finally {
                _isTechniciansLoading.value = false
            }
        }
    }

    private fun matchNormalized(target: String?, filter: String?): Boolean {
        val normTarget = normalizePersian(target)
        val normFilter = normalizePersian(filter)
        if (normFilter == "همه" || normFilter.isEmpty()) return true
        return normTarget == normFilter || normTarget.contains(normFilter, ignoreCase = true) || normFilter.contains(normTarget, ignoreCase = true)
    }

    fun getAvailableModelsFor(brand: String, category: String): List<String> {
        val codes = _liveErrorCodes.value
        if (codes.isEmpty()) return listOf("همه")

        val isAllBrand = brand == "همه" || brand.isBlank()
        val isAllCategory = category == "همه" || category.isBlank()

        val filtered = if (isAllBrand && isAllCategory) {
            codes
        } else {
            codes.filter { error ->
                (isAllBrand || matchNormalized(error.brand, brand)) &&
                (isAllCategory || matchNormalized(error.category ?: error.resolvedCategory, category))
            }
        }

        val models = filtered.mapNotNull { error ->
            val modelName = error.model?.trim()
            if (!modelName.isNullOrEmpty()) {
                modelName
            } else {
                val title = error.title?.trim()
                val code = error.code?.trim()
                if (!title.isNullOrEmpty() && title != code) {
                    title
                } else {
                    null
                }
            }
        }.distinct().sorted()

        return listOf("همه") + models
    }

    fun canonicalModel(input: String?): String {
        if (input == null) return ""
        var text = input.lowercase()
        
        // 1. Convert Persian/Arabic digits to English digits
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        for (i in 0..9) {
            text = text.replace(persianDigits[i].toString(), i.toString())
            text = text.replace(arabicDigits[i].toString(), i.toString())
        }
        
        // 2. Map common Persian words for English letters
        val replacements = listOf(
            "دبلیو" to "w",
            "ایکس" to "x",
            "کیو" to "q",
            "وای" to "y",
            "اچ" to "h",
            "اس" to "s",
            "ام" to "m",
            "ال" to "l",
            "اف" to "f",
            "پی" to "p",
            "جی" to "g",
            "ار" to "r",
            "زد" to "z",
            "وی" to "v",
            "تی" to "t",
            "سی" to "c",
            "دی" to "d",
            "بی" to "b",
            "کی" to "k",
            "ای" to "e",
            "ان" to "n",
            "یو" to "u",
            "ب" to "b",
            "پ" to "p",
            "ت" to "t",
            "ج" to "j",
            "د" to "d",
            "ر" to "r",
            "س" to "s",
            "ف" to "f",
            "ک" to "k",
            "ل" to "l",
            "م" to "m",
            "ن" to "n",
            "و" to "v",
            "ه" to "h",
            "ی" to "y"
        )
        
        for ((persian, english) in replacements) {
            text = text.replace(persian, english)
        }
        
        // 3. Remove all non-alphanumeric characters
        return text.replace("[^a-zA-Z0-9]".toRegex(), "")
    }

    fun matchModelCanonical(error: KodyarErrorCode, modelQuery: String): Boolean {
        if (modelQuery.isEmpty() || modelQuery == "همه") return true
        val canonicalQuery = canonicalModel(modelQuery)
        if (canonicalQuery.isEmpty()) return true
        
        // Check error.model
        val modelCanonical = canonicalModel(error.model)
        if (modelCanonical.contains(canonicalQuery) || canonicalQuery.contains(modelCanonical)) {
            return true
        }
        
        // Check error.title
        val titleCanonical = canonicalModel(error.title)
        if (titleCanonical.contains(canonicalQuery) || canonicalQuery.contains(titleCanonical)) {
            return true
        }
        
        // Check error.code
        val codeCanonical = canonicalModel(error.code)
        if (codeCanonical.contains(canonicalQuery) || canonicalQuery.contains(codeCanonical)) {
            return true
        }

        // Check error.description
        val descCanonical = canonicalModel(error.description)
        if (descCanonical.contains(canonicalQuery) || canonicalQuery.contains(descCanonical)) {
            return true
        }
        
        return false
    }

    fun normalizeSearchToken(input: String?): String {
        if (input == null) return ""
        var text = normalizePersian(input).trim().lowercase()
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        for (i in 0..9) {
            text = text.replace(persianDigits[i].toString(), i.toString())
            text = text.replace(arabicDigits[i].toString(), i.toString())
        }
        return text
    }

    fun canonicalCode(rawCode: String?): String {
        if (rawCode == null) return ""
        var s = normalizeSearchToken(rawCode)
        if (s.isEmpty()) return ""

        val prefixes = listOf("کد خطای ", "کد خطا ", "کدخطای ", "کدخطا ", "کد ", "ارور ", "error ", "code ")
        for (p in prefixes) {
            if (s.startsWith(p)) {
                s = s.removePrefix(p).trim()
            }
        }

        val punctWords = listOf(
            "دو نقطه" to ":", "دونقطه" to ":", "خط تیره" to "-", "خط‌تیره" to "-",
            "فاصله" to " ", "اسلش" to "/", "ممیز" to "/", "نقطه" to "."
        )
        for ((pw, rep) in punctWords) {
            s = s.replace(pw, rep)
        }

        val compoundNums = listOf(
            "بیست و یک" to "21", "بیست و دو" to "22", "بیست و سه" to "23", "بیست و چهار" to "24", "بیست و پنج" to "25",
            "بیست و شش" to "26", "بیست و شیش" to "26", "بیست و هفت" to "27", "بیست و هشت" to "28", "بیست و نه" to "29", "بیست" to "20",
            "سی و یک" to "31", "سی و دو" to "32", "سی و سه" to "33", "سی و چهار" to "34", "سی و پنج" to "35",
            "پانزده" to "15", "پونزده" to "15", "شانزده" to "16", "شونزده" to "16",
            "سیزده" to "13", "چهارده" to "14", "هفده" to "17", "هجده" to "18", "نوزده" to "19",
            "یازده" to "11", "دوازده" to "12", "ده" to "10",
            "نه" to "9", "هشت" to "8", "هفت" to "7", "شش" to "6", "شیش" to "6", "پنج" to "5", "چهار" to "4", "سه" to "3", "دو" to "2", "یک" to "1", "صفر" to "0"
        )
        for ((cw, rep) in compoundNums) {
            s = s.replace(cw, rep)
        }

        val phonetics = listOf(
            "ای " to "e", "ای" to "e",
            "اف " to "f", "اف" to "f",
            "دی " to "d", "دی" to "d",
            "سی " to "c", "سی" to "c",
            "پی " to "p", "پی" to "p",
            "ال " to "l", "ال" to "l",
            "ار " to "r", "ار" to "r",
            "اچ " to "h", "اچ" to "h",
            "او " to "o", "او" to "o"
        )
        for ((ph, letter) in phonetics) {
            if (s.startsWith(ph) && s.length > ph.length && (s[ph.length].isDigit() || s[ph.length] == '-' || s[ph.length] == ':' || s[ph.length] == '.' || s[ph.length] == ' ')) {
                s = letter + s.substring(ph.length).trim()
                break
            }
        }

        s = s.replace("[^a-z0-9]".toRegex(), "")
        return s
    }

    private fun matchesErrorCodeQuery(error: KodyarErrorCode, query: String): Boolean {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return true

        val rawTokens = cleanQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (rawTokens.isEmpty()) return true

        val fillerWords = setOf("مدل", "کد", "خطای", "خطا", "ارور", "دستگاه", "برند", "نوع")
        val tokens = if (rawTokens.size > 1) {
            rawTokens.filter { it !in fillerWords }
        } else {
            rawTokens
        }.ifEmpty { rawTokens }

        val errorCanonicalCode = canonicalCode(error.resolvedCode)

        // Case 1: Pure single code search (e.g. user searched "e2", "E-02", "ای دو", "ارور e2")
        // Returns all errors with code E2 across all devices, brands, and models
        if (tokens.size == 1) {
            val singleToken = tokens[0]
            val tokenCode = canonicalCode(singleToken)
            if (tokenCode.isNotEmpty() && tokenCode.any { it.isDigit() }) {
                return errorCanonicalCode.isNotEmpty() && tokenCode == errorCanonicalCode
            }
        }

        // Check if there is an explicit error code token in the multi-token query (e.g., "e2" in "e2 پکیج بوتان مدل 1")
        val codeToken = tokens.firstOrNull { token ->
            val c = canonicalCode(token)
            c.isNotEmpty() && c.length <= 6 && c.any { it.isDigit() } && (c.any { it.isLetter() } || c.length >= 2)
        }?.let { canonicalCode(it) }

        // If the query contains an explicit error code token, the error's code MUST match it!
        if (codeToken != null && codeToken.isNotEmpty()) {
            if (errorCanonicalCode.isEmpty() || errorCanonicalCode != codeToken) {
                return false
            }
        }

        val normBrand = normalizeSearchToken(error.brand)
        val normCat = normalizeSearchToken(error.resolvedCategory)
        val normModel = normalizeSearchToken(error.model)
        val canonicalModelVal = canonicalModel(error.model)
        val normTitle = normalizeSearchToken(error.resolvedTitle)
        val normDesc = normalizeSearchToken(error.description)
        val normCauses = normalizeSearchToken(error.causes?.toString())

        // Check if query mentions any known brand in the live database
        val allBrands = _liveBrands.value.filter { it != "همه" && it.isNotBlank() }
        val matchedBrandsInQuery = allBrands.filter { b ->
            val bNorm = normalizeSearchToken(b)
            bNorm.isNotEmpty() && tokens.any { t ->
                val tNorm = normalizeSearchToken(t)
                tNorm == bNorm || (tNorm.length >= 3 && bNorm.contains(tNorm)) || (bNorm.length >= 3 && tNorm.contains(bNorm))
            }
        }

        // If query specifies a brand (like "الزان", "بوتان"), error MUST belong to that brand
        if (matchedBrandsInQuery.isNotEmpty()) {
            val isBrandMatchStrict = matchedBrandsInQuery.any { b ->
                val bNorm = normalizeSearchToken(b)
                normBrand == bNorm || normBrand.contains(bNorm) || bNorm.contains(normBrand)
            }
            if (!isBrandMatchStrict) {
                return false
            }
        }

        // Check if query mentions any known category / device type in the live database
        val allCats = _liveCategories.value.filter { it != "همه" && it.isNotBlank() }
        val matchedCatsInQuery = allCats.filter { c ->
            val cNorm = normalizeSearchToken(c)
            cNorm.isNotEmpty() && tokens.any { t ->
                val tNorm = normalizeSearchToken(t)
                tNorm == cNorm || (tNorm.length >= 3 && cNorm.contains(tNorm)) || (cNorm.length >= 3 && tNorm.contains(cNorm))
            }
        }

        // If query specifies a device type (e.g. "پکیج", "لباسشویی"), error MUST belong to that device
        if (matchedCatsInQuery.isNotEmpty()) {
            val isCatMatchStrict = matchedCatsInQuery.any { c ->
                val cNorm = normalizeSearchToken(c)
                normCat == cNorm || normCat.contains(cNorm) || cNorm.contains(normCat)
            }
            if (!isCatMatchStrict) {
                return false
            }
        }

        val titleWords = normTitle.split(Regex("[^a-z0-9آ-ی]+")).filter { it.isNotEmpty() }
        val descWords = normDesc.split(Regex("[^a-z0-9آ-ی]+")).filter { it.isNotEmpty() }

        return tokens.all { token ->
            val tokenNorm = normalizeSearchToken(token)
            val tokenCodeVal = canonicalCode(token)
            val tokenModelCanonical = canonicalModel(token)

            // 1. Error Code Match (Exact code equality, e.g. e2 only matches e2, e02 only matches e02)
            val isCodeMatch = tokenCodeVal.isNotEmpty() && errorCanonicalCode.isNotEmpty() && tokenCodeVal == errorCanonicalCode

            // If token is an explicit error code pattern (e.g. e2, e02, f1, 10), it MUST match the actual code
            if (tokenCodeVal.isNotEmpty() && tokenCodeVal.any { it.isDigit() } && tokenCodeVal.length <= 6) {
                return@all isCodeMatch
            }

            // 2. Brand Match
            val isBrandMatch = normBrand.isNotEmpty() && (normBrand.contains(tokenNorm) || tokenNorm.contains(normBrand))

            // 3. Category / Device Match
            val isCatMatch = normCat.isNotEmpty() && (normCat.contains(tokenNorm) || tokenNorm.contains(normCat))

            // 4. Model Match (matches model name like "پارما", "اپتیما", "کالدا", "1", "bn324", etc.)
            val isModelMatch = (normModel.isNotEmpty() && (normModel.contains(tokenNorm) || tokenNorm.contains(normModel))) ||
                    (tokenModelCanonical.isNotEmpty() && canonicalModelVal.isNotEmpty() && (canonicalModelVal.contains(tokenModelCanonical) || tokenModelCanonical.contains(canonicalModelVal))) ||
                    (normTitle.contains(tokenNorm))

            // 5. Title Match
            val isTitleMatch = normTitle.contains(tokenNorm)

            // 6. Description / Causes Match (only for general keywords with length >= 3)
            val isDescMatch = tokenNorm.length >= 3 && (normDesc.contains(tokenNorm) || normCauses.contains(tokenNorm))

            isCodeMatch || isBrandMatch || isCatMatch || isModelMatch || isTitleMatch || isDescMatch
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun updateSearchFilters(query: String, brand: String, category: String, model: String = _modelQuery.value) {
        _searchQuery.value = query
        _selectedBrand.value = brand
        _selectedCategory.value = category
        _modelQuery.value = model

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val allLive = _liveErrorCodes.value
            val showOnlySavedVal = _showOnlySaved.value
            val savedList = savedErrors.value
            val queryCanonicalCode = if (query.split(Regex("\\s+")).filter { it.isNotEmpty() }.size == 1) canonicalCode(query) else ""

            val filtered = allLive.filter { error ->
                val isSavedMatch = if (showOnlySavedVal) {
                    savedList.any {
                        it.code == error.code && it.brand == error.brand && it.category == error.category
                    }
                } else {
                    true
                }

                if (!isSavedMatch) return@filter false

                val matchBrand = matchNormalized(error.brand, brand)
                if (!matchBrand) return@filter false

                val matchCategory = matchNormalized(error.category ?: error.resolvedCategory, category)
                if (!matchCategory) return@filter false

                val matchModel = model.isEmpty() || model == "همه" || matchModelCanonical(error, model)
                if (!matchModel) return@filter false

                matchesErrorCodeQuery(error, query)
            }

            val result = if (queryCanonicalCode.isNotEmpty()) {
                filtered.sortedByDescending { canonicalCode(it.resolvedCode) == queryCanonicalCode }
            } else {
                filtered
            }

            withContext(Dispatchers.Main) {
                _searchResults.value = result
            }
        }
    }

    private fun checkForDatabaseUpdates(response: KodyarDatabaseResponse) {
        val oldErrorIds = sharedPrefs.getStringSet("known_error_ids", emptySet()) ?: emptySet()
        val oldProblemIds = sharedPrefs.getStringSet("known_problem_ids", emptySet()) ?: emptySet()
        val oldPartIds = sharedPrefs.getStringSet("known_part_ids", emptySet()) ?: emptySet()

        val newErrorIds = response.errorCodes?.mapNotNull { it.id }.orEmpty().toSet()
        val newProblemIds = response.commonProblems?.mapNotNull { it.id }.orEmpty().toSet()
        val newPartIds = response.spareParts?.mapNotNull { it.id }.orEmpty().toSet()

        if (oldErrorIds.isEmpty() && oldProblemIds.isEmpty() && oldPartIds.isEmpty()) {
            // First run, silently save the list
            sharedPrefs.edit()
                .putStringSet("known_error_ids", newErrorIds)
                .putStringSet("known_problem_ids", newProblemIds)
                .putStringSet("known_part_ids", newPartIds)
                .apply()
        } else {
            // Check for added/new items
            val addedErrors = response.errorCodes?.filter { it.id != null && !oldErrorIds.contains(it.id) }.orEmpty()
            val addedProblems = response.commonProblems?.filter { it.id != null && !oldProblemIds.contains(it.id) }.orEmpty()
            val addedParts = response.spareParts?.filter { it.id != null && !oldPartIds.contains(it.id) }.orEmpty()

            if (addedErrors.isNotEmpty() || addedProblems.isNotEmpty() || addedParts.isNotEmpty()) {
                _appUpdateNotification.value = AppUpdateNotification(
                    newErrors = addedErrors,
                    newProblems = addedProblems,
                    newParts = addedParts
                )

                // Persist updated sets
                val updatedErrorIds = oldErrorIds.toMutableSet().apply { addAll(newErrorIds) }
                val updatedProblemIds = oldProblemIds.toMutableSet().apply { addAll(newProblemIds) }
                val updatedPartIds = oldPartIds.toMutableSet().apply { addAll(newPartIds) }

                sharedPrefs.edit()
                    .putStringSet("known_error_ids", updatedErrorIds)
                    .putStringSet("known_problem_ids", updatedProblemIds)
                    .putStringSet("known_part_ids", updatedPartIds)
                    .apply()
            }
        }
    }



    // --- Shorthand Helper to parse Causes / Steps ---
    fun Any?.toListOfStrings(): List<String> {
        if (this == null) return emptyList()
        if (this is List<*>) {
            return this.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        }
        val str = this.toString().trim()
        if (str.isEmpty()) return emptyList()
        return str.split(Regex("[\r\n؛•\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    // --- Bookmarked / Saved Errors ---

    fun toggleSavedError(code: String, brand: String, category: String, isCurrentlySaved: Boolean) {
        viewModelScope.launch {
            repository.toggleSavedError(code, brand, category, isCurrentlySaved)
        }
    }

    fun isErrorSaved(code: String, brand: String, category: String): Flow<Boolean> {
        return repository.isErrorSaved(code, brand, category)
    }

    // --- Custom / Notes Errors ---
    val customErrors: StateFlow<List<CustomErrorEntity>> = repository.allCustomErrors
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addCustomError(code: String, brand: String, category: String, description: String, solution: String, severity: String, userNote: String) {
        viewModelScope.launch {
            val custom = CustomErrorEntity(
                code = code,
                brand = brand,
                category = category,
                description = description,
                solution = solution,
                severity = severity,
                userNote = userNote
            )
            repository.addCustomError(custom)
        }
    }

    fun deleteCustomError(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomError(id)
        }
    }

    fun updateCustomErrorNote(id: Long, note: String) {
        viewModelScope.launch {
            repository.updateCustomErrorNote(id, note)
        }
    }

    // --- Cart functions ---
    private fun persistCart() {
        try {
            val cartList = _cart.value
            val qtyMap = _cartQty.value
            val cartJson = moshi.adapter<List<String>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
            ).toJson(cartList)
            val qtyJson = moshi.adapter<Map<String, Int>>(
                com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Int::class.javaObjectType)
            ).toJson(qtyMap)

            sharedPrefs.edit()
                .putString("persisted_cart_items", cartJson)
                .putString("persisted_cart_quantities", qtyJson)
                .apply()
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Error persisting cart", e)
        }
    }

    private fun loadPersistedCart() {
        try {
            val cartJson = sharedPrefs.getString("persisted_cart_items", null)
            val qtyJson = sharedPrefs.getString("persisted_cart_quantities", null)
            if (!cartJson.isNullOrEmpty()) {
                val list = moshi.adapter<List<String>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
                ).fromJson(cartJson)
                if (list != null) {
                    _cart.value = list
                }
            }
            if (!qtyJson.isNullOrEmpty()) {
                val map = moshi.adapter<Map<String, Int>>(
                    com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Int::class.javaObjectType)
                ).fromJson(qtyJson)
                if (map != null) {
                    _cartQty.value = map
                }
            }
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Error loading persisted cart", e)
        }
    }

    fun addToCart(partId: String) {
        val part = _liveSpareParts.value.find { it.id == partId }
        val maxStock = part?.resolvedStock ?: 0
        if (maxStock <= 0) return

        val currentCart = _cart.value.toMutableList()
        if (!currentCart.contains(partId)) {
            currentCart.add(partId)
            _cart.value = currentCart
        }
        val currentQty = _cartQty.value.toMutableMap()
        currentQty[partId] = currentQty[partId] ?: 1
        _cartQty.value = currentQty
        persistCart()
    }

    fun addToCartWithQty(partId: String, qty: Int) {
        val part = _liveSpareParts.value.find { it.id == partId }
        val maxStock = part?.resolvedStock ?: 0
        if (maxStock <= 0) return

        val currentCart = _cart.value.toMutableList()
        if (!currentCart.contains(partId)) {
            currentCart.add(partId)
            _cart.value = currentCart
        }
        val currentQty = _cartQty.value.toMutableMap()
        val existing = currentQty[partId] ?: 0
        currentQty[partId] = minOf(maxStock, (existing + qty).coerceAtLeast(1))
        _cartQty.value = currentQty
        persistCart()
    }

    fun removeFromCart(partId: String) {
        val currentCart = _cart.value.toMutableList()
        currentCart.remove(partId)
        _cart.value = currentCart

        val currentQty = _cartQty.value.toMutableMap()
        currentQty.remove(partId)
        _cartQty.value = currentQty
        persistCart()
    }

    fun updateCartQty(partId: String, qty: Int) {
        val part = _liveSpareParts.value.find { it.id == partId }
        val maxStock = part?.resolvedStock ?: 0
        val currentQty = _cartQty.value.toMutableMap()
        if (qty > 0 && maxStock > 0) {
            currentQty[partId] = minOf(maxStock, qty)
            _cartQty.value = currentQty
            persistCart()
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _cartQty.value = emptyMap()
        _purchaseSuccess.value = false
        persistCart()
    }

    /**
     * Submits every item currently in the cart to the server's dedicated
     * `api/store/purchase-part` endpoint, one request per line item, so each purchase
     * lands in the customer's website profile AND the admin panel's purchase list for
     * manual approval — exactly like the website's card-to-card checkout flow.
     *
     * Replaces the previous implementation, which only saved the order in local
     * SharedPreferences (invisible to the server/admin) and then redirected the user to a
     * non-existent WooCommerce cart URL, making part purchases from the app a dead end.
     *
     * @param cardHolder Full name of the person who deposited the money (as printed on the bank card).
     * @param trackNumber The bank transfer tracking/reference number from the deposit receipt.
     * @param address Delivery address for the purchased part(s).
     * @param onResult Callback invoked with `(success, errorMessage)` once all items are processed.
     */
    fun submitPartPurchaseOrdersToServer(
        cardHolder: String = "",
        trackNumber: String = "",
        address: String = "",
        onResult: (Boolean, String?) -> Unit
    ) {
        val token = getSessionToken()
        if (token == null) {
            onResult(false, "باید وارد حساب خود شوید")
            return
        }

        val finalCardHolder = if (cardHolder.isBlank()) "پرداخت آنلاین سایت" else cardHolder
        val finalTrackNumber = if (trackNumber.isBlank()) "همگام‌سازی وب‌سایت" else trackNumber
        val finalAddress = if (address.isBlank()) (_currentUser.value?.city ?: "آدرس ثبت‌شده در حساب") else address

        viewModelScope.launch {
            _isPurchaseLoading.value = true
            try {
                val user = _currentUser.value
                val cartList = _cart.value.toList()
                val qtyMap = _cartQty.value.toMap()
                val failedParts = mutableListOf<String>()

                for (partId in cartList) {
                    val part = _liveSpareParts.value.find { it.id == partId }
                    if (part == null || part.resolvedStock <= 0) {
                        failedParts.add(part?.name ?: "قطعه ناموجود")
                        continue
                    }
                    val qty = qtyMap[partId] ?: 1
                    val price = part.price ?: 0.0
                    val subtotal = price * qty

                    try {
                        // 1. Send store purchase request to backend API /api/store/order or /api/store/purchase
                        val paymentNotes = if (trackNumber.isNotBlank()) {
                            "واریز کارت به کارت - پیگیری: $finalTrackNumber - واریزکننده: $finalCardHolder"
                        } else {
                            "واریز کارت به کارت به حساب مدیر سایت کدیار۲۴"
                        }
                        repository.purchasePart(
                            token = token,
                            partId = partId,
                            partName = part.name ?: "قطعه یدکی",
                            quantity = qty,
                            unitPrice = price,
                            totalPrice = subtotal,
                            address = finalAddress,
                            city = user?.city,
                            notes = paymentNotes,
                            customerName = user?.full_name,
                            customerPhone = user?.phone
                        )

                        val order = PartPurchaseOrder(
                            id = "order_${System.currentTimeMillis()}_${partId}",
                            partId = partId,
                            partName = part.name ?: "قطعه یدکی",
                            quantity = qty,
                            unitPrice = price,
                            totalPrice = subtotal,
                            address = finalAddress,
                            notes = "واریز کارت به کارت - در انتظار تایید مدیر سایت",
                            dateStr = getCurrentPersianDate(),
                            status = "pending_payment"
                        )
                        savePartPurchase(order)
                    } catch (e: Exception) {
                        Log.e("AssistantViewModel", "Error submitting purchase for part $partId", e)
                        failedParts.add(part.name ?: partId)
                    }
                }

                if (failedParts.isEmpty()) {
                    _purchaseSuccess.value = true
                    clearCart()
                    loadKodyarDatabase()
                    loadRepairs()
                    onResult(true, null)
                } else {
                    onResult(false, "ثبت سفارش برای این قطعات ناموفق بود: ${failedParts.joinToString("، ")}")
                }
            } catch (e: Exception) {
                onResult(false, "خطا در ثبت سفارش: ${e.message}")
            } finally {
                _isPurchaseLoading.value = false
            }
        }
    }

    fun openPartPurchaseWebUrl(context: Context, partId: String, userPhone: String?, paymentUrl: String? = null) {
        val targetUrl = if (!paymentUrl.isNullOrBlank()) {
            paymentUrl.trim()
        } else {
            val cleanPartId = Uri.encode(partId.trim())
            val cleanPhone = Uri.encode((userPhone ?: "").trim())
            val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
            "$baseUrl/?action=buy_part&partId=$cleanPartId&phone=$cleanPhone"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در باز کردن مرورگر جهت خرید قطعه", Toast.LENGTH_SHORT).show()
        }
    }

    fun initiateDirectPartPurchase(
        context: Context,
        partId: String,
        partName: String,
        quantity: Int = 1,
        totalPrice: Double = 0.0,
        deliveryAddress: String? = null
    ) {
        val user = _currentUser.value
        if (user == null || user.phone.isNullOrBlank()) {
            Toast.makeText(context, "لطفاً ابتدا وارد حساب کاربری خود شوید.", Toast.LENGTH_LONG).show()
            return
        }
        val phone = user.phone ?: ""
        val token = getSessionToken() ?: ""
        val effectiveAddress = deliveryAddress?.takeIf { it.isNotBlank() } ?: user.address?.takeIf { it.isNotBlank() } ?: user.city ?: "ثبت شده از اپلیکیشن"

        viewModelScope.launch {
            try {
                val part = _liveSpareParts.value.find { it.id == partId }
                val unitPrice = part?.price ?: (if (quantity > 0) totalPrice / quantity else 0.0)
                val finalTotal = if (totalPrice > 0) totalPrice else (unitPrice * quantity)

                // Send store purchase request to backend API /api/store/order or /api/store/purchase
                val resp = repository.purchasePart(
                    token = token,
                    partId = partId,
                    partName = partName,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    totalPrice = finalTotal,
                    address = effectiveAddress,
                    city = user.city,
                    notes = "خرید مستقیم از اپلیکیشن کدیار۲۴",
                    customerName = user.full_name,
                    customerPhone = phone
                )

                val effectiveDate = resp.shamsi_date ?: resp.shamsiDate ?: getCurrentPersianDate()
                val paymentUrl = resp.payment_url ?: resp.paymentUrl
                val orderIdStr = resp.order_id?.toString() ?: resp.orderId?.toString() ?: "order_${System.currentTimeMillis()}_${partId}"

                // Save purchase order locally in Room DB
                val localOrder = PartPurchaseOrder(
                    id = orderIdStr,
                    partId = partId,
                    partName = partName,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    totalPrice = finalTotal,
                    address = effectiveAddress,
                    notes = "خرید مستقیم - ثبت شده در سایت",
                    dateStr = effectiveDate,
                    status = "pending",
                    paymentUrl = paymentUrl,
                    shamsiDate = resp.shamsi_date ?: resp.shamsiDate
                )
                savePartPurchase(localOrder)

                withContext(Dispatchers.Main) {
                    val msg = resp.message ?: "سفارش قطعه در دیتابیس ثبت شد. در حال انتقال به درگاه پرداخت..."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    openPartPurchaseWebUrl(context, partId, phone, paymentUrl)
                }

            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error submitting store purchase API: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "در حال انتقال به صفحه پرداخت...", Toast.LENGTH_SHORT).show()
                }
                openPartPurchaseWebUrl(context, partId, phone, null)
            }
        }
    }

    private fun isPartPurchase(order: com.example.data.model.KodyarRepairOrder): Boolean {
        val desc = order.description?.trim() ?: ""
        val status = order.status?.trim() ?: ""
        val techId = order.technician_id?.trim() ?: ""
        
        if (status == "sent" || status == "delivered") return true
        if (desc.contains("خرید") || desc.contains("قطعه") || desc.contains("فروشگاه") || desc.contains("فیش")) {
            if (techId.isEmpty() || techId == "null") return true
        }
        return false
    }

    // --- Repair Orders ---
    fun loadRepairs(silent: Boolean = false) {
        val token = getSessionToken() ?: return
        viewModelScope.launch {
            if (!silent) {
                _isRepairsLoading.value = true
            }
            try {
                val user = _currentUser.value
                val resp = repository.getRepairs(token)

                val allOrders = ((resp.repairs ?: emptyList()) +
                        (resp.repair_requests ?: emptyList()) +
                        (resp.orders ?: emptyList()) +
                        (resp.data ?: emptyList())).distinctBy { it.id ?: "${it.description}_${it.created_at}" }

                // Classify orders into Repair Requests vs Part Purchases dynamically
                val rawRepairs = allOrders.filter { !isPartPurchase(it) }
                val rawPurchases = allOrders.filter { isPartPurchase(it) }

                // 1. Repair orders
                val isTech = user != null && (user.role == "technician" || user.role == "tech" || user.role == "repairman" || user.isTechnicianUser)
                val isApprovedTech = isTech && user?.isApprovedUser == true
                val isOnline = _isTechnicianOnline.value
                val localOverrides = getLocalOrderStatusOverrides()

                val filtered = if (isTech) {
                    rawRepairs.mapNotNull { rawOrder ->
                        val oid = rawOrder.resolvedOrderId.ifBlank { rawOrder.id ?: "" }
                        val overrideStatus = localOverrides[oid]
                        val order = if (overrideStatus != null) {
                            rawOrder.copy(
                                status = overrideStatus,
                                technician_id = user?.id ?: rawOrder.technician_id,
                                technician_name = user?.full_name ?: rawOrder.technician_name,
                                technician_phone = user?.phone ?: rawOrder.technician_phone
                            )
                        } else {
                            rawOrder
                        }

                        val isAssignedToMe = (order.technician_id != null && order.technician_id == user!!.id) ||
                                (!order.technician_name.isNullOrBlank() && user!!.full_name != null && order.technician_name == user.full_name) ||
                                overrideStatus != null ||
                                _liveTechnicians.value.any { tech ->
                                    (tech.id == user!!.id || (!tech.name.isNullOrBlank() && tech.name == user.full_name)) &&
                                    (!order.technician_id.isNullOrBlank() && tech.id == order.technician_id)
                                }

                        // اگر سرور رسماً سفارش را به این تکنسین اختصاص داده بود، یعنی مدیر تایید کرده و وضعیت در انتظار پاک می‌شود
                        val isOfficiallyAssignedByServer = (rawOrder.technician_id != null && rawOrder.technician_id == user!!.id) ||
                                (!rawOrder.technician_phone.isNullOrBlank() && rawOrder.technician_phone == user!!.phone)
                        if (isOfficiallyAssignedByServer && oid.isNotBlank() && isOrderCommissionPendingApproval(oid)) {
                            removeOrderCommissionPending(oid)
                        }
                        val isCreatedByMe = (order.user_id != null && order.user_id == user!!.id) ||
                                (!user!!.phone.isNullOrBlank() && (order.user_phone == user.phone || order.customer_phone == user.phone || order.description?.contains(user.phone!!) == true))
                        val orderCity = if (!order.city.isNullOrBlank()) order.city else user!!.city
                        val cityMatches = areCitiesCompatible(orderCity, user!!.city)
                        val isUnassigned = (order.technician_id.isNullOrBlank() || order.technician_id == "null") && overrideStatus == null

                        // Unapproved technicians can only see orders directly assigned to or created by them; never new unassigned pool orders
                        if (isAssignedToMe || isCreatedByMe || (isApprovedTech && isOnline && isUnassigned && cityMatches)) {
                            order
                        } else {
                            null
                        }
                    }
                } else {
                    rawRepairs.map { rawOrder ->
                        val oid = rawOrder.resolvedOrderId.ifBlank { rawOrder.id ?: "" }
                        val isLocalRated = sharedPrefs.getBoolean("rated_order_$oid", false)
                        val ratedRating = sharedPrefs.getFloat("rating_val_$oid", -1f)
                        val ratedComment = sharedPrefs.getString("rating_comment_$oid", null)
                        val skillRating = sharedPrefs.getFloat("skill_rating_$oid", -1f)
                        val ethicsRating = sharedPrefs.getFloat("ethics_rating_$oid", -1f)
                        val punctualityRating = sharedPrefs.getFloat("punctuality_rating_$oid", -1f)
                        if (isLocalRated || ratedRating > 0f) {
                            rawOrder.copy(
                                is_rated = true,
                                isRated = true,
                                rating = if (ratedRating > 0f) ratedRating.toDouble() else rawOrder.rating ?: 5.0,
                                rating_comment = ratedComment ?: rawOrder.rating_comment,
                                skill_rating = if (skillRating > 0f) skillRating.toDouble() else rawOrder.skill_rating,
                                ethics_rating = if (ethicsRating > 0f) ethicsRating.toDouble() else rawOrder.ethics_rating,
                                punctuality_rating = if (punctualityRating > 0f) punctualityRating.toDouble() else rawOrder.punctuality_rating
                            )
                        } else {
                            rawOrder
                        }
                    }
                }
                if (_repairOrders.value != filtered) {
                    _repairOrders.value = filtered
                }

                // Detect new unassigned orders for active & approved technician and trigger sound & alert popup
                if (isApprovedTech && _isTechnicianOnline.value) {
                    val currentOrderIds = filtered.mapNotNull { it.resolvedOrderId.ifBlank { it.id } }
                    if (hasInitializedOrderIds) {
                        val brandNewOrders = filtered.filter { order ->
                            val oid = order.resolvedOrderId.ifBlank { order.id ?: "" }
                            oid.isNotBlank() && !knownOrderIds.contains(oid) && 
                            (order.technician_id.isNullOrBlank() || order.technician_id == "null") &&
                            (order.status == "pending" || order.status == "new" || order.status.isNullOrBlank())
                        }
                        if (brandNewOrders.isNotEmpty()) {
                            val newest = brandNewOrders.first()
                            _newOrderAlert.value = newest
                            playOrderAlertSound()
                        }
                    }
                    knownOrderIds.addAll(currentOrderIds)
                    hasInitializedOrderIds = true
                }

                // 2. Part Purchases (direct purchases list from server or classified orders)
                val directPurchases = (resp.purchases ?: emptyList()) + (resp.part_orders ?: emptyList()) + (resp.store_orders ?: emptyList())
                val mappedPurchasesFromOrders = rawPurchases.map { order ->
                    PartPurchaseOrder(
                        id = order.id ?: "order_${System.currentTimeMillis()}_${order.id.hashCode()}",
                        order_id = order.order_id ?: order.orderId ?: order.id,
                        partId = "",
                        partName = order.description ?: "سفارش قطعه",
                        quantity = 1,
                        unitPrice = 0.0,
                        totalPrice = 0.0,
                        address = order.city ?: "",
                        notes = "ثبت شده در سایت",
                        dateStr = order.resolvedDate.ifBlank { getCurrentPersianDate() },
                        status = order.status ?: "pending",
                        status_label_fa = order.status_label_fa ?: order.statusLabelFa,
                        tracking_code = order.tracking_code ?: order.trackingCode,
                        shamsiDate = order.shamsi_date ?: order.shamsiDate
                    )
                }
                val allServerPurchases = (directPurchases + mappedPurchasesFromOrders).distinctBy { it.resolvedId.ifBlank { "${it.resolvedPartName}_${it.resolvedDate}" } }
                
                // Merge server-fetched purchases with existing local purchases so local order details are retained while status is refreshed
                val currentLocal = _partPurchases.value
                val updatedLocal = currentLocal.map { local ->
                    val matchingServer = allServerPurchases.find { s ->
                        (s.resolvedId.isNotBlank() && s.resolvedId == local.resolvedId) ||
                        (s.resolvedPartName.isNotBlank() && (s.resolvedPartName == local.resolvedPartName || s.resolvedPartName.contains(local.resolvedPartName)))
                    }
                    if (matchingServer != null) {
                        local.copy(
                            status = matchingServer.status.ifBlank { local.status },
                            status_label_fa = matchingServer.resolvedStatusLabelFa ?: local.resolvedStatusLabelFa,
                            tracking_code = matchingServer.resolvedTrackingCode.ifBlank { local.resolvedTrackingCode },
                            shamsiDate = matchingServer.shamsiDate ?: local.shamsiDate,
                            totalPrice = if (matchingServer.resolvedTotalPrice > 0) matchingServer.resolvedTotalPrice else local.resolvedTotalPrice,
                            quantity = if (matchingServer.quantity > 0) matchingServer.quantity else local.quantity
                        )
                    } else {
                        local
                    }
                }
                val newFromServer = allServerPurchases.filter { s ->
                    currentLocal.none { local ->
                        (s.resolvedId.isNotBlank() && s.resolvedId == local.resolvedId) ||
                        (s.resolvedPartName.isNotBlank() && (s.resolvedPartName == local.resolvedPartName || s.resolvedPartName.contains(local.resolvedPartName)))
                    }
                }
                val mergedPurchases = updatedLocal + newFromServer

                if (_partPurchases.value != mergedPurchases) {
                    _partPurchases.value = mergedPurchases
                }
                try {
                    val json = partPurchasesAdapter.toJson(mergedPurchases)
                    sharedPrefs.edit().putString("part_purchases_json", json).apply()
                } catch (ex: java.lang.Exception) {
                    Log.e("AssistantViewModel", "Error updating local part purchases cache", ex)
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error fetching repairs", e)
            } finally {
                _isRepairsLoading.value = false
            }
        }
    }

    fun submitRepairRequest(
        techId: String,
        description: String,
        city: String,
        appliance: String = "عمومی",
        brand: String = "عمومی",
        model: String? = null,
        errorCode: String? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        address: String? = null,
        region: String? = null,
        postalCode: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationUrl: String? = null,
        addressNote: String? = null,
        scheduledDate: String? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        val token = getSessionToken()
        if (token == null) {
            onResult(false, "باید وارد حساب خود شوید")
            return
        }

        val user = _currentUser.value
        val tech = _liveTechnicians.value.find { it.id == techId }
        if (user != null && tech != null) {
            val userCityStr = user.resolvedCity.ifBlank { user.city }
            val techCityStr = tech.resolvedCity.ifBlank { tech.city }
            if (!areCitiesCompatible(userCityStr, techCityStr)) {
                onResult(false, "شما فقط می‌توانید از تکنسین‌های فعال در محدوده خود درخواست کار ثبت کنید")
                return
            }
        }

        viewModelScope.launch {
            try {
                val response = repository.createRepair(
                    token = token,
                    city = if (city.isNotBlank()) city else (user?.city ?: "عمومی"),
                    appliance = if (appliance.isNotBlank()) appliance else "عمومی",
                    brand = if (brand.isNotBlank()) brand else "عمومی",
                    model = model,
                    problemDescription = description,
                    errorCode = errorCode,
                    customerName = customerName ?: user?.full_name,
                    customerPhone = customerPhone ?: user?.phone,
                    address = address,
                    technicianId = techId,
                    region = region ?: user?.province ?: user?.city,
                    postalCode = postalCode,
                    latitude = latitude,
                    longitude = longitude,
                    locationUrl = locationUrl,
                    addressNote = addressNote,
                    scheduledDate = scheduledDate
                )
                if (response.status == "ok" || response.status == "success") {
                    loadRepairs()
                    onResult(true, null)
                } else {
                    onResult(false, response.error ?: response.message ?: "خطا در ثبت درخواست تعمیرکار")
                }
            } catch (e: Exception) {
                onResult(false, "خطای ارتباط به سرور: ${e.message}")
            }
        }
    }

    fun updateUserProfile(fullName: String?, city: String?, onResult: (Boolean, String?) -> Unit) {
        val token = getSessionToken()
        if (token.isNullOrBlank()) {
            onResult(false, "نشست کاربری یافت نشد.")
            return
        }
        viewModelScope.launch {
            try {
                val resp = repository.updateProfile(token, fullName, city)
                if (resp.status == "ok" || resp.status == "success") {
                    val user = _currentUser.value
                    if (user != null) {
                        val updated = user.copy(
                            full_name = fullName ?: user.full_name,
                            city = city ?: user.city
                        )
                        _currentUser.value = updated
                        saveUserToCache(updated)
                    }
                    onResult(true, null)
                } else {
                    onResult(false, resp.error ?: "خطا در بروزرسانی پروفایل")
                }
            } catch (e: Exception) {
                onResult(false, "خطای ارتباط با سرور: ${e.message}")
            }
        }
    }

    fun updateOrderStatus(orderId: String, status: String, amount: Long? = null, onResult: (Boolean, String?) -> Unit) {
        val token = getSessionToken()
        if (token.isNullOrBlank()) {
            onResult(false, "نشست کاربری یافت نشد.")
            return
        }
        val user = _currentUser.value
        val techId = user?.id

        // 1. Save persistent local override so this order stays assigned/updated across all future loads
        saveLocalOrderStatusOverride(orderId, status)

        // 2. Optimistically update local order state immediately for instant feedback
        val currentOrders = _repairOrders.value
        val updatedOrders = currentOrders.map { order ->
            val oid = order.resolvedOrderId.ifBlank { order.id ?: "" }
            if (oid == orderId || order.id == orderId || order.order_id == orderId) {
                order.copy(
                    status = status,
                    technician_id = techId ?: order.technician_id,
                    technician_name = user?.full_name ?: order.technician_name,
                    technician_phone = user?.phone ?: order.technician_phone
                )
            } else {
                order
            }
        }
        _repairOrders.value = updatedOrders

        viewModelScope.launch {
            try {
                val res = repository.updateOrderStatus(token, orderId, status, techId, amount)
                loadRepairs(silent = true)
                checkSavedSession() // Refresh user wallet and commission status
                if (res.status == "error" || res.success == false) {
                    val errMsg = res.message ?: res.error ?: "خطا در بروزرسانی وضعیت سفارش"
                    onResult(false, errMsg)
                } else {
                    onResult(true, null)
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "API updateOrderStatus sync error", e)
                loadRepairs(silent = true)
                val errMsg = repository.parseApiError(e)
                onResult(false, errMsg)
            }
        }
    }

    fun acceptRepairOrder(orderId: String, onResult: (Boolean, String?) -> Unit) {
        updateOrderStatus(orderId, "accepted", amount = null, onResult = onResult)
    }

    fun unlockOrderWithCommission(
        order: KodyarRepairOrder,
        trackingCode: String,
        depositorName: String,
        commissionAmount: Long,
        onResult: (Boolean, String?) -> Unit
    ) {
        val orderId = order.resolvedOrderId.ifBlank { order.id ?: "" }
        if (orderId.isBlank()) {
            onResult(false, "شناسه سفارش نامعتبر است")
            return
        }
        val user = _currentUser.value
        viewModelScope.launch {
            try {
                // 1. Submit card-to-card commission payment receipt to server
                val settleReq = com.example.data.api.SettleCommissionRequest(
                    techId = user?.id,
                    phone = user?.phone,
                    amount = commissionAmount,
                    paymentMethod = "card_to_card",
                    trackingCode = trackingCode,
                    orderId = orderId
                )
                val settleRes = repository.settleCommission(settleReq)

                // 2. Mark this order as waiting for admin approval (DO NOT unlock or assign yet until admin approves)
                markOrderCommissionPending(orderId)
                loadRepairs(silent = true)
                checkSavedSession()

                val msg = settleRes.message ?: "فیش کمیسیون با موفقیت ثبت شد و در انتظار تایید مدیریت قرار گرفت."
                onResult(true, msg)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "خطا در ارتباط با سرور")
            }
        }
    }

    // --- Technician Rating & Review System ---
    private val reviewsAdapter by lazy {
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, TechnicianReview::class.java)
        moshi.adapter<List<TechnicianReview>>(type)
    }

    fun enrichTechniciansWithReviews(techs: List<KodyarTechnician>): List<KodyarTechnician> {
        return techs.map { t ->
            val localReviews = getLocalReviewsForTechnician(t.id, t.name)
            val serverReviews = t.reviews ?: emptyList()
            if (localReviews.isNotEmpty() || serverReviews.isNotEmpty()) {
                val merged = (serverReviews + localReviews).distinctBy { it.order_id ?: it.id ?: "${it.comment}_${it.created_at}" }
                val avgRating = merged.map { it.rating }.average()
                val avgSkill = merged.map { it.skill_rating }.average()
                val avgEthics = merged.map { it.ethics_rating }.average()
                val avgPunct = merged.map { it.punctuality_rating }.average()
                val satisfaction = ((avgRating / 5.0) * 100).toInt().coerceIn(60, 100)
                val newCount = kotlin.math.max(merged.size, (t.ratingsCount ?: t.ratings_count ?: 0))
                t.copy(
                    rating = kotlin.math.round(avgRating * 10) / 10.0,
                    ratingsCount = newCount,
                    ratings_count = newCount,
                    satisfactionRate = satisfaction,
                    satisfaction_rate = satisfaction,
                    skill_rating = kotlin.math.round(avgSkill * 10) / 10.0,
                    ethics_rating = kotlin.math.round(avgEthics * 10) / 10.0,
                    punctuality_rating = kotlin.math.round(avgPunct * 10) / 10.0,
                    reviews = merged
                )
            } else {
                t
            }
        }
    }

    fun getLocalReviewsForTechnician(techId: String?, techName: String?): List<TechnicianReview> {
        val keys = listOfNotNull(
            techId?.takeIf { it.isNotBlank() }?.let { "tech_reviews_$it" },
            techName?.takeIf { it.isNotBlank() }?.let { "tech_reviews_${it.trim().replace(" ", "_")}" }
        )
        for (key in keys) {
            val json = sharedPrefs.getString(key, null)
            if (!json.isNullOrBlank()) {
                try {
                    val list = reviewsAdapter.fromJson(json)
                    if (!list.isNullOrEmpty()) return list
                } catch (e: Exception) {
                    Log.e("AssistantViewModel", "Error parsing technician reviews from cache", e)
                }
            }
        }
        return emptyList()
    }

    private fun saveLocalReviewForTechnician(techId: String?, techName: String?, newReview: TechnicianReview) {
        val current = (getLocalReviewsForTechnician(techId, techName) + newReview).distinctBy { it.order_id ?: it.id }
        try {
            val json = reviewsAdapter.toJson(current)
            if (!techId.isNullOrBlank()) {
                sharedPrefs.edit().putString("tech_reviews_$techId", json).apply()
            }
            if (!techName.isNullOrBlank()) {
                val key = "tech_reviews_${techName.trim().replace(" ", "_")}"
                sharedPrefs.edit().putString(key, json).apply()
            }
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Error saving technician reviews to cache", e)
        }
    }

    fun getTechnicianReviews(techId: String?, techName: String?): List<TechnicianReview> {
        val local = getLocalReviewsForTechnician(techId, techName)
        val techInList = _liveTechnicians.value.firstOrNull { t ->
            (!techId.isNullOrBlank() && (t.id == techId || t.user_id == techId)) ||
            (!techName.isNullOrBlank() && t.resolvedName == techName)
        }
        val serverReviews = techInList?.resolvedReviews ?: emptyList()
        return (serverReviews + local).distinctBy { it.order_id ?: it.id ?: "${it.comment}_${it.created_at}" }
    }

    fun rateTechnicianOrder(
        order: KodyarRepairOrder,
        overallRating: Double,
        skillRating: Double,
        ethicsRating: Double,
        punctualityRating: Double,
        comment: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val orderId = order.resolvedOrderId.ifBlank { order.id ?: "" }
        if (orderId.isBlank()) {
            onResult(false, "شناسه سفارش نامعتبر است")
            return
        }

        val techId = order.technician_id ?: ""
        val techName = order.resolvedTechnicianName
        val calculatedOverall = if (overallRating > 0.0) overallRating else kotlin.math.round(((skillRating + ethicsRating + punctualityRating) / 3.0) * 10) / 10.0

        val token = getSessionToken()
        val user = _currentUser.value

        // 1. Immediately cache the rating locally so user sees it instantly
        val ratingKey = "rated_order_$orderId"
        sharedPrefs.edit()
            .putBoolean(ratingKey, true)
            .putFloat("rating_val_$orderId", calculatedOverall.toFloat())
            .putFloat("skill_rating_$orderId", skillRating.toFloat())
            .putFloat("ethics_rating_$orderId", ethicsRating.toFloat())
            .putFloat("punctuality_rating_$orderId", punctualityRating.toFloat())
            .putString("rating_comment_$orderId", comment)
            .apply()

        // 2. Optimistically update local order state
        val updatedOrders = _repairOrders.value.map { o ->
            val oid = o.resolvedOrderId.ifBlank { o.id ?: "" }
            if (oid == orderId || o.id == orderId || o.order_id == orderId) {
                o.copy(
                    is_rated = true,
                    isRated = true,
                    rating = calculatedOverall,
                    skill_rating = skillRating,
                    ethics_rating = ethicsRating,
                    punctuality_rating = punctualityRating,
                    rating_comment = comment
                )
            } else {
                o
            }
        }
        _repairOrders.value = updatedOrders

        // 3. Create review object and save locally
        val newReview = TechnicianReview(
            id = "rev_${System.currentTimeMillis()}",
            order_id = orderId,
            technician_id = techId,
            customer_name = order.resolvedCustomerName.ifBlank { user?.full_name ?: "مشتری کدیار" },
            customer_phone = order.resolvedCustomerPhone.ifBlank { user?.phone },
            rating = calculatedOverall,
            skill_rating = skillRating,
            ethics_rating = ethicsRating,
            punctuality_rating = punctualityRating,
            comment = comment,
            created_at = getCurrentPersianDate(),
            shamsi_date = getCurrentPersianDate()
        )
        saveLocalReviewForTechnician(techId, techName, newReview)

        // 4. Update technician's live aggregate score and reviews in _liveTechnicians
        val currentTechs = _liveTechnicians.value
        val updatedTechs = currentTechs.map { t ->
            val matches = (techId.isNotBlank() && (t.id == techId || t.user_id == techId)) ||
                          (!techName.isNullOrBlank() && t.resolvedName == techName)
            if (matches) {
                val allRevs = (t.resolvedReviews + newReview).distinctBy { it.order_id ?: it.id }
                val newCount = kotlin.math.max(allRevs.size, (t.ratingsCount ?: t.ratings_count ?: 0) + 1)
                val avgRating = allRevs.map { it.rating }.average()
                val avgSkill = allRevs.map { it.skill_rating }.average()
                val avgEthics = allRevs.map { it.ethics_rating }.average()
                val avgPunct = allRevs.map { it.punctuality_rating }.average()
                val satisfaction = ((avgRating / 5.0) * 100).toInt().coerceIn(60, 100)
                t.copy(
                    rating = kotlin.math.round(avgRating * 10) / 10.0,
                    ratingsCount = newCount,
                    ratings_count = newCount,
                    satisfactionRate = satisfaction,
                    satisfaction_rate = satisfaction,
                    skill_rating = kotlin.math.round(avgSkill * 10) / 10.0,
                    ethics_rating = kotlin.math.round(avgEthics * 10) / 10.0,
                    punctuality_rating = kotlin.math.round(avgPunct * 10) / 10.0,
                    reviews = allRevs
                )
            } else {
                t
            }
        }
        _liveTechnicians.value = updatedTechs

        // 5. Send real request to backend server
        viewModelScope.launch {
            try {
                val resp = repository.rateTechnicianOrder(
                    token = token,
                    orderId = orderId,
                    technicianId = techId,
                    rating = calculatedOverall,
                    skillRating = skillRating,
                    ethicsRating = ethicsRating,
                    punctualityRating = punctualityRating,
                    comment = comment,
                    customerName = order.resolvedCustomerName.ifBlank { user?.full_name },
                    customerPhone = order.resolvedCustomerPhone.ifBlank { user?.phone }
                )
                loadRepairs(silent = true)
                onResult(true, resp.message ?: "امتیاز و نظر شما با موفقیت ثبت شد")
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error sending rateTechnicianOrder to server", e)
                onResult(true, "امتیاز شما ثبت و ذخیره شد")
            }
        }
    }

    // --- Subscription Card Verification ---
    fun submitCardVerify(cardHolder: String, trackNumber: String, productId: String, onResult: (Boolean, String?) -> Unit) {
        val token = getSessionToken()
        if (token == null) {
            onResult(false, "باید وارد حساب خود شوید")
            return
        }

        viewModelScope.launch {
            _isCardVerifyLoading.value = true
            try {
                val response = repository.verifyCard(token, cardHolder, trackNumber, productId)
                if (response.status == "ok" || response.status == "success") {
                    _cardVerifySuccess.value = true
                    checkSavedSession() // Refreshes premium status
                    onResult(true, null)
                } else {
                    onResult(false, response.error ?: "خطا در ثبت فیش پرداخت")
                }
            } catch (e: Exception) {
                onResult(false, "خطای ارتباط به سرور: ${e.message}")
            } finally {
                _isCardVerifyLoading.value = false
            }
        }
    }

    fun resetCardVerifySuccess() {
        _cardVerifySuccess.value = false
    }

    // --- Free usages ---
    fun loadFreeStatus() {
        val token = getSessionToken() ?: return
        viewModelScope.launch {
            try {
                val response = repository.getFreeStatus(token)
                _freeErrorCount.value = response.error_count ?: 0
                _freeProblemCount.value = response.problem_count ?: 0
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error loading free status", e)
            }
        }
    }

    fun useFreeCount(type: String, onComplete: () -> Unit) {
        val token = getSessionToken()
        if (token == null) {
            onComplete()
            return
        }
        viewModelScope.launch {
            try {
                val response = repository.useFree(token, type)
                _freeErrorCount.value = response.error_count ?: _freeErrorCount.value
                _freeProblemCount.value = response.problem_count ?: _freeProblemCount.value
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error posting free use", e)
            } finally {
                onComplete()
            }
        }
    }

    // --- AI Smart Repair Assistant Conversation State ---
    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<MessageEntity>> = _currentConversationId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMessages(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dedicated System Instruction for Home Appliance Repair Expert AI
    private val repairAssistantSystemInstruction = """
        شما یک تعمیرکار فوق‌العاده با‌تجربه، دقیق، دلسوز و حرفه‌ای لوازم خانگی به نام کدیار۲۴ (Codyar24) هستید. 
        شما باید به زبان فارسی صمیمی، روان، مودبانه و بسیار فنی، کاربران و تعمیرکاران را برای عیب‌یابی و حل مشکلات لوازم خانگی مانند ماشین لباسشویی، یخچال، فریزر، ماشین ظرفشویی، کولر گازی، اسپلیت، جاروبرقی، مایکروویو و سایر تجهیزات هدایت کنید.
        
        لطفاً هنگام پاسخ دادن به هر سوال عیب‌یابی:
        1. علت‌های احتمالی (مثلاً خرابی قطعه، اتصالات، کثیفی فیلترها و غیره) را به صورت لیست‌وار و خوانا بنویسید.
        2. مراحل گام‌به‌گام برای تست و رفع عیب ارائه دهید.
        3. نکات ایمنی (مانند کشیدن دوشاخه از برق قبل از هر تستی) را حتماً متذکر شوید.
        4. در صورت نیاز به بررسی تخصصی‌تر، بگویید که نیاز به تکنسین مجاز است.
        پاسخ‌های شما باید کاملاً متمرکز بر حل مشکل و کاربردی باشند.
    """.trimIndent()

    fun selectConversation(id: Long?) {
        _currentConversationId.value = id
        _errorMessage.value = null
    }

    fun startNewConversation() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val conversationId = repository.createConversation("گفتگوی جدید تعمیراتی", "Codyar24")
                _currentConversationId.value = conversationId
                
                // Seed custom welcoming message in Persian
                val welcome = MessageEntity(
                    conversationId = conversationId,
                    role = "model",
                    text = "سلام! من کدیار۲۴ دستیار هوشمند و تخصصی تعمیرات لوازم خانگی شما هستم. دستگاه شما چه مشکلی دارد؟ یا به دنبال کدام کد خطا هستید؟ برند و مشکل آن را مطرح کنید تا با هم عیب‌یابی کنیم."
                )
                // Use sendMessage or a custom direct insertion
            } catch (e: Exception) {
                _errorMessage.value = "خطا در شروع گفتگو: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            if (_currentConversationId.value == id) {
                _currentConversationId.value = null
            }
            repository.deleteConversation(id)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            _currentConversationId.value = null
            repository.clearAllConversations()
        }
    }

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                var convId = _currentConversationId.value

                // If no active conversation, create one dynamically
                if (convId == null) {
                    val generatedTitle = if (trimmedText.length > 25) {
                        "${trimmedText.take(22)}..."
                    } else {
                        trimmedText
                    }
                    convId = repository.createConversation(generatedTitle, "Codyar24")
                    _currentConversationId.value = convId
                }

                if (convId != null) {
                    val result = repository.sendMessage(
                        conversationId = convId,
                        userText = trimmedText,
                        systemInstructionText = repairAssistantSystemInstruction
                    )
                    
                    result.onFailure { exception ->
                        _errorMessage.value = exception.localizedMessage ?: "خطای ارتباط با سرور هوش مصنوعی"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "خطا در ارسال پیام: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun setupNetworkCallback() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("AssistantViewModel", "Internet connected dynamically! Refreshing database immediately...")
                    viewModelScope.launch {
                        loadKodyarDatabase()
                        fetchSubscriptionPlans()
                        val token = getSessionToken()
                        if (token != null) {
                            loadCurrentUser(token)
                        }
                    }
                }
            }
            networkCallback = callback
            connectivityManager.registerNetworkCallback(networkRequest, callback)
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to register network callback.", e)
        }
    }

    // --- Global Live Sync & App Refresh ---
    private val _isGlobalRefreshing = MutableStateFlow(false)
    val isGlobalRefreshing: StateFlow<Boolean> = _isGlobalRefreshing.asStateFlow()

    fun syncAllLiveAppData(isInitial: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLiveDataSyncing.value = true
            if (!isInitial) {
                _isGlobalRefreshing.value = true
            }
            var success = false
            try {
                // Fetch all dynamic live backend endpoints in parallel
                val sessionJob = async { checkSavedSession() }
                val techsJob = async { refreshTechnicians() }
                val dbJob = async { loadKodyarDatabase() }
                val plansJob = async { fetchSubscriptionPlans() }
                val repairsJob = async { loadRepairs() }
                val purchasesJob = async { loadPartPurchases() }
                val freeStatusJob = async { loadFreeStatus() }

                sessionJob.await()
                techsJob.await()
                dbJob.await()
                plansJob.await()
                repairsJob.await()
                purchasesJob.await()
                freeStatusJob.await()
                success = true
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error in syncAllLiveAppData", e)
            } finally {
                _isLiveDataSyncing.value = false
                _isGlobalRefreshing.value = false
                _isSparePartsLoading.value = false
                _isTechniciansLoading.value = false
                _isRepairsLoading.value = false
                onComplete?.invoke(success)
            }
        }
    }

    fun refreshAllAppData(onComplete: ((Boolean) -> Unit)? = null) {
        syncAllLiveAppData(isInitial = false, onComplete = onComplete)
    }

    fun isCurrentUserVerifiedTechnician(): Boolean {
        val user = _currentUser.value ?: return false
        if (user.isTechnicianUser) return true
        return _liveTechnicians.value.any { tech ->
            (tech.id == user.id ||
             (!tech.phone.isNullOrBlank() && tech.phone == user.phone) ||
             (!tech.name.isNullOrBlank() && tech.name == user.full_name)) &&
            tech.isVerified == true
        }
    }

    // 🎫 --- سامانه تیکت‌های پشتیبانی (Support Tickets) ---
    private val _userTickets = MutableStateFlow<List<TicketModel>>(emptyList())
    val userTickets: StateFlow<List<TicketModel>> = _userTickets

    private val _isTicketsLoading = MutableStateFlow(false)
    val isTicketsLoading: StateFlow<Boolean> = _isTicketsLoading

    private val _ticketsErrorMessage = MutableStateFlow<String?>(null)
    val ticketsErrorMessage: StateFlow<String?> = _ticketsErrorMessage

    private val _selectedTicket = MutableStateFlow<TicketModel?>(null)
    val selectedTicket: StateFlow<TicketModel?> = _selectedTicket

    fun selectTicket(ticket: TicketModel?) {
        _selectedTicket.value = ticket
    }

    fun loadUserTickets(silent: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        val phoneOrToken = getSessionToken() ?: _currentUser.value?.phone ?: _currentUser.value?.id ?: ""
        if (phoneOrToken.isBlank()) {
            if (!silent) _isTicketsLoading.value = false
            onComplete?.invoke(false)
            return
        }
        if (!silent) {
            _isTicketsLoading.value = true
            _ticketsErrorMessage.value = null
        }
        viewModelScope.launch {
            val result = repository.getMyTickets(phoneOrToken)
            if (!silent) _isTicketsLoading.value = false
            result.onSuccess { list ->
                if (_userTickets.value != list) {
                    _userTickets.value = list
                }
                // If a ticket is currently selected, update it with fresh data
                _selectedTicket.value?.let { currentSelected ->
                    val updated = list.find { it.id == currentSelected.id }
                    if (updated != null && _selectedTicket.value != updated) {
                        _selectedTicket.value = updated
                    }
                }
                onComplete?.invoke(true)
            }.onFailure { e ->
                Log.e("AssistantViewModel", "Failed to load tickets", e)
                if (!silent) {
                    _ticketsErrorMessage.value = "خطا در دریافت لیست تیکت‌ها: ${e.localizedMessage ?: "عدم ارتباط با سرور"}"
                }
                onComplete?.invoke(false)
            }
        }
    }

    fun createNewTicket(
        subject: String,
        category: String,
        department: String,
        message: String,
        onSuccess: (TicketModel?) -> Unit,
        onError: (String) -> Unit
    ) {
        val phoneOrToken = getSessionToken() ?: _currentUser.value?.phone ?: _currentUser.value?.id ?: ""
        if (phoneOrToken.isBlank()) {
            onError("لطفاً ابتدا وارد حساب کاربری خود شوید.")
            return
        }
        viewModelScope.launch {
            val req = CreateTicketRequest(
                subject = subject.trim(),
                category = category,
                department = department,
                message = message.trim()
            )
            val result = repository.createTicket(phoneOrToken, req)
            result.onSuccess { createdTicket ->
                loadUserTickets()
                onSuccess(createdTicket)
            }.onFailure { e ->
                onError(e.localizedMessage ?: "خطا در ثبت تیکت جدید")
            }
        }
    }

    fun sendTicketReply(
        ticketId: String,
        message: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val phoneOrToken = getSessionToken() ?: _currentUser.value?.phone ?: _currentUser.value?.id ?: ""
        if (phoneOrToken.isBlank()) {
            onError("لطفاً ابتدا وارد حساب کاربری خود شوید.")
            return
        }
        viewModelScope.launch {
            val result = repository.sendTicketReply(phoneOrToken, ticketId, message.trim())
            result.onSuccess { updatedTicket ->
                if (updatedTicket != null) {
                    _selectedTicket.value = updatedTicket
                    _userTickets.value = _userTickets.value.map {
                        if (it.id == updatedTicket.id) updatedTicket else it
                    }
                }
                loadUserTickets()
                onSuccess()
            }.onFailure { e ->
                onError(e.localizedMessage ?: "خطا در ارسال پاسخ")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to unregister network callback on clear.", e)
            }
        }
    }
}

class AssistantViewModelFactory(
    private val repository: AssistantRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AssistantViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class AppUpdateNotification(
    val newErrors: List<com.example.data.model.KodyarErrorCode>,
    val newProblems: List<com.example.data.model.KodyarCommonProblem>,
    val newParts: List<com.example.data.model.KodyarSparePart>
)

data class AppUpdateInfo(
    val latestVersionCode: Int = com.example.BuildConfig.VERSION_CODE,
    val latestVersionName: String = com.example.BuildConfig.VERSION_NAME,
    val isForceUpdate: Boolean = false,
    val updateTitle: String = "بروزرسانی جدید کدیار۲۴",
    val updateDescription: String = "نسخه ${com.example.BuildConfig.VERSION_NAME} اپلیکیشن تخصصی کدیار۲۴ منتشر شد.\nبرای استفاده از امکانات جدید، لطفاً نسخه جدید را دریافت کنید.",
    val notes: List<String> = listOf(
        "ارتقا به نسخه جدید (${com.example.BuildConfig.VERSION_NAME})",
        "امکان بازیابی آنلاین رمز عبور با کد پیامک سریع",
        "بهبود سرعت بارگذاری و رفع مشکلات گزارش شده"
    )
)

