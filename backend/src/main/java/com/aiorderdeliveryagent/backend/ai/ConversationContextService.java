package com.aiorderdeliveryagent.backend.ai;

import java.util.Collections;
import com.aiorderdeliveryagent.backend.auth.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static com.aiorderdeliveryagent.backend.ai.AiModelContract.*;

/** Read-only bounded recent history. No new conversation semantics, persistence, memory, RAG or embeddings. */
@Service
public class ConversationContextService {
	private final AuthenticatedUserContextProvider contexts;
	private final TenantDataAuthorizationService authorization;
	private final JdbcTemplate jdbc;
	public ConversationContextService(AuthenticatedUserContextProvider contexts, TenantDataAuthorizationService authorization, JdbcTemplate jdbc) {
		this.contexts=contexts; this.authorization=authorization; this.jdbc=jdbc;
	}
	public History getHistory(long conversationId) {
		var user=contexts.getCurrentUser(); authorization.requireConversationAccess(conversationId);
		var rows=jdbc.query("""
			SELECT message_role,content,created_at FROM public.messages
			WHERE conversation_id=? AND tenant_id=? AND user_id=? ORDER BY id DESC LIMIT 21
			""", (row,index)->new HistoryText(switch(row.getString(1)) {
				case "user" -> ContextRole.USER; case "assistant" -> ContextRole.ASSISTANT; default -> ContextRole.OTHER;
			},row.getString(2),row.getTimestamp(3).toInstant(),Trust.UNTRUSTED_HISTORY),conversationId,user.tenantId(),user.userId());
		boolean truncated=rows.size()>20; if(truncated) rows=rows.subList(0,20);
		Collections.reverse(rows); return new History(rows,truncated);
	}
}
