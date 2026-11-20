package org.group10.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "ID token que Google le da al front después del Sign-In")
public record GoogleLoginRequest(
        @Schema(example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...") @NotBlank String idToken
) {}
