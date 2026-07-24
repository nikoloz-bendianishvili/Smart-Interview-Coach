package interview_coach.services.core;

import interview_coach.dto.UserUpdateDTO;
import interview_coach.entities.User;
import interview_coach.exceptions.EmailAlreadyExistsException;
import interview_coach.exceptions.InvalidCredentialsException;
import interview_coach.exceptions.UserNotFoundException;
import interview_coach.exceptions.WebNameAlreadyExistsException;
import interview_coach.repositories.UserRepository;
import interview_coach.services.email.EmailService;
import interview_coach.services.email.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;
    private final EmailService emailService;

    public void register(User user) {
        if(userRepository.existsByEmail(user.getEmail())) {
            throw new EmailAlreadyExistsException("Email already in use");
        } else if(userRepository.existsByWebName(user.getWebName())) {
            throw new WebNameAlreadyExistsException("Web name already in use");
        }

        String password = user.getPasswordHash();
        String hashedPassword = passwordEncoder.encode(password);
        user.setPasswordHash(hashedPassword);
        user.setBanned(false);
        userRepository.save(user);
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }

    public User getUserByWebName(String webName) {
        return userRepository.findByWebName(webName)
                .orElseThrow(() -> new UserNotFoundException("User not found with web name: " + webName));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }


    public boolean userIsBanned(Long userId) {
        User user = getUserById(userId);
        if (user.isBanned()) {
            if (user.getBanExpirationTime() != null && user.getBanExpirationTime().isBefore(LocalDateTime.now())) {
                user.setBanned(false);
                user.setBanExpirationTime(null);
                userRepository.save(user);
                return false;
            }
            return true;
        }
        return false;
    }

    @Transactional
    public void banUser(Long userId, LocalDateTime expirationTime) {
        User user = getUserById(userId);
        user.setBanned(true);
        user.setBanExpirationTime(expirationTime);
        userRepository.save(user);
    }


    @Transactional
    public void deleteUser(Long userId) {
        if(userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
        } else {
            throw new UserNotFoundException("User not found with id: " + userId);
        }
    }

    @Transactional
    public void updateUser(Long userId, UserUpdateDTO updateDTO) {
        User user = getUserById(userId);

        if(updateDTO.firstName() != null && !updateDTO.firstName().isEmpty()) {
            user.setFirstName(updateDTO.firstName());
        }

        if(updateDTO.lastName() != null && !updateDTO.lastName().isEmpty()) {
            user.setLastName(updateDTO.lastName());
        }

        if(updateDTO.email() != null && !updateDTO.email().isEmpty()) {
            if(!user.getEmail().equals(updateDTO.email()) && userRepository.existsByEmail(updateDTO.email())) {
                throw new EmailAlreadyExistsException("Email already in use");
            }
            if (!user.getEmail().equals(updateDTO.email())) {
                user.setEmail(updateDTO.email());
                user.setVerified(false);
            }
        }

        if(updateDTO.webName() != null && !updateDTO.webName().isEmpty()) {
            if(!user.getWebName().equals(updateDTO.webName()) && userRepository.existsByWebName(updateDTO.webName())) {
                throw new WebNameAlreadyExistsException("Web name already in use");
            }
            user.setWebName(updateDTO.webName());
        }

        if(updateDTO.targetRole() != null && !updateDTO.targetRole().isEmpty()) {
            user.setTargetRole(updateDTO.targetRole());
        }

        if (updateDTO.password() != null && !updateDTO.password().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(updateDTO.password()));
        }
        userRepository.save(user);

        if (!user.isVerified()) {
            String token = verificationService.createVerificationToken(user);
            emailService.sendVerificationEmail(user.getEmail(), token);
        }
    }
}
