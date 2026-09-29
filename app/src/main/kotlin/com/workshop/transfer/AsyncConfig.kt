package com.workshop.transfer

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.support.ContextPropagatingTaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "transfer")
class AsyncConfig {

    /**
     * Background job executor with Micrometer context propagation.
     *
     * ContextPropagatingTaskDecorator (Spring Framework 6.1+, available on the
     * classpath via spring-core) uses io.micrometer:context-propagation under the
     * hood to carry the full Micrometer Observation context — including the active
     * trace/span and MDC keys (traceId, spanId) — across the thread-pool boundary.
     *
     * No manual OTel context capture or MDC copying is needed.
     */
    @Bean("backgroundJobExecutor")
    fun backgroundJobExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 8
        executor.maxPoolSize = 16
        executor.queueCapacity = 64
        executor.setThreadNamePrefix("bg-job-")
        executor.setTaskDecorator(ContextPropagatingTaskDecorator())
        executor.initialize()
        return executor
    }
}
