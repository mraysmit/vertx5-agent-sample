package dev.mars.sample.agent.runner;

import dev.mars.sample.agent.Addresses;
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

public class AgentRunnerVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(AgentRunnerVerticle.class.getName());
  private static final int MAX_STEPS = 5;

  private final LlmClient llm;
  private final Map<String, Tool> tools;
  private final MemoryStore memory;

  public AgentRunnerVerticle(LlmClient llm, Map<String, Tool> tools, MemoryStore memory) {
    this.llm = llm;
    this.tools = tools;
    this.memory = memory;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    vertx.eventBus().consumer(Addresses.AGENT_REQUIRED, msg -> {
      JsonObject event = (JsonObject) msg.body();
      String caseId = event.getString("tradeId");
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
    if (step >= MAX_STEPS) {
      LOG.warning("Step limit reached for case=" + ctx.caseId());
      return Future.succeededFuture(new JsonObject()
        .put("status", "error")
        .put("path", "agent")
        .put("reason", "Step limit reached (safety stop)")
        .put("tradeId", ctx.caseId()));
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
          .put("tradeId", ctx.caseId()));
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
