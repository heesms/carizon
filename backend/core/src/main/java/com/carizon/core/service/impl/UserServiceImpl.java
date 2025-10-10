package com.carizon.core.service.impl;

import com.carizon.core.domain.user.User;
import com.carizon.core.repository.UserRepository;
import com.carizon.core.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User registerOrUpdateOAuthUser(String email, String name) {
        return userRepository.findByEmail(email)
            .map(existing -> {
                existing.setName(name);
                return userRepository.save(existing);
            })
            .orElseGet(() -> userRepository.save(new User(email, name, List.of("ROLE_USER"))));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
