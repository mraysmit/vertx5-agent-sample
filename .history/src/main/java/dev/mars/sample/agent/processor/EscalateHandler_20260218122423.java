package dev.mars.sample.agent.processor;

import dev.mars.sample.agent.EventBusAddresses;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * {@link FailureHandler} that escalates a trade failure which is recognised but
 * cannot be automatically repaired — for example, an invalid counterparty.
 *
 * <h2>Side effects</h2>
 * Publishes a {@code TradeEscalated} event to {@link EventBusAddresses#EVENTS_OUT}
 * containing the trade ID and the original failure reason.
 *
 * <h2>Example result</h2>
 * <pre>
 * {
 *   "type":    "TradeEscalated",
 *   "tradeId": "T-100",
 *   "by":      "deterministic-processor",
 *   "reason":  "Invalid Counterparty"
 * }
 * </pre>
 *
 * @see FailureHandler
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

    vertx.eventBus().publish(EventBusAddresses.EVENTS_OUT, escalated);

    return Future.succeededFuture(escalated);
  }
}
