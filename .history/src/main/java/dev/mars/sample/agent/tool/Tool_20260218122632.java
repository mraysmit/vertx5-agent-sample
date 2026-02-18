package dev.mars.sample.agent.tool;

import dev.mars.sample.agent.runner.AgentContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A named capability that the agent can invoke during its reasoning loop.
 *
 * <p>Each tool has a unique {@link #name()} (e.g. {@code "events.publish"},
 * {@code "case.raiseTicket"}) and an {@link #invoke} method that performs
 * the side-effecting action and returns a result describing what happened.
 *
 * <p>Tools are registered in a {@link ToolRegistry} and only tools present
 * in the allow-list can be called by the agent — this is the primary
 * security boundary between the LLM's decisions and real-world actions.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@code args} — tool-specific arguments supplied by the LLM command.</li>
 *   <li>{@code ctx} — the current {@link AgentContext} (correlation ID,
 *       case ID, accumulated state), useful for enriching outbound events.</li>
 *   <li>Return value — a {@code JsonObject} describing the outcome;
 *       the agent runner appends it to the memory store.</li>
 * </ul>
 *
 * @see ToolRegistry
 * @see dev.mars.sample.agent.runner.AgentRunnerVerticle
 */
public interface Tool {

  /** The unique name of this tool as referenced by LLM commands. */
  String name();

  /**
   * Execute the tool's action.
   *
   * @param args tool-specific arguments from the LLM command
   * @param ctx  the current agent execution context
   * @return a Future with a JSON result describing the outcome
   */
  Future<JsonObject> invoke(JsonObject args, AgentContext ctx);
}
