package com.cosmos.fraud.stream.serialization;

import com.cosmos.fraud.stream.model.TransactionEvent;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

/**
 * Flink {@link DeserializationSchema} that converts raw Avro-encoded bytes from
 * the {@code transactions.raw} Kafka topic into {@link TransactionEvent} POJOs.
 *
 * <p>Uses Avro's {@link ReflectDatumReader} so that no Avro-generated code is
 * required — the schema is derived from the Java class at runtime.
 *
 * <p><strong>Thread safety:</strong> Flink calls {@link #deserialize} from a
 * single operator thread, so the cached reader and decoder are safe.
 */
public final class AvroDeserializationSchema implements DeserializationSchema<TransactionEvent> {

    private static final long serialVersionUID = 1L;

    /**
     * Reusable datum reader — instantiated once per operator instance and then
     * reused across records to avoid repeated schema reflection overhead.
     */
    private transient ReflectDatumReader<TransactionEvent> datumReader;

    /**
     * Reusable binary decoder.  Replaced per call via
     * {@link DecoderFactory#binaryDecoder(byte[], BinaryDecoder)}.
     */
    private transient BinaryDecoder decoder;

    // -----------------------------------------------------------------
    // DeserializationSchema implementation
    // -----------------------------------------------------------------

    @Override
    public void open(InitializationContext context) throws Exception {
        datumReader = new ReflectDatumReader<>(TransactionEvent.class);
    }

    /**
     * Deserialises a single Avro-binary record.
     *
     * @param message the raw bytes received from Kafka
     * @return the decoded {@link TransactionEvent}
     * @throws IOException if Avro decoding fails
     */
    @Override
    public TransactionEvent deserialize(byte[] message) throws IOException {
        ensureReader();
        decoder = DecoderFactory.get().binaryDecoder(message, decoder);
        return datumReader.read(null, decoder);
    }

    /**
     * Returns {@code false}: a {@code null} byte array from Kafka indicates a
     * tombstone / delete marker and should be filtered out by the pipeline.
     */
    @Override
    public boolean isEndOfStream(TransactionEvent nextElement) {
        return false;
    }

    @Override
    public TypeInformation<TransactionEvent> getProducedType() {
        return TypeInformation.of(TransactionEvent.class);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void ensureReader() {
        if (datumReader == null) {
            datumReader = new ReflectDatumReader<>(TransactionEvent.class);
        }
    }
}
