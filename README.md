# AgentHub Java SDK

Java 8+ SDK for Agent Hub.

## Install

```xml
<dependency>
    <groupId>io.agenthub</groupId>
    <artifactId>agenthub-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

```java
import io.webaa.sdk.AgentHubSDK;
import io.webaa.sdk.event.EventEmitter;
import io.webaa.sdk.model.InitOptions;
import io.webaa.sdk.model.RunOptions;

public class Example {
    public static void main(String[] args) throws Exception {
        AgentHubSDK sdk = new AgentHubSDK();

        sdk.init(
            InitOptions.builder("your-channel-key")
                .apiBase("https://your-agenthub-server")
                .build()
        );

        EventEmitter emitter = sdk.run(
            RunOptions.builder("杭州明天天气如何？")
                .webSearchEnabled(true)
                .build()
        );

        emitter.on("TextMessageDelta", event -> {
            Object delta = event.getPayload().get("delta");
            System.out.print(delta == null ? "" : delta.toString());
        });

        emitter.on("done", event -> {
            System.out.println("\n任务完成");
        });
    }
}
```

## Skill Provider mode

```java
sdk.init(
    InitOptions.builder("your-channel-key")
        .apiBase("https://your-agenthub-server")
        .runtimeMode("skill_provider")
        .providerId("stable-provider-id")
        .skills(skills)
        .build()
);
```

The default `runtimeMode("agent")` preserves the existing `run()` behavior.

## Notes

- Maven coordinates are `io.agenthub:agenthub-sdk`, Java package is `io.webaa.sdk`.
- `channelKey` is required.
- Web search is off by default. Set `webSearchEnabled(true)` explicitly, and ensure the channel allows web search.
- SDK-side skills must use `executionMode("sdk")`.

- `SkillExecuteInstruction` is auto-dispatched and auto-resumed by the SDK.
