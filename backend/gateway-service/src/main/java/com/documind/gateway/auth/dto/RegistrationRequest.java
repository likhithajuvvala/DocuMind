package com.documind.gateway.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank String workspaceName) {
}
