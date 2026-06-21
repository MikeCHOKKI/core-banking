package com.corebanking.api.controller;

import com.corebanking.account.repository.AccountRepository;
import com.corebanking.account.service.AccountCommandService;
import com.corebanking.api.TestMvcConfig;
import com.corebanking.api.dto.LoginResponse;
import com.corebanking.api.service.AuthService;
import com.corebanking.query.service.AccountQueryService;
import com.corebanking.transfer.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestMvcConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController — Tests d'intégration")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Service de AuthController
    @MockitoBean
    private AuthService authService;

    // Services des autres contrôleurs (chargés par @RestController scan)
    @MockitoBean
    private AccountCommandService accountCommandService;

    @MockitoBean
    private AccountQueryService accountQueryService;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private TransferService transferService;

    // -----------------------------------------------------------------------
    // POST /api/v1/auth/login
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/auth/login — retourne 200 avec token JWT pour admin/admin123")
    void login_shouldReturn200WithTokenForValidCredentials() throws Exception {
        String username = "admin";
        String password = "admin123";
        String token = "jwt-token-123";
        List<String> roles = List.of("ADMIN");

        LoginResponse response = new LoginResponse(token, "Bearer", username, roles);

        when(authService.authenticate(any())).thenReturn(response);

        String body = """
                { "username": "%s", "password": "%s" }
                """.formatted(username, password);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is(token)))
                .andExpect(jsonPath("$.type", is("Bearer")))
                .andExpect(jsonPath("$.username", is(username)))
                .andExpect(jsonPath("$.roles[0]", is("ADMIN")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — retourne 400 si le mot de passe est invalide")
    void login_shouldReturn400WhenPasswordInvalid() throws Exception {
        when(authService.authenticate(any()))
                .thenThrow(new IllegalArgumentException("Identifiants invalides"));

        String body = """
                { "username": "admin", "password": "wrong-password" }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — retourne 400 si l'utilisateur est inconnu")
    void login_shouldReturn400WhenUserUnknown() throws Exception {
        when(authService.authenticate(any()))
                .thenThrow(new IllegalArgumentException("Utilisateur inconnu: unknown"));

        String body = """
                { "username": "unknown", "password": "some-password" }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
