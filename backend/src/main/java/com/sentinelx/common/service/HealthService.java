package com.sentinelx.common.service;

import com.sentinelx.common.dto.DbHealthResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final ConnectionValidator connectionValidator;

    public DbHealthResult getDbHealthResult() {
        return connectionValidator.validate();
    }

    public boolean isDatabaseConnected() {
        return getDbHealthResult().dbReachable();
    }
}
