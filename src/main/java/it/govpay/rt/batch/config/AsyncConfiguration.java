package it.govpay.rt.batch.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

/**
 * Configuration for async task execution.
 * Supports virtual threads (Java 21+) for efficient I/O-bound operations.
 */
@Configuration
public class AsyncConfiguration {

	@Value("${govpay.async.virtual-threads.enabled:true}")
	private boolean virtualThreadsEnabled;

	@Value("${govpay.async.thread-name-prefix:gde-async-}")
	private String threadNamePrefix;

	/**
	 * Creates a TaskExecutor for async operations like GDE event sending.
	 * Uses virtual threads when enabled (recommended for I/O-bound tasks).
	 */
	@Bean(name = "gdeTaskExecutor")
	public TaskExecutor gdeTaskExecutor() {
		if (virtualThreadsEnabled) {
			Executor virtualThreadExecutor = Executors.newThreadPerTaskExecutor(
				Thread.ofVirtual()
					.name(threadNamePrefix, 0)
					.factory()
			);
			return new TaskExecutorAdapter(virtualThreadExecutor);
		} else {
			// Fallback to platform threads with a cached thread pool
			Executor platformExecutor = Executors.newCachedThreadPool(r -> {
				Thread t = new Thread(r);
				t.setName(threadNamePrefix + t.threadId());
				t.setDaemon(true);
				return t;
			});
			return new TaskExecutorAdapter(platformExecutor);
		}
	}
}
