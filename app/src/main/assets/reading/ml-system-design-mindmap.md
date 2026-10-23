# The ML System Design Mental Model: One Diagram, the Whole Pipeline

Same format as the Android and Go mindmaps — a small overview, then one full-size diagram per branch. This is about the **engineering system around a model** — data, pipelines, serving, monitoring — not the model architecture itself. **ML System** sits in the center; four branches radiate out, roughly in the order data actually flows through a real production system.

## Overview

```mermaid
flowchart TD
    MLS((ML System))
    MLS --> DL[Data Layer]
    MLS --> TP[Training Pipeline]
    MLS --> SI[Serving and Inference]
    MLS --> MF[Monitoring and Feedback Loop]
```

---

## Branch 1: Data Layer

```mermaid
flowchart LR
    DL[Data Layer] --> Ingestion["Data ingestion"]
    Ingestion --> Batch["Batch (ETL, scheduled)"]
    Ingestion --> Streaming["Streaming (Kafka, real-time events)"]

    DL --> Labeling["Labeling / ground truth"]
    Labeling --> HumanLabel["Human-in-the-loop labeling"]
    Labeling --> WeakLabel["Weak / heuristic labeling"]

    DL --> FeatureStore["Feature store"]
    FeatureStore --> OfflineFeatures["Offline store (training)"]
    FeatureStore --> OnlineFeatures["Online store (low-latency serving)"]
    FeatureStore --> TrainServeSkew["Train/serve skew — the #1 silent bug"]

    DL --> DataVersioning["Data versioning"]
    DataVersioning --> Reproducibility["Reproducible training runs"]

    DL --> DataQuality["Data quality checks"]
    DataQuality --> SchemaValidation["Schema validation"]
    DataQuality --> Dedup["Deduplication / outlier handling"]
```

---

## Branch 2: Training Pipeline

```mermaid
flowchart LR
    TP[Training Pipeline] --> ExperimentTracking["Experiment tracking"]
    ExperimentTracking --> Hyperparams["Hyperparameters, metrics, artifacts logged"]

    TP --> TrainOrch["Training orchestration"]
    TrainOrch --> Scheduled["Scheduled retraining jobs"]
    TrainOrch --> TriggeredRetrain["Drift-triggered retraining"]

    TP --> DistributedTraining["Distributed training infra"]
    DistributedTraining --> DataParallel["Data parallelism across workers"]

    TP --> ModelRegistry["Model registry"]
    ModelRegistry --> Versioning["Model versioning"]
    ModelRegistry --> Lineage["Lineage: which data, which code, which run"]

    TP --> Validation["Offline validation gate"]
    Validation --> HoldoutSet["Holdout / backtest evaluation"]
    Validation --> RegressionCheck["Regression check vs current production model"]
```

---

## Branch 3: Serving and Inference

```mermaid
flowchart LR
    SI[Serving and Inference] --> ServingMode{"Serving mode"}
    ServingMode --> OnlineServing["Online (real-time, low latency)"]
    ServingMode --> BatchServing["Batch (precomputed, scheduled)"]

    SI --> LatencySLA["Latency budget / SLA"]
    LatencySLA --> ModelOptimization["Quantization, distillation, caching"]

    SI --> Deployment["Deployment strategy"]
    Deployment --> ShadowMode["Shadow deployment (no user impact)"]
    Deployment --> CanaryRelease["Canary release"]
    Deployment --> ABTest["A/B test vs current model"]

    SI --> Rollback["Rollback plan"]
    Rollback --> InstantRevert["Instant revert to prior model version"]

    SI --> Infra["Serving infra"]
    Infra --> ModelServer["Model server (batches requests, GPU/CPU routing)"]
    Infra --> Autoscaling["Autoscaling under load"]
```

---

## Branch 4: Monitoring and Feedback Loop

```mermaid
flowchart LR
    MF[Monitoring and Feedback] --> DataDrift["Data drift detection"]
    DataDrift --> InputDistShift["Input distribution shift over time"]

    MF --> ModelDrift["Model / concept drift"]
    ModelDrift --> PerformanceDecay["Accuracy decays as the real world changes"]

    MF --> OnlineMetrics["Online metric tracking"]
    OnlineMetrics --> BusinessMetric["Business metric (not just model metric)"]
    OnlineMetrics --> ProxyMetricGap["Proxy metric vs real outcome gap"]

    MF --> Alerting["Alerting"]
    Alerting --> AnomalyDetection["Anomaly detection on predictions"]

    MF --> FeedbackLoop["Feedback loop back to Data Layer"]
    FeedbackLoop --> LabelCollection["New labels from production outcomes"]
    FeedbackLoop --> RetrainTrigger["Feeds back into retraining triggers"]
```

---

## How the four branches actually connect

This is the part that separates "I can name ML infra buzzwords" from actually having the mental model: **these four branches form a loop, not a pipeline with an end.** Monitoring doesn't just watch the system from the outside — its whole reason for existing is to feed back into the Data Layer (new labels from real outcomes) and the Training Pipeline (drift-triggered retraining), which is exactly what the arrow from Branch 4 back into Branch 1 represents. A system that trains once, deploys once, and never closes that loop isn't really an ML *system* — it's a one-time model export with a REST endpoint bolted on.

The single most common real-world failure point sits at the seam between Branch 1 and Branch 3: **train/serve skew** — the features computed during training (often batch, from a data warehouse) don't exactly match the features computed during live serving (often streaming, from a cache), and the model silently performs worse in production than its offline validation numbers ever suggested, for reasons that have nothing to do with the model itself. This is precisely why a feature store's split between an offline and online store is called out explicitly in Branch 1 rather than treated as a minor implementation detail — it's usually the actual root cause when a model that "tested great" quietly underperforms once it's live.

The deployment strategies in Branch 3 (shadow mode, canary, A/B test) exist for the same underlying reason system design's caching and rollout patterns exist in the HLD cheat sheet — a new model is a new, unproven piece of the system, and the safe move is always to validate it against real traffic with a controlled blast radius before fully trusting it, exactly the same caution a rolling deployment applies to a new code version.

---

*Pairs with the HLD (System Design) cheat sheet in this series — many of the same underlying tradeoffs (caching, latency budgets, rollout strategy, monitoring) reappear here, just applied to a system whose core component happens to be a model instead of a stateless API.*
