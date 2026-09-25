package controller;

import domain.dto.LoginDTO;
import domain.model.entities.User;
import security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginDTO data) {

        var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.password());


        Authentication auth = authenticationManager.authenticate(usernamePassword);


        String token = tokenService.gerarToken((User) auth.getPrincipal());

        return ResponseEntity.ok(token);
    }
}
