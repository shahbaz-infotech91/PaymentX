package com.paymentx.auth.repository;

import com.paymentx.auth.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    Optional<AuthUser> findByUsername(String username);
}
