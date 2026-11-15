package com.example.service

import com.example.domain.model.PaymentEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SubscribedSharedFlow<out T> : SharedFlow<T> {
    val subscriptionCount: StateFlow<Int>
}

class PaymentEventBus {
    private val _events = MutableSharedFlow<PaymentEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SubscribedSharedFlow<PaymentEvent> = object : SubscribedSharedFlow<PaymentEvent>, SharedFlow<PaymentEvent> by _events {
        override val subscriptionCount: StateFlow<Int> get() = _events.subscriptionCount
    }
    
    suspend fun emit(event: PaymentEvent) = _events.emit(event)
}
