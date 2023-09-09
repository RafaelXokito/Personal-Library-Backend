package com.personal_book_library_api.demo.rest;

import com.personal_book_library_api.demo.dtos.auth.LoginDTO;
import com.personal_book_library_api.demo.dtos.auth.SignupDTO;
import com.personal_book_library_api.demo.dtos.auth.TokenDTO;
import com.personal_book_library_api.demo.entities.Reader;
import com.personal_book_library_api.demo.entities.Writer;
import com.personal_book_library_api.demo.security.TokenGenerator;
import com.personal_book_library_api.demo.services.UserManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    UserManager userDetailsManager;
    @Autowired
    TokenGenerator tokenGenerator;
    @Autowired
    DaoAuthenticationProvider daoAuthenticationProvider;
    @Autowired
    @Qualifier("jwtRefreshTokenAuthProvider")
    JwtAuthenticationProvider refreshTokenAuthProvider;

    @PostMapping("/register")
    public ResponseEntity register(@RequestBody SignupDTO signupDTO) {
        Authentication authentication = null;
        if (signupDTO.isWriter()) {
            Writer writer = new Writer(signupDTO.getFirstName(), signupDTO.getLastName(), signupDTO.getEmail(), signupDTO.getPassword());
            userDetailsManager.createUser(writer);
            authentication = UsernamePasswordAuthenticationToken.authenticated(writer, signupDTO.getPassword(), Collections.EMPTY_LIST);
        } else {
            Reader reader = new Reader(signupDTO.getFirstName(), signupDTO.getLastName(), signupDTO.getEmail(), signupDTO.getPassword());
            userDetailsManager.createUser(reader);
            authentication = UsernamePasswordAuthenticationToken.authenticated(reader, signupDTO.getPassword(), Collections.EMPTY_LIST);
        }

        return ResponseEntity.ok(tokenGenerator.createToken(authentication));
    }

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody LoginDTO loginDTO) {
        Authentication authentication = daoAuthenticationProvider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(loginDTO.getEmail(), loginDTO.getPassword()));

        return ResponseEntity.ok(tokenGenerator.createToken(authentication));
    }

    @PostMapping("/token")
    public ResponseEntity refreshToken(@RequestBody TokenDTO tokenDTO) {
        Authentication authentication = refreshTokenAuthProvider.authenticate(new BearerTokenAuthenticationToken(tokenDTO.getRefreshToken()));
        return ResponseEntity.ok(tokenGenerator.createToken(authentication));
    }
}
