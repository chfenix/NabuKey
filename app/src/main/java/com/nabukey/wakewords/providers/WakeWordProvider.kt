package com.nabukey.wakewords.providers

import com.nabukey.wakewords.models.WakeWordWithId

interface WakeWordProvider {
    suspend fun get(): List<WakeWordWithId>
}