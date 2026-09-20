package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.model.KodyarSparePart
import com.example.data.model.KodyarUser
import com.example.ui.AssistantViewModel

@Composable
fun CartDialog(
    showCartDialog: Boolean,
    onDismiss: () -> Unit,
    cartItemsList: List<String>,
    cartQtyMap: Map<String, Int>,
    liveSpareParts: List<KodyarSparePart>,
    currentUser: KodyarUser?,
    viewModel: AssistantViewModel,
    onShowAuth: () -> Unit
) {
    if (!showCartDialog) return
    val context = LocalContext.current

    val cartTotal = cartItemsList.sumOf { partId ->
        val part = liveSpareParts.find { it.id == partId }
        val qty = cartQtyMap[partId] ?: 1
        (part?.price ?: 0.0) * qty
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
                    text = "سبد خرید قطعات",
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
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (cartItemsList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🛒", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("سبد خرید شما خالی است", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    cartItemsList.forEach { partId ->
                        val part = liveSpareParts.find { it.id == partId }
                        if (part != null) {
                            val qty = cartQtyMap[partId] ?: 1
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val finalImg = part.image ?: part.imageUrl ?: ""
                                        if (finalImg.isNotEmpty()) {
                                            AsyncImage(
                                                model = if (finalImg.startsWith("http")) finalImg else "${com.example.data.api.KodyarRetrofitClient.siteRootUrl}/${finalImg.removePrefix("/")}",
                                                contentDescription = part.name,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Text("⚙️", fontSize = 28.sp)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            part.name ?: "",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = CodyarTextPrimary
                                        )
                                        Text(
                                            "${formatToman(part.price ?: 0.0)} تومان",
                                            fontSize = 11.sp,
                                            color = CodyarTextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.updateCartQty(partId, qty - 1) },
                                                modifier = Modifier
                                                    .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(5.dp))
                                                    .size(26.dp)
                                            ) {
                                                Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = qty.toString(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.width(22.dp),
                                                textAlign = TextAlign.Center
                                            )
                                            IconButton(
                                                onClick = { viewModel.updateCartQty(partId, qty + 1) },
                                                modifier = Modifier
                                                    .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(5.dp))
                                                    .size(26.dp)
                                            ) {
                                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            TextButton(
                                                onClick = { viewModel.removeFromCart(partId) },
                                                colors = ButtonDefaults.textButtonColors(contentColor = CodyarRed)
                                            ) {
                                                Text("حذف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("جمع کل اقلام:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "${formatToman(cartTotal)} تومان",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CodyarRed
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "💳 پرداخت آنلاین و مستقیم در سایت",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1E40AF)
                            )
                            Text(
                                text = "جهت نهایی‌سازی سفارش و پرداخت، به درگاه پرداخت وب‌سایت کدیار۲۴ هدایت خواهید شد. پس از پرداخت آنلاین، سفارش شما ثبت شده، موجودی انبار به‌روز شده و سوابق در هر دو پنل سایت و اپلیکیشن ثبت می‌گردد.",
                                fontSize = 11.sp,
                                color = Color(0xFF1E40AF),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (cartItemsList.isNotEmpty()) {
                Button(
                    onClick = {
                        val user = currentUser
                        if (user == null) {
                            Toast.makeText(context, "جهت اقدام به پرداخت و نهایی‌سازی سفارش، لطفاً ثبت‌نام یا ورود کنید.", Toast.LENGTH_LONG).show()
                            onShowAuth()
                        } else {
                            val firstPartId = cartItemsList.firstOrNull() ?: ""
                            val firstPart = liveSpareParts.find { it.id == firstPartId }
                            val qty = cartQtyMap[firstPartId] ?: 1
                            onDismiss()
                            viewModel.initiateDirectPartPurchase(
                                context = context,
                                partId = firstPartId,
                                partName = firstPart?.name ?: "قطعه سبد خرید",
                                quantity = qty,
                                totalPrice = cartTotal
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_order_button"),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text("تایید نهایی و اتصال به درگاه پرداخت 💳", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بستن", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {}
    )
}
