/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.codec.parquet;

import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.codec.DecompressionEngine;
import org.opensearch.dataprepper.model.codec.InputCodec;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.JacksonEvent;
import org.opensearch.dataprepper.model.io.InputFile;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.fs.LocalInputFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Consumer;

import static org.apache.parquet.avro.AvroReadSupport.READ_INT96_AS_FIXED;

/**
 * An implementation of {@link InputCodec} which parses parquet records into fields.
 */
@DataPrepperPlugin(name = "parquet", pluginType = InputCodec.class)
public class ParquetInputCodec implements InputCodec {

    static final String EVENT_TYPE = "event";

    static final String FILE_PREFIX = "parquet-data";

    static final String FILE_SUFFIX = ".parquet";

    private static final Logger LOG = LoggerFactory.getLogger(ParquetInputCodec.class);

    private final Configuration configuration;

    public ParquetInputCodec() {
        configuration = new Configuration();
        configuration.setBoolean(READ_INT96_AS_FIXED, true);
    }

    @Override
    public void parse(final InputStream inputStream, final Consumer<Record<Event>> eventConsumer) throws IOException {
        Objects.requireNonNull(inputStream);
        Objects.requireNonNull(eventConsumer);

        final File tempFile = File.createTempFile(FILE_PREFIX, FILE_SUFFIX);
        Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        try {
            parseParquetFile(new LocalInputFile(tempFile), eventConsumer);
        } finally {
            Files.delete(tempFile.toPath());
        }

    }

    @Override
    public void parse(final InputFile inputFile, final DecompressionEngine decompressionEngine, final Consumer<Record<Event>> eventConsumer) throws IOException {
        Objects.requireNonNull(inputFile);
        Objects.requireNonNull(eventConsumer);
        parseParquetFile(inputFile, eventConsumer);
    }

    private void parseParquetFile(final InputFile inputFile, final Consumer<Record<Event>> eventConsumer) throws IOException {
        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(inputFile)
                .withConf(this.configuration)
                .build()) {
            GenericRecordJsonEncoder encoder = new GenericRecordJsonEncoder();
            GenericRecord record = null;

            while ((record = reader.read()) != null) {
                final String json = encoder.serialize(record);

                final JacksonEvent event = JacksonEvent.builder()
                        .withEventType(EVENT_TYPE)
                        .withData(json)
                        .build();

                eventConsumer.accept(new Record<>(event));
            }
        } catch (Exception parquetException){
            LOG.error("An exception occurred while parsing parquet InputStream  ", parquetException);
        }
    }

}
