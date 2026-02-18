package dev.mars.sample.agent.tool;

import dev.mars.sample.agent.EventBusAddresses;
import dev.mars.sample.agent.runner.AgentContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

/**
 * Agent tool that creates a support ticket for a failure that requires
 * manual investigation.
 *
 * <p><b>Tool name:</b> {@code case.raiseTicket}<br>
 * <b>Side effects:</b>
 * <ul>
 *   <li>Generates a unique ticket ID ({@code TICKET-<UUID>}).</li>
 *   <li>Publishes a {@code TicketCreated} event to
 *       {@link EventBusAddresses#EVENTS_OUT} for observability.</li>
 * </ul>
 *
 * <h2>Expected args</h2>
 * <pre>
 * {
 *   "tradeId":   "T-200",
 *   "category":  "ReferenceData",
 *   "summary":   "Counterparty LEI issue",
 *   "detail":    "Failure reason: LEI not found in registry"
 * }
 * </pre>
 *
 * <h2>Return value</h2>
 * <pre>
 * {
 *   "status":   "created",
 *   "ticketId": "TICKET-...",
 *   "tradeId":  "T-200",
 *   "category": "ReferenceData",
 *   "summary":  "Counterparty LEI issue"
 * }
 * </pre>
 */
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
    vertx.eventBus().publish(EventBusAddresses.EVENTS_OUT, event);

    return Future.succeededFuture(result);
  }
}
