package dev.mars.sample.agent.runner;

import io.vertx.core.json.JsonObject;

public record AgentContext(String correlationId, String caseId, JsonObject state) {}
