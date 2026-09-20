package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.KodyarCommonProblem
import com.example.data.model.KodyarErrorCode
import com.example.data.model.KodyarSparePart
import com.example.data.model.KodyarTechnician

@Entity(tableName = "cached_error_codes")
data class ErrorCodeEntity(
    @PrimaryKey val id: String,
    val code: String?,
    val brand: String?,
    val category: String?,
    val title: String?,
    val description: String?,
    val causesRaw: String?,
    val stepsRaw: String?,
    val hazardLevel: String?,
    val videoUrl: String?,
    val isApproved: Boolean?,
    val model: String?
)

fun KodyarErrorCode.toEntity(): ErrorCodeEntity {
    val finalId = id ?: "${brand}_${category}_${code}"
    val causesStr = when (val c = causes) {
        is List<*> -> c.filterNotNull().joinToString("|||")
        is String -> c
        else -> c?.toString()
    }
    val stepsStr = when (val s = steps) {
        is List<*> -> s.filterNotNull().joinToString("|||")
        is String -> s
        else -> s?.toString()
    }
    return ErrorCodeEntity(
        id = finalId,
        code = code,
        brand = brand,
        category = category,
        title = title,
        description = description,
        causesRaw = causesStr,
        stepsRaw = stepsStr,
        hazardLevel = hazardLevel,
        videoUrl = video_url ?: videoUrl,
        isApproved = isApproved,
        model = model
    )
}

fun ErrorCodeEntity.toDomain(): KodyarErrorCode {
    val causesList = causesRaw?.let { raw ->
        if (raw.contains("|||")) raw.split("|||") else listOf(raw)
    }
    val stepsList = stepsRaw?.let { raw ->
        if (raw.contains("|||")) raw.split("|||") else listOf(raw)
    }
    return KodyarErrorCode(
        id = id,
        code = code,
        brand = brand,
        category = category,
        title = title,
        description = description,
        causes = causesList ?: causesRaw,
        steps = stepsList ?: stepsRaw,
        hazardLevel = hazardLevel,
        videoUrl = videoUrl,
        isApproved = isApproved,
        model = model
    )
}

@Entity(tableName = "cached_spare_parts")
data class SparePartEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val brand: String?,
    val category: String? = null,
    val model: String? = null,
    val description: String? = null,
    val price: Double?,
    val stock: Int?,
    val image: String?,
    val imageUrl: String?
)

fun KodyarSparePart.toEntity(): SparePartEntity {
    val finalId = id ?: "${name}_${brand}_${price}"
    return SparePartEntity(
        id = finalId,
        name = name,
        brand = resolvedBrand,
        category = resolvedCategory,
        model = resolvedModel,
        description = resolvedDescription,
        price = price,
        stock = stock,
        image = image,
        imageUrl = imageUrl
    )
}

fun SparePartEntity.toDomain(): KodyarSparePart {
    return KodyarSparePart(
        id = id,
        name = name,
        brand = brand,
        category = category,
        model = model,
        description = description,
        price = price,
        stock = stock,
        image = image,
        imageUrl = imageUrl
    )
}

@Entity(tableName = "cached_common_problems")
data class CommonProblemEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val brand: String?,
    val category: String?,
    val description: String?,
    val causesRaw: String?,
    val stepsRaw: String?
)

fun KodyarCommonProblem.toEntity(): CommonProblemEntity {
    val finalId = id ?: "${brand}_${category}_${title}"
    val causesStr = when (val c = causes) {
        is List<*> -> c.filterNotNull().joinToString("|||")
        is String -> c
        else -> c?.toString()
    }
    val stepsStr = when (val s = steps) {
        is List<*> -> s.filterNotNull().joinToString("|||")
        is String -> s
        else -> s?.toString()
    }
    return CommonProblemEntity(
        id = finalId,
        title = title,
        brand = brand,
        category = category,
        description = description,
        causesRaw = causesStr,
        stepsRaw = stepsStr
    )
}

fun CommonProblemEntity.toDomain(): KodyarCommonProblem {
    val causesList = causesRaw?.let { raw ->
        if (raw.contains("|||")) raw.split("|||") else listOf(raw)
    }
    val stepsList = stepsRaw?.let { raw ->
        if (raw.contains("|||")) raw.split("|||") else listOf(raw)
    }
    return KodyarCommonProblem(
        id = id,
        title = title,
        brand = brand,
        category = category,
        description = description,
        causes = causesList ?: causesRaw,
        steps = stepsList ?: stepsRaw
    )
}

@Entity(tableName = "cached_technicians")
data class TechnicianEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val city: String?,
    val isVerified: Boolean?,
    val completedOrders: Int?,
    val bio: String?,
    val categoriesRaw: String?,
    val rating: Double?,
    val satisfactionRate: Int?,
    val image: String?,
    val imageUrl: String?,
    val documentsRaw: String?
)

fun KodyarTechnician.toEntity(): TechnicianEntity {
    val techName = resolvedName
    val techCity = resolvedCity
    val finalId = if (!id.isNullOrBlank()) id else "${techName}_${techCity}_${resolvedCategories.firstOrNull() ?: ""}"
    val catsStr = resolvedCategories.joinToString("|||")
    val docsStr = (documents as? List<*>)?.mapNotNull { it?.toString() }?.joinToString("|||")
        ?: (uploaded_documents as? List<*>)?.mapNotNull { it?.toString() }?.joinToString("|||")
        ?: (document_images as? List<*>)?.mapNotNull { it?.toString() }?.joinToString("|||")
    val avatar = resolvedAvatarUrl ?: image ?: imageUrl
    return TechnicianEntity(
        id = finalId,
        name = techName,
        city = techCity,
        isVerified = resolvedIsVerified,
        completedOrders = completedOrders ?: 0,
        bio = bio ?: "تکنسین متخصص کدیار۲۴",
        categoriesRaw = catsStr,
        rating = rating ?: 5.0,
        satisfactionRate = satisfactionRate ?: 100,
        image = avatar,
        imageUrl = avatar,
        documentsRaw = docsStr
    )
}

fun TechnicianEntity.toDomain(): KodyarTechnician {
    val catList = categoriesRaw?.split("|||")?.filter { it.isNotBlank() }
    val docList = documentsRaw?.split("|||")?.filter { it.isNotBlank() }
    return KodyarTechnician(
        id = id,
        name = name,
        city = city,
        isVerified = isVerified,
        completedOrders = completedOrders,
        bio = bio,
        categories = catList,
        rating = rating,
        satisfactionRate = satisfactionRate,
        image = image,
        imageUrl = imageUrl,
        documents = docList
    )
}
