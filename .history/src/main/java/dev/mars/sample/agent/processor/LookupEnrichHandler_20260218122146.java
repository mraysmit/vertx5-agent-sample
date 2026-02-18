package dev.mars.sample.agent.processor;

import dev.mars.sample.agent.Addresses;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Repairs a trade by looking up and enriching a missing identifier (e.g. ISIN).
 * Publishes a {@code TradeRepaired} event on success.
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

    vertx.eventBus().publish(Addresses.EVENTS_OUT, repaired);

    return Future.succeededFuture(repaired);
  }
}
