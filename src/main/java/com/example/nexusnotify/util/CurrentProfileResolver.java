package com.example.nexusnotify.util;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentProfileResolver {

    public Integer profileId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Nenhum perfil autenticado no contexto atual");
        }

        try {
            int profileId = Integer.parseInt(token.getToken().getSubject());
            if (profileId <= 0) {
                throw new InsufficientAuthenticationException("O token possui um perfil invalido");
            }
            return profileId;
        } catch (NumberFormatException exception) {
            throw new InsufficientAuthenticationException("O token possui um perfil invalido", exception);
        }
    }
}
