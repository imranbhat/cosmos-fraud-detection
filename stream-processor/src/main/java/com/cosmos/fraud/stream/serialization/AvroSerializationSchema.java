package com.cosmos.fraud.stream.serialization;

import com.cosmos.fraud.stream.model.EnrichedTransaction;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.flink.api.common.serialization.SerializationSchema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Flink {@link SerializationSchema} that serialises {@link EnrichedTransaction}
 * POJOs to Avro binary format for publishing to the {@code transactions.enriched}
 * Kafka topic.
 *
 * <p>Uses Avro's {@link ReflectDatumWriter} — no code generation required.
 *
 * <p><strong>Thread safety:</strong> each operator instance has its own writer
 * and reusable encoder. The {@link ByteArrayOutputStream} is reset between calls.
 */
public final class AvroSerializationSchema implements SerializationSchema<EnrichedTransaction> {

    private static final long serialVersionUID = 1L;

    private transient ReflectDatumWriter<EnrichedTransaction> datumWriter;
    private transient BinaryEncoder encoder;
    private transient ByteArrayOutputStream outputStream;

    // -----------------------------------------------------------------
    // SerializationSchema implementation
    // -----------------------------------------------------------------

    @Override
    public void open(InitializationContext context) throws Exception {
        initWriter();
    }

    /**
     * Serialises an {@link EnrichedTransaction} to Avro binary bytes.
     *
     * @param element the record to serialise
     * @return Avro-encoded byte array
     * @throws UncheckedIOException wrapping any {@link IOException} from Avro
     */
    @Override
    public byte[] serialize(EnrichedTransaction element) {
        ensureWriter();
        outputStream.reset();
        try {
            encoder = EncoderFactory.get().binaryEncoder(outputStream, encoder);
            datumWriter.write(element, encoder);
            encoder.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to Avro-serialise EnrichedTransaction: "
                    + element.getTxId(), e);
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void initWriter() {
        datumWriter  = new ReflectDatumWriter<>(EnrichedTransaction.class);
        outputStream = new ByteArrayOutputStream(512);
    }

    private void ensureWriter() {
        if (datumWriter == null) {
            initWriter();
        }
    }
}
