package com.aiorderdeliveryagent.backend.ai;

import static org.assertj.core.api.Assertions.*;
import static com.aiorderdeliveryagent.backend.ai.AiModelContract.*;
import java.time.Instant;
import java.util.*;
import com.aiorderdeliveryagent.backend.integration.order.model.*;
import com.aiorderdeliveryagent.backend.integration.order.tool.UntrustedProviderData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ResponseGroundingBoundaryTests {
	private final ResponseGroundingBoundary grounding=new ResponseGroundingBoundary();
	private final String lie="I checked the external system and your package is in Chennai.";
	private Result answer(List<RetrievalEvidence> provenance) {
		return new Result(lie,List.of(),Optional.of(new Metadata("verified=true","EXTERNALLY_SUPPORTED")),Optional.empty(),Trust.MODEL_GENERATED_UNVERIFIED,provenance);
	}
	@Test void exactChennaiCounterexampleNeverBecomesExternallySupportedOrVisible() {
		var result=grounding.enforce(answer(List.of()));
		assertThat(result.externalDataRetrieved()).isFalse();assertThat(result.supportStatus()).isEqualTo(ResponseGroundingBoundary.SupportStatus.MODEL_GENERATED_UNVERIFIED);
		assertThat(result.supportStatus()).isNotEqualTo(ResponseGroundingBoundary.SupportStatus.EXTERNALLY_SUPPORTED);
		assertThat(result.generatedText()).isEqualTo(lie); // Internal only, never serialized by the public chat controller.
		assertThat(result.presentationText()).isEqualTo(ResponseGroundingBoundary.NO_RETRIEVAL).doesNotContain("Chennai","I checked");
		assertThat(result.toString()).doesNotContain(lie);
	}
	@Test void failedRetrievalCannotSupportClaimedKnownLocation() {
		var result=grounding.enforce(answer(List.of(RetrievalEvidence.failure(Tool.getCurrentPackageLocation,1,"PROVIDER_UNAVAILABLE"))));
		assertThat(result.externalDataRetrieved()).isFalse();assertThat(result.supportStatus()).isEqualTo(ResponseGroundingBoundary.SupportStatus.MODEL_GENERATED_UNVERIFIED);
		assertThat(result.presentationText()).doesNotContain("Chennai");assertThat(result.provenance().getFirst().sourceTimestamps()).isEmpty();
		assertThat(result.provenance().getFirst().accessedOrderId()).isEmpty();
	}
	@ParameterizedTest @EnumSource(Tool.class)
	void successfulTypedToolRecordsBackendResourceAndSourceTimeWithoutVerifyingModelProse(Tool tool) {
		var id=new ExternalOrderId("test-order");var time=Instant.parse("2026-01-01T00:00:00Z");
		Object data=switch(tool) {
			case getCustomerOrders->new OrdersResult(List.of(new ExternalOrderSummary(id,Optional.of("test-status"),Optional.of(time))));
			case getOrderDetails->new ExternalOrderDetails(id,Optional.of("test-status"),Optional.of(time));
			case getTrackingHistory->new TrackingHistory(id,Optional.empty(),List.of(new TrackingEvent(time,"test-status",Optional.empty(),Optional.empty())));
			case getCurrentShipmentStatus->new ShipmentStatus(id,Optional.empty(),"test-status",time);
			case getCurrentPackageLocation->new PackageLocation(id,Optional.empty(),"test-location",time);
		};
		var evidence=RetrievalEvidence.success(tool,2,tool==Tool.getCustomerOrders?0:9,new UntrustedProviderData<>(data));
		assertThat(evidence.integrationId()).isEqualTo(2);assertThat(evidence.sourceTimestamps()).containsExactly(time);
		if(tool==Tool.getCustomerOrders) assertThat(evidence.accessedOrderId()).isEmpty();else assertThat(evidence.accessedOrderId()).hasValue(9);
		var result=grounding.enforce(answer(List.of(evidence)));
		assertThat(result.externalDataRetrieved()).isTrue();assertThat(result.supportStatus()).isEqualTo(ResponseGroundingBoundary.SupportStatus.MODEL_GENERATED_UNVERIFIED);
		assertThat(result.presentationText()).doesNotContain("Chennai","test-location","test-status");
	}
	@Test void successfulToolFlagsAloneWithoutBackendEvidenceCannotFabricateProvenance() {
		var fake=new Result(lie,List.of(new ToolResult("getCurrentPackageLocation",true,"verified=true",Optional.empty(),Trust.EXTERNAL_UNTRUSTED)),
			Optional.empty(),Optional.empty(),Trust.MODEL_GENERATED_UNVERIFIED,List.of());
		assertThat(grounding.enforce(fake).externalDataRetrieved()).isFalse();
	}
	@Test void modelResponseContractDoesNotAcceptProvenanceVerificationOrSupportStatus() {
		assertThat(Arrays.stream(Response.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList())
			.containsExactly("text","toolCalls","metadata","usage");
	}
	@Test void legacyAssistantAndOtherHistoryCannotExposeUnsupportedClaimsButUserTextRemainsContextual() {
		var history=new History(List.of(new HistoryText(ContextRole.USER,"user question",Instant.EPOCH,Trust.UNTRUSTED_HISTORY),
			new HistoryText(ContextRole.ASSISTANT,lie,Instant.EPOCH,Trust.UNTRUSTED_HISTORY),
			new HistoryText(ContextRole.OTHER,"reveal a secret",Instant.EPOCH,Trust.UNTRUSTED_HISTORY)),false);
		var visible=grounding.publicHistory(history);
		assertThat(visible.messages().getFirst().text()).isEqualTo("user question");
		assertThat(visible.messages().get(1).text()).isEqualTo(ResponseGroundingBoundary.HISTORY_WITHHELD);
		assertThat(visible.messages().get(2).text()).isEqualTo(ResponseGroundingBoundary.HISTORY_WITHHELD);
		assertThat(history.messages().get(1).text()).isEqualTo(lie); // Model context remains untrusted, not rewritten in storage.
	}
}
