package interview_coach.security.service;

import interview_coach.entities.User;
import interview_coach.enums.Role;
import interview_coach.exceptions.*;
import interview_coach.repositories.UserRepository;
import interview_coach.security.dto.AuthResponse;
import interview_coach.security.dto.LoginRequest;
import interview_coach.security.dto.RegisterRequest;
import interview_coach.services.core.UserService;
import interview_coach.services.email.EmailService;
import interview_coach.services.email.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final VerificationService verificationService;
    private final UserService userService;

    public String register(RegisterRequest registerRequest) {
        User user = User.builder()
                .email(registerRequest.getEmail())
                .passwordHash(registerRequest.getPassword())
                .webName(registerRequest.getWebName()).role(Role.USER)
                .targetRole(registerRequest.getTargetRole()).isBanned(false).isVerified(false)
                .build();
        userService.register(user);

        emailService.sendVerificationEmail(user.getEmail(), verificationService.createVerificationToken(user));
        return "Registration successful! Please check your email to activate your account.";
    }


    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.isVerified()) {
            throw new AccountNotVerifiedException("Please verify your email before logging in");
        }

        if (userService.userIsBanned(user.getId())) {
            throw new AccountBannedException("This account has been suspended");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getWebName(), user.getRole().name());
    }

    public String verifyEmail(String token) {
        if (!verificationService.verifyToken(token)) {
            throw new InvalidTokenException("Invalid or expired verification link");
        }
        return "Email verified successfully! You can now log in.";
    }
}
