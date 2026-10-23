# The HLD (System Design) Cheat Sheet: Concepts to Tradeoffs

A reference doc — not a language or a tool this time, but the vocabulary and building blocks for designing systems that survive contact with real traffic. Easy through hard, same as the rest of this series.

---

## 1. What Problem System Design Actually Solves

Every cheat sheet before this one in the series solved a piece of "how do I build one correct thing" — a language, a container, an orchestrator, a query. **System design (High-Level Design, or HLD) answers a completely different question: how do I architect the pieces so the whole thing survives real-world scale, failure, and growth** — not "does this function work," but "does this system stay up when a server dies at 3 AM, when traffic 100x's overnight, when a database that's been fine for two years suddenly isn't."

HLD is the layer above code — the diagram you'd draw on a whiteboard before writing a single line, deciding which components exist, how they talk to each other, and where the tradeoffs live. There's rarely a single "correct" answer here — nearly every decision in this document is a **tradeoff**, not a fact, and being able to articulate the tradeoff is the actual skill being tested, in interviews and in production alike.

---

## 2. Vertical vs. Horizontal Scaling — The First Fork in Every Design

**Vertical scaling (scale up):** make one machine bigger — more CPU, more RAM, faster disks.

**Horizontal scaling (scale out):** add more machines, and spread the load across all of them.

Vertical scaling is simpler — no distributed systems complexity, no coordination between machines — but it has a hard physical ceiling (there's a biggest machine you can buy) and a single point of failure (that one machine dies, everything's down). Horizontal scaling has no such ceiling and survives individual machine failures, but it introduces real complexity — now you need to actually distribute requests across machines, keep them coordinated, and handle the fact that state living on one machine isn't automatically visible to another. Almost every concept in the rest of this document exists specifically to make horizontal scaling actually work in practice.

---

## 3. Load Balancing — Spreading Traffic Across Many Machines

A **load balancer** sits in front of a fleet of servers and distributes incoming requests across them, so no single server gets overwhelmed while others sit idle.

**Common algorithms:**
- **Round robin** — requests distributed in simple rotation, server 1, then 2, then 3, then back to 1. Simple, doesn't account for server load or request cost.
- **Least connections** — send the next request to whichever server currently has the fewest active connections. Better under uneven request costs.
- **IP hash** — route based on a hash of the client's IP, so the same client consistently lands on the same server — useful when a server holds session state, though this pattern is itself something modern designs try to avoid (see stateless services, below).

**Layer 4 vs. Layer 7 load balancing:** Layer 4 balances based on network-level info (IP, port) without looking at the actual request content — faster, less CPU overhead. Layer 7 balances based on the actual HTTP request content (path, headers, cookies) — enables smarter routing (`/api/*` to one fleet, `/static/*` to another) at the cost of more processing per request.

---

## 4. Caching — The Single Highest-Leverage Optimization in This Whole Document

A cache stores a copy of frequently-accessed data somewhere faster to read than its original source, so repeated requests for the same thing don't have to redo expensive work (a database query, an API call, a computation) every single time.

**Where caches live, roughly cheapest-and-closest to most-expensive-and-distant:**
- **Client-side / browser cache** — closest to the user, zero network trip at all if hit.
- **CDN (Content Delivery Network)** — caches static content (images, JS, CSS, and increasingly whole pages) at edge locations physically close to users worldwide, so a user in Tokyo doesn't fetch a static asset from a server in Virginia every time.
- **Application-level cache (Redis, Memcached)** — an in-memory store sitting between your application and your database, holding frequently-accessed data so the database doesn't get hit for every single request.
- **Database query cache** — some databases cache query results internally.

**Cache invalidation — the genuinely hard part**, famously one of the two hardest problems in computer science (the other being naming things, and off-by-one errors):

- **TTL (Time To Live)** — the cache entry simply expires after a fixed duration, whether or not the underlying data actually changed. Simple, but can serve stale data for up to the full TTL window.
- **Write-through** — every write goes to the cache and the database at the same time, synchronously. Cache is always consistent with the database, but every write pays the latency cost of both.
- **Write-back (write-behind)** — writes go to the cache immediately and are asynchronously flushed to the database later. Fast writes, but a real risk of data loss if the cache dies before the flush happens.
- **Cache-aside (lazy loading)** — the application checks the cache first; on a miss, it reads from the database and populates the cache for next time. The most common real-world pattern — simple, and the cache only ever holds data that's actually been requested.

---

## 5. CDN — Caching, Specifically for Geography

Worth its own short section because it's such a common interview topic: a **CDN** is a globally distributed network of edge servers that cache and serve static (and increasingly dynamic) content from a location physically close to the requesting user, instead of every request traveling all the way back to one origin server. The entire point is reducing **latency** — the actual speed-of-light and network-hop cost of distance — not just reducing load on your origin server, though it does that too.

---

## 6. Database Choices — SQL vs. NoSQL, and Why "It Depends" Is the Real Answer

**Relational (SQL)** databases (Postgres, MySQL) — structured schemas, strong consistency guarantees, powerful JOIN capability (the entire previous SQL cheat sheet in this series), and ACID transactions (also covered there). Best fit when your data has clear relational structure and you need strong consistency guarantees — financial systems, anything where "the balance is definitely correct right now" genuinely matters.

**NoSQL** databases come in several genuinely different flavors, each solving a different shape of problem:
- **Document stores** (MongoDB) — flexible, schema-less JSON-like documents. Good fit for rapidly evolving data models where forcing a rigid relational schema upfront is more friction than benefit.
- **Key-value stores** (Redis, DynamoDB) — the simplest model, extremely fast lookups by key. Ideal for caching, session storage, anything accessed almost exclusively by a single known key.
- **Wide-column stores** (Cassandra) — built for massive write throughput and horizontal scale across many machines, at the cost of some query flexibility.
- **Graph databases** (Neo4j) — built specifically for highly connected data (social networks, recommendation engines) where relationships between entities are the actual point of the queries, not an afterthought.

The honest framing: SQL vs. NoSQL was never "old vs. new" or "worse vs. better" — it's "strong consistency and relational structure" vs. "flexible schema and easier horizontal scale," and real systems very often use **both**, side by side, for different pieces of the same product (Postgres for orders and billing, Redis for session caching, Elasticsearch for search).

---

## 7. Database Scaling: Replication & Sharding

### Replication — copies of the same data, for availability and read scaling

**Leader-follower (primary-replica) replication:** one node (the leader) handles all writes; changes propagate to one or more follower nodes, which can serve read traffic. This scales read throughput horizontally (add more followers, handle more reads) and provides redundancy (a follower can be promoted if the leader dies), but writes still bottleneck through a single leader.

**Synchronous vs. asynchronous replication:** synchronous means the leader waits for confirmation from followers before considering a write complete — stronger consistency, higher write latency. Asynchronous means the leader confirms the write immediately and propagates to followers in the background — lower latency, but a real window where a follower is behind the leader, and data can be lost if the leader dies before replicating a recent write.

### Sharding (partitioning) — splitting data itself across multiple machines

Replication copies the *same* data everywhere. **Sharding** splits *different* data across different machines — user IDs 1–1,000,000 on shard A, 1,000,001–2,000,000 on shard B, and so on. This is how you scale **write** throughput and total storage capacity horizontally, which replication alone cannot do (every replica still holds the full dataset).

**Sharding strategies:**
- **Range-based** — split by a value range (user ID ranges, date ranges). Simple, but can create "hot shards" if data isn't evenly distributed (a viral event dumping traffic onto one date range's shard).
- **Hash-based** — hash the shard key, distribute based on the hash. Better distribution in practice, but range queries ("all users created this month") become much harder since related data is scattered across shards by design.
- **Geographic** — shard by region, useful for data residency requirements and reducing latency for regional users.

The real cost of sharding, always worth naming explicitly: cross-shard queries and transactions become genuinely hard — a query that needs data from two different shards can no longer be a single simple query, and a transaction spanning two shards needs distributed transaction coordination, which is expensive and complex. Shard only once you've genuinely outgrown what replication and vertical scaling can handle — it's a significant complexity cost, not a default choice.

---

## 8. CAP Theorem — The Tradeoff Everyone Name-Drops, Fewer People Actually Apply

The CAP theorem states that a distributed system can only genuinely guarantee **two** of these three properties **at the same time**, during a network partition:

- **Consistency** — every node sees the same data at the same time; a read always returns the most recent write.
- **Availability** — every request receives a response, even if some nodes are down or unreachable.
- **Partition tolerance** — the system keeps functioning even when network communication between nodes breaks down.

**The part people get wrong:** network partitions **will** happen in any real distributed system — hardware fails, networks drop packets, this isn't optional. So partition tolerance isn't really a choice you're making — it's a given. The actual, practical decision CAP forces on you is **CP vs. AP**: when a partition happens, do you sacrifice consistency (keep serving requests, possibly with stale data) or sacrifice availability (refuse to serve requests you can't guarantee are correct, until the partition heals)?

- **CP systems** (many traditional relational databases in certain configurations, ZooKeeper) choose consistency over availability during a partition — correctness over uptime.
- **AP systems** (Cassandra, DynamoDB in its default configuration) choose availability over consistency — uptime over strict correctness, typically settling for **eventual consistency** instead (see below).

This isn't an abstract theorem — it's the actual, concrete design decision behind why your bank's balance check is (usually) strongly consistent and your social media "like count" is (usually) eventually consistent, and neither choice is wrong, they're solving for different priorities.

---

## 9. Consistency Models — Strong vs. Eventual

**Strong consistency** — every read reflects the most recent write, immediately, everywhere. Simpler to reason about, but expensive to guarantee across a distributed system (usually requires waiting for replication to complete or for a majority of nodes to agree, adding latency).

**Eventual consistency** — after a write, the system guarantees that *eventually*, if no new writes occur, all nodes will converge on the same value — but there's a window where different nodes might return different, temporarily stale answers. Much cheaper and faster in a distributed system, and genuinely fine for a huge class of real use cases (a social media like count being off by a few for a few seconds is invisible to users; a bank balance being off by a few dollars for a few seconds is not).

The skill being tested here, same as CAP: knowing which consistency model a given feature actually needs, rather than reflexively demanding strong consistency everywhere "to be safe," which is often just paying a real performance cost for a guarantee the feature never actually needed.

---

## 10. Message Queues & Asynchronous Processing

A **message queue** (Kafka, RabbitMQ, SQS) decouples the production of work from the consumption of it — a producer drops a message onto a queue and moves on immediately, and one or more consumers process messages from the queue independently, at their own pace.

**Why this matters, concretely:** imagine an e-commerce checkout that needs to charge a card, update inventory, send a confirmation email, and notify a shipping partner. Doing all four synchronously in one request means the user waits for the *slowest* one (probably the email or the shipping API), and any single failure blocks the whole checkout. Route the non-critical steps through a queue instead — charge the card synchronously (the user needs to know immediately if that failed), then drop a message for "send confirmation email" and "notify shipping" onto a queue and respond to the user right away. Those steps happen asynchronously, retry independently on failure, and never block the actual checkout experience.

**Delivery guarantees, a real and commonly-tested distinction:**
- **At-most-once** — a message might be lost, but is never delivered twice. Fast, but risks silently dropping work.
- **At-least-once** — a message is guaranteed to be delivered, but might be delivered more than once (if a consumer crashes after processing but before acknowledging). Requires consumers to be **idempotent** (processing the same message twice produces the same end result as processing it once) — this is the practical reason idempotency shows up constantly in real system design discussions.
- **Exactly-once** — the hardest and most expensive guarantee to actually provide correctly at scale; many systems that claim it are really doing at-least-once delivery plus idempotent processing underneath, which achieves the same practical outcome without the theoretical purity.

---

## 11. Rate Limiting — Protecting a System From Its Own Popularity

Rate limiting caps how many requests a client can make in a given time window, protecting backend systems from being overwhelmed — whether by a genuine traffic spike, an abusive client, or a buggy retry loop hammering an endpoint.

- **Token bucket** — a bucket holds tokens, refilled at a fixed rate; each request consumes a token, and requests are rejected once the bucket's empty. Allows short bursts up to the bucket's capacity while still enforcing a long-run average rate.
- **Leaky bucket** — requests queue up and are processed at a fixed, steady rate, regardless of burstiness on the input side — smooths traffic out rather than allowing bursts through.
- **Fixed window** — count requests in fixed time windows (e.g., per-minute); simple, but has a real edge-case flaw where a client can send double the intended rate right at a window boundary (max requests at the very end of one window, then max requests again at the very start of the next).
- **Sliding window** — smooths over the fixed-window boundary problem by considering a continuously moving time window rather than fixed discrete buckets.

---

## 12. Stateless Services & Session Management

A **stateless** service holds no client-specific data between requests — every request carries everything the server needs to handle it, and any server instance can handle any request from any client. This is what actually makes horizontal scaling (section 2) work cleanly — a load balancer can route a request to *any* available server, because no particular server holds state that only it knows about.

The obvious tension: users do need sessions (staying logged in, shopping carts). The common solutions:
- **Externalize session state** into a shared store (Redis, a database) that every server instance can read — the server itself stays stateless, the session data just lives somewhere all instances can reach.
- **JWTs** (from the earlier language cheat sheets' auth sections) — encode session/identity data directly into a signed token the client holds and sends with every request, so the server needs to verify a signature rather than look up session state anywhere at all.

Both approaches exist specifically to avoid **sticky sessions** (routing a specific client to the specific server that holds their session state), which works but directly undermines the flexibility horizontal scaling is supposed to provide.

---

## 13. API Design Patterns — REST, GraphQL, gRPC

- **REST** — resources identified by URLs, manipulated via HTTP verbs (GET/POST/PUT/DELETE). Simple, cacheable (GET requests map naturally onto HTTP caching), the overwhelming default for public APIs.
- **GraphQL** — the client specifies exactly which fields it needs in a single request, instead of a fixed response shape per endpoint. Solves REST's common "over-fetching" (getting a big response when you needed three fields) and "under-fetching" (needing multiple round-trips to assemble what you actually need) problems, at the cost of more complexity on the server and much harder HTTP-level caching.
- **gRPC** — binary protocol (Protocol Buffers) over HTTP/2, built for fast, strongly-typed service-to-service communication. Common for internal microservice-to-microservice calls where both ends are your own systems, less common for public-facing APIs where broad client compatibility and human-readability matter more than raw throughput.

---

## 14. Microservices vs. Monolith — The Architecture-Level Tradeoff

**Monolith** — one codebase, one deployable unit, containing all of an application's functionality. Simpler to develop, test, and deploy initially — one thing to build, one thing to run, no network calls between internal components. The cost shows up at scale: the whole thing has to be deployed together even for a tiny change, a bug in one part can bring down the whole application, and different components can't be scaled independently even if only one part is actually under heavy load.

**Microservices** — the application is split into many independently deployable services, each owning a specific piece of functionality, communicating over the network (REST, gRPC, message queues). Each service can be deployed, scaled, and even rewritten independently, and a failure is more naturally contained to just that one service. The cost: genuine distributed-systems complexity — network calls where function calls used to be, the CAP theorem's consequences becoming real operational concerns, service discovery, and a significant increase in operational overhead (this is exactly the gap the Kubernetes cheat sheet in this series exists to manage).

The honest, commonly-cited guidance: most systems should **start as a monolith** and split into microservices only once genuine, specific pain points (independent scaling needs, independent team ownership, independent deploy cadences) actually justify the very real complexity cost — reaching for microservices on day one, before those pressures exist, is a well-known and common overcorrection, the same caution the Kubernetes cheat sheet raised about reaching for a cluster before you actually need one.

---

## 15. Putting It Together: A Worked Example Sketch

A rough shape of how these pieces compose for a typical "design a URL shortener" or "design a social media feed"-style interview question:

```
Client
  │
  ▼
CDN (static assets, cached responses)
  │
  ▼
Load Balancer (Layer 7, routes by path)
  │
  ├──► Stateless API servers (horizontally scaled, no local session state)
  │         │
  │         ├──► Cache (Redis) — cache-aside pattern for hot reads
  │         │
  │         ├──► Primary database (writes) ──► Read replicas (reads, replication)
  │         │         │
  │         │         └──► Sharded once write volume outgrows a single primary
  │         │
  │         └──► Message queue (async work — emails, notifications, analytics)
  │                   │
  │                   └──► Background worker services (consume + process, idempotently)
  │
  └──► Rate limiter (protects the whole stack from abusive or runaway traffic)
```

Every box in that diagram is a decision this document gave you the vocabulary to justify and trade off deliberately — which is the actual point of HLD: not memorizing this exact diagram, but being able to explain *why* each piece is there, and what you'd change under different constraints (read-heavy vs. write-heavy, strong consistency requirements vs. not, global user base vs. single region).

---

## 16. Quick Reference: The Tradeoff Pairs Worth Having Memorized

```
Vertical scaling      <->  Horizontal scaling      (simplicity/ceiling  vs.  complexity/no ceiling)
SQL                   <->  NoSQL                    (structure/consistency  vs.  flexibility/scale)
Strong consistency    <->  Eventual consistency      (correctness  vs.  latency/availability)
Synchronous replication <-> Asynchronous replication  (consistency  vs.  write latency)
Monolith              <->  Microservices             (simplicity  vs.  independent scaling/failure isolation)
Write-through cache   <->  Write-back cache           (consistency  vs.  write latency)
CP                    <->  AP                          (correctness during partition  vs.  uptime during partition)
```

---

*Pairs with the Docker and Kubernetes cheat sheets in this series — those cover how you package and run the pieces; this one covers how you decide what the pieces should even be.*
