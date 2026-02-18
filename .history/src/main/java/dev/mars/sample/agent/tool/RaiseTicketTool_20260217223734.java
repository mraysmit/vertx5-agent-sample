package dev.mars.sample.agent.tool;

import dev.mars.sample.agent.Addresses;
import dev.mars.sample.agent.runner.AgentContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class RaiseTicketTool implements Tool {

  private final Vertx vertx;

  public RaiseTicketTool(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public String name() {
    return "case.raiseTicket";
  }

  @Override
  public Future<JsonObject> invoke(JsonObject args, AgentContext ctx) {
    String ticketId = "TICKET-" + UUID.randomUUID();

    JsonObject result = new JsonObject()
      .put("status", "created")
      .put("ticketId", ticketId)
      .put("tradeId", args.getString("tradeId"))
      .put("category", args.getString("category"))
      .put("summary", args.getString("summary"));

    JsonObject event = new JsonObject()
      .put("type", "TicketCreated")
      .put("ticketId", ticketId)
      .put("tradeId", args.getString("tradeId"))
      .put("category", args.getString("category"))
      .put("correlationId", ctx.correlationId())
      .put("caseId", ctx.caseId());
    vertx.eventBus().publish(Addresses.EVENTS_OUT, event);

    return Future.succeededFuture(result);
  }
}
