package io.embedai;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static io.embedai.EmbedAiChat.*;

/** 无网络回归：验证多工具、多次发送消息、主动结束、错误与兼容入口。通过 main 运行。 */
public final class ConversationEventsTest {
  private static JsonObject call(String name, JsonObject args) {
    return object("id", UUID.randomUUID().toString(), "type", "function",
        "function", object("name", name, "arguments", args.toString()));
  }
  private static JsonObject batch(JsonObject... calls) {
    return object("role", "assistant", "content", "", "tool_calls", array((Object[]) calls));
  }
  private static Engine engine(JsonObject... replies) {
    var queue = new ArrayDeque<>(List.of(replies));
    Engine engine = new Engine((url, headers, body) -> {
      if (queue.isEmpty()) throw new AssertionError("Unexpected model request after end");
      return object("choices", array(object("message", queue.remove())));
    });
    engine.config.model = "test";
    engine.allowTools = true;
    return engine;
  }
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
  public static void main(String[] args) throws Exception {
    Engine engine = engine(
        batch(call("assistant_reply", object("text", "开始查询")), call("a_query", object()), call("b_query", object())),
        batch(call("assistant_reply", object("text", "继续处理")), call("a_query", object())),
        batch(call("assistant_reply", object("text", "已完成")), call("assistant_end", object()), call("a_query", object())));
    AtomicInteger operations = new AtomicInteger();
    for (String name : List.of("a_query", "b_query")) {
      engine.registerTool(new Tool(name, "test", object("type", "object"), a -> object("count", operations.incrementAndGet())));
    }
    List<JsonObject> events = new ArrayList<>();
    check(engine.send("执行任务", events::add).equals("已完成"), "Final reply");
    check(operations.get() == 3, "No operation may run after end");
    check(events.stream().filter(e -> e.get("type").getAsString().equals("assistant")).count() == 3, "Three delivered messages");
    check(events.get(events.size()-1).get("type").getAsString().equals("done"), "Explicit completion");
    JsonObject archive = engine.exportHistory();
    check(archive.getAsJsonArray("messages").size() == 4, "Archive contains every reply");
    check(!archive.getAsJsonArray("audit").get(7).getAsJsonObject().getAsJsonObject("result").get("ok").getAsBoolean(), "Skipped operation audited");

    Engine legacy = engine(object("content", "普通回复"));
    legacy.allowTools = false;
    check(legacy.send("你好").equals("普通回复"), "Legacy send without tools");

    Engine failing = engine(batch(call("assistant_reply", object("text", "已收到"))));
    List<JsonObject> partial = new ArrayList<>();
    try {
      failing.send("继续", partial::add);
      throw new AssertionError("Expected model failure");
    } catch (AssertionError expected) {
      check(expected.getMessage().equals("Unexpected model request after end"), "Expected failure");
    }
    check(failing.exportHistory().getAsJsonArray("messages").size() == 2, "Partial reply survives failure");
    check(partial.stream().noneMatch(e -> e.get("type").getAsString().equals("done")), "Failure is not completion");
    System.out.println("ConversationEventsTest passed");
  }
}
