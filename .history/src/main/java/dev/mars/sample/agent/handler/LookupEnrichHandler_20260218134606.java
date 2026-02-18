package dev.mars.sample.agent.handler;

import dev.mars.sample.agent.EventBusAddresses;
import dev.mars.sample.agent.processor.FailureHandler;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * {@link FailureHandler} that repairs a trade by looking up and enriching a
 * missing identifier.
 *
 * <p>The identifier name (e.g. {@code "ISIN"}, {@code "CUSIP"}) is supplied at
 * construction time, making this handler reusable for different lookup+enrich
 * scenarios without code changes.
 *
 * <h2>Side effects</h2>
 * Publishes a {@code TradeRepaired} event to {@link EventBusAddresses#EVENTS_OUT}
 * with the repair action detail.
 *
 * <h2>Example result</h2>
 * <pre>
 * {
 *   "type":    "TradeRepaired",
 *   "tradeId": "T-100",
 *   "by":      "deterministic-processor",
 *   "details": { "action": "lookup+enrich ISIN" }
 * }
 * </pre>
 *
 * @see FailureHandler
 */
public class LookupEnrichHandler implements FailureHandler {

  private final Vertx vertx;
  private final String identifierName;

  public LookupEnrichHandler(Vertx vertx, String identifierName) {
    this.vertx = vertx;
    this.identifierName = identifierName;
  }

  @Override
  public Future<JsonObject> handle(JsonObject event) {
    String tradeId = event.getString("tradeId");

    JsonObject repaired = new JsonObject()
      .put("type", "TradeRepaired")
      .put("tradeId", tradeId)
      .put("by", "deterministic-processor")
      .put("details", new JsonObject().put("action", "lookup+enrich " + identifierName));

    vertx.eventBus().publish(EventBusAddresses.EVENTS_OUT, repaired);

    return Future.succeededFuture(repaired);
  }
}
