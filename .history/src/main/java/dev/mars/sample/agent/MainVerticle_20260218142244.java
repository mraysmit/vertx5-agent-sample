package dev.mars.sample.agent;

import dev.mars.sample.agent.api.HttpApiVerticle;
import dev.mars.sample.agent.config.HandlerFactory;
import dev.mars.sample.agent.config.LlmClientFactory;
import dev.mars.sample.agent.config.PipelineConfig;
import dev.mars.sample.agent.config.PipelineConfigLoader;
import dev.mars.sample.agent.config.ToolFactory;
import dev.mars.sample.agent.event.EventSinkVerticle;
import dev.mars.sample.agent.llm.LlmClient;
import dev.mars.sample.agent.memory.InMemoryMemoryStore;
import dev.mars.sample.agent.memory.MemoryStore;
import dev.mars.sample.agent.processor.DeterministicFailureProcessorVerticle;
import dev.mars.sample.agent.processor.FailureHandler;
import dev.mars.sample.agent.runner.AgentRunnerVerticle;
import dev.mars.sample.agent.tool.Tool;
import dev.mars.sample.agent.tool.ToolRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Application bootstrap verticle that loads {@link PipelineConfig} from
 * YAML, resolves all components via factories, and deploys the child
 * verticles in the correct order.
 *
 * <h2>Configuration</h2>
 * All pipeline wiring — event bus addresses, HTTP endpoint, schema
 * validation, handlers, tools, LLM client — is externalised to
 * {@code src/main/resources/pipeline.yaml}. See {@link PipelineConfig}
 * for the full YAML schema.
 *
 * <p>The config file path can be overridden via the system property
 * {@code pipeline.config}:
 * <pre>
 * java -Dpipeline.config=my-config.yaml -jar app.jar
 * </pre>
 *
 * <h2>Factories</h2>
 * Short aliases in YAML (e.g. {@code "lookup-enrich"}, {@code "raise-ticket"},
 * {@code "stub"}) are resolved to concrete classes by:
 * <ul>
 *   <li>{@link HandlerFactory} — deterministic failure handlers</li>
 *   <li>{@link ToolFactory} — agent tools</li>
 *   <li>{@link LlmClientFactory} — LLM client implementations</li>
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
 * @see PipelineConfig
 * @see PipelineConfigLoader
 */
public class MainVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(MainVerticle.class.getName());

  private final PipelineConfig pipelineConfig;
  private final MemoryStore memory;

  /**
   * Default constructor: loads configuration from the classpath YAML
   * file and uses an in-memory store.
   */
  public MainVerticle() {
    this(PipelineConfigLoader.load(), new InMemoryMemoryStore());
  }

  /**
   * Full constructor for dependency injection and testing.
   *
   * @param pipelineConfig the pipeline configuration
   * @param memory         the memory store implementation
   */
  public MainVerticle(PipelineConfig pipelineConfig, MemoryStore memory) {
    this.pipelineConfig = pipelineConfig;
    this.memory = memory;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    var cfg = pipelineConfig;
    LOG.info("Initialising pipeline from config: addresses=" + cfg.addresses()
        + ", llm=" + cfg.llm().type()
        + ", handlers=" + cfg.handlers().size()
        + ", tools=" + cfg.tools().size());

    // ── Resolve addresses ───────────────────────────────────────────
    String inbound = cfg.addresses().inbound();
    String agent   = cfg.addresses().agent();
    String events  = cfg.addresses().events();

    // ── Resolve LLM client via factory ──────────────────────────────
    LlmClient llm = LlmClientFactory.create(
        cfg.llm().type(), cfg.llm().params(), vertx);

    // ── Resolve tools via factory ───────────────────────────────────
    Tool[] toolArray = cfg.tools().stream()
        .map(tc -> ToolFactory.create(tc.type(), vertx, events))
        .toArray(Tool[]::new);
    var tools = ToolRegistry.of(toolArray);

    // ── Resolve handlers via factory ────────────────────────────────
    Map<String, FailureHandler> failureHandlers = new LinkedHashMap<>();
    for (var hc : cfg.handlers()) {
      failureHandlers.put(hc.reason(),
          HandlerFactory.create(hc.type(), hc.params(), vertx, events));
    }

    // ── Build Vert.x config for child verticles ─────────────────────
    JsonObject childConfig = config().copy()
        .put("http.port", cfg.http().port())
        .put("request.timeout.ms", cfg.http().requestTimeoutMs())
        .put("agent.max.steps", cfg.agent().maxSteps())
        .put("agent.timeout.ms", cfg.agent().timeoutMs());
    DeploymentOptions childOpts = new DeploymentOptions().setConfig(childConfig);

    // ── Deploy verticles in order ───────────────────────────────────
    vertx.deployVerticle(
          new DeterministicFailureProcessorVerticle(inbound, agent, failureHandlers), childOpts)
      .compose(id -> vertx.deployVerticle(
          new AgentRunnerVerticle(agent, llm, tools, memory, cfg.schema().caseIdField()), childOpts))
      .compose(id -> vertx.deployVerticle(new EventSinkVerticle(events)))
      .compose(id -> vertx.deployVerticle(
          new HttpApiVerticle(cfg.http().route(), inbound,
              cfg.schema().allowedFields(), cfg.schema().requiredFields()), childOpts))
      .onSuccess(id -> {
        LOG.info("Pipeline deployed successfully");
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }
}
