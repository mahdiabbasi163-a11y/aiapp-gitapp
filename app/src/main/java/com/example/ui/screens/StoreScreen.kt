package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.example.data.model.KodyarSparePart
import com.example.ui.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    viewModel: AssistantViewModel,
    parts: List<KodyarSparePart>,
    cartItems: List<String>,
    onAddToCart: (String) -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.storeSearchQuery.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isPartsLoading by viewModel.isSparePartsLoading.collectAsState()
    var selectedPartForDetails by remember { mutableStateOf<KodyarSparePart?>(null) }
    var zoomedPartForImage by remember { mutableStateOf<KodyarSparePart?>(null) }

    val liveCategories by viewModel.liveCategories.collectAsState()
    val liveBrands by viewModel.liveBrands.collectAsState()

    var showStoreFilterBar by remember { mutableStateOf(false) }
    var storeSelectedCategory by remember { mutableStateOf("همه") }
    var storeSelectedBrand by remember { mutableStateOf("همه") }
    var storeModelQuery by remember { mutableStateOf("") }

    val storeCategories = remember(liveCategories, parts) {
        val list = mutableListOf("همه")
        val fromParts = parts.mapNotNull { it.resolvedCategory.ifBlank { null } }.distinct()
        list.addAll(fromParts)
        liveCategories.filter { it != "همه" && !list.contains(it) }.forEach { list.add(it) }
        list
    }

    val storeBrands = remember(liveBrands, parts, storeSelectedCategory) {
        val list = mutableListOf("همه")
        val relevantParts = if (storeSelectedCategory == "همه") parts else parts.filter {
            it.resolvedCategory.contains(storeSelectedCategory, ignoreCase = true)
        }
        val fromParts = relevantParts.mapNotNull { it.resolvedBrand.ifBlank { null } }.distinct()
        list.addAll(fromParts)
        liveBrands.filter { it != "همه" && !list.contains(it) }.forEach { list.add(it) }
        list
    }

    val storeAvailableModels = remember(parts, storeSelectedBrand, storeSelectedCategory) {
        val list = mutableListOf<String>()
        parts.filter { part ->
            (storeSelectedCategory == "همه" || part.resolvedCategory.contains(storeSelectedCategory, ignoreCase = true)) &&
            (storeSelectedBrand == "همه" || part.resolvedBrand.contains(storeSelectedBrand, ignoreCase = true))
        }.forEach { part ->
            part.model?.takeIf { it.isNotBlank() }?.let { if (!list.contains(it)) list.add(it) }
            part.device_model?.takeIf { it.isNotBlank() }?.let { if (!list.contains(it)) list.add(it) }
        }
        list
    }

    // Smart Persian normalized filtering and scoring logic
    val baseFilteredParts = remember(parts, searchQuery) {
        filterSparePartsByQuery(parts, searchQuery)
    }

    val filteredParts = remember(baseFilteredParts, storeSelectedCategory, storeSelectedBrand, storeModelQuery) {
        if (storeSelectedCategory == "همه" && storeSelectedBrand == "همه" && storeModelQuery.isBlank()) {
            baseFilteredParts
        } else {
            baseFilteredParts.filter { part ->
                val matchesCategory = if (storeSelectedCategory == "همه") true else {
                    val normCat = normalizePersian(storeSelectedCategory).lowercase().trim()
                    val partCat = normalizePersian(part.resolvedCategory).lowercase()
                    val partName = normalizePersian(part.name ?: "").lowercase()
                    partCat.contains(normCat) || partName.contains(normCat)
                }

                val matchesBrand = if (storeSelectedBrand == "همه") true else {
                    val normBrand = normalizePersian(storeSelectedBrand).lowercase().trim()
                    val partBrand = normalizePersian(part.resolvedBrand).lowercase()
                    val partName = normalizePersian(part.name ?: "").lowercase()
                    partBrand.contains(normBrand) || partName.contains(normBrand)
                }

                val matchesModel = if (storeModelQuery.isBlank() || storeModelQuery == "همه") true else {
                    val normModel = normalizePersian(storeModelQuery).lowercase().trim()
                    val partModel = normalizePersian(part.model ?: "").lowercase()
                    val partDeviceModel = normalizePersian(part.device_model ?: "").lowercase()
                    val partName = normalizePersian(part.name ?: "").lowercase()
                    val partDesc = normalizePersian(part.description ?: "").lowercase()
                    partModel.contains(normModel) || partDeviceModel.contains(normModel) || partName.contains(normModel) || partDesc.contains(normModel)
                }

                matchesCategory && matchesBrand && matchesModel
            }
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
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isPartsLoading) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFFD97706)
                            )
                            Text(
                                text = "در حال دریافت...",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }
                Text(
                    "فروشگاه قطعات و لوازم یدکی",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CodyarNavy
                )
            }
            if (searchQuery.isNotBlank()) {
                TextButton(
                    onClick = { viewModel.clearStoreSearchQuery() },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("نمایش همه قطعات", fontSize = 12.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Store Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setStoreSearchQuery(it) },
            placeholder = { Text("جستجوی قطعه، برند (مثلاً بوتان، پکیج)...", fontSize = 12.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CodyarNavy) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearStoreSearchQuery() }) {
                        Icon(Icons.Default.Clear, contentDescription = "پاک کردن", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = CodyarNavy,
                unfocusedBorderColor = Color(0xFFE2E8F0)
            )
        )

        // Active Filter Banner (when navigated from search or error cards)
        if (searchQuery.isNotBlank()) {
            Surface(
                color = Color(0xFFEAFAF1),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFA9DFBF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(16.dp))
                        Text(
                            text = "قطعات مرتبط با: $searchQuery",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E8449),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearStoreSearchQuery() },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "بستن فیلتر", tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Smart Filter Bar (Device Category, Brand, Model)
        val isSmartFilterActive = storeSelectedCategory != "همه" || storeSelectedBrand != "همه" || storeModelQuery.isNotBlank()

        Card(
            onClick = { showStoreFilterBar = !showStoreFilterBar },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFEAECEF))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSmartFilterActive || showStoreFilterBar) CodyarNavy else CodyarTextPrimary
                    )
                    Text(
                        text = "فیلتر هوشمند (نوع دستگاه، برند، مدل)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSmartFilterActive || showStoreFilterBar) CodyarNavy else CodyarTextPrimary
                    )
                    if (isSmartFilterActive) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E8449), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("فعال", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Icon(
                    imageVector = if (showStoreFilterBar) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = CodyarTextSecondary
                )
            }
        }

        AnimatedVisibility(visible = showStoreFilterBar) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Dropdowns for Device Category and Brand
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            FilterDropdown(
                                label = "نوع دستگاه:",
                                selectedValue = storeSelectedCategory,
                                options = storeCategories,
                                onSelect = { storeSelectedCategory = it }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            FilterDropdown(
                                label = "برند قطعه:",
                                selectedValue = storeSelectedBrand,
                                options = storeBrands,
                                onSelect = { storeSelectedBrand = it }
                            )
                        }
                    }

                    // Row 2: Model Filter
                    var storeModelExpanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "مدل دستگاه / قطعه:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CodyarTextPrimary,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = storeModelQuery,
                                onValueChange = { storeModelQuery = it },
                                placeholder = { Text("مثلاً: کالدا، اپتیما، پرلا، ۲۴...", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (storeModelQuery.isNotEmpty()) {
                                            IconButton(onClick = { storeModelQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(15.dp))
                                            }
                                        }
                                        if (storeAvailableModels.isNotEmpty()) {
                                            IconButton(onClick = { storeModelExpanded = !storeModelExpanded }) {
                                                Icon(
                                                    imageVector = if (storeModelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "نمایش لیست",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
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

                            if (storeAvailableModels.isNotEmpty() && storeModelExpanded) {
                                DropdownMenu(
                                    expanded = storeModelExpanded,
                                    onDismissRequest = { storeModelExpanded = false },
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .heightIn(max = 200.dp)
                                        .background(CodyarSurface)
                                ) {
                                    storeAvailableModels.forEach { modelOpt ->
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
                                                storeModelQuery = modelOpt
                                                storeModelExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isSmartFilterActive) {
                        OutlinedButton(
                            onClick = {
                                storeSelectedCategory = "همه"
                                storeSelectedBrand = "همه"
                                storeModelQuery = ""
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.align(Alignment.End),
                            border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("× پاک کردن فیلترها", fontSize = 11.sp, color = CodyarTextPrimary)
                        }
                    }
                }
            }
        }

        if (parts.isEmpty()) {
            if (isPartsLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = CodyarNavy
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("در حال دریافت آنلاین لیست قطعات...", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CodyarNavy)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("لطفاً چند لحظه شکیبا باشید", fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📦", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("فروشگاه در حال تکمیل است", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("به زودی قطعات اضافه می‌شوند", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else if (filteredParts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🔍", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(10.dp))
                val queryLabel = if (searchQuery.isNotBlank()) "«$searchQuery»" else "فیلترهای انتخابی"
                Text("قطعه‌ای متناسب با $queryLabel یافت نشد", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CodyarNavy)
                Spacer(modifier = Modifier.height(6.dp))
                Text("می‌توانید تمام قطعات موجود را مشاهده کنید.", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        viewModel.clearStoreSearchQuery()
                        storeSelectedCategory = "همه"
                        storeSelectedBrand = "همه"
                        storeModelQuery = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("مشاهده همه قطعات فروشگاه")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredParts, key = { it.id ?: (it.name ?: "") + it.hashCode() }) { part ->
                    val inCart = cartItems.contains(part.id)
                    val outOfStock = part.resolvedStock <= 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(310.dp),
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPartForDetails = part }
                            ) {
                                // Product Image Container - Fill container with rounded top corners
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable {
                                            zoomedPartForImage = part
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val finalImg = part.image ?: part.imageUrl ?: ""
                                    if (finalImg.isNotEmpty()) {
                                        AsyncImage(
                                            model = if (finalImg.startsWith("http")) finalImg else "${com.example.data.api.KodyarRetrofitClient.siteRootUrl}/${finalImg.removePrefix("/")}",
                                            contentDescription = part.name,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        // Zoom indicator badge
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomIn,
                                                    contentDescription = "بزرگ‌نمایی عکس",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "زوم",
                                                    fontSize = 9.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Text("⚙️", fontSize = 48.sp)
                                    }

                                    // Category badge on top-right of the image (matched with website)
                                    if (part.resolvedCategory.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .background(Color(0xFF2C3E50).copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = part.resolvedCategory,
                                                fontSize = 9.5.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // Info block
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 1. Part Name
                                    Text(
                                        text = part.name ?: "",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CodyarTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // 2. Brand & Model (from Database)
                                    val brandAndModel = buildString {
                                        if (part.resolvedBrand.isNotBlank()) append(part.resolvedBrand)
                                        if (part.resolvedModel.isNotBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append("مدل ${part.resolvedModel}")
                                        }
                                    }
                                    if (brandAndModel.isNotBlank()) {
                                        Text(
                                            text = brandAndModel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = CodyarNavy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // 3. Stock & Price Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (outOfStock) "ناموجود" else "موجود (${part.resolvedStock})",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (outOfStock) Color(0xFFC0392B) else Color(0xFF1E8449)
                                        )

                                        Text(
                                            text = "${formatToman(part.price ?: 0.0)} ت",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E8449)
                                        )
                                    }
                                }
                            }

                            // Direct purchase and add to cart buttons pinned at the bottom
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (!outOfStock) {
                                            selectedPartForDetails = part
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (outOfStock) Color(0xFFF0F2F5) else CodyarNavy,
                                        contentColor = if (outOfStock) CodyarTextSecondary else Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("buy_part_direct_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !outOfStock,
                                    contentPadding = PaddingValues(vertical = 0.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = if (outOfStock) "ناموجود" else "خرید آنلاین 💳",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                Surface(
                                    onClick = { if (!outOfStock) onAddToCart(part.id ?: "") },
                                    enabled = !outOfStock,
                                    modifier = Modifier.size(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (inCart) Color(0xFFEAFAF1) else Color(0xFFF0F2F5),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (inCart) Color(0xFFA9DFBF) else Color(0xFFE2E8F0)
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (inCart) Icons.Default.Check else Icons.Default.ShoppingCart,
                                            contentDescription = "افزودن به سبد خرید",
                                            tint = if (inCart) Color(0xFF1E8449) else CodyarNavy,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- PART DETAILS & QUANTITY DIALOG ---
        val detailPart = selectedPartForDetails
        if (detailPart != null) {
            val rawStock = detailPart.stock ?: 0
            val isOutOfStock = rawStock <= 0
            val maxStock = if (rawStock > 0) rawStock else 10
            var selectedQty by remember(detailPart.id) { mutableIntStateOf(if (isOutOfStock) 0 else 1) }
            val unitPrice = detailPart.price ?: 0.0
            val totalPrice = unitPrice * selectedQty

            val defaultAddress = remember(currentUser) {
                val cityPart = currentUser?.city?.takeIf { it.isNotBlank() }
                val addressPart = currentUser?.address?.takeIf { it.isNotBlank() }
                when {
                    !cityPart.isNullOrBlank() && !addressPart.isNullOrBlank() -> "$cityPart، $addressPart"
                    !addressPart.isNullOrBlank() -> addressPart
                    !cityPart.isNullOrBlank() -> "$cityPart، "
                    else -> ""
                }
            }
            var deliveryAddress by remember(detailPart.id, currentUser) { mutableStateOf(defaultAddress) }

            AlertDialog(
                onDismissRequest = { selectedPartForDetails = null },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "مشخصات و خرید قطعه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        IconButton(onClick = { selectedPartForDetails = null }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .clickable {
                                    zoomedPartForImage = detailPart
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val imgUrl = detailPart.image ?: detailPart.imageUrl ?: ""
                            if (imgUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = if (imgUrl.startsWith("http")) imgUrl else "${com.example.data.api.KodyarRetrofitClient.siteRootUrl}/${imgUrl.removePrefix("/")}",
                                    contentDescription = detailPart.name,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                // Zoom hint button / badge
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ZoomIn,
                                            contentDescription = "بزرگ‌نمایی عکس",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "بزرگ‌نمایی عکس",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Text("⚙️", fontSize = 56.sp)
                            }
                        }

                        // Title & Device Info
                        Text(
                            text = detailPart.name ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CodyarTextPrimary
                        )

                        Text(
                            text = "⚙️ ${detailPart.deviceAndBrandSummary}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = CodyarNavy
                        )

                        if (detailPart.resolvedDescription.isNotBlank()) {
                            Text(
                                text = detailPart.resolvedDescription,
                                fontSize = 12.sp,
                                color = CodyarTextSecondary,
                                lineHeight = 18.sp
                            )
                        }

                        // Stock & Unit Price
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isOutOfStock) "ناموجود" else "وضعیت موجودی در انبار مرکزی ($rawStock عدد)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOutOfStock) Color(0xFFC0392B) else Color(0xFF1E8449)
                            )
                            Text(
                                text = "${formatToman(unitPrice)} تومان",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E8449)
                            )
                        }

                        if (!isOutOfStock) {
                            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                            // Quantity Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تعداد سفارش:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CodyarTextPrimary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (selectedQty > 1) selectedQty-- },
                                        enabled = selectedQty > 1,
                                        modifier = Modifier
                                            .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(6.dp))
                                            .size(32.dp)
                                    ) {
                                        Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = selectedQty.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.width(30.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    IconButton(
                                        onClick = { if (selectedQty < maxStock) selectedQty++ },
                                        enabled = selectedQty < maxStock,
                                        modifier = Modifier
                                            .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(6.dp))
                                            .size(32.dp)
                                    ) {
                                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Delivery Address Input
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "📍 آدرس دقیق پستی جهت تحویل قطعه:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CodyarNavy,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = deliveryAddress,
                                    onValueChange = { deliveryAddress = it },
                                    placeholder = { Text("استان، شهر، خیابان، کوچه، پلاک و واحد...", fontSize = 11.sp, color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    minLines = 2,
                                    maxLines = 3,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFFFAFAFA),
                                        unfocusedContainerColor = Color(0xFFFAFAFA),
                                        focusedBorderColor = CodyarNavy,
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    )
                                )
                            }

                            // Total Price Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFEFF6FF), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "مبلغ کل ($selectedQty عدد):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E40AF)
                                )
                                Text(
                                    text = "${formatToman(totalPrice)} تومان",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1E40AF)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!isOutOfStock) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (currentUser == null) {
                                        Toast.makeText(context, "لطفاً ابتدا وارد حساب کاربری خود شوید.", Toast.LENGTH_SHORT).show()
                                    } else if (deliveryAddress.trim().length < 5) {
                                        Toast.makeText(context, "لطفاً آدرس دقیق ارسال پستی را وارد فرمایید.", Toast.LENGTH_LONG).show()
                                    } else {
                                        val partId = detailPart.id ?: ""
                                        val partName = detailPart.name ?: "قطعه"
                                        selectedPartForDetails = null
                                        viewModel.initiateDirectPartPurchase(
                                            context = context,
                                            partId = partId,
                                            partName = partName,
                                            quantity = selectedQty,
                                            totalPrice = totalPrice,
                                            deliveryAddress = deliveryAddress.trim()
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("خرید اینترنتی و ارسال پستی 💳", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val partId = detailPart.id ?: ""
                                    viewModel.addToCartWithQty(partId, selectedQty)
                                    selectedPartForDetails = null
                                    Toast.makeText(context, "قطعه به سبد خرید اضافه شد 🛒", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("افزودن به سبد خرید 🛒", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = { selectedPartForDetails = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("بستن")
                        }
                    }
                },
                dismissButton = {}
            )
        }

        // --- PRODUCT IMAGE ZOOM DIALOG (REAL PINCH, PAN, DOUBLE TAP, BACK BUTTON) ---
        val zoomPart = zoomedPartForImage
        if (zoomPart != null) {
            ProductImageZoomDialog(
                part = zoomPart,
                onDismiss = { zoomedPartForImage = null }
            )
        }
    }
}

@Composable
fun ProductImageZoomDialog(
    part: KodyarSparePart,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val rawImg = part.image ?: part.imageUrl ?: ""
    val fullImageUrl = if (rawImg.startsWith("http")) rawImg else "${com.example.data.api.KodyarRetrofitClient.siteRootUrl}/${rawImg.removePrefix("/")}"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF00B1120))
        ) {
            // Top Bar with Back / Return Button, Title & Brand
            Surface(
                color = Color(0xDE0F172A),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "بازگشت",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = part.name ?: "تصویر قطعه",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!part.brand.isNullOrBlank()) {
                                Text(
                                    text = "برند: ${part.brand}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Return Button
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "بازگشت",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Central Zoomable Image Area (Pinch to Zoom, Pan, Double Tap)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 70.dp, bottom = 90.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val maxX = (size.width * (scale - 1f)) / 2f
                                val maxY = (size.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x * scale).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y * scale).coerceIn(-maxY, maxY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (rawImg.isNotEmpty()) {
                    AsyncImage(
                        model = fullImageUrl,
                        contentDescription = part.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("⚙️", fontSize = 72.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "تصویری برای این قطعه ثبت نشده است",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom Controls Bar with Quick Zoom Buttons & Reset
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Zoom Out (-)
                        IconButton(
                            onClick = {
                                scale = (scale - 0.5f).coerceAtLeast(1f)
                                if (scale == 1f) offset = Offset.Zero
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "کوچک‌نمایی",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Scale Indicator / Reset on click
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                                .padding(horizontal = 6.dp)
                        )

                        // Zoom In (+)
                        IconButton(
                            onClick = {
                                scale = (scale + 0.5f).coerceAtMost(5f)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "بزرگ‌نمایی",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Reset (1:1)
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.FitScreen,
                                contentDescription = "اندازه اصلی تصویر",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "با دو انگشت یا دوبار لمس می‌توانید روی عکس زوم و جابه‌جا کنید",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
