package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KodyarUser
import com.example.ui.AssistantViewModel

@Composable
fun SubscriptionPlansDialog(
    showPlansDialog: Boolean,
    onDismiss: () -> Unit,
    viewModel: AssistantViewModel,
    currentUser: KodyarUser?,
    onShowAuth: () -> Unit,
    onPurchasePlan: (String) -> Unit
) {
    if (!showPlansDialog) return
    val context = LocalContext.current

    val appliedReferralCode by viewModel.appliedReferralCode.collectAsState()
    val referralDiscountPercent by viewModel.referralDiscountPercent.collectAsState()
    val isPlansLoading by viewModel.isPlansLoading.collectAsState()

    val displayPlans = listOf(
        mapOf(
            "id" to "ir.golden.com",
            "name" to "اشتراک طلایی",
            "price" to 1500000.0,
            "tag" to "",
            "description" to "اشتراکی | ۳۰ روزه"
        ),
        mapOf(
            "id" to "ir.silver.com",
            "name" to "اشتراک نقره ای",
            "price" to 3900000.0,
            "tag" to "",
            "description" to "اشتراکی | ۹۰ روزه"
        ),
        mapOf(
            "id" to "ir.almas.com",
            "name" to "اشتراک الماس",
            "price" to 6900000.0,
            "tag" to "بهترین ارزش",
            "description" to "اشتراکی | ۱۸۰ روزه"
        ),
        mapOf(
            "id" to "ir.12-month.com",
            "name" to "اشتراک 12ماهه",
            "price" to 1190000.0,
            "tag" to "خرید به صرفه",
            "description" to "اشتراکی | ۳۶۵ روزه"
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "پکیج اشتراک کدیار۲۴",
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "جهت دسترسی کامل به تمامی بخش‌های برنامه، کدهای خطا و راهنمای عیب‌یابی، یکی از پکیج‌های زیر را تهیه کنید.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = CodyarNavy,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodyarBg, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                )

                // Referral/Coupon Code Section
                var inviteCodeInput by remember { mutableStateOf("") }
                var showInactiveDiscountDialog by remember { mutableStateOf(false) }

                if (showInactiveDiscountDialog) {
                    AlertDialog(
                        onDismissRequest = { showInactiveDiscountDialog = false },
                        icon = {
                            Text("ℹ️", fontSize = 32.sp)
                        },
                        title = {
                            Text(
                                text = "اطلاعیه کد تخفیف",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CodyarNavy
                            )
                        },
                        text = {
                            Text(
                                text = "متاسفانه فعلاً بخش کد تخفیف و معرف در فرآیند خرید فعال نشده است و خرید اشتراک‌ها مستقیماً با تعرفه مصوب از طریق درگاه بازار انجام می‌پذیرد.",
                                fontSize = 12.sp,
                                color = CodyarTextSecondary,
                                lineHeight = 20.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { showInactiveDiscountDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("متوجه شدم", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CodyarBg),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "کد تخفیف / معرف دارید؟",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = CodyarNavy
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inviteCodeInput,
                                onValueChange = { inviteCodeInput = it },
                                placeholder = { Text("کد معرف یا تخفیف", fontSize = 11.sp) },
                                modifier = Modifier.weight(1.5f).height(48.dp),
                                textStyle = TextStyle(fontSize = 12.sp),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CodyarNavy,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                )
                            )
                            Button(
                                onClick = {
                                    if (inviteCodeInput.isNotBlank()) {
                                        showInactiveDiscountDialog = true
                                    } else {
                                        Toast.makeText(context, "لطفاً کد را وارد کنید", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(0.8f).height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("اعمال کد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (isPlansLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CodyarNavy)
                    }
                } else {
                    displayPlans.forEach { plan ->
                        val hasTag = (plan["tag"] as String).isNotEmpty()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = plan["name"] as String,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = CodyarNavy
                                        )
                                        if (hasTag) {
                                            Box(
                                                modifier = Modifier
                                                    .background(CodyarRed.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = plan["tag"] as String,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CodyarRed
                                                )
                                            }
                                        }
                                    }

                                    val planPrice = plan["price"] as Double

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${formatToman(planPrice)} ریال",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = CodyarRed
                                        )
                                    }
                                }

                                val desc = plan["description"] as? String
                                if (!desc.isNullOrEmpty()) {
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = CodyarTextSecondary,
                                        lineHeight = 16.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (currentUser == null) {
                                            onDismiss()
                                            onShowAuth()
                                            Toast.makeText(context, "ابتدا وارد حساب کاربری شوید", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        
                                        // Check if Cafe Bazaar is installed on the device
                                        val hasBazaar = try {
                                            context.packageManager.getPackageInfo("com.farsitel.bazaar", 0)
                                            true
                                        } catch (e: Exception) {
                                            try {
                                                context.packageManager.getPackageInfo("ir.cafebazaar.pardakht", 0)
                                                true
                                            } catch (e2: Exception) {
                                                false
                                            }
                                        }

                                        if (hasBazaar) {
                                            onDismiss()
                                            onPurchasePlan(plan["id"] as String)
                                        } else {
                                            Toast.makeText(context, "برنامه بازار بر روی این دستگاه نصب نیست. لطفاً ابتدا بازار را نصب کنید.", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CodyarNavy
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Text(
                                            text = "خرید و فعال‌سازی اشتراک از بازار",
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
        },
        confirmButton = {}
    )
}
