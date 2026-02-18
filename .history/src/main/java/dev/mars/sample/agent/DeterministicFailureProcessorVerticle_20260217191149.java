package dev.mars.sample.agent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeterministicFailureProcessorVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(DeterministicFailureProcessorVerticle.class.getName());
  private static final long AGENT_TIMEOUT_MS = 10_000;

  private static final Set<String> KNOWN_DETERMINISTIC = Set.of(
    "Missing ISIN",
    "Invalid Counterparty"
  );

  @Override
  public void start(Promise<Void> startPromise) {
    vertx.eventBus().consumer(Addresses.TRADE_FAILURES, msg -> {
      JsonObject event = (JsonObject) msg.body();
      String reason = event.getString("reason", "");

      if (KNOWN_DETERMINISTIC.contains(reason)) {
        LOG.info("Deterministic path for reason='" + reason + "'");
        handleDeterministically(event)
          .onSuccess(msg::reply)
          .onFailure(err -> {
            LOG.log(Level.SEVERE, "Deterministic handling failed", err);
            msg.fail(500, err.getMessage());
          });
      } else {
        LOG.info("Routing to agent for reason='" + reason + "'");
        DeliveryOptions opts = new DeliveryOptions().setSendTimeout(AGENT_TIMEOUT_MS);
        vertx.eventBus().request(Addresses.AGENT_REQUIRED, event, opts)
          .onSuccess(reply -> msg.reply(reply.body()))
          .onFailure(err -> {
            LOG.log(Level.SEVERE, "Agent dispatch failed", err);
            msg.fail(500, err.getMessage());
          });
      }
    });

    startPromise.complete();
  }

  private Future<JsonObject> handleDeterministically(JsonObject event) {
    String tradeId = event.getString("tradeId");
    String reason = event.getString("reason");

    if ("Missing ISIN".equals(reason)) {
      JsonObject repaired = new JsonObject()
        .put("type", "TradeRepaired")
        .put("tradeId", tradeId)
        .put("by", "deterministic-processor")
        .put("details", new JsonObject().put("action", "lookup+enrich ISIN"));

      vertx.eventBus().publish(Addresses.EVENTS_OUT, repaired);

      return Future.succeededFuture(new JsonObject()
        .put("status", "ok")
        .put("path", "deterministic")
        .put("resultEvent", repaired));
    }

    JsonObject escalated = new JsonObject()
      .put("type", "TradeEscalated")
      .put("tradeId", tradeId)
      .put("by", "deterministic-processor")
      .put("reason", reason);

    vertx.eventBus().publish(Addresses.EVENTS_OUT, escalated);

    return Future.succeededFuture(new JsonObject()
      .put("status", "ok")
      .put("path", "deterministic")
      .put("resultEvent", escalated));
  }
}
