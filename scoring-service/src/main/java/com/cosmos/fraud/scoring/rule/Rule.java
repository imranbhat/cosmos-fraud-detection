package com.cosmos.fraud.scoring.rule;

import com.cosmos.fraud.avro.EnrichedTransaction;

public interface Rule {

    String name();

    int evaluate(EnrichedTransaction transaction);
}
