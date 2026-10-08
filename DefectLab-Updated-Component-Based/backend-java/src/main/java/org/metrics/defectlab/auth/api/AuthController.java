package org.metrics.defectlab.auth.api;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.security.CurrentUser;
import org.metrics.defectlab.auth.usecase.changepassword.ChangePasswordUseCase;
import org.metrics.defectlab.auth.usecase.currentuser.GetCurrentUserUseCase;
import org.metrics.defectlab.auth.usecase.login.AuthenticateUserUseCase;
import org.metrics.defectlab.auth.usecase.passwordreset.RequestPasswordResetUseCase;
import org.metrics.defectlab.auth.usecase.passwordreset.ResetPasswordUseCase;
import org.metrics.defectlab.auth.usecase.register.RegisterUserCommand;
import org.metrics.defectlab.auth.usecase.register.RegisterUserUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for user authentication, registration, and profile management. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final CurrentUser currentUser;

    public AuthController(RegisterUserUseCase registerUserUseCase,
            AuthenticateUserUseCase authenticateUserUseCase,
            RequestPasswordResetUseCase requestPasswordResetUseCase,
            ResetPasswordUseCase resetPasswordUseCase,
            ChangePasswordUseCase changePasswordUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            CurrentUser currentUser) {
        this.registerUserUseCase = registerUserUseCase;
        this.authenticateUserUseCase = authenticateUserUseCase;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, String> requestPayload, HttpServletRequest request) {
        String name = requestPayload.get("name");
        String email = requestPayload.get("email");
        String password = requestPayload.get("password");

        User registeredUser = registerUserUseCase.register(new RegisterUserCommand(name, email, password));
        currentUser.startSession(request, registeredUser.getId());
        return ResponseEntity.ok(toUserProfile(registeredUser));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> requestPayload, HttpServletRequest request) {
        String email = requestPayload.get("email");
        String password = requestPayload.get("password");

        User authenticatedUser = authenticateUserUseCase.authenticate(email, password);
        currentUser.startSession(request, authenticatedUser.getId());
        return ResponseEntity.ok(toUserProfile(authenticatedUser));
    }

    /**
     * Initiates the password recovery workflow by verifying if the account exists.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @RequestBody Map<String, String> requestPayload) {
        String email = requestPayload.get("email");
        if (!requestPasswordResetUseCase.isEmailRegistered(email)) {
            throw new IllegalArgumentException("No account uses that email address.");
        }
        return ResponseEntity.ok(Map.of("email", email, "registered", true));
    }

    /**
     * Completes password reset for a verified user account.
     */
    @PostMapping("/password/reset")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @RequestBody Map<String, String> requestPayload) {
        String email = requestPayload.get("email");
        String newPassword = requestPayload.get("newPassword");

        resetPasswordUseCase.resetPassword(email, newPassword);
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        currentUser.endSession(request);
        return ResponseEntity.ok(Map.of("signedOut", true));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = currentUser.findUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Sign in to continue."));
        }
        User existingUser = getCurrentUserUseCase.getById(userId);
        return ResponseEntity.ok(toUserProfile(existingUser));
    }

    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody Map<String, String> requestPayload, HttpServletRequest request) {
        Long currentUserId = currentUser.requireUserId(request);
        String currentPassword = requestPayload.get("currentPassword");
        String newPassword = requestPayload.get("newPassword");

        changePasswordUseCase.changePassword(currentUserId, currentPassword, newPassword);
        return ResponseEntity.ok(Map.of("updated", true));
    }

    /**
     * Converts a domain User entity into a sanitized profile representation
     * that never exposes sensitive credentials or password hashes.
     */
    private Map<String, Object> toUserProfile(User user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("createdAt", user.getCreatedAt().toString());
        return profile;
    }
}
