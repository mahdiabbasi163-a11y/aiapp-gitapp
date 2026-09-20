package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.TicketModel
import com.example.data.model.TicketReplyModel
import com.example.ui.AssistantViewModel

private enum class TicketScreenMode {
    LIST,
    DETAIL,
    CREATE
}

@Composable
fun SupportTicketDialog(
    viewModel: AssistantViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tickets by viewModel.userTickets.collectAsState()
    val isLoading by viewModel.isTicketsLoading.collectAsState()
    val errorMessage by viewModel.ticketsErrorMessage.collectAsState()
    val selectedTicket by viewModel.selectedTicket.collectAsState()

    var screenMode by remember { mutableStateOf(TicketScreenMode.LIST) }

    // Initial load and silent background polling while dialog is open
    LaunchedEffect(Unit) {
        viewModel.loadUserTickets()
        while (true) {
            kotlinx.coroutines.delay(25_000L) // Poll every 25 seconds silently
            viewModel.loadUserTickets(silent = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = CodyarBg,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CodyarBg)
                ) {
                    // Header Bar
                    Surface(
                        color = CodyarNavy,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (screenMode != TicketScreenMode.LIST) {
                                    IconButton(
                                        onClick = {
                                            if (screenMode == TicketScreenMode.DETAIL) {
                                                viewModel.selectTicket(null)
                                            }
                                            screenMode = TicketScreenMode.LIST
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "بازگشت",
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x22FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🎫", fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column {
                                    Text(
                                        text = when (screenMode) {
                                            TicketScreenMode.LIST -> "پشتیبانی و ثبت تیکت"
                                            TicketScreenMode.DETAIL -> "گفتگوی پشتیبانی"
                                            TicketScreenMode.CREATE -> "ارسال پیام جدید به پشتیبانی"
                                        },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "پاسخگویی مستقیم توسط تیم پشتیبانی",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "بستن",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Content based on screen mode
                    Box(modifier = Modifier.weight(1f)) {
                        when (screenMode) {
                            TicketScreenMode.LIST -> {
                                TicketListContent(
                                    tickets = tickets,
                                    isLoading = isLoading,
                                    errorMessage = errorMessage,
                                    onRefresh = { viewModel.loadUserTickets() },
                                    onCreateNew = { screenMode = TicketScreenMode.CREATE },
                                    onSelectTicket = { ticket ->
                                        viewModel.selectTicket(ticket)
                                        screenMode = TicketScreenMode.DETAIL
                                    }
                                )
                            }
                            TicketScreenMode.DETAIL -> {
                                val current = selectedTicket
                                if (current != null) {
                                    TicketDetailContent(
                                        ticket = current,
                                        viewModel = viewModel,
                                        onReplySuccess = {
                                            Toast.makeText(context, "پاسخ شما با موفقیت ارسال شد.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("تیکت یافت نشد", color = CodyarTextSecondary)
                                    }
                                }
                            }
                            TicketScreenMode.CREATE -> {
                                CreateTicketContent(
                                    viewModel = viewModel,
                                    onSuccess = { createdTicket ->
                                        Toast.makeText(context, "تیکت با موفقیت ثبت شد و در صف بررسی قرار گرفت.", Toast.LENGTH_LONG).show()
                                        if (createdTicket != null) {
                                            viewModel.selectTicket(createdTicket)
                                            screenMode = TicketScreenMode.DETAIL
                                        } else {
                                            screenMode = TicketScreenMode.LIST
                                        }
                                    },
                                    onCancel = { screenMode = TicketScreenMode.LIST }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketListContent(
    tickets: List<TicketModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onCreateNew: () -> Unit,
    onSelectTicket: (TicketModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Quick Action Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "سوال، انتقاد یا مشکلی دارید؟",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CodyarNavy
                    )
                    Text(
                        text = "تیکت پشتیبانی ارسال کنید تا سریعاً پاسخ دهیم",
                        fontSize = 11.sp,
                        color = CodyarTextSecondary
                    )
                }

                Button(
                    onClick = onCreateNew,
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ارسال پیام جدید", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Surface(
                color = Color(0xFFFEE2E2),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFF991B1B),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRefresh) {
                        Text("تلاش مجدد", fontSize = 11.sp, color = Color(0xFF991B1B), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isLoading && tickets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CodyarNavy, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("در حال دریافت تیکت‌های شما...", color = CodyarTextSecondary, fontSize = 12.sp)
                }
            }
        } else if (tickets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📬", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "هنوز تیکتی ثبت نکرده‌اید",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CodyarTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "در صورت بروز هرگونه مشکل در استعلام کدهای خطا، فعال‌سازی اشتراک یا سفارش قطعات، می‌توانید به پشتیبانی تیکت بزنید.",
                        fontSize = 12.sp,
                        color = CodyarTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onCreateNew,
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ایجاد اولین تیکت پشتیبانی", fontSize = 13.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tickets, key = { it.id }) { ticket ->
                    TicketItemCard(ticket = ticket, onClick = { onSelectTicket(ticket) })
                }
            }
        }
    }
}

@Composable
private fun TicketItemCard(
    ticket: TicketModel,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Category Tag
                Surface(
                    color = when (ticket.category?.lowercase()) {
                        "technical", "فنی" -> Color(0xFFEFF6FF)
                        "sales", "فروش" -> Color(0xFFFEF3C7)
                        "accounting", "مالی" -> Color(0xFFF0FDF4)
                        else -> Color(0xFFF3F4F6)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = ticket.categoryDisplay,
                        color = when (ticket.category?.lowercase()) {
                            "technical", "فنی" -> Color(0xFF1D4ED8)
                            "sales", "فروش" -> Color(0xFFB45309)
                            "accounting", "مالی" -> Color(0xFF15803D)
                            else -> Color(0xFF4B5563)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Status Badge
                val disp = ticket.statusDisplay
                val (statusBg, statusFg) = when {
                    disp == "رسیدگی و بسته شد" || disp == "حل شده" || disp == "بسته شده" -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
                    disp == "پاسخ داده شد" || disp == "پاسخ داده شده" -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
                    disp == "بررسی مجدد" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
                    disp == "در حال بررسی توسط کارشناس" || disp == "در حال بررسی" -> Pair(Color(0xFFE0F2FE), Color(0xFF0369A1))
                    else -> Pair(Color(0xFFE0F2FE), Color(0xFF0369A1))
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = ticket.statusDisplay,
                        color = statusFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subject
            Text(
                text = ticket.subject,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CodyarNavy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Message preview
            Text(
                text = ticket.message,
                fontSize = 12.sp,
                color = CodyarTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Replies count
                val replyCount = ticket.replies?.size ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = if (replyCount > 0) CodyarNavy else Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (replyCount > 0) "$replyCount پاسخ" else "بدون پاسخ",
                        fontSize = 11.sp,
                        color = if (replyCount > 0) CodyarNavy else Color(0xFF94A3B8),
                        fontWeight = if (replyCount > 0) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Date
                val rawDate = ticket.resolvedDate
                val formattedDate = if (rawDate.isNotBlank()) convertGregorianToJalali(rawDate) else ""
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun TicketDetailContent(
    ticket: TicketModel,
    viewModel: AssistantViewModel,
    onReplySuccess: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val replies = ticket.replies ?: emptyList()

    LaunchedEffect(replies.size) {
        if (replies.isNotEmpty()) {
            listState.animateScrollToItem(replies.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Ticket Info Header Card
        Surface(
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = ticket.subject,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CodyarNavy,
                        modifier = Modifier.weight(1f)
                    )

                    val disp = ticket.statusDisplay
                    val (statusBg, statusFg) = when {
                        disp == "حل شده" || disp == "پاسخ داده شده" -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
                        disp == "بسته شده" -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
                        disp == "بررسی مجدد" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
                        disp == "در حال بررسی" -> Pair(Color(0xFFE0F2FE), Color(0xFF0369A1))
                        else -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
                    }

                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = ticket.statusDisplay,
                            color = statusFg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "دسته‌بندی: ${ticket.categoryDisplay}",
                        fontSize = 11.sp,
                        color = CodyarTextSecondary
                    )

                    val jalaliDate = convertGregorianToJalali(ticket.resolvedDate)
                    if (jalaliDate.isNotBlank()) {
                        Text(
                            text = "تاریخ: $jalaliDate",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // Conversation List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // First item: Original User Request / Problem
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(CodyarNavy),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👤", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "شرح درخواست شما (شروع تیکت)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = CodyarNavy
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ticket.message,
                            fontSize = 13.sp,
                            color = CodyarTextPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            if (replies.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                        Text(
                            text = "پاسخ‌ها و گفتگو",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                    }
                }
            }

            // Replies
            items(replies) { reply ->
                ReplyBubble(reply = reply)
            }
        }

        // Bottom Input Area for sending reply
        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("پاسخ خود را اینجا بنویسید...", fontSize = 12.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp, max = 110.dp),
                    shape = RoundedCornerShape(20.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Right),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CodyarNavy,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (replyText.isNotBlank() && !isSending) {
                            isSending = true
                            viewModel.sendTicketReply(
                                ticketId = ticket.id,
                                message = replyText,
                                onSuccess = {
                                    isSending = false
                                    replyText = ""
                                    onReplySuccess()
                                },
                                onError = {
                                    isSending = false
                                }
                            )
                        }
                    },
                    enabled = replyText.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (replyText.isNotBlank() && !isSending) CodyarNavy else Color(0xFFE2E8F0))
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "ارسال",
                            tint = if (replyText.isNotBlank()) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyBubble(reply: TicketReplyModel) {
    val isAdmin = reply.isAdmin

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isAdmin) Alignment.Start else Alignment.End
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f),
            colors = CardDefaults.cardColors(
                containerColor = if (isAdmin) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
            ),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isAdmin) 2.dp else 12.dp,
                bottomEnd = if (isAdmin) 12.dp else 2.dp
            ),
            border = BorderStroke(
                1.dp,
                if (isAdmin) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAdmin) {
                            Text("🛡️", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "پاسخ پشتیبان کدیار۲۴",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF15803D)
                            )
                        } else {
                            Text("👤", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "شما",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = CodyarNavy
                            )
                        }
                    }

                    val dateText = convertGregorianToJalali(reply.resolvedDate)
                    if (dateText.isNotBlank()) {
                        Text(
                            text = dateText,
                            fontSize = 10.sp,
                            color = if (isAdmin) Color(0xFF15803D).copy(alpha = 0.7f) else Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = reply.message,
                    fontSize = 12.sp,
                    color = CodyarTextPrimary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun CreateTicketContent(
    viewModel: AssistantViewModel,
    onSuccess: (TicketModel?) -> Unit,
    onCancel: () -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("technical") }
    var message by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        Triple("technical", "فنی و عیب‌یابی", "کدهای خطا، پکیج، کولر گازی، لباسشویی"),
        Triple("sales", "فروش و قطعات", "استعلام قیمت، سفارش برد و قطعات یدکی"),
        Triple("accounting", "پشتیبانی مالی", "فعال‌سازی اشتراک، فاکتور و پرداخت"),
        Triple("general", "عمومی و پیشنهادات", "انتقادات، پیشنهادات و سایر موارد")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text(
            text = "موضوع تیکت",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = CodyarNavy,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            placeholder = { Text("مثلاً: عدم نمایش دیاگرام پکیج بوتان...", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = TextAlign.Right),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CodyarNavy,
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "بخش و دسته‌بندی پشتیبانی",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = CodyarNavy,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { (catKey, catTitle, catDesc) ->
                val isSelected = selectedCategory == catKey
                Surface(
                    onClick = { selectedCategory = catKey },
                    color = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) CodyarNavy else Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedCategory = catKey },
                            colors = RadioButtonDefaults.colors(selectedColor = CodyarNavy)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = catTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected) CodyarNavy else CodyarTextPrimary
                            )
                            Text(
                                text = catDesc,
                                fontSize = 10.sp,
                                color = CodyarTextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "متن پیام / شرح درخواست",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = CodyarNavy,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            placeholder = { Text("لطفاً شرح مشکل یا سوال خود را با جزئیات کامل بنویسید...", fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            shape = RoundedCornerShape(10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = TextAlign.Right),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CodyarNavy,
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        if (!localError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚠️ $localError",
                color = Color(0xFFDC2626),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("انصراف", fontSize = 13.sp, color = CodyarTextSecondary)
            }

            val deptTitle = categories.find { it.first == selectedCategory }?.second ?: "پشتیبانی"
            Button(
                onClick = {
                    if (subject.isBlank()) {
                        localError = "لطفاً موضوع تیکت را وارد کنید."
                        return@Button
                    }
                    if (message.isBlank()) {
                        localError = "لطفاً شرح پیام را بنویسید."
                        return@Button
                    }
                    localError = null
                    isSubmitting = true
                    viewModel.createNewTicket(
                        subject = subject,
                        category = selectedCategory,
                        department = deptTitle,
                        message = message,
                        onSuccess = { created ->
                            isSubmitting = false
                            onSuccess(created)
                        },
                        onError = { err ->
                            isSubmitting = false
                            localError = err
                        }
                    )
                },
                enabled = !isSubmitting && subject.isNotBlank() && message.isNotBlank(),
                modifier = Modifier
                    .weight(2f)
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("در حال ثبت...", fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ثبت و ارسال پیام پشتیبانی", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
