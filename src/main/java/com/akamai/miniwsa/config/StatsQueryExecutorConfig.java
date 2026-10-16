package com.akamai.miniwsa.config;

import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
public class StatsQueryExecutorConfig {

    private final StatsProperties statsProperties;

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor statsQueryExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        var threads = statsProperties.queryThreads();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(statsProperties.queryQueueCapacity());
        executor.setThreadNamePrefix("stats-query-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
