package com.sentinelx.auth.refresh;

import jakarta.validation.constraints.Min;
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
public class RefreshTokenProperties {

    @Min(value = 1, message = "jwt.refresh-expiration-ms must be greater than zero")
    private long refreshExpirationMs;
}
