package com.example.data

import com.example.data.model.KodyarCommonProblem
import com.example.data.model.KodyarErrorCode
import com.example.data.model.KodyarSparePart

/**
 * هیچ داده پیش‌فرضی در برنامه وجود ندارد.
 * تمامی داده‌ها صرفاً و مستقیماً از پایگاه‌داده مشترک اپلیکیشن و وب‌سایت کدیار دریافت می‌شوند.
 */
object DefaultOfflineData {
    val errorCodes: List<KodyarErrorCode> = emptyList()
    val commonProblems: List<KodyarCommonProblem> = emptyList()
    val spareParts: List<KodyarSparePart> = emptyList()
}
