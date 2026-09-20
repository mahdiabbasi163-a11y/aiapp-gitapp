package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.*
import com.example.ui.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onPurchasePlan: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Screen navigation state
    // "home", "search", "problems", "store", "profile", "technicians", "orders", "ai_chat"
    var activeTab by remember { mutableStateOf("home") }

    // Dialog & Sheet states
    var showAuthDialog by remember { mutableStateOf(false) }
    var authMode by remember { mutableStateOf("login") } // "login" or "register"

    var showCartDialog by remember { mutableStateOf(false) }
    var showWebCheckout by remember { mutableStateOf(false) }
    var cartStep by remember { mutableStateOf("cart") } // "cart" or "payment"

    var showPlansDialog by remember { mutableStateOf(false) }

    // Detail view states
    var selectedErrorDetail by remember { mutableStateOf<KodyarErrorCode?>(null) }
    var selectedProblemDetail by remember { mutableStateOf<KodyarCommonProblem?>(null) }

    // Restricted access dialogs
    var showRegisterRequiredDialog by remember { mutableStateOf(false) }
    var showPremiumRequiredDialog by remember { mutableStateOf(false) }

    // Disclaimer / Terms of Use modal states
    var showDisclaimerModal by remember { mutableStateOf(false) }
    var disclaimerChecked by remember { mutableStateOf(false) }
    val isDisclaimerAccepted by viewModel.isDisclaimerAccepted.collectAsState()

    // Form inputs
    var authPhone by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var authName by remember { mutableStateOf("") }
    var authRole by remember { mutableStateOf("customer") } // "customer" or "technician"
    var authCity by remember { mutableStateOf("") }
    var authDistrict by remember { mutableStateOf("") }
    var authSelectedCategories by remember { mutableStateOf(setOf<String>()) }
    var techCategorySearch by remember { mutableStateOf("") }
    var docNationalCardUploaded by remember { mutableStateOf(false) }
    var docTechnicalCertUploaded by remember { mutableStateOf(false) }
    var docTradeLicenseUploaded by remember { mutableStateOf(false) }
    var techDocTitleInput by remember { mutableStateOf("") }
    var techUploadedDocs by remember { mutableStateOf(listOf<String>()) }
    var currentDocTypeToPick by remember { mutableStateOf("") }
    var techUploadedDocUris by remember { mutableStateOf(mapOf<String, Uri>()) }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val docName = if (currentDocTypeToPick.isNotBlank()) currentDocTypeToPick else "مدرک تکنسین"
            techUploadedDocUris = techUploadedDocUris + (docName to uri)
            if (!techUploadedDocs.contains(docName)) {
                techUploadedDocs = techUploadedDocs + docName
            }
            if (docName.contains("کارت ملی")) docNationalCardUploaded = true
            if (docName.contains("فنی") || docName.contains("مدرک")) docTechnicalCertUploaded = true
            if (docName.contains("جواز") || docName.contains("کسب") || docName.contains("اصناف")) docTradeLicenseUploaded = true
            Toast.makeText(context, "تصویر مدرک «$docName» از گالری گوشی انتخاب و پیوست شد", Toast.LENGTH_SHORT).show()
        }
    }

    // Live variables from ViewModel
    val currentUser by viewModel.currentUser.collectAsState()
    val isDatabaseLoading by viewModel.isDatabaseLoading.collectAsState()
    val liveErrorCodes by viewModel.liveErrorCodes.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

    val liveSpareParts by viewModel.liveSpareParts.collectAsState()

    val cartItemsList by viewModel.cart.collectAsState()
    val cartQtyMap by viewModel.cartQty.collectAsState()

    val repairOrders by viewModel.repairOrders.collectAsState()
    val isRepairsLoading by viewModel.isRepairsLoading.collectAsState()
    val isGlobalRefreshing by viewModel.isGlobalRefreshing.collectAsState()
    val isLiveDataSyncing by viewModel.isLiveDataSyncing.collectAsState()

    val freeErrorCount by viewModel.freeErrorCount.collectAsState()
    val freeProblemCount by viewModel.freeProblemCount.collectAsState()

    val appUpdateNotification by viewModel.appUpdateNotification.collectAsState()
    val isTechnicianOnline by viewModel.isTechnicianOnline.collectAsState()
    val isTechStatusUpdating by viewModel.isTechStatusUpdating.collectAsState()
    val newOrderAlert by viewModel.newOrderAlert.collectAsState()

    val isTechnicianUser = currentUser?.isTechnicianUser == true || currentUser?.userRole == com.example.data.model.UserRole.TECHNICIAN

    LaunchedEffect(isTechnicianUser, isTechnicianOnline) {
        if (isTechnicianUser && isTechnicianOnline) {
            viewModel.startOrderPolling()
        } else {
            viewModel.stopOrderPolling()
        }
    }

    LaunchedEffect(showPlansDialog) {
        if (showPlansDialog) {
            viewModel.fetchSubscriptionPlans()
        }
    }

    LaunchedEffect(showAuthDialog) {
        if (showAuthDialog) {
            val savedPhone = viewModel.getSavedAuthPhone()
            val savedPass = viewModel.getSavedAuthPassword()
            if (savedPhone.isNotBlank()) {
                authPhone = savedPhone
            }
            if (savedPass.isNotBlank()) {
                authPassword = savedPass
            }
        }
    }

    androidx.activity.compose.BackHandler(
        enabled = showAuthDialog || showCartDialog || showWebCheckout || showPlansDialog ||
            selectedErrorDetail != null || selectedProblemDetail != null ||
            showRegisterRequiredDialog || showPremiumRequiredDialog || showDisclaimerModal ||
            activeTab != "home"
    ) {
        when {
            showAuthDialog -> showAuthDialog = false
            showWebCheckout -> showWebCheckout = false
            showCartDialog -> showCartDialog = false
            showPlansDialog -> showPlansDialog = false
            showRegisterRequiredDialog -> showRegisterRequiredDialog = false
            showPremiumRequiredDialog -> showPremiumRequiredDialog = false
            showDisclaimerModal -> showDisclaimerModal = false
            selectedErrorDetail != null -> selectedErrorDetail = null
            selectedProblemDetail != null -> selectedProblemDetail = null
            activeTab != "home" -> activeTab = "home"
        }
    }

    val isPremium = currentUser?.subscription?.is_premium == true || currentUser?.is_premium == true || 
        currentUser?.is_vip == true || currentUser?.has_subscription == true || 
        !currentUser?.plan.isNullOrBlank() || !currentUser?.subscription?.plan.isNullOrBlank()

    // Trigger RTL context for Persian/Arabic UI
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = modifier
                    .fillMaxSize()
                    .background(CodyarBg),
                topBar = {
                    Surface(
                        color = CodyarNavy,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_app_logo),
                                    contentDescription = "لوگوی کدیار ۲۴",
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Text(
                                    text = "کدیار ۲۴",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isTechnicianUser) {
                                    // Technician Status Toggle Button (آنلاین / مرخصی)
                                    Surface(
                                        onClick = {
                                            if (!isTechStatusUpdating) {
                                                viewModel.toggleTechnicianStatus { success, err ->
                                                    if (success) {
                                                        val msg = if (isTechnicianOnline) "وضعیت: آماده به کار و دریافت سفارش ✅" else "وضعیت: مرخصی (عدم دریافت سفارش) 🏖️"
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, err ?: "خطا در تغییر وضعیت", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isTechnicianOnline) Color(0xFF16A34A) else Color(0xFFDC2626),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isTechStatusUpdating) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color.White, CircleShape)
                                                )
                                            }
                                            Text(
                                                text = if (isTechnicianOnline) "آنلاین" else "مرخصی",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                if (isPremium) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFC9A227), RoundedCornerShape(8.dp))
                                            .height(36.dp)
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "ویژه",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Cart Button
                                Box {
                                    IconButton(
                                        onClick = {
                                            if (cartItemsList.isNotEmpty()) {
                                                cartStep = "cart"
                                                showCartDialog = true
                                            } else {
                                                Toast.makeText(context, "سبد خرید شما خالی است", Toast.LENGTH_SHORT).show()
                                                activeTab = "store"
                                            }
                                        },
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCart,
                                            contentDescription = "سبد خرید",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    if (cartItemsList.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .offset(x = (-3).dp, y = (-3).dp)
                                                .background(CodyarRed, CircleShape)
                                                .size(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cartItemsList.size.toString(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Profile Button
                                IconButton(
                                    onClick = {
                                        if (currentUser != null) {
                                            activeTab = "profile"
                                        } else {
                                            authMode = "login"
                                            showAuthDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "پروفایل",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        color = Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 6.dp,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(vertical = 2.dp, horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            data class NavItem(
                                val id: String,
                                val activeIcon: ImageVector,
                                val inactiveIcon: ImageVector,
                                val label: String
                            )

                            val navigationItems = listOf(
                                NavItem("home", Icons.Filled.Home, Icons.Outlined.Home, "خانه"),
                                NavItem("search", Icons.Filled.Search, Icons.Outlined.Search, "کد خطا"),
                                NavItem("problems", Icons.Filled.Build, Icons.Outlined.Build, "مشکلات"),
                                NavItem("store", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart, "فروشگاه"),
                                NavItem("profile", Icons.Filled.Person, Icons.Outlined.Person, "پروفایل")
                            )

                            navigationItems.forEach { item ->
                                val active = activeTab == item.id || (item.id == "profile" && activeTab == "orders") || (item.id == "search" && activeTab == "technicians")

                                val animatedScale by animateFloatAsState(
                                    targetValue = if (active) 1.08f else 1.0f,
                                    animationSpec = tween(durationMillis = 150),
                                    label = "iconScale"
                                )

                                val animatedBgColor by animateColorAsState(
                                    targetValue = if (active) CodyarRed.copy(alpha = 0.12f) else Color.Transparent,
                                    animationSpec = tween(durationMillis = 150),
                                    label = "bgColor"
                                )

                                val animatedContentColor by animateColorAsState(
                                    targetValue = if (active) CodyarRed else Color(0xFF64748B),
                                    animationSpec = tween(durationMillis = 150),
                                    label = "contentColor"
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedErrorDetail = null
                                            selectedProblemDetail = null
                                            if (item.id != "home" && item.id != "store" && currentUser == null) {
                                                showRegisterRequiredDialog = true
                                                return@clickable
                                            }
                                            if (activeTab != item.id) {
                                                if (item.id == "search" && viewModel.showOnlySaved.value) {
                                                    viewModel.setShowOnlySaved(false)
                                                }
                                                activeTab = item.id
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(26.dp)
                                            .width(44.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(animatedBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (active) item.activeIcon else item.inactiveIcon,
                                            contentDescription = item.label,
                                            tint = animatedContentColor,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .scale(animatedScale)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = item.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = animatedContentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                val isOnline by viewModel.isOnline.collectAsState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(CodyarBg)
                ) {
                    if (!isOnline) {
                        Surface(
                            color = Color(0xFFD97706),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "بدون اتصال — نمایش آخرین داده ذخیره‌شده",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (activeTab) {
                            "home" -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToSearch = {
                                        if (currentUser == null) {
                                            showRegisterRequiredDialog = true
                                        } else {
                                            activeTab = "search"
                                        }
                                    },
                                    onNavigateToTechnicians = {
                                        if (currentUser == null) {
                                            showRegisterRequiredDialog = true
                                        } else {
                                            activeTab = "technicians"
                                        }
                                    },
                                    onNavigateToStore = {
                                        activeTab = "store"
                                    },
                                    onShowPlans = {
                                        if (currentUser == null) {
                                            showRegisterRequiredDialog = true
                                        } else {
                                            showPlansDialog = true
                                        }
                                    },
                                    onOpenErrorCode = { err ->
                                        if (currentUser == null) {
                                            showRegisterRequiredDialog = true
                                        } else if (isPremium) {
                                            selectedErrorDetail = err
                                            activeTab = "search"
                                        } else {
                                            showPremiumRequiredDialog = true
                                        }
                                    }
                                )
                            }
                            "search" -> {
                                SearchScreen(
                                    viewModel = viewModel,
                                    selectedErrorDetail = selectedErrorDetail,
                                    onSelectError = { err ->
                                        if (currentUser == null) {
                                            showRegisterRequiredDialog = true
                                        } else if (isPremium) {
                                            selectedErrorDetail = err
                                        } else {
                                            showPremiumRequiredDialog = true
                                        }
                                    },
                                    onBack = { selectedErrorDetail = null },
                                    onNavigateToTechnicians = {
                                        if (currentUser == null) {
                                            showRegisterRequiredDialog = true
                                        } else {
                                            activeTab = "technicians"
                                        }
                                    },
                                    onNavigateToStore = {
                                        activeTab = "store"
                                    },
                                    isPremium = isPremium,
                                    freeErrorCount = freeErrorCount,
                                    onShowPlans = { showPlansDialog = true }
                                )
                            }
                            "problems" -> {
                                ProblemsScreen(
                                    viewModel = viewModel,
                                    liveProblems = viewModel.liveCommonProblems,
                                    selectedProblemDetail = selectedProblemDetail,
                                    onSelectProblem = { prob ->
                                        if (currentUser == null) {
                                            showRegisterRequiredDialog = true
                                        } else if (isPremium) {
                                            selectedProblemDetail = prob
                                        } else {
                                            showPremiumRequiredDialog = true
                                        }
                                    },
                                    onBack = { selectedProblemDetail = null },
                                    onNavigateToTechnicians = {
                                        if (currentUser == null) {
                                            showRegisterRequiredDialog = true
                                        } else {
                                            activeTab = "technicians"
                                        }
                                    },
                                    onNavigateToStore = {
                                        activeTab = "store"
                                    },
                                    isPremium = isPremium,
                                    freeProblemCount = freeProblemCount,
                                    onShowPlans = { showPlansDialog = true }
                                )
                            }
                            "store" -> {
                                StoreScreen(
                                    viewModel = viewModel,
                                    parts = liveSpareParts,
                                    cartItems = cartItemsList,
                                    onAddToCart = { viewModel.addToCart(it) }
                                )
                            }
                            "profile" -> {
                                ProfileScreen(
                                    viewModel = viewModel,
                                    currentUser = currentUser,
                                    onShowAuth = {
                                        authMode = "login"
                                        showAuthDialog = true
                                    },
                                    onShowPlans = { showPlansDialog = true },
                                    onNavigateToOrders = {
                                        viewModel.loadRepairs()
                                        activeTab = "orders"
                                    },
                                    onNavigateToSaved = {
                                        viewModel.setShowOnlySaved(true)
                                        activeTab = "search"
                                    },
                                    onShowDisclaimer = {
                                        showDisclaimerModal = true
                                    }
                                )
                            }
                            "technicians" -> {
                                TechniciansScreen(
                                    viewModel = viewModel,
                                    liveTechs = viewModel.liveTechnicians,
                                    currentUser = currentUser,
                                    onShowAuth = {
                                        authMode = "login"
                                        showAuthDialog = true
                                    }
                                )
                            }
                            "orders" -> {
                                OrdersScreen(
                                    viewModel = viewModel,
                                    repairOrders = repairOrders,
                                    isRepairsLoading = isRepairsLoading,
                                    onBack = { activeTab = "profile" },
                                    onNavigateToTechs = { activeTab = "technicians" }
                                )
                            }
                            "ai_chat" -> {
                                AiChatScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }

            // --- AUTH DIALOG ---
            AuthDialog(
                showAuthDialog = showAuthDialog,
                onDismiss = { showAuthDialog = false },
                authMode = authMode,
                onAuthModeChange = { authMode = it },
                authRole = authRole,
                onAuthRoleChange = { authRole = it },
                authPhone = authPhone,
                onPhoneChange = {
                    authPhone = it
                    viewModel.saveAuthCredentials(it, authPassword)
                },
                authPassword = authPassword,
                onPasswordChange = {
                    authPassword = it
                    viewModel.saveAuthCredentials(authPhone, it)
                },
                isPasswordVisible = isPasswordVisible,
                onPasswordVisibleChange = { isPasswordVisible = it },
                authName = authName,
                onNameChange = { authName = it },
                authCity = authCity,
                onCityChange = { authCity = it },
                authDistrict = authDistrict,
                onDistrictChange = { authDistrict = it },
                authSelectedCategories = authSelectedCategories,
                onCategoriesChange = { authSelectedCategories = it },
                techCategorySearch = techCategorySearch,
                onCategorySearchChange = { techCategorySearch = it },
                techUploadedDocs = techUploadedDocs,
                onUploadedDocsChange = { techUploadedDocs = it },
                techUploadedDocUris = techUploadedDocUris,
                onUploadedDocUrisChange = { techUploadedDocUris = it },
                techDocTitleInput = techDocTitleInput,
                onDocTitleInputChange = { techDocTitleInput = it },
                currentDocTypeToPick = currentDocTypeToPick,
                onDocTypeToPickChange = { currentDocTypeToPick = it },
                docPickerLauncher = docPickerLauncher,
                viewModel = viewModel,
                isAuthLoading = isAuthLoading,
                authError = authError
            )

            // --- CART DIALOG ---
            CartDialog(
                showCartDialog = showCartDialog,
                onDismiss = { showCartDialog = false },
                cartItemsList = cartItemsList,
                cartQtyMap = cartQtyMap,
                liveSpareParts = liveSpareParts,
                currentUser = currentUser,
                viewModel = viewModel,
                onShowAuth = {
                    authMode = "login"
                    showAuthDialog = true
                }
            )

            // --- AUTHENTICATED KODYAR24 WEBSITE CHECKOUT ---
            if (showWebCheckout) {
                WebCheckoutDialog(
                    showWebCheckout = showWebCheckout,
                    onDismiss = { showWebCheckout = false },
                    sessionToken = viewModel.getSessionToken() ?: ""
                )
            }

            // --- SUBSCRIPTION PLANS DIALOG ---
            SubscriptionPlansDialog(
                showPlansDialog = showPlansDialog,
                onDismiss = { showPlansDialog = false },
                viewModel = viewModel,
                currentUser = currentUser,
                onShowAuth = {
                    authMode = "login"
                    showAuthDialog = true
                },
                onPurchasePlan = onPurchasePlan
            )

            // --- LIVE WEBSITE UPDATE NOTIFICATION DIALOG ---
            UpdateNotificationDialog(
                updates = appUpdateNotification,
                isPremium = isPremium,
                onDismiss = { viewModel.dismissUpdateNotification() }
            )

            // --- REGISTRATION REQUIRED DIALOG ---
            RegisterRequiredDialog(
                showRegisterRequiredDialog = showRegisterRequiredDialog,
                onDismiss = { showRegisterRequiredDialog = false },
                onNavigateToAuth = {
                    authMode = "register"
                    showAuthDialog = true
                }
            )

            // --- PREMIUM REQUIRED DIALOG ---
            PremiumRequiredDialog(
                showPremiumRequiredDialog = showPremiumRequiredDialog,
                onDismiss = { showPremiumRequiredDialog = false },
                onShowPlans = { showPlansDialog = true }
            )

            // --- BAZAAR APP UPDATE DIALOG ---
            val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
            val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()

            if (showUpdateDialog) {
                AppUpdateDialog(
                    updateInfo = appUpdateInfo,
                    onDismiss = { viewModel.dismissUpdateDialog() },
                    onUpdateClick = {
                        openBazaarUpdate(context)
                        viewModel.dismissUpdateDialog()
                    }
                )
            }

            // --- NEW REPAIR ORDER POPUP ALERT FOR TECHNICIANS ---
            newOrderAlert?.let { orderAlert ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissNewOrderAlert() },
                    confirmButton = {
                        Button(
                            onClick = {
                                val orderIdToAct = orderAlert.resolvedOrderId.ifBlank { orderAlert.id ?: "" }
                                viewModel.acceptRepairOrder(orderIdToAct) { success, err ->
                                    if (success) {
                                        Toast.makeText(context, "سفارش با موفقیت به شما اختصاص یافت ✅", Toast.LENGTH_SHORT).show()
                                        viewModel.dismissNewOrderAlert()
                                        activeTab = "orders"
                                    } else {
                                        Toast.makeText(context, err ?: "خطا در قبول سفارش", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("قبول و تایید سفارش 🤝", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { viewModel.dismissNewOrderAlert() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("بستن", fontSize = 12.sp, color = CodyarNavy)
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF22C55E), CircleShape)
                            )
                            Text(
                                text = "🔔 اعلام فوری سفارش کار جدید!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = CodyarNavy
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "یک سفارش جدید تعمیرات در منطقه شما ثبت شده است:",
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!orderAlert.description.isNullOrBlank()) {
                                        Text(text = "📌 شرح کار: ${orderAlert.description}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CodyarNavy)
                                    }
                                    if (!orderAlert.city.isNullOrBlank()) {
                                        Text(text = "📍 شهر: ${orderAlert.city}", fontSize = 12.sp, color = Color(0xFF475569))
                                    }
                                    if (!orderAlert.customer_name.isNullOrBlank()) {
                                        Text(text = "👤 مشتری: ${orderAlert.customer_name}", fontSize = 12.sp, color = Color(0xFF475569))
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White
                )
            }

            // --- DISCLAIMER & TERMS OF USE DIALOG ---
            DisclaimerDialog(
                showDisclaimerModal = showDisclaimerModal,
                isDisclaimerAccepted = isDisclaimerAccepted,
                disclaimerChecked = disclaimerChecked,
                onDisclaimerCheckedChange = { disclaimerChecked = it },
                onAccept = {
                    viewModel.acceptDisclaimer()
                    showDisclaimerModal = false
                }
            )
        }
    }
}
