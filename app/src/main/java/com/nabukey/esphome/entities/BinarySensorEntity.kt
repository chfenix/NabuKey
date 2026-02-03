package com.nabukey.esphome.entities

import com.example.esphomeproto.api.ListEntitiesRequest
import com.example.esphomeproto.api.listEntitiesBinarySensorResponse
import com.example.esphomeproto.api.binarySensorStateResponse
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class BinarySensorEntity(
    val key: Int,
    val name: String,
    val objectId: String,
    val getState: Flow<Boolean>
) : Entity {
    override fun handleMessage(message: MessageLite) = flow {
        when (message) {
            is ListEntitiesRequest -> emit(listEntitiesBinarySensorResponse {
                key = this@BinarySensorEntity.key
                name = this@BinarySensorEntity.name
                objectId = this@BinarySensorEntity.objectId
            })
        }
    }

    override fun subscribe() = getState.map {
        binarySensorStateResponse {
            key = this@BinarySensorEntity.key
            this.state = it
        }
    }
}
