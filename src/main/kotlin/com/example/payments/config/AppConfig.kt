package com.example.payments.config

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AppConfig : WebMvcConfigurer {
    /** Injected everywhere time matters so tests can pin it. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** Enables `@Observed` -> metrics + spans. */
    @Bean
    fun observedAspect(registry: ObservationRegistry): ObservedAspect = ObservedAspect(registry)

    /** Serves the contract itself at /openapi/payments-api.yaml (Swagger UI at /docs reads it from there). */
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/openapi/**").addResourceLocations("classpath:/openapi/")
    }
}
