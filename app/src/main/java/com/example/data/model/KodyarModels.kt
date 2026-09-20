package com.example.data.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Json
@JsonClass(generateAdapter = true)
data class KodyarDatabaseData(
    val errorCodes: List<KodyarErrorCode>? = null,
    @Json(name = "error_codes") val error_codes: List<KodyarErrorCode>? = null,
    val spareParts: List<KodyarSparePart>? = null,
    @Json(name = "spare_parts") val spare_parts: List<KodyarSparePart>? = null,
    val technicians: List<KodyarTechnician>? = null,
    @Json(name = "techs") val techs: List<KodyarTechnician>? = null,
    val users: List<KodyarUser>? = null,
    val commonProblems: List<KodyarCommonProblem>? = null,
    @Json(name = "common_problems") val common_problems: List<KodyarCommonProblem>? = null,
    val categoriesList: List<String>? = null,
    @Json(name = "categories_list") val categories_list: List<String>? = null,
    val brandsList: List<String>? = null,
    @Json(name = "brands_list") val brands_list: List<String>? = null,
    val citiesList: List<KodyarCity>? = null,
    @Json(name = "cities_list") val cities_list: List<KodyarCity>? = null
)

@JsonClass(generateAdapter = true)
data class KodyarDatabaseResponse(
    val status: String? = null,
    val data: KodyarDatabaseData? = null,
    val errorCodes: List<KodyarErrorCode>? = null,
    @Json(name = "error_codes") val error_codes: List<KodyarErrorCode>? = null,
    val spareParts: List<KodyarSparePart>? = null,
    @Json(name = "spare_parts") val spare_parts: List<KodyarSparePart>? = null,
    val technicians: List<KodyarTechnician>? = null,
    @Json(name = "techs") val techs: List<KodyarTechnician>? = null,
    val users: List<KodyarUser>? = null,
    val commonProblems: List<KodyarCommonProblem>? = null,
    @Json(name = "common_problems") val common_problems: List<KodyarCommonProblem>? = null,
    val categoriesList: List<String>? = null,
    @Json(name = "categories_list") val categories_list: List<String>? = null,
    val brandsList: List<String>? = null,
    @Json(name = "brands_list") val brands_list: List<String>? = null,
    val citiesList: List<KodyarCity>? = null,
    @Json(name = "cities_list") val cities_list: List<KodyarCity>? = null,
    val latestVersionCode: Int? = null,
    val latestVersionName: String? = null,
    val isForceUpdate: Boolean? = null,
    val updateNotes: List<String>? = null
) {
    val resolvedErrorCodes: List<KodyarErrorCode>
        get() = errorCodes ?: error_codes ?: data?.errorCodes ?: data?.error_codes ?: emptyList()

    val resolvedSpareParts: List<KodyarSparePart>
        get() = spareParts ?: spare_parts ?: data?.spareParts ?: data?.spare_parts ?: emptyList()

    val resolvedTechnicians: List<KodyarTechnician>
        get() {
            val directList = if (!technicians.isNullOrEmpty()) technicians else if (!techs.isNullOrEmpty()) techs else if (data?.technicians?.isNotEmpty() == true) data.technicians else if (data?.techs?.isNotEmpty() == true) data.techs else null
            val combined = mutableListOf<KodyarTechnician>()
            if (!directList.isNullOrEmpty()) {
                combined.addAll(directList)
            }
            val allUsers = users ?: data?.users
            val techUsers = allUsers?.filter { it.role == "technician" || it.role == "tech" || it.role == "repairman" }
            if (!techUsers.isNullOrEmpty()) {
                for (u in techUsers) {
                    val existingIdx = combined.indexOfFirst {
                        (it.id?.isNotBlank() == true && (it.id == u.id || it.user_id == u.id)) ||
                        (!it.phone.isNullOrBlank() && it.phone == u.phone) ||
                        (!it.name.isNullOrBlank() && it.name == u.full_name)
                    }
                    val isUserVacation = u.isVacation
                    if (existingIdx >= 0) {
                        val existing = combined[existingIdx]
                        val effectiveStatus = if (isUserVacation) "vacation" else (u.status ?: existing.status)
                        val effectiveWorkStatus = if (isUserVacation) "vacation" else (u.work_status ?: existing.work_status)
                        val effectiveOnline = if (isUserVacation) false else (u.is_online ?: existing.is_online)
                        combined[existingIdx] = existing.copy(
                            phone = existing.phone ?: u.phone,
                            user_id = existing.user_id ?: u.id,
                            status = effectiveStatus,
                            work_status = effectiveWorkStatus,
                            is_online = effectiveOnline,
                            vacation = if (isUserVacation) true else (u.vacation ?: existing.vacation),
                            on_vacation = if (isUserVacation) true else (u.on_vacation ?: existing.on_vacation),
                            city = existing.city ?: u.resolvedCity,
                            isVerified = existing.isVerified ?: u.isApprovedUser
                        )
                    } else {
                        combined.add(
                            KodyarTechnician(
                                id = u.id,
                                user_id = u.id,
                                name = u.full_name.ifBlank { "تکنسین کدیار" },
                                phone = u.phone,
                                city = u.resolvedCity,
                                isVerified = u.isApprovedUser,
                                status = if (isUserVacation) "vacation" else (u.status ?: "active"),
                                work_status = if (isUserVacation) "vacation" else (u.work_status ?: "active"),
                                is_online = if (isUserVacation) false else (u.is_online ?: true),
                                vacation = if (isUserVacation) true else u.vacation,
                                on_vacation = if (isUserVacation) true else u.on_vacation,
                                completedOrders = 0,
                                bio = "تکنسین متخصص کدیار۲۴",
                                categories = u.categories ?: u.specialty ?: listOf("لوازم خانگی"),
                                rating = 5.0,
                                satisfactionRate = 100,
                                image = u.resolvedAvatarUrl,
                                imageUrl = u.resolvedAvatarUrl
                            )
                        )
                    }
                }
            }
            return combined.map { t ->
                t.copy(
                    name = t.resolvedName,
                    city = t.resolvedCity,
                    categories = t.resolvedCategories,
                    isVerified = t.resolvedIsVerified
                )
            }
        }

    val resolvedCommonProblems: List<KodyarCommonProblem>
        get() = commonProblems ?: common_problems ?: data?.commonProblems ?: data?.common_problems ?: emptyList()

    val resolvedCategoriesList: List<String>
        get() = categoriesList ?: categories_list ?: data?.categoriesList ?: data?.categories_list ?: emptyList()

    val resolvedBrandsList: List<String>
        get() = brandsList ?: brands_list ?: data?.brandsList ?: data?.brands_list ?: emptyList()

    val resolvedCitiesList: List<KodyarCity>
        get() = citiesList ?: cities_list ?: data?.citiesList ?: data?.cities_list ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class KodyarCity(
    val name: String? = null,
    val title: String? = null,
    val city: String? = null,
    val cityName: String? = null,
    val name_fa: String? = null,
    val nameFarsi: String? = null,
    val slug: String? = null,
    val regions: List<String>? = null
)

class KodyarCityAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): KodyarCity? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        if (reader.peek() == JsonReader.Token.STRING) {
            val nameValue = reader.nextString()
            return KodyarCity(name = nameValue)
        }
        reader.beginObject()
        var name: String? = null
        var title: String? = null
        var city: String? = null
        var cityName: String? = null
        var name_fa: String? = null
        var nameFarsi: String? = null
        var slug: String? = null
        var regions: MutableList<String>? = null

        val options = JsonReader.Options.of("name", "title", "city", "cityName", "name_fa", "nameFarsi", "slug", "regions", "regions_list")
        while (reader.hasNext()) {
            when (reader.selectName(options)) {
                0 -> name = reader.nextString()
                1 -> title = reader.nextString()
                2 -> city = reader.nextString()
                3 -> cityName = reader.nextString()
                4 -> name_fa = reader.nextString()
                5 -> nameFarsi = reader.nextString()
                6 -> slug = reader.nextString()
                7, 8 -> {
                    if (reader.peek() == JsonReader.Token.NULL) {
                        reader.nextNull<Unit>()
                    } else {
                        val list = mutableListOf<String>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == JsonReader.Token.STRING) {
                                list.add(reader.nextString())
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endArray()
                        regions = list
                    }
                }
                else -> {
                    reader.skipName()
                    reader.skipValue()
                }
            }
        }
        reader.endObject()
        return KodyarCity(name, title, city, cityName, name_fa, nameFarsi, slug, regions)
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: KodyarCity?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        if (value.name != null) writer.name("name").value(value.name)
        if (value.title != null) writer.name("title").value(value.title)
        if (value.city != null) writer.name("city").value(value.city)
        if (value.cityName != null) writer.name("cityName").value(value.cityName)
        if (value.name_fa != null) writer.name("name_fa").value(value.name_fa)
        if (value.nameFarsi != null) writer.name("nameFarsi").value(value.nameFarsi)
        if (value.slug != null) writer.name("slug").value(value.slug)
        if (value.regions != null) {
            writer.name("regions")
            writer.beginArray()
            for (r in value.regions) writer.value(r)
            writer.endArray()
        }
        writer.endObject()
    }
}

@JsonClass(generateAdapter = true)
data class KodyarErrorCode(
    val id: String? = null,
    val code: String? = null,
    @Json(name = "error_code") val error_code: String? = null,
    val brand: String? = null,
    val category: String? = null,
    @Json(name = "device_type") val device_type: String? = null,
    val title: String? = null,
    @Json(name = "error_title") val error_title: String? = null,
    val description: String? = null,
    val causes: Any? = null,
    val steps: Any? = null,
    val solutions: Any? = null,
    val precautions: Any? = null,
    @Json(name = "safety_precautions") val safety_precautions: Any? = null,
    @Json(name = "precaution") val precautions_alt: Any? = null,
    val hazardLevel: String? = null,
    @Json(name = "hazard_level") val hazard_level: String? = null,
    val videoUrl: String? = null,
    @Json(name = "video_url") val video_url: String? = null,
    val isApproved: Boolean? = null,
    @Json(name = "is_approved") val is_approved: Boolean? = null,
    val model: String? = null
) {
    val resolvedCode: String
        get() = (code ?: error_code ?: id ?: "").ifBlank { "N/A" }

    val resolvedCategory: String
        get() = (category ?: device_type ?: "").ifBlank { "عمومی" }

    val resolvedTitle: String
        get() = (title ?: error_title ?: description ?: "").ifBlank { "کد خطا $resolvedCode" }

    val resolvedSteps: Any?
        get() = steps ?: solutions

    val resolvedPrecautions: Any?
        get() = precautions ?: safety_precautions ?: precautions_alt

    val resolvedVideoUrl: String?
        get() {
            val candidate = listOfNotNull(video_url, videoUrl).firstOrNull { it.isNotBlank() }?.trim() ?: return null
            val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
            return when {
                candidate.startsWith("http://") || candidate.startsWith("https://") -> candidate
                candidate.startsWith("/") -> "$baseUrl$candidate"
                else -> "$baseUrl/$candidate"
            }
        }

    val deviceBrandModelSummary: String
        get() {
            val parts = mutableListOf<String>()
            val dev = (category ?: device_type ?: "").trim()
            val br = (brand ?: "").trim()
            val md = (model ?: "").trim()
            if (dev.isNotBlank()) parts.add(dev)
            if (br.isNotBlank()) parts.add(br)
            if (md.isNotBlank()) parts.add(md)
            return if (parts.isNotEmpty()) parts.joinToString(" · ") else "عمومی"
        }
}

@JsonClass(generateAdapter = true)
data class KodyarSparePart(
    val id: String? = null,
    val name: String? = null,
    val brand: String? = null,
    @Json(name = "compatible_brands") val compatible_brands: Any? = null,
    val category: String? = null,
    @Json(name = "device_category") val device_category: String? = null,
    @Json(name = "device_type") val device_type: String? = null,
    val model: String? = null,
    @Json(name = "device_model") val device_model: String? = null,
    val price: Double? = null,
    val stock: Int? = null,
    @Json(name = "stock_quantity") val stock_quantity: Int? = null,
    val image: String? = null,
    val imageUrl: String? = null,
    @Json(name = "image_url") val image_url: String? = null,
    val description: String? = null,
    @Json(name = "short_description") val short_description: String? = null,
    @Json(name = "technical_description") val technical_description: String? = null
) {
    val resolvedStock: Int
        get() = stock_quantity ?: stock ?: 0

    val isAvailable: Boolean
        get() = resolvedStock > 0

    val resolvedCategory: String
        get() = (device_category ?: category ?: device_type ?: "").trim()

    val resolvedBrand: String
        get() {
            if (!brand.isNullOrBlank()) return brand.trim()
            if (compatible_brands is List<*>) {
                return compatible_brands.filterNotNull().joinToString("، ")
            } else if (compatible_brands is String && compatible_brands.isNotBlank()) {
                return compatible_brands.trim()
            }
            return ""
        }

    val resolvedModel: String
        get() = (model ?: device_model ?: "").trim()

    val resolvedDescription: String
        get() = (short_description ?: technical_description ?: description ?: "").trim()

    val deviceAndBrandSummary: String
        get() {
            val parts = mutableListOf<String>()
            if (resolvedCategory.isNotBlank()) parts.add(resolvedCategory)
            if (resolvedBrand.isNotBlank()) parts.add(resolvedBrand)
            if (resolvedModel.isNotBlank()) parts.add("مدل $resolvedModel")
            return parts.joinToString(" • ")
        }
}

@JsonClass(generateAdapter = true)
data class KodyarTechnician(
    val id: String? = null,
    val user_id: String? = null,
    val name: String? = null,
    @Json(name = "full_name") val full_name: String? = null,
    val phone: String? = null,
    val city: String? = null,
    @Json(name = "cityName") val cityName: String? = null,
    @Json(name = "city_name") val city_name: String? = null,
    @Json(name = "activeLocation") val activeLocation: String? = null,
    @Json(name = "active_location") val active_location: String? = null,
    @Json(name = "location") val location: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "address") val address: String? = null,
    val isVerified: Any? = null,
    @Json(name = "is_verified") val is_verified: Any? = null,
    @Json(name = "is_approved") val is_approved: Any? = null,
    val completedOrders: Int? = null,
    @Json(name = "completed_orders") val completed_orders: Int? = null,
    val bio: String? = null,
    val categories: Any? = null,
    @Json(name = "specialty") val specialty: Any? = null,
    @Json(name = "specialties") val specialties: Any? = null,
    val rating: Double? = 5.0,
    val satisfactionRate: Int? = 100,
    @Json(name = "satisfaction_rate") val satisfaction_rate: Int? = 100,
    val ratingsCount: Int? = null,
    @Json(name = "ratings_count") val ratings_count: Int? = null,
    @Json(name = "reviews_count") val reviews_count: Int? = null,
    @Json(name = "reviewsCount") val reviewsCount: Int? = null,
    @Json(name = "skill_rating") val skill_rating: Double? = null,
    @Json(name = "skillRating") val skillRating: Double? = null,
    @Json(name = "ethics_rating") val ethics_rating: Double? = null,
    @Json(name = "ethicsRating") val ethicsRating: Double? = null,
    @Json(name = "punctuality_rating") val punctuality_rating: Double? = null,
    @Json(name = "punctualityRating") val punctualityRating: Double? = null,
    val reviews: List<TechnicianReview>? = null,
    val image: String? = null,
    val imageUrl: String? = null,
    @Json(name = "image_url") val image_url: String? = null,
    @Json(name = "profile_image") val profile_image: String? = null,
    @Json(name = "profile_image_url") val profile_image_url: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "avatar_url") val avatar_url: String? = null,
    @Json(name = "photo") val photo: String? = null,
    @Json(name = "picture") val picture: String? = null,
    @Json(name = "user_avatar") val user_avatar: String? = null,
    @Json(name = "user_image") val user_image: String? = null,
    val documents: Any? = null,
    val document_images: Any? = null,
    val uploaded_documents: Any? = null,
    val status: String? = null,
    @Json(name = "approval_status") val approval_status: String? = null,
    @Json(name = "is_online") val is_online: Any? = null,
    @Json(name = "isOnline") val isOnline: Any? = null,
    @Json(name = "work_status") val work_status: String? = null,
    @Json(name = "workStatus") val workStatus: String? = null,
    @Json(name = "vacation") val vacation: Any? = null,
    @Json(name = "on_vacation") val on_vacation: Any? = null
) {
    val isVacation: Boolean
        get() {
            val s = (status ?: "").trim().lowercase()
            val ws = (work_status ?: workStatus ?: "").trim().lowercase()
            val offlineKeywords = listOf("vacation", "on_leave", "مرخصی", "offline", "off", "آفلاین", "عدم فعالیت", "تعطیل")
            if (offlineKeywords.any { s.contains(it) || ws.contains(it) }) {
                return true
            }
            fun isTruthy(v: Any?): Boolean {
                if (v == null) return false
                if (v is Boolean) return v
                if (v is Number) return v.toInt() == 1
                val str = v.toString().trim().lowercase()
                return str == "1" || str == "true" || str == "on" || str == "active" || str == "yes" || str == "online" || str == "آنلاین"
            }
            fun isFalsy(v: Any?): Boolean {
                if (v == null) return false
                if (v is Boolean) return !v
                if (v is Number) return v.toInt() == 0
                val str = v.toString().trim().lowercase()
                return str == "0" || str == "false" || str == "off" || str == "inactive" || str == "no" || str == "offline" || str == "آفلاین" || str == "مرخصی"
            }
            if (isTruthy(vacation) || isTruthy(on_vacation)) return true
            if (isFalsy(is_online) || isFalsy(isOnline)) return true
            return false
        }
    val resolvedName: String
        get() = (name ?: full_name ?: "").ifBlank { "تکنسین کدیار" }

    val resolvedCity: String
        get() = listOfNotNull(city, cityName, city_name, activeLocation, active_location, location, province, address)
            .firstOrNull { it.isNotBlank() } ?: ""

    val resolvedCategories: List<String>
        get() {
            fun parseCatList(input: Any?): List<String>? {
                if (input == null) return null
                if (input is List<*>) {
                    val list = input.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() && it != "[]" }
                    if (list.isNotEmpty()) return list
                }
                if (input is String && input.isNotBlank()) {
                    val trimmed = input.trim()
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        val inner = trimmed.substring(1, trimmed.length - 1)
                        val items = inner.split(",").map { it.trim().removeSurrounding("\"").removeSurrounding("'") }.filter { it.isNotBlank() }
                        if (items.isNotEmpty()) return items
                    }
                    if (trimmed != "[]" && trimmed != "null") {
                        return listOf(trimmed)
                    }
                }
                return null
            }
            return parseCatList(specialties)
                ?: parseCatList(categories)
                ?: parseCatList(specialty)
                ?: listOf("لوازم خانگی")
        }

    val isSuspended: Boolean
        get() {
            val s = (status ?: "").trim().lowercase()
            val a = (approval_status ?: "").trim().lowercase()
            val suspendedKeywords = listOf("suspended", "blocked", "banned", "inactive", "rejected", "disabled", "معلق", "مسدود", "غیرفعال", "رد شده")
            return suspendedKeywords.any { s == it || a == it || s.contains("معلق") || s.contains("مسدود") || a.contains("معلق") || a.contains("مسدود") }
        }

    val resolvedIsVerified: Boolean
        get() {
            if (isSuspended) return false
            fun parseBool(input: Any?): Boolean? {
                if (input == null) return null
                if (input is Boolean) return input
                if (input is Number) return input.toInt() == 1
                val s = input.toString().trim().lowercase()
                return s == "1" || s == "true" || s == "approved" || s == "verified" || s == "تایید شده"
            }
            if (parseBool(is_approved) == true) return true
            val appStat = (approval_status ?: "").trim().lowercase()
            if (appStat == "approved" || appStat == "verified" || appStat == "تایید شده") return true
            val mainStat = (status ?: "").trim().lowercase()
            if (mainStat == "approved" || mainStat == "verified" || mainStat == "تایید شده") return true
            if (parseBool(isVerified) == true || parseBool(is_verified) == true) return true
            return false
        }

    val resolvedAvatarUrl: String?
        get() {
            val candidate = listOfNotNull(
                image, imageUrl, image_url, profile_image, profile_image_url,
                avatar, avatar_url, photo, picture, user_avatar, user_image,
                (uploaded_documents as? List<*>)?.firstOrNull()?.toString(),
                (document_images as? List<*>)?.firstOrNull()?.toString(),
                (documents as? List<*>)?.firstOrNull()?.toString()
            ).firstOrNull { it.isNotBlank() } ?: return null

            val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
            val trimmed = candidate.trim()
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:image/") -> trimmed
                trimmed.startsWith("/") -> "$baseUrl$trimmed"
                else -> "$baseUrl/$trimmed"
            }
        }

    val resolvedRating: Double
        get() = rating ?: 5.0

    val resolvedRatingsCount: Int
        get() = ratingsCount ?: ratings_count ?: reviews_count ?: reviewsCount ?: (if ((completedOrders ?: 0) > 0) completedOrders!! else 1)

    val resolvedSkillRating: Double
        get() = skill_rating ?: skillRating ?: rating ?: 5.0

    val resolvedEthicsRating: Double
        get() = ethics_rating ?: ethicsRating ?: rating ?: 5.0

    val resolvedPunctualityRating: Double
        get() = punctuality_rating ?: punctualityRating ?: rating ?: 5.0

    val resolvedReviews: List<TechnicianReview>
        get() = reviews ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class KodyarCommonProblem(
    val id: String? = null,
    val title: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val model: String? = null,
    val description: String? = null,
    val causes: Any? = null,
    val steps: Any? = null,
    val video_url: String? = null,
    val videoUrl: String? = null
) {
    val resolvedVideoUrl: String?
        get() {
            val candidate = listOfNotNull(video_url, videoUrl).firstOrNull { it.isNotBlank() }?.trim() ?: return null
            val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
            return when {
                candidate.startsWith("http://") || candidate.startsWith("https://") -> candidate
                candidate.startsWith("/") -> "$baseUrl$candidate"
                else -> "$baseUrl/$candidate"
            }
        }
}

enum class UserRole {
    CUSTOMER,
    TECHNICIAN;

    companion object {
        fun fromString(roleStr: String?): UserRole {
            val r = (roleStr ?: "").trim().lowercase()
            return when (r) {
                "technician", "tech", "repairman", "تکنسین", "سرویس‌کار" -> TECHNICIAN
                else -> CUSTOMER
            }
        }
    }
}

enum class TechnicianApprovalStatus {
    PENDING,
    APPROVED,
    SUSPENDED
}

@JsonClass(generateAdapter = true)
data class KodyarUser(
    val id: String = "",
    val full_name: String = "",
    val phone: String = "",
    val subscription: KodyarSubscription? = null,
    val is_premium: Boolean? = null,
    @Json(name = "has_active_subscription") val has_active_subscription: Boolean? = null,
    @Json(name = "hasActiveSubscription") val hasActiveSubscription: Boolean? = null,
    @Json(name = "is_active") val is_active: Boolean? = null,
    @Json(name = "isActive") val isActive: Boolean? = null,
    @Json(name = "is_vip") val is_vip: Boolean? = null,
    @Json(name = "vip") val vip: Boolean? = null,
    @Json(name = "has_subscription") val has_subscription: Boolean? = null,
    @Json(name = "hasSubscription") val hasSubscription: Boolean? = null,
    @Json(name = "subscription_status") val subscription_status: String? = null,
    @Json(name = "subscription_name") val subscription_name: String? = null,
    @Json(name = "plan") val plan: String? = null,
    @Json(name = "plan_name") val plan_name: String? = null,
    val expiry_date: String? = null,
    @Json(name = "expiryDate") val expiryDate: String? = null,
    val subscription_expiry: String? = null,
    @Json(name = "expires_at") val expires_at: String? = null,
    @Json(name = "expire_at") val expire_at: String? = null,
    @Json(name = "end_date") val end_date: String? = null,
    val role: String? = "customer",
    val city: String? = null,
    @Json(name = "cityName") val cityName: String? = null,
    @Json(name = "city_name") val city_name: String? = null,
    @Json(name = "location") val location: String? = null,
    @Json(name = "activeLocation") val activeLocation: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "address") val address: String? = null,
    val categories: List<String>? = null,
    @Json(name = "specialty") val specialty: List<String>? = null,
    val district: String? = null,
    val is_approved: Boolean? = null,
    val approval_status: String? = null,
    val uploaded_documents: List<String>? = null,
    val status: String? = null,
    val is_verified: Boolean? = null,
    val isVerified: Boolean? = null,
    val wallet_balance: Double? = null,
    @Json(name = "walletBalance") val walletBalance: Double? = null,
    val debt: Double? = null,
    val commission_debt: Double? = null,
    @Json(name = "is_debt") val is_debt: Boolean? = null,
    @Json(name = "isDebt") val isDebt: Boolean? = null,
    @Json(name = "has_debt") val has_debt: Boolean? = null,
    @Json(name = "completed_orders_count") val completed_orders_count: Int? = null,
    val image: String? = null,
    val imageUrl: String? = null,
    @Json(name = "image_url") val image_url: String? = null,
    @Json(name = "profile_image") val profile_image: String? = null,
    @Json(name = "profile_image_url") val profile_image_url: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "avatar_url") val avatar_url: String? = null,
    @Json(name = "photo") val photo: String? = null,
    @Json(name = "picture") val picture: String? = null,
    @Json(name = "user_avatar") val user_avatar: String? = null,
    @Json(name = "user_image") val user_image: String? = null,
    val documents: List<String>? = null,
    val document_images: List<String>? = null,
    @Json(name = "is_online") val is_online: Any? = null,
    @Json(name = "isOnline") val isOnline: Any? = null,
    @Json(name = "work_status") val work_status: String? = null,
    @Json(name = "workStatus") val workStatus: String? = null,
    @Json(name = "vacation") val vacation: Any? = null,
    @Json(name = "on_vacation") val on_vacation: Any? = null,
    @Json(name = "technician_status") val technician_status: String? = null
) {
    val isVacation: Boolean
        get() {
            val s = (status ?: "").trim().lowercase()
            val ws = (work_status ?: workStatus ?: technician_status ?: "").trim().lowercase()
            val offlineKeywords = listOf("vacation", "on_leave", "مرخصی", "offline", "off", "آفلاین", "عدم فعالیت", "تعطیل")
            if (offlineKeywords.any { s.contains(it) || ws.contains(it) }) {
                return true
            }
            fun isTruthy(v: Any?): Boolean {
                if (v == null) return false
                if (v is Boolean) return v
                if (v is Number) return v.toInt() == 1
                val str = v.toString().trim().lowercase()
                return str == "1" || str == "true" || str == "on" || str == "active" || str == "yes" || str == "online" || str == "آنلاین"
            }
            fun isFalsy(v: Any?): Boolean {
                if (v == null) return false
                if (v is Boolean) return !v
                if (v is Number) return v.toInt() == 0
                val str = v.toString().trim().lowercase()
                return str == "0" || str == "false" || str == "off" || str == "inactive" || str == "no" || str == "offline" || str == "آفلاین" || str == "مرخصی"
            }
            if (isTruthy(vacation) || isTruthy(on_vacation)) return true
            if (isFalsy(is_online) || isFalsy(isOnline)) return true
            return false
        }

    val resolvedCity: String
        get() = listOfNotNull(city, cityName, city_name, activeLocation, location, province, address, district)
            .firstOrNull { it.isNotBlank() } ?: "اراک"

    val isSuspended: Boolean
        get() {
            val s = (status ?: "").trim().lowercase()
            val a = (approval_status ?: "").trim().lowercase()
            val suspendedKeywords = listOf("suspended", "blocked", "banned", "inactive", "rejected", "disabled", "معلق", "مسدود", "غیرفعال", "رد شده")
            return suspendedKeywords.any { s == it || a == it || s.contains("معلق") || s.contains("مسدود") || a.contains("معلق") || a.contains("مسدود") }
        }

    val userRole: UserRole
        get() = UserRole.fromString(role)

    val isApprovedUser: Boolean
        get() {
            if (userRole != UserRole.TECHNICIAN) return true
            // If user is suspended/blocked by admin, they are NEVER approved
            if (isSuspended) return false
            // Technicians check: if explicitly approved or verified in any server field
            fun parseBool(input: Any?): Boolean? {
                if (input == null) return null
                if (input is Boolean) return input
                if (input is Number) return input.toInt() == 1
                val s = input.toString().trim().lowercase()
                return s == "1" || s == "true" || s == "approved" || s == "verified" || s == "تایید شده" || s == "active"
            }
            if (parseBool(is_approved) == true) return true
            if (parseBool(is_verified) == true) return true
            if (parseBool(isVerified) == true) return true
            val appStat = (approval_status ?: "").trim().lowercase()
            if (appStat == "approved" || appStat == "verified" || appStat == "تایید شده" || appStat == "active") return true
            val mainStat = (status ?: "").trim().lowercase()
            if (mainStat == "approved" || mainStat == "verified" || mainStat == "تایید شده" || mainStat == "active") return true
            return false
        }

    val isTechnicianUser: Boolean
        get() = userRole == UserRole.TECHNICIAN && isApprovedUser

    val resolvedWalletBalance: Double
        get() = wallet_balance ?: walletBalance ?: 0.0

    val resolvedDebt: Double
        get() {
            val directDebt = debt ?: commission_debt
            if (directDebt != null && directDebt > 0) return directDebt
            val balance = resolvedWalletBalance
            return if (balance < 0) kotlin.math.abs(balance) else 0.0
        }

    val hasCommissionDebt: Boolean
        get() {
            if (is_debt == true || isDebt == true || has_debt == true) return true
            return resolvedWalletBalance < 0 || resolvedDebt > 0
        }

    val isFirstOrderFree: Boolean
        get() = (completed_orders_count ?: 0) == 0 && !hasCommissionDebt

    val resolvedAvatarUrl: String?
        get() {
            val candidate = listOfNotNull(
                image, imageUrl, image_url, profile_image, profile_image_url,
                avatar, avatar_url, photo, picture, user_avatar, user_image,
                uploaded_documents?.firstOrNull(),
                document_images?.firstOrNull(),
                documents?.firstOrNull()
            ).firstOrNull { it.isNotBlank() } ?: return null

            val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
            val trimmed = candidate.trim()
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:image/") -> trimmed
                trimmed.startsWith("/") -> "$baseUrl$trimmed"
                else -> "$baseUrl/$trimmed"
            }
        }
}

@JsonClass(generateAdapter = true)
data class KodyarSubscription(
    val is_premium: Boolean = false,
    @Json(name = "is_active") val is_active: Boolean? = null,
    @Json(name = "isActive") val isActive: Boolean? = null,
    @Json(name = "active") val active: Boolean? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "plan") val plan: String? = null,
    @Json(name = "plan_name") val plan_name: String? = null,
    @Json(name = "subscription_name") val subscription_name: String? = null,
    val expiry_date: String? = null,
    @Json(name = "expiryDate") val expiryDate: String? = null,
    @Json(name = "expires_at") val expires_at: String? = null,
    @Json(name = "expire_at") val expire_at: String? = null,
    @Json(name = "end_date") val end_date: String? = null,
    @Json(name = "subscription_expiry") val subscription_expiry: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarResponse(
    val status: String? = "ok",
    val success: Boolean? = null,
    val token: String? = null,
    val session_token: String? = null,
    val user: KodyarUser? = null,
    val subscription: KodyarSubscription? = null,
    @Json(name = "is_premium") val is_premium: Boolean? = null,
    @Json(name = "is_active") val is_active: Boolean? = null,
    @Json(name = "isActive") val isActive: Boolean? = null,
    @Json(name = "expiry_date") val expiry_date: String? = null,
    @Json(name = "expires_at") val expires_at: String? = null,
    val error: String? = null,
    val message: String? = null,
    val otp: String? = null,
    val order_id: Any? = null,
    val orderId: Any? = null,
    val payment_url: String? = null,
    val paymentUrl: String? = null,
    val shamsi_date: String? = null,
    val shamsiDate: String? = null,
    val repairs: List<KodyarRepairOrder>? = null,
    val repair_requests: List<KodyarRepairOrder>? = null,
    val orders: List<KodyarRepairOrder>? = null,
    val purchases: List<PartPurchaseOrder>? = null,
    val part_orders: List<PartPurchaseOrder>? = null,
    val store_orders: List<PartPurchaseOrder>? = null,
    val data: List<KodyarRepairOrder>? = null,
    val error_count: Int? = null,
    val problem_count: Int? = null
)

@JsonClass(generateAdapter = true)
data class KodyarRepairOrder(
    val id: String? = null,
    val order_id: String? = null,
    val orderId: String? = null,
    val user_id: String? = null,
    val user_name: String? = null,
    val user_phone: String? = null,
    val customer_name: String? = null,
    val customerName: String? = null,
    val customer_phone: String? = null,
    val customerPhone: String? = null,
    val technician_id: String? = null,
    val technicianId: String? = null,
    val technician_name: String? = null,
    val technicianName: String? = null,
    val technician_phone: String? = null,
    val technicianPhone: String? = null,
    val scheduled_date: String? = null,
    val scheduledDate: String? = null,
    val scheduled_time: String? = null,
    val scheduledTime: String? = null,
    val device_brand: String? = null,
    val brand: String? = null,
    val device_category: String? = null,
    val category: String? = null,
    val appliance: String? = null,
    val model: String? = null,
    val error_code: String? = null,
    val errorCode: String? = null,
    val description: String? = null,
    val problem_description: String? = null,
    val problemDescription: String? = null,
    val city: String? = null,
    val region: String? = null,
    val address: String? = null,
    val full_address: String? = null,
    val fullAddress: String? = null,
    val postal_code: String? = null,
    val postalCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val location_url: String? = null,
    val locationUrl: String? = null,
    val address_note: String? = null,
    val addressNote: String? = null,
    val status: String? = null, // pending, assigned, accepted, in_progress, ongoing, completed, done, cancelled, rejected
    val status_label_fa: String? = null,
    val statusLabelFa: String? = null,
    val tracking_code: String? = null,
    val trackingCode: String? = null,
    val created_at: String? = null,
    val shamsi_date: String? = null,
    val shamsiDate: String? = null,
    val estimated_cost: Long? = null,
    val estimatedCost: Long? = null,
    val price: Long? = null,
    val cost: Long? = null,
    val amount: Long? = null,
    val rating: Double? = null,
    val is_rated: Boolean? = null,
    val isRated: Boolean? = null,
    val rating_comment: String? = null,
    val review_comment: String? = null,
    val feedback: String? = null,
    val skill_rating: Double? = null,
    val ethics_rating: Double? = null,
    val punctuality_rating: Double? = null
) {
    val resolvedOrderId: String
        get() = listOfNotNull(order_id, orderId, id, tracking_code, trackingCode).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedCustomerName: String
        get() = listOfNotNull(customer_name, customerName, user_name).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedCustomerPhone: String
        get() = listOfNotNull(customer_phone, customerPhone, user_phone).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedTechnicianName: String
        get() = listOfNotNull(technician_name, technicianName).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedTechnicianPhone: String
        get() = listOfNotNull(technician_phone, technicianPhone).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedScheduledDate: String
        get() = listOfNotNull(scheduled_date, scheduledDate).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedScheduledTime: String
        get() = listOfNotNull(scheduled_time, scheduledTime).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedScheduledInfo: String
        get() {
            val d = resolvedScheduledDate
            val t = resolvedScheduledTime
            if (d.isNotBlank() || t.isNotBlank()) {
                return when {
                    d.isNotBlank() && t.isNotBlank() -> if (d.contains("ساعت")) "$d (تکمیلی: $t)" else "تاریخ: $d - ساعت: $t"
                    d.isNotBlank() -> if (d.contains("ساعت") || d.startsWith("روز") || d.startsWith("تاریخ")) d else "تاریخ: $d"
                    t.isNotBlank() -> "ساعت: $t"
                    else -> ""
                }
            }
            val raw = listOfNotNull(description, problem_description, problemDescription).firstOrNull { it.isNotBlank() } ?: ""
            for (line in raw.lines()) {
                val tr = line.trim()
                if (tr.startsWith("زمان پیشنهادی مراجعه کارشناس:") || tr.startsWith("زمان پیشنهادی:") || tr.startsWith("زمان مراجعه:")) {
                    return tr.substringAfter(":").trim()
                }
                if (tr.startsWith("[ساعت ثبت سفارش از زمان گوشی:") || tr.startsWith("ساعت ثبت سفارش از زمان گوشی:")) {
                    return tr.removePrefix("[").removeSuffix("]").trim()
                }
            }
            return ""
        }

    val resolvedDescription: String
        get() = listOfNotNull(description, problem_description, problemDescription).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedCategory: String
        get() {
            val direct = listOfNotNull(category, device_category, appliance).firstOrNull { it.isNotBlank() && it != "عمومی" }
            if (direct != null) return direct
            val raw = listOfNotNull(description, problem_description, problemDescription).firstOrNull { it.isNotBlank() } ?: ""
            for (line in raw.lines()) {
                val tr = line.trim()
                if (tr.startsWith("دستگاه و برند:") || tr.startsWith("دستگاه:") || tr.startsWith("نوع دستگاه:")) {
                    return tr.substringAfter(":").trim()
                }
            }
            return listOfNotNull(category, device_category, appliance).firstOrNull { it.isNotBlank() } ?: ""
        }

    val resolvedBrand: String
        get() = listOfNotNull(brand, device_brand).firstOrNull { it.isNotBlank() && it != "عمومی" } ?: ""

    val resolvedDate: String
        get() = listOfNotNull(shamsi_date, shamsiDate, created_at).firstOrNull { it.isNotBlank() } ?: ""

    /** آدرس کامل پستی مشتری، با پشتیبانی از هر دو نام‌گذاری سرور و فال‌بک از متن سفارش. */
    val resolvedAddress: String
        get() {
            val direct = listOfNotNull(full_address, fullAddress, address).firstOrNull { it.isNotBlank() }
            if (!direct.isNullOrBlank()) return direct
            val raw = listOfNotNull(description, problem_description, problemDescription).firstOrNull { it.isNotBlank() } ?: ""
            for (line in raw.lines()) {
                val tr = line.trim()
                if (tr.startsWith("آدرس محل:") || tr.startsWith("آدرس:") || tr.startsWith("آدرس دقیق:")) {
                    return tr.substringAfter(":").trim()
                }
            }
            return ""
        }

    /** کد پستی ۱۰ رقمی محل سفارش. */
    val resolvedPostalCode: String
        get() = listOfNotNull(postal_code, postalCode).firstOrNull { it.isNotBlank() } ?: ""

    /** توضیح مسیر و نشانه محل. */
    val resolvedAddressNote: String
        get() = listOfNotNull(address_note, addressNote).firstOrNull { it.isNotBlank() } ?: ""

    /** مختصات ثبت‌شده توسط مشتری، در صورت وجود یا استخراج از لینک/متن سفارش. */
    val resolvedCoordinates: Pair<Double, Double>?
        get() {
            val la = latitude ?: lat
            val ln = longitude ?: lng
            if (la != null && ln != null && la != 0.0 && ln != 0.0) return la to ln

            val candidateSources = listOfNotNull(
                location_url,
                locationUrl,
                full_address,
                fullAddress,
                address,
                problem_description,
                description
            )
            val regex = Regex("""(-?\d{1,2}\.\d{3,})\s*[,/]\s*(-?\d{1,3}\.\d{3,})""")
            for (text in candidateSources) {
                if (text.isNotBlank()) {
                    val match = regex.find(text)
                    if (match != null) {
                        val parsedLat = match.groupValues[1].toDoubleOrNull()
                        val parsedLng = match.groupValues[2].toDoubleOrNull()
                        if (parsedLat != null && parsedLng != null && parsedLat in -90.0..90.0 && parsedLng in -180.0..180.0) {
                            return parsedLat to parsedLng
                        }
                    }
                }
            }
            return null
        }

    /** لینک نقشه محل سفارش؛ اگر سرور نداشته باشد از مختصات ساخته می‌شود. */
    val resolvedLocationUrl: String
        get() = listOfNotNull(location_url, locationUrl).firstOrNull { it.isNotBlank() }
            ?: resolvedCoordinates?.let { "https://maps.google.com/?q=${it.first},${it.second}" }
            ?: ""

    val isOrderRated: Boolean
        get() = is_rated == true || isRated == true || (rating != null && rating > 0.0)

    val resolvedIsRated: Boolean
        get() = isOrderRated

    val resolvedOrderRating: Double
        get() = rating ?: 5.0

    val resolvedRating: Double
        get() = resolvedOrderRating

    val resolvedRatingComment: String
        get() = listOfNotNull(rating_comment, review_comment, feedback).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedSkillRating: Double
        get() = skill_rating ?: rating ?: 5.0

    val resolvedEthicsRating: Double
        get() = ethics_rating ?: rating ?: 5.0

    val resolvedPunctualityRating: Double
        get() = punctuality_rating ?: rating ?: 5.0
}

@JsonClass(generateAdapter = true)
data class KodyarSubscriptionPlan(
    val id: String = "",
    val name: String = "",
    val duration_days: Int = 0,
    val price: Double = 0.0,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarPlansResponse(
    val status: String = "ok",
    val plans: List<KodyarSubscriptionPlan>? = null
)

@JsonClass(generateAdapter = true)
data class PartPurchaseOrder(
    val id: String = "",
    val order_id: String? = null,
    val orderId: String? = null,
    val partId: String = "",
    @Json(name = "part_id") val part_id: String? = null,
    val partName: String = "",
    @Json(name = "part_name") val part_name: String? = null,
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    @Json(name = "unit_price") val unit_price: Double? = null,
    val totalPrice: Double = 0.0,
    @Json(name = "total_price") val total_price: Double? = null,
    val address: String = "",
    val notes: String = "",
    val dateStr: String = "",
    val status: String = "pending", // pending, processing, sent, shipped, completed, delivered, cancelled, rejected
    val status_label_fa: String? = null,
    val statusLabelFa: String? = null,
    val tracking_code: String? = null,
    val trackingCode: String? = null,
    val shipping_code: String? = null,
    val shippingCode: String? = null,
    val paymentUrl: String? = null,
    @Json(name = "payment_url") val payment_url: String? = null,
    val shamsiDate: String? = null,
    @Json(name = "shamsi_date") val shamsi_date: String? = null,
    val created_at: String? = null,
    val createdAt: String? = null
) {
    val resolvedId: String
        get() = listOfNotNull(order_id, orderId, id).firstOrNull { it.isNotBlank() } ?: id

    val resolvedPartName: String
        get() = listOfNotNull(part_name, partName).firstOrNull { it.isNotBlank() } ?: (if (partName.isNotBlank()) partName else "قطعه یدکی")

    val resolvedTotalPrice: Double
        get() = total_price ?: (if (totalPrice > 0) totalPrice else ((unit_price ?: unitPrice) * quantity))

    val resolvedTrackingCode: String
        get() = listOfNotNull(tracking_code, trackingCode, shipping_code, shippingCode).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedStatusLabelFa: String?
        get() = listOfNotNull(status_label_fa, statusLabelFa).firstOrNull { it.isNotBlank() }

    val resolvedDate: String
        get() = listOfNotNull(shamsi_date, shamsiDate, dateStr, created_at, createdAt).firstOrNull { it.isNotBlank() } ?: ""
}

@JsonClass(generateAdapter = true)
data class RepairRequest(
    @Json(name = "customer_name") val customer_name: String? = null,
    @Json(name = "customerName") val customerName: String? = null,
    @Json(name = "customer_phone") val customer_phone: String? = null,
    @Json(name = "customerPhone") val customerPhone: String? = null,
    val city: String = "",
    val region: String? = null,
    val category: String? = null,
    val appliance: String? = null,
    @Json(name = "problem_description") val problem_description: String? = null,
    @Json(name = "description") val description: String? = null,
    val address: String? = null,
    @Json(name = "full_address") val full_address: String? = null,
    @Json(name = "postal_code") val postal_code: String? = null,
    @Json(name = "postalCode") val postalCode: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "location_url") val location_url: String? = null,
    @Json(name = "address_note") val address_note: String? = null,
    @Json(name = "scheduled_date") val scheduled_date: String? = null,
    @Json(name = "scheduledDate") val scheduledDate: String? = null,
    val brand: String = "",
    val model: String? = null,
    @Json(name = "error_code") val error_code: String? = null,
    @Json(name = "errorCode") val errorCode: String? = null,
    @Json(name = "technician_id") val technician_id: String? = null,
    @Json(name = "technicianId") val technicianId: String? = null
)

@JsonClass(generateAdapter = true)
data class PurchasePartRequest(
    @Json(name = "customer_name") val customer_name: String? = null,
    @Json(name = "customerName") val customerName: String? = null,
    @Json(name = "customer_phone") val customer_phone: String? = null,
    @Json(name = "customerPhone") val customerPhone: String? = null,
    @Json(name = "part_id") val part_id: String = "",
    @Json(name = "partId") val partId: String = "",
    @Json(name = "part_name") val part_name: String = "",
    @Json(name = "partName") val partName: String = "",
    val quantity: Int = 1,
    @Json(name = "unit_price") val unit_price: Double = 0.0,
    @Json(name = "unitPrice") val unitPrice: Double = 0.0,
    @Json(name = "total_price") val total_price: Double = 0.0,
    @Json(name = "totalPrice") val totalPrice: Double = 0.0,
    val address: String? = null,
    val city: String? = null,
    val notes: String? = null,
    @Json(name = "user_phone") val user_phone: String? = null,
    @Json(name = "payment_method") val payment_method: String? = "online",
    @Json(name = "payment_type") val payment_type: String? = "part_purchase",
    @Json(name = "type") val type: String? = "part_purchase",
    @Json(name = "status") val status: String? = "pending"
)

@JsonClass(generateAdapter = true)
data class OrderStatusUpdateRequest(
    @Json(name = "order_id") val order_id: String? = null,
    @Json(name = "orderId") val orderId: String? = null,
    val status: String = "", // accepted | in_progress | completed | rejected
    @Json(name = "technician_id") val technician_id: String? = null,
    @Json(name = "technicianId") val technicianId: String? = null,
    val amount: Long? = null,
    @Json(name = "estimated_cost") val estimated_cost: Long? = null,
    val estimatedCost: Long? = null,
    val action: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    @Json(name = "full_name") val fullName: String? = null,
    val name: String? = fullName,
    val city: String? = null,
    val district: String? = null,
    val status: String? = null,
    @Json(name = "work_status") val work_status: String? = null,
    @Json(name = "is_online") val is_online: Boolean? = null,
    val vacation: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class KodyarChatRequest(
    val message: String,
    @Json(name = "device_type") val device_type: String? = null,
    val brand: String? = null,
    val history: List<Map<String, String>>? = null
)

@JsonClass(generateAdapter = true)
data class KodyarChatResponse(
    val reply: String? = null,
    val status: String? = null,
    val suggestions: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class BazaarPaymentVerificationRequest(
    val sku: String? = null,
    @Json(name = "productId") val productId: String? = sku,
    @Json(name = "plan") val plan: String? = sku,
    val purchaseToken: String? = null,
    @Json(name = "purchase_token") val purchase_token: String? = purchaseToken,
    val packageName: String? = "com.example",
    @Json(name = "package_name") val package_name: String? = packageName,
    val price: Long? = null,
    @Json(name = "amount") val amount: Long? = price,
    val userId: String? = null,
    @Json(name = "user_id") val user_id: String? = userId,
    val phone: String? = null,
    val orderId: String? = null
)

@JsonClass(generateAdapter = true)
data class PartOrderPayload(
    val partId: String? = null,
    @Json(name = "part_id") val part_id: String? = partId,
    val quantity: Int = 1,
    val totalPrice: Double? = null,
    @Json(name = "total_price") val total_price: Double? = totalPrice,
    val address: String? = null,
    val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class UploadFileRequest(
    val name: String = "",
    val fileData: String = "" // data:image/jpeg;base64,...
)

@JsonClass(generateAdapter = true)
data class UploadFileResponse(
    val success: Boolean? = null,
    val url: String? = null,
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val error: String? = null
)

// 🎫 مدل‌های سیستم تیکت‌های پشتیبانی (Support Tickets)
@JsonClass(generateAdapter = true)
data class TicketResponse(
    val success: Boolean? = true,
    val status: String? = null,
    val tickets: List<TicketModel>? = null,
    val ticket: TicketModel? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class TicketModel(
    val id: String = "",
    @Json(name = "user_id") val user_id: String? = null,
    val subject: String = "",
    val category: String? = null,
    val department: String? = null,
    val status: String = "pending", // "pending", "answered", "closed", "open", "in_progress", "reopened", "resolved", etc.
    @Json(name = "ticket_status") val ticket_status: String? = null,
    val message: String = "",
    val createdAt: String? = null,
    @Json(name = "created_at") val created_at: String? = null,
    val updatedAt: String? = null,
    @Json(name = "updated_at") val updated_at: String? = null,
    val replies: List<TicketReplyModel>? = emptyList()
) {
    val resolvedDate: String
        get() = createdAt ?: created_at ?: updatedAt ?: updated_at ?: ""
        
    val resolvedStatus: String
        get() = (ticket_status ?: status).trim()

    val statusDisplay: String
        get() {
            val s = resolvedStatus.lowercase()
            return when {
                s.contains("حل") || s.contains("resolved") || s.contains("solved") || s.contains("done") -> "رسیدگی و بسته شد"
                s.contains("مجدد") || s.contains("reopen") || s.contains("review") || s.contains("بررسی مجدد") -> "بررسی مجدد"
                s.contains("بست") || s.contains("close") -> "رسیدگی و بسته شد"
                s.contains("پاسخ") || s.contains("answered") || s.contains("replied") -> "پاسخ داده شد"
                s.contains("بررسی") || s.contains("open") || s.contains("progress") || s.contains("ongoing") || s.contains("در حال") -> "در حال بررسی توسط کارشناس"
                s.contains("انتظار") || s.contains("pending") || s.contains("waiting") -> "در حال بررسی توسط کارشناس"
                resolvedStatus.isNotBlank() -> resolvedStatus
                else -> "در حال بررسی توسط کارشناس"
            }
        }
        
    val categoryDisplay: String
        get() = when (category?.lowercase()) {
            "technical", "فنی" -> "فنی و عیب‌یابی"
            "sales", "فروش" -> "فروش و قطعات"
            "accounting", "مالی" -> "مالی و اشتراک"
            "general", "عمومی" -> "عمومی و پیشنهادات"
            else -> category ?: "عمومی"
        }
}

@JsonClass(generateAdapter = true)
data class TicketReplyModel(
    val id: String? = null,
    @Json(name = "sender_type") val sender_type: String? = null, // "admin" یا "user"
    @Json(name = "user_id") val user_id: String? = null,
    val message: String = "",
    @Json(name = "created_at") val created_at: String? = null,
    val createdAt: String? = null
) {
    val resolvedDate: String
        get() = createdAt ?: created_at ?: ""
        
    val isAdmin: Boolean
        get() = sender_type.equals("admin", ignoreCase = true) || user_id.equals("admin", ignoreCase = true)
}

@JsonClass(generateAdapter = true)
data class CreateTicketRequest(
    val subject: String,
    val category: String,
    val department: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class SendReplyRequest(
    val message: String
)

@JsonClass(generateAdapter = true)
data class TechnicianStatusUpdateRequest(
    val technicianId: String? = null,
    @Json(name = "technician_id") val technician_id: String? = technicianId,
    val status: String // "active" or "vacation"
)

@JsonClass(generateAdapter = true)
data class AcceptOrderApiRequest(
    val orderId: String? = null,
    @Json(name = "order_id") val order_id: String? = orderId,
    val technicianId: String? = null,
    @Json(name = "technician_id") val technician_id: String? = technicianId,
    val technicianPhone: String? = null,
    @Json(name = "technician_phone") val technician_phone: String? = technicianPhone
)

@JsonClass(generateAdapter = true)
data class TechnicianReview(
    val id: String? = null,
    val order_id: String? = null,
    val orderId: String? = null,
    val technician_id: String? = null,
    val technicianId: String? = null,
    val customer_name: String? = null,
    val customerName: String? = null,
    val customer_phone: String? = null,
    val rating: Double = 5.0,
    val skill_rating: Double = 5.0,
    val ethics_rating: Double = 5.0,
    val punctuality_rating: Double = 5.0,
    val comment: String? = null,
    val created_at: String? = null,
    val shamsi_date: String? = null
) {
    val resolvedCustomerName: String
        get() = listOfNotNull(customer_name, customerName).firstOrNull { it.isNotBlank() } ?: "مشتری کدیار"

    val resolvedComment: String
        get() = comment?.trim() ?: ""

    val resolvedDate: String
        get() = listOfNotNull(shamsi_date, created_at).firstOrNull { it.isNotBlank() } ?: ""
}

@JsonClass(generateAdapter = true)
data class RateTechnicianOrderRequest(
    val order_id: String? = null,
    val orderId: String? = null,
    val technician_id: String? = null,
    val technicianId: String? = null,
    val rating: Double = 5.0,
    val skill_rating: Double = 5.0,
    val ethics_rating: Double = 5.0,
    val punctuality_rating: Double = 5.0,
    val comment: String? = null,
    val customer_name: String? = null,
    val customer_phone: String? = null
)





