package dev.mars.sample.agent.llm;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class StubLlmClient implements LlmClient {

  @Override
  public Future<JsonObject> decideNext(JsonObject event, JsonObject state) {
    String reason = event.getString("reason", "").toLowerCase();
    String tradeId = event.getString("tradeId");

    if (reason.contains("lei")) {
      return Future.succeededFuture(new JsonObject()
        .put("intent", "CALL_TOOL")
        .put("tool", "case.raiseTicket")
        .put("args", new JsonObject()
          .put("tradeId", tradeId)
          .put("category", "ReferenceData")
          .put("summary", "Counterparty LEI issue")
          .put("detail", "Failure reason: " + event.getString("reason")))
        .put("expected", "Ticket created and linked to case")
        .put("stop", true));
    }

    return Future.succeededFuture(new JsonObject()
      .put("intent", "CALL_TOOL")
      .put("tool", "events.publish")
      .put("args", new JsonObject()
        .put("type", "TradeEscalated")
        .put("tradeId", tradeId)
        .put("by", "agent")
        .put("reason", event.getString("reason")))
      .put("expected", "Escalation event published")
      .put("stop", true));
  }
}
