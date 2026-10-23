# AI Agents Interview Cheat Sheet

Questions by topic, each with a tight answer you can say out loud. Last section covers the system design angle. Skim the **Rapid-fire** section the night before.

---

## 0. How to answer agent questions

Interviewers are mostly testing one thing: **do you know where agents fail, and what you do about it?** Anyone can wire up a tool-calling loop. For every answer, try to add:

1. What it is (one line)
2. Why it exists / what it fixes
3. Where it breaks
4. What you'd do to make it reliable (guardrails, evals, caps, human approval)

Lean toward "use the least autonomy that solves the problem." That stance is almost always rewarded.

---

## 1. Fundamentals

**Q: What is an AI agent?**
An LLM running in a loop where it decides the next action (usually a tool call), observes the result, and repeats until it decides it's done or hits a limit. The LLM controls the flow, not your code.

**Q: Agent vs. workflow vs. plain LLM call?**
- **LLM call:** one prompt, one response.
- **Workflow:** LLMs and tools orchestrated along **predefined code paths**. You decide the steps.
- **Agent:** the **model** decides the steps dynamically, based on what it observes.

Workflows are more predictable, cheaper, easier to test. Agents are flexible but slower, costlier, and harder to debug.

**Q: When should you NOT use an agent?**
When the steps are known ahead of time (use a workflow), when errors are expensive and irreversible, when latency/cost budgets are tight, or when a deterministic function already solves it. Start with the simplest thing (single call, then chain, then agent) and add autonomy only when measurably needed.

**Q: Explain the agent loop.**
```
messages = [system, user]
while not done and steps < MAX_STEPS:
    response = llm(messages, tools)
    if response has tool_calls:
        for call in tool_calls:
            result = execute(call)          # validated, permissioned, timed out
            messages.append(tool_result)
    else:
        return response.text                # model decided it's finished
```
Key engineering points: step cap, tool timeouts, error results fed back to the model, context growth management.

**Q: What is ReAct?**
Reason + Act. The model interleaves a thought ("I need the user's order status"), an action (tool call), and an observation (tool result), then reasons again. Most modern tool-calling agents are effectively ReAct loops, even when the "thought" isn't shown.

**Q: What makes something "agentic" vs. "autonomous"?**
Agentic = has tools and a decision loop. Autonomy is a dial: from "suggests, human executes" to "executes with approval gates" to "fully unattended." Pick the point on the dial per action, based on blast radius.

---

## 2. Tool Use / Function Calling

**Q: How does function calling actually work?**
You pass tool definitions (name, description, JSON schema for arguments) with the prompt. The model doesn't execute anything. It emits a structured request ("call `get_order` with `{id: 42}`"). **Your code** validates and runs it, then returns the result as a new message. The model never touches your systems directly.

**Q: What makes a good tool definition?**
- Clear, specific name and description (the description is effectively a prompt)
- Narrow purpose, one job per tool
- Strict JSON schema with enums/constraints instead of free text
- Descriptions that say **when to use it and when not to**
- Error messages that tell the model how to fix its call

**Q: The model calls a tool with bad arguments. What do you do?**
Validate against the schema before executing. If invalid, return a clear error as the tool result (not an exception that kills the loop) so the model can self-correct. Cap retries so it can't spin forever.

**Q: What if the agent has 50+ tools?**
Accuracy of tool selection degrades as the tool list grows and tool definitions eat context. Options: group tools and route to specialist sub-agents, retrieve only relevant tools per request (tool RAG), use a router step first, or prune and merge overlapping tools.

**Q: Parallel tool calls: when and risks?**
Safe when calls are independent and read-only (fetch three docs at once). Risky when calls have ordering dependencies or side effects. Execute in parallel in your code, but keep writes serialized and idempotent.

**Q: What is MCP (Model Context Protocol)?**
An open protocol for exposing tools, resources, and prompts to LLM apps through a standard client/server interface, so a tool server written once works across different clients instead of rewriting integrations per framework. Interview angle: it standardizes **integration**, but it does not make tools safe. Auth, permissions, and trust in third-party servers are still on you.

**Q: How do you handle tools with side effects?**
Idempotency keys, dry-run/preview mode, approval gates for destructive actions, narrow scopes, audit logs, and the ability to undo or compensate where possible.

**Q: Structured output vs. tool calling?**
Structured output constrains the final response shape (extract JSON). Tool calling lets the model request actions mid-run. They combine: tools to act, structured output for the final answer.

---

## 3. Agent Design Patterns

These five workflow/agent patterns cover most real systems:

| Pattern | Idea | Use when |
|---|---|---|
| **Prompt chaining** | Fixed sequence of LLM steps, each feeding the next | Task decomposes cleanly into steps |
| **Routing** | Classify input, send to a specialized path | Distinct categories need different handling |
| **Parallelization** | Run subtasks simultaneously, or vote across runs | Independent subtasks, or you want confidence via consensus |
| **Orchestrator-workers** | Central LLM breaks down task dynamically, delegates to workers | Subtasks can't be predicted in advance |
| **Evaluator-optimizer** | One LLM generates, another critiques, loop until good | Clear quality criteria, refinement helps |

**Q: Plan-and-execute vs. ReAct?**
Plan-and-execute makes a full plan upfront, then executes steps (cheaper, more predictable, but brittle if the world changes mid-plan, so add replanning). ReAct decides one step at a time (adaptive, but can wander and costs more tokens per step). Hybrid is common: plan, execute, replan on failure.

**Q: What is reflection / self-critique and does it work?**
The model reviews its own output and revises. Works well when there's an external signal (tests failing, compiler errors, schema validation). Works poorly as pure self-judgment, because models are often overconfident in their own answers. **Ground reflection in a deterministic check whenever you can.**

**Q: Explain the code migration loop pattern (e.g., JS to TS).**
Convert module, run compiler/tests, feed errors back, retry, stop when green or retry cap reached. This is evaluator-optimizer where the evaluator is **deterministic** (compiler/tests), which is the strongest version of the pattern. Mention: cap retries, track diff size, escalate to a human when stuck, never let the agent weaken tests to pass.

**Q: How do you stop an agent from looping forever?**
Max steps, max tokens/cost budget, wall-clock timeout, loop detection (same tool + same args repeated), progress checks, and a defined "give up and escalate" path.

---

## 4. Memory & Context

**Q: Short-term vs. long-term memory?**
- **Short-term:** the current context window (conversation + tool results).
- **Long-term:** persisted outside the model: vector store, database, files, key-value notes, retrieved on demand.

**Q: What happens as context fills up?**
Cost and latency grow, and quality can degrade (relevant details get lost in a long context, old tool outputs distract). Strategies: summarize/compact old turns, drop or truncate bulky tool outputs, store large results externally and pass references, keep a running "scratchpad" of key facts, retrieve instead of stuffing.

**Q: How do you design agent memory?**
Decide what's worth remembering (user preferences, task state, decisions), when to write (end of task, on explicit signal), how to retrieve (recency, semantic search, structured lookup), and how to **forget or correct** (stale and wrong memories are a real failure mode). Treat memory writes as untrusted input if they come from external content.

**Q: RAG vs. fine-tuning vs. long context for giving an agent knowledge?**
RAG for fresh, large, or private knowledge you must cite and update. Long context for small corpora where retrieval adds complexity. Fine-tuning for style/format/behavior, not as a reliable way to inject facts.

**Q: Common RAG failure modes in agents?**
Bad chunking, retrieval misses (right doc never fetched), irrelevant chunks diluting context, stale index, no citations/grounding check. Fix with hybrid search (keyword + vector), reranking, better chunking, evals on retrieval separately from generation.

**Q: How do agents keep state across a long-running task?**
Persist state outside the model after each step (checkpointing), so a crash or restart resumes rather than starts over. LangGraph checkpointers and durable-execution engines both do this.

---

## 5. Multi-Agent Systems

**Q: When do multiple agents beat one?**
When subtasks need different tools/context/prompts, when parallelism helps (research fan-out), when context for one agent would overflow, or when you want separation of concerns (writer vs. critic). When it doesn't: simple tasks, tightly coupled steps, or when coordination overhead outweighs the gain.

**Q: Common topologies?**
- **Supervisor/orchestrator:** one agent delegates to specialists and merges results.
- **Hierarchical:** supervisors of supervisors.
- **Peer handoff / swarm:** agents pass control to each other.
- **Pipeline:** fixed sequence of specialist agents.
- **Debate/critic loop:** agents challenge each other's output.

**Q: Failure modes of multi-agent systems?**
Error compounding across hops, agents talking past each other, lost context at handoffs, runaway cost (N agents x loops), infinite delegation cycles, hard-to-trace failures, and conflicting writes to shared state. Mitigate with explicit state schemas, handoff contracts, per-agent budgets, tracing, and a single owner for final decisions.

**Q: How do agents share information?**
Shared state object (graph state), message passing, or a shared store/blackboard. Prefer **structured state** over free-text chatter, since structure survives handoffs.

**Q: Design: supervisor, researcher, analyst, writer, critic.**
Supervisor plans and fans out research queries in parallel. Researchers fetch and summarize sources with citations. Analyst merges and checks consistency. Writer drafts. Critic scores against a rubric and sends specific revision notes back. Loop capped (e.g., 2-3 rounds). Shared state holds sources, claims, draft, critique. Deterministic checks: every claim has a source, links resolve, length/format constraints.

---

## 6. Frameworks

**Q: LangGraph: core concepts?**
Agent as a **graph**: nodes (functions/LLM calls), edges (control flow, including conditional edges), shared **state** (typed schema, with reducers defining how updates merge), **checkpointers** (persist state per step, enabling resume, time-travel, and human-in-the-loop `interrupt`). Good fit when you need explicit control flow, loops, branching, persistence.

```python
from typing import Annotated, TypedDict
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langgraph.checkpoint.memory import MemorySaver

class State(TypedDict):
    messages: Annotated[list, add_messages]   # reducer: append, don't overwrite
    attempts: int

def route(state: State) -> str:
    last = state["messages"][-1]
    return "tools" if getattr(last, "tool_calls", None) else "end"

g = StateGraph(State)
g.add_node("agent", agent_node)
g.add_node("tools", tool_node)
g.add_edge(START, "agent")
g.add_conditional_edges("agent", route, {"tools": "tools", "end": END})
g.add_edge("tools", "agent")
app = g.compile(checkpointer=MemorySaver())
```

**Q: What is a reducer in LangGraph?**
A function defining how a node's output merges into existing state for a key (append to list, sum, overwrite). Without it, parallel nodes writing the same key conflict or overwrite each other.

**Q: LangGraph vs. LangChain vs. CrewAI vs. AutoGen vs. raw API?**
- **Raw API loop:** maximum control, little magic, great for simple agents and for understanding what's happening.
- **LangChain:** component library (models, retrievers, tools, chains).
- **LangGraph:** low-level orchestration for stateful, controllable agent graphs.
- **CrewAI / AutoGen:** higher-level multi-agent abstractions (role-based crews, conversational agents). Faster to prototype, less control.

Honest answer: frameworks speed up common patterns but can hide prompts and behavior. Know how to build the loop without one.

**Q: How do you do human-in-the-loop?**
Pause the graph at a defined point (before a destructive tool call, or at low confidence), persist state, surface the proposed action to a human, resume with approve/edit/reject. Needs checkpointing to work across process restarts.

---

## 7. Reliability, Evals & Observability

**Q: Why are agents hard to test?**
Non-deterministic outputs, multi-step trajectories where early errors compound, many valid paths to the same answer, and flaky external tools. Unit-testing the final string isn't enough.

**Q: How do you evaluate an agent?**
Layer it:
1. **Deterministic unit tests** for tools, parsers, validators (no LLM involved).
2. **Outcome evals:** did it achieve the goal? (tests pass, DB state correct, answer matches ground truth)
3. **Trajectory evals:** did it use sensible steps/tools, avoid forbidden actions, stay within budget?
4. **LLM-as-judge** for subjective quality, with a rubric.
5. **Regression suite** of real failure cases, run on every prompt/model change.
6. **Online monitoring** on production traces, with human spot checks.

**Q: Pitfalls of LLM-as-judge?**
Position and verbosity bias, self-preference, inconsistency, and shared blind spots with the generator. Mitigate with clear rubrics, pairwise comparisons with order swapped, calibrating against human labels, and preferring programmatic checks wherever possible.

**Q: How do you make an agent more reliable?**
Narrow scope, deterministic components where possible (validation, routing rules, calculations), schema-constrained outputs, step/cost caps, retries with backoff for transient errors, idempotent tools, checkpointing, human approval for high-risk actions, and evals gating every change.

**Q: Where should you use deterministic code instead of the LLM?**
Arithmetic, validation, permission checks, routing on known categories, formatting, anything with a right answer you can compute. Use the LLM for ambiguity, language, and judgment. A good agent is mostly ordinary code with LLM calls at the points that genuinely need them.

**Q: What do you log/trace?**
Every LLM call (prompt, response, model, tokens, latency, cost), every tool call (args, result, duration, errors), step count, state transitions, final outcome, and user feedback. Tools: LangSmith, Langfuse, OpenTelemetry-based tracing. Without traces you cannot debug a bad run.

**Q: How do you debug a bad agent run?**
Replay the trace. Find the **first** wrong step (not the last). Classify: bad retrieval, bad tool choice, bad arguments, bad tool result, context loss, or bad reasoning. Fix at the right layer (tool description, schema, prompt, state design), then add the case to the regression suite.

**Q: Hallucination in agents: what's different?**
Beyond wrong facts, agents can hallucinate **actions** (claiming a tool succeeded, inventing tool outputs, fabricating IDs). Mitigations: verify actions against system state rather than trusting the model's claim, make tool results authoritative, require citations/IDs from tool output.

---

## 8. Safety & Security

**Q: What is prompt injection?**
Untrusted text that the model treats as instructions. **Direct:** user types malicious instructions. **Indirect:** instructions hidden in content the agent reads (web page, email, PDF, repo file, tool output).

**Q: Why is indirect prompt injection hard to fully solve?**
LLMs don't reliably separate instructions from data in the same context window. You can reduce risk but not eliminate it with prompting alone, so you design for **containment**, assuming injection will sometimes succeed.

**Q: What's the "lethal trifecta"?**
An agent that has (1) access to private data, (2) exposure to untrusted content, and (3) a way to communicate externally. All three together enables data exfiltration. Remove at least one leg.

**Q: How do you secure an agent?**
- **Least privilege:** scoped credentials per task, read-only by default
- **Sandboxing:** run code and shell tools in isolated containers with no ambient secrets, restricted network egress
- **Approval gates** for destructive or external actions
- **Allowlists** for tools, domains, commands
- **Treat tool output and retrieved content as untrusted data**
- Never put secrets in prompts; keep credentials in the tool layer
- Output filtering and egress controls to block exfil channels (URLs, markdown images, emails)
- Audit logging and anomaly detection
- Red-team with injection test suites

**Q: What is the confused deputy problem for agents?**
The agent holds the user's (or a service's) authority and is tricked by untrusted input into using it for an attacker's goal. Fix: authorize actions against the **end user's** permissions, not the agent's broad ones.

**Q: Risks of running coding agents specifically?**
Executing malicious repo content, supply-chain installs, reading secrets from env/files, destructive commands, pushing to remotes, and injected instructions in code comments/READMEs/issues. Mitigate with sandboxes, no credentials in the environment, command allowlists/approval, network restrictions, review before merge.

**Q: How do you handle PII and compliance?**
Minimize data sent to the model, redact where possible, control retention, pick providers/regions per policy, log access, and keep PII out of traces or protect those traces.

---

## 9. Cost, Latency & Scaling

**Q: How do you reduce agent cost and latency?**
- Route easy steps to smaller/cheaper models, hard ones to larger
- **Prompt caching** for stable prefixes (system prompt, tool definitions, docs)
- Shorter contexts: compact history, truncate tool outputs
- Parallelize independent calls
- Fewer steps: better tools (one tool that does the job instead of five round trips)
- Deterministic shortcuts for known cases
- Stream tokens/progress to improve perceived latency
- Set token and step budgets

**Q: Why is cost per task unpredictable for agents?**
Variable number of steps and growing context per step (each step resends history). Cost can grow roughly quadratically with steps without compaction. Budget per task, alert on outliers.

**Q: Sync vs. async agents?**
Short tasks can run in the request/response path. Long-running ones (minutes+) need background execution: queue, workers, status polling or webhooks/streaming, resumable state.

---

## 10. Agent System Design Questions

### Framework to answer any of these

1. **Clarify:** users, task, success metric, what's allowed to be autonomous, latency/cost budget, risk of wrong actions
2. **Decide autonomy:** workflow vs. agent, and which steps need a human
3. **Tools & data:** what tools, what permissions, what data sources
4. **Architecture:** orchestration, state, memory, execution environment
5. **Reliability:** validation, caps, retries, checkpointing, deterministic checks
6. **Evals & monitoring:** how you know it works and keeps working
7. **Safety:** injection, privilege, sandbox, approvals
8. **Scale & cost**
9. **Tradeoffs and what you'd cut for v1**

### Q: Design a customer support agent
- Router classifies intent (refund, order status, technical, escalate)
- Tools: order lookup, refund (with limit + approval above threshold), KB search (RAG with citations)
- Deterministic policy engine decides refund eligibility, the LLM only phrases the response
- Escalate to a human on low confidence, angry sentiment, or high-value actions
- Evals: resolution rate, escalation correctness, policy violations (must be ~zero), CSAT
- Risks: prompt injection via customer message, over-refunding, leaking another customer's data (authorize by session identity)

### Q: Design a coding agent
- Loop: read repo, plan, edit, run tests/linters/compiler, iterate
- Tools: file read/search, edit/patch, shell (sandboxed), test runner, git (no push without approval)
- Sandbox: container, no secrets, restricted network, resource limits
- Context: repo map/index, retrieve relevant files rather than stuffing, compact long outputs
- Deterministic evaluator: tests/compiler as the source of truth, forbid editing tests to pass
- Caps: step/time/cost, diff-size limits, human review before merge
- Evals: held-out task suite (SWE-bench style), pass rate, regressions, cost per task

### Q: Design a deep research agent
- Planner decomposes the question, researchers run in parallel (search, fetch, extract), dedupe sources, analyst reconciles conflicts, writer drafts with citations, critic verifies claims against sources
- State: queries, sources, claims-to-source map, draft
- Deterministic checks: every claim cites a source, URLs resolve, quote-in-source verification
- Controls: breadth/depth limits, budget cap, source quality filters
- Risks: injection from fetched pages, citation fabrication, SEO-spam sources

### Q: Design a code migration agent (e.g., JS to TS)
- Chunk by module/dependency order, convert, run `tsc` + tests, loop on errors with retry cap
- Deterministic gate: compiler and tests; no `any`-spam acceptance threshold; no test weakening
- Track per-file status in persistent state, resume after failure
- Escalate stuck files to a human with the error history
- Metrics: files converted green, `any` count, human-touch rate

### Q: Design an email/inbox triage agent
- Classify, summarize, draft replies, never send without approval at first
- Untrusted-content problem: emails are injection vectors, strip/flag instructions, no external-send tool in the same context as private data (lethal trifecta)
- Graduated autonomy: auto-archive low-risk, draft-only for replies, approval for sends

### Q: Design an agent platform (many tenants run agents)
- Isolation per tenant (compute, credentials, data), quota and rate limits, durable execution with queues, tool registry with per-tenant permissions, tracing and cost attribution per tenant, kill switch, audit logs

---

## 11. Classic System Design Angles for Agents

**Q: How do you run long-running agent tasks reliably?**
Queue-backed workers or a durable execution engine (Temporal-style). Persist state after each step, make steps idempotent, support resume after crash, expose status via polling/webhooks/SSE. Don't hold a long task inside one HTTP request.

**Q: Idempotency: why does it matter more with agents?**
Retries are everywhere (model retries, step retries, worker restarts). Each side-effecting tool needs an idempotency key so a retried step doesn't double-charge or double-send.

**Q: How do you handle rate limits and provider outages?**
Backoff with jitter, request queues with concurrency limits, per-tenant quotas, fallback models/providers, circuit breakers, graceful degradation (smaller model, cached answer, "try later").

**Q: Where does state live?**
Conversation/graph state in a DB or checkpoint store (Postgres/Redis), large artifacts in object storage, long-term memory in a vector store plus relational store for structured facts. Keep the context window as a **cache** of state, not the source of truth.

**Q: How do you stream progress to users?**
SSE/WebSockets for token and step events; emit structured events (tool started, tool finished, plan updated) so the UI can show what the agent is doing.

**Q: How do you version and roll out prompt/model changes?**
Treat prompts and tool schemas as code: version control, eval gate in CI, canary/shadow traffic, compare against baseline traces, quick rollback.

**Q: Caching in agent systems?**
Prompt caching for stable prefixes, tool-result caching for deterministic reads (with TTL), semantic cache for repeated questions (careful with correctness and personalization).

**Q: Multi-tenancy concerns?**
Never mix tenant data in shared memory/vector indexes without tenant filters enforced server-side, scope credentials per tenant, isolate execution sandboxes, attribute cost, audit access.

---

## 12. Coding Questions You Might Get

**Implement a minimal agent loop (provider-neutral):**
```python
def run_agent(llm, tools: dict, user_msg: str, max_steps: int = 10):
    messages = [
        {"role": "system", "content": "You are a helpful agent. Use tools when needed."},
        {"role": "user", "content": user_msg},
    ]
    for _ in range(max_steps):
        resp = llm.chat(messages, tools=[t.schema for t in tools.values()])
        messages.append(resp.message)

        if not resp.tool_calls:
            return resp.text

        for call in resp.tool_calls:
            tool = tools.get(call.name)
            if tool is None:
                result = {"error": f"unknown tool {call.name}"}
            else:
                try:
                    args = tool.validate(call.arguments)      # schema validation
                    result = tool.run(**args)
                except Exception as e:
                    result = {"error": str(e)}               # feed back, don't crash
            messages.append({"role": "tool", "tool_call_id": call.id,
                             "content": json.dumps(result)})
    return "Stopped: step limit reached."
```
Talk through: step cap, validation, error as data, unknown-tool handling, where you'd add timeouts, cost tracking, loop detection.

**Retry with exponential backoff and jitter:**
```python
import random, time

def with_retry(fn, retries=5, base=0.5, cap=20):
    for attempt in range(retries):
        try:
            return fn()
        except TransientError:
            if attempt == retries - 1:
                raise
            time.sleep(min(cap, base * 2 ** attempt) * random.uniform(0.5, 1.5))
```

**Truncate/compact conversation to a token budget:** keep system prompt + last N turns + a running summary of older turns; always keep unresolved tool calls paired with their results.

**Detect a stuck loop:** hash `(tool_name, normalized_args)`; if the same hash repeats 3 times, abort or force a different strategy.

---

## 13. Behavioral / Experience Questions

- Tell me about an agent you built. What was the hardest failure mode?
- A time an LLM did something unexpected in production. How did you find and fix it?
- How did you decide what to automate vs. keep human?
- How do you decide when to trust model output?
- A time you cut scope or removed autonomy to improve reliability.
- How did you measure that your agent actually worked?
- What would you build first with limited time, and what would you defer?

Strong theme to reuse: *you prefer deterministic components for anything verifiable, and use the model only where judgment or language is genuinely needed.*

---

## 14. Rapid-Fire Definitions

- **Agent:** LLM in a loop choosing actions via tools
- **Tool/function calling:** model emits structured call, your code executes
- **ReAct:** interleaved reasoning and acting
- **RAG:** retrieve relevant docs, add to prompt
- **Embedding:** vector representing meaning, used for similarity search
- **Context window:** max tokens the model can attend to per call
- **Prompt caching:** reuse computation for a repeated prompt prefix
- **Checkpointing:** persisting state per step to resume/replay
- **Reducer:** how state updates merge (LangGraph)
- **Handoff:** one agent passing control/context to another
- **Guardrails:** checks on inputs/outputs/actions (validation, policy, filters)
- **HITL:** human-in-the-loop approval/intervention
- **Prompt injection:** untrusted text hijacking model behavior
- **Lethal trifecta:** private data + untrusted content + external comms
- **LLM-as-judge:** model grading another model's output
- **Trajectory eval:** grading the steps, not just the final answer
- **Idempotency key:** makes retried side effects safe
- **MCP:** open protocol for tool/context servers
- **Orchestrator-workers:** central planner delegating to workers
- **Evaluator-optimizer:** generate, critique, refine loop
- **Durable execution:** workflow engine that survives crashes and resumes

---

## 15. Questions to Ask Them

- What's the most common way your agents fail in production today?
- How do you evaluate changes before shipping, and how big is your eval set?
- What level of autonomy do agents have, and where do humans stay in the loop?
- How do you handle prompt injection and tool permissions?
- What does tracing and debugging look like for a bad run?
- Build vs. framework: what do you use for orchestration and why?
- How do you control cost per task?

---

## Last-minute checklist

- [ ] Can explain agent vs. workflow and when not to use an agent
- [ ] Can write the agent loop from memory
- [ ] Can name the 5 patterns (chain, route, parallel, orchestrator-workers, evaluator-optimizer)
- [ ] Can explain prompt injection + lethal trifecta + containment
- [ ] Have one concrete story about a failure and how you fixed it
- [ ] Can design a support agent and a coding agent end to end
- [ ] Can explain how you'd eval an agent (outcome + trajectory + regression)
- [ ] Can explain durable execution, idempotency, and checkpointing
