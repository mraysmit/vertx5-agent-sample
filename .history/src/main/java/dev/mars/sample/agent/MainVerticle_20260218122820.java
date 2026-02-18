package dev.mars.sample.agent;

import dev.mars.sample.agent.api.HttpApiVerticle;
import dev.mars.sample.agent.event.EventSinkVerticle;
import dev.mars.sample.agent.llm.StubLlmClient;
import dev.mars.sample.agent.memory.InMemoryMemoryStore;
import dev.mars.sample.agent.processor.DeterministicFailureProcessorVerticle;
import dev.mars.sample.agent.processor.EscalateHandler;
import dev.mars.sample.agent.processor.FailureHandler;
import dev.mars.sample.agent.processor.LookupEnrichHandler;
import dev.mars.sample.agent.runner.AgentRunnerVerticle;
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
 * <h2>Component wiring</h2>
 * <ul>
 *   <li><b>MemoryStore</b> — {@link InMemoryMemoryStore} (swap for a
 *       persistent implementation in production).</li>
 *   <li><b>LlmClient</b> — {@link StubLlmClient} (replace with a real
 *       OpenAI / Anthropic / etc. client).</li>
 *   <li><b>Tools</b> — built via {@link ToolRegistry#defaultTools}.</li>
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
 * This verticle forwards its own Vert.x config to child verticles.
 * See each child's Javadoc for supported keys (e.g. {@code http.port},
 * {@code agent.timeout.ms}).
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    var memory = new InMemoryMemoryStore();
    var llm = new StubLlmClient();
    var tools = ToolRegistry.defaultTools(vertx);

    // Register deterministic handlers by failure reason
    Map<String, FailureHandler> failureHandlers = Map.of(
      "Missing ISIN",        new LookupEnrichHandler(vertx, "ISIN"),
      "Invalid Counterparty", new EscalateHandler(vertx)
    );

    // Forward our config to HttpApiVerticle so http.port can be overridden (e.g., 0 for tests)
    DeploymentOptions httpOpts = new DeploymentOptions().setConfig(config());

    vertx.deployVerticle(new DeterministicFailureProcessorVerticle(failureHandlers))
      .compose(id -> vertx.deployVerticle(new AgentRunnerVerticle(llm, tools, memory)))
      .compose(id -> vertx.deployVerticle(new EventSinkVerticle()))
      .compose(id -> vertx.deployVerticle(new HttpApiVerticle(), httpOpts))
      .onSuccess(id -> startPromise.complete())
      .onFailure(startPromise::fail);
  }
}
