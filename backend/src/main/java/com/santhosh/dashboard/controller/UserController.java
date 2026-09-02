package com.santhosh.dashboard.controller;

import com.santhosh.dashboard.dto.*;
import com.santhosh.dashboard.model.User;
import com.santhosh.dashboard.mapper.UserMapper;
import com.santhosh.dashboard.repository.RoleRepository;
import com.santhosh.dashboard.repository.UserRepository;
import com.santhosh.dashboard.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final RoleRepository roleRepository;

    public UserController(UserRepository userRepository, UserService userService,
                          RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.roleRepository = roleRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserRegistrationRequest request) {
        log.info("Received registration request for username: {}", request.getUsername());
        try {
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Username is required."));
            }
            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Password is required."));
            }
            if (request.getPassword().length() < 8) {
                return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 8 characters."));
            }
            if (request.getRole() == null || request.getRole().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Role is required."));
            }
            if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                if (!request.getEmail().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Please enter a valid email address."));
                }
            }

            User registered = userService.register(
                request.getUsername().trim(),
                request.getEmail() != null ? request.getEmail().trim() : null,
                request.getPassword(),
                request.getFirstName(),
                request.getLastName(),
                request.getPhone(),
                request.getOrganization(),
                "ROLE_VIEWER"
            );

            log.info("User registered successfully: {}", registered.getUsername());
            return ResponseEntity.ok(UserMapper.toResponse(registered));
        } catch (IllegalArgumentException e) {
            log.warn("Registration rejected for username {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unhandled exception during user registration for username {}:", request.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal server error. Database connection failed or invalid query."));
        }
    }

    /**
     * Admin-only user creation. Public self-registration is intentionally kept
     * separate so an anonymous caller can never choose an elevated role.
     */
    @PostMapping("/admin-create")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<?> createUser(@RequestBody UserRegistrationRequest request) {
        try {
            User created = userService.createAdminUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getPhone(),
                    request.getOrganization(),
                    request.getRole()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(UserMapper.toResponse(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unhandled exception while creating admin-managed user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unable to create user. Please try again."));
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    public ResponseEntity<?> assignRole(@PathVariable Long id, @RequestParam String role) {
        try {
            userService.assignRole(id, role);
            return ResponseEntity.ok(Map.of("message", "Role updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<?> disableUser(@PathVariable Long id, @RequestParam boolean enabled) {
        try {
            userService.setEnabled(id, enabled);
            return ResponseEntity.ok(Map.of("message", "User status updated", "enabled", enabled));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @RequestParam String newPassword) {
        try {
            userService.resetPassword(id, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password reset"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            if (!userRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }
            userRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "User deleted"));
        } catch (Exception e) {
            log.error("Failed to delete user {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unable to delete user."));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ProfileUpdateRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        User updatedUser = userService.updateProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(UserMapper.toResponse(updatedUser));
    }
}
