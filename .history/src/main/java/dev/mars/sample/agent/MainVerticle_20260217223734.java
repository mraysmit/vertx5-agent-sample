package dev.mars.sample.agent;

import dev.mars.sample.agent.api.HttpApiVerticle;
import dev.mars.sample.agent.event.EventSinkVerticle;
import dev.mars.sample.agent.llm.StubLlmClient;
import dev.mars.sample.agent.memory.InMemoryMemoryStore;
import dev.mars.sample.agent.processor.DeterministicFailureProcessorVerticle;
import dev.mars.sample.agent.runner.AgentRunnerVerticle;
import dev.mars.sample.agent.tool.ToolRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    var memory = new InMemoryMemoryStore();
    var llm = new StubLlmClient();
    var tools = ToolRegistry.defaultTools(vertx);

    // Forward our config to HttpApiVerticle so http.port can be overridden (e.g., 0 for tests)
    DeploymentOptions httpOpts = new DeploymentOptions().setConfig(config());

    vertx.deployVerticle(new DeterministicFailureProcessorVerticle())
      .compose(id -> vertx.deployVerticle(new AgentRunnerVerticle(llm, tools, memory)))
      .compose(id -> vertx.deployVerticle(new EventSinkVerticle()))
      .compose(id -> vertx.deployVerticle(new HttpApiVerticle(), httpOpts))
      .onSuccess(id -> startPromise.complete())
      .onFailure(startPromise::fail);
  }
}
