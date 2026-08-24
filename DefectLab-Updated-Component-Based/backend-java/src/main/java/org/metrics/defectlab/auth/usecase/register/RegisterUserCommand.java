package org.metrics.defectlab.auth.usecase.register;

public record RegisterUserCommand(String name, String email, String password) {
}
