package com.nabukey.stt

data class STTResult(
    val text: String,
    val emotion: String = "neutral",
    val language: String = ""
)
