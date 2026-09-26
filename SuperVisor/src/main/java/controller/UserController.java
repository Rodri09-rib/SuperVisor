package controller;

import domain.dto.CurrentUserDTO;
import domain.model.entities.User;
import service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public ResponseEntity<CurrentUserDTO> currentUser(@AuthenticationPrincipal User loggedUser) {

        return ResponseEntity.ok(userService.currentUser(loggedUser.getEmail()));
    }
}
