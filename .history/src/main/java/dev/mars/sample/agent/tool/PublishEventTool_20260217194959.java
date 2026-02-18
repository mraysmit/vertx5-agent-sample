package dev.mars.sample.agent.tool;

import dev.mars.sample.agent.Addresses;
import dev.mars.sample.agent.runner.AgentContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class PublishEventTool implements Tool {

  private final Vertx vertx;

  public PublishEventTool(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public String name() {
    return "events.publish";
  }

  @Override
  public Future<JsonObject> invoke(JsonObject args, AgentContext ctx) {
    JsonObject event = args.copy()
      .put("correlationId", ctx.correlationId())
      .put("caseId", ctx.caseId());

    vertx.eventBus().publish(Addresses.EVENTS_OUT, event);
    return Future.succeededFuture(new JsonObject()
      .put("status", "published")
      .put("event", event));
  }
}
