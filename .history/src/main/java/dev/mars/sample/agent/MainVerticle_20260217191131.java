package dev.mars.sample.agent;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    var memory = new InMemoryMemoryStore();
    var llm = new StubLlmClient();
    var tools = ToolRegistry.defaultTools(vertx);

    vertx.deployVerticle(new DeterministicFailureProcessorVerticle())
      .compose(id -> vertx.deployVerticle(new AgentRunnerVerticle(llm, tools, memory)))
      .compose(id -> vertx.deployVerticle(new EventSinkVerticle()))
      .compose(id -> vertx.deployVerticle(new HttpApiVerticle()))
      .onSuccess(id -> startPromise.complete())
      .onFailure(startPromise::fail);
  }
}
