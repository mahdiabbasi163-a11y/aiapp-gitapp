package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    val bankCardInfo by viewModel.bankCardInfo.collectAsState()
    val isPurchaseLoading by viewModel.isPurchaseLoading.collectAsState()

    val cardNum = bankCardInfo?.cardNumber ?: bankCardInfo?.card_number ?: "۶۱۰۴-۳۳۸۹-۶۱۱۲-۶۶۶۷"
    val cardHolderName = bankCardInfo?.cardHolder ?: bankCardInfo?.card_holder ?: "مهدی عباسی (مدیر سایت کدیار۲۴)"
    val bankName = bankCardInfo?.bankName ?: bankCardInfo?.bank_name ?: "بانک ملت"

    var trackingNumberInput by remember { mutableStateOf("") }
    var depositorNameInput by remember(currentUser) { mutableStateOf(currentUser?.full_name ?: "") }
    var deliveryAddressInput by remember(currentUser) { mutableStateOf(currentUser?.address ?: currentUser?.city ?: "") }

    AlertDialog(
        onDismissRequest = { if (!isPurchaseLoading) onDismiss() },
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
                IconButton(onClick = { if (!isPurchaseLoading) onDismiss() }) {
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

                    // Manager Card Info Box for Card-to-Card Payment
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "💳 پرداخت به روش کارت به کارت",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = Color(0xFF166534)
                            )
                            Text(
                                text = "لطفاً مبلغ کل را به شماره کارت مدیر سایت واریز نموده و پس از واریز، کد پیگیری و نام خود را وارد کرده و دکمه «تایید و ثبت سفارش» را بزنید:",
                                fontSize = 11.5.sp,
                                color = Color(0xFF166534),
                                lineHeight = 18.sp
                            )
                            Divider(color = Color(0xFFDCFCE7), thickness = 0.8.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("شماره کارت مدیر سایت جهت واریز:", fontSize = 11.sp, color = Color(0xFF166534))
                                    Text(
                                        text = cardNum,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF15803D),
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "$cardHolderName - $bankName",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF166534)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Card Number", cardNum.replace("-", "").replace(" ", ""))
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "شماره کارت کپی شد", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "کپی شماره کارت", tint = Color(0xFF16A34A))
                                }
                            }
                        }
                    }

                    // Input fields: Tracking code, Depositor Name, Delivery Address
                    OutlinedTextField(
                        value = trackingNumberInput,
                        onValueChange = { trackingNumberInput = it },
                        label = { Text("شماره پیگیری / کد رهگیری واریز *") },
                        placeholder = { Text("مثلاً ۱۲۳۴۵۶۷۸") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cart_tracking_number_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = depositorNameInput,
                        onValueChange = { depositorNameInput = it },
                        label = { Text("نام واریزکننده (صاحب کارت)") },
                        placeholder = { Text("مثلاً علی رضایی") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cart_depositor_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = deliveryAddressInput,
                        onValueChange = { deliveryAddressInput = it },
                        label = { Text("آدرس دقیق پستی جهت ارسال قطعات *") },
                        placeholder = { Text("استان، شهر، خیابان، پلاک، کد پستی...") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cart_address_input"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (cartItemsList.isNotEmpty()) {
                Button(
                    onClick = {
                        val user = currentUser
                        if (user == null) {
                            Toast.makeText(context, "جهت ثبت سفارش، لطفاً ابتدا وارد حساب کاربری شوید.", Toast.LENGTH_LONG).show()
                            onShowAuth()
                            return@Button
                        }
                        if (trackingNumberInput.trim().length < 4) {
                            Toast.makeText(context, "لطفاً کد رهگیری واریز به شماره کارت مدیر سایت را وارد فرمایید.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (deliveryAddressInput.trim().length < 5) {
                            Toast.makeText(context, "لطفاً آدرس دقیق ارسال را وارد فرمایید.", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        viewModel.submitPartPurchaseOrdersToServer(
                            cardHolder = depositorNameInput.trim().ifBlank { user.full_name ?: "" },
                            trackNumber = trackingNumberInput.trim(),
                            address = deliveryAddressInput.trim(),
                            onResult = { success, errorMsg ->
                                if (success) {
                                    onDismiss()
                                    Toast.makeText(
                                        context,
                                        "سفارش شما با موفقیت ثبت شد و پس از تأیید واریزی توسط مدیر سایت، موجودی انبار به‌روز شده و سفارش آماده ارسال می‌گردد.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, errorMsg ?: "خطا در ثبت سفارش", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    },
                    enabled = !isPurchaseLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_order_button"),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    if (isPurchaseLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("تایید و ثبت سفارش ✅", fontWeight = FontWeight.Bold)
                    }
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
