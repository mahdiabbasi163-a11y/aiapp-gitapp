package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RegisterRequiredDialog(
    showRegisterRequiredDialog: Boolean,
    onDismiss: () -> Unit,
    onNavigateToAuth: () -> Unit
) {
    if (!showRegisterRequiredDialog) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = "ثبت‌نام در کدیار۲۴",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CodyarNavy
                )
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = "برای دسترسی به این بخش و امکانات برنامه، ابتدا باید در برنامه ثبت‌نام کنید یا وارد حساب کاربری خود شوید.",
                    fontSize = 13.sp,
                    color = CodyarTextSecondary,
                    textAlign = TextAlign.Right
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onNavigateToAuth()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("ثبت‌نام / ورود", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = Color(0xFF718096))
            }
        }
    )
}

@Composable
fun PremiumRequiredDialog(
    showPremiumRequiredDialog: Boolean,
    onDismiss: () -> Unit,
    onShowPlans: () -> Unit
) {
    if (!showPremiumRequiredDialog) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = "نیاز به اشتراک فعال",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CodyarNavy
                )
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = "برای مشاهده کارت کامل عیب‌یابی و راهکارهای تخصصی، تهیه اشتراک کدیار۲۴ الزامی است.",
                    fontSize = 13.sp,
                    color = CodyarTextSecondary,
                    textAlign = TextAlign.Right
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onShowPlans()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("خرید اشتراک کدیار۲۴", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = Color(0xFF718096))
            }
        }
    )
}
