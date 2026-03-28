package com.cosmos.fraud.scoring.rule;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.cosmos.fraud.avro.EnrichedTransaction;

@Component
public class MerchantCategoryRule implements Rule {

    /**
     * High-risk Merchant Category Codes:
     * 7995 - Betting/Casino/Gambling
     * 5967 - Direct Marketing: Inbound Telemarketing
     * 5912 - Drug Stores and Pharmacies
     * 5122 - Drugs, Drug Proprietaries and Druggist's Sundries
     */
    private static final Set<String> HIGH_RISK_MCC = Set.of("7995", "5967", "5912", "5122");
    private static final int HIGH_RISK_SCORE = 15;
    private static final int NO_RISK_SCORE = 0;

    @Override
    public String name() {
        return "MERCHANT_CATEGORY";
    }

    @Override
    public int evaluate(EnrichedTransaction transaction) {
        String mcc = transaction.getMcc() != null ? transaction.getMcc().toString() : null;
        if (mcc != null && HIGH_RISK_MCC.contains(mcc)) {
            return HIGH_RISK_SCORE;
        }
        return NO_RISK_SCORE;
    }
}
