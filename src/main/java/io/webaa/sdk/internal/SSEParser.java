package io.webaa.sdk.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.webaa.sdk.model.AGUIEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Parses an SSE (Server-Sent Events) stream into AGUIEvent objects.
 * Each event is delimited by a blank line, and data lines start with "data: ".
 */
public class SSEParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Parse an SSE input stream, invoking the callback for each successfully parsed event.
     * Blocks until the stream ends or is interrupted.
     *
     * @param inputStream the SSE response body
     * @param onEvent     callback for each parsed AGUIEvent
     */
    public static void parse(InputStream inputStream, Consumer<AGUIEvent> onEvent) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder dataBuffer = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // Blank line = end of event
                    if (dataBuffer.length() > 0) {
                        String json = dataBuffer.toString();
                        dataBuffer.setLength(0);
                        try {
                            AGUIEvent event = MAPPER.readValue(json, AGUIEvent.class);
                            onEvent.accept(event);
                        } catch (Exception ignored) {
                            // Skip malformed JSON (same as JS SDK)
                        }
                    }
                } else if (line.startsWith("data: ")) {
                    dataBuffer.append(line.substring(6));
                }
                // Ignore other SSE fields (event:, id:, retry:, comments)
            }

            // Handle trailing data without final blank line
            if (dataBuffer.length() > 0) {
                try {
                    AGUIEvent event = MAPPER.readValue(dataBuffer.toString(), AGUIEvent.class);
                    onEvent.accept(event);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Shared ObjectMapper for JSON serialization across the SDK.
     */
    public static ObjectMapper objectMapper() {
        return MAPPER;
    }
}
