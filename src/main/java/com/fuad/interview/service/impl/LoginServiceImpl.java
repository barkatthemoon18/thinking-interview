package com.fuad.interview.service.impl;

import com.fuad.interview.dto.LoginPayloadDto;
import com.fuad.interview.dto.UserDto;
import com.fuad.interview.repositories.RolesRepository;
import com.fuad.interview.repositories.UsersRepository;
import com.fuad.interview.service.LoginService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;

@Service
@Transactional(readOnly = true)
@AllArgsConstructor
public class LoginServiceImpl implements LoginService {
    private final UsersRepository usersRepository;
    private final RolesRepository rolesRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String login(UserDto user) {
        if (user == null || user.username() == null || user.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de usuario es obligatorio.");
        }
        LoginPayloadDto payload = findIdentity(user.username());
        return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(payload));
    }

    @Override
    public LoginPayloadDto authenticate(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El header Authorization es obligatorio.");
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        LoginPayloadDto payload;
        try {
            payload = objectMapper.readValue(Base64.getDecoder().decode(token), LoginPayloadDto.class);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El token de login no es válido.");
        }
        if (payload == null || !"login".equals(payload.scheme())
                || payload.username() == null || payload.username().isBlank()
                || payload.role() == null || payload.role().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El token de login no es válido.");
        }
        LoginPayloadDto identity = findIdentity(payload.username());
        if (!identity.role().equals(payload.role())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El rol del token no coincide con la base de datos.");
        }
        return identity;
    }

    @Override
    public LoginPayloadDto requireRole(String authorization, String requiredRole) {
        LoginPayloadDto identity = authenticate(authorization);
        if (!requiredRole.equals(identity.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu rol no permite realizar esta operación.");
        }
        return identity;
    }

    private LoginPayloadDto findIdentity(String username) {
        var user = usersRepository.findByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no existe.");
        }
        List<String> roles = rolesRepository.findRoleNamesByUserId(user.getId());
        if (roles.size() != 1 || !("reportante".equals(roles.get(0)) || "revisor".equals(roles.get(0)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario debe tener un único rol válido.");
        }
        return new LoginPayloadDto("login", user.getUsername(), roles.get(0));
    }
}
