package com.example.ChatAppBackend.User;

import com.example.ChatAppBackend.TokenAndFilter.CurrentUserDetails;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService){
        this.userService = userService;
    }

    @PostMapping("/enter")
    public User enter(@CurrentSecurityContext(expression = "authentication.details") CurrentUserDetails user){
        return this.userService.createOrTouchUser(user);
    }

    @GetMapping("/retrieve")
    public User retrieve(@CurrentSecurityContext(expression = "authentication.details") CurrentUserDetails user){
        return this.userService.retrieveUser(user);
    }

    @DeleteMapping("/detach")
    public void detach(@CurrentSecurityContext(expression = "authentication.details") CurrentUserDetails user){
        this.userService.removeUser(user);
    }
}
