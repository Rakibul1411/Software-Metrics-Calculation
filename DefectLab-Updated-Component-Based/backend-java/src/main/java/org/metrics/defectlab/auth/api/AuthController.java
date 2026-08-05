package org.metrics.defectlab.auth.api;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.metrics.defectlab.auth.application.AuthService;
import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = authService.register(
                body.get("name"), body.get("email"), body.get("password"));
        currentUser.startSession(request, user.getId());
        return ResponseEntity.ok(profile(user));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = authService.authenticate(body.get("email"), body.get("password"));
        currentUser.startSession(request, user.getId());
        return ResponseEntity.ok(profile(user));
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
        return ResponseEntity.ok(profile(authService.requireUser(userId)));
    }

    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody Map<String, String> body, HttpServletRequest request) {
        authService.changePassword(currentUser.requireUserId(request),
                body.get("currentPassword"), body.get("newPassword"));
        return ResponseEntity.ok(Map.of("updated", true));
    }

    /** Never exposes the password hash. */
    private Map<String, Object> profile(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("name", user.getName());
        result.put("email", user.getEmail());
        result.put("createdAt", user.getCreatedAt().toString());
        return result;
    }
}
