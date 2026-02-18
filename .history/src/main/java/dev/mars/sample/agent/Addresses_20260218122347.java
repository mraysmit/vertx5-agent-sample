package dev.mars.sample.agent;

/**
 * Central registry of Vert.x {@link io.vertx.core.eventbus.EventBus} addresses
 * used for inter-verticle communication.
 *
 * <p>Every address is a dot-delimited string that acts as a logical channel name.
 * Consumer verticles listen on these addresses; producer verticles send or publish
 * messages to them. Centralising the constants here avoids magic strings scattered
 * across the codebase and makes the message-flow topology easy to audit.
 *
 * <h2>Message flow</h2>
 * <pre>
 *   HTTP POST /trade/failures
 *       │
 *       ▼
 *   {@link #TRADE_FAILURES}  (request/reply)
 *       │
 *       ├── known reason  → DeterministicFailureProcessorVerticle handles locally
 *       │                     publishes result to {@link #EVENTS_OUT}
 *       │
 *       └── unknown reason → forwarded to {@link #AGENT_REQUIRED}  (request/reply)
 *                               AgentRunnerVerticle invokes LLM + tools
 *                               tool results published to {@link #EVENTS_OUT}
 * </pre>
 *
 * @see dev.mars.sample.agent.processor.DeterministicFailureProcessorVerticle
 * @see dev.mars.sample.agent.runner.AgentRunnerVerticle
 * @see dev.mars.sample.agent.event.EventSinkVerticle
 */
public final class EventBusAddresses {

  private EventBusAddresses() {}

  /**
   * Address for inbound trade-failure events.
   *
   * <p><b>Pattern:</b> request/reply<br>
   * <b>Producer:</b> {@link dev.mars.sample.agent.api.HttpApiVerticle}<br>
   * <b>Consumer:</b> {@link dev.mars.sample.agent.processor.DeterministicFailureProcessorVerticle}
   *
   * <p>Expected message body: {@code JsonObject} with at least {@code tradeId} and {@code reason}.
   */
  public static final String TRADE_FAILURES = "trade.failures";

  /**
   * Address used to dispatch a failure event to the LLM-based agent when no
   * deterministic handler matches.
   *
   * <p><b>Pattern:</b> request/reply<br>
   * <b>Producer:</b> {@link dev.mars.sample.agent.processor.DeterministicFailureProcessorVerticle}<br>
   * <b>Consumer:</b> {@link dev.mars.sample.agent.runner.AgentRunnerVerticle}
   *
   * <p>Expected message body: the original failure {@code JsonObject}.
   */
  public static final String AGENT_REQUIRED = "agent.required";

  /**
   * Address for outbound domain events (fire-and-forget publish, not request/reply).
   *
   * <p><b>Pattern:</b> publish/subscribe<br>
   * <b>Producers:</b> any handler, tool, or verticle that produces a domain event
   *   (e.g. {@code TradeRepaired}, {@code TradeEscalated}, {@code TicketCreated})<br>
   * <b>Consumer:</b> {@link dev.mars.sample.agent.event.EventSinkVerticle}
   *
   * <p>Message body is a {@code JsonObject} whose {@code "type"} field identifies the
   * event kind.
   */
  public static final String EVENTS_OUT = "events.out";
}
