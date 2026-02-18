package dev.mars.sample.agent.llm;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface LlmClient {
  Future<JsonObject> decideNext(JsonObject event, JsonObject state);
}
