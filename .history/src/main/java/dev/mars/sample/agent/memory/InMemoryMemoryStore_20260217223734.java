package dev.mars.sample.agent.memory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMemoryStore implements MemoryStore {

  private final Map<String, JsonObject> stateByCase = new ConcurrentHashMap<>();
  private final Map<String, JsonArray> logByCase = new ConcurrentHashMap<>();

  @Override
  public Future<JsonObject> load(String caseId) {
    JsonObject state = stateByCase.computeIfAbsent(caseId, k -> new JsonObject()
      .put("caseId", caseId)
      .put("createdAt", System.currentTimeMillis())
      .put("step", 0));
    return Future.succeededFuture(state.copy());
  }

  @Override
  public Future<Void> append(String caseId, JsonObject entry) {
    logByCase.compute(caseId, (k, existing) -> {
      JsonArray log = existing == null ? new JsonArray() : existing;
      log.add(entry);
      return log;
    });
    stateByCase.compute(caseId, (k, old) -> {
      JsonObject s = old == null ? new JsonObject() : old.copy();
      int step = s.getInteger("step", 0);
      s.put("step", step + 1);
      s.put("last", entry);
      s.put("updatedAt", System.currentTimeMillis());
      return s;
    });
    return Future.succeededFuture();
  }
}
