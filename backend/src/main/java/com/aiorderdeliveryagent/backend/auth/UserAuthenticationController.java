package com.aiorderdeliveryagent.backend.auth;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class UserAuthenticationController {

	private final UserAuthenticationService authenticationService;

	UserAuthenticationController(UserAuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	@PostMapping("/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authenticationService.login(request);
	}

	@PostMapping("/refresh")
	TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authenticationService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		authenticationService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}
}
