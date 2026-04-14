package io.webaa.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webaa.sdk.cache.SkillCache;
import io.webaa.sdk.event.EventEmitter;
import io.webaa.sdk.exception.WebAAException;
import io.webaa.sdk.internal.SSEParser;
import io.webaa.sdk.model.*;
import io.webaa.sdk.skill.SkillExecutor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebAA Java SDK — headless AG-UI protocol client.
 * <p>
 * Compatible with Java 8+. Uses {@link HttpURLConnection} for HTTP.
 */
public class WebAASDK {

    private static final String SDK_VERSION = "0.1.0";
    private static final String DEFAULT_PROTOCOL_VERSION = "1.0.0";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 0; // no read timeout for SSE

    private static final Set<String> KNOWN_EVENT_TYPES;
    static {
        Set<String> s = new HashSet<String>();
        s.add("RunStarted"); s.add("RunFinished");
        s.add("TextMessageStart"); s.add("TextMessageDelta"); s.add("TextMessageEnd");
        s.add("ToolCallStart"); s.add("ToolCallDelta"); s.add("ToolCallEnd");
        s.add("SkillExecuteInstruction"); s.add("StateSnapshotEvent"); s.add("Error");
        KNOWN_EVENT_TYPES = Collections.unmodifiableSet(s);
    }

    private final ObjectMapper mapper = SSEParser.objectMapper();

    // State
    private String channelId;
    private String channelKey;
    private String accessToken;
    private volatile String runId;
    private volatile String threadId;
    private String userId;
    private String apiBase = "";
    private String protocolVersion = DEFAULT_PROTOCOL_VERSION;
    private ChannelConfig channelConfig;

    // Debug logging
    private boolean debug = false;

    // Connection lifecycle
    private int maxRetries = 3;
    private long retryDelayMs = 1000;
    private long heartbeatTimeoutMs = 45000;
    private final AtomicBoolean disconnected = new AtomicBoolean(false);

    // Skills
    private final ConcurrentHashMap<String, SkillDefinition> skills = new ConcurrentHashMap<String, SkillDefinition>();
    private final ConcurrentHashMap<String, SkillExecutor> localSkills = new ConcurrentHashMap<String, SkillExecutor>();

    // L1 Cache
    private final SkillCache skillCache = new SkillCache();

    // Callbacks
    private final List<Runnable> onIdentifyCallbacks = new CopyOnWriteArrayList<Runnable>();
    private final List<Runnable> onResetCallbacks = new CopyOnWriteArrayList<Runnable>();

    // Thread pool for async SSE processing
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "webaa-sdk-sse");
            t.setDaemon(true);
            return t;
        }
    });

    public WebAASDK() {}

    // ── Init ──

    public void init(InitOptions options) throws WebAAException {
        this.apiBase = options.getApiBase() != null ? options.getApiBase() : "";
        this.channelKey = options.getChannelKey();
        this.protocolVersion = options.getProtocolVersion() != null ? options.getProtocolVersion() : DEFAULT_PROTOCOL_VERSION;
        this.maxRetries = options.getMaxRetries();
        this.retryDelayMs = options.getRetryDelayMs();
        this.heartbeatTimeoutMs = options.getHeartbeatTimeoutMs();
        this.debug = options.isDebug();
        this.disconnected.set(false);

        log("init start | apiBase=%s channelKey=%s protocol=%s debug=%s", apiBase, channelKey, protocolVersion, debug);

        for (SkillDefinition skill : options.getSkills()) {
            skills.put(skill.getName(), skill);
        }

        acquireToken();
        log("token acquired");

        this.channelConfig = fetchChannelConfig();
        log("config fetched | channelConfig=%s", channelConfig != null ? "ok" : "null");

        if (!options.getSkills().isEmpty()) {
            registerSkills(options.getSkills());
            log("skills registered | count=%d channelId=%s", options.getSkills().size(), channelId);
        }

        if (options.getUser() != null) {
            identify(options.getUser());
            log("user identified | userId=%s", options.getUser().getUserId());
        }

        // Register default handlers for platform builtin SDK-side skills
        registerDefaultSkillHandlers();

        log("init complete");
    }

    /**
     * Register default local handlers for platform builtin skills (execution_mode: sdk).
     * Applications can override these by calling registerLocalSkill() after init().
     */
    private void registerDefaultSkillHandlers() {
        // dialog_skill — default: auto-confirm, log message
        if (!localSkills.containsKey("dialog_skill")) {
            localSkills.put("dialog_skill", new SkillExecutor() {
                @Override
                public java.util.concurrent.CompletableFuture<Map<String, Object>> execute(Map<String, Object> params) {
                    String action = params.get("action") != null ? params.get("action").toString() : "notify";
                    String message = params.get("message") != null ? params.get("message").toString() : "";
                    log("dialog_skill (default) | action=%s message=%s", action, message);

                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("action", action);
                    result.put("message", message);
                    if ("confirm".equals(action)) {
                        result.put("confirmed", true); // auto-confirm in headless mode
                    } else if ("input".equals(action)) {
                        result.put("value", ""); // no UI to collect input
                    }
                    result.put("success", true);
                    return java.util.concurrent.CompletableFuture.completedFuture(result);
                }
            });
        }

        // wait_skill — default: sleep for the requested duration
        if (!localSkills.containsKey("wait_skill")) {
            localSkills.put("wait_skill", new SkillExecutor() {
                @Override
                public java.util.concurrent.CompletableFuture<Map<String, Object>> execute(Map<String, Object> params) {
                    String condition = params.get("condition") != null ? params.get("condition").toString() : "duration";
                    int timeoutMs = 5000;
                    if (params.get("timeout_ms") instanceof Number) {
                        timeoutMs = ((Number) params.get("timeout_ms")).intValue();
                    }
                    log("wait_skill (default) | condition=%s timeout=%dms", condition, timeoutMs);

                    if ("duration".equals(condition)) {
                        try { Thread.sleep(Math.min(timeoutMs, 30000)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                    // For element_visible/element_hidden: no DOM in headless, just return success
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("success", true);
                    result.put("condition", condition);
                    return java.util.concurrent.CompletableFuture.completedFuture(result);
                }
            });
        }

        // http_skill — default: execute HTTP request
        if (!localSkills.containsKey("http_skill")) {
            localSkills.put("http_skill", new SkillExecutor() {
                @Override
                public java.util.concurrent.CompletableFuture<Map<String, Object>> execute(Map<String, Object> params) {
                    String method = params.get("method") != null ? params.get("method").toString() : "GET";
                    String url = params.get("url") != null ? params.get("url").toString() : "";
                    log("http_skill (default) | %s %s", method, url);

                    Map<String, Object> result = new HashMap<String, Object>();
                    try {
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                        conn.setRequestMethod(method);
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);

                        // Set headers if provided
                        if (params.get("headers") instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, String> headers = (Map<String, String>) params.get("headers");
                            for (Map.Entry<String, String> h : headers.entrySet()) {
                                conn.setRequestProperty(h.getKey(), h.getValue());
                            }
                        }

                        // Send body for POST/PUT/PATCH
                        if (params.get("body") != null && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
                            conn.setDoOutput(true);
                            conn.setRequestProperty("Content-Type", "application/json");
                            try (java.io.OutputStream os = conn.getOutputStream()) {
                                os.write(params.get("body").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                        }

                        int status = conn.getResponseCode();
                        result.put("success", status >= 200 && status < 300);
                        result.put("status", status);

                        // Read response
                        java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
                        if (is != null) {
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            char[] buf = new char[4096];
                            int n;
                            while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
                            reader.close();
                            result.put("data", sb.toString());
                        }
                    } catch (Exception e) {
                        result.put("success", false);
                        result.put("error", e.getMessage());
                    }
                    return java.util.concurrent.CompletableFuture.completedFuture(result);
                }
            });
        }
    }

    private void acquireToken() {
        try {
            Map<String, String> body = new HashMap<String, String>();
            body.put("channel_key", channelKey);
            HttpResult resp = postJson(apiBase + "/api/auth/token", body, null);
            if (resp.status != 200) {
                String detail = extractDetail(resp.body, resp.status);
                throw new WebAAException(resp.status, "Token acquisition failed (" + resp.status + "): " + detail);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(resp.body, Map.class);
            this.accessToken = (String) data.get("access_token");
        } catch (WebAAException e) {
            throw e;
        } catch (Exception e) {
            throw new WebAAException("Token acquisition failed", e);
        }
    }

    private ChannelConfig fetchChannelConfig() {
        try {
            HttpResult resp = doGet(apiBase + "/api/config");
            if (resp.status != 200) return null;
            return mapper.readValue(resp.body, ChannelConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void registerSkills(List<SkillDefinition> skillList) {
        try {
            List<Map<String, Object>> skillsMeta = new ArrayList<Map<String, Object>>();
            for (SkillDefinition s : skillList) {
                Map<String, Object> meta = new LinkedHashMap<String, Object>();
                meta.put("name", s.getName());
                meta.put("schema", s.getSchema());
                meta.put("prompt_injection", s.getPromptInjection());
                meta.put("execution_mode", s.getExecutionMode());
                skillsMeta.add(meta);
            }
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("skills", skillsMeta);
            body.put("protocol_version", protocolVersion);

            HttpResult resp = postJson(apiBase + "/api/sdk/register", body, accessToken);
            if (resp.status != 200) {
                String detail = extractDetail(resp.body, resp.status);
                throw new WebAAException(resp.status, "Register failed (" + resp.status + "): " + detail);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(resp.body, Map.class);
            this.channelId = (String) data.get("channel_id");
        } catch (WebAAException e) {
            throw e;
        } catch (Exception e) {
            throw new WebAAException("Register failed", e);
        }
    }

    // ── Run ──

    public EventEmitter run(RunOptions options) {
        EventEmitter emitter = new EventEmitter();
        disconnected.set(false);

        if (options.getRunId() != null) this.runId = options.getRunId();
        if (options.getThreadId() != null) this.threadId = options.getThreadId();

        log("run | userInput=\"%s\" runId=%s threadId=%s", truncate(options.getUserInput(), 80), options.getRunId(), options.getThreadId());

        sseExecutor.submit(new Runnable() {
            @Override
            public void run() {
                startSSEStream(options, emitter, 0, false);
            }
        });
        return emitter;
    }

    private void startSSEStream(RunOptions options, EventEmitter emitter, int retryCount, boolean isRetryAfterRefresh) {
        if (disconnected.get()) return;

        log("sse-connect | retry=%d/%d", retryCount, maxRetries);

        try {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("user_input", options.getUserInput());
            body.put("context", options.getContext() != null ? options.getContext() : Collections.emptyMap());

            // L1: inject skill cache
            Map<String, Object> cacheCtx = skillCache.buildContext();
            if (cacheCtx != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) body.get("context");
                Map<String, Object> mutableCtx = new HashMap<String, Object>(ctx);
                mutableCtx.put("skill_cache", cacheCtx);
                body.put("context", mutableCtx);
            }

            if (options.getRunId() != null) body.put("run_id", options.getRunId());
            if (options.getToolResult() != null) body.put("tool_result", options.getToolResult());
            if (userId != null) body.put("user_id", userId);
            if (options.getThreadId() != null) {
                body.put("thread_id", options.getThreadId());
            } else if (threadId != null) {
                body.put("thread_id", threadId);
            }

            String jsonBody = mapper.writeValueAsString(body);

            HttpURLConnection conn = openConnection(apiBase + "/api/agent/run");
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setDoOutput(true);
            conn.setReadTimeout(0); // SSE — no read timeout
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();

            if (status != 200) {
                String respBody = readFully(conn.getErrorStream());
                String detail = extractDetail(respBody, status);
                log("sse-error | status=%d detail=%s", status, truncate(detail, 120));
                WebAAException error = new WebAAException(status, "Run failed (" + status + "): " + detail);

                if (status == 401 && !isRetryAfterRefresh) {
                    try {
                        acquireToken();
                        startSSEStream(options, emitter, retryCount, true);
                        return;
                    } catch (Exception refreshErr) {
                        emitError(emitter, refreshErr);
                        return;
                    }
                }

                if (status >= 400 && status < 500) {
                    emitError(emitter, error);
                    return;
                }

                if (retryCount < maxRetries && !disconnected.get()) {
                    scheduleReconnect(options, emitter, retryCount);
                    return;
                }

                emitError(emitter, error);
                return;
            }

            parseSSEStream(conn.getInputStream(), emitter, options, retryCount);

        } catch (Exception e) {
            if (disconnected.get()) return;
            log("sse-exception | %s", e.getMessage());
            if (retryCount < maxRetries && !disconnected.get()) {
                scheduleReconnect(options, emitter, retryCount);
                return;
            }
            emitError(emitter, e);
        }
    }

    private void parseSSEStream(InputStream stream, EventEmitter emitter, RunOptions options, int retryCount) {
        try {
            SSEParser.parse(stream, new java.util.function.Consumer<AGUIEvent>() {
                @Override
                public void accept(AGUIEvent event) {
                    if (disconnected.get()) return;

                    if ("RunStarted".equals(event.getType())) {
                        String rid = event.payloadString("run_id");
                        if (rid != null) runId = rid;
                        String tid = event.payloadString("thread_id");
                        if (tid != null) threadId = tid;
                        log("event RunStarted | runId=%s threadId=%s", rid, tid);
                    }

                    if (!KNOWN_EVENT_TYPES.contains(event.getType())) {
                        log("event unknown (skipped) | type=%s", event.getType());
                        return;
                    }

                    emitter.emit(event.getType(), event);
                    emitter.emit("event", event);

                    if ("RunFinished".equals(event.getType())) {
                        log("event RunFinished");
                        emitter.emit("done", event);
                        return;
                    }
                    if ("Error".equals(event.getType())) {
                        log("event Error | %s", event.payloadString("message"));
                        emitter.emit("error", event);
                        return;
                    }

                    if ("SkillExecuteInstruction".equals(event.getType())) {
                        log("event SkillExecuteInstruction | skill=%s toolCallId=%s", event.payloadString("skill_name"), event.payloadString("tool_call_id"));
                        handleSkillExecution(event, emitter);
                    }
                }
            });
        } catch (Exception e) {
            if (disconnected.get()) return;
            if (retryCount < maxRetries && !disconnected.get()) {
                scheduleReconnect(options, emitter, retryCount);
                return;
            }
            emitError(emitter, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSkillExecution(AGUIEvent event, EventEmitter emitter) {
        String skillName = event.payloadString("skill_name");
        Map<String, Object> params = event.getPayload().containsKey("params")
                ? (Map<String, Object>) event.getPayload().get("params")
                : Collections.<String, Object>emptyMap();
        String toolCallId = event.payloadString("tool_call_id");

        SkillExecutor skillExecutor = null;
        SkillDefinition skillDef = skills.get(skillName);
        if (skillDef != null) {
            skillExecutor = skillDef.getExecutor();
        }
        if (skillExecutor == null) {
            skillExecutor = localSkills.get(skillName);
        }

        Map<String, Object> toolResult;
        if (skillExecutor != null) {
            try {
                log("skill-exec | skill=%s params=%s", skillName, params);
                Map<String, Object> result = skillExecutor.execute(params).get(60, TimeUnit.SECONDS);
                toolResult = newMap("tool_call_id", toolCallId, "result", result);
                log("skill-exec ok | skill=%s", skillName);
                if (skillDef != null) {
                    skillCache.put(skillName, result, skillDef.getCachePolicy());
                }
            } catch (Exception e) {
                String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                log("skill-exec error | skill=%s error=%s", skillName, msg);
                Map<String, Object> errResult = new HashMap<String, Object>();
                errResult.put("error", msg != null ? msg : "Unknown error");
                toolResult = newMap("tool_call_id", toolCallId, "result", errResult);
            }
        } else {
            log("skill-exec miss | skill=%s not registered", skillName);
            Map<String, Object> errResult = new HashMap<String, Object>();
            errResult.put("error", "Skill '" + skillName + "' not registered locally");
            toolResult = newMap("tool_call_id", toolCallId, "result", errResult);
        }

        // Emit synthetic ToolCallEnd
        Map<String, Object> endPayload = new HashMap<String, Object>();
        endPayload.put("tool_call_id", toolCallId);
        endPayload.put("tool_name", skillName);
        endPayload.put("result", toolResult.get("result"));
        AGUIEvent toolCallEnd = new AGUIEvent("ToolCallEnd", endPayload,
                event.getProtocolVersion(), new Date().toString());
        emitter.emit("ToolCallEnd", toolCallEnd);
        emitter.emit("event", toolCallEnd);

        // Follow-up run
        RunOptions resumeOptions = RunOptions.builder("")
                .runId(this.runId)
                .toolResult(toolResult)
                .build();
        startSSEStream(resumeOptions, emitter, 0, false);
    }

    private void scheduleReconnect(RunOptions options, EventEmitter emitter, int retryCount) {
        if (disconnected.get()) return;
        log("reconnect scheduled | retry=%d delay=%dms", retryCount + 1, retryDelayMs);
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!disconnected.get()) {
            startSSEStream(options, emitter, retryCount + 1, false);
        }
    }

    private void emitError(EventEmitter emitter, Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("message", msg);
        AGUIEvent errorEvent = new AGUIEvent("Error", payload, protocolVersion, new Date().toString());
        emitter.emit("error", errorEvent);
    }

    // ── Identify ──

    public void identify(UserIdentity user) {
        this.userId = user.getUserId();
        if (accessToken == null) return;

        try {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("user_id", user.getUserId());
            body.put("name", user.getName());
            body.put("avatar", user.getAvatar());
            body.put("metadata", user.getMetadata() != null ? user.getMetadata() : Collections.emptyMap());

            HttpResult resp = postJson(apiBase + "/api/sdk/identify", body, accessToken);
            if (resp.status != 200) {
                String detail = extractDetail(resp.body, resp.status);
                throw new WebAAException(resp.status, "Identify failed (" + resp.status + "): " + detail);
            }

            for (Runnable cb : onIdentifyCallbacks) {
                try { cb.run(); } catch (Exception ignored) {}
            }
        } catch (WebAAException e) {
            throw e;
        } catch (Exception e) {
            throw new WebAAException("Identify failed", e);
        }
    }

    public void onIdentify(Runnable callback) { onIdentifyCallbacks.add(callback); }

    // ── Thread Management ──

    public Map<String, Object> createThread(String title) {
        if (userId == null) throw new WebAAException("Call identify() before creating threads");
        if (accessToken == null) throw new WebAAException("SDK not initialized");

        try {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("user_id", userId);
            body.put("title", title);

            HttpResult resp = postJson(apiBase + "/api/sdk/threads", body, accessToken);
            if (resp.status != 200) {
                String detail = extractDetail(resp.body, resp.status);
                throw new WebAAException(resp.status, "Create thread failed: " + detail);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(resp.body, Map.class);
            this.threadId = (String) data.get("id");
            return data;
        } catch (WebAAException e) {
            throw e;
        } catch (Exception e) {
            throw new WebAAException("Create thread failed", e);
        }
    }

    public String newThread() {
        disconnect();
        this.runId = null;
        this.threadId = null;
        this.disconnected.set(false);
        skillCache.clear();

        if (userId != null && accessToken != null) {
            Map<String, Object> thread = createThread(null);
            return (String) thread.get("id");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> switchThread(String threadId) {
        if (accessToken == null) throw new WebAAException("SDK not initialized");
        disconnect();
        this.runId = null;
        this.threadId = threadId;

        try {
            HttpResult resp = doGet(apiBase + "/api/sdk/threads/" + threadId);
            if (resp.status != 200) {
                String detail = extractDetail(resp.body, resp.status);
                throw new WebAAException(resp.status, "Switch thread failed: " + detail);
            }
            return mapper.readValue(resp.body, Map.class);
        } catch (WebAAException e) {
            throw e;
        } catch (Exception e) {
            throw new WebAAException("Switch thread failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listThreads(int limit, int offset) {
        if (userId == null || accessToken == null) return Collections.emptyList();
        try {
            String url = apiBase + "/api/sdk/threads?user_id=" + userId + "&limit=" + limit + "&offset=" + offset;
            HttpResult resp = doGet(url);
            if (resp.status != 200) return Collections.emptyList();
            return mapper.readValue(resp.body, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> listThreads() { return listThreads(20, 0); }

    // ── Local Skills ──

    public void registerLocalSkill(String name, SkillExecutor executor) {
        localSkills.put(name, executor);
    }

    // ── Connection Lifecycle ──

    public void disconnect() {
        log("disconnect");
        disconnected.set(true);
    }

    public void reset() {
        log("reset");
        disconnect();
        this.userId = null;
        this.runId = null;
        this.threadId = null;
        this.disconnected.set(false);
        skillCache.clear();
        for (Runnable cb : onResetCallbacks) {
            try { cb.run(); } catch (Exception ignored) {}
        }
    }

    public void onReset(Runnable callback) { onResetCallbacks.add(callback); }

    // ── L1 Cache ──

    public void invalidateCache(String eventName) { skillCache.invalidate(eventName); }

    // ── Getters ──

    public String getVersion() { return SDK_VERSION; }
    public String getChannelId() { return channelId; }
    public String getRunId() { return runId; }
    public String getThreadId() { return threadId; }
    public String getUserId() { return userId; }
    public String getAccessToken() { return accessToken; }
    public String getApiBase() { return apiBase; }
    public ChannelConfig getChannelConfig() { return channelConfig; }

    // ── HTTP Helpers (HttpURLConnection, Java 8 compatible) ──

    private static class HttpResult {
        final int status;
        final String body;
        HttpResult(int status, String body) { this.status = status; this.body = body; }
    }

    HttpURLConnection openConnection(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        return conn;
    }

    private HttpResult postJson(String url, Object body, String token) throws Exception {
        String json = mapper.writeValueAsString(body);
        HttpURLConnection conn = openConnection(url);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        conn.setDoOutput(true);
        conn.setReadTimeout(CONNECT_TIMEOUT_MS);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String respBody = readFully(is);
        return new HttpResult(status, respBody);
    }

    private HttpResult doGet(String url) throws Exception {
        HttpURLConnection conn = openConnection(url);
        conn.setRequestMethod("GET");
        if (accessToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        }
        conn.setReadTimeout(CONNECT_TIMEOUT_MS);
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String respBody = readFully(is);
        return new HttpResult(status, respBody);
    }

    private static String readFully(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractDetail(String responseBody, int statusCode) {
        try {
            Map<String, Object> data = mapper.readValue(responseBody, Map.class);
            Object detail = data.get("detail");
            if (detail != null) return detail.toString();
            Object message = data.get("message");
            if (message != null) return message.toString();
        } catch (Exception ignored) {}
        return "HTTP " + statusCode;
    }

    // Java 8 compatible map factory
    private static Map<String, Object> newMap(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    // ── Debug Logging ──

    private void log(String format, Object... args) {
        if (!debug) return;
        String msg = String.format(format, args);
        System.out.println("[WebAA SDK] " + msg);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
