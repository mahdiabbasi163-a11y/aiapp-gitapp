package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KodyarCommonProblem
import com.example.ui.AssistantViewModel
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProblemsScreen(
    viewModel: AssistantViewModel,
    liveProblems: StateFlow<List<KodyarCommonProblem>>,
    selectedProblemDetail: KodyarCommonProblem?,
    onSelectProblem: (KodyarCommonProblem) -> Unit,
    onBack: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    onNavigateToStore: () -> Unit = {},
    isPremium: Boolean,
    freeProblemCount: Int,
    onShowPlans: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val problemsList by liveProblems.collectAsState()
    var problemsSearchQuery by remember { mutableStateOf("") }

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

    if (selectedProblemDetail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Text(
                "مشکلات رایج لوازم خانگی",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CodyarNavy,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Search within problems
            OutlinedTextField(
                value = problemsSearchQuery,
                onValueChange = { problemsSearchQuery = it },
                placeholder = { Text("جستجو در مشکلات...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CodyarNavy,
                    unfocusedBorderColor = Color(0xFFDDE1E7)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            val filtered = remember(problemsList, problemsSearchQuery) {
                if (problemsSearchQuery.isBlank()) {
                    problemsList
                } else {
                    problemsList.filter {
                        (it.title ?: "").contains(problemsSearchQuery, ignoreCase = true) ||
                        (it.brand ?: "").contains(problemsSearchQuery, ignoreCase = true) ||
                        (it.category ?: "").contains(problemsSearchQuery, ignoreCase = true)
                    }
                }
            }

            // Problem Item lists
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id ?: "${it.title}_${it.brand}_${it.category}" }) { prob ->
                    val devCat = prob.category?.trim()?.takeIf { it.isNotBlank() } ?: "دستگاه"
                    val br = prob.brand?.trim()?.takeIf { it.isNotBlank() } ?: "عمومی"
                    val md = prob.model?.trim()?.takeIf { it.isNotBlank() } ?: "عمومی"

                    Card(
                        onClick = { onSelectProblem(prob) },
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFF0F2F5), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔧", fontSize = 22.sp)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prob.title ?: "مشکل دستگاه",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = CodyarTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "$devCat · $br · مدل: $md",
                                    fontSize = 12.sp,
                                    color = CodyarTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPremium) Icons.Default.CheckCircle else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isPremium) Color(0xFF1E8449) else Color(0xFFD97706),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (isPremium) "راهکار موجود" else "نیازمند اشتراک",
                                        fontSize = 10.sp,
                                        color = if (isPremium) Color(0xFF1E8449) else Color(0xFFD97706),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isPremium) Icons.Default.KeyboardArrowLeft else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isPremium) CodyarTextSecondary else Color(0xFFD97706)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // PROBLEM DETAIL VIEW SCREEN
        val prob = selectedProblemDetail
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(scrollState)
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

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CodyarNavy)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Column {
                            Text(
                                text = prob.title ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${prob.brand ?: ""} ${if (!prob.category.isNullOrBlank()) "· ${prob.category}" else ""}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Description
                        if (!prob.description.isNullOrBlank()) {
                            Text(
                                text = prob.description,
                                fontSize = 15.sp,
                                color = CodyarTextPrimary,
                                lineHeight = 30.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                                    .padding(14.dp)
                            )
                        }

                        // Causes
                        val causesList = with(viewModel) { prob.causes.toListOfStrings() }
                        if (causesList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(14.dp))
                                    Text("علت‌های احتمالی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
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

                        // Solutions / Steps
                        val solList = with(viewModel) { prob.steps.toListOfStrings() }
                        if (solList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                                    Text("راه‌حل و مراحل رفع", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    solList.forEachIndexed { i, sol ->
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
                                                text = sol,
                                                fontSize = 15.sp,
                                                color = CodyarTextPrimary,
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Source Disclaimer
                        Text(
                            text = "منبع: وب‌سایت رسمی کدیار۲۴ (kodyar24.ir) و تجربیات تکنسین‌های مجرب لوازم خانگی",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        // Actions Grid (2 Columns x 2 Rows)
                        val videoUrl = prob.resolvedVideoUrl ?: (prob.video_url ?: prob.videoUrl)?.takeIf { it.isNotBlank() }

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
                                            category = prob.category,
                                            brand = prob.brand,
                                            model = prob.model,
                                            title = prob.title,
                                            code = null,
                                            description = prob.description,
                                            causes = prob.causes
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
                                            Toast.makeText(context, "ویدیوی آموزشی برای این مورد ثبت نشده است", Toast.LENGTH_SHORT).show()
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

                                // دکمه کپی متن عیب‌یابی
                                Button(
                                    onClick = {
                                        val stepsToCopy = with(viewModel) { prob.steps.toListOfStrings() }
                                        clipboardManager.setText(
                                            AnnotatedString(
                                                "مشکل: ${prob.title ?: ""}\nدستگاه: ${prob.category ?: ""} ${prob.brand ?: ""}${if (!prob.model.isNullOrBlank()) " · مدل: ${prob.model}" else ""}\nشرح عیب: ${prob.description ?: ""}\nمراحل حل:\n${stepsToCopy.joinToString("\n")}"
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
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("کپی عیب‌یابی", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
