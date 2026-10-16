package com.akamai.miniwsa.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class StatsQueryExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor statsQueryExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(6);
        executor.setThreadNamePrefix("stats-query-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
