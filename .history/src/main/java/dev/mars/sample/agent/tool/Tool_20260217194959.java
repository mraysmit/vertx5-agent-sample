package dev.mars.sample.agent.tool;

import dev.mars.sample.agent.runner.AgentContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface Tool {
  String name();
  Future<JsonObject> invoke(JsonObject args, AgentContext ctx);
}
