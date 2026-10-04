package com.aiorderdeliveryagent.backend.integration;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
class ExternalIntegrationController {

	private final ExternalIntegrationService integrationService;

	ExternalIntegrationController(ExternalIntegrationService integrationService) {
		this.integrationService = integrationService;
	}

	@PostMapping
	ResponseEntity<IntegrationResponse> create(@Valid @RequestBody CreateIntegrationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(integrationService.create(request));
	}

	@GetMapping
	List<IntegrationResponse> list() {
		return integrationService.list();
	}

	@GetMapping("/{integrationId}")
	IntegrationResponse get(@PathVariable long integrationId) {
		return integrationService.get(integrationId);
	}

	@PatchMapping("/{integrationId}")
	IntegrationResponse update(
			@PathVariable long integrationId,
			@Valid @RequestBody UpdateIntegrationRequest request) {
		return integrationService.update(integrationId, request);
	}

	@DeleteMapping("/{integrationId}")
	ResponseEntity<Void> delete(@PathVariable long integrationId) {
		integrationService.delete(integrationId);
		return ResponseEntity.noContent().build();
	}
}
