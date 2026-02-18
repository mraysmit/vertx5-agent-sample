# Vert.x 5.x Agent vs Workflow Sample (Java)

A **minimal, runnable Vert.x 5 sample** showing the correct hybrid pattern:

- **Deterministic processor** handles known failures.
- **Agent boundary** only triggers when the failure is unknown/ambiguous.
- The "LLM" is a **stub** that emits strict JSON commands (replace later).

## Requirements
- Java 21+
- Maven 3.9+

## Build + Run
```bash
mvn -q test
mvn -q package
java -jar target/vertx5-agent-sample-0.1.0-SNAPSHOT-fat.jar
```

Server starts on **http://localhost:8080** (override with config `http.port`)

## Health Check
```bash
curl http://localhost:8080/health
```

## Try it

### 1) Known deterministic workflow (no agent)
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-100","reason":"Missing ISIN"}' | jq
```

### 2) Unknown failure (agent classification kicks in)
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-200","reason":"LEI not found in registry"}' | jq
```

## What to look at
- `DeterministicFailureProcessorVerticle` — the normal service (fast, predictable)
- `AgentRunnerVerticle` — step-limited agent runner + tool execution
- `StubLlmClient` — returns strict JSON commands
- Tools (`events.publish`, `case.raiseTicket`) — the only place real state changes would happen
