package org.metrics.defectlab.auth.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Component;

/**
 * Reads the signed-in user from the servlet session.
 *
 * <p>Every dataset, run, and comparison lookup is filtered by this id, so
 * authorization is enforced in the query rather than after loading a row.</p>
 */
@Component
public class CurrentUser {

    static final String SESSION_ATTRIBUTE = "defectlab.userId";

    public Long requireUserId(HttpServletRequest request) {
        Long userId = findUserId(request);
        if (userId == null) {
            throw new UnauthenticatedException("Sign in to continue.");
        }
        return userId;
    }

    public Long findUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        return value instanceof Long ? (Long) value : null;
    }

    public void startSession(HttpServletRequest request, Long userId) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            // Drop the old identifier so a fixated session cannot be reused.
            existing.invalidate();
        }
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, userId);
    }

    public void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public static class UnauthenticatedException extends RuntimeException {
        public UnauthenticatedException(String message) {
            super(message);
        }
    }
}
