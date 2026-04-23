package com.sentinelx.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbHealthResult {
    private String status;  // UP, DEGRADED, DOWN
    
    @JsonProperty("dbReachable")
    private boolean dbReachable;
    
    @JsonProperty("dbLatencyMs")
    private long dbLatencyMs;
}
