package com.nabukey.esphome.entities

import com.example.esphomeproto.api.ButtonCommandRequest
import com.example.esphomeproto.api.ListEntitiesRequest
import com.example.esphomeproto.api.listEntitiesButtonResponse
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

class ButtonEntity(
    val key: Int,
    val name: String,
    val objectId: String,
    val onPress: suspend () -> Unit
) : Entity {
    override fun handleMessage(message: MessageLite) = flow {
        when (message) {
            is ListEntitiesRequest -> emit(listEntitiesButtonResponse {
                key = this@ButtonEntity.key
                name = this@ButtonEntity.name
                objectId = this@ButtonEntity.objectId
            })

            is ButtonCommandRequest -> {
                if (message.key == key)
                    onPress()
            }
        }
    }

    // Buttons are stateless, so no state subscription needed
    override fun subscribe() = emptyFlow<MessageLite>()
}
