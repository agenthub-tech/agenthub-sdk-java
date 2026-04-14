package io.webaa.sdk.cache;

import io.webaa.sdk.model.SkillCachePolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L1 SDK Auto Cache — caches skill execution results for injection into run context.
 */
public class SkillCache {

    public enum Freshness { FRESH, STALE, EXPIRED }

    private static class Entry {
        Map<String, Object> result;
        Freshness freshness;
        long timestamp;
        SkillCachePolicy policy;

        Entry(Map<String, Object> result, Freshness freshness, long timestamp, SkillCachePolicy policy) {
            this.result = result;
            this.freshness = freshness;
            this.timestamp = timestamp;
            this.policy = policy;
        }
    }

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * Cache a skill result according to its cache policy.
     */
    public void put(String skillName, Map<String, Object> result, SkillCachePolicy policy) {
        if (policy == null || !policy.isEnabled()) return;

        switch (policy.getMode()) {
            case SNAPSHOT:
                cache.put(skillName, new Entry(result, Freshness.FRESH, System.currentTimeMillis(), policy));
                break;
            case APPEND:
                Entry existing = cache.get(skillName);
                if (existing != null) {
                    Map<String, Object> merged = new HashMap<>(existing.result);
                    merged.putAll(result);
                    cache.put(skillName, new Entry(merged, Freshness.FRESH, System.currentTimeMillis(), policy));
                } else {
                    cache.put(skillName, new Entry(result, Freshness.FRESH, System.currentTimeMillis(), policy));
                }
                break;
            case NONE:
            default:
                break;
        }
    }

    /**
     * Build the skill_cache context map for injection into run requests.
     * Only includes fresh/stale entries.
     */
    public Map<String, Object> buildContext() {
        refreshFreshness();
        Map<String, Object> ctx = new HashMap<>();
        for (Map.Entry<String, Entry> e : cache.entrySet()) {
            if (e.getValue().freshness == Freshness.EXPIRED) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("result", e.getValue().result);
            item.put("freshness", e.getValue().freshness.name().toLowerCase());
            ctx.put(e.getKey(), item);
        }
        return ctx.isEmpty() ? null : ctx;
    }

    /**
     * Invalidate cache entries matching the given event name.
     */
    public void invalidate(String eventName) {
        for (Entry entry : cache.values()) {
            if (entry.freshness == Freshness.EXPIRED) continue;
            if (entry.policy.getInvalidateOn().contains(eventName)) {
                entry.freshness = Freshness.STALE;
            }
        }
    }

    /**
     * Clear all cache entries.
     */
    public void clear() {
        cache.clear();
    }

    private void refreshFreshness() {
        long now = System.currentTimeMillis();
        for (Entry entry : cache.values()) {
            if (entry.freshness == Freshness.EXPIRED) continue;
            if (entry.policy.getTtlMs() > 0 && (now - entry.timestamp) > entry.policy.getTtlMs()) {
                entry.freshness = Freshness.EXPIRED;
            }
        }
    }
}
