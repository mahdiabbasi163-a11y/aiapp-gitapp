package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.KodyarRepairOrder
import com.example.data.model.KodyarTechnician
import com.example.data.model.TechnicianReview
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RateTechnicianDialog(
    order: KodyarRepairOrder,
    onDismiss: () -> Unit,
    onSubmit: (overallRating: Double, skillRating: Double, ethicsRating: Double, punctualityRating: Double, comment: String) -> Unit
) {
    var skillRating by remember { mutableStateOf((order.skill_rating ?: 5.0).toInt().coerceIn(1, 5)) }
    var ethicsRating by remember { mutableStateOf((order.ethics_rating ?: 5.0).toInt().coerceIn(1, 5)) }
    var punctualityRating by remember { mutableStateOf((order.punctuality_rating ?: 5.0).toInt().coerceIn(1, 5)) }
    var comment by remember { mutableStateOf(order.rating_comment ?: "") }
    var isSubmitting by remember { mutableStateOf(false) }

    val calculatedOverall = remember(skillRating, ethicsRating, punctualityRating) {
        val avg = (skillRating + ethicsRating + punctualityRating) / 3.0
        (avg * 10).roundToInt() / 10.0
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (!isSubmitting) onDismiss() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "بستن", tint = Color.Gray)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "⭐ ثبت نظر و امتیاز به تکنسین",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        Text(
                            text = "سفارش ${order.resolvedOrderId}",
                            fontSize = 11.sp,
                            color = CodyarTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Technician Info Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0E7FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF4338CA),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = order.resolvedTechnicianName.ifBlank { "تکنسین اعزامی کدیار" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CodyarTextPrimary
                            )
                            if (order.resolvedCategory.isNotBlank()) {
                                Text(
                                    text = "خدمت انجام شده: ${order.resolvedCategory} ${order.resolvedBrand}",
                                    fontSize = 11.sp,
                                    color = CodyarTextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3 Criteria Star Ratings
                Text(
                    text = "لطفاً تجربه خود از انجام کار را ارزیابی کنید:",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CodyarTextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    textAlign = TextAlign.Right
                )

                // 1. Skill & Expertise
                InteractiveRatingRow(
                    title = "⚙️ تخصص و مهارت فنی تکنسین",
                    subtitle = "تسلط بر عیب‌یابی و کیفیت تعمیر قطعات",
                    rating = skillRating,
                    onRatingChanged = { skillRating = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Ethics & Behavior
                InteractiveRatingRow(
                    title = "🤝 برخورد، اخلاق و رفتار حرفه‌ای",
                    subtitle = "احترام به مشتری و رعایت اصول اخلاقی",
                    rating = ethicsRating,
                    onRatingChanged = { ethicsRating = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Process & Punctuality
                InteractiveRatingRow(
                    title = "⏱️ فرایند کار، نظم و زمان‌بندی",
                    subtitle = "حضور به موقع و سرعت انجام خدمت",
                    rating = punctualityRating,
                    onRatingChanged = { punctualityRating = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Calculated Average Score Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val scoreText = when {
                            calculatedOverall >= 4.8 -> "عالی و کم‌نظیر 🌟"
                            calculatedOverall >= 4.0 -> "بسیار خوب و رضایت‌بخش 👍"
                            calculatedOverall >= 3.0 -> "متوسط و قابل قبول 👌"
                            else -> "نیازمند بهبود ⚠️"
                        }
                        Text(
                            text = scoreText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "امتیاز کل: ${String.format(Locale.US, "%.1f", calculatedOverall)} از ۵",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF78350F)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Feedback / Comment TextField
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("متن نظر و تجربیات شما (اختیاری)") },
                    placeholder = { Text("درباره تخصص، اخلاق، قیمت‌گذاری و فرایند انجام کار بنویسید...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Public Visibility notice
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFDCFCE7))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🔒", fontSize = 14.sp)
                        Text(
                            text = "این امتیاز و نظر مستقیماً در کارنامه و رتبه تکنسین ثبت شده و برای سایر مشتریان قابل مشاهده است.",
                            fontSize = 10.5.sp,
                            color = Color(0xFF166534),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("انصراف", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            isSubmitting = true
                            onSubmit(
                                calculatedOverall,
                                skillRating.toDouble(),
                                ethicsRating.toDouble(),
                                punctualityRating.toDouble(),
                                comment.trim()
                            )
                        },
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "ثبت و ارسال امتیاز ✅",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveRatingRow(
    title: String,
    subtitle: String,
    rating: Int,
    onRatingChanged: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFAFAFA),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Stars (Clickable 1..5)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        IconButton(
                            onClick = { onRatingChanged(i) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$i ستاره",
                                tint = if (i <= rating) Color(0xFFFFB000) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Title
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = CodyarTextPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = CodyarTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun TechnicianReviewsDialog(
    technician: KodyarTechnician,
    reviews: List<TechnicianReview>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "بستن", tint = Color.Gray)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "⭐ کارنامه و نظرات مشتریان",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CodyarNavy
                        )
                        Text(
                            text = technician.name ?: "متخصص کدیار",
                            fontSize = 12.sp,
                            color = CodyarTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Technician Score Summary Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Big Rating Number & Stars
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", technician.resolvedRating),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFD97706)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    for (i in 1..5) {
                                        val fillThreshold = i - 0.3
                                        val isFilled = technician.resolvedRating >= fillThreshold
                                        Icon(
                                            imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = if (isFilled) Color(0xFFFFB000) else Color(0xFFCBD5E1),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                val count = maxOf(reviews.size, technician.ratingsCount ?: technician.ratings_count ?: 0)
                                Text(
                                    text = "از $count نظر ثبت‌شده",
                                    fontSize = 10.sp,
                                    color = CodyarTextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            // Technician details
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = technician.name ?: "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = CodyarTextPrimary
                                    )
                                    Text(
                                        text = "📍 ${technician.city ?: "اراک"}",
                                        fontSize = 11.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Surface(
                                        color = Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "✓ ${technician.satisfactionRate ?: 100}% رضایت مشتریان",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                val avatarUrl = technician.resolvedAvatarUrl
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, CodyarNavy, CircleShape)
                                        .background(Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarUrl != null) {
                                        AsyncImage(
                                            model = avatarUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 3 Sub-criteria rating bars
                        RatingMetricBar(
                            title = "⚙️ مهارت و تخصص فنی",
                            score = technician.resolvedSkillRating
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        RatingMetricBar(
                            title = "🤝 برخورد و اخلاق حرفه‌ای",
                            score = technician.resolvedEthicsRating
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        RatingMetricBar(
                            title = "⏱️ نظم، وقت‌شناسی و فرایند",
                            score = technician.resolvedPunctualityRating
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reviews List Section
                Text(
                    text = "نظرات و بازخوردهای ثبت‌شده (${reviews.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CodyarTextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    textAlign = TextAlign.Right
                )

                if (reviews.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFFFAFAFA), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text("💬", fontSize = 32.sp)
                            Text(
                                text = "هنوز نظری برای این متخصص ثبت نشده است",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary
                            )
                            Text(
                                text = "پس از دریافت خدمت، مشتریان می‌توانند در بخش سفارش‌ها به تخصص و اخلاق تکنسین نظر دهند.",
                                fontSize = 11.sp,
                                color = CodyarTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(reviews) { review ->
                            ReviewItemCard(review = review)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بستن کارنامه", fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun RatingMetricBar(title: String, score: Double) {
    val progress = (score / 5.0).toFloat().coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${String.format(Locale.US, "%.1f", score)} / ۵",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CodyarTextPrimary
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(90.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFFD97706),
                trackColor = Color(0xFFE2E8F0)
            )
        }

        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = CodyarTextPrimary
        )
    }
}

@Composable
private fun ReviewItemCard(review: TechnicianReview) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF9FAFB),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Star display
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.US, "%.1f", review.rating),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB000),
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Customer Name & Date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val dateText = review.resolvedDate
                    if (dateText.isNotBlank()) {
                        Text(
                            text = dateText,
                            fontSize = 10.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                    Text(
                        text = review.customer_name ?: "مشتری کدیار",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CodyarTextPrimary
                    )
                    Text("👤", fontSize = 12.sp)
                }
            }

            // Sub-scores pill row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "تخصص: ${review.skill_rating.toInt()}  |  اخلاق: ${review.ethics_rating.toInt()}  |  نظم: ${review.punctuality_rating.toInt()}",
                        fontSize = 9.5.sp,
                        color = Color(0xFF1E40AF),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (!review.comment.isNullOrBlank()) {
                Text(
                    text = review.comment,
                    fontSize = 11.5.sp,
                    color = CodyarTextPrimary,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.Right
                )
            }
        }
    }
}
