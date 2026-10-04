package com.aiorderdeliveryagent.backend.integration.transport;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;

import com.aiorderdeliveryagent.backend.integration.ProviderExecutionException;
import org.springframework.security.core.context.SecurityContextHolder;

/** Backend-only deadline, resource cleanup and HTTP-call budget; never a tool argument. */
public final class ProviderExecutionBudget implements AutoCloseable {
	private static final ThreadLocal<ProviderExecutionBudget> CURRENT = new ThreadLocal<>();
	private static final ExecutorService WORKERS = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
		new SynchronousQueue<>(), task -> {
			Thread thread = new Thread(task, "external-provider");
			thread.setDaemon(true);
			return thread;
		}, new ThreadPoolExecutor.AbortPolicy());
	private final long deadline;
	private final Set<AutoCloseable> resources = new HashSet<>();
	private int callsRemaining;
	private boolean closed;

	private ProviderExecutionBudget(Duration timeout, int maxCalls) {
		deadline = System.nanoTime() + timeout.toNanos();
		callsRemaining = maxCalls;
	}

	public static <T> T execute(Function<ProviderExecutionBudget, T> operation) {
		return execute(Duration.ofSeconds(20), 5, operation);
	}

	// Shorter deadlines only in same-package tests; no runtime configuration/request override.
	static <T> T execute(Duration timeout, int maxCalls, Function<ProviderExecutionBudget, T> operation) {
		var budget = new ProviderExecutionBudget(timeout, maxCalls);
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		Future<T> pending = null;
		try {
			pending = WORKERS.submit(() -> {
				var context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
				CURRENT.set(budget);
				try { budget.check(); return operation.apply(budget); }
				finally { budget.close(); CURRENT.remove(); SecurityContextHolder.clearContext(); }
			});
			return pending.get(Math.max(1, budget.deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
		}
		catch (TimeoutException exception) { throw failure(ProviderExecutionException.Reason.TIMEOUT); }
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw failure(ProviderExecutionException.Reason.TIMEOUT);
		}
		catch (RejectedExecutionException exception) { throw failure(ProviderExecutionException.Reason.EXECUTION_BUSY); }
		catch (ExecutionException exception) {
			if (exception.getCause() instanceof ProviderExecutionException safe) throw safe;
			throw failure(ProviderExecutionException.Reason.EXECUTION_FAILED);
		}
		finally { budget.close(); if (pending != null) pending.cancel(true); }
	}

	static ProviderExecutionBudget current() { return CURRENT.get(); }

	public synchronized void check() {
		if (closed || System.nanoTime() >= deadline || Thread.currentThread().isInterrupted())
			throw failure(ProviderExecutionException.Reason.TIMEOUT);
	}

	synchronized Duration consumeHttpCall(Duration maximum) {
		check();
		if (callsRemaining-- <= 0) throw failure(ProviderExecutionException.Reason.EXECUTION_LIMIT_EXCEEDED);
		return Duration.ofNanos(Math.min(maximum.toNanos(), Math.max(1, deadline - System.nanoTime())));
	}

	/** Late retrieval after cancellation is closed immediately, before any adapter can receive it. */
	public synchronized AutoCloseable track(AutoCloseable resource) {
		try { check(); }
		catch (RuntimeException failure) { closeQuietly(resource); throw failure; }
		resources.add(resource);
		return () -> { synchronized (this) { resources.remove(resource); } };
	}

	@Override public synchronized void close() {
		closed = true;
		for (var resource : resources) closeQuietly(resource);
		resources.clear();
	}
	private static void closeQuietly(AutoCloseable resource) {
		try { resource.close(); } catch (Exception ignored) { /* Never log resource/secret-bearing exceptions. */ }
	}
	private static ProviderExecutionException failure(ProviderExecutionException.Reason reason) {
		return new ProviderExecutionException(reason);
	}
}
