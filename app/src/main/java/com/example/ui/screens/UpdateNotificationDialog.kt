package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppUpdateNotification

@Composable
fun UpdateNotificationDialog(
    updates: AppUpdateNotification?,
    isPremium: Boolean = false,
    onDismiss: () -> Unit
) {
    if (updates == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CodyarNavy.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = CodyarNavy,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "اطلاعات جدید در سایت کدیار۲۴",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CodyarNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "هم‌اکنون کدهای خطا، مشکلات فنی یا قطعات جدیدی در وب‌سایت ثبت شده و به صورت آنلاین در اپلیکیشن هماهنگ و لود گردید:",
                    fontSize = 12.sp,
                    color = CodyarTextSecondary,
                    lineHeight = 18.sp
                )

                // 1. New Error Codes
                if (updates.newErrors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🔧", fontSize = 14.sp)
                            Text("کدهای خطای جدید:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E8449))
                        }
                        updates.newErrors.forEach { error ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "کد ${error.code ?: ""} — ${error.resolvedCategory}${if (!error.brand.isNullOrBlank()) " · ${error.brand}" else ""}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF14532D)
                                    )
                                    if (isPremium && !error.title.isNullOrBlank()) {
                                        Text(
                                            text = error.title,
                                            fontSize = 11.sp,
                                            color = Color(0xFF166534),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. New Common Problems
                if (updates.newProblems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("💡", fontSize = 14.sp)
                            Text("مشکلات و ایرادات فنی جدید:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                        updates.newProblems.forEach { prob ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (isPremium) (prob.title ?: "") else "دستگاه: ${prob.brand ?: ""} - ${prob.category ?: ""}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF78350F)
                                    )
                                    if (isPremium && (!prob.brand.isNullOrBlank() || !prob.category.isNullOrBlank())) {
                                        Text(
                                            text = "دستگاه: ${prob.brand ?: ""} - ${prob.category ?: ""}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF92400E),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. New Spare Parts / Products
                if (updates.newParts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📦", fontSize = 14.sp)
                            Text("محصولات و قطعات یدکی جدید:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                        }
                        updates.newParts.forEach { part ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = part.name ?: "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    Text(
                                        text = "برند: ${part.brand ?: ""} | قیمت: ${formatToman(part.price ?: 0.0)} تومان",
                                        fontSize = 11.sp,
                                        color = Color(0xFF1E40AF),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("متوجه شدم و بررسی کدهای جدید", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    )
}
