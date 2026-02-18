package dev.mars.sample.agent.tool;

import dev.mars.sample.agent.EventBusAddresses;
import dev.mars.sample.agent.runner.AgentContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Agent tool that publishes an arbitrary domain event to the
 * {@link EventBusAddresses#EVENTS_OUT} channel.
 *
 * <p><b>Tool name:</b> {@code events.publish}<br>
 * <b>Side effects:</b> publishes the supplied args (enriched with
 * {@code correlationId} and {@code caseId} from the {@link AgentContext})
 * as a fire-and-forget event.
 *
 * <h2>Expected args</h2>
 * Any valid {@code JsonObject}; typically includes a {@code "type"} field
 * (e.g. {@code TradeEscalated}), a {@code tradeId}, and domain details.
 *
 * <h2>Return value</h2>
 * <pre>
 * {
 *   "status": "published",
 *   "event":  { ... the event that was published ... }
 * }
 * </pre>
 */
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

    vertx.eventBus().publish(EventBusAddresses.EVENTS_OUT, event);
    return Future.succeededFuture(new JsonObject()
      .put("status", "published")
      .put("event", event));
  }
}
