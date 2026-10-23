# HLD Walkthrough: URL Shortener — Full Depth, Split for Readability

Same content as the full-depth version, split into one overview plus separate diagrams per branch — same fix as the Android mindmap. Each diagram below renders at full width on its own, so nothing shrinks and nothing needs zooming.

## Overview

```mermaid
flowchart TD
    Root["Design URL<br/>Shortener?"]
    Root --> QDo["What can<br/>users DO?"]
    Root --> QQual["What QUALITY<br/>is needed?"]
    Root --> QScale["At WHAT<br/>scale?"]
    Root --> QData["What DATA<br/>to store?"]
    Root --> QApi["What<br/>APIs?"]
    Root --> QFlow["How does<br/>TRAFFIC flow?"]
    Root --> QId["How to mint<br/>SHORT IDs?"]
    Root --> QEnc["How to<br/>ENCODE IDs?"]
    Root --> QGrow["How to SCALE<br/>writes?"]
```

---

## Branch 1: What can users DO?

```mermaid
flowchart LR
    QDo["What can<br/>users DO?"] --> Shorten["Shorten a<br/>long URL?"]
    Shorten -->|yes| APIShorten["POST<br/>/api/urls/shorten<br/>CREATE"]

    QDo --> Open["Open a<br/>short link?"]
    Open -->|yes| APIOpen["GET /{short}<br/>-&gt; 302<br/>READ, hot path"]

    QDo --> Track["Count<br/>clicks?"]
    Track -->|yes| APITrack["Async event,<br/>off redirect path<br/>UPDATE"]
    Track -->|no| OOS["Out of scope,<br/>hook stays open<br/>for later"]
```

---

## Branch 2: What QUALITY is needed?

```mermaid
flowchart LR
    QQual["What QUALITY<br/>is needed?"] --> QInstant["Must feel<br/>instant?"]
    QInstant -->|yes| ALatency["Low latency<br/>single-digit ms<br/>read-through cache"]

    QQual --> QBusy["Up under<br/>high traffic?"]
    QBusy -->|yes| AAvail["High availability<br/>stateless + LB<br/>no single point"]

    QQual --> QKeep["Links live<br/>for years?"]
    QKeep -->|yes| ADur["Durability<br/>replicated store"]

    QQual --> QOne["One short -&gt;<br/>one long, always?"]
    QOne -->|yes| AUniq["Global uniqueness<br/>rules out naive<br/>random IDs"]
```

---

## Branch 3: At WHAT scale? (worked math, chained)

```mermaid
flowchart LR
    QScale["At WHAT<br/>scale?"] --> SUsers["Daily users?<br/>assume 100M DAU"]
    SUsers --> SRatio["Reads per write?<br/>100 : 1<br/>optimize reads"]
    SRatio --> SWrites["New URLs/day?<br/>100M / 100<br/>= 1M writes/day"]
    SWrites --> SBytes["Size per entry?<br/>short+long+meta<br/>≈ 500 bytes"]
    SBytes --> SRet["Retention?<br/>5 years"]
    SRet --> SStore["Total storage:<br/>1M x 500B x 365<br/>x 5 ≈ 0.9 TB"]
```

---

## Branch 4: What DATA to store?

```mermaid
flowchart LR
    QData["What DATA<br/>to store?"] --> DMap["URLMapping table<br/>short_url PK,<br/>original_url"]
    QData --> DClick["Analytics table<br/>short_url,<br/>click_count"]
```

---

## Branch 5: What APIs?

```mermaid
flowchart LR
    QApi["What<br/>APIs?"] --> AiPost["POST /shorten<br/>{longUrl} -&gt;<br/>{shortUrl}"]
    QApi --> AiGet["GET /{short}<br/>302 + Location<br/>emits async event"]
    QApi --> AiNo["Analytics API?<br/>NO — internal<br/>event stream only"]
```

---

## Branch 6: How does TRAFFIC flow?

```mermaid
flowchart LR
    QFlow["How does<br/>TRAFFIC flow?"] --> FWrite["Write: LB -&gt;<br/>Shortening Svc<br/>-&gt; mapping store"]
    QFlow --> FRead["Read: LB -&gt;<br/>cache check<br/>-&gt; DB on miss"]
    QFlow --> FAnalytics["Analytics: event<br/>-&gt; Redis counter<br/>-&gt; batched flush"]
```

---

## Branch 7: How to mint SHORT IDs?

```mermaid
flowchart LR
    QId["How to mint<br/>SHORT IDs?<br/>needs unique + short"] --> IdHash["Hash the<br/>long URL?"]
    IdHash --> IdHashUse["USE: want free<br/>dedup, no coordinator<br/>AVOID: high write scale"]
    IdHash --> IdHashTrade["Truncating causes<br/>real collisions —<br/>needs retry-with-salt"]

    QId --> IdUuid["Random<br/>UUID?"]
    IdUuid --> IdUuidUse["USE: internal keys,<br/>zero coordination<br/>AVOID: public short code"]
    IdUuid --> IdUuidTrade["36 chars raw —<br/>truncating it back<br/>reintroduces collisions"]

    QId --> IdSnow["Snowflake<br/>IDs?"]
    IdSnow --> IdSnowUse["USE: need ordering,<br/>can run a coordinator<br/>AVOID: want zero ops"]
    IdSnow --> IdSnowTrade["Sortable, high<br/>throughput, but<br/>clock-skew risk"]

    QId --> IdMachine["★ CHOSEN:<br/>Machine ID + Seq"]
    IdMachine --> IdMachineUse["USE: write paths<br/>must stay independent<br/>— scale by adding boxes"]
    IdMachine --> IdMachineTrade["Short, unique,<br/>zero collision retries<br/>needs seq persistence"]
```

---

## Branch 8: How to ENCODE the IDs?

```mermaid
flowchart LR
    QEnc["How to<br/>ENCODE the IDs?"] --> EncHex["Base16 hex?<br/>0-9a-f only<br/>7 chars ≈ 268M space<br/>too small for scale"]
    QEnc --> Enc64["Base64?<br/>shortest per char<br/>but +/= break URLs<br/>needs url-safe variant"]
    QEnc --> Enc62["★ Base62 chosen<br/>0-9A-Za-z<br/>62^7 ≈ 3.5T space<br/>covers years of growth"]
```

---

## Branch 9: How to SCALE writes? (cross-linked to Branch 7's choice)

```mermaid
flowchart LR
    IdMachine2["★ CHOSEN ID:<br/>Machine ID + Seq<br/>(from Branch 7)"] -.->|feeds directly into| GShard["Machine prefix<br/>= shard key"]
    GShard --> GRead["Cache miss routes<br/>straight to the<br/>right shard, no scatter"]
    GShard --> GPregen["Pre-generate ID<br/>batches? Usually<br/>unnecessary here —<br/>prefix already decouples writers"]
```

---

## What's preserved from the full-depth version

Nothing was cut to make this split — same worked math in Branch 3 (100M DAU chained all the way to ≈0.9 TB), same two-sided decision rules in Branch 7, same real numbers in Branch 8 (268M vs. 3.5T), same explicit cross-link in Branch 9 back to the chosen ID strategy. The only change is structural: nine smaller diagrams instead of one tall one, so each renders at a readable size without needing to zoom at all.

---

*Same URL shortener problem as the other versions in this series — this is the full-depth content, restructured for actual readability instead of forcing one oversized diagram.*
