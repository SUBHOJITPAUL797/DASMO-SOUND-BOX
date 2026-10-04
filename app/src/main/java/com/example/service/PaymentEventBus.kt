package com.example.service

import com.example.domain.model.PaymentEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PaymentEventBus {
    private val _events = MutableSharedFlow<PaymentEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<PaymentEvent> = _events.asSharedFlow()
    
    suspend fun emit(event: PaymentEvent) = _events.emit(event)
}
