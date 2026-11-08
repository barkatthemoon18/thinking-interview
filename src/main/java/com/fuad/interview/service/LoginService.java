package com.fuad.interview.service;

import com.fuad.interview.dto.LoginPayloadDto;
import com.fuad.interview.dto.UserDto;

public interface LoginService {
    String login(UserDto user);

    LoginPayloadDto authenticate(String authorization);

    LoginPayloadDto requireRole(String authorization, String requiredRole);
}
