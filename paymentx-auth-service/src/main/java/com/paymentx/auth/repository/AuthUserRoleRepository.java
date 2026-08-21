package com.paymentx.auth.repository;

import com.paymentx.auth.entity.AuthUser;
import com.paymentx.auth.entity.AuthUserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuthUserRoleRepository extends JpaRepository<AuthUserRole, UUID> {

    List<AuthUserRole> findByAuthUser(AuthUser authUser);
}
