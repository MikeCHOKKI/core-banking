package com.corebanking.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String type;
    private String username;
    private List<String> roles;
}
