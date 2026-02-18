package dev.mars.sample.agent;

import dev.mars.sample.agent.api.HttpApiVerticle;
import dev.mars.sample.agent.event.EventSinkVerticle;
import dev.mars.sample.agent.llm.LlmClient;
import dev.mars.sample.agent.llm.StubRuleLoader;
import dev.mars.sample.agent.handler.TradeFailureRuleLoader;
import dev.mars.sample.agent.memory.InMemoryMemoryStore;
import dev.mars.sample.agent.memory.MemoryStore;
import dev.mars.sample.agent.handler.EscalateHandler;
import dev.mars.sample.agent.handler.LookupEnrichHandler;
import dev.mars.sample.agent.processor.DeterministicFailureProcessorVerticle;
import dev.mars.sample.agent.processor.FailureHandler;
import dev.mars.sample.agent.runner.AgentRunnerVerticle;
import dev.mars.sample.agent.tool.PublishEventTool;
import dev.mars.sample.agent.tool.RaiseTicketTool;
import dev.mars.sample.agent.tool.ToolRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;

import java.util.Map;
import java.util.Set;

/**
 * Application bootstrap verticle that wires together all components and
 * deploys the child verticles in the correct order.
 *
 * <h2>Dependency injection</h2>
 * Core infrastructure components — {@link MemoryStore} and {@link LlmClient}
 * — are accepted via the constructor so they can be swapped without editing
 * this class (e.g. Redis-backed memory, real OpenAI client). The no-arg
 * constructor wires the <b>trade-failure demo</b> use case using a
 * {@link StubRuleLoader}:
 * <ul>
 *   <li><b>MemoryStore</b> — {@link InMemoryMemoryStore}.</li>
 *   <li><b>LlmClient</b> — built from the rules loaded by
 *       {@link TradeFailureRuleLoader}.</li>
 * </ul>
 *
 * <h2>Component wiring</h2>
 * All domain-specific behaviour is plugged in here so it is easy to see
 * at a glance which rules, handlers, and tools are active:
 * <ul>
 *   <li><b>Rule loader</b> — a {@link StubRuleLoader} implementation
 *       that assembles the agent rules for the current use case (swap
 *       the loader to switch use cases).</li>
 *   <li><b>Tools</b> — built via {@link ToolRegistry#of}; extend
 *       with {@link ToolRegistry#withAdditional} for custom tools.</li>
 *   <li><b>Failure handlers</b> — deterministic strategies keyed by
 *       failure reason string and injected into the processor verticle.</li>
 * </ul>
 *
 * <h2>Deployment order</h2>
 * Verticles are deployed sequentially so that event-bus consumers are
 * registered before producers start sending messages:
 * <ol>
 *   <li>{@link DeterministicFailureProcessorVerticle}</li>
 *   <li>{@link AgentRunnerVerticle}</li>
 *   <li>{@link EventSinkVerticle}</li>
 *   <li>{@link HttpApiVerticle} (last, so HTTP traffic only arrives once
 *       the pipeline is ready)</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * This verticle forwards its own Vert.x config to all child verticles.
 * See each child's Javadoc for supported keys (e.g. {@code http.port},
 * {@code agent.max.steps}, {@code agent.timeout.ms},
 * {@code request.timeout.ms}).
 */
public class MainVerticle extends AbstractVerticle {

  private final MemoryStore memory;
  private final LlmClient llm;

  /**
   * Default constructor wiring the trade-failure demo use case.
   *
   * <p>Uses {@link TradeFailureRuleLoader} to load the agent rules.
   * To switch to a different use case, replace the loader.
   */
  public MainVerticle() {
    this(new InMemoryMemoryStore(), new TradeFailureRuleLoader().load().toClient());
  }

  /**
   * Full constructor for dependency injection.
   *
   * @param memory the memory store implementation
   * @param llm    the LLM client implementation
   */
  public MainVerticle(MemoryStore memory, LlmClient llm) {
    this.memory = memory;
    this.llm = llm;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    // Event bus addresses — single place to change if addresses evolve
    String tradeFailures = EventBusAddresses.TRADE_FAILURES;
    String agentRequired = EventBusAddresses.AGENT_REQUIRED;
    String eventsOut     = EventBusAddresses.EVENTS_OUT;

    var tools = ToolRegistry.of(
      new PublishEventTool(vertx, eventsOut),
      new RaiseTicketTool(vertx, eventsOut)
    );

    // Register deterministic handlers by failure reason
    Map<String, FailureHandler> failureHandlers = Map.of(
      "Missing ISIN",        new LookupEnrichHandler(vertx, eventsOut, "ISIN"),
      "Invalid Counterparty", new EscalateHandler(vertx, eventsOut)
    );

    // Forward config to all child verticles so keys like http.port,
    // agent.max.steps, agent.timeout.ms, request.timeout.ms are available
    DeploymentOptions childOpts = new DeploymentOptions().setConfig(config());

    // Schema configuration for the HTTP endpoint
    String caseIdField = "tradeId";
    Set<String> allowedFields = Set.of("tradeId", "reason", "altIds");
    Set<String> requiredFields = Set.of("tradeId", "reason");

    vertx.deployVerticle(
        new DeterministicFailureProcessorVerticle(tradeFailures, agentRequired, failureHandlers), childOpts)
      .compose(id -> vertx.deployVerticle(
        new AgentRunnerVerticle(agentRequired, llm, tools, memory, caseIdField), childOpts))
      .compose(id -> vertx.deployVerticle(new EventSinkVerticle(eventsOut)))
      .compose(id -> vertx.deployVerticle(
        new HttpApiVerticle("/trade/failures", tradeFailures,
          allowedFields, requiredFields), childOpts))
      .onSuccess(id -> startPromise.complete())
      .onFailure(startPromise::fail);
  }
}
