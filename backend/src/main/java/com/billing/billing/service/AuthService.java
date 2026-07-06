package com.billing.billing.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.RegisterRequest;
import com.billing.billing.dto.UserResponse;
import com.billing.billing.model.Role;
import com.billing.billing.model.User;
import com.billing.billing.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.email(), hashedPassword, Role.OWNER);
        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getRole());
    }
}