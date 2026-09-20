package com.example.data.repository

import com.example.data.model.ErrorCode

object ErrorCodeRepository {
    val brands: List<String> = emptyList()
    val categories: List<String> = emptyList()
    val staticErrorCodes: List<ErrorCode> = emptyList()

    fun searchErrorCodes(query: String, brand: String, category: String): List<ErrorCode> {
        return emptyList()
    }
}
