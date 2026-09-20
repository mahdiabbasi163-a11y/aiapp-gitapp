package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * موقعیت مکانی ثبت‌شده توسط مشتری برای سفارش اعزام تکنسین.
 *
 * @property latitude عرض جغرافیایی
 * @property longitude طول جغرافیایی
 * @property accuracyMeters دقت تقریبی موقعیت بر حسب متر
 * @property resolvedAddress آدرس متنی استخراج‌شده از مختصات (در صورت موجود بودن)
 */
data class PickedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val resolvedAddress: String? = null
) {
    /** نمایش کوتاه مختصات برای درج در متن سفارش. */
    val shortLabel: String
        get() = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    /** لینک نقشه گوگل که تکنسین می‌تواند مستقیم باز کند. */
    val mapsUrl: String
        get() = "https://maps.google.com/?q=$latitude,$longitude"

    /**
     * بررسی تقریبی اینکه مختصات درون محدوده جغرافیایی ایران است.
     * وقتی VPN/فیلترشکن فعال باشد، موقعیت‌یابی مبتنی بر شبکه/IP ممکن است کشور دیگری بدهد،
     * برای همین قبل از قبول موقعیت باید این بررسی انجام شود.
     */
    val isPlausibleForIran: Boolean
        get() = latitude in 24.0..40.5 && longitude in 43.0..64.0
}

/**
 * بررسی می‌کند که آیا مجوز دسترسی به موقعیت مکانی صادر شده است یا نه.
 *
 * @param context کانتکست اندروید
 * @return true اگر دسترسی تقریبی یا دقیق صادر شده باشد
 */
fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

/**
 * آخرین موقعیت شناخته‌شده دستگاه را می‌خواند و آدرس متنی کامل آن را استخراج و آماده درج می‌کند.
 * از LocationManager داخلی اندروید استفاده می‌کند تا نیازی به وابستگی Google Play Services نباشد.
 * آدرس کامل به صورت چندلایه (Geocoder + معکوس‌ساز آنلاین Nominatim با زبان فارسی) استخراج می‌گردد.
 *
 * @param context کانتکست اندروید
 * @param onResult کال‌بک نتیجه؛ در صورت موفقیت شیء PickedLocation با resolvedAddress کامل، و در صورت خطا پیام فارسی برگردانده می‌شود
 */
fun requestCurrentLocation(
    context: Context,
    onResult: (PickedLocation?, String?) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onResult(null, "دسترسی به موقعیت مکانی داده نشده است")
        return
    }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (manager == null) {
        onResult(null, "سرویس موقعیت مکانی در دسترس نیست")
        return
    }

    val gpsEnabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    val networkEnabled = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
    if (!gpsEnabled && !networkEnabled) {
        onResult(null, "لطفاً موقعیت مکانی (GPS) گوشی را روشن کنید")
        return
    }

    fun processLocation(loc: Location) {
        val pickedPre = PickedLocation(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = loc.accuracy.takeIf { it > 0f }
        )
        if (!pickedPre.isPlausibleForIran) {
            onResult(null, "موقعیت دریافتی غیرمعقول به نظر می‌رسد (خارج ایران). اگر VPN یا فیلترشکن روشن است خاموش کنید و دوباره تلاش کنید")
            return
        }

        // استخراج آدرس کامل متنی در پس‌زمینه با Geocoder و فال‌بک آنلاین
        CoroutineScope(Dispatchers.IO).launch {
            val resolved = fetchAddressFromCoordinates(context, loc.latitude, loc.longitude)
            val finalPicked = pickedPre.copy(resolvedAddress = resolved)
            withContext(Dispatchers.Main) {
                onResult(finalPicked, null)
            }
        }
    }

    // اولویت همیشه با GPS واقعی گوشی است، نه موقعیت شبکه/وای‌فای که با VPN ممکن است کشور اشتباه بدهد
    val best = bestKnownLocation(manager, gpsEnabled)
    if (best != null) {
        processLocation(best)
        return
    }

    // اگر موقعیت ذخیره‌شده‌ای نبود، یک‌بار موقعیت تازه درخواست می‌کنیم — GPS اولویت دارد
    val provider = when {
        gpsEnabled -> LocationManager.GPS_PROVIDER
        else -> LocationManager.NETWORK_PROVIDER
    }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(context)
            ) { loc ->
                if (loc == null) {
                    onResult(null, "موقعیت مکانی پیدا نشد، لطفاً چند لحظه بعد دوباره تلاش کنید")
                    return@getCurrentLocation
                }
                processLocation(loc)
            }
        } else {
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(
                provider,
                { loc ->
                    processLocation(loc)
                },
                context.mainLooper
            )
        }
    } catch (e: SecurityException) {
        onResult(null, "دسترسی به موقعیت مکانی مجاز نیست")
    } catch (e: Exception) {
        onResult(null, "خطا در دریافت موقعیت: ${e.message}")
    }
}

/**
 * موقعیت ذخیره‌شده را برمی‌گرداند. اگر GPS فعال باشد فقط از GPS می‌خواند و سراغ Network Provider
 * نمی‌رود — چون Network Provider بر اساس IP/VPN است و ممکن است موقعیت کاملاً اشتباه (مثلاً آمریکا) بدهد.
 */
private fun bestKnownLocation(manager: LocationManager, preferGps: Boolean): Location? {
    return try {
        if (preferGps) {
            val gpsLoc = runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            if (gpsLoc != null && System.currentTimeMillis() - gpsLoc.time < 10 * 60 * 1000) {
                return gpsLoc
            }
            return null
        }
        manager.getProviders(true)
            .mapNotNull { p -> runCatching { manager.getLastKnownLocation(p) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 10 * 60 * 1000 }
            .maxByOrNull { -it.accuracy }
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * دریافت آدرس کامل متنی (استان، شهر، خیابان، کوچه و پلاک) از روی مختصات جغرافیایی.
 * ابتدا از Geocoder رسمی اندروید به زبان فارسی استفاده می‌کند.
 * در صورت خطا یا فیلتر بودن، از وب‌سرویس Reverse Geocoding آنلاین (Nominatim با زبان فارسی) به عنوان فال‌بک استفاده می‌کند.
 */
suspend fun fetchAddressFromCoordinates(
    context: Context,
    latitude: Double,
    longitude: Double
): String? = withContext(Dispatchers.IO) {
    // ۱. بررسی Geocoder داخلی اندروید
    val geocoderAddress = runCatching {
        if (Geocoder.isPresent()) {
            val geocoder = Geocoder(context, Locale("fa"))
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var result: List<android.location.Address>? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    result = addresses
                    latch.countDown()
                }
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                result
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
            }
            list?.firstOrNull()?.let { addr ->
                val lines = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }
                if (lines.isNotEmpty()) {
                    lines.joinToString("، ")
                } else {
                    listOfNotNull(
                        addr.adminArea,
                        addr.subAdminArea ?: addr.locality,
                        addr.subLocality,
                        addr.thoroughfare,
                        addr.subThoroughfare
                    ).distinct().joinToString("، ")
                }
            }
        } else null
    }.getOrNull()

    if (!geocoderAddress.isNullOrBlank()) {
        val cleaned = cleanPersianAddress(geocoderAddress)
        if (cleaned.isNotBlank()) return@withContext cleaned
    }

    // ۲. فال‌بک آنلاین پایدار با استفاده از سرور OSM Nominatim به زبان فارسی
    val onlineAddress = runCatching {
        val urlString = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&accept-language=fa&zoom=18&addressdetails=1"
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("User-Agent", "KodyarApp/2.0 (contact@kodyar24.ir)")
            setRequestProperty("Accept", "application/json")
        }
        if (conn.responseCode in 200..299) {
            val stream = conn.inputStream
            val text = stream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parseNominatimJson(text)
        } else {
            conn.disconnect()
            null
        }
    }.getOrNull()

    if (!onlineAddress.isNullOrBlank()) {
        val cleaned = cleanPersianAddress(onlineAddress)
        if (cleaned.isNotBlank()) return@withContext cleaned
    }

    // ۳. فال‌بک شهری بر اساس مختصات
    estimateCityFromCoordinates(latitude, longitude)
}

/**
 * استخراج و چینش صحیح بخش‌های آدرس از خروجی وب‌سرویس Nominatim
 */
private fun parseNominatimJson(jsonStr: String): String? {
    return runCatching {
        val root = JSONObject(jsonStr)
        val addrObj = root.optJSONObject("address")
        if (addrObj != null) {
            val state = addrObj.optString("province", addrObj.optString("state", "")).trim()
            val county = addrObj.optString("county", "").trim()
            val city = addrObj.optString("city", addrObj.optString("town", addrObj.optString("municipality", ""))).trim()
            val district = addrObj.optString("suburb", addrObj.optString("neighbourhood", addrObj.optString("city_district", ""))).trim()
            val road = addrObj.optString("road", addrObj.optString("street", "")).trim()
            val houseNumber = addrObj.optString("house_number", "").trim()

            val parts = mutableListOf<String>()
            if (city.isNotBlank()) parts.add(city)
            else if (county.isNotBlank()) parts.add(county)
            else if (state.isNotBlank()) parts.add(state)

            if (district.isNotBlank() && district != city) parts.add(district)
            if (road.isNotBlank()) parts.add(road)
            if (houseNumber.isNotBlank()) parts.add("پلاک $houseNumber")

            if (parts.isNotEmpty()) {
                return parts.joinToString("، ")
            }
        }
        val displayName = root.optString("display_name", "").trim()
        displayName.ifBlank { null }
    }.getOrNull()
}

/**
 * پاکسازی آدرس متنی از کلمات زائد مانند نام کشور یا بخش‌های تکراری
 */
private fun cleanPersianAddress(raw: String): String {
    return raw.split("،", ",")
        .map { it.trim() }
        .filter { part ->
            part.isNotBlank() &&
            part != "ایران" &&
            part != "Iran" &&
            !part.contains("بخش مرکزی") &&
            !part.matches(Regex("""^\d{5,10}$""")) // حذف کد پستی خام اگر در آدرس باشد
        }
        .distinct()
        .joinToString("، ")
}

/**
 * تخمین نام شهر از مختصات جهت جلوگیری از خالی ماندن فیلد در شرایط آفلاین
 */
private fun estimateCityFromCoordinates(lat: Double, lng: Double): String? {
    val majorCities = listOf(
        Triple("اراک", 34.0917, 49.6892),
        Triple("تهران", 35.6892, 51.3890),
        Triple("اصفهان", 32.6546, 51.6680),
        Triple("مشهد", 36.2972, 59.6067),
        Triple("شیراز", 29.5918, 52.5837),
        Triple("تبریز", 38.0800, 46.2919),
        Triple("کرج", 35.8400, 50.9391),
        Triple("قم", 34.6401, 50.8764),
        Triple("اهواز", 31.3183, 48.6706),
        Triple("کرمانشاه", 34.3142, 47.0650),
        Triple("رشت", 37.2808, 49.5832),
        Triple("ساری", 36.5659, 53.0586),
        Triple("قزوین", 36.2797, 50.0049),
        Triple("همدان", 34.7989, 48.5150),
        Triple("یزد", 31.8974, 54.3569),
        Triple("کرمان", 30.2839, 57.0834),
        Triple("ارومیه", 37.5527, 45.0761),
        Triple("خرم‌آباد", 33.4878, 48.3558),
        Triple("ساوه", 35.0208, 50.3582),
        Triple("شازند", 33.9272, 49.4097),
        Triple("خمین", 33.6425, 50.0789),
        Triple("محلات", 33.9103, 50.4578),
        Triple("دلیجان", 33.9905, 50.6838)
    )
    for ((name, cLat, cLng) in majorCities) {
        val dLat = Math.toRadians(lat - cLat)
        val dLng = Math.toRadians(lng - cLng)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(cLat)) * Math.cos(Math.toRadians(lat)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distanceKm = 6371 * c
        if (distanceKm <= 35.0) {
            return "محدوده $name (مختصات: ${String.format(Locale.US, "%.4f, %.4f", lat, lng)})"
        }
    }
    return "موقعیت جغرافیایی: ${String.format(Locale.US, "%.4f, %.4f", lat, lng)}"
}

/**
 * لینک نقشه‌ای که مشتری پیست می‌کند را می‌خواند و مختصات را از آن بیرون می‌کشد.
 * از لینک‌های گوگل‌مپ، نشان و مختصات خام پشتیبانی می‌کند.
 *
 * @param raw متن یا لینک واردشده توسط کاربر
 * @return موقعیت استخراج‌شده یا null اگر لینک قابل تشخیص نبود
 */
fun parseLocationFromLink(raw: String): PickedLocation? {
    val text = raw.trim()
    if (text.isBlank()) return null
    val regex = Regex("""(-?\d{1,2}\.\d{3,})\s*[,/]\s*(-?\d{1,3}\.\d{3,})""")
    val match = regex.find(text) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lng = match.groupValues[2].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return PickedLocation(latitude = lat, longitude = lng)
}

/**
 * وضعیت‌های مسیریاب جهت حفظ سازگاری کد
 */
enum class NavApp {
    AUTO, NESHAN, BALAD, GOOGLE_MAPS, WAZE
}

/** بررسی می‌کند که آیا پکیج مورد نظر روی گوشی نصب است یا خیر. */
fun isPackageInstalled(context: Context, packageName: String): Boolean {
    if (packageName.isBlank()) return false
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)
}

/**
 * اپلیکیشن نقشه/مسیریاب دستگاه را روی مختصات سفارش باز می‌کند.
 * از طریق سامانه استاندارد اندروید، کاربر هر مسیریابی که روی گوشی دارد را انتخاب می‌کند.
 *
 * @param context کانتکست اندروید
 * @param latitude عرض جغرافیایی مقصد
 * @param longitude طول جغرافیایی مقصد
 * @param label برچسب نمایشی مقصد
 * @return true اگر برنامه با موفقیت باز شد
 */
fun openInNavigator(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String = "محل مشتری"
): Boolean {
    val encoded = Uri.encode(label)
    return try {
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encoded)")
        val baseIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(baseIntent, "انتخاب مسیریاب").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
        true
    } catch (_: Exception) {
        try {
            val web = Uri.parse("https://maps.google.com/?q=$latitude,$longitude")
            context.startActivity(Intent(Intent.ACTION_VIEW, web).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * جهت حفظ سازگاری با فراخوانی‌های دارای پارامتر target
 */
fun openInNavigator(
    context: Context,
    latitude: Double,
    longitude: Double,
    @Suppress("UNUSED_PARAMETER") target: NavApp,
    label: String = "محل مشتری"
): Boolean = openInNavigator(context, latitude, longitude, label)

/**
 * جهت حفظ سازگاری با متدهای قبلی پروژه
 */
fun openInMaps(context: Context, latitude: Double, longitude: Double, label: String = "محل سفارش"): Boolean {
    return openInNavigator(context, latitude, longitude, label)
}

/**
 * کارت اختصاصی نمایش آدرس، کد پستی، نشانه و دکمه باز کردن مسیریاب برای تکنسین.
 */
@Composable
fun TechnicianLocationCard(
    fullAddress: String,
    postalCode: String,
    addressNote: String,
    coordinates: Pair<Double, Double>?,
    orderTitle: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFF0FDF4),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // آدرس کامل پستی
            if (fullAddress.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("📍", fontSize = 13.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "آدرس دقیق مشتری:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                        Text(
                            text = fullAddress,
                            fontSize = 12.sp,
                            color = Color(0xFF14532D),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // کد پستی
            if (postalCode.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("✉️", fontSize = 12.sp)
                    Text(
                        text = "کد پستی: $postalCode",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534)
                    )
                }
            }

            // نشانه یا توضیح مسیر
            if (addressNote.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🧭", fontSize = 12.sp)
                    Text(
                        text = "نشانه: $addressNote",
                        fontSize = 11.sp,
                        color = Color(0xFF166534)
                    )
                }
            }

            // دکمه باز کردن مسیریاب
            if (coordinates != null) {
                HorizontalDivider(color = Color(0xFFDCFCE7), thickness = 1.dp)

                Button(
                    onClick = {
                        openInNavigator(context, coordinates.first, coordinates.second, orderTitle)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "باز کردن در مسیریاب",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * ارقام فارسی و عربی را به ارقام انگلیسی تبدیل می‌کند تا کد پستی درست ذخیره شود.
 *
 * @param input متن ورودی کاربر
 * @return متن با ارقام انگلیسی
 */
fun normalizePersianDigits(input: String): String {
    val builder = StringBuilder(input.length)
    for (ch in input) {
        val normalized = when (ch) {
            in '\u06F0'..'\u06F9' -> ('0' + (ch - '\u06F0'))
            in '\u0660'..'\u0669' -> ('0' + (ch - '\u0660'))
            else -> ch
        }
        builder.append(normalized)
    }
    return builder.toString()
}