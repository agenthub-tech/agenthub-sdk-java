package io.webaa.sdk.event;

import io.webaa.sdk.model.AGUIEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight event emitter for AG-UI events.
 * Thread-safe — handlers can be registered from any thread.
 */
public class EventEmitter {

    private final Map<String, List<Consumer<AGUIEvent>>> listeners = new ConcurrentHashMap<>();

    /**
     * Register a listener for a specific event type.
     * Known types: RunStarted, RunFinished, TextMessageStart, TextMessageDelta,
     * TextMessageEnd, ToolCallStart, ToolCallEnd, SkillExecuteInstruction,
     * StateSnapshotEvent, Error.
     * Special types: "event" (all events), "done" (RunFinished), "error" (Error or exception).
     */
    public EventEmitter on(String eventType, Consumer<AGUIEvent> handler) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        return this;
    }

    /**
     * Remove a specific listener.
     */
    public EventEmitter off(String eventType, Consumer<AGUIEvent> handler) {
        List<Consumer<AGUIEvent>> list = listeners.get(eventType);
        if (list != null) {
            list.remove(handler);
        }
        return this;
    }

    /**
     * Remove all listeners for a given event type, or all listeners if eventType is null.
     */
    public EventEmitter removeAllListeners(String eventType) {
        if (eventType == null) {
            listeners.clear();
        } else {
            listeners.remove(eventType);
        }
        return this;
    }

    /**
     * Emit an event to all registered listeners.
     */
    public void emit(String eventType, AGUIEvent event) {
        List<Consumer<AGUIEvent>> list = listeners.get(eventType);
        if (list != null) {
            for (Consumer<AGUIEvent> handler : list) {
                try {
                    handler.accept(event);
                } catch (Exception e) {
                    // Swallow listener exceptions to avoid breaking the stream
                }
            }
        }
    }
}
