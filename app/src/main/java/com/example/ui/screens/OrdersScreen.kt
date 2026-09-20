package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KodyarRepairOrder
import com.example.data.model.PartPurchaseOrder
import com.example.ui.AssistantViewModel

@Composable
fun OrdersScreen(
    viewModel: AssistantViewModel,
    repairOrders: List<KodyarRepairOrder>,
    isRepairsLoading: Boolean,
    onBack: () -> Unit,
    onNavigateToTechs: () -> Unit
) {
    val partPurchases by viewModel.partPurchases.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isTechnicianOnline by viewModel.isTechnicianOnline.collectAsState()
    val isTechStatusUpdating by viewModel.isTechStatusUpdating.collectAsState()
    val isTech = currentUser?.isTechnicianUser == true || currentUser?.userRole == com.example.data.model.UserRole.TECHNICIAN
    val isApprovedTech = isTech && currentUser?.isApprovedUser == true

    var selectedSubTab by remember { mutableStateOf(0) } // 0: Repair Orders, 1: Part Purchases
    val context = LocalContext.current

    var showCompleteOrderDialog by remember { mutableStateOf<KodyarRepairOrder?>(null) }
    var completeOrderAmountInput by remember { mutableStateOf("") }
    var isSubmittingComplete by remember { mutableStateOf(false) }

    // State for Unlocking Customer Contact Info with 15% Commission (Card-to-Card)
    var showUnlockOrderDialog by remember { mutableStateOf<KodyarRepairOrder?>(null) }
    var trackingNumberInput by remember { mutableStateOf("") }
    var depositorNameInput by remember { mutableStateOf(currentUser?.full_name ?: "") }
    var isSubmittingUnlock by remember { mutableStateOf(false) }

    var showRateTechnicianDialog by remember { mutableStateOf<KodyarRepairOrder?>(null) }

    val bankCardInfo by viewModel.bankCardInfo.collectAsState()
    val pendingCommissionOrders by viewModel.pendingCommissionOrderIds.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRepairs()
        viewModel.loadBankCardInfo()
        while (true) {
            kotlinx.coroutines.delay(20_000L)
            viewModel.loadRepairs(silent = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CodyarSurface,
                    contentColor = CodyarTextPrimary
                ),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("بازگشت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            IconButton(
                onClick = {
                    viewModel.loadRepairs()
                    Toast.makeText(context, "در حال همگام‌سازی اطلاعات...", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی وضعیت", tint = CodyarNavy)
            }
        }

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = CodyarSurface,
            contentColor = CodyarNavy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = {
                    Text(
                        text = "سفارش‌های اعزام تکنسین (${repairOrders.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = {
                    Text(
                        text = "سفارش‌های قطعات و لوازم (${partPurchases.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }

        if (selectedSubTab == 0) {
            // Repair Orders Tab
            if (isRepairsLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CodyarNavy)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Technician Online / Vacation Status Card
                    if (isTech && isApprovedTech) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isTechnicianOnline) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, if (isTechnicianOnline) Color(0xFFBBF7D0) else Color(0xFFFECACA)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(if (isTechnicianOnline) Color(0xFF16A34A) else Color(0xFFDC2626), CircleShape)
                                        )
                                        Column {
                                            Text(
                                                text = if (isTechnicianOnline) "وضعیت: آماده به کار 🟢" else "وضعیت: در حال مرخصی 🏖️",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isTechnicianOnline) Color(0xFF166534) else Color(0xFF991B1B)
                                            )
                                            Text(
                                                text = if (isTechnicianOnline) "سفارش‌های شهر ${currentUser?.city ?: ""} به شما نمایش داده می‌شود" else "در مرخصی سفارش جدیدی ارسال نمی‌شود",
                                                fontSize = 10.sp,
                                                color = if (isTechnicianOnline) Color(0xFF15803D) else Color(0xFFB91C1C)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (!isTechStatusUpdating) {
                                                val willBeOnline = !isTechnicianOnline
                                                viewModel.toggleTechnicianStatus { success, err ->
                                                    if (success) {
                                                        val msg = if (willBeOnline) "وضعیت: آماده به کار ✅" else "وضعیت: مرخصی 🏖️"
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, err ?: "خطا در تغییر وضعیت", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isTechnicianOnline) Color(0xFFDC2626) else Color(0xFF16A34A)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        if (isTechStatusUpdating) {
                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.White)
                                        } else {
                                            Text(
                                                text = if (isTechnicianOnline) "رفتن به مرخصی" else "خروج از مرخصی",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isTech && currentUser?.isSuspended == true) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("⛔", fontSize = 18.sp)
                                        Text(
                                            "حساب کاربری تعلیق شده است",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF991B1B)
                                        )
                                    }
                                    Text(
                                        "امکان پذیرش سفارش جدید وجود ندارد. جهت بررسی با پشتیبانی تماس بگیرید.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF7F1D1D)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.checkTechnicianApprovalStatus { _, message ->
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFDC2626))
                                    ) {
                                        Text("🔄 استعلام وضعیت رفع تعلیق", fontSize = 11.sp, color = Color(0xFF991B1B), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (isTech && currentUser?.isApprovedUser != true) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFCE8)),
                                border = BorderStroke(1.dp, Color(0xFFFEF08A)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("⏳", fontSize = 18.sp)
                                        Text(
                                            "در انتظار بررسی و تایید مدارک",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF854D0E)
                                        )
                                    }
                                    Text(
                                        "مدارک شما در حال بررسی است. پس از تایید، سفارش‌های شهر ${currentUser?.city ?: ""} برای شما فعال خواهد شد.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF713F12)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.checkTechnicianApprovalStatus { _, message ->
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFCA8A04))
                                    ) {
                                        Text("🔄 استعلام وضعیت تایید مدارک", fontSize = 11.sp, color = Color(0xFF854D0E), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (repairOrders.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(if (isTech) (if (!isTechnicianOnline) "🏖️" else "📋") else "🔧", fontSize = 44.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isTech) (if (!isTechnicianOnline) "شما در حالت مرخصی هستید" else "سفارش تعمیری برای انجام وجود ندارد") else "سفارشی ثبت نشده است",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = CodyarTextPrimary
                                )
                                Text(
                                    text = if (isTech) (if (!isTechnicianOnline) "در زمان مرخصی، سفارش‌های جدید شهر دریافت نمی‌شوند." else "سفارش‌های جدید ارجاع شده از طرف مشتریان در این بخش قرار می‌گیرند") else "جهت ثبت درخواست با تکنسین تماس حاصل فرمایید",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 20.dp),
                                    textAlign = TextAlign.Center
                                )
                                if (isTech) {
                                    Button(
                                        onClick = { viewModel.loadRepairs() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("بروزرسانی سفارش‌ها")
                                    }
                                } else {
                                    Button(
                                        onClick = onNavigateToTechs,
                                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy)
                                    ) {
                                        Text("لیست تکنسین‌ها")
                                    }
                                }
                            }
                        }
                    } else {
                        items(repairOrders.size) { i ->
                        val o = repairOrders[i]
                        val rawStatus = (o.status ?: "").lowercase().trim()
                        val customLabel = o.status_label_fa ?: o.statusLabelFa

                        val (statusText, textCol, bgCol) = when {
                            !customLabel.isNullOrBlank() ->
                                Triple(customLabel, Color(0xFF1E8449), Color(0xFFEAFAF1))
                            rawStatus in listOf("assigned", "accepted", "تایید شده", "ارجاع به تکنسین", "اختصاص داده شد") ->
                                Triple("تکنسین تخصیص داده شد / در مسیر 👨‍🔧", Color(0xFF1E8449), Color(0xFFEAFAF1))
                            rawStatus in listOf("in_progress", "ongoing", "processing", "در حال انجام", "در مسیر") ->
                                Triple("در حال انجام تعمیرات 🛠️", Color(0xFF2563EB), Color(0xFFEFF6FF))
                            rawStatus in listOf("completed", "done", "تکمیل شده", "انجام شد") ->
                                Triple("انجام شده و تحویل داده شد ✅", Color(0xFF0F766E), Color(0xFFF0FDFA))
                            rawStatus in listOf("cancelled", "rejected", "لغو شده", "رد شد") ->
                                Triple("لغو شده ❌", Color(0xFFC0392B), Color(0xFFFDF0EE))
                            else ->
                                Triple("در انتظار تایید و تخصیص تکنسین ⏳", Color(0xFFD68910), Color(0xFFFEF9E7))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "سفارش اعزام تکنسین #${o.order_id ?: o.id ?: (i + 1)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CodyarTextPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(bgCol, RoundedCornerShape(7.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = textCol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                val orderIdToAct = o.resolvedOrderId.ifBlank { o.order_id ?: o.id ?: "" }
                                val isAssignedToCurrentTech = !o.technician_id.isNullOrBlank() && (o.technician_id == currentUser?.id || o.technician_phone == currentUser?.phone || o.technician_name == currentUser?.full_name)
                                val isAcceptedOrAssigned = rawStatus in listOf("assigned", "accepted", "تایید شده", "ارجاع به تکنسین", "اختصاص داده شد") || isAssignedToCurrentTech
                                val isOngoing = rawStatus in listOf("in_progress", "ongoing", "processing", "در حال انجام", "در مسیر")
                                val isDone = rawStatus in listOf("completed", "done", "تکمیل شده", "انجام شد", "cancelled", "rejected", "لغو شده")
                                val isCommissionPending = pendingCommissionOrders.contains(orderIdToAct)
                                // آدرس و شماره فقط وقتی آزاد می‌شود که سفارش واقعاً به این تکنسین تخصیص یافته باشد و در انتظار تایید فیش نباشد
                                val isUnlockedForTech = isAssignedToCurrentTech && (isAcceptedOrAssigned || isOngoing || isDone) && !isCommissionPending

                                val displayDesc = remember(o.resolvedDescription, isTech, isUnlockedForTech) {
                                    if (isTech && !isUnlockedForTech) {
                                        o.resolvedDescription
                                            .lines()
                                            .filterNot { line ->
                                                val l = line.lowercase()
                                                l.contains("تلفن") || l.contains("تماس") || l.contains("همراه") || l.contains("شماره")
                                            }
                                            .joinToString("\n")
                                            .replace(Regex("""(09|\+989|۰۹)[0-9۰-۹\s\-]{8,12}"""), "")
                                            .trim()
                                    } else {
                                        o.resolvedDescription
                                    }
                                }

                                val cleanInfo = remember(displayDesc, o.resolvedCategory, o.resolvedBrand, o.model, o.resolvedScheduledInfo, o.resolvedAddress) {
                                    parseAndCleanOrderInfo(
                                        rawDescription = displayDesc,
                                        resolvedCategory = o.resolvedCategory,
                                        resolvedBrand = o.resolvedBrand,
                                        model = o.model,
                                        resolvedScheduledInfo = o.resolvedScheduledInfo,
                                        resolvedAddress = o.resolvedAddress
                                    )
                                }

                                if (cleanInfo.deviceName.isNotBlank()) {
                                    Text(
                                        text = "🔧 دستگاه: ${cleanInfo.deviceName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CodyarNavy,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                    )
                                }

                                if (cleanInfo.problemDescription.isNotBlank()) {
                                    Text(
                                        text = cleanInfo.problemDescription,
                                        fontSize = 13.sp,
                                        color = CodyarTextPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .background(Color(0xFFF7F8FA), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    )
                                }

                                // ۱. ساعت حضور تکنسین برای تعمیرات در منزل مشتری
                                if (cleanInfo.visitTime.isNotBlank()) {
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("⏰", fontSize = 14.sp)
                                            Text(
                                                text = "ساعت حضور تکنسین در منزل: ${cleanInfo.visitTime}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1D4ED8)
                                            )
                                        }
                                    }
                                }

                                // ۲. زمان ثبت سفارش توسط مشتری از زمان گوشی (جهت جلوگیری از اختلاف زمان)
                                if (cleanInfo.orderPhoneTime.isNotBlank()) {
                                    Surface(
                                        color = Color(0xFFF0FDF4),
                                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("📱", fontSize = 14.sp)
                                            Text(
                                                text = "زمان ثبت سفارش توسط مشتری (زمان گوشی): ${cleanInfo.orderPhoneTime}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF166534)
                                            )
                                        }
                                    }
                                }

                                if (!isTech) {
                                    val custCoords = o.resolvedCoordinates
                                    val custAddress = cleanInfo.fullAddress.ifBlank { o.resolvedAddress.ifBlank { o.city ?: "" } }
                                    if (custAddress.isNotBlank() || o.resolvedPostalCode.isNotBlank() || o.resolvedAddressNote.isNotBlank() || custCoords != null) {
                                        TechnicianLocationCard(
                                            fullAddress = custAddress,
                                            postalCode = o.resolvedPostalCode,
                                            addressNote = o.resolvedAddressNote,
                                            coordinates = custCoords,
                                            orderTitle = "محل سفارش ${o.resolvedOrderId}",
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                } else if (!isUnlockedForTech) {
                                    if (!o.city.isNullOrBlank()) {
                                        Text(
                                            text = "📍 محدوده سفارش: ${o.city}",
                                            fontSize = 12.sp,
                                            color = CodyarTextSecondary,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    val hasLocationDetails = o.resolvedPostalCode.isNotBlank() ||
                                            o.resolvedAddressNote.isNotBlank() || o.resolvedCoordinates != null || cleanInfo.fullAddress.isNotBlank()
                                    if (hasLocationDetails) {
                                        Text(
                                            text = "🔒 آدرس دقیق، کد پستی و دکمه مسیریابی پس از پذیرش سفارش در دسترس قرار می‌گیرد",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFFB45309),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                }

                                // If Technician: Protect Customer Contact Info & Require 15% Commission Payment First (Card-to-Card)
                                if (isTech) {
                                    if (isUnlockedForTech) {
                                        // 🔓 Unlocked: Technician has paid 15% commission and claimed the order
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                                .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "👤 مشتری: ${o.resolvedCustomerName.ifBlank { "مشتری کدیار" }}",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF1E40AF),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (o.resolvedCustomerPhone.isNotBlank()) {
                                                    Text(
                                                        text = "📞 تلفن: ${o.resolvedCustomerPhone}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF3B82F6),
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }

                                            if (o.resolvedCustomerPhone.isNotBlank()) {
                                                Button(
                                                    onClick = {
                                                        try {
                                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${o.resolvedCustomerPhone}"))
                                                            dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                            context.startActivity(dialIntent)
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "شماره مشتری: ${o.resolvedCustomerPhone}", Toast.LENGTH_LONG).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                                        Text("تماس با مشتری", fontSize = 11.sp, color = Color.White)
                                                    }
                                                }
                                            }
                                        }

                                        // زیر شماره تماس مشتری: آدرس دقیق، کد پستی و دکمه باز کردن مسیریاب
                                        val techCoords = o.resolvedCoordinates
                                        val techAddress = cleanInfo.fullAddress.ifBlank { o.resolvedAddress.ifBlank { o.city ?: "" } }
                                        if (techAddress.isNotBlank() || o.resolvedPostalCode.isNotBlank() || o.resolvedAddressNote.isNotBlank() || techCoords != null) {
                                            TechnicianLocationCard(
                                                fullAddress = techAddress,
                                                postalCode = o.resolvedPostalCode,
                                                addressNote = o.resolvedAddressNote,
                                                coordinates = techCoords,
                                                orderTitle = "مشتری: ${o.resolvedCustomerName.ifBlank { "سفارش ${o.resolvedOrderId}" }}",
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    } else {
                                        // 🔒 Locked: Hidden until 15% commission is paid and approved by admin
                                        val isOrderPendingAdmin = pendingCommissionOrders.contains(orderIdToAct)
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            color = if (isOrderPendingAdmin) Color(0xFFFEF3C7) else Color(0xFFFFFBEB),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, if (isOrderPendingAdmin) Color(0xFFF59E0B) else Color(0xFFFDE68A))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                val rawPhone = o.resolvedCustomerPhone.trim()
                                                val phonePrefix = if (rawPhone.length >= 7) {
                                                    rawPhone.dropLast(4)
                                                } else if (rawPhone.isNotBlank()) {
                                                    rawPhone.take(4)
                                                } else {
                                                    "۰۹"
                                                }
                                                val phoneMask = if (rawPhone.length >= 7 || rawPhone.isNotBlank()) "****" else "********"

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "📞 شماره تماس مشتری:",
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF78350F)
                                                    )
                                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                        Text(
                                                            text = "$phonePrefix$phoneMask",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF78350F)
                                                        )
                                                    }
                                                }

                                                if (isOrderPendingAdmin) {
                                                    // وضعیت فیش ثبت‌شده و در انتظار تایید مدیریت
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFFFFFBEB), RoundedCornerShape(8.dp))
                                                            .padding(8.dp)
                                                    ) {
                                                        Text(
                                                            text = "⏳ فیش واریز شما ثبت گردید و در حال بررسی توسط مدیریت است. به محض تأیید مالی، شماره و آدرس مشتری در دسترس شما قرار می‌گیرد.",
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Color(0xFF92400E),
                                                            lineHeight = 17.sp
                                                        )
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            showUnlockOrderDialog = o
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "پرداخت کمیسیون کدیار24 و ثبت فیش",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Action buttons for Technician (Start, Complete, or Unlock)
                                    if (orderIdToAct.isNotBlank() && !isDone) {
                                        if (isUnlockedForTech) {
                                            if (isOngoing) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Button(
                                                    onClick = {
                                                        showCompleteOrderDialog = o
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("اتمام تعمیر و تحویل دستگاه به مشتری ✅", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else if (isAcceptedOrAssigned) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            viewModel.updateOrderStatus(orderIdToAct, "in_progress") { success, err ->
                                                                if (success) {
                                                                    Toast.makeText(context, "وضعیت به در حال انجام تغییر یافت", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    Toast.makeText(context, err ?: "خطا", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("اعزام به محل مشتری 🛠️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            showCompleteOrderDialog = o
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("اتمام کار و تحویل ✅", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // If Customer: show Assigned Technician Contact Info
                                if (!isTech && o.resolvedTechnicianName.isNotBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "👨‍🔧 تکنسین اعزامی: ${o.resolvedTechnicianName}",
                                                fontSize = 12.sp,
                                                color = Color(0xFF166534),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (o.resolvedTechnicianPhone.isNotBlank()) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${o.resolvedTechnicianPhone}"))
                                                        dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                        context.startActivity(dialIntent)
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "شماره تکنسین: ${o.resolvedTechnicianPhone}", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                                    Text("تماس با تکنسین", fontSize = 11.sp, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (o.resolvedDate.isNotBlank()) {
                                    Text(
                                        text = "📅 تاریخ ثبت: ${o.resolvedDate}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9AA3AF),
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }

                                // If Customer: Rating & Review for Technician
                                if (!isTech && (isDone || o.resolvedIsRated || o.resolvedTechnicianName.isNotBlank() || o.technician_id != null)) {
                                    val isRated = o.resolvedIsRated
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        color = if (isRated) Color(0xFFF0FDF4) else Color(0xFFFFFBEB),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (isRated) Color(0xFFBBF7D0) else Color(0xFFFDE68A))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isRated) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = String.format(java.util.Locale.US, "%.1f", o.resolvedRating),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFD97706)
                                                        )
                                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB000), modifier = Modifier.size(16.dp))
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "⭐ نظر و امتیاز شما ثبت شد",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF15803D)
                                                        )
                                                        Text("✓", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A))
                                                    }
                                                }

                                                // Sub-scores
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    Surface(
                                                        color = Color(0xFFDCFCE7),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "تخصص: ${o.resolvedSkillRating.toInt()}  |  اخلاق: ${o.resolvedEthicsRating.toInt()}  |  نظم: ${o.resolvedPunctualityRating.toInt()}",
                                                            fontSize = 10.sp,
                                                            color = Color(0xFF166534),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                if (!o.rating_comment.isNullOrBlank()) {
                                                    Text(
                                                        text = "\"${o.rating_comment}\"",
                                                        fontSize = 11.5.sp,
                                                        color = Color(0xFF374151),
                                                        textAlign = TextAlign.Right,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }

                                                OutlinedButton(
                                                    onClick = { showRateTechnicianDialog = o },
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.align(Alignment.End).height(30.dp)
                                                ) {
                                                    Text("ویرایش نظر و امتیاز ✏️", fontSize = 11.sp, color = Color(0xFF15803D))
                                                }
                                            } else {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("⭐", fontSize = 16.sp)
                                                    Text(
                                                        text = if (isDone) "خدمت به اتمام رسید! به عملکرد تکنسین امتیاز دهید" else "ارزیابی تخصص، اخلاق و فرایند کار تکنسین",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF92400E)
                                                    )
                                                }
                                                Text(
                                                    text = "امتیاز شما در کارنامه عمومی این تکنسین ثبت شده و برای سایر کاربران قابل مشاهده خواهد بود.",
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF78350F),
                                                    lineHeight = 16.sp
                                                )
                                                Button(
                                                    onClick = { showRateTechnicianDialog = o },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                        Text("ثبت نظر و امتیاز به تکنسین", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
            // Part Purchases Tab
            if (partPurchases.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📦", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("هیچ سفارش قطعه‌ای ثبت نشده است", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CodyarTextPrimary)
                    Text("با مراجعه به فروشگاه می‌توانید قطعه مورد نظر خود را سفارش دهید", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 20.dp), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(partPurchases.size) { i ->
                        val purchase = partPurchases[i]

                        // Status styling: pending -> در انتظار بررسی/پرداخت, approved/processing -> تایید شده (آماده‌سازی), sent/shipped -> ارسال شده به پست, completed/delivered -> تحویل مشتری
                        val rawStatus = (purchase.status).lowercase().trim()
                        val customLabel = purchase.resolvedStatusLabelFa

                        val (statusText, textCol, bgCol) = when {
                            !customLabel.isNullOrBlank() ->
                                Triple(customLabel, Color(0xFF1E8449), Color(0xFFEAFAF1))
                            rawStatus in listOf("approved", "accepted", "processing", "تایید شد", "تایید شده", "در حال آماده سازی", "در حال آماده‌سازی") ->
                                Triple("در حال آماده‌سازی در انبار ⚙️", Color(0xFF1E8449), Color(0xFFEAFAF1))
                            rawStatus in listOf("sent", "shipped", "posted", "ارسال شد", "ارسال شده", "ارسال به پست", "تحویل پست", "تیپاکس") ->
                                Triple("تحویل به پست / پیک 📦", Color(0xFF1D4ED8), Color(0xFFEFF6FF))
                            rawStatus in listOf("delivered", "completed", "تحویل شد", "تحویل داده شده") ->
                                Triple("تحویل داده شد ✅", Color(0xFF0F766E), Color(0xFFF0FDFA))
                            rawStatus in listOf("rejected", "cancelled", "رد شد", "لغو شده") ->
                                Triple("لغو سفارش ❌", Color(0xFFC0392B), Color(0xFFFDEDEC))
                            else ->
                                Triple("در انتظار بررسی و تایید مالی ⏳", Color(0xFFD68910), Color(0xFFFEF9E7))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = purchase.resolvedPartName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CodyarTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(bgCol, RoundedCornerShape(7.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = textCol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "تعداد: ${purchase.quantity} عدد",
                                        fontSize = 13.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Text(
                                        text = "مبلغ کل: ${formatToman(purchase.resolvedTotalPrice)} تومان",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CodyarRed
                                    )
                                }

                                // Tracking code box if present from website / postal dispatch
                                if (purchase.resolvedTrackingCode.isNotBlank()) {
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "کد رهگیری مرسوله (پست/تیپاکس):",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF1E40AF),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = purchase.resolvedTrackingCode,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF1D4ED8),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("tracking_code", purchase.resolvedTrackingCode)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "کد رهگیری کپی شد: ${purchase.resolvedTrackingCode}", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "کپی کد رهگیری", tint = Color(0xFF1D4ED8), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = Color(0xFFEAECEF)
                                )

                                if (purchase.resolvedDate.isNotBlank()) {
                                    Text(
                                        text = "📅 تاریخ سفارش: ${purchase.resolvedDate}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF718096),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                if (!purchase.address.isNullOrBlank()) {
                                    Text(
                                        text = "📍 آدرس ارسال: ${purchase.address}",
                                        fontSize = 12.sp,
                                        color = CodyarTextSecondary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                if (!purchase.notes.isNullOrBlank()) {
                                    Text(
                                        text = "📝 توضیحات: ${purchase.notes}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF718096)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for Technician to confirm order completion
    if (showCompleteOrderDialog != null) {
        val order = showCompleteOrderDialog!!
        val orderId = order.resolvedOrderId.ifBlank { order.order_id ?: order.id ?: "" }

        AlertDialog(
            onDismissRequest = { if (!isSubmittingComplete) showCompleteOrderDialog = null },
            icon = {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(36.dp))
            },
            title = {
                Text(
                    text = "ثبت اتمام کار و تحویل به مشتری",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "آیا تعمیر این دستگاه با موفقیت انجام شد و دستگاه به مشتری تحویل گردید؟",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("دستگاه: ${order.resolvedCategory} - ${order.resolvedBrand} ${order.model ?: ""}", fontSize = 12.sp, color = Color(0xFF166534))
                            Text("وضعیت کمیسیون: پیش‌تر با موفقیت تسویه و تایید شده است ✅", fontSize = 11.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (orderId.isBlank()) return@Button
                        isSubmittingComplete = true
                        viewModel.updateOrderStatus(orderId, "completed") { success, err ->
                            isSubmittingComplete = false
                            if (success) {
                                showCompleteOrderDialog = null
                                Toast.makeText(context, "سفارش با موفقیت به پایان رسید و بایگانی شد ✅", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, err ?: "خطا در اتمام سفارش", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSubmittingComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSubmittingComplete) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("بله، اتمام و بایگانی کار ✅", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompleteOrderDialog = null },
                    enabled = !isSubmittingComplete
                ) {
                    Text("انصراف")
                }
            }
        )
    }

    // Dialog for Technician to Pay 15% Commission (Card to Card) and Unlock Customer Contact Info
    if (showUnlockOrderDialog != null) {
        val order = showUnlockOrderDialog!!
        val orderId = order.resolvedOrderId.ifBlank { order.order_id ?: order.id ?: "" }
        val estCost = listOfNotNull(order.estimated_cost, order.estimatedCost, order.amount, order.price, order.cost).firstOrNull { it > 0 } ?: 300000L
        val commissionAmount = (estCost * 0.15).toLong().coerceAtLeast(35000L)
        val formattedEstCost = NumberFormat.getNumberInstance(Locale.US).format(estCost)
        val formattedCommission = NumberFormat.getNumberInstance(Locale.US).format(commissionAmount)

        val cardNum = bankCardInfo?.cardNumber ?: bankCardInfo?.card_number ?: "۶۱۰۴-۳۳۸۹-۶۱۱۲-۶۶۶۷"
        val cardHolderName = bankCardInfo?.cardHolder ?: bankCardInfo?.card_holder ?: "مهدی عباسی (کدیار۲۴)"
        val bankName = bankCardInfo?.bankName ?: bankCardInfo?.bank_name ?: "بانک ملت"

        AlertDialog(
            onDismissRequest = { if (!isSubmittingUnlock) showUnlockOrderDialog = null },
            icon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(36.dp))
            },
            title = {
                Text(
                    text = "مشاهده مشتری و پرداخت کمیسیون (۱۵٪)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "همانند سامانه‌های آچاره و خدمت‌ازما، جهت دریافت شماره تماس، آدرس دقیق و پذیرش این سفارش، مبلغ کمیسیون را به شماره کارت زیر کارت‌به‌کارت نمایید و کد رهگیری فیش را وارد کنید:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    // Order Summary & Commission Calculation
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("دستگاه:", fontSize = 11.5.sp, color = Color(0xFF92400E))
                                Text("${order.resolvedCategory} ${order.resolvedBrand}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78350F))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("اجرت برآوردی کار:", fontSize = 11.5.sp, color = Color(0xFF92400E))
                                Text("$formattedEstCost تومان", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78350F))
                            }
                            Divider(color = Color(0xFFFDE68A), thickness = 0.8.dp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("مبلغ کمیسیون واریزی (۱۵٪):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                Text("$formattedCommission تومان", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309))
                            }
                        }
                    }

                    // Bank Card Info Box with Copy Button
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("شماره کارت جهت واریز کمیسیون:", fontSize = 11.sp, color = Color(0xFF166534))
                                    Text(
                                        text = cardNum,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF15803D),
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "$cardHolderName - $bankName",
                                        fontSize = 11.sp,
                                        color = Color(0xFF166534)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Card Number", cardNum.replace("-", "").replace(" ", ""))
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "شماره کارت کپی شد", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "کپی شماره کارت", tint = Color(0xFF16A34A))
                                }
                            }
                        }
                    }

                    // Inputs for Tracking Code & Depositor
                    OutlinedTextField(
                        value = trackingNumberInput,
                        onValueChange = { trackingNumberInput = it },
                        label = { Text("شماره پیگیری / کد رهگیری واریز *") },
                        placeholder = { Text("مثلاً 12345678") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = depositorNameInput,
                        onValueChange = { depositorNameInput = it },
                        label = { Text("نام و نام‌خانوادگی صاحب کارت واریزکننده") },
                        placeholder = { Text("مثلاً علی رضایی") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (orderId.isBlank()) return@Button
                        if (trackingNumberInput.isBlank() || trackingNumberInput.length < 4) {
                            Toast.makeText(context, "لطفاً کد رهگیری فیش واریز را وارد فرمایید.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmittingUnlock = true
                        viewModel.unlockOrderWithCommission(
                            order = order,
                            trackingCode = trackingNumberInput.trim(),
                            depositorName = depositorNameInput.trim(),
                            commissionAmount = commissionAmount
                        ) { success, msgOrErr ->
                            isSubmittingUnlock = false
                            if (success) {
                                showUnlockOrderDialog = null
                                trackingNumberInput = ""
                                Toast.makeText(
                                    context,
                                    msgOrErr ?: "فیش واریز با موفقیت ثبت شد و پس از تأیید مدیریت، اطلاعات مشتری فعال خواهد شد.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, msgOrErr ?: "خطا در ثبت واریز کمیسیون", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSubmittingUnlock && trackingNumberInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSubmittingUnlock) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("ثبت فیش و ارسال به مدیریت ⏳", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUnlockOrderDialog = null },
                    enabled = !isSubmittingUnlock
                ) {
                    Text("انصراف")
                }
            }
        )
    }

    showRateTechnicianDialog?.let { orderToRate ->
        RateTechnicianDialog(
            order = orderToRate,
            onDismiss = { showRateTechnicianDialog = null },
            onSubmit = { overall, skill, ethics, punct, comment ->
                viewModel.rateTechnicianOrder(
                    order = orderToRate,
                    overallRating = overall,
                    skillRating = skill,
                    ethicsRating = ethics,
                    punctualityRating = punct,
                    comment = comment
                ) { success, msg ->
                    showRateTechnicianDialog = null
                    Toast.makeText(context, msg ?: if (success) "امتیاز شما با موفقیت ثبت شد" else "خطا در ثبت امتیاز", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

/**
 * مدل داده‌ای تفکیک‌شده و بدون تکرار اطلاعات سفارش برای جلوگیری از نمایش تکراری خرابی، دستگاه و تاریخ
 */
data class CleanOrderDisplay(
    val problemDescription: String,
    val deviceName: String,
    val visitTime: String,
    val orderPhoneTime: String,
    val fullAddress: String
)

/**
 * پاکسازی متن سفارش و استخراج داده‌های دستگاه، زمان مراجعه و آدرس از متن‌های مرکب گذشته
 */
fun parseAndCleanOrderInfo(
    rawDescription: String,
    resolvedCategory: String,
    resolvedBrand: String,
    model: String?,
    resolvedScheduledInfo: String,
    resolvedAddress: String
): CleanOrderDisplay {
    var extractedDevice: String? = null
    var extractedVisitTime: String? = null
    var extractedPhoneTime: String? = null
    var extractedAddr: String? = null
    val cleanProblemLines = mutableListOf<String>()

    for (rawLine in rawDescription.lines()) {
        val line = rawLine.trim()
        if (line.isBlank()) continue
        when {
            line.startsWith("دستگاه و برند:") || line.startsWith("دستگاه:") || line.startsWith("نوع دستگاه:") -> {
                val value = line.substringAfter(":").trim()
                if (value.isNotBlank() && value != "عمومی") {
                    extractedDevice = value
                }
            }
            line.startsWith("زمان هماهنگ‌شده حضور تکنسین در منزل:") || line.startsWith("زمان حضور تکنسین در منزل:") ||
            line.startsWith("زمان پیشنهادی مراجعه کارشناس:") || line.startsWith("زمان پیشنهادی:") || line.startsWith("زمان مراجعه:") -> {
                val value = line.substringAfter(":").trim()
                if (value.isNotBlank()) {
                    extractedVisitTime = value
                }
            }
            line.startsWith("ساعت ثبت سفارش توسط مشتری") || line.startsWith("[ساعت ثبت سفارش توسط مشتری") ||
            line.startsWith("ساعت ثبت سفارش از زمان گوشی") || line.startsWith("[ساعت ثبت سفارش از زمان گوشی") ||
            line.startsWith("ساعت ثبت سفارش:") || line.startsWith("[ساعت ثبت سفارش:") -> {
                val value = line.removePrefix("[").removeSuffix("]").substringAfter(":").trim()
                if (value.isNotBlank()) {
                    extractedPhoneTime = value
                }
            }
            line.startsWith("آدرس محل:") || line.startsWith("آدرس:") || line.startsWith("آدرس پستی:") || line.startsWith("آدرس دقیق:") -> {
                val value = line.substringAfter(":").trim()
                if (value.isNotBlank()) {
                    extractedAddr = value
                }
            }
            line.startsWith("لوکیشن روی نقشه:") || line.startsWith("لوکیشن:") || line.startsWith("http://") || line.startsWith("https://") -> {
                // در کارت موقعیت و نقشه نمایش داده می‌شود
            }
            line.startsWith("شرح خرابی:") || line.startsWith("شرح مشکل:") || line.startsWith("مشکل:") -> {
                val value = line.substringAfter(":").trim()
                if (value.isNotBlank()) {
                    cleanProblemLines.add(value)
                }
            }
            else -> {
                cleanProblemLines.add(line)
            }
        }
    }

    val existingCatBrand = listOf(resolvedCategory, resolvedBrand, model)
        .filter { !it.isNullOrBlank() && it != "عمومی" }
        .joinToString(" - ")

    val finalDevice = when {
        existingCatBrand.isNotBlank() -> existingCatBrand
        !extractedDevice.isNullOrBlank() -> extractedDevice
        resolvedCategory.isNotBlank() && resolvedCategory != "عمومی" -> resolvedCategory
        else -> ""
    }

    var finalVisit = extractedVisitTime ?: ""
    var finalPhoneTime = extractedPhoneTime ?: ""

    if (resolvedScheduledInfo.isNotBlank()) {
        if (resolvedScheduledInfo.contains("ثبت سفارش:") || resolvedScheduledInfo.contains("ساعت ثبت")) {
            val parts = resolvedScheduledInfo.split("(", ")", "|")
            for (p in parts) {
                val pt = p.trim()
                if (pt.contains("ثبت سفارش") || pt.contains("ساعت ثبت")) {
                    if (finalPhoneTime.isBlank()) {
                        finalPhoneTime = pt.removePrefix("ثبت سفارش:").removePrefix("ساعت ثبت:").removePrefix("ثبت سفارش").trim()
                    }
                } else if (pt.isNotBlank() && finalVisit.isBlank() && !pt.contains("ساعت ثبت")) {
                    finalVisit = pt
                }
            }
        } else if (finalVisit.isBlank()) {
            finalVisit = resolvedScheduledInfo
        }
    }

    val finalAddr = when {
        resolvedAddress.isNotBlank() -> resolvedAddress
        !extractedAddr.isNullOrBlank() -> extractedAddr
        else -> ""
    }

    return CleanOrderDisplay(
        problemDescription = cleanProblemLines.joinToString("\n").trim(),
        deviceName = finalDevice,
        visitTime = finalVisit,
        orderPhoneTime = finalPhoneTime,
        fullAddress = finalAddr
    )
}
