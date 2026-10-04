package com.aiorderdeliveryagent.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null
				? List.of()
				: allowedOrigins.stream()
						.map(String::trim)
						.filter(origin -> !origin.isEmpty())
						.distinct()
						.toList();
	}
}
