package com.fuad.interview.controllers;

import com.fuad.interview.dto.LoginResponseDto;
import com.fuad.interview.dto.UserDto;
import com.fuad.interview.service.LoginService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class LoginController {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginController.class);

    private final LoginService loginService;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponseDto login(@RequestBody UserDto user) {
        LOGGER.info("Solicitud recibida: POST /api/login");
        var response = new LoginResponseDto(loginService.login(user));
        LOGGER.info("Solicitud completada: POST /api/login status=200");
        return response;
    }
}
