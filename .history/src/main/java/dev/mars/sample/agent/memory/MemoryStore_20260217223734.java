package dev.mars.sample.agent.memory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface MemoryStore {
  Future<JsonObject> load(String caseId);
  Future<Void> append(String caseId, JsonObject entry);
}
