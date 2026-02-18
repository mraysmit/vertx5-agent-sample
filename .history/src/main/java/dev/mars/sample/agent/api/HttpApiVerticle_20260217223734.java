package dev.mars.sample.agent.api;

import dev.mars.sample.agent.Addresses;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Set;
import java.util.logging.Logger;

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
      vertx.eventBus().request(Addresses.TRADE_FAILURES, sanitized, opts)
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
