package dev.mars.sample.agent.llm;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A hard-coded {@link LlmClient} stub for local development and testing.
 *
 * <p>This implementation uses simple keyword matching on the failure reason
 * to decide which tool to call:
 * <ul>
 *   <li>If the reason contains {@code "lei"} (case-insensitive) →
 *       calls {@code case.raiseTicket} with a {@code ReferenceData} category.</li>
 *   <li>For all other reasons → calls {@code events.publish} to emit a
 *       {@code TradeEscalated} event.</li>
 * </ul>
 *
 * <p>Both branches set {@code stop: true}, so the agent loop always
 * completes in a single step. Replace this class with a real LLM
 * integration (e.g. OpenAI, Anthropic) for production use.
 *
 * @see LlmClient
 */
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
