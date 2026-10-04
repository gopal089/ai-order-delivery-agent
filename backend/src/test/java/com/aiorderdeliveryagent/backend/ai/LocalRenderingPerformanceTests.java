package com.aiorderdeliveryagent.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.aiorderdeliveryagent.backend.integration.order.model.ExternalOrderId;
import com.aiorderdeliveryagent.backend.integration.order.model.PackageLocation;
import com.aiorderdeliveryagent.backend.integration.order.tool.UntrustedProviderData;

/** Bounded local micro-measurement. No network, model, database or throughput claim. */
class LocalRenderingPerformanceTests {
    @Test void measureControlledLocationRenderingWithVerifiedFixture() {
        var boundary = new ResponseGroundingBoundary();
        var tool = AiModelContract.Tool.getCurrentPackageLocation;
        Object data = new UntrustedProviderData<>(new PackageLocation(
            new ExternalOrderId("local-fixture"), Optional.empty(), "fixture-location", Instant.EPOCH));
        var input = new AiModelContract.Result("Unsupported model claim", List.of(),
            Optional.empty(), Optional.empty(), AiModelContract.Trust.MODEL_GENERATED_UNVERIFIED,
            List.of(RetrievalEvidence.success(tool, 1, 1, data)), ControlledFact.project(0, tool, data));
        int warmup = 25, iterations = 100;
        for (int i = 0; i < warmup; i++) boundary.enforce(input);
        long total = 0, minimum = Long.MAX_VALUE, maximum = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            var result = boundary.enforce(input);
            long elapsed = System.nanoTime() - start;
            total += elapsed; minimum = Math.min(minimum, elapsed); maximum = Math.max(maximum, elapsed);
            // Assertions outside the measured segment; correctness, not a latency threshold.
            assertThat(result.supportStatus()).isEqualTo(ResponseGroundingBoundary.SupportStatus.EXTERNALLY_SUPPORTED);
            assertThat(result.presentationText()).contains("fixture-location").doesNotContain("Unsupported model claim");
        }
        System.out.printf(java.util.Locale.ROOT,
            "LOCAL_PERFORMANCE_RESULT warmup=%d iterations=%d concurrency=1 totalNanos=%d minNanos=%d maxNanos=%d meanNanos=%.2f java=%s os=%s arch=%s%n",
            warmup, iterations, total, minimum, maximum, (double) total / iterations,
            System.getProperty("java.version"), System.getProperty("os.name").replace(' ', '_'), System.getProperty("os.arch"));
    }
}
