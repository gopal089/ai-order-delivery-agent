package com.aiorderdeliveryagent.backend.integration;

import java.net.URI;
import com.aiorderdeliveryagent.backend.integration.credential.CredentialMaterial;
import com.aiorderdeliveryagent.backend.integration.order.ExternalOrderProvider;
import com.aiorderdeliveryagent.backend.integration.transport.SecureHttpTransport;

/**
 * Trusted backend adapter construction, separate from public domain requests and AI tools.
 * No real implementation exists. A future documented adapter must use the supplied transport,
 * validate external responses, and avoid retaining credentials beyond the execution.
 */
public interface BackendOrderProviderAdapter {
	String providerKey();
	ExternalOrderProvider open(URI baseUri, CredentialMaterial credentials, SecureHttpTransport transport);
}
