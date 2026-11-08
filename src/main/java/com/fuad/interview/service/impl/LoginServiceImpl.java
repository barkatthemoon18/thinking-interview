package com.fuad.interview.service.impl;

import com.fuad.interview.dto.LoginPayloadDto;
import com.fuad.interview.dto.UserDto;
import com.fuad.interview.repositories.RolesRepository;
import com.fuad.interview.repositories.UsersRepository;
import com.fuad.interview.service.LoginService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginServiceImpl.class);

    private final UsersRepository usersRepository;
    private final RolesRepository rolesRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String login(UserDto user) {
        LOGGER.debug("Iniciando login simulado");
        if (user == null || user.username() == null || user.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de usuario es obligatorio.");
        }
        LoginPayloadDto payload = findIdentity(user.username());
        String token = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(payload));
        LOGGER.info("Login simulado completado: username={} role={}", payload.username(), payload.role());
        return token;
    }

    @Override
    public LoginPayloadDto authenticate(String authorization) {
        LOGGER.debug("Validando token y consultando identidad actual en base de datos");
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
            LOGGER.debug("Rol del token desactualizado: username={} currentRole={}",
                    identity.username(), identity.role());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El rol del token no coincide con la base de datos.");
        }
        LOGGER.debug("Identidad validada: username={} role={}", identity.username(), identity.role());
        return identity;
    }

    @Override
    public LoginPayloadDto requireRole(String authorization, String requiredRole) {
        LoginPayloadDto identity = authenticate(authorization);
        if (!requiredRole.equals(identity.role())) {
            LOGGER.debug("Permiso rechazado: username={} role={} requiredRole={}",
                    identity.username(), identity.role(), requiredRole);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu rol no permite realizar esta operación.");
        }
        LOGGER.debug("Permiso concedido: username={} role={} requiredRole={}",
                identity.username(), identity.role(), requiredRole);
        return identity;
    }

    private LoginPayloadDto findIdentity(String username) {
        var user = usersRepository.findByUsername(username);
        if (user == null) {
            LOGGER.debug("Identidad rechazada: usuario inexistente");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no existe.");
        }
        List<String> roles = rolesRepository.findRoleNamesByUserId(user.getId());
        if (roles.size() != 1 || !("reportante".equals(roles.get(0)) || "revisor".equals(roles.get(0)))) {
            LOGGER.debug("Identidad rechazada: username={} roleCount={} motivo=se requiere un único rol válido",
                    user.getUsername(), roles.size());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario debe tener un único rol válido.");
        }
        return new LoginPayloadDto("login", user.getUsername(), roles.get(0));
    }
}
