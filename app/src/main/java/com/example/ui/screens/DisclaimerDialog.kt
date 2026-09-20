package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.AssistantViewModel

@Composable
fun DisclaimerDialog(
    showDisclaimerModal: Boolean,
    isDisclaimerAccepted: Boolean,
    showSplashScreen: Boolean = false,
    disclaimerChecked: Boolean,
    onDisclaimerCheckedChange: (Boolean) -> Unit,
    onAccept: () -> Unit
) {
    if (showDisclaimerModal || !isDisclaimerAccepted) {
        Dialog(
            onDismissRequest = { /* Mandatory acceptance required */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(CodyarNavy.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = CodyarNavy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "سلب مسئولیت و شرایط استفاده",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CodyarNavy,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable content area
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Section 1: Intro
                        Text(
                            text = "این برنامه صرفاً جهت آگاهی، آموزش و عیب یابی اولیه تهیه شده است. اطلاعات ارائه شده جایگزین نظر یا خدمات تعمیرکار مجاز نیست.",
                            fontSize = 13.sp,
                            color = Color(0xFF2D3748),
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Section 2: Danger Warning Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF2F2), shape = RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFFCA5A5), shape = RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .padding(top = 2.dp)
                                )
                                Text(
                                    text = "هرگونه تعمیر، باز کردن یا دستکاری دستگاه های برقی، گازی یا الکترونیکی ممکن است باعث برق گرفتگی، آتش سوزی، نشت گاز، آسیب به دستگاه، خسارات مالی یا صدمات جانی شود. پیش از هر اقدامی، نکات ایمنی را رعایت کرده و در صورت نداشتن دانش یا مهارت کافی، از خدمات تعمیرکار مجاز استفاده کنید.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF991B1B),
                                    lineHeight = 19.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 3: Conditions List Intro
                        Text(
                            text = "با فعال کردن گزینه زیر و انتخاب «ادامه»، تأیید می کنید که:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CodyarNavy,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val conditions = listOf(
                            "این شرایط را مطالعه و پذیرفته اید.",
                            "مسئولیت استفاده از اطلاعات برنامه و اقدامات انجام شده بر عهده خود شماست.",
                            "می دانید اطلاعات این برنامه صرفاً جنبه راهنمایی دارد و تضمین کننده رفع مشکل یا ایمنی کامل نیست.",
                            "موافقت می کنید ثبت این تأییدیه (همراه با تاریخ، ساعت، نسخه برنامه و شناسه کاربری یا شناسه دستگاه) در سامانه انجام شود."
                        )

                        conditions.forEach { condition ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = condition,
                                    fontSize = 12.sp,
                                    color = Color(0xFF4A5568),
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "• ",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CodyarNavy,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Checkbox Acceptance Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDisclaimerCheckedChange(!disclaimerChecked) }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = "شرایط فوق را مطالعه کرده و میپذیرم.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (disclaimerChecked) CodyarNavy else Color(0xFF4A5568),
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Checkbox(
                            checked = disclaimerChecked,
                            onCheckedChange = onDisclaimerCheckedChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = CodyarNavy,
                                uncheckedColor = Color(0xFFCBD5E1)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Continue Button
                    Button(
                        onClick = {
                            if (disclaimerChecked) {
                                onAccept()
                            }
                        },
                        enabled = disclaimerChecked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CodyarNavy,
                            disabledContainerColor = Color(0xFFE2E8F0),
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Text(
                            text = "ادامه",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (disclaimerChecked) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}
