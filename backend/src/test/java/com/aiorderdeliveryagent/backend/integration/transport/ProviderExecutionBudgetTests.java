package com.aiorderdeliveryagent.backend.integration.transport;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.aiorderdeliveryagent.backend.integration.ProviderExecutionException;
import com.aiorderdeliveryagent.backend.integration.credential.CredentialMaterial;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ProviderExecutionBudgetTests {
	private static final Duration SHORT = Duration.ofMillis(200);
	private static void awaitIgnoringInterrupt(CountDownLatch latch) {
		boolean interrupted = false;
		while (true) { try { latch.await(); break; } catch (InterruptedException e) { interrupted = true; } }
		if (interrupted) Thread.currentThread().interrupt();
	}
	@Test void deadlineReturnsAndClosesMaterialEvenWhenAdapterIgnoresInterrupt() throws Exception {
		var release = new CountDownLatch(1); var done = new CountDownLatch(1);
		var material = CredentialMaterial.copyOf(new char[] {'x'});
		try {
			long start = System.nanoTime();
			assertThatThrownBy(() -> ProviderExecutionBudget.execute(SHORT, 5, budget -> {
				budget.track(material); awaitIgnoringInterrupt(release); done.countDown(); return "ignored";
			})).isInstanceOfSatisfying(ProviderExecutionException.class, e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.TIMEOUT));
			assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isLessThan(1500);
			assertThatThrownBy(material::copyForTrustedBackend).isInstanceOf(IllegalStateException.class);
		}
		finally { release.countDown(); assertThat(done.await(2, TimeUnit.SECONDS)).isTrue(); }
	}
	@Test void lateCredentialRetrievalIsZeroizedBeforeAdapterCanUseIt() throws Exception {
		var release = new CountDownLatch(1); var done = new CountDownLatch(1);
		var material = new AtomicReference<CredentialMaterial>();
		try {
			assertThatThrownBy(() -> ProviderExecutionBudget.execute(SHORT, 5, budget -> {
				awaitIgnoringInterrupt(release);
				var late = CredentialMaterial.copyOf(new char[] {'x'}); material.set(late);
				try { budget.track(late); return "must not execute"; } finally { done.countDown(); }
			})).isInstanceOf(ProviderExecutionException.class);
		}
		finally { release.countDown(); assertThat(done.await(2, TimeUnit.SECONDS)).isTrue(); }
		assertThatThrownBy(material.get()::copyForTrustedBackend).isInstanceOf(IllegalStateException.class);
	}
	@Test void repeatedHttpAttemptsCannotExceedFiveEvenForPolicyFailures() {
		var transport = new SecureHttpTransport(new MockEnvironment());
		assertThatThrownBy(() -> ProviderExecutionBudget.execute(budget -> {
			for (int i = 0; i < 6; i++) {
				try { transport.execute(SecureHttpTransport.Method.GET, URI.create("https://127.0.0.1/"), Map.of()); }
				catch (TransportFailureException expected) { }
			}
			return "must not complete";
		})).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.EXECUTION_LIMIT_EXCEEDED));
	}
	@Test void remainingDeadlineCapsHttpTimeout() {
		Duration timeout = ProviderExecutionBudget.execute(SHORT, 5, budget -> budget.consumeHttpCall(Duration.ofSeconds(15)));
		assertThat(timeout).isPositive().isLessThanOrEqualTo(SHORT);
	}
	@Test void trustedAuthenticationPropagatesButDoesNotLeakToNextExecution() {
		var authentication = new UsernamePasswordAuthenticationToken("test-only", null);
		try {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			var propagated = ProviderExecutionBudget.execute(b -> SecurityContextHolder.getContext().getAuthentication());
			assertThat(propagated).isSameAs(authentication);
		}
		finally { SecurityContextHolder.clearContext(); }
		var cleared = ProviderExecutionBudget.execute(b -> SecurityContextHolder.getContext().getAuthentication());
		assertThat(cleared).isNull();
	}
	@Test void unexpectedWorkerErrorsHaveFixedMessageAndNoSensitiveCause() {
		assertThatThrownBy(() -> ProviderExecutionBudget.execute(b -> { throw new IllegalStateException("synthetic-sensitive-marker"); }))
			.isInstanceOf(ProviderExecutionException.class).hasMessageNotContaining("synthetic").hasCause(null);
	}
	@Test void poolSaturationFailsClosedWithoutCreatingAnUnboundedQueue() throws Exception {
		var entered = new CountDownLatch(4); var release = new CountDownLatch(1);
		var callers = Executors.newFixedThreadPool(4);
		try {
			for (int i = 0; i < 4; i++) callers.submit(() -> ProviderExecutionBudget.execute(b -> {
				entered.countDown(); awaitIgnoringInterrupt(release); return "fixture";
			}));
			assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> ProviderExecutionBudget.execute(b -> "must not execute"))
				.isInstanceOfSatisfying(ProviderExecutionException.class,
					e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.EXECUTION_BUSY));
		}
		finally { release.countDown(); callers.shutdown(); assertThat(callers.awaitTermination(2, TimeUnit.SECONDS)).isTrue(); }
	}
}
