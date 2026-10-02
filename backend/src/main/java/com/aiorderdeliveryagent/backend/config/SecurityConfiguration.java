package com.aiorderdeliveryagent.backend.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.aiorderdeliveryagent.backend.auth.AuthTokenProperties;
import com.aiorderdeliveryagent.backend.auth.UserAccountRepository;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(AuthTokenProperties.class)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.httpBasic(httpBasic -> httpBasic.disable())
				.formLogin(formLogin -> formLogin.disable())
				.logout(logout -> logout.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/v1/auth/**").permitAll()
						.anyRequest().denyAll())
				.exceptionHandling(Customizer.withDefaults());
		return http.build();
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
	JwtDecoder jwtDecoder(SecretKey accessTokenSigningKey) {
		return NimbusJwtDecoder.withSecretKey(accessTokenSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
	}
}
