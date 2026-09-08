package com.smartprep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * How long shutdown waits for work already running to finish.
     *
     * <p>The server now shuts down gracefully, which drains HTTP requests but says nothing
     * about these pools. Without the drain configured below, replacing the container during
     * a deploy kills async work mid-flight: a mock test being graded at that moment stays in
     * GRADING forever, and only a FAILED submission can be retried, so the user is left with
     * a result that never arrives and no way to ask for it again.
     *
     * <p>Thirty seconds does not cover a full grading run -- two Gemini calls can take
     * minutes -- and it is not meant to. It is bounded by how long a deployment can
     * reasonably wait, and it rescues the common case where the work is nearly done.
     */
    private static final int SHUTDOWN_DRAIN_SECONDS = 30;

    @Bean(name = "ttsExecutor")
    public Executor ttsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("tts-audio-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_DRAIN_SECONDS);
        executor.initialize();
        return executor;
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        // Ten threads, and the database pool is sized with this in mind: these can each ask
        // for a connection at the same time as ttsExecutor's five, which is why the Hikari
        // pool is 20 rather than the default 10.
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_DRAIN_SECONDS);
        executor.initialize();
        return executor;
    }
}
