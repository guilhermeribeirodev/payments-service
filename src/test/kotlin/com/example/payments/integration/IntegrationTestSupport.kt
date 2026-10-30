package com.example.payments.integration

import com.example.payments.application.port.EventMessage
import com.example.payments.application.port.EventPublisher
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.CopyOnWriteArrayList

/** Kotlin-friendly subclass: avoids the self-referential generic of PostgreSQLContainer. */
class PostgresContainer(image: String) : PostgreSQLContainer<PostgresContainer>(image)

class RecordingEventPublisher : EventPublisher {
    val messages = CopyOnWriteArrayList<EventMessage>()

    override fun publish(message: EventMessage) {
        messages += message
    }
}

@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfig {
    /** Started once per JVM and wired into spring.datasource.* via @ServiceConnection. */
    @Bean
    @ServiceConnection
    fun postgres(): PostgresContainer = PostgresContainer("postgres:16-alpine")

    @Bean
    @Primary
    fun recordingPublisher(): RecordingEventPublisher = RecordingEventPublisher()
}
