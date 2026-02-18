package dev.mars.sample.agent.llm;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Abstraction over a Large Language Model (LLM) that decides the next action
 * the agent should take.
 *
 * <p>Implementations receive the original failure event and the accumulated
 * case state, and must return a structured command {@code JsonObject} with
 * the following schema:
 *
 * <pre>
 * {
 *   "intent": "CALL_TOOL",         // the action type (currently only CALL_TOOL)
 *   "tool":   "events.publish",     // name of the tool to invoke
 *   "args":   { ... },              // arguments passed to the tool
 *   "stop":   true | false          // whether to stop looping after this step
 * }
 * </pre>
 *
 * <p>The contract is intentionally simple so that the same interface can be
 * backed by a local stub, an HTTP call to an LLM provider, or a
 * retrieval-augmented pipeline.
 *
 * @see dev.mars.sample.agent.runner.AgentRunnerVerticle
 */
public interface LlmClient {

  /**
   * Given the failure event and the current case state, decide what the agent
   * should do next.
   *
   * @param event the original failure event ({@code tradeId}, {@code reason}, etc.)
   * @param state the accumulated case state from the {@link dev.mars.sample.agent.memory.MemoryStore}
   * @return a Future containing a structured command JSON
   */
  Future<JsonObject> decideNext(JsonObject event, JsonObject state);
}
