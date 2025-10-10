package com.carizon.core.service;

import com.carizon.core.domain.user.User;

import java.util.Optional;

public interface UserService {
    User registerOrUpdateOAuthUser(String email, String name);
    Optional<User> findByEmail(String email);
}
