package br.com.gate8.pos.core.network

class ApiException(
    val httpCode: Int,
    message: String,
    val errorCode: String? = null,
    val available: Int? = null,
) : Exception(message)
