package com.cosmos.fraud.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    private static final int SAMPLE_SIZE = 1000;
    private static final int ULID_LENGTH = 26;
    private static final String ULID_PATTERN = "^[0-9A-Z]{26}$";

    @Test
    void generatedIdIsNonNullAndNonEmpty() {
        String id = IdGenerator.generateTxId();

        assertThat(id).isNotNull().isNotBlank();
    }

    @Test
    void generatedIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            ids.add(IdGenerator.generateTxId());
        }

        assertThat(ids).hasSize(SAMPLE_SIZE);
    }

    @Test
    void generatedIdHasValidUlidFormat() {
        String id = IdGenerator.generateTxId();

        assertThat(id).hasSize(ULID_LENGTH);
        assertThat(id).matches(ULID_PATTERN);
    }

    @Test
    void allGeneratedIdsHaveValidUlidFormat() {
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            String id = IdGenerator.generateTxId();
            assertThat(id)
                    .hasSize(ULID_LENGTH)
                    .matches(ULID_PATTERN);
        }
    }
}
