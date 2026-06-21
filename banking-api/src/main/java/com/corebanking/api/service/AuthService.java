package com.corebanking.api.service;

import com.corebanking.api.config.JwtTokenProvider;
import com.corebanking.api.dto.LoginRequest;
import com.corebanking.api.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse authenticate(LoginRequest request) {
        // Mode démo : utilisateurs hardcodés
        // En production, remplacer par une vraie base d'utilisateurs
        List<String> roles = switch (request.getUsername()) {
            case "admin" -> {
                if (!"admin123".equals(request.getPassword()))
                    throw new IllegalArgumentException("Identifiants invalides");
                yield List.of("ADMIN");
            }
            case "client" -> {
                if (!"client123".equals(request.getPassword()))
                    throw new IllegalArgumentException("Identifiants invalides");
                yield List.of("CLIENT");
            }
            case "auditor" -> {
                if (!"auditor123".equals(request.getPassword()))
                    throw new IllegalArgumentException("Identifiants invalides");
                yield List.of("AUDITOR");
            }
            default -> throw new IllegalArgumentException("Utilisateur inconnu: " + request.getUsername());
        };

        String token = jwtTokenProvider.generateToken(request.getUsername(), roles);
        return new LoginResponse(token, "Bearer", request.getUsername(), roles);
    }
}
