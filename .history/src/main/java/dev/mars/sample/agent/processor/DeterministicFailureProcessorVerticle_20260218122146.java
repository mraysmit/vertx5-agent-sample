package dev.mars.sample.agent.processor;

import dev.mars.sample.agent.Addresses;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes inbound failure events: known reasons are handled by a registered
 * {@link FailureHandler}; unknown reasons are forwarded to the agent.
 *
 * <p>Configuration (Vert.x config):
 * <ul>
 *   <li>{@code agent.timeout.ms} — timeout for agent dispatch (default 10 000)</li>
 * </ul>
 */
public class DeterministicFailureProcessorVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(DeterministicFailureProcessorVerticle.class.getName());
  private static final long DEFAULT_AGENT_TIMEOUT_MS = 10_000;

  private final Map<String, FailureHandler> handlers;

  /**
   * @param handlers a map of failure-reason string → handler strategy.
   *                 Reasons not present in this map are routed to the agent.
   */
  public DeterministicFailureProcessorVerticle(Map<String, FailureHandler> handlers) {
    this.handlers = Map.copyOf(handlers);  // defensive immutable copy
  }

  @Override
  public void start(Promise<Void> startPromise) {
    long agentTimeout = config().getLong("agent.timeout.ms", DEFAULT_AGENT_TIMEOUT_MS);

    vertx.eventBus().consumer(Addresses.TRADE_FAILURES, msg -> {
      JsonObject event = (JsonObject) msg.body();
      String reason = event.getString("reason", "");

      FailureHandler handler = handlers.get(reason);
      if (handler != null) {
        LOG.info("Deterministic path for reason='" + reason + "'");
        handler.handle(event)
          .map(resultEvent -> new JsonObject()
            .put("status", "ok")
            .put("path", "deterministic")
            .put("resultEvent", resultEvent))
          .onSuccess(msg::reply)
          .onFailure(err -> {
            LOG.log(Level.SEVERE, "Deterministic handling failed", err);
            msg.fail(500, err.getMessage());
          });
      } else {
        LOG.info("Routing to agent for reason='" + reason + "'");
        DeliveryOptions opts = new DeliveryOptions().setSendTimeout(agentTimeout);
        vertx.eventBus().request(Addresses.AGENT_REQUIRED, event, opts)
          .onSuccess(reply -> msg.reply(reply.body()))
          .onFailure(err -> {
            LOG.log(Level.SEVERE, "Agent dispatch failed", err);
            msg.fail(500, err.getMessage());
          });
      }
    });

    LOG.info("Processor started with " + handlers.size() + " deterministic handler(s): " + handlers.keySet());
    startPromise.complete();
  }
}
