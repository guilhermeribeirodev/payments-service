package com.example.payments.adapter.messaging

import com.example.payments.application.port.EventMessage
import com.example.payments.application.port.EventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Placeholder broker adapter: logs the event. Replace with Kafka / SNS / Service Bus by providing
 * another [EventPublisher] implementation (see docs/adr/0003-transactional-outbox.md).
 */
@Component
class LoggingEventPublisher : EventPublisher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(message: EventMessage) {
        log.info("Publishing event id={} type={} aggregateId={}", message.id, message.type, message.aggregateId)
    }
}
