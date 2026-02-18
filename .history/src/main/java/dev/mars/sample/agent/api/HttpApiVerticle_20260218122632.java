package dev.mars.sample.agent.api;

import dev.mars.sample.agent.EventBusAddresses;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Set;
import java.util.logging.Logger;

/**
 * HTTP ingress verticle that exposes a REST API for submitting failure events
 * and a health-check endpoint.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /health} — returns {@code {"status":"UP"}}.</li>
 *   <li>{@code POST /trade/failures} — accepts a JSON body with at least
 *       {@code tradeId} and {@code reason}, sanitises the payload to a whitelist
 *       of allowed fields, then dispatches it over the event bus to
 *       {@link EventBusAddresses#TRADE_FAILURES} using request/reply.</li>
 * </ul>
 *
 * <h2>Configuration (Vert.x config)</h2>
 * <ul>
 *   <li>{@code http.port} — TCP port to listen on (default {@code 8080},
 *       use {@code 0} for a random port in tests).</li>
 * </ul>
 *
 * <h2>Input sanitisation</h2>
 * Only fields listed in {@link #ALLOWED_FIELDS} are forwarded downstream.
 * This prevents unexpected or malicious data from reaching the processor or
 * LLM agent.
 *
 * @see EventBusAddresses#TRADE_FAILURES
 */
public class HttpApiVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(HttpApiVerticle.class.getName());
  private static final Set<String> ALLOWED_FIELDS = Set.of("tradeId", "reason", "altIds");
  private static final long REQUEST_TIMEOUT_MS = 10_000;

  @Override
  public void start(Promise<Void> startPromise) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.get("/health").handler(ctx -> ctx.response()
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("status", "UP").encode()));

    router.post("/trade/failures").handler(ctx -> {
      JsonObject event = ctx.body().asJsonObject();
      if (event == null) {
        ctx.response().setStatusCode(400)
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("error", "Expected JSON body").encode());
        return;
      }
      String tradeId = event.getString("tradeId");
      String reason = event.getString("reason");
      if (tradeId == null || reason == null) {
        ctx.response().setStatusCode(400)
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("error", "tradeId and reason are required").encode());
        return;
      }

      // Whitelist known fields to prevent unexpected data reaching the agent/LLM
      JsonObject sanitized = new JsonObject();
      for (String field : ALLOWED_FIELDS) {
        if (event.containsKey(field)) {
          sanitized.put(field, event.getValue(field));
        }
      }

      DeliveryOptions opts = new DeliveryOptions().setSendTimeout(REQUEST_TIMEOUT_MS);
      vertx.eventBus().request(EventBusAddresses.TRADE_FAILURES, sanitized, opts)
        .onSuccess(reply -> ctx.response()
          .putHeader("content-type", "application/json")
          .end(((JsonObject) reply.body()).encodePrettily()))
        .onFailure(err -> {
          LOG.warning("Request failed for tradeId=" + tradeId + ": " + err.getMessage());
          ctx.response().setStatusCode(500)
            .putHeader("content-type", "application/json")
            .end(new JsonObject()
              .put("error", err.getMessage())
              .put("tradeId", tradeId).encode());
        });
    });

    int port = config().getInteger("http.port", 8080);
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .onSuccess(server -> {
        LOG.info("HTTP server started on port " + server.actualPort());
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }
}
