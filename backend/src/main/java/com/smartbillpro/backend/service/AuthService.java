package com.smartbillpro.backend.service;

import com.smartbillpro.backend.dto.auth.*;
import com.smartbillpro.backend.entity.Role;
import com.smartbillpro.backend.entity.User;
import com.smartbillpro.backend.exception.DuplicateResourceException;
import com.smartbillpro.backend.exception.ResourceNotFoundException;
import com.smartbillpro.backend.repository.RoleRepository;
import com.smartbillpro.backend.repository.UserRepository;
import com.smartbillpro.backend.security.JwtTokenProvider;
import com.smartbillpro.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Transactional
    public JwtAuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // update last login timestamp
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public JwtAuthResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered");
        }

        // Self-signup is always assigned the lowest-privilege role.
        Role cashierRole = roleRepository.findByName("ROLE_CASHIER")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "ROLE_CASHIER"));

        User user = User.builder()
                .fullName(request.getFullName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(cashierRole)
                .isActive(true)
                .build();

        user = userRepository.save(user);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, UserPrincipal.create(user).getAuthorities());

        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public JwtAuthResponse refreshToken(RefreshTokenRequest request) {
        String oldRefreshToken = request.getRefreshToken();
        if (!tokenProvider.validateToken(oldRefreshToken)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid or expired refresh token");
        }
        Long userId = tokenProvider.getUserIdFromToken(oldRefreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, UserPrincipal.create(user).getAuthorities());

        String newAccessToken = tokenProvider.generateAccessToken(authentication);
        String newRefreshToken = tokenProvider.generateRefreshToken(authentication);

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    private JwtAuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return JwtAuthResponse.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtExpirationMs / 1000)
                .user(JwtAuthResponse.UserSummary.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole().getName())
                        .build())
                .build();
    }

    // NOTE: forgotPassword/resetPassword require an email-sending integration (e.g. SMTP/SendGrid)
    // which needs credentials you'll supply. The token-issuing logic is stubbed here; wire in
    // your mail provider in EmailService before enabling this in production.
}
