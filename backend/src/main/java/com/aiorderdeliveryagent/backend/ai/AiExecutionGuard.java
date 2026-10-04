package com.aiorderdeliveryagent.backend.ai;

import java.time.Duration;
import java.util.concurrent.*;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;

/** Separate bounded model/tool pools. Model workers never inherit authenticated context. */
final class AiExecutionGuard {
	private static ExecutorService pool(String name) {
		return new ThreadPoolExecutor(0,4,30,TimeUnit.SECONDS,new SynchronousQueue<>(),task->{
			Thread thread=new Thread(task,name);thread.setDaemon(true);return thread;
		},new ThreadPoolExecutor.AbortPolicy());
	}
	private static final ExecutorService MODELS=pool("ai-model"), TOOLS=pool("ai-tool");
	static <T> T call(Callable<T> task, Duration timeout, boolean tool) {
		var authentication=tool?SecurityContextHolder.getContext().getAuthentication():null;
		Future<T> pending=null;
		try {
			pending=(tool?TOOLS:MODELS).submit(()->{
				SecurityContextHolder.clearContext(); MDC.clear();
				if(tool) { var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);SecurityContextHolder.setContext(context); }
				try { return task.call(); } finally { SecurityContextHolder.clearContext();MDC.clear(); }
			});
			return pending.get(Math.max(1,timeout.toNanos()),TimeUnit.NANOSECONDS);
		}
		catch(TimeoutException exception) { throw new AiBoundaryException(tool?AiBoundaryException.Reason.EXECUTION_TIMEOUT:AiBoundaryException.Reason.MODEL_TIMEOUT); }
		catch(InterruptedException exception) { Thread.currentThread().interrupt();throw new AiBoundaryException(AiBoundaryException.Reason.EXECUTION_TIMEOUT); }
		catch(RejectedExecutionException exception) { throw new AiBoundaryException(AiBoundaryException.Reason.EXECUTION_BUSY); }
		catch(ExecutionException exception) {
			if(tool && exception.getCause() instanceof org.springframework.security.access.AccessDeniedException) throw new org.springframework.security.access.AccessDeniedException("Access is denied");
			if(tool && exception.getCause() instanceof com.aiorderdeliveryagent.backend.integration.ProviderExecutionException safe) throw safe;
			if(exception.getCause() instanceof AiBoundaryException safe) throw safe;
			throw new AiBoundaryException(tool?AiBoundaryException.Reason.TOOL_FAILURE:AiBoundaryException.Reason.MODEL_FAILURE);
		}
		finally { if(pending!=null) pending.cancel(true); }
	}
}
