package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.data.model.KodyarSparePart

// Custom Theme Colors matching Codyar HTML
val CodyarNavy: Color @Composable get() = MaterialTheme.colorScheme.primary
val CodyarRed: Color @Composable get() = MaterialTheme.colorScheme.secondary
val CodyarBg: Color @Composable get() = MaterialTheme.colorScheme.background
val CodyarSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val CodyarOnSurface: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val CodyarBorder: Color @Composable get() = MaterialTheme.colorScheme.outline
val CodyarTextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val CodyarTextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

fun normalizePersian(input: String?): String {
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

fun extractModelFromText(text: String): String? {
    if (text.isBlank()) return null
    val norm = normalizePersian(text)
    val modelRegex = Regex("""مدل\s+([آ-یa-zA-Z0-9_\-]+)""", RegexOption.IGNORE_CASE)
    val match = modelRegex.find(norm)
    if (match != null) {
        val found = match.groupValues[1].trim()
        if (found.length >= 2 && found != "های" && found != "مختلف") {
            return found
        }
    }
    val commonModels = listOf(
        "روما", "اپتیما", "پرلا", "بیتا", "پاویا", "کالدا ونزیا", "ورونا", "پیرلا",
        "پارما", "پستیو", "پلی اتان", "کالدا", "M24", "L24", "CV424", "L28", "BN", "K24"
    )
    for (m in commonModels) {
        if (norm.contains(m, ignoreCase = true)) {
            return m
        }
    }
    return null
}

fun extractComponentKeyword(text: String): String? {
    if (text.isBlank()) return null
    val norm = normalizePersian(text).lowercase()
    val components = listOf(
        "مبدل", "پمپ", "فن", "برد", "پرشر", "فلوسویچ", "فلومتر",
        "سنسور", "شیر برقی", "شیر سه طرفه", "شیر گاز", "شیر پرکن", "شیر اطمینان", "شیر",
        "منبع انبساط", "ترموستات", "جرقه زن", "الکترود", "یون", "گیج", "مانومتر",
        "ترموکوپل", "تخلیه", "بلبرینگ", "کاسه نمد", "واشر", "اورینگ", "دیافراگم"
    )
    for (comp in components) {
        if (norm.contains(comp)) {
            return comp
        }
    }
    return null
}

fun buildSparePartQueryForError(
    category: String?,
    brand: String?,
    model: String?,
    title: String?,
    code: String?,
    description: String?,
    causes: Any?
): String {
    val queryParts = mutableListOf<String>()

    if (!category.isNullOrBlank()) {
        queryParts.add(category.trim())
    }

    if (!brand.isNullOrBlank()) {
        queryParts.add(brand.trim())
    }

    val extractedModel = when {
        !model.isNullOrBlank() -> model.trim()
        !title.isNullOrBlank() -> extractModelFromText(title)
        !description.isNullOrBlank() -> extractModelFromText(description)
        else -> null
    }
    if (!extractedModel.isNullOrBlank() && !queryParts.contains(extractedModel)) {
        queryParts.add(extractedModel)
    }

    val fullText = listOfNotNull(title, description, causes?.toString(), code).joinToString(" ")
    val component = extractComponentKeyword(fullText)
    if (!component.isNullOrBlank() && !queryParts.contains(component)) {
        queryParts.add(component)
    }

    return if (queryParts.isNotEmpty()) {
        queryParts.joinToString(" ")
    } else {
        code ?: title ?: ""
    }
}

fun filterSparePartsByQuery(parts: List<KodyarSparePart>, query: String): List<KodyarSparePart> {
    if (query.isBlank()) return parts

    val normQuery = normalizePersian(query).lowercase().trim()
    val knownBrands = setOf(
        "بوتان", "ایران رادیاتور", "ایساتیز", "بوش", "تاچی", "لورچ", "آریستون", "ورونا",
        "گلدیران", "الجی", "سامسونگ", "اسنوا", "دوو", "امرسان", "پارس", "هیمالیا", "جنرال",
        "التروستیل", "بیکاس", "فرولی", "مکس لیت", "باپسی", "پایکان", "والرو", "ایمرگاس",
        "آگ", "باکسی", "ایکس ویژن", "هایسنس", "تکنوگاز", "سپهر الکتریک", "آبسال", "دونار",
        "میدیا", "بکو", "آدمیرال", "تپسی", "کنوود", "پارس خزر", "گرنیه"
    )

    val knownCategories = setOf(
        "پکیج", "آبگرمکن", "لباسشویی", "ظرفشویی", "کولر گازی", "یخچال", "ماکروفر", "جاروبرقی"
    )

    val knownComponents = setOf(
        "مبدل", "پمپ", "فن", "برد", "پرشر", "فلوسویچ", "فلومتر", "سنسور", "ان تی سی", "ntc",
        "شیر برقی", "شیر سه طرفه", "شیر گاز", "شیر پرکن", "شیر اطمینان", "شیر",
        "منبع انبساط", "ترموستات", "جرقه زن", "الکترود", "یون", "گیج", "مانومتر",
        "ترموکوپل", "تخلیه", "بلبرینگ", "کاسه نمد", "واشر", "اورینگ", "دیافراگم"
    )

    val matchedBrandsInQuery = knownBrands.filter { normQuery.contains(it) }
    val matchedCategoriesInQuery = knownCategories.filter { normQuery.contains(it) }
    val matchedComponentsInQuery = knownComponents.filter { normQuery.contains(it) }

    val stopWords = setOf("مدل", "دستگاه", "کد", "خطا", "ارور", "و", "یا", "به", "با", "در", "برای")
    val queryTokens = normQuery.split(Regex("""[\s_()\-\[\]]+"""))
        .filter { it.length >= 2 && !stopWords.contains(it) }

    val modelTokens = queryTokens.filter { token ->
        !matchedBrandsInQuery.contains(token) &&
        !matchedCategoriesInQuery.contains(token) &&
        !matchedComponentsInQuery.contains(token)
    }

    val scoredParts = mutableListOf<Pair<KodyarSparePart, Int>>()

    for (part in parts) {
        val partNameNorm = normalizePersian(part.name ?: "").lowercase()
        val partBrandNorm = normalizePersian(part.brand ?: "").lowercase()
        val combinedNorm = "$partNameNorm $partBrandNorm"

        // 1. Strict Brand Filtering
        if (matchedBrandsInQuery.isNotEmpty()) {
            val partHasMatchedBrand = matchedBrandsInQuery.any { b ->
                partBrandNorm.contains(b) || partNameNorm.contains(b)
            }
            val otherBrandsInPart = knownBrands.filter { b ->
                !matchedBrandsInQuery.contains(b) && (partBrandNorm.contains(b) || partNameNorm.contains(b))
            }

            if (!partHasMatchedBrand && otherBrandsInPart.isNotEmpty()) {
                continue
            }
        }

        // 2. Strict Category Filtering
        if (matchedCategoriesInQuery.isNotEmpty()) {
            val otherCategoriesInPart = knownCategories.filter { c ->
                !matchedCategoriesInQuery.contains(c) && combinedNorm.contains(c)
            }
            if (otherCategoriesInPart.isNotEmpty() && !matchedCategoriesInQuery.any { combinedNorm.contains(it) }) {
                continue
            }
        }

        // 3. Score Calculation
        var score = 0

        if (combinedNorm.contains(normQuery)) {
            score += 200
        }

        for (m in modelTokens) {
            if (partNameNorm.contains(m)) {
                score += 100
            }
        }

        for (comp in matchedComponentsInQuery) {
            if (partNameNorm.contains(comp)) {
                score += 60
            }
        }

        for (b in matchedBrandsInQuery) {
            if (partBrandNorm.contains(b) || partNameNorm.contains(b)) {
                score += 40
            }
        }

        for (t in queryTokens) {
            if (combinedNorm.contains(t)) {
                score += 10
            }
        }

        if (score > 0 || (matchedBrandsInQuery.isEmpty() && matchedComponentsInQuery.isEmpty() && modelTokens.isEmpty())) {
            scoredParts.add(part to score)
        }
    }

    scoredParts.sortByDescending { it.second }
    val result = scoredParts.map { it.first }

    return if (result.isNotEmpty()) {
        result
    } else {
        if (matchedBrandsInQuery.isNotEmpty()) {
            parts.filter { part ->
                val pBrand = normalizePersian(part.brand ?: "").lowercase()
                val pName = normalizePersian(part.name ?: "").lowercase()
                matchedBrandsInQuery.any { b -> pBrand.contains(b) || pName.contains(b) }
            }
        } else {
            emptyList()
        }
    }
}

val voicePunctuationReplacements = listOf(
    "دو نقطه" to ":",
    "دونقطه" to ":",
    "دو نقطه‌" to ":",
    "دو‌نقطه" to ":",
    "خط تیره" to "-",
    "خط‌تیره" to "-",
    "خط فاصله" to "-",
    "خط‌فاصله" to "-",
    "دش" to "-",
    "منها" to "-",
    "فاصله" to " ",
    "اسپیس" to " ",
    "اسلش" to "/",
    "ممیز" to "/",
    "خط مورب" to "/",
    "نقطه" to ".",
    "دات" to "."
)

val voiceCompoundNumbers = listOf(
    "بیست و یک" to "21", "بیست و دو" to "22", "بیست و سه" to "23", "بیست و چهار" to "24", "بیست و پنج" to "25",
    "بیست و شش" to "26", "بیست و شیش" to "26", "بیست و هفت" to "27", "بیست و هشت" to "28", "بیست و نه" to "29", "بیست" to "20",
    "سی و یک" to "31", "سی و دو" to "32", "سی و سه" to "33", "سی و چهار" to "34", "سی و پنج" to "35",
    "سی و شش" to "36", "سی و شیش" to "36", "سی و هفت" to "37", "سی و هشت" to "38", "سی و نه" to "39", "سی" to "30",
    "چهل و یک" to "41", "چهل و دو" to "42", "چهل و سه" to "43", "چهل و چهار" to "44", "چهل و پنج" to "45",
    "چهل و شش" to "46", "چهل و شیش" to "46", "چهل و هفت" to "47", "چهل و هشت" to "48", "چهل و نه" to "49", "چهل" to "40",
    "پنجاه و یک" to "51", "پنجاه و دو" to "52", "پنجاه و سه" to "53", "پنجاه و چهار" to "54", "پنجاه و پنج" to "55",
    "پنجاه و شش" to "56", "پنجاه و شیش" to "56", "پنجاه و هفت" to "57", "پنجاه و هشت" to "58", "پنجاه و نه" to "59", "پنجاه" to "50",
    "شصت و یک" to "61", "شصت و دو" to "62", "شصت و سه" to "63", "شصت و چهار" to "64", "شصت و پنج" to "65",
    "شصت و شش" to "66", "شصت و شیش" to "66", "شصت و هفت" to "67", "شصت و هشت" to "68", "شصت و نه" to "69", "شصت" to "60",
    "هفتاد و یک" to "71", "هفتاد و دو" to "72", "هفتاد و سه" to "73", "هفتاد و چهار" to "74", "هفتاد و پنج" to "75",
    "هفتاد و شش" to "76", "هفتاد و شیش" to "76", "هفتاد و هفت" to "77", "هفتاد و هشت" to "78", "هفتاد و نه" to "79", "هفتاد" to "70",
    "هشتاد و یک" to "81", "هشتاد و دو" to "82", "هشتاد و سه" to "83", "هشتاد و چهار" to "84", "هشتاد و پنج" to "85",
    "هشتاد و شش" to "86", "هشتاد و شیش" to "86", "هشتاد و هفت" to "87", "هشتاد و هشت" to "88", "هشتاد و نه" to "89", "هشتاد" to "80",
    "نود و یک" to "91", "نود و دو" to "92", "نود و سه" to "93", "نود و چهار" to "94", "نود و پنج" to "95",
    "نود و شش" to "96", "نود و شیش" to "96", "نود و هفت" to "97", "نود و هشت" to "98", "نود و نه" to "99", "نود" to "90",
    "پانزده" to "15", "پونزده" to "15", "شانزده" to "16", "شونزده" to "16",
    "سیزده" to "13", "چهارده" to "14", "هفده" to "17", "هجده" to "18", "نوزده" to "19",
    "یازده" to "11", "دوازده" to "12", "ده" to "10", "صد" to "100"
)

val voiceSingleNumbers = listOf(
    "صفر" to "0", "یک" to "1", "یکم" to "1", "دو" to "2", "دوم" to "2", "سه" to "3", "سوم" to "3",
    "چهار" to "4", "چهارم" to "4", "پنج" to "5", "پنجم" to "5", "شش" to "6", "شیش" to "6", "ششم" to "6",
    "هفت" to "7", "هفتم" to "7", "هشت" to "8", "هشتم" to "8", "نه" to "9", "نهم" to "9"
)

val voicePhonetics = listOf(
    "اِی" to "e", "ایی" to "e",
    "اف" to "f", "اِف" to "f",
    "پی" to "p", "دی" to "d", "سی" to "c",
    "اچ" to "h", "اِچ" to "h",
    "یو" to "u", "ال" to "l", "اِل" to "l",
    "ار" to "r", "اِر" to "r",
    "او" to "o", "تی" to "t", "آی" to "i",
    "بی" to "b", "زد" to "z", "جی" to "g",
    "کی" to "k", "ان" to "n", "اِن" to "n", "ام" to "m", "اِم" to "m",
    "آ" to "a", "اِ" to "e"
)

fun normalizeVoiceSearchText(text: String): String {
    if (text.isBlank()) return ""

    var normalized = normalizePersian(text).trim().lowercase()

    // 1. Replace Persian & Arabic digits with English
    val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    for (i in 0..9) {
        normalized = normalized.replace(persianDigits[i].toString(), i.toString())
        normalized = normalized.replace(arabicDigits[i].toString(), i.toString())
    }

    // 2. Replace spoken punctuation (like "دونقطه", "دو نقطه", "فاصله", "خط تیره")
    for ((punct, symbol) in voicePunctuationReplacements) {
        normalized = normalized.replace(punct, " $symbol ")
    }

    // 3. Replace compound numbers (e.g. "بیست و دو" -> "22")
    for ((phrase, num) in voiceCompoundNumbers) {
        normalized = normalized.replace(phrase, " $num ")
    }

    // 4. Tokenize and replace single numbers, phonetics, and clean noise prefixes
    val rawTokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val convertedTokens = mutableListOf<String>()

    val noiseWords = setOf("کدخطای", "کدخطا", "کدهای", "کد", "خطای", "خطا", "ارور", "شماره", "ارورهای")
    val singleNumberMap = voiceSingleNumbers.toMap()
    val phoneticsMap = voicePhonetics.toMap()
    val standalonePhonetics = mapOf(
        "ای" to "e", "اِی" to "e", "ایی" to "e", "یی" to "e",
        "اف" to "f", "اِف" to "f", "پی" to "p", "دی" to "d", "سی" to "c",
        "اچ" to "h", "اِچ" to "h", "یو" to "u", "ال" to "l", "اِل" to "l",
        "ار" to "r", "اِر" to "r", "او" to "o", "تی" to "t", "آی" to "i",
        "بی" to "b", "زد" to "z", "جی" to "g", "کی" to "k", "ان" to "n",
        "ام" to "m", "آ" to "a"
    )

    var isLeadingNoise = true
    for (i in rawTokens.indices) {
        val clean = rawTokens[i].trim()
        if (clean.isEmpty()) continue

        // Skip leading filler words like "ارور", "کد خطای" at the start of spoken text
        if (isLeadingNoise && noiseWords.contains(clean)) {
            continue
        }
        isLeadingNoise = false

        val nextToken = if (i + 1 < rawTokens.size) rawTokens[i + 1].trim() else null
        val nextIsDigit = nextToken != null && (nextToken.all { it.isDigit() } || singleNumberMap.containsKey(nextToken) || voiceCompoundNumbers.any { it.first == nextToken })

        when {
            singleNumberMap.containsKey(clean) -> convertedTokens.add(singleNumberMap[clean]!!)
            phoneticsMap.containsKey(clean) -> convertedTokens.add(phoneticsMap[clean]!!)
            standalonePhonetics.containsKey(clean) && nextIsDigit -> convertedTokens.add(standalonePhonetics[clean]!!)
            else -> convertedTokens.add(clean)
        }
    }

    if (convertedTokens.isEmpty()) return ""

    // 5. Smart join: Compact letter + digit or digit + punctuation combinations (e.g., ["e", "2"] -> "e2", ["e", "2", "پکیج"] -> "e2 پکیج")
    val sb = StringBuilder()
    for (i in convertedTokens.indices) {
        val current = convertedTokens[i]
        val prev = if (i > 0) convertedTokens[i - 1] else null

        if (prev != null) {
            val prevIsCodePart = prev.length <= 4 && prev.all { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() }
            val currentIsCodePart = current.length <= 4 && current.all { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() }
            val isPunctuation = current in listOf(":", "-", ".", "/") || prev in listOf(":", "-", ".", "/")

            // If it's a code-like combo (e.g. 'e' and '2', or '2' and ':', or ':' and '22', or 'e' and '-')
            if ((prevIsCodePart && currentIsCodePart && (prev.any { it.isLetter() } || prev.length == 1)) || isPunctuation) {
                // Attach without space
                sb.append(current)
            } else {
                sb.append(" ").append(current)
            }
        } else {
            sb.append(current)
        }
    }

    return sb.toString().trim()
}

fun normalizePersianText(input: String): String {
    return input.lowercase()
        .replace("ك", "ک")
        .replace("ي", "ی")
        .replace("‌", "")
        .replace(" ", "")
        .replace("-", "")
        .replace("آ", "ا")
}

fun formatToman(price: Double): String {
    return String.format("%,.0f", price)
}

fun convertGregorianToJalali(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    val persianDigits = listOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
    val englishDigits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    var normalized = dateString.trim()
    for (i in 0..9) {
        normalized = normalized.replace(persianDigits[i], englishDigits[i])
    }
    
    // If already in Jalali format with slash (e.g. 1405/7/7 or 1405/07/07)
    if (normalized.contains("/")) {
        val pParts = normalized.split("/")
        if (pParts.size == 3 && pParts[0].toIntOrNull()?.let { it in 1300..1500 } == true) {
            return normalized.map { char ->
                if (char.isDigit()) persianDigits[char.toString().toInt()] else char
            }.joinToString("")
        }
    }
    if (!normalized.any { it.isDigit() }) {
        return dateString
    }
    try {
        val cleanDate = normalized.substringBefore("T").substringBefore(" ").trim()
        val parts = cleanDate.split("-")
        if (parts.size == 3) {
            val year = parts[0].toIntOrNull() ?: return dateString
            val month = parts[1].toIntOrNull() ?: return dateString
            val day = parts[2].toIntOrNull() ?: return dateString
            
            // If year is already Jalali (e.g. 1403 or 1405)
            if (year in 1300..1500) {
                val rawPersianDate = "$year/${String.format("%02d", month)}/${String.format("%02d", day)}"
                return rawPersianDate.map { char ->
                    if (char.isDigit()) persianDigits[char.toString().toInt()] else char
                }.joinToString("")
            }

            val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 335)
            var gy = year - 1600
            var gm = month - 1
            var gd = day - 1

            var gDayNo = 365 * gy + (gy + 4) / 4 - (gy + 100) / 100 + (gy + 400) / 400
            gDayNo += gDaysInMonth[gm]
            if (gm > 1 && ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0))) {
                gDayNo++
            }
            gDayNo += gd

            var jDayNo = gDayNo - 79
            val jNp = jDayNo / 12053
            jDayNo %= 12053

            var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
            jDayNo %= 1461

            if (jDayNo >= 366) {
                jy += (jDayNo - 1) / 365
                jDayNo = (jDayNo - 1) % 365
            }

            var jm = 0
            var jd = 0
            for (i in 0..11) {
                val monthLength = if (i < 6) 31 else if (i < 11) 30 else 29
                if (jDayNo < monthLength) {
                    jm = i + 1
                    jd = jDayNo + 1
                    break
                }
                jDayNo -= monthLength
            }
            
            val rawPersianDate = "$jy/${String.format("%02d", jm)}/${String.format("%02d", jd)}"
            return rawPersianDate.map { char ->
                if (char.isDigit()) persianDigits[char.toString().toInt()] else char
            }.joinToString("")
        }
    } catch (e: Exception) {
        // ignore
    }
    return dateString
}

@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}

@Composable
fun FilterDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }

    LaunchedEffect(expanded) {
        if (!expanded) {
            filterText = ""
        }
    }

    val filteredOptions = remember(options, filterText) {
        if (filterText.isEmpty()) {
            options
        } else {
            val normFilter = normalizePersian(filterText)
            options.filter {
                it == "همه" || normalizePersian(it).contains(normFilter, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CodyarTextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedValue,
                    fontSize = 12.sp,
                    color = if (selectedValue == "همه") Color(0xFF64748B) else CodyarTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = CodyarTextSecondary
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 280.dp)
                    .background(CodyarSurface)
            ) {
                if (options.size > 5) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("جستجو...", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Right),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (filterText.isNotEmpty()) {
                                IconButton(onClick = { filterText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CodyarNavy,
                            unfocusedBorderColor = Color(0xFFDDE1E7),
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC)
                        )
                    )
                }

                if (filteredOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "موردی یافت نشد",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        onClick = {}
                    )
                } else {
                    filteredOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Right
                                )
                            },
                            onClick = {
                                onSelect(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

fun openBazaarUpdate(context: Context, appPackageName: String = "ir.novincol.com") {
    try {
        val bazaarIntent = Intent(Intent.ACTION_VIEW, Uri.parse("bazaar://details?id=$appPackageName")).apply {
            setPackage("com.farsitel.bazaar")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(bazaarIntent)
    } catch (e: Exception) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cafebazaar.ir/app/$appPackageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e2: Exception) {
            val apkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kodyar24.ir/download/app.apk")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(apkIntent)
        }
    }
}

fun compressAndEncodeUriToBase64(context: Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val maxDim = 1024
        var scale = 1
        while (options.outWidth / scale > maxDim || options.outHeight / scale > maxDim) {
            scale *= 2
        }

        val options2 = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = scale
        }
        val inputStream2 = context.contentResolver.openInputStream(uri) ?: return ""
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream2, null, options2)
        inputStream2.close()

        if (bitmap != null) {
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, baos)
            val bytes = baos.toByteArray()
            bitmap.recycle()
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}
