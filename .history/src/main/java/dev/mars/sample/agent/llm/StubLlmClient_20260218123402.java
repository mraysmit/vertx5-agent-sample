package dev.mars.sample.agent.llm;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A configurable {@link LlmClient} stub for local development and testing.
 *
 * <p>Instead of calling a real LLM, this implementation evaluates an
 * ordered list of {@link StubRule}s against the incoming event. The first
 * rule that returns a non-null command wins; if no rule matches, the
 * {@code fallback} rule is used.
 *
 * <p>Rules and the fallback are injected via the constructor, keeping
 * the routing logic fully separate from the client contract. Use the
 * convenience factory {@link #withDefaultRules()} to get a pre-configured
 * instance suitable for the trade-failure demo:
 * <ul>
 *   <li>Reason contains {@code "lei"} → {@code case.raiseTicket}
 *       (ReferenceData category).</li>
 *   <li>Anything else → {@code events.publish} (TradeEscalated).</li>
 * </ul>
 *
 * <h2>Extending</h2>
 * Build custom rules with {@link #keywordRule(String, String, Function)}
 * or implement {@link StubRule} directly for more complex matching.
 *
 * @see LlmClient
 * @see StubRule
 */
public class StubLlmClient implements LlmClient {

  private final List<StubRule> rules;
  private final StubRule fallback;

  /**
   * Creates a new stub client with the given rules and fallback.
   *
   * @param rules    ordered list of rules evaluated first-match-wins
   * @param fallback rule invoked when no other rule matches; must not
   *                 return {@code null}
   */
  public StubLlmClient(List<StubRule> rules, StubRule fallback) {
    this.rules = List.copyOf(rules);
    this.fallback = Objects.requireNonNull(fallback, "fallback rule must not be null");
  }

  /**
   * Factory that returns a stub pre-loaded with the default trade-failure
   * demo rules.
   *
   * @return a ready-to-use {@code StubLlmClient}
   */
  public static StubLlmClient withDefaultRules() {
    List<StubRule> rules = List.of(
      keywordRule("lei", "case.raiseTicket", event -> new JsonObject()
        .put("tradeId", event.getString("tradeId"))
        .put("category", "ReferenceData")
        .put("summary", "Counterparty LEI issue")
        .put("detail", "Failure reason: " + event.getString("reason")))
    );

    StubRule fallback = event -> new JsonObject()
      .put("intent", "CALL_TOOL")
      .put("tool", "events.publish")
      .put("args", new JsonObject()
        .put("type", "TradeEscalated")
        .put("tradeId", event.getString("tradeId"))
        .put("by", "agent")
        .put("reason", event.getString("reason")))
      .put("expected", "Escalation event published")
      .put("stop", true);

    return new StubLlmClient(rules, fallback);
  }

  /**
   * Convenience factory for a keyword-matching rule.
   *
   * <p>The rule matches when the event's {@code reason} field contains
   * the given {@code keyword} (case-insensitive). On match it returns a
   * {@code CALL_TOOL} command targeting the specified tool with
   * {@code stop: true}.
   *
   * @param keyword     substring to look for in the reason (case-insensitive)
   * @param toolName    the tool to invoke on match
   * @param argsBuilder builds the {@code args} object from the event
   * @return a reusable {@link StubRule}
   */
  public static StubRule keywordRule(String keyword, String toolName,
                                     Function<JsonObject, JsonObject> argsBuilder) {
    String lowerKeyword = keyword.toLowerCase();
    return event -> {
      String reason = event.getString("reason", "").toLowerCase();
      if (reason.contains(lowerKeyword)) {
        return new JsonObject()
          .put("intent", "CALL_TOOL")
          .put("tool", toolName)
          .put("args", argsBuilder.apply(event))
          .put("stop", true);
      }
      return null;
    };
  }

  @Override
  public Future<JsonObject> decideNext(JsonObject event, JsonObject state) {
    for (StubRule rule : rules) {
      JsonObject cmd = rule.tryMatch(event);
      if (cmd != null) {
        return Future.succeededFuture(cmd);
      }
    }
    return Future.succeededFuture(fallback.tryMatch(event));
  }
}
