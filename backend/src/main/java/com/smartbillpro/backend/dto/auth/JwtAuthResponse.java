package com.smartbillpro.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class JwtAuthResponse {

    @Builder.Default
    private String tokenType = "Bearer";

    private String accessToken;
    private String refreshToken;
    private Long expiresIn; // seconds

    private UserSummary user;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    public static class UserSummary {
        private Long id;
        private String fullName;
        private String username;
        private String email;
        private String role;
    }
}
