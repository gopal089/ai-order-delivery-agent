package com.aiorderdeliveryagent.backend.integration.order.tool;

import java.util.Objects;

/** External text remains data. Future AI integration must not promote it to instructions/tool arguments. */
public record UntrustedProviderData<T>(T data, Trust trust) {
	public enum Trust { EXTERNAL_UNTRUSTED }
	public UntrustedProviderData(T data) { this(data, Trust.EXTERNAL_UNTRUSTED); }
	public UntrustedProviderData { Objects.requireNonNull(data); Objects.requireNonNull(trust); }
	@Override public String toString() { return "UntrustedProviderData[EXTERNAL_UNTRUSTED, content omitted]"; }
}
