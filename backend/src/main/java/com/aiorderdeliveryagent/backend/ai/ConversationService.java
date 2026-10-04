package com.aiorderdeliveryagent.backend.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.aiorderdeliveryagent.backend.auth.*;
import com.aiorderdeliveryagent.backend.observability.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Successful turns are atomic. No message/tool content is sent to audit or diagnostic logs. */
@Service
public class ConversationService {
	public record Conversation(long id, Instant createdAt) { }
	public record ToolActivity(String tool, boolean externalDataRetrieved, String errorCode) { }
	public record Turn(long userMessageId, long assistantMessageId, String text,
			AiModelContract.Trust trust, boolean externalDataRetrieved, List<ToolActivity> tools,
			ResponseGroundingBoundary.SupportStatus supportStatus,List<ControlledFact> facts) {
		public Turn { tools=List.copyOf(tools);facts=List.copyOf(facts); }
		@Override public String toString() { return "ConversationTurn[content omitted]"; }
	}
	private final AuthenticatedUserContextProvider contexts;
	private final TenantDataAuthorizationService authorization;
	private final ConversationContextService history;
	private final AgentOrchestrationService agent;
	private final JdbcTemplate jdbc;
	private final AuditEventService audit;
	private final ResponseGroundingBoundary grounding;
	public ConversationService(AuthenticatedUserContextProvider contexts,TenantDataAuthorizationService authorization,
			ConversationContextService history,AgentOrchestrationService agent,JdbcTemplate jdbc,AuditEventService audit,ResponseGroundingBoundary grounding) {
		this.contexts=contexts;this.authorization=authorization;this.history=history;this.agent=agent;this.jdbc=jdbc;this.audit=audit;this.grounding=grounding;
	}
	@Transactional
	public Conversation create() {
		var owner=contexts.getCurrentUser();
		return jdbc.queryForObject("""
			INSERT INTO conversations(tenant_id,user_id) VALUES (?,?) RETURNING id,created_at
			""",(row,index)->new Conversation(row.getLong(1),row.getTimestamp(2).toInstant()),owner.tenantId(),owner.userId());
	}
	public AiModelContract.History history(long conversationId) { return grounding.publicHistory(history.getHistory(conversationId)); }
	@Transactional
	public Turn send(long conversationId,long integrationId,String message) {
		if(conversationId<=0||integrationId<=0||message==null||message.isBlank()||message.length()>4000)
			throw new IllegalArgumentException("Invalid conversation request");
		var owner=contexts.getCurrentUser();authorization.requireConversationAccess(conversationId);
		// Serialize successful turns without holding an application-global lock. Bounds apply to chat SQL.
		jdbc.execute("SET LOCAL lock_timeout = '2s'");jdbc.execute("SET LOCAL statement_timeout = '3s'");
		jdbc.queryForObject("SELECT id FROM conversations WHERE id=? AND tenant_id=? AND user_id=? FOR UPDATE",
			Long.class,conversationId,owner.tenantId(),owner.userId());
		var result=agent.execute(conversationId,integrationId,message);
		var grounded=grounding.enforce(result);
		// The current message is provided separately to the model, then persisted once with the answer.
		long userId=insertMessage(conversationId,"user",message,owner);
		long assistantId=insertMessage(conversationId,"assistant",grounded.persistedContent(),owner);
		var activity=grounded.provenance().stream().map(e->new ToolActivity(e.tool().name(),e.retrieved(),e.errorCode().orElse(null))).toList();
		int toolSequence=0;
		for(var evidence:grounded.provenance()) {
			var metadata=new java.util.LinkedHashMap<String,Object>();
			metadata.put("toolSequence",toolSequence++);
			metadata.put("tool",evidence.tool().name());metadata.put("externalDataRetrieved",evidence.retrieved());
			metadata.put("errorCode",evidence.errorCode().orElse("NONE"));metadata.put("integrationId",evidence.integrationId());
			metadata.put("assistantMessageId",assistantId);metadata.put("completedAt",evidence.completedAt().toString());
			if(evidence.retrieved()) metadata.put("retrievedAt",evidence.completedAt().toString());
			evidence.accessedOrderId().ifPresent(id->metadata.put("accessedOrderId",id));
			metadata.put("sourceTimestamps",evidence.sourceTimestamps().stream().map(Instant::toString).toList());
			audit.record(AuditEventType.AI_TOOL_EXECUTION,owner.tenantId(),owner.userId(),"conversation",Long.toString(conversationId),
				evidence.retrieved()?AuditOutcome.SUCCESS:AuditOutcome.FAILURE,metadata);
		}
		audit.record(AuditEventType.AI_RESPONSE_GROUNDING,owner.tenantId(),owner.userId(),"conversation",Long.toString(conversationId),AuditOutcome.SUCCESS,
			Map.of("assistantMessageId",assistantId,"supportStatus",grounded.supportStatus().name(),"externalDataRetrieved",grounded.externalDataRetrieved(),"factCount",grounded.facts().size(),"modelContentWithheld",true));
		jdbc.update("UPDATE conversations SET updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND user_id=?",
			conversationId,owner.tenantId(),owner.userId());
		return new Turn(userId,assistantId,grounded.presentationText(),AiModelContract.Trust.MODEL_GENERATED_UNVERIFIED,
			grounded.externalDataRetrieved(),activity,grounded.supportStatus(),grounded.facts());
	}
	private long insertMessage(long conversationId,String role,String content,AuthenticatedUserContext owner) {
		return jdbc.queryForObject("""
			INSERT INTO messages(tenant_id,user_id,conversation_id,message_role,content)
			VALUES (?,?,?,?,?) RETURNING id
			""",Long.class,owner.tenantId(),owner.userId(),conversationId,role,content);
	}
}
