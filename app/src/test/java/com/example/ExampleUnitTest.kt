package com.example

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import com.example.data.api.KodyarRetrofitClient

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSmsOtpHelperExtraction() {
    // 1. Persian digits
    assertEquals("12345", com.example.data.utils.SmsOtpHelper.extractOtp("کد تایید کدیار: ۱۲۳۴۵ اعتبار تا ۲ دقیقه"))
    
    // 2. Forgot password message
    assertEquals("48921", com.example.data.utils.SmsOtpHelper.extractOtp("کد فراموشی رمز عبور: 48921"))
    
    // 3. One-time code
    assertEquals("8301", com.example.data.utils.SmsOtpHelper.extractOtp("رمز یکبار مصرف: 8301"))
    
    // 4. English message
    assertEquals("84210", com.example.data.utils.SmsOtpHelper.extractOtp("Your verification code is 84210"))
    
    // 5. Message with mobile number (should not confuse mobile number with 5-digit OTP)
    assertEquals("57214", com.example.data.utils.SmsOtpHelper.extractOtp("شماره تماس 09123456789، کد ورود شما 57214 است"))

    // 6. 6-digit code
    assertEquals("981245", com.example.data.utils.SmsOtpHelper.extractOtp("کد تایید: 981245"))
  }

  @Test
  fun testFetchDatabase() {
    runBlocking {
        try {
            val response = KodyarRetrofitClient.service.getDatabase()
            println("SUCCESS: Fetched database successfully!")
            println("Cities: ${response.citiesList}")
            println("Technicians count: ${response.technicians?.size}")
            response.technicians?.forEach { tech ->
                println("TECH -> ID: ${tech.id}, Name: ${tech.name}, City: ${tech.city}, isVerified: ${tech.isVerified}")
            }
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
        }
    }
  }
}


