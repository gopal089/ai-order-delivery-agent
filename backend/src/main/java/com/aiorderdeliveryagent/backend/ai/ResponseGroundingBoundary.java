package com.aiorderdeliveryagent.backend.ai;

import java.util.List;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Controlled provider-field rendering, not a prose parser or independent verification of provider truth. */
@Service
public final class ResponseGroundingBoundary {
	/** EXTERNALLY_SUPPORTED applies only to rendered fields with backend execution evidence. */
	public enum SupportStatus { MODEL_GENERATED_UNVERIFIED, EXTERNALLY_SUPPORTED, PARTIALLY_SUPPORTED }
	public static final String NO_RETRIEVAL="No current external information is available for this response. Unverified model-generated claims are not displayed.";
	public static final String RETRIEVED="An external retrieval completed, but the generated answer has not been verified and is not displayed.";
	public static final String HISTORY_WITHHELD="Prior assistant content is unverified and is not displayed.";
	private final ObjectMapper json;
	@Autowired public ResponseGroundingBoundary(ObjectMapper json) {this.json=json;}
	ResponseGroundingBoundary() {this(new ObjectMapper());}
	public record PublicEvidence(int toolSequence,String tool,boolean retrieved,java.time.Instant completedAt,String errorCode) { }
	public record StoredResponse(int schemaVersion,String renderedText,SupportStatus supportStatus,
		boolean modelContentWithheld,List<ControlledFact> facts,List<PublicEvidence> retrievalEvidence) { }
	public record GroundedResponse(String generatedText,String presentationText,SupportStatus supportStatus,
			boolean externalDataRetrieved,List<RetrievalEvidence> provenance,List<ControlledFact> facts,String persistedContent) {
		public GroundedResponse { provenance=List.copyOf(provenance);facts=List.copyOf(facts); }
		@Override public String toString() {return "GroundedResponse[generated content omitted]";}
	}
	public GroundedResponse enforce(AiModelContract.Result result) {
		boolean retrieved=result.provenance().stream().anyMatch(RetrievalEvidence::retrieved);
		// Verify association with actual successful backend execution, not model ToolResult.success flags.
		var facts=result.facts().stream().filter(f->f.toolSequence()<result.provenance().size()
			&&result.provenance().get(f.toolSequence()).retrieved()
			&&result.provenance().get(f.toolSequence()).tool()==f.tool()).toList();
		var support=facts.stream().anyMatch(f->f.value().isPresent())?SupportStatus.EXTERNALLY_SUPPORTED:SupportStatus.MODEL_GENERATED_UNVERIFIED;
		var text=new StringBuilder();
		if(facts.isEmpty()) text.append(retrieved?RETRIEVED:NO_RETRIEVAL);
		else {
			text.append("Provider-reported fields (not independent verification or a freshness guarantee):\n");
			for(var fact:facts) text.append(fact.field().name()).append(": ")
				.append(fact.value().map(json::writeValueAsString).orElse("unavailable"))
				.append("; source timestamp: ").append(fact.sourceTimestamp().map(Object::toString).orElse("unavailable")).append('\n');
			text.append("Model-generated claims are withheld. No delivery estimate or freshness is inferred.");
		}
		for(var evidence:result.provenance()) if(!evidence.retrieved()&&evidence.tool()==AiModelContract.Tool.getCurrentPackageLocation)
			text.append("\nPACKAGE_LOCATION: unavailable (external retrieval did not succeed).");
		var references=new java.util.ArrayList<PublicEvidence>();
		for(int i=0;i<result.provenance().size();i++) {var e=result.provenance().get(i);references.add(new PublicEvidence(i,e.tool().name(),e.retrieved(),e.completedAt(),e.errorCode().orElse(null)));}
		String stored=json.writeValueAsString(new StoredResponse(1,text.toString(),support,true,facts,List.copyOf(references)));
		if(stored.length()>8192) throw new AiBoundaryException(AiBoundaryException.Reason.CONTEXT_TOO_LARGE);
		return new GroundedResponse(result.text(),text.toString(),support,retrieved,result.provenance(),facts,stored);
	}
	public AiModelContract.History publicHistory(AiModelContract.History history) {
		return new AiModelContract.History(history.messages().stream().map(message->
			message.role()==AiModelContract.ContextRole.USER?message:new AiModelContract.HistoryText(message.role(),
				HISTORY_WITHHELD,message.createdAt(),AiModelContract.Trust.UNTRUSTED_HISTORY)).toList(),history.truncated());
	}
}
