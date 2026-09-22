package com.mirattic.flow.user.repository;

import com.mirattic.flow.user.entity.AuthProvider;
import com.mirattic.flow.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 소셜 로그인의 식별자는 이메일이 아니라 (provider, providerId) 조합이다. */
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
