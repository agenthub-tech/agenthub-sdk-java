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
     * Result of parsing an SSE stream.
     */
    public static class ParseResult {
        public final boolean receivedTerminal;

        public ParseResult(boolean receivedTerminal) {
            this.receivedTerminal = receivedTerminal;
        }
    }

    /**
     * Parse an SSE input stream, invoking the callback for each successfully parsed event.
     * Blocks until the stream ends or is interrupted.
     *
     * @param inputStream the SSE response body
     * @param onEvent     callback for each parsed AGUIEvent
     * @return ParseResult indicating whether a terminal event (RunFinished/Error) was received
     */
    public static ParseResult parse(InputStream inputStream, Consumer<AGUIEvent> onEvent) throws Exception {
        return parse(inputStream, onEvent, null);
    }

    /**
     * Parse an SSE input stream with an optional onData callback for heartbeat resets.
     *
     * @param inputStream the SSE response body
     * @param onEvent     callback for each parsed AGUIEvent
     * @param onData      called whenever data is received from the stream (for heartbeat reset); may be null
     * @return ParseResult indicating whether a terminal event (RunFinished/Error) was received
     */
    public static ParseResult parse(InputStream inputStream, Consumer<AGUIEvent> onEvent, Runnable onData) throws Exception {
        boolean receivedTerminal = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder dataBuffer = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (onData != null) {
                    onData.run();
                }

                if (line.isEmpty()) {
                    // Blank line = end of event
                    if (dataBuffer.length() > 0) {
                        String json = dataBuffer.toString();
                        dataBuffer.setLength(0);
                        try {
                            AGUIEvent event = MAPPER.readValue(json, AGUIEvent.class);
                            String type = event.getType();
                            if ("RunFinished".equals(type) || "Error".equals(type)) {
                                receivedTerminal = true;
                            }
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
                    String type = event.getType();
                    if ("RunFinished".equals(type) || "Error".equals(type)) {
                        receivedTerminal = true;
                    }
                    onEvent.accept(event);
                } catch (Exception ignored) {
                }
            }
        }
        return new ParseResult(receivedTerminal);
    }

    /**
     * Shared ObjectMapper for JSON serialization across the SDK.
     */
    public static ObjectMapper objectMapper() {
        return MAPPER;
    }
}
