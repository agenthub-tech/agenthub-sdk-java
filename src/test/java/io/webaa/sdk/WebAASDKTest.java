package io.webaa.sdk;

import io.webaa.sdk.cache.SkillCache;
import io.webaa.sdk.event.EventEmitter;
import io.webaa.sdk.internal.SSEParser;
import io.webaa.sdk.model.*;
import io.webaa.sdk.skill.SkillExecutor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WebAASDKTest {

    // ── Java 8 compatible helpers ──

    private static Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((String) kvs[i], kvs[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> emptyMap() {
        return Collections.<String, Object>emptyMap();
    }

    // ── SSEParser Tests ──

    @Test
    void sseParser_parsesMultipleEvents() throws Exception {
        String sse = sseEvent("RunStarted", mapOf("run_id", "s-1"))
                + sseEvent("TextMessageDelta", mapOf("delta", "hello"))
                + sseEvent("RunFinished", emptyMap());

        List<AGUIEvent> events = new ArrayList<AGUIEvent>();
        SSEParser.parse(toStream(sse), new Consumer<AGUIEvent>() {
            @Override public void accept(AGUIEvent e) { events.add(e); }
        });

        assertEquals(3, events.size());
        assertEquals("RunStarted", events.get(0).getType());
        assertEquals("s-1", events.get(0).payloadString("run_id"));
        assertEquals("TextMessageDelta", events.get(1).getType());
        assertEquals("RunFinished", events.get(2).getType());
    }

    @Test
    void sseParser_skipsMalformedJson() throws Exception {
        String sse = sseEvent("RunStarted", mapOf("run_id", "s-1"))
                + "data: {not valid json}\n\n"
                + sseEvent("RunFinished", emptyMap());

        List<AGUIEvent> events = new ArrayList<AGUIEvent>();
        SSEParser.parse(toStream(sse), new Consumer<AGUIEvent>() {
            @Override public void accept(AGUIEvent e) { events.add(e); }
        });

        assertEquals(2, events.size());
        assertEquals("RunStarted", events.get(0).getType());
        assertEquals("RunFinished", events.get(1).getType());
    }

    @Test
    void sseParser_ignoresNonDataLines() throws Exception {
        String sse = ": this is a comment\n"
                + "event: custom\n"
                + sseEvent("RunFinished", emptyMap());

        List<AGUIEvent> events = new ArrayList<AGUIEvent>();
        SSEParser.parse(toStream(sse), new Consumer<AGUIEvent>() {
            @Override public void accept(AGUIEvent e) { events.add(e); }
        });

        assertEquals(1, events.size());
        assertEquals("RunFinished", events.get(0).getType());
    }

    // ── EventEmitter Tests ──

    @Test
    void eventEmitter_emitsToRegisteredListeners() {
        EventEmitter emitter = new EventEmitter();
        final List<String> received = new ArrayList<String>();

        emitter.on("RunStarted", new Consumer<AGUIEvent>() {
            @Override public void accept(AGUIEvent e) { received.add(e.getType()); }
        });
        emitter.on("event", new Consumer<AGUIEvent>() {
            @Override public void accept(AGUIEvent e) { received.add("generic:" + e.getType()); }
        });

        AGUIEvent event = new AGUIEvent("RunStarted", emptyMap(), "1.0.0", "now");
        emitter.emit("RunStarted", event);
        emitter.emit("event", event);

        assertEquals(Arrays.asList("RunStarted", "generic:RunStarted"), received);
    }

    @Test
    void eventEmitter_offRemovesListener() {
        EventEmitter emitter = new EventEmitter();
        final List<String> received = new ArrayList<String>();
        Consumer<AGUIEvent> handler = new Consumer<AGUIEvent>() {
            @Override public void accept(AGUIEvent e) { received.add(e.getType()); }
        };

        emitter.on("test", handler);
        emitter.emit("test", new AGUIEvent("test", emptyMap(), "1.0.0", "now"));
        assertEquals(1, received.size());

        emitter.off("test", handler);
        emitter.emit("test", new AGUIEvent("test", emptyMap(), "1.0.0", "now"));
        assertEquals(1, received.size());
    }

    @Test
    void eventEmitter_swallowsListenerExceptions() {
        EventEmitter emitter = new EventEmitter();
        final List<String> received = new ArrayList<String>();

        emitter.on("test", new Consumer<AGUIEvent>() {
            @Override public void accept(AGUIEvent e) { throw new RuntimeException("boom"); }
        });
        emitter.on("test", new Consumer<AGUIEvent>() {
            @Override public void accept(AGUIEvent e) { received.add("ok"); }
        });

        emitter.emit("test", new AGUIEvent("test", emptyMap(), "1.0.0", "now"));
        assertEquals(Arrays.asList("ok"), received);
    }

    // ── SkillCache Tests ──

    @Test
    void skillCache_snapshotMode() {
        SkillCache cache = new SkillCache();
        SkillCachePolicy policy = new SkillCachePolicy(true, 0, SkillCachePolicy.Mode.SNAPSHOT, null);

        cache.put("page_skill", mapOf("elements", Arrays.asList("a", "b")), policy);
        Map<String, Object> ctx = cache.buildContext();

        assertNotNull(ctx);
        assertTrue(ctx.containsKey("page_skill"));
    }

    @Test
    void skillCache_appendMode() {
        SkillCache cache = new SkillCache();
        SkillCachePolicy policy = new SkillCachePolicy(true, 0, SkillCachePolicy.Mode.APPEND, null);

        cache.put("clip", mapOf("a", 1), policy);
        cache.put("clip", mapOf("b", 2), policy);

        Map<String, Object> ctx = cache.buildContext();
        assertNotNull(ctx);
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) ctx.get("clip");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) entry.get("result");
        assertEquals(1, result.get("a"));
        assertEquals(2, result.get("b"));
    }

    @Test
    void skillCache_ttlExpiration() throws Exception {
        SkillCache cache = new SkillCache();
        SkillCachePolicy policy = new SkillCachePolicy(true, 50, SkillCachePolicy.Mode.SNAPSHOT, null);

        cache.put("test", mapOf("x", 1), policy);
        assertNotNull(cache.buildContext());

        Thread.sleep(100);
        assertNull(cache.buildContext());
    }

    @Test
    void skillCache_invalidation() {
        SkillCache cache = new SkillCache();
        SkillCachePolicy policy = new SkillCachePolicy(true, 0, SkillCachePolicy.Mode.SNAPSHOT,
                Arrays.asList("urlchange"));

        cache.put("page", mapOf("x", 1), policy);
        cache.invalidate("urlchange");

        Map<String, Object> ctx = cache.buildContext();
        assertNotNull(ctx);
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) ctx.get("page");
        assertEquals("stale", entry.get("freshness"));
    }

    @Test
    void skillCache_disabledPolicyDoesNotCache() {
        SkillCache cache = new SkillCache();
        cache.put("test", mapOf("x", 1), SkillCachePolicy.disabled());
        assertNull(cache.buildContext());
    }

    // ── Model Tests ──

    @Test
    void initOptions_builderDefaults() {
        InitOptions opts = InitOptions.builder("key-123").build();
        assertEquals("key-123", opts.getChannelKey());
        assertEquals("", opts.getApiBase());
        assertEquals("1.0.0", opts.getProtocolVersion());
        assertEquals(3, opts.getMaxRetries());
        assertEquals(1000, opts.getRetryDelayMs());
        assertEquals(45000, opts.getHeartbeatTimeoutMs());
        assertTrue(opts.getSkills().isEmpty());
        assertNull(opts.getUser());
    }

    @Test
    void skillDefinition_builderDefaults() {
        SkillDefinition skill = SkillDefinition.builder(
                "test", mapOf("type", "function"), new SkillExecutor() {
                    @Override
                    public CompletableFuture<Map<String, Object>> execute(Map<String, Object> params) {
                        return CompletableFuture.completedFuture(emptyMap());
                    }
                }
        ).build();

        assertEquals("test", skill.getName());
        assertEquals("sdk", skill.getExecutionMode());
        assertNull(skill.getPromptInjection());
        assertFalse(skill.getCachePolicy().isEnabled());
    }

    @Test
    void runOptions_builderWithAllFields() {
        RunOptions opts = RunOptions.builder("hello")
                .context(mapOf("url", "https://example.com"))
                .runId("r-1")
                .threadId("t-1")
                .toolResult(mapOf("tool_call_id", "tc-1"))
                .build();

        assertEquals("hello", opts.getUserInput());
        assertEquals("r-1", opts.getRunId());
        assertEquals("t-1", opts.getThreadId());
        assertNotNull(opts.getToolResult());
    }

    @Test
    void aguiEvent_payloadString() {
        AGUIEvent event = new AGUIEvent("RunStarted", mapOf("run_id", "s-1", "count", 42), "1.0.0", "now");
        assertEquals("s-1", event.payloadString("run_id"));
        assertEquals("42", event.payloadString("count"));
        assertNull(event.payloadString("missing"));
    }

    // ── Helpers ──

    private static String sseEvent(String type, Map<String, Object> payload) {
        try {
            Map<String, Object> event = new LinkedHashMap<String, Object>();
            event.put("type", type);
            event.put("payload", payload);
            event.put("protocol_version", "1.0.0");
            event.put("timestamp", "2026-01-01T00:00:00Z");
            return "data: " + SSEParser.objectMapper().writeValueAsString(event) + "\n\n";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream toStream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
