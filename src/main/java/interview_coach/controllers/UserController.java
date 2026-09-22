package interview_coach.controllers;


import interview_coach.dto.ChangePasswordRequest;
import interview_coach.dto.PublicProfileResponse;
import interview_coach.dto.UserProfileResponse;
import interview_coach.dto.UserUpdateDTO;
import interview_coach.entities.User;
import interview_coach.services.core.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "The current user's own profile, plus other users' public profiles.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userService.getUserByEmail(email);

        UserProfileResponse response = new UserProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getWebName(),
                user.getEmail(),
                user.getRole(),
                user.getTargetRole(),
                user.isBanned(),
                user.isVerified(),
                user.getBanExpirationTime(),
                user.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{webname}")
    public ResponseEntity<PublicProfileResponse> getUserByWebName(@PathVariable String webname) {
        User user = userService.getUserByWebName(webname);

        PublicProfileResponse response = new PublicProfileResponse(
                user.getWebName(),
                user.getTargetRole(),
                user.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }


    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(
            Authentication authentication,
            @RequestBody UserUpdateDTO userUpdateDTO) {

        String email = authentication.getName();
        User user = userService.getUserByEmail(email);

        userService.updateUser(user.getId(), userUpdateDTO);

        User updatedUser = userService.getUserByEmail(email);

        return ResponseEntity.ok(new UserProfileResponse(
                updatedUser.getId(),
                updatedUser.getFirstName(),
                updatedUser.getLastName(),
                updatedUser.getWebName(),
                updatedUser.getEmail(),
                updatedUser.getRole(),
                updatedUser.getTargetRole(),
                updatedUser.isBanned(),
                updatedUser.isVerified(),
                updatedUser.getBanExpirationTime(),
                updatedUser.getCreatedAt()
        ));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request) {

        String email = authentication.getName();
        User user = userService.getUserByEmail(email);

        userService.changePassword(user.getId(), request.currentPassword(), request.newPassword());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userService.getUserByEmail(email);

        userService.deleteUser(user.getId());

        return ResponseEntity.noContent().build();
    }
}
