package com.cosmos.fraud.common.dto;

import java.util.List;

public record ScoringResponse(
        String txId,
        int riskScore,
        String decision,
        List<String> appliedRules,
        long latencyMs
) {
}
