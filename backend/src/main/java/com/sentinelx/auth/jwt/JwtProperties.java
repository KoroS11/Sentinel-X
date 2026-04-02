package com.sentinelx.auth.jwt;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank
    @Size(min = 32, message = "jwt.secret must be at least 32 characters")
    private String secret;

    @Min(value = 1, message = "jwt.expiration-ms must be greater than zero")
    private long expirationMs;
}