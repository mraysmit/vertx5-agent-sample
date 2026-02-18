package dev.mars.sample.agent.processor;

import dev.mars.sample.agent.Addresses;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Escalates a trade failure that is known but cannot be auto-repaired.
 * Publishes a {@code TradeEscalated} event.
 */
public class EscalateHandler implements FailureHandler {

  private final Vertx vertx;

  public EscalateHandler(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Future<JsonObject> handle(JsonObject event) {
    String tradeId = event.getString("tradeId");
    String reason = event.getString("reason");

    JsonObject escalated = new JsonObject()
      .put("type", "TradeEscalated")
      .put("tradeId", tradeId)
      .put("by", "deterministic-processor")
      .put("reason", reason);

    vertx.eventBus().publish(Addresses.EVENTS_OUT, escalated);

    return Future.succeededFuture(escalated);
  }
}
