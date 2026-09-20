package com.example.ui.screens

import android.Manifest
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.utils.SmsOtpHelper
import com.example.ui.AssistantViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthDialog(
    showAuthDialog: Boolean,
    onDismiss: () -> Unit,
    authMode: String,
    onAuthModeChange: (String) -> Unit,
    authRole: String,
    onAuthRoleChange: (String) -> Unit,
    authPhone: String,
    onPhoneChange: (String) -> Unit,
    authPassword: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    authName: String,
    onNameChange: (String) -> Unit,
    authCity: String,
    onCityChange: (String) -> Unit,
    authDistrict: String,
    onDistrictChange: (String) -> Unit,
    authSelectedCategories: Set<String>,
    onCategoriesChange: (Set<String>) -> Unit,
    techCategorySearch: String,
    onCategorySearchChange: (String) -> Unit,
    techUploadedDocs: List<String>,
    onUploadedDocsChange: (List<String>) -> Unit,
    techUploadedDocUris: Map<String, Uri>,
    onUploadedDocUrisChange: (Map<String, Uri>) -> Unit,
    techDocTitleInput: String,
    onDocTitleInputChange: (String) -> Unit,
    currentDocTypeToPick: String,
    onDocTypeToPickChange: (String) -> Unit,
    docPickerLauncher: ActivityResultLauncher<String>,
    viewModel: AssistantViewModel,
    isAuthLoading: Boolean,
    authError: String?
) {
    if (!showAuthDialog) return
    val context = LocalContext.current

    var isForgotPasswordMode by remember { mutableStateOf(false) }
    var otpCodeInput by remember { mutableStateOf("") }
    var otpTimerSeconds by remember { mutableIntStateOf(120) }
    var autoFilledSms by remember { mutableStateOf(false) }
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
        if (isGranted) {
            val inboxOtp = SmsOtpHelper.readRecentOtpFromInbox(context)
            if (!inboxOtp.isNullOrBlank() && otpCodeInput.isBlank()) {
                otpCodeInput = inboxOtp
                autoFilledSms = true
            }
        }
    }

    // دریافت خودکار کد تایید پیامک‌شده در صورتی که سیم‌کارت روی همین گوشی باشد
    DisposableEffect(isForgotPasswordMode) {
        var smsReceiver: BroadcastReceiver? = null
        if (isForgotPasswordMode) {
            // ۱. بررسی پیامک‌های دریافتی اخیر از اینباکس
            val inboxOtp = SmsOtpHelper.readRecentOtpFromInbox(context)
            if (!inboxOtp.isNullOrBlank() && otpCodeInput.isBlank()) {
                otpCodeInput = inboxOtp
                autoFilledSms = true
            }

            // ۲. ثبت لیسنر آنلاین برای شنود پیامک دریافتی
            smsReceiver = SmsOtpHelper.registerSmsListener(context) { code ->
                otpCodeInput = code
                autoFilledSms = true
                Toast.makeText(context, "کد تایید پیامک‌شده ($code) به‌صورت خودکار درج شد ✓", Toast.LENGTH_SHORT).show()
                // اعتبارسنجی خودکار بدون معطلی کاربر
                viewModel.verifyOtp(authPhone, code, authPassword.ifBlank { null }) { success, msg ->
                    if (success) {
                        isForgotPasswordMode = false
                        onDismiss()
                        Toast.makeText(context, msg ?: "ورود به حساب با موفقیت انجام شد!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        onDispose {
            SmsOtpHelper.unregisterSmsListener(context, smsReceiver)
        }
    }

    LaunchedEffect(isForgotPasswordMode, otpTimerSeconds) {
        if (isForgotPasswordMode && otpTimerSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            otpTimerSeconds -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (authMode == "login") "ورود به حساب کاربری" else "ثبت‌نام حساب جدید",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CodyarNavy
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "بستن")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Toggle bar for Login / Register
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F2F5), RoundedCornerShape(9.dp))
                        .padding(3.dp)
                ) {
                    Button(
                        onClick = { onAuthModeChange("login") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (authMode == "login") Color.White else Color.Transparent,
                            contentColor = if (authMode == "login") CodyarTextPrimary else CodyarTextSecondary
                        ),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("ورود", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onAuthModeChange("register") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (authMode == "register") Color.White else Color.Transparent,
                            contentColor = if (authMode == "register") CodyarTextPrimary else CodyarTextSecondary
                        ),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("ثبت‌نام", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }



                // Sub-toggle for Customer / Technician role selection (Login and Registration)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (authMode == "register") "نوع حساب کاربری برای ثبت‌نام:" else "ورود در نقش:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CodyarNavy,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Technician Option
                        Surface(
                            onClick = { onAuthRoleChange("technician") },
                            shape = RoundedCornerShape(10.dp),
                            color = if (authRole == "technician") CodyarNavy else Color.White,
                            border = BorderStroke(
                                2.dp,
                                if (authRole == "technician") CodyarNavy else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = if (authRole == "technician") Color.White else CodyarNavy,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "تکنسین تعمیرات",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (authRole == "technician") Color.White else CodyarNavy
                                    )
                                    Text(
                                        text = "دریافت سفارش و تعمیرات",
                                        fontSize = 9.sp,
                                        color = if (authRole == "technician") Color.White.copy(alpha = 0.8f) else Color.Gray
                                    )
                                }
                            }
                        }

                        // Customer Option
                        Surface(
                            onClick = { onAuthRoleChange("customer") },
                            shape = RoundedCornerShape(10.dp),
                            color = if (authRole == "customer") CodyarRed else Color.White,
                            border = BorderStroke(
                                2.dp,
                                if (authRole == "customer") CodyarRed else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (authRole == "customer") Color.White else CodyarRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "مشتری / کاربر",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (authRole == "customer") Color.White else CodyarRed
                                    )
                                    Text(
                                        text = "ثبت سفارش و عیب‌یابی",
                                        fontSize = 9.sp,
                                        color = if (authRole == "customer") Color.White.copy(alpha = 0.8f) else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                if (authMode == "register") {
                    OutlinedTextField(
                        value = authName,
                        onValueChange = onNameChange,
                        placeholder = { Text("نام و نام خانوادگی (مثال: علی محمدی)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(9.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = authPhone,
                    onValueChange = onPhoneChange,
                    placeholder = { Text(if (authMode == "register") "شماره تلفن همراه (مثال: ۰۹۱۲۰۰۰۰۰۰۰)" else "شماره تلفن همراه") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = authPassword,
                    onValueChange = onPasswordChange,
                    placeholder = { Text(if (authMode == "register") "کلمه عبور (حداقل ۶ کاراکتر)" else "کلمه عبور") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp),
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { onPasswordVisibleChange(!isPasswordVisible) }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "مخفی کردن رمز" else "نمایش رمز"
                            )
                        }
                    }
                )

                if (authMode == "login" && isForgotPasswordMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (autoFilledSms) Color(0xFFF0FDF4) else Color(0xFFEFF6FF), RoundedCornerShape(10.dp))
                            .border(1.dp, if (autoFilledSms) Color(0xFF86EFAC) else Color(0xFF93C5FD), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "کد ۵ رقمی تایید پیامک‌شده:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (autoFilledSms) Color(0xFF166534) else Color(0xFF1E40AF)
                            )
                            if (autoFilledSms) {
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                ) {
                                    Text(
                                        text = "✓ پر شده خودکار از پیامک",
                                        fontSize = 10.sp,
                                        color = Color(0xFF166534),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = otpCodeInput,
                            onValueChange = { if (it.length <= 6) otpCodeInput = SmsOtpHelper.normalizeDigits(it) },
                            placeholder = { Text("کد تایید ۵ رقمی (مثلاً 12345)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                if (autoFilledSms && otpCodeInput.isNotBlank()) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "کد تایید خودکار پر شد",
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )

                        // وضعیت سیم‌کارت و دریافت پیامک خودکار
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (autoFilledSms) Color(0xFFDCFCE7) else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (autoFilledSms) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(if (autoFilledSms) "✅" else "📲", fontSize = 13.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (autoFilledSms) {
                                            "کد تایید با موفقیت از پیامک همین گوشی دریافت و خودکار ثبت شد."
                                        } else {
                                            "در صورت قرار داشتن سیم‌کارت روی همین گوشی، پیامک خودکار خوانده و پر می‌شود."
                                        },
                                        fontSize = 10.5.sp,
                                        color = if (autoFilledSms) Color(0xFF166534) else Color(0xFF475569),
                                        fontWeight = if (autoFilledSms) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (!hasSmsPermission && !autoFilledSms) {
                                        TextButton(
                                            onClick = { smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text(
                                                text = "⚡ فعال‌سازی دریافت خودکار پیامک",
                                                fontSize = 10.sp,
                                                color = Color(0xFF0284C7),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (otpTimerSeconds > 0) {
                                val mins = otpTimerSeconds / 60
                                val secs = otpTimerSeconds % 60
                                val timeStr = String.format("%02d:%02d", mins, secs)
                                Text(
                                    text = "زمان باقی‌مانده تا ارسال مجدد: $timeStr",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1E3A8A),
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                TextButton(
                                    onClick = {
                                        if (authPhone.isNotBlank()) {
                                            otpTimerSeconds = 120
                                            autoFilledSms = false
                                            viewModel.sendOtp(authPhone) { _, msg, directOtp ->
                                                if (!directOtp.isNullOrBlank()) {
                                                    otpCodeInput = directOtp
                                                    autoFilledSms = true
                                                }
                                                Toast.makeText(context, msg ?: "کد مجدداً ارسال شد", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("ارسال مجدد کد پیامک", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CodyarRed)
                                }
                            }
                        }
                    }
                }

                // City field for both customer and technician registration
                if (authMode == "register") {
                    var cityExpanded by remember { mutableStateOf(false) }
                    val liveCitiesList by viewModel.liveCities.collectAsState()
                    val filteredCities = remember(authCity, liveCitiesList) {
                        val cleanQuery = authCity.trim().lowercase()
                        if (cleanQuery.isEmpty()) {
                            liveCitiesList.filter { it != "همه" }
                        } else {
                            liveCitiesList.filter { it != "همه" && it.lowercase().contains(cleanQuery) }
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = authCity,
                            onValueChange = { 
                                onCityChange(it)
                                cityExpanded = true
                            },
                            placeholder = { Text("شهر محل فعالیت / سکونت (انتخاب یا تایپ)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        cityExpanded = true
                                    }
                                },
                            shape = RoundedCornerShape(9.dp),
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (authCity.isNotEmpty()) {
                                        IconButton(onClick = {
                                            onCityChange("")
                                            cityExpanded = true
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "پاک کردن"
                                            )
                                        }
                                    }
                                    IconButton(onClick = { cityExpanded = !cityExpanded }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "انتخاب شهر ومحله"
                                        )
                                    }
                                }
                            }
                        )
                        
                        if (cityExpanded && filteredCities.isNotEmpty()) {
                            DropdownMenu(
                                expanded = cityExpanded,
                                onDismissRequest = { cityExpanded = false },
                                properties = PopupProperties(focusable = false),
                                modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 220.dp)
                            ) {
                                filteredCities.forEach { cityItem ->
                                    DropdownMenuItem(
                                        text = { Text(cityItem, fontSize = 12.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                                        onClick = {
                                            onCityChange(cityItem)
                                            cityExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // District field for technician
                if (authMode == "register" && authRole == "technician") {
                    OutlinedTextField(
                        value = authDistrict,
                        onValueChange = onDistrictChange,
                        placeholder = { Text("محدوده / محلات فعالیت * (الزامی - مثلا: کل شهر یا محله‌ها)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(9.dp),
                        singleLine = true
                    )
                }

                // Technician-specific fields (Categories dynamically loaded from site API with Search)
                if (authMode == "register" && authRole == "technician") {
                    val dbCategories by viewModel.liveCategories.collectAsState()
                    val rawCategories = remember(dbCategories) {
                        dbCategories.filter { it != "همه" && it != "همه دستگاه‌ها" && it.isNotBlank() }.distinct()
                    }

                    val filteredCategories = remember(rawCategories, techCategorySearch) {
                        val normQuery = normalizePersianText(techCategorySearch)
                        if (normQuery.isEmpty()) {
                            rawCategories
                        } else {
                            rawCategories.filter { cat ->
                                normalizePersianText(cat).contains(normQuery)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("زمینه‌های تخصصی تعمیرات:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CodyarNavy)
                        Text("${authSelectedCategories.size} مورد انتخاب شده", fontSize = 10.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                    }
                    
                    OutlinedTextField(
                        value = techCategorySearch,
                        onValueChange = onCategorySearchChange,
                        placeholder = { Text("🔍 فیلتر و جستجوی سریع تخصص (مثلا: لباسشویی، پکیج، کولر...)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (techCategorySearch.isNotEmpty()) {
                                IconButton(onClick = { onCategorySearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن جستجو")
                                }
                            }
                        }
                    )

                    if (authSelectedCategories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                "⚠️ هنوز هیچ تخصص انتخاب نکرده‌اید (باید حداقل ۱ تخصص را از لیست زیر انتخاب کنید).",
                                color = Color(0xFF991B1B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (filteredCategories.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFFBEB), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFFCD34D), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "تخصصی با عنوان «$techCategorySearch» در لیست پیش‌فرض پیدا نشد.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = {
                                    val newCat = techCategorySearch.trim()
                                    if (newCat.isNotEmpty()) {
                                        onCategoriesChange(authSelectedCategories + newCat)
                                        onCategorySearchChange("")
                                        Toast.makeText(context, "تخصص «$newCat» به لیست تخصص‌های شما اضافه شد", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("➕ افزودن «$techCategorySearch» به تخصص‌های من", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredCategories.forEach { cat ->
                                val isSelected = authSelectedCategories.contains(cat)
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) CodyarRed else Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) CodyarRed else Color(0xFFCBD5E1),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            onCategoriesChange(
                                                if (isSelected) authSelectedCategories - cat else authSelectedCategories + cat
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isSelected) "✓ $cat" else cat,
                                        color = if (isSelected) Color.White else Color(0xFF475569),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Document Upload Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("📄", fontSize = 20.sp)
                                Column {
                                    Text(
                                        "تصویر کارت ملی و گواهی مهارت فنی *",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = CodyarNavy
                                    )
                                    Text(
                                        "جهت ممیزی و فعال‌سازی توسط مدیر، روی کارت‌ها کلیک کرده یا مدرک جدید اضافه کنید:",
                                        fontSize = 10.sp,
                                        color = CodyarTextSecondary
                                    )
                                }
                            }

                            // Quick cards
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val hasCard1 = techUploadedDocs.any { it.contains("کارت ملی") }
                                val uri1 = techUploadedDocUris.entries.firstOrNull { it.key.contains("کارت ملی") }?.value
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (hasCard1) Color(0xFFDCFCE7) else Color.White, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (hasCard1) Color(0xFF22C55E) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                        .clickable {
                                            onDocTypeToPickChange("کارت ملی هوشمند")
                                            try {
                                                docPickerLauncher.launch("image/*")
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "خطا در باز کردن گالری گوشی: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (uri1 != null) {
                                            AsyncImage(
                                                model = uri1,
                                                contentDescription = "کارت ملی",
                                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text("🪪", fontSize = 20.sp)
                                        }
                                        Text("کارت ملی", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                        Text(if (hasCard1) "✓ فایل انتخاب شد" else "➕ انتخاب از گوشی", fontSize = 9.sp, color = if (hasCard1) Color(0xFF15803D) else CodyarRed, fontWeight = FontWeight.Bold)
                                    }
                                }

                                val hasCard2 = techUploadedDocs.any { it.contains("فنی") || it.contains("مدرک") }
                                val uri2 = techUploadedDocUris.entries.firstOrNull { it.key.contains("فنی") || it.key.contains("مدرک") }?.value
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (hasCard2) Color(0xFFDCFCE7) else Color.White, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (hasCard2) Color(0xFF22C55E) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                        .clickable {
                                            onDocTypeToPickChange("گواهی فنی‌وحرفه‌ای")
                                            try {
                                                docPickerLauncher.launch("image/*")
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "خطا در باز کردن گالری گوشی: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (uri2 != null) {
                                            AsyncImage(
                                                model = uri2,
                                                contentDescription = "گواهی فنی",
                                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text("🛠️", fontSize = 20.sp)
                                        }
                                        Text("گواهی فنی", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                        Text(if (hasCard2) "✓ فایل انتخاب شد" else "➕ انتخاب از گوشی", fontSize = 9.sp, color = if (hasCard2) Color(0xFF15803D) else CodyarRed, fontWeight = FontWeight.Bold)
                                    }
                                }

                                val hasCard3 = techUploadedDocs.any { it.contains("جواز") || it.contains("کسب") || it.contains("اصناف") }
                                val uri3 = techUploadedDocUris.entries.firstOrNull { it.key.contains("جواز") || it.key.contains("کسب") || it.key.contains("اصناف") }?.value
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (hasCard3) Color(0xFFDCFCE7) else Color.White, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (hasCard3) Color(0xFF22C55E) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                        .clickable {
                                            onDocTypeToPickChange("جواز کسب کار / کارت اصناف")
                                            try {
                                                docPickerLauncher.launch("image/*")
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "خطا در باز کردن گالری گوشی: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (uri3 != null) {
                                            AsyncImage(
                                                model = uri3,
                                                contentDescription = "جواز کسب",
                                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text("🏢", fontSize = 20.sp)
                                        }
                                        Text("جواز کسب", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                        Text(if (hasCard3) "✓ فایل انتخاب شد" else "➕ انتخاب از گوشی", fontSize = 9.sp, color = if (hasCard3) Color(0xFF15803D) else CodyarRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Add custom doc
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = techDocTitleInput,
                                    onValueChange = onDocTitleInputChange,
                                    placeholder = { Text("عنوان مدرک تکمیلی (مثلاً: عدم سوءپیشینه)", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        val t = techDocTitleInput.trim()
                                        onDocTypeToPickChange(if (t.isNotEmpty()) t else "مدرک تکنسین")
                                        try {
                                            docPickerLauncher.launch("image/*")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "خطا در باز کردن گالری گوشی: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("📁 انتخاب تصویر فایل", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Attached documents overview
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("مدارک پیوست‌شده شما جهت بررسی مدیریت:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                if (techUploadedDocs.isEmpty()) {
                                    Text("⚠️ هنوز مدرکی از حافظه گوشی پیوست نشده است. لطفاً روی یکی از کارت‌های بالا کلیک کرده و عکس مدرک را انتخاب کنید.", fontSize = 9.sp, color = Color(0xFFDC2626))
                                } else {
                                    techUploadedDocs.forEach { docName ->
                                        val docUri = techUploadedDocUris[docName]
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White, RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (docUri != null) {
                                                    AsyncImage(
                                                        model = docUri,
                                                        contentDescription = docName,
                                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Text("✅", fontSize = 11.sp)
                                                }
                                                Column {
                                                    Text(docName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                                    if (docUri != null) {
                                                        Text("📁 فایل واقعی انتخاب شد: ${docUri.lastPathSegment ?: "مدرک پیوست شده"}", fontSize = 8.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    onUploadedDocsChange(techUploadedDocs.filter { it != docName })
                                                    onUploadedDocUrisChange(techUploadedDocUris - docName)
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "حذف مدرک", tint = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (authError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFDF0EE), RoundedCornerShape(7.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = authError,
                            color = CodyarRed,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (authMode == "login") {
                if (!isForgotPasswordMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (authPhone.isBlank() || authPassword.isBlank()) {
                                    Toast.makeText(context, "لطفاً شماره و رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.login(authPhone, authPassword, authRole) { success, err ->
                                    if (success) {
                                        onDismiss()
                                        Toast.makeText(context, "خوش آمدید!", Toast.LENGTH_SHORT).show()
                                    } else if (!err.isNullOrBlank()) {
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("login_button"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            enabled = !isAuthLoading
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Text("ورود به حساب کاربری", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = {
                                if (authPhone.isBlank()) {
                                    Toast.makeText(context, "لطفاً ابتدا شماره موبایل را وارد کنید", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isForgotPasswordMode = true
                                otpCodeInput = ""
                                autoFilledSms = false
                                otpTimerSeconds = 120

                                // اگر دسترسی دریافت پیامک داده نشده باشد، از کاربر درخواست می‌شود تا خودکار پر شود
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                                    smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                }

                                viewModel.sendOtp(authPhone) { success, msg, directOtp ->
                                    if (!directOtp.isNullOrBlank()) {
                                        otpCodeInput = directOtp
                                        autoFilledSms = true
                                    }
                                    Toast.makeText(context, msg ?: if (success) "کد تایید پیامک شد" else "خطا در ارسال پیامک", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("forgot_password_button"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            enabled = !isAuthLoading
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فراموشی رمز عبور", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (otpCodeInput.isBlank()) {
                                    Toast.makeText(context, "لطفاً کد تایید ۵ رقمی را وارد کنید", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.verifyOtp(authPhone, otpCodeInput, authPassword.ifBlank { null }) { success, msg ->
                                    if (success) {
                                        isForgotPasswordMode = false
                                        onDismiss()
                                        Toast.makeText(context, msg ?: "تایید کد با موفقیت انجام شد!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, msg ?: "کد وارد شده نامعتبر است", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("verify_otp_button"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            enabled = !isAuthLoading
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Text("تایید کد و ورود", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = {
                                isForgotPasswordMode = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            enabled = !isAuthLoading
                        ) {
                            Text("انصراف", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        val cleanName = authName.trim()
                        val cleanPhone = authPhone.trim()
                        val cleanPassword = authPassword.trim()
                        val cleanCity = authCity.trim()
                        val cleanDistrict = authDistrict.trim()

                        if (cleanName.isBlank()) {
                            Toast.makeText(context, "لطفاً نام و نام خانوادگی را وارد کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (cleanPhone.isBlank()) {
                            Toast.makeText(context, "لطفاً شماره موبایل را وارد کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (cleanPhone.length < 10) {
                            Toast.makeText(context, "لطفاً شماره موبایل معتبر (مثلاً 0912...) وارد کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (cleanPassword.isBlank()) {
                            Toast.makeText(context, "لطفاً رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (cleanPassword.length < 4) {
                            Toast.makeText(context, "رمز عبور باید حداقل ۴ کاراکتر باشد", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (cleanCity.isBlank()) {
                            Toast.makeText(context, "لطفاً شهر محل سکونت/فعالیت را وارد یا انتخاب کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (authRole == "technician") {
                            if (cleanDistrict.isBlank()) {
                                Toast.makeText(context, "لطفاً محدوده / محلات فعالیت تکنسین را وارد کنید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (authSelectedCategories.isEmpty()) {
                                Toast.makeText(context, "لطفاً حداقل یک تخصص از لیست انتخاب کنید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (techUploadedDocs.isEmpty()) {
                                Toast.makeText(context, "لطفاً تصویر مدارک (کارت ملی، گواهی فنی یا جواز کسب) را پیوست نمایید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }
                        viewModel.register(
                            phone = cleanPhone,
                            pass = cleanPassword,
                            name = cleanName,
                            role = authRole,
                            city = cleanCity,
                            categories = if (authRole == "technician") authSelectedCategories.toList() else null,
                            district = if (authRole == "technician") cleanDistrict else null,
                            documents = techUploadedDocs,
                            documentImages = if (authRole == "technician") {
                                techUploadedDocs.map { docName ->
                                    val uri = techUploadedDocUris[docName]
                                    if (uri != null) {
                                        compressAndEncodeUriToBase64(context, uri)
                                    } else {
                                        ""
                                    }
                                }
                            } else null
                        ) { success, err ->
                            if (success) {
                                onDismiss()
                                val welcomeMsg = if (authRole == "technician") {
                                    "ثبت‌نام تکنسین انجام شد! مدارک شما جهت تایید ارسال گردید."
                                } else {
                                    "ثبت‌نام مشتری با موفقیت انجام شد!"
                                }
                                Toast.makeText(context, welcomeMsg, Toast.LENGTH_LONG).show()
                            } else if (!err.isNullOrBlank()) {
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (authRole == "technician") CodyarNavy else CodyarRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_button"),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isAuthLoading
                ) {
                    if (isAuthLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (authRole == "technician") Icons.Default.Build else Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (authRole == "technician") "ثبت‌نام و ایجاد حساب متخصص" else "ثبت‌نام و ایجاد حساب کاربری",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    )
}
