# Agenthub Java SDK

Agenthub 平台的 Java SDK，基于 AG-UI 协议实现 Agent 通信。

## 安装

Maven:

```xml
<dependency>
    <groupId>io.agenthub</groupId>
    <artifactId>agenthub-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 快速开始

```java
import io.agenthub.sdk.WebAASDK;
import io.agenthub.sdk.InitOptions;
import io.agenthub.sdk.SkillDefinition;
import io.agenthub.sdk.RunOptions;

public class Example {
    public static void main(String[] args) throws Exception {
        WebAASDK sdk = new WebAASDK();
        
        sdk.init(InitOptions.builder()
            .channelKey("your-channel-key")
            .apiBase("https://your-agenthub-server")
            .skill(SkillDefinition.builder()
                .name("my_skill")
                .schema(Map.of(
                    "type", "function",
                    "function", Map.of(
                        "name", "my_skill",
                        "description", "执行自定义操作",
                        "parameters", Map.of("type", "object", "properties", Map.of())
                    )
                ))
                .executionMode("sdk")
                .execute(params -> {
                    // 你的业务逻辑
                    return Map.of("success", true);
                })
                .build())
            .build());
        
        // 发送消息
        EventEmitter emitter = sdk.run(RunOptions.builder()
            .userInput("帮我完成任务")
            .build());
        
        emitter.on("TextMessageDelta", event -> {
            System.out.print(event.getPayload().get("delta"));
        });
        
        emitter.on("done", event -> {
            System.out.println("\n任务完成");
        });
    }
}
```

## 核心功能

- Skill 注册与执行
- SSE 事件流处理
- 自动重连与心跳
- 会话管理（Thread）
- 用户身份识别

## API

### `init(options)`

初始化 SDK，获取 Token 并注册 Skills。

### `run(options)`

发送用户消息，返回 EventEmitter 接收 AG-UI 事件流。

### `identify(user)`

标识当前用户身份。

### `registerLocalSkill(name, execute)`

注册本地 Skill 处理器（不上报后端）。

### `disconnect()`

断开连接，停止当前任务。

## 事件类型

| 事件 | 说明 |
|------|------|
| `RunStarted` | 任务开始 |
| `TextMessageDelta` | 流式文本输出 |
| `ToolCallStart/End` | 工具调用开始/结束 |
| `SkillExecuteInstruction` | SDK 端 Skill 执行指令 |
| `RunFinished` | 任务完成 |
| `Error` | 错误 |

## 系统要求

- Java 8+
- Jackson 2.17+

## License

MIT
