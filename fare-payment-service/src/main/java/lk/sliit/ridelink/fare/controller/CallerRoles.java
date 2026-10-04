package lk.sliit.ridelink.fare.controller;

import org.springframework.security.core.Authentication;

final class CallerRoles {

    private CallerRoles() {
        // Utility class
    }

    static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
