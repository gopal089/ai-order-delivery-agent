package com.aiorderdeliveryagent.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
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
	private final ClientAddressResolver clientAddressResolver;

	UserAuthenticationController(
			UserAuthenticationService authenticationService,
			ClientAddressResolver clientAddressResolver) {
		this.authenticationService = authenticationService;
		this.clientAddressResolver = clientAddressResolver;
	}

	@PostMapping("/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return authenticationService.login(request, clientAddressResolver.resolve(servletRequest));
	}

	@PostMapping("/refresh")
	TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
		return authenticationService.refresh(
				request.refreshToken(),
				clientAddressResolver.resolve(servletRequest));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		authenticationService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}
}
