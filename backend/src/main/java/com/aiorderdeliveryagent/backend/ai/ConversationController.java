package com.aiorderdeliveryagent.backend.ai;

import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations")
class ConversationController {
	private final ConversationService conversations;
	ConversationController(ConversationService conversations) { this.conversations=conversations; }
	@PostMapping
	ResponseEntity<ConversationService.Conversation> create(@RequestBody JsonNode body,HttpServletRequest request) {
		check(request,body,Set.of());return ResponseEntity.status(201).body(conversations.create());
	}
	@GetMapping("/{conversationId}/messages")
	AiModelContract.History history(@PathVariable long conversationId,HttpServletRequest request) {
		if(conversationId<=0||!request.getParameterMap().isEmpty()||request.getContentLengthLong()>0||request.getHeader("Transfer-Encoding")!=null) throw invalid();
		return conversations.history(conversationId);
	}
	@PostMapping("/{conversationId}/messages")
	ConversationService.Turn send(@PathVariable long conversationId,@RequestBody JsonNode body,HttpServletRequest request) {
		check(request,body,Set.of("integrationId","message"));
		var id=body.get("integrationId");var message=body.get("message");
		if(id==null||!id.isIntegralNumber()||!id.canConvertToLong()||id.longValue()<=0
				||message==null||!message.isString()) throw invalid();
		return conversations.send(conversationId,id.longValue(),message.stringValue());
	}
	private void check(HttpServletRequest request,JsonNode body,Set<String> fields) {
		if(!request.getParameterMap().isEmpty()||body==null||!body.isObject()||body.size()!=fields.size()) throw invalid();
		for(var property:body.properties()) if(!fields.contains(property.getKey())) throw invalid();
	}
	private IllegalArgumentException invalid() {return new IllegalArgumentException("Invalid conversation request");}
}
