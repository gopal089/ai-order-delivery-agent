package com.aiorderdeliveryagent.backend.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

	boolean existsByEmailIgnoreCase(String email);

	Optional<UserAccount> findByEmailIgnoreCase(String email);
}
