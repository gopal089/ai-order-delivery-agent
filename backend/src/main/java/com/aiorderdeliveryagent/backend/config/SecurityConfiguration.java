package com.aiorderdeliveryagent.backend.config;

import java.time.Clock;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.aiorderdeliveryagent.backend.auth.AccessTokenAuthenticationConverter;
import com.aiorderdeliveryagent.backend.auth.AccessTokenClaimsValidator;
import com.aiorderdeliveryagent.backend.auth.AuthenticationRateLimitProperties;
import com.aiorderdeliveryagent.backend.auth.AuthTokenProperties;
import com.aiorderdeliveryagent.backend.auth.UserAccountRepository;
import com.aiorderdeliveryagent.backend.observability.AuthenticatedLoggingContextFilter;
import com.aiorderdeliveryagent.backend.observability.RequestContext;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({
		AuthTokenProperties.class,
		AuthenticationRateLimitProperties.class,
		CorsProperties.class
})
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			AccessTokenAuthenticationConverter accessTokenAuthenticationConverter) throws Exception {
		http
				.cors(cors -> {})
				.csrf(csrf -> csrf.disable())
				.httpBasic(httpBasic -> httpBasic.disable())
				.formLogin(formLogin -> formLogin.disable())
				.logout(logout -> logout.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health/liveness",
								"/actuator/health/readiness").permitAll()
						.requestMatchers("/api/v1/auth/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(accessTokenAuthenticationConverter))
						.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
						.accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
				.addFilterAfter(
						new AuthenticatedLoggingContextFilter(),
						BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of(
				"Authorization",
				"Content-Type",
				"Accept",
				RequestContext.REQUEST_ID_HEADER));
		configuration.setExposedHeaders(List.of(RequestContext.REQUEST_ID_HEADER));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	@Bean
	UserDetailsService userDetailsService(UserAccountRepository userAccountRepository) {
		return normalizedEmail -> userAccountRepository.findByEmailIgnoreCase(normalizedEmail)
				.filter(user -> user.getPasswordHash() != null)
				.map(user -> new com.aiorderdeliveryagent.backend.auth.AuthenticatedUserPrincipal(
						user.getId(),
						user.getTenantId(),
						user.getEmail(),
						user.getPasswordHash(),
						user.isActive()))
				.orElseThrow(() -> new UsernameNotFoundException("Authentication failed"));
	}

	@Bean
	DaoAuthenticationProvider daoAuthenticationProvider(
			UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}

	@Bean
	AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
		return new ProviderManager(provider);
	}

	@Bean
	SecretKey accessTokenSigningKey(AuthTokenProperties properties) {
		if (properties.accessTokenSigningKey() == null || properties.accessTokenSigningKey().isBlank()) {
			throw new IllegalStateException("AUTH_ACCESS_TOKEN_SIGNING_KEY is required");
		}

		final byte[] keyBytes;
		try {
			keyBytes = Base64.getDecoder().decode(properties.accessTokenSigningKey());
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("AUTH_ACCESS_TOKEN_SIGNING_KEY must be valid Base64", exception);
		}

		if (keyBytes.length < 32) {
			throw new IllegalStateException("AUTH_ACCESS_TOKEN_SIGNING_KEY must decode to at least 32 bytes");
		}
		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey accessTokenSigningKey) {
		return NimbusJwtEncoder.withSecretKey(accessTokenSigningKey).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey accessTokenSigningKey, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(accessTokenSigningKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(AuthTokenProperties.ACCESS_TOKEN_ISSUER),
				new AccessTokenClaimsValidator(clock)));
		return decoder;
	}
}
