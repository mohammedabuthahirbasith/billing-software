package com.billing.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.billing.billing.security.JwtService;
import com.billing.billing.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long STORE_ID = 3L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerProvisionsANewStoreAndItsFirstOwner() {
        Store store = TestEntities.store(STORE_ID, "Kirana Mart");
        when(storeRepository.save(any(Store.class))).thenReturn(store);
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0), 11L));

        UserResponse response = authService.register(
                new RegisterRequest("owner@example.com", "password123", "Kirana Mart"));

        ArgumentCaptor<Store> storeCaptor = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository).save(storeCaptor.capture());
        assertThat(storeCaptor.getValue().getName()).isEqualTo("Kirana Mart");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.OWNER);
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(userCaptor.getValue().getStore()).isSameAs(store);

        assertThat(response).isEqualTo(new UserResponse(11L, "owner@example.com", Role.OWNER, "Kirana Mart"));
    }

    @Test
    void registerRejectsAnAlreadyRegisteredEmail() {
        when(storeRepository.save(any(Store.class))).thenReturn(TestEntities.store(STORE_ID, "Kirana Mart"));
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("owner@example.com", "password123", "Kirana Mart")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email already registered")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createStaffUserAttachesTheNewUserToTheCallersOwnStore() {
        TestEntities.authenticate(1L, "owner@example.com", Role.OWNER, STORE_ID);
        Store store = TestEntities.store(STORE_ID, "Kirana Mart");
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(userRepository.existsByEmail("cashier@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0), 12L));

        UserResponse response = authService.createStaffUser(
                new CreateUserRequest("cashier@example.com", "password123", Role.CASHIER));

        assertThat(response).isEqualTo(new UserResponse(12L, "cashier@example.com", Role.CASHIER, "Kirana Mart"));
        verify(storeRepository, never()).save(any());
    }

    @Test
    void createStaffUserFailsWhenTheCallersStoreIsMissing() {
        TestEntities.authenticate(1L, "owner@example.com", Role.OWNER, STORE_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.createStaffUser(
                new CreateUserRequest("cashier@example.com", "password123", Role.CASHIER)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void loginIssuesATokenForValidCredentials() {
        User user = TestEntities.user(11L, "owner@example.com", "hashed", Role.OWNER,
                TestEntities.store(STORE_ID, "Kirana Mart"));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("owner@example.com", "password123"));

        assertThat(response).isEqualTo(new AuthResponse("jwt-token", "owner@example.com", Role.OWNER));
    }

    @Test
    void loginRejectsUnknownEmailWithTheSameGenericMessageAsABadPassword() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid email or password")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginRejectsAWrongPassword() {
        User user = TestEntities.user(11L, "owner@example.com", "hashed", Role.OWNER,
                TestEntities.store(STORE_ID, "Kirana Mart"));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("owner@example.com", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid email or password");
        verify(jwtService, never()).generateToken(any());
    }
}
