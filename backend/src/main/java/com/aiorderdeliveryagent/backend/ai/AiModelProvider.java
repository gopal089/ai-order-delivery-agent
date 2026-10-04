package com.aiorderdeliveryagent.backend.ai;

/** Trusted backend adapter contract. No production adapter/SDK/model is registered. */
public interface AiModelProvider {
	AiModelContract.Response generate(AiModelContract.Request request);
}
