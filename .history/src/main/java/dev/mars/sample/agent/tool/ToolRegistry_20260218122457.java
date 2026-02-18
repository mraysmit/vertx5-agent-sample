package dev.mars.sample.agent.tool;

import io.vertx.core.Vertx;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory that builds the default set of {@link Tool} instances available
 * to the agent.
 *
 * <p>The current implementation hard-codes the tool list. In a more
 * extensible design this could read tool names from configuration or
 * discover them via a service-loader / plugin mechanism.
 *
 * <h2>Default tools</h2>
 * <ul>
 *   <li>{@link PublishEventTool} ({@code events.publish})</li>
 *   <li>{@link RaiseTicketTool} ({@code case.raiseTicket})</li>
 * </ul>
 *
 * @see Tool
 */
public final class ToolRegistry {
  private ToolRegistry() {}

  /**
   * Create the default tool map keyed by {@link Tool#name()}.
   *
   * @param vertx the Vert.x instance (needed by tools that publish events)
   * @return an unmodifiable map of tool-name → tool instance
   */
  public static Map<String, Tool> defaultTools(Vertx vertx) {
    List<Tool> tools = List.of(
      new PublishEventTool(vertx),
      new RaiseTicketTool(vertx)
    );
    return tools.stream().collect(Collectors.toMap(Tool::name, t -> t));
  }
}
