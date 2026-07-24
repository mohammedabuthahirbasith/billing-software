package com.billing.billing.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.AuthResponse;
import com.billing.billing.dto.CreateUserRequest;
import com.billing.billing.dto.LoginRequest;
import com.billing.billing.dto.RegisterRequest;
import com.billing.billing.dto.UserResponse;
import com.billing.billing.model.Role;
import com.billing.billing.model.Store;
import com.billing.billing.model.User;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.repository.UserRepository;
import com.billing.billing.security.CurrentUser;
import com.billing.billing.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, StoreRepository storeRepository,
                        PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // Self-serve signup: provisions a whole new tenant (Store) plus its first OWNER, atomically.
    public UserResponse register(RegisterRequest request) {
        Store store = storeRepository.save(new Store(request.storeName()));
        return createUser(request.email(), request.password(), Role.OWNER, store);
    }

    // OWNER-only, called from UserController — provisions a staff login (typically CASHIER) within
    // the CURRENT authenticated user's existing store. Never creates a new store.
    // findById, not getReferenceById: createUser() below always reads store.getName() for the
    // response, so the lazy-proxy optimization doesn't apply here and would only throw once this
    // method (and its session) has already returned.
    public UserResponse createStaffUser(CreateUserRequest request) {
        Store store = storeRepository.findById(CurrentUser.get().storeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Store not found"));
        return createUser(request.email(), request.password(), request.role(), store);
    }

    private UserResponse createUser(String email, String password, Role role, Store store) {
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        String hashedPassword = passwordEncoder.encode(password);
        User user = new User(email, hashedPassword, role, store);
        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getRole(), store.getName());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole());
    }
}
