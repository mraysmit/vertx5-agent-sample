package dev.mars.sample.agent;

import io.vertx.core.Vertx;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ToolRegistry {
  private ToolRegistry() {}

  public static Map<String, Tool> defaultTools(Vertx vertx) {
    List<Tool> tools = List.of(
      new PublishEventTool(vertx),
      new RaiseTicketTool(vertx)
    );
    return tools.stream().collect(Collectors.toMap(Tool::name, t -> t));
  }
}
