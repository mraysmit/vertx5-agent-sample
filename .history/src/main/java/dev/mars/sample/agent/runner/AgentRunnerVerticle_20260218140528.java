package dev.mars.sample.agent.runner;

import dev.mars.sample.agent.llm.LlmClient;
import dev.mars.sample.agent.memory.MemoryStore;
import dev.mars.sample.agent.tool.Tool;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LLM-based agent verticle that listens on {@link EventBusAddresses#AGENT_REQUIRED}
 * and performs an iterative decide → execute → record loop until the LLM signals
 * completion or the safety step-limit is reached.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Receives a failure event that no deterministic handler could match.</li>
 *   <li>Extracts the case identifier using the configurable {@code caseIdField}
 *       (e.g. {@code "tradeId"}, {@code "settlementId"}).</li>
 *   <li>Loads the case's prior state from the {@link MemoryStore}.</li>
 *   <li>Calls {@link LlmClient#decideNext} to get a structured command
 *       ({@code intent}, {@code tool}, {@code args}, {@code stop}).</li>
 *   <li>Executes the command by looking up the named {@link Tool} in the
 *       allow-listed tool map.</li>
 *   <li>Appends the step to the memory store for audit and context.</li>
 *   <li>If {@code stop == false}, loops back to step 4 (up to
 *       {@code agent.max.steps}).</li>
 * </ol>
 *
 * <h2>Configuration (Vert.x config)</h2>
 * <ul>
 *   <li>{@code agent.max.steps} — maximum number of iterative steps before
 *       a safety stop is triggered (default {@code 5}).</li>
 * </ul>
 *
 * <h2>Safety</h2>
 * The configurable step limit prevents runaway loops when the LLM never
 * sets {@code stop: true}. Only tools present in the injected allow-list
 * can be invoked — anything else is rejected with a clear error.
 *
 * @see LlmClient
 * @see Tool
 * @see MemoryStore
 * @see AgentContext
 */
public class AgentRunnerVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(AgentRunnerVerticle.class.getName());
  private static final int DEFAULT_MAX_STEPS = 5;

  private final String listenAddress;
  private final LlmClient llm;
  private final Map<String, Tool> tools;
  private final MemoryStore memory;
  private final String caseIdField;

  private int maxSteps;

  /**
   * Creates a new agent runner verticle.
   *
   * @param listenAddress the event bus address to consume agent requests from
   * @param llm           the LLM client used to decide each step
   * @param tools         allow-listed tool map (name → tool)
   * @param memory        the memory store for case state
   * @param caseIdField   the JSON field name used to extract the case
   *                      identifier from incoming events (e.g. {@code "tradeId"})
   */
  public AgentRunnerVerticle(String listenAddress, LlmClient llm,
                             Map<String, Tool> tools,
                             MemoryStore memory, String caseIdField) {
    this.listenAddress = listenAddress;
    this.llm = llm;
    this.tools = tools;
    this.memory = memory;
    this.caseIdField = caseIdField;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    maxSteps = config().getInteger("agent.max.steps", DEFAULT_MAX_STEPS);

    vertx.eventBus().consumer(listenAddress, msg -> {
      JsonObject event = (JsonObject) msg.body();
      String caseId = event.getString(caseIdField);
      String corrId = event.getString("correlationId", UUID.randomUUID().toString());

      LOG.info("Agent invoked for case=" + caseId + " correlationId=" + corrId);

      memory.load(caseId)
        .compose(state -> runLoop(event, new AgentContext(corrId, caseId, state), 0))
        .onSuccess(msg::reply)
        .onFailure(err -> {
          LOG.log(Level.SEVERE, "Agent failed for case=" + caseId, err);
          msg.fail(500, err.getMessage());
        });
    });

    startPromise.complete();
  }

  private Future<JsonObject> runLoop(JsonObject event, AgentContext ctx, int step) {
    if (step >= maxSteps) {
      LOG.warning("Step limit reached for case=" + ctx.caseId());
      return Future.succeededFuture(new JsonObject()
        .put("status", "error")
        .put("path", "agent")
        .put("reason", "Step limit reached (safety stop)")
        .put(caseIdField, ctx.caseId()));
    }

    LOG.fine("Agent step " + step + " for case=" + ctx.caseId());

    return llm.decideNext(event, ctx.state())
      .compose(cmd -> executeCommand(cmd, ctx))
      .compose(outcome -> memory.append(ctx.caseId(), new JsonObject()
          .put("step", step)
          .put("command", outcome.getJsonObject("command"))
          .put("toolResult", outcome.getJsonObject("toolResult"))
          .put("at", System.currentTimeMillis()))
        .map(v -> outcome))
      .compose(outcome -> {
        if (!outcome.getBoolean("stop", true)) {
          return runLoop(event, ctx, step + 1);
        }
        return Future.succeededFuture(new JsonObject()
          .put("status", "ok")
          .put("path", "agent")
          .put("result", outcome.getJsonObject("toolResult"))
          .put(caseIdField, ctx.caseId()));
      });
  }

  private Future<JsonObject> executeCommand(JsonObject cmd, AgentContext ctx) {
    String intent = cmd.getString("intent", "");
    String toolName = cmd.getString("tool", "");
    JsonObject args = cmd.getJsonObject("args", new JsonObject());

    if (!"CALL_TOOL".equals(intent)) {
      return Future.failedFuture("Unsupported intent: " + intent);
    }

    Tool tool = tools.get(toolName);
    if (tool == null) {
      return Future.failedFuture("Tool not allowlisted: " + toolName);
    }

    return tool.invoke(args, ctx).map(result -> new JsonObject()
      .put("stop", cmd.getBoolean("stop", true))
      .put("command", cmd)
      .put("toolResult", result));
  }
}
