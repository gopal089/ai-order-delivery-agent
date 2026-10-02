package com.aiorderdeliveryagent.backend.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserRegistrationService {

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	UserRegistrationService(
			UserAccountRepository userAccountRepository,
			PasswordEncoder passwordEncoder,
			Clock clock) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Transactional
	RegisterResponse register(RegisterRequest request) {
		String normalizedEmail = request.email().strip().toLowerCase(Locale.ROOT);

		if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new EmailAlreadyRegisteredException();
		}

		String passwordHash = passwordEncoder.encode(request.password());
		Instant now = clock.instant();
		UserAccount userAccount = new UserAccount(UUID.randomUUID(), normalizedEmail, passwordHash, now);

		try {
			UserAccount savedUser = userAccountRepository.saveAndFlush(userAccount);
			return new RegisterResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getCreatedAt());
		}
		catch (DataIntegrityViolationException exception) {
			throw new EmailAlreadyRegisteredException();
		}
	}
}
