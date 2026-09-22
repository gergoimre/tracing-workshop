package com.workshop.transfer

import io.opentelemetry.context.Context
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
@ConditionalOnProperty(name = ["APP_ROLE"], havingValue = "transfer")
class AsyncConfig {

    @Bean("backgroundJobExecutor")
    fun backgroundJobExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 8
        executor.maxPoolSize = 16
        executor.queueCapacity = 64
        executor.setThreadNamePrefix("bg-job-")
        executor.setTaskDecorator(OtelMdcTaskDecorator())
        executor.initialize()
        return executor
    }
}

private class OtelMdcTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val otelContext = Context.current()
        val mdcCopy = MDC.getCopyOfContextMap() ?: emptyMap()
        return Runnable {
            val previousMdc = MDC.getCopyOfContextMap()
            try {
                mdcCopy.forEach { (k, v) -> MDC.put(k, v) }
                otelContext.makeCurrent().use { runnable.run() }
            } finally {
                MDC.clear()
                previousMdc?.forEach { (k, v) -> MDC.put(k, v) }
            }
        }
    }
}
