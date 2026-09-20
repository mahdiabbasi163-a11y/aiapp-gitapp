package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.KodyarTechnician
import com.example.data.model.KodyarUser
import com.example.ui.AssistantViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun TechniciansScreen(
    viewModel: AssistantViewModel,
    liveTechs: StateFlow<List<KodyarTechnician>>,
    currentUser: KodyarUser?,
    onShowAuth: () -> Unit
) {
    val context = LocalContext.current
    val techsList by liveTechs.collectAsState()
    val isTechsLoading by viewModel.isTechniciansLoading.collectAsState()
    val userCity = currentUser?.city?.takeIf { it.isNotBlank() }
    val targetCity = userCity ?: "اراک"

    var sortBy by remember { mutableStateOf("top_rated") }
    var selectedTechForRepair by remember { mutableStateOf<KodyarTechnician?>(null) }
    var selectedTechForReviews by remember { mutableStateOf<KodyarTechnician?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshTechnicians()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Top Header: Total Nationwide Counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "متخصصین و تعمیرکاران مجاز",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CodyarNavy
                )
                Text(
                    text = "(${techsList.size} متخصص فعال در سراسر کشور)",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }
            if (isTechsLoading) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFD97706)
                        )
                        Text(
                            text = "در حال استعلام...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            } else if (techsList.isNotEmpty()) {
                Surface(
                    color = Color(0xFFE0F2FE),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "🟢 شبکه فعال سراسری",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0369A1),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Clean user location indicator card (Shows customer's province/city without manual switching)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📍", fontSize = 16.sp)
                Text(
                    text = "نمایش متخصصین و تعمیرکاران مجاز در محدوده شما: $targetCity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF1E40AF),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val listAvatars = remember {
            listOf(
                "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1566492031773-4f4e44671857?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=150&h=150&fit=crop"
            )
        }

        // Sorting Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مرتب‌سازی:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CodyarTextSecondary
            )
            
            val sortOptions = listOf(
                "top_rated" to "⭐ متخصصین برتر و منتخب",
                "most_orders" to "🛠️ پرکارترین‌ها",
                "all" to "👤 همه"
            )
            
            sortOptions.forEach { (optionKey, optionLabel) ->
                val isActive = sortBy == optionKey
                Box(
                    modifier = Modifier
                        .background(
                            if (isActive) CodyarNavy.copy(alpha = 0.12f) else Color(0xFFF3F4F6),
                            RoundedCornerShape(30.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) CodyarNavy else Color.Transparent,
                            shape = RoundedCornerShape(30.dp)
                        )
                        .clickable { sortBy = optionKey }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = optionLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) CodyarNavy else CodyarTextPrimary
                    )
                }
            }
        }

        // List - Strictly filtered to the user's city/province
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            val baseFiltered = techsList.filter { tech ->
                val techCity = tech.resolvedCity
                if (techCity.isBlank()) false
                else viewModel.areCitiesCompatible(techCity, targetCity)
            }

            val filtered = when (sortBy) {
                "top_rated" -> baseFiltered.sortedWith(
                    compareByDescending<KodyarTechnician> { it.resolvedRating }
                        .thenByDescending { it.satisfactionRate ?: 100 }
                        .thenByDescending { it.completedOrders ?: 0 }
                )
                "most_orders" -> baseFiltered.sortedByDescending { it.completedOrders ?: 0 }
                else -> baseFiltered
            }

            if (filtered.isEmpty()) {
                item {
                    if (isTechsLoading) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = CodyarNavy
                                )
                                Text(
                                    "در حال استعلام و دریافت لیست تکنسین‌ها از سرور...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CodyarNavy,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "ارتباط زنده با پایگاه داده کدیار۲۴ برقرار است",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📍", fontSize = 28.sp)
                                Text(
                                    "تکنسینی در شهر $targetCity ثبت نشده است",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    "در حال حاضر تکنسین فعالی برای شهر مورد نظر ثبت نشده است. در صورت نیاز به هماهنگی تکنسین یا ثبت نهایی درخواست تعمیرات، لطفاً با پشتیبانی کدیار۲۴ ارتباط برقرار کنید.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB45309),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            } else {
                items(filtered) { tech ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Avatar on the side
                            val avatarUrl = tech.resolvedAvatarUrl
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, CodyarNavy, CircleShape)
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUrl != null) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = tech.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = tech.name,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                val techReviews = remember(tech) { viewModel.getTechnicianReviews(tech.id, tech.name) }
                                val reviewsCount = maxOf(techReviews.size, tech.ratingsCount ?: tech.ratings_count ?: 0)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        tech.name ?: "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CodyarTextPrimary
                                    )
                                    if (tech.isSuspended) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFFDE8E8), RoundedCornerShape(5.dp))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text("⛔ تعلیق شده", color = Color(0xFFC81E1E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (tech.isVacation) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFFEF2F2), RoundedCornerShape(5.dp))
                                                .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(5.dp))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text("🏖️ در حال انجام سفارش / عدم دسترسی", color = Color(0xFFDC2626), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (tech.resolvedIsVerified) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFF0FDF4), RoundedCornerShape(5.dp))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text("🟢 متخصص آماده به خدمت", color = Color(0xFF16A34A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFEAFAF1), RoundedCornerShape(5.dp))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text("✓ تایید شده", color = Color(0xFF1E8449), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFFFF3CD), RoundedCornerShape(5.dp))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text("در انتظار تایید", color = Color(0xFF856404), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        "📍 ${tech.city ?: "نامشخص"}",
                                        fontSize = 11.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Text("•", fontSize = 11.sp, color = Color.LightGray)
                                    Text(
                                        "🛠️ ${tech.completedOrders ?: 0} سرویس",
                                        fontSize = 11.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Text("•", fontSize = 11.sp, color = Color.LightGray)
                                    
                                    // Star Rating & Reviews Clickable
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { selectedTechForReviews = tech }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB000),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            String.format(java.util.Locale.US, "%.1f", tech.resolvedRating),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CodyarTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            if (reviewsCount > 0) "($reviewsCount نظر)" else "(${tech.satisfactionRate ?: 100}% رضایت)",
                                            fontSize = 10.sp,
                                            color = Color(0xFFD97706),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!tech.bio.isNullOrBlank()) {
                                    Text(
                                        tech.bio,
                                        fontSize = 11.sp,
                                        color = CodyarTextPrimary,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(bottom = 7.dp)
                                    )
                                }

                                // Categories
                                if (tech.resolvedCategories.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 9.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        tech.resolvedCategories.forEach { cat ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFE8EAF0), RoundedCornerShape(5.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(cat, color = CodyarTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                val isDispatchable = !tech.isSuspended && !tech.isVacation

                                OutlinedButton(
                                    onClick = { selectedTechForReviews = tech },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF8FAFC)),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color(0xFFD97706)
                                        )
                                        Text(
                                            text = if (reviewsCount > 0) "مشاهده کارنامه، نظرات و امتیازات ($reviewsCount نظر) ⭐" else "مشاهده کارنامه و نظرات مشتریان",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (tech.isSuspended) return@Button
                                        if (tech.isVacation) {
                                            Toast.makeText(context, "این همکار در حال حاضر در مرخصی و آفلاین است و امکان اعزام ایشان وجود ندارد.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (currentUser == null) {
                                            onShowAuth()
                                            return@Button
                                        }
                                        selectedTechForRepair = tech
                                    },
                                    enabled = isDispatchable,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CodyarNavy,
                                        disabledContainerColor = if (tech.isVacation) Color(0xFFFEE2E2) else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(
                                        if (tech.isSuspended || tech.isVacation) Icons.Default.Close else Icons.Default.Phone,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (tech.isSuspended) Color.Gray else if (tech.isVacation) Color(0xFFDC2626) else Color.White
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        if (tech.isSuspended) "غیرفعال توسط مدیریت" else if (tech.isVacation) "همکار در مرخصی و آفلاین است" else "ثبت سفارش اعزام تکنسین",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (tech.isSuspended) Color.Gray else if (tech.isVacation) Color(0xFFDC2626) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("تکنسینی ثبت نشده است", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("به زودی تکنسین‌های تایید شده اضافه می‌شوند", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // Bottom CTA for techs
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("تکنسین هستید؟", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text(
                            "با همکاران ما در وب‌سایت کدیار۲۴ تماس بگیرید و پس از تایید مدارک سفارش کار دریافت کنید.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://kodyar24.ir"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطا در باز کردن وب‌سایت", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text("ثبت‌نام تکنسین در سایت", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG FOR VIEWING REVIEWS & RATINGS ---
    selectedTechForReviews?.let { tech ->
        TechnicianReviewsDialog(
            technician = tech,
            reviews = viewModel.getTechnicianReviews(tech.id, tech.name),
            onDismiss = { selectedTechForReviews = null }
        )
    }

    // --- DIALOG FOR REQUESTING DISPATCH ---
    selectedTechForRepair?.let { tech ->
        val next7Days = remember { calculateNext7JalaliDays() }
        var selectedDayIndex by remember { mutableStateOf(if (next7Days.isNotEmpty()) 0 else 0) }
        val timeSlots = remember {
            listOf(
                "صبح (۹ الی ۱۳)",
                "عصر (۱۴ الی ۱۸)",
                "غروب (۱۸ الی ۲۱)"
            )
        }
        var selectedTimeSlot by remember { mutableStateOf(timeSlots[1]) } // Default to afternoon
        val coroutineScope = rememberCoroutineScope()

        var deviceBrand by remember { mutableStateOf("") }
        var problemDesc by remember { mutableStateOf("") }
        var contactPhone by remember(currentUser) { mutableStateOf(currentUser?.phone ?: "") }
        var isSubmitting by remember { mutableStateOf(false) }

        // مرحله ۱: مشخصات دستگاه و زمان | مرحله ۲: آدرس و لوکیشن
        var formStep by remember { mutableStateOf(1) }
        var fullAddress by remember { mutableStateOf("") }
        var postalCode by remember { mutableStateOf("") }
        var addressNote by remember { mutableStateOf("") }
        var pickedLocation by remember { mutableStateOf<PickedLocation?>(null) }
        var locationLink by remember { mutableStateOf("") }
        var isLocating by remember { mutableStateOf(false) }

        val fetchLocation = {
            isLocating = true
            requestCurrentLocation(context) { loc, err ->
                isLocating = false
                if (loc != null) {
                    pickedLocation = loc
                    val resAddr = loc.resolvedAddress
                    if (!resAddr.isNullOrBlank()) {
                        fullAddress = resAddr
                        Toast.makeText(context, "✅ موقعیت مکانی ثبت و آدرس کامل در کادر درج گردید", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "✅ موقعیت مکانی ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, err ?: "خطا در دریافت موقعیت", Toast.LENGTH_LONG).show()
                }
            }
        }

        val locationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.values.any { it }) {
                fetchLocation()
            } else {
                Toast.makeText(context, "برای ثبت لوکیشن باید دسترسی موقعیت مکانی را اجازه دهید", Toast.LENGTH_LONG).show()
            }
        }

        val chosenDay = next7Days.getOrNull(selectedDayIndex)
        val technicianVisitTime = chosenDay?.let { "${it.formattedLabel} - $selectedTimeSlot" } ?: selectedTimeSlot

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) selectedTechForRepair = null },
            title = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "سفارش اعزام تکنسین (${tech.name ?: "متخصص"})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CodyarNavy,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // نوار پیشرفت دو مرحله‌ای
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1 to "۱. مشخصات دستگاه", 2 to "۲. آدرس محل").forEach { (step, label) ->
                            val active = formStep >= step
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(
                                            if (active) CodyarNavy else Color(0xFFE2E8F0),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (formStep == step) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) CodyarNavy else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                  if (formStep == 1) {
                    Text(
                        text = "جهت هماهنگی دقیق مراجعه تکنسین، مشخصات و تاریخ مورد نظر را انتخاب فرمایید:",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 1. Device and Brand input
                    OutlinedTextField(
                        value = deviceBrand,
                        onValueChange = { deviceBrand = it },
                        label = { Text("نوع دستگاه و برند", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: پکیج بوتان یا لباسشویی ال‌جی", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // 2. Problem description input
                    OutlinedTextField(
                        value = problemDesc,
                        onValueChange = { problemDesc = it },
                        label = { Text("شرح مشکل دستگاه", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: خطای E01 می‌دهد یا آب گرم نمی‌شود", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2,
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // 3. 7 Days Jalali Date Picker (No typing needed)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📅 انتخاب روز مراجعه (۷ روز آینده):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CodyarNavy,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            next7Days.forEachIndexed { index, dayItem ->
                                val isSelected = (index == selectedDayIndex)
                                Surface(
                                    onClick = { selectedDayIndex = index },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) CodyarNavy else Color(0xFFF1F5F9),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) CodyarNavy else Color(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier.width(92.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = when {
                                                dayItem.isToday -> "امروز"
                                                dayItem.isTomorrow -> "فردا"
                                                else -> dayItem.dayOfWeekName
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = "${dayItem.dayOfMonth} ${dayItem.monthName}",
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF64748B),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. بازه ساعت حضور تکنسین در منزل مشتری
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "⏰ ساعت حضور تکنسین در منزل:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CodyarNavy,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            timeSlots.forEach { slot ->
                                val isSelected = (selectedTimeSlot == slot)
                                Surface(
                                    onClick = { selectedTimeSlot = slot },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF1E8449) else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF1E8449) else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = slot,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF334155),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // کارت پیش‌نمایش زمان حضور تکنسین در منزل
                    Surface(
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🗓️", fontSize = 14.sp)
                            Text(
                                text = "زمان حضور تکنسین در منزل: $technicianVisitTime",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }

                    // 5. Contact phone input
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("شماره همراه جهت هماهنگی", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                  } else {
                    // ===== مرحله ۲: آدرس و لوکیشن محل خرابی =====
                    Text(
                        text = "آدرس دقیق محل را ثبت کنید تا تکنسین برای پیدا کردن آدرس تماس نگیرد:",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ثبت موقعیت مکانی روی نقشه
                    Surface(
                        color = if (pickedLocation != null) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (pickedLocation != null) Color(0xFF86EFAC) else Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (hasLocationPermission(context)) {
                                        fetchLocation()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                enabled = !isLocating,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (pickedLocation != null) Color(0xFF16A34A) else CodyarNavy
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isLocating) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = if (pickedLocation != null) "✅ لوکیشن ثبت شد (برای تغییر بزنید)" else "ثبت موقعیت فعلی من روی نقشه",
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            pickedLocation?.let { loc ->
                                Text(
                                    text = "📍 مختصات: ${loc.shortLabel}" +
                                            (loc.accuracyMeters?.let { " (دقت حدود ${it.toInt()} متر)" } ?: ""),
                                    fontSize = 10.sp,
                                    color = Color(0xFF15803D),
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // اگر مشتری جای دیگری است (مثلاً سرکار) و برای منزل سفارش می‌دهد
                            OutlinedTextField(
                                value = locationLink,
                                onValueChange = { input ->
                                    locationLink = input
                                    parseLocationFromLink(input)?.let { parsed ->
                                        pickedLocation = parsed
                                        Toast.makeText(context, "✅ لوکیشن از لینک خوانده شد", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            val addr = fetchAddressFromCoordinates(context, parsed.latitude, parsed.longitude)
                                            if (!addr.isNullOrBlank()) {
                                                fullAddress = addr
                                                pickedLocation = parsed.copy(resolvedAddress = addr)
                                                Toast.makeText(context, "✅ آدرس دقیق در کادر آدرس درج شد", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                label = { Text("یا لینک نقشه محل را پیست کنید", fontSize = 11.sp) },
                                placeholder = { Text("مخصوص وقتی خودتان در محل نیستید", fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                        }
                    }

                    // آدرس پستی کامل
                    OutlinedTextField(
                        value = fullAddress,
                        onValueChange = { fullAddress = it },
                        label = { Text("آدرس کامل پستی (خیابان، کوچه، پلاک، واحد)", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: اراک، خیابان شریعتی، کوچه ۱2، پلاک ۵، طبقه ۲", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2,
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // کد پستی
                    val normalizedPostal = normalizePersianDigits(postalCode).filter { it.isDigit() }
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { input ->
                            val digits = normalizePersianDigits(input).filter { it.isDigit() }
                            if (digits.length <= 10) postalCode = digits
                        },
                        label = { Text("کد پستی ۱۰ رقمی", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: 3819764521", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = normalizedPostal.isNotEmpty() && normalizedPostal.length != 10,
                        supportingText = {
                            if (normalizedPostal.isNotEmpty() && normalizedPostal.length != 10) {
                                Text("کد پستی باید دقیقاً ۱۰ رقم باشد", fontSize = 10.sp, color = Color(0xFFDC2626))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // نشانه و توضیح مسیر
                    OutlinedTextField(
                        value = addressNote,
                        onValueChange = { addressNote = it },
                        label = { Text("نشانه و توضیح مسیر (اختیاری)", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: روبروی بانک ملی، درب قهوه‌ای، زنگ دوم", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 2,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                  }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (formStep == 1) {
                            if (deviceBrand.isBlank() || problemDesc.isBlank() || contactPhone.isBlank()) {
                                Toast.makeText(context, "لطفاً نوع دستگاه، شرح مشکل و شماره تماس را تکمیل فرمایید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            formStep = 2
                            return@Button
                        }

                        val cleanPostal = normalizePersianDigits(postalCode).filter { it.isDigit() }
                        if (fullAddress.isBlank() && pickedLocation == null) {
                            Toast.makeText(context, "لطفاً آدرس کامل را بنویسید یا موقعیت فعلی را ثبت کنید", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (cleanPostal.isNotEmpty() && cleanPostal.length != 10) {
                            Toast.makeText(context, "کد پستی باید دقیقاً ۱۰ رقم باشد", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        val repairCity = currentUser?.resolvedCity?.takeIf { it.isNotBlank() }
                            ?: currentUser?.city?.takeIf { it.isNotBlank() }
                            ?: tech.resolvedCity.takeIf { it.isNotBlank() }
                            ?: tech.city?.takeIf { it.isNotBlank() }
                            ?: "اراک"

                        val loc = pickedLocation
                        val composedAddress = listOfNotNull(
                            fullAddress.trim().takeIf { it.isNotBlank() },
                            addressNote.trim().takeIf { it.isNotBlank() }?.let { "نشانه: $it" },
                            cleanPostal.takeIf { it.length == 10 }?.let { "کد پستی: $it" }
                        ).joinToString(" - ")

                        val pureProblemDesc = problemDesc.trim()
                        val devBrandClean = deviceBrand.trim().ifBlank { "عمومی" }

                        // استخراج ساعت دقیق از زمان گوشی جهت جلوگیری از هرگونه اختلاف زمان بین مشتری و تکنسین
                        val nowCal = java.util.Calendar.getInstance()
                        val phoneHour = nowCal.get(java.util.Calendar.HOUR_OF_DAY)
                        val phoneMinute = nowCal.get(java.util.Calendar.MINUTE)
                        val phoneTimeFormatted = String.format(java.util.Locale.US, "%02d:%02d", phoneHour, phoneMinute)

                        val todayJalali = calculateNext7JalaliDays().firstOrNull()
                        val todayShamsi = if (todayJalali != null) "${todayJalali.dayOfWeekName} ${todayJalali.dayOfMonth} ${todayJalali.monthName}" else ""

                        val scheduledDatePayload = "$technicianVisitTime (ثبت سفارش: ساعت $phoneTimeFormatted)"

                        val problemWithTimestamp = buildString {
                            if (pureProblemDesc.isNotBlank()) {
                                append(pureProblemDesc)
                                append("\n")
                            }
                            append("زمان حضور تکنسین در منزل: $technicianVisitTime\n")
                            append("[ساعت ثبت سفارش توسط مشتری از زمان گوشی: $phoneTimeFormatted ($todayShamsi)]")
                        }

                        viewModel.submitRepairRequest(
                            techId = tech.id ?: "",
                            description = problemWithTimestamp,
                            city = repairCity,
                            appliance = devBrandClean,
                            brand = devBrandClean,
                            customerPhone = contactPhone,
                            address = composedAddress.takeIf { it.isNotBlank() },
                            postalCode = cleanPostal.takeIf { it.length == 10 },
                            latitude = loc?.latitude,
                            longitude = loc?.longitude,
                            locationUrl = loc?.mapsUrl,
                            addressNote = addressNote.trim().takeIf { it.isNotBlank() },
                            scheduledDate = scheduledDatePayload
                        ) { success, err ->
                            isSubmitting = false
                            if (success) {
                                selectedTechForRepair = null
                                Toast.makeText(
                                    context,
                                    "✅ سفارش اعزام در ساعت $phoneTimeFormatted با موفقیت ثبت شد.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, err ?: "خطا در ثبت سفارش اعزام", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            text = if (formStep == 1) "مرحله بعد: ثبت آدرس" else "ثبت سفارش اعزام تکنسین",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (formStep == 2) formStep = 1 else selectedTechForRepair = null
                    },
                    enabled = !isSubmitting
                ) {
                    Text(
                        text = if (formStep == 2) "بازگشت" else "انصراف",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        )
    }
}

data class JalaliScheduleDay(
    val dayOfWeekName: String,
    val dayOfMonth: Int,
    val monthName: String,
    val formattedLabel: String,
    val isToday: Boolean,
    val isTomorrow: Boolean
)

fun calculateNext7JalaliDays(): List<JalaliScheduleDay> {
    val persianMonths = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    val weekDays = mapOf(
        java.util.Calendar.SATURDAY to "شنبه",
        java.util.Calendar.SUNDAY to "یکشنبه",
        java.util.Calendar.MONDAY to "دوشنبه",
        java.util.Calendar.TUESDAY to "سه‌شنبه",
        java.util.Calendar.WEDNESDAY to "چهارشنبه",
        java.util.Calendar.THURSDAY to "پنجشنبه",
        java.util.Calendar.FRIDAY to "جمعه"
    )

    val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 335)
    val list = mutableListOf<JalaliScheduleDay>()
    val cal = java.util.Calendar.getInstance()

    for (step in 0 until 7) {
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)

        val gy = year - 1600
        val gm = month - 1
        val gd = day - 1

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

        val monthName = persianMonths.getOrElse(jm - 1) { "" }
        val weekDay = weekDays[dayOfWeek] ?: "شنبه"

        val label = when (step) {
            0 -> "امروز ($weekDay $jd $monthName)"
            1 -> "فردا ($weekDay $jd $monthName)"
            else -> "$weekDay $jd $monthName"
        }

        list.add(
            JalaliScheduleDay(
                dayOfWeekName = weekDay,
                dayOfMonth = jd,
                monthName = monthName,
                formattedLabel = label,
                isToday = (step == 0),
                isTomorrow = (step == 1)
            )
        )

        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    return list
}
