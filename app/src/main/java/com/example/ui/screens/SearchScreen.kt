package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.example.data.model.KodyarErrorCode
import com.example.ui.AssistantViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AssistantViewModel,
    selectedErrorDetail: KodyarErrorCode?,
    onSelectError: (KodyarErrorCode) -> Unit,
    onBack: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    onNavigateToStore: () -> Unit,
    isPremium: Boolean,
    freeErrorCount: Int,
    onShowPlans: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                val normalizedText = normalizeVoiceSearchText(spokenText)
                viewModel.updateSearchFilters(normalizedText, "همه", "همه", "")
            }
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val modelQuery by viewModel.modelQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val savedErrors by viewModel.savedErrors.collectAsState()
    val showOnlySaved by viewModel.showOnlySaved.collectAsState()

    val liveCategories by viewModel.liveCategories.collectAsState()
    val liveBrands by viewModel.liveBrands.collectAsState()
    val liveErrorCodes by viewModel.liveErrorCodes.collectAsState()

    val currentUser by viewModel.currentUser.collectAsState()
    val liveTechs by viewModel.liveTechnicians.collectAsState()

    val isVerifiedTechnician = remember(currentUser, liveTechs) {
        if (currentUser == null) false
        else {
            val user = currentUser!!
            val isRoleTech = (user.role == "technician" || user.role == "tech" || user.role == "repairman") && user.isApprovedUser
            val isMatchedInList = liveTechs.any { tech ->
                (tech.id == user.id ||
                 (!tech.phone.isNullOrBlank() && tech.phone == user.phone) ||
                 (!tech.name.isNullOrBlank() && tech.name == user.full_name)) &&
                tech.isVerified == true
            }
            isRoleTech || isMatchedInList
        }
    }

    var showTechOnlyVideoDialog by remember { mutableStateOf(false) }
    var showFilterBar by remember { mutableStateOf(false) }

    if (selectedErrorDetail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchFilters(it, selectedBrand, selectedCategory, modelQuery) },
                placeholder = { Text("کد خطا، برند، دستگاه یا شرح عیب...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchFilters("", selectedBrand, selectedCategory, modelQuery) }) {
                                Icon(Icons.Default.Close, contentDescription = "پاک کردن", tint = Color(0xFF64748B))
                            }
                        }
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fa-IR")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "لطفاً عیب، کد خطا، برند یا مدل را بگویید...")
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
                            }
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "سیستم صوتی روی این دستگاه در دسترس نیست", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "جستجوی صوتی", tint = CodyarRed)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CodyarNavy,
                    unfocusedBorderColor = Color(0xFFDDE1E7)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (showOnlySaved) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(CodyarNavy.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Text("درحال نمایش کدهای ذخیره شده شما", fontSize = 12.sp, color = CodyarTextPrimary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { viewModel.setShowOnlySaved(false) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("نمایش همه کدها", fontSize = 11.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Expandable Filter Chip
            Card(
                onClick = { showFilterBar = !showFilterBar },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFEAECEF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = if (showFilterBar) CodyarRed else CodyarTextPrimary
                        )
                        Text(
                            text = "فیلتر پیشرفته",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (showFilterBar) CodyarRed else CodyarTextPrimary
                        )
                        if (selectedCategory != "همه" || selectedBrand != "همه" || modelQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(CodyarRed, CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                            ) {
                                Text("فعال", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Icon(
                        imageVector = if (showFilterBar) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = CodyarTextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = showFilterBar) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Side-by-side Dropdowns for Device Category and Brand
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                FilterDropdown(
                                    label = "نوع دستگاه:",
                                    selectedValue = selectedCategory,
                                    options = liveCategories,
                                    onSelect = { viewModel.updateSearchFilters(searchQuery, selectedBrand, it, modelQuery) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                FilterDropdown(
                                    label = "برند:",
                                    selectedValue = selectedBrand,
                                    options = liveBrands,
                                    onSelect = { viewModel.updateSearchFilters(searchQuery, it, selectedCategory, modelQuery) }
                                )
                            }
                        }

                        // Model Filter TextField and Dropdown
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "فیلتر مدل (دقت بیشتر):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            val availableModels = remember(selectedBrand, selectedCategory, liveErrorCodes) {
                                viewModel.getAvailableModelsFor(selectedBrand, selectedCategory)
                            }

                            var modelExpanded by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = modelQuery,
                                    onValueChange = {
                                        viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, it)
                                    },
                                    placeholder = { Text("مثلاً: ۲۴۰۰، v12، دایرکت درایو...", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (modelQuery.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, "") }) {
                                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            IconButton(onClick = { modelExpanded = !modelExpanded }) {
                                                Icon(
                                                    imageVector = if (modelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "نمایش لیست",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Right),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CodyarNavy,
                                        unfocusedBorderColor = Color(0xFFDDE1E7),
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC)
                                    )
                                )

                                val filteredModels = remember(availableModels, modelQuery) {
                                    if (modelQuery.isEmpty() || modelQuery == "همه") {
                                        availableModels
                                    } else {
                                        val normQuery = viewModel.canonicalModel(modelQuery)
                                        availableModels.filter {
                                            it == "همه" || viewModel.canonicalModel(it).contains(normQuery, ignoreCase = true)
                                        }
                                    }
                                }

                                if (filteredModels.isNotEmpty() && modelExpanded) {
                                    DropdownMenu(
                                        expanded = modelExpanded,
                                        onDismissRequest = { modelExpanded = false },
                                        properties = PopupProperties(focusable = false),
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .heightIn(max = 240.dp)
                                            .background(CodyarSurface)
                                    ) {
                                        filteredModels.forEach { modelOpt ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = modelOpt,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = TextAlign.Right
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, if (modelOpt == "همه") "" else modelOpt)
                                                    modelExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedCategory != "همه" || selectedBrand != "همه" || modelQuery.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.updateSearchFilters("", "همه", "همه", "") },
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.align(Alignment.End),
                                border = BorderStroke(1.dp, Color(0xFFD1D5DB))
                            ) {
                                Text("× پاک کردن فیلترها", fontSize = 11.sp, color = CodyarTextPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${searchResults.size} نتیجه یافت شد",
                fontSize = 11.sp,
                color = CodyarTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Results List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults, key = { it.id ?: "${it.code}_${it.brand}_${it.category}" }) { err ->
                    val devCat = err.resolvedCategory
                    val br = err.brand?.trim()?.takeIf { it.isNotBlank() && it != "عمومی" }
                    val md = err.model?.trim()?.takeIf { it.isNotBlank() }
                    val brandCatText = if (br != null) "$devCat · $br" else devCat

                    // کد خطا — کدهای طولانی مثل «40 60 80» یا «CR CF» دو خطی می‌شوند
                    val codeText = err.resolvedCode
                    val codeFontSize = when {
                        codeText.length > 9 -> 11.sp
                        codeText.length > 5 -> 12.sp
                        else -> 14.sp
                    }

                    // برچسب و رنگ سطح خطر
                    val (hazardLabel, hazardColor, hazardBg) = when (err.hazardLevel?.lowercase()?.trim()) {
                        "high", "خطرناک", "بحرانی" -> Triple("خطرناک", Color(0xFFC0392B), Color(0xFFFDF0EE))
                        "low", "آسان", "کم‌خطر", "کم خطر" -> Triple("کم‌خطر", Color(0xFF1E8449), Color(0xFFEAFAF1))
                        else -> Triple("متوسط", Color(0xFFD68910), Color(0xFFFEF9E7))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectError(err) },
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // جعبه کد خطا — دارای فضای باز برای کدهای طولانی مانند «40 60 80»
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 64.dp, minHeight = 46.dp)
                                    .widthIn(max = 110.dp)
                                    .background(Color(0xFFF0F4F8), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFD9E2EC), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = codeText,
                                    fontSize = codeFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = CodyarNavy,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 15.sp
                                )
                            }

                            // مشخصات دستگاه (دسته‌بندی، برند، مدل) — عنوان عیب‌یافته هرگز اینجا درج نمی‌شود
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = brandCatText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CodyarTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "مدل: ${md ?: "عمومی"}",
                                    fontSize = 11.sp,
                                    color = CodyarTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            // سطح خطر (خطرناک / متوسط / کم‌خطر) و وضعیت دسترسی
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(hazardBg, RoundedCornerShape(6.dp))
                                        .border(0.8.dp, hazardColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = hazardLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = hazardColor
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPremium) Icons.Default.CheckCircle else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isPremium) Color(0xFF1E8449) else Color(0xFFD97706),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (isPremium) "راهنما" else "اشتراک",
                                        fontSize = 10.sp,
                                        color = if (isPremium) Color(0xFF1E8449) else Color(0xFFD97706),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                if (searchResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("❌", fontSize = 36.sp)
                            Text("نتیجه‌ای پیدا نشد", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("کد خطا یا برند را بررسی کنید", fontSize = 12.sp, color = CodyarTextSecondary)
                        }
                    }
                }
            }
        }
    } else {
        // ERROR DETAIL SCREEN
        val err = selectedErrorDetail
        val isBookmarked = savedErrors.any { it.code == err.code && it.brand == err.brand && it.category == err.category }

        val severityLevel = err.hazardLevel ?: "medium"
        val (themeColor, bgText, titleText) = when (severityLevel) {
            "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "بحرانی / خطرناک")
            "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "آسان / کم‌خطر")
            else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                // Bookmark Icon
                IconButton(
                    onClick = {
                        viewModel.toggleSavedError(err.code ?: "", err.brand ?: "", err.category ?: "", isBookmarked)
                        Toast.makeText(
                            context,
                            if (isBookmarked) "از ذخیره‌شده‌ها حذف شد" else "به ذخیره‌شده‌ها اضافه شد",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "ذخیره",
                        tint = if (isBookmarked) Color.Red else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Detail Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header: colored based on hazard level if premium, else neutral CodyarNavy
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isPremium) themeColor else CodyarNavy)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Jadar error code box (fits long codes like "40 60 80")
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                                    .widthIn(max = 110.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = err.resolvedCode,
                                    fontSize = if (err.resolvedCode.length > 9) 12.sp
                                               else if (err.resolvedCode.length > 5) 13.sp
                                               else 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 16.sp
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                if (isPremium) {
                                    Text(
                                        text = err.title ?: "بررسی ارور ${err.resolvedCode}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${err.resolvedCategory} · ${err.brand ?: "عمومی"}${if (!err.model.isNullOrBlank()) " · مدل: ${err.model}" else ""}",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                } else {
                                    Text(
                                        text = "کد خطا: ${err.resolvedCode}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${err.resolvedCategory} · ${err.brand ?: "عمومی"}${if (!err.model.isNullOrBlank()) " · مدل: ${err.model}" else ""}",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            if (isPremium) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = titleText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Content Padding
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!isPremium) {
                            // Non-premium locked view: hides title, description, causes, steps, precautions, hazardLevel
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9E7)),
                                border = BorderStroke(1.dp, Color(0xFFFDEBD0)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .background(Color(0xFFD97706).copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Text(
                                        text = "راهنمای تخصصی عیب‌یابی قفل است",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CodyarTextPrimary,
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = "برای دسترسی به شرح کامل خطا، علت‌های احتمالی خرابی، مراحل رفع عیب، نکات احتیاطی و درجه خطر دستگاه ${err.brand ?: ""}، لطفاً اشتراک خود را فعال کنید.",
                                        fontSize = 12.sp,
                                        color = CodyarTextSecondary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )

                                    Button(
                                        onClick = onShowPlans,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("خرید و فعال‌سازی اشتراک", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // Premium full diagnosis: Pattern (code, category, brand, model, title, description, causes, steps, precautions, hazardLevel)

                            // 5. Title
                            if (!err.title.isNullOrBlank()) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Build, contentDescription = null, tint = CodyarNavy, modifier = Modifier.size(14.dp))
                                        Text("عنوان تخصصی خطا", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                    }
                                    Text(
                                        text = err.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CodyarNavy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF0F4F8), RoundedCornerShape(10.dp))
                                            .padding(12.dp)
                                    )
                                }
                            }

                            // 6. Description
                            if (!err.description.isNullOrBlank()) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(bottom = 9.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD68910), modifier = Modifier.size(14.dp))
                                        Text("توضیح خطا", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                    }

                                    Text(
                                        text = err.description,
                                        fontSize = 15.sp,
                                        color = CodyarTextPrimary,
                                        lineHeight = 30.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFEF9E7), RoundedCornerShape(10.dp))
                                            .border(BorderStroke(1.dp, Color(0xFFFEF9E7)))
                                            .padding(14.dp)
                                    )
                                }
                            }

                            // 7. Causes List
                            val causesList = with(viewModel) { err.causes.toListOfStrings() }
                            if (causesList.isNotEmpty()) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(bottom = 9.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(14.dp))
                                        Text("علت‌های احتمالی خرابی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        causesList.forEachIndexed { i, cause ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFFDF0EE), RoundedCornerShape(9.dp))
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    text = "${i + 1}.",
                                                    color = Color(0xFFC0392B),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = cause,
                                                    fontSize = 15.sp,
                                                    color = CodyarTextPrimary,
                                                    lineHeight = 26.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 8. Steps list
                            val stepsList = with(viewModel) { err.steps.toListOfStrings() }
                            if (stepsList.isNotEmpty()) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(bottom = 9.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                                        Text("مراحل رفع مشکل", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        stepsList.forEachIndexed { i, step ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFEAFAF1), RoundedCornerShape(9.dp))
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .background(Color(0xFF1E8449), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = (i + 1).toString(),
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = step,
                                                    fontSize = 15.sp,
                                                    color = CodyarTextPrimary,
                                                    lineHeight = 26.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 9. Precautions
                            val precautionsList = with(viewModel) { err.resolvedPrecautions.toListOfStrings() }
                            if (precautionsList.isNotEmpty()) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(bottom = 9.dp)
                                    ) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                                        Text("نکات ایمنی و احتیاطی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        precautionsList.forEach { precaution ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFEFF6FF), RoundedCornerShape(9.dp))
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2563EB),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = precaution,
                                                    fontSize = 14.sp,
                                                    color = CodyarTextPrimary,
                                                    lineHeight = 24.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 10. Hazard Level details
                            if (!err.hazardLevel.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bgText, RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = themeColor, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "درجه خطر دستگاه: $titleText",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = themeColor
                                    )
                                }
                            }
                        }

                        // Source Disclaimer
                        Text(
                            text = "منبع: وب‌سایت کدیار۲۴ (kodyar24.ir) و دفترچه راهنمای رسمی شرکت سازنده ${err.brand}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        // Actions Grid (2 Columns x 2 Rows)
                        val videoUrl = err.resolvedVideoUrl ?: (err.video_url ?: err.videoUrl)?.takeIf { it.isNotBlank() }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: اعزام تکنسین & قطعات یدکی
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onNavigateToTechnicians,
                                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("اعزام تکنسین", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val partQuery = buildSparePartQueryForError(
                                            category = err.category,
                                            brand = err.brand,
                                            model = err.model,
                                            title = err.title,
                                            code = err.code,
                                            description = err.description,
                                            causes = err.causes
                                        )
                                        if (partQuery.isNotBlank()) {
                                            viewModel.setStoreSearchQuery(partQuery)
                                        }
                                        onNavigateToStore()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E8449)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("قطعات یدکی", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Row 2: ویدیوی آموزشی & کپی متن خطایابی
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // دکمه ویدیوی آموزشی (مخصوص تکنسین‌ها جهت حفظ ایمنی دستگاه)
                                Button(
                                    onClick = {
                                        if (!isVerifiedTechnician) {
                                            showTechOnlyVideoDialog = true
                                        } else if (!videoUrl.isNullOrBlank()) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl.trim()))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "خطا در باز کردن ویدیوی آموزشی", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "ویدیوی آموزشی برای این کد خطا ثبت نشده است", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isVerifiedTechnician) {
                                            if (!videoUrl.isNullOrBlank()) Color(0xFFE53935) else Color(0xFF94A3B8)
                                        } else {
                                            Color(0xFF64748B)
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        if (isVerifiedTechnician) Icons.Default.PlayArrow else Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isVerifiedTechnician) "ویدیوی آموزشی" else "ویدیو (تکنسین)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                // دکمه کپی متن خطایابی
                                Button(
                                    onClick = {
                                        val stepsToCopy = with(viewModel) { err.steps.toListOfStrings() }
                                        clipboardManager.setText(
                                            AnnotatedString(
                                                "${err.deviceBrandModelSummary}\nکد خطا: ${err.code}\nشرح عیب: ${err.description ?: ""}\nمراحل حل: ${stepsToCopy.joinToString("\n")}"
                                            )
                                        )
                                        Toast.makeText(context, "اطلاعات عیب‌یابی کپی شد!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF475569)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("کپی خطایابی", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTechOnlyVideoDialog) {
        AlertDialog(
            onDismissRequest = { showTechOnlyVideoDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔒", fontSize = 22.sp)
                    Text(
                        text = "دسترسی ویژه تکنسین‌ها",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = CodyarNavy
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ویدیوهای آموزش رفع عیب و بازوبست تخصصی صرفاً برای تکنسین‌های مجاز و احراز هویت شده ثبت شده است.",
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 20.sp
                    )
                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚠️", fontSize = 18.sp)
                            Text(
                                text = "اقدام به تعمیر خودسرانه توسط افراد غیرمتخصص می‌تواند منجر به آسیب جدی به دستگاه یا خطرات برق‌گرفتگی شود.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E),
                                lineHeight = 17.sp
                            )
                        }
                    }
                    Text(
                        text = "جهت رفع اصولی عیب دستگاه، لطفاً از بخش «اعزام تکنسین» با تعمیرکار متخصص شهر خود هماهنگ فرمایید.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTechOnlyVideoDialog = false
                        onNavigateToTechnicians()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اعزام تکنسین", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTechOnlyVideoDialog = false }
                ) {
                    Text("متوجه شدم", color = Color(0xFF64748B), fontSize = 13.sp)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}