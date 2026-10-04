package com.aiorderdeliveryagent.backend.ai;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AiExecutionGuardTests {
	@Test void onlyToolWorkersReceiveAuthenticationAndModelWorkersRemainEmpty() {
		var authentication=UsernamePasswordAuthenticationToken.authenticated("test-only",null,java.util.List.of());
		try {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			var actual=AiExecutionGuard.call(()->SecurityContextHolder.getContext().getAuthentication(),Duration.ofSeconds(1),true);
			assertThat(actual).isSameAs(authentication);
			var model=AiExecutionGuard.call(()->SecurityContextHolder.getContext().getAuthentication(),Duration.ofSeconds(1),false);
			assertThat(model).isNull();
		} finally {SecurityContextHolder.clearContext();}
		var next=AiExecutionGuard.call(()->SecurityContextHolder.getContext().getAuthentication(),Duration.ofSeconds(1),true);
		assertThat(next).isNull();
	}
	@Test void interruptionIgnoringModelWorkIsBoundedAndSaturationFailsClosed() throws Exception {
		var entered=new CountDownLatch(4);var release=new CountDownLatch(1);var exited=new CountDownLatch(4);
		var callers=Executors.newFixedThreadPool(4);
		try {
			for(int i=0;i<4;i++) callers.submit(()->AiExecutionGuard.call(()->{
				entered.countDown();boolean interrupted=false;
				while(true) {try{release.await();break;}catch(InterruptedException e){interrupted=true;}}
				if(interrupted)Thread.currentThread().interrupt();exited.countDown();return "test-only";
			},Duration.ofMillis(200),false));
			assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(()->AiExecutionGuard.call(()->"must not execute",Duration.ofMillis(100),false))
				.isInstanceOfSatisfying(AiBoundaryException.class,e->assertThat(e.reason()).isEqualTo(AiBoundaryException.Reason.EXECUTION_BUSY));
		} finally {release.countDown();assertThat(exited.await(2,TimeUnit.SECONDS)).isTrue();callers.shutdown();assertThat(callers.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
	}
}
