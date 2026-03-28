package com.cosmos.fraud.common.util;

import com.github.f4b6a3.ulid.UlidCreator;

public final class IdGenerator {

    private IdGenerator() {
    }

    public static String generateTxId() {
        return UlidCreator.getMonotonicUlid().toString();
    }
}
