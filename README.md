# Vert.x 5.x Agent vs Workflow Sample (Java)

A **minimal, runnable Vert.x 5 sample** showing the correct hybrid pattern:

- **Deterministic processor** handles known failures via pluggable handlers.
- **Agent boundary** only triggers when the failure is unknown/ambiguous.
- The "LLM" is a **stub** that emits strict JSON commands (replace later).
- **Tools are self-describing** with JSON Schema metadata aligned with MCP.

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

---

## Architecture

```
HTTP POST ──► HttpApiVerticle ──► DeterministicFailureProcessorVerticle
                                        │
                         ┌──────────────┴──────────────┐
                  Known reason?                  Unknown reason?
                         │                              │
                  FailureHandler                 AgentRunnerVerticle
                  (lookup-enrich,                       │
                   escalate, ...)              LlmClient.decideNext()
                         │                              │
                  Returns result              ┌─────────▼─────────┐
                                              │  { intent, tool,  │
                                              │    args, stop }   │
                                              └─────────┬─────────┘
                                                        │
                                               Tool.invoke(args, ctx)
                                                        │
                                              EventSinkVerticle (events.out)
```

All wiring — addresses, handlers, tools, LLM client — is externalised
to `pipeline.yaml` and resolved at startup via factory classes.

---

## How This Demonstrates MCP Without Implementing It

This project is deliberately designed so that its core abstractions map
directly onto [Model Context Protocol (MCP)](https://modelcontextprotocol.io/)
concepts. The architecture can be adopted into a full MCP implementation with
minimal changes, but runs today without any MCP dependencies.

### MCP Concept Mapping

| MCP Concept | This Project | Where |
|---|---|---|
| **Tool** — a callable capability with a name, description, and input schema | `Tool` interface with `name()`, `description()`, `schema()` | `tool/Tool.java` |
| **`tools/list`** — server advertises available tools with their schemas | `ToolRegistry` holds all registered tools; each tool self-describes via `schema()` | `tool/ToolRegistry.java` |
| **`tools/call`** — client invokes a tool by name with JSON arguments | `AgentRunnerVerticle.executeCommand()` dispatches `{tool, args}` to the matching `Tool` | `runner/AgentRunnerVerticle.java` |
| **`inputSchema`** — JSON Schema describing a tool's parameters | `Tool.schema()` returns JSON Schema 2020-12 compatible `JsonObject` | `tool/Tool.java` |
| **Tool allow-listing** — security boundary controlling which tools an agent can use | `ToolRegistry` acts as the allow-list; unknown tools are rejected with "not allowlisted" | `runner/AgentRunnerVerticle.java` |
| **LLM function-calling** — LLM decides which tool to call and with what arguments | `LlmClient.decideNext()` returns `{intent: "CALL_TOOL", tool, args}` commands | `llm/LlmClient.java` |
| **Agent loop** — iterative tool calls until the task is complete | `AgentRunnerVerticle.runLoop()` loops until `stop: true` or step limit | `runner/AgentRunnerVerticle.java` |

### What Each Tool Exposes (MCP-Ready)

Every `Tool` implementation provides three pieces of metadata that an MCP
server would advertise in a `tools/list` response:

```java
public interface Tool {
    String name();             // MCP tool name
    String description();      // MCP tool description
    JsonObject schema();       // MCP inputSchema (JSON Schema)
    Future<JsonObject> invoke(JsonObject args, AgentContext ctx);  // MCP tools/call
}
```

For example, `RaiseTicketTool` exposes:

```json
{
  "name": "case.raiseTicket",
  "description": "Creates a support ticket for a trade failure requiring manual investigation.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "tradeId":  { "type": "string", "description": "The trade identifier" },
      "category": { "type": "string", "description": "Ticket category, e.g. ReferenceData" },
      "summary":  { "type": "string", "description": "Brief summary of the issue" },
      "detail":   { "type": "string", "description": "Detailed description of the failure" }
    },
    "required": ["tradeId", "category", "summary"]
  }
}
```

### The Command Protocol

The LLM (or stub) communicates with the agent runner using a simple JSON
command format that mirrors MCP's `tools/call` request:

```json
{
  "intent": "CALL_TOOL",
  "tool": "case.raiseTicket",
  "args": {
    "tradeId": "T-200",
    "category": "ReferenceData",
    "summary": "Counterparty LEI issue"
  },
  "stop": true
}
```

| Field | Purpose | MCP Equivalent |
|---|---|---|
| `intent` | Action type (only `CALL_TOOL` supported) | Implicit in `tools/call` |
| `tool` | Tool name to invoke | `tools/call` → `name` |
| `args` | Tool arguments | `tools/call` → `arguments` |
| `stop` | Whether to end the agent loop | Application-level control |

### What's Missing for Full MCP

To turn this into an actual MCP server/client, you would add:

| Gap | What's Needed |
|---|---|
| **Transport** | MCP JSON-RPC 2.0 over HTTP+SSE or stdio (currently uses Vert.x event bus) |
| **Protocol messages** | `initialize`, `tools/list`, `tools/call` JSON-RPC handlers |
| **Resources** | MCP resources for exposing contextual data (e.g. trade details) |
| **Prompts** | MCP prompt templates for structured LLM interactions |
| **External tool servers** | Connect to out-of-process MCP servers instead of in-process `Tool` instances |

The key architectural decision — that **the runner does not choose tools** but
instead validates and dispatches commands from an external decision-maker (LLM)
— is exactly the MCP client pattern. Swapping `StubLlmClient` for a real LLM
with MCP-compatible function-calling requires no changes to the tool layer.

### Path to Full MCP Adoption

1. **Add an `McpToolAdapter`** — a `Tool` implementation that wraps an MCP
   server connection, translating `invoke()` calls into MCP `tools/call`
   JSON-RPC requests. This lets external MCP tools sit alongside in-process
   tools transparently.

2. **Expose tools as an MCP server** — add a thin HTTP+SSE endpoint that
   serves `tools/list` (derived from `ToolRegistry` + `Tool.schema()`) and
   handles `tools/call` requests by delegating to `Tool.invoke()`.

3. **Replace `StubLlmClient`** — use `OpenAiLlmClient` (placeholder in
   `llm/OpenAiLlmClient.java`) with function-calling, passing tool schemas
   as the function definitions.

---

## What to Look At

| Component | Purpose |
|---|---|
| `DeterministicFailureProcessorVerticle` | Normal service — fast, predictable, strategy-pattern handlers |
| `AgentRunnerVerticle` | Step-limited agent runner + tool dispatch (MCP client pattern) |
| `Tool` interface | Self-describing tools with `name()`, `description()`, `schema()` |
| `StubLlmClient` | Rule-based stub returning strict JSON commands |
| `pipeline.yaml` | All configuration externalised — addresses, handlers, tools, LLM |
| `config/` package | Records, loader, and factory classes for YAML-driven wiring |

## Configuration

All pipeline wiring is in `src/main/resources/pipeline.yaml`. Override the
config file path at startup:

```bash
java -Dpipeline.config=my-config.yaml -jar app.jar
```

See `PipelineConfigLoader` for loading details and `MainVerticle` for how
factories resolve YAML aliases to concrete classes.

## Test Coverage

102 tests across 22 test classes covering:
- Config record validation and defensive copying
- Factory resolution (handlers, tools, LLM clients) with error cases
- YAML config parsing and missing-resource handling
- Handler behaviour and event bus publishing
- Tool invocation, schema metadata, and registry immutability
- StubLlmClient rule matching, fallback, and defensive copying
- InMemoryMemoryStore lifecycle and case isolation
- Verticle deployment, routing, step limits, and error paths
- End-to-end smoke tests (deterministic + agent paths)
