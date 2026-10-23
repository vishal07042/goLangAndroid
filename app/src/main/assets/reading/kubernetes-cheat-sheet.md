# The Kubernetes Cheat Sheet: Pods to Production

A reference doc — starts with why this exists on top of Docker at all, then becomes the thing you keep open in a terminal tab.

---

## 1. What Problem Kubernetes Actually Solves

Docker (previous cheat sheet in this series) solves "how do I package and run one consistent, portable unit of an application." It does not solve what happens the moment you have more than a handful of containers running in production, and reality starts happening to them:

- A container crashes at 3 AM. Who notices? Who restarts it?
- Traffic spikes and one instance of your API can't keep up. Who starts three more copies?
- You need to deploy a new version without dropping any in-flight requests. Who coordinates that, container by container, across every server you own?
- A physical server dies. Every container that was running on it just vanished. Who notices, and who reschedules that work onto a server that's still alive?
- You have 40 containers across 15 machines. Which containers are on which machines, and who's actually deciding that, by hand, today?

Running a handful of containers with plain `docker run` commands is fine right up until you have enough of them that a human manually watching, restarting, and placing containers stops being a viable job description. **Kubernetes (K8s) exists to be that human, automated** — a system that continuously watches a fleet of containers across a fleet of machines and takes action to keep reality matching what you declared you wanted, without you personally SSH-ing into a server at 3 AM.

---

## 2. How It Actually Solves It — Declarative, Self-Healing, Reconciled

The core mechanic that makes Kubernetes different from "a fancier script that runs `docker run` in a loop" is the **reconciliation loop**, and it's worth understanding as the one idea everything else in this document builds on top of.

You don't tell Kubernetes *how* to do something, step by step. You tell it **what you want to be true** — "I want 3 replicas of this container running, always" — and Kubernetes continuously compares that **desired state** against the **actual state** of the cluster, and takes action to close any gap it finds. This is a fundamentally different programming model from an imperative script:

```
Desired state: 3 replicas running
Actual state:  2 replicas running (one crashed)
Action:        start 1 more, automatically, no human involved
```

This loop runs constantly, forever, for everything in the cluster — not just "restart crashed things" but scaling, rolling updates, rescheduling work off a dead machine onto a healthy one, all the same underlying pattern: **declare the goal, let the control loop continuously drag reality toward it.** This is why Kubernetes is described as "self-healing" — it's not that failures don't happen, it's that the system never stops noticing the gap between what you asked for and what's actually true, and never stops correcting it.

---

## 3. Kubernetes vs. Plain Docker — Why Docker Alone Wasn't Enough

Docker gives you the **unit** (a container) and a way to run it consistently. It does not give you, out of the box:

- **Orchestration across multiple machines** — Docker alone runs containers on the one host you're commanding directly. Real production workloads span many machines, and something needs to decide which container goes where.
- **Self-healing** — plain Docker doesn't watch a crashed container and restart it on its own, and it definitely doesn't notice an entire dead server and reschedule that server's work elsewhere.
- **Automatic scaling** — Docker won't decide on its own to run more copies of your API under load, or scale back down when the load passes.
- **Rolling, zero-downtime deployments** — coordinating "replace old version with new version, gradually, without ever dropping traffic" across many instances is not something `docker run` was ever designed to manage.
- **Service discovery & load balancing across containers** — with a handful of containers you might track IPs by hand; with hundreds moving around dynamically as they restart and reschedule, you need a system that knows where everything currently lives and routes accordingly.

Docker Compose gets you partway there for a **single machine** — multiple containers, wired together, described in one file. Kubernetes picks up exactly where Compose's usefulness runs out: **multiple machines, self-healing, scaling, and rollout strategies**, as a first-class part of the system rather than something you'd have to script yourself. Docker Swarm existed as Docker's own attempt at solving this same orchestration problem — simpler than Kubernetes, and largely lost the adoption battle for reasons that are more "ecosystem and momentum" than "fundamentally worse technology," but Kubernetes is what became the industry default, so that's what this document covers.

The honest framing: Docker answers "how do I package and run this." Kubernetes answers "how do I run potentially thousands of these, reliably, across many machines, without a human personally babysitting each one." They're not competitors — Kubernetes runs containers, and those containers are very often built using Docker (or another OCI-compatible tool) in the first place.

---

## 4. Core Architecture — The Control Plane and the Nodes

A Kubernetes cluster has two categories of machines:

**Control Plane** — the brain, making decisions:
- **API Server** — the front door; every single interaction with the cluster (from `kubectl`, from other components, from anything) goes through this.
- **etcd** — a distributed key-value store holding the entire cluster's actual state — every desired configuration, every current status, all of it. If etcd is lost, the cluster's memory of itself is lost with it.
- **Scheduler** — decides which node a newly-created pod should run on, based on resource availability and constraints.
- **Controller Manager** — runs the actual reconciliation loops described in section 2 — the process continuously comparing desired vs. actual state and issuing corrections.

**Worker Nodes** — the muscle, actually running things:
- **kubelet** — an agent on every node that talks to the API server and makes sure the containers that are *supposed* to be running on this specific node actually are.
- **Container runtime** — the actual thing running containers (containerd is the standard today; Docker itself is no longer used directly as Kubernetes' runtime, though images built by Docker work completely fine).
- **kube-proxy** — handles network routing so traffic reaches the right pod, even as pods move around and get recreated with new addresses.

You never talk to nodes directly in normal use — everything goes through the API server, usually via `kubectl`.

---

## 5. Easy Concepts — The Building Blocks You'll Use Constantly

### Pod — the smallest deployable unit (not a container itself)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-app-pod
spec:
  containers:
  - name: my-app
    image: myapp:1.0
    ports:
    - containerPort: 3000
```

A **Pod** wraps one or more containers that need to be scheduled and run together, sharing the same network namespace and storage. Almost always it's one container per pod — multi-container pods exist for specific patterns (a "sidecar" container helping the main one, like a log shipper) but are the exception, not the default. **You almost never create bare Pods directly in real usage** — you create a higher-level object (a Deployment, below) that manages Pods on your behalf, because a bare Pod that dies just stays dead; nothing recreates it.

### Deployment — the thing you'll actually use to run stateless apps

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        app: my-app
    spec:
      containers:
      - name: my-app
        image: myapp:1.0
        ports:
        - containerPort: 3000
```

A **Deployment** manages a set of identical Pods for you — declares "I want 3 replicas of this," and Kubernetes' reconciliation loop keeps that true, restarting crashed pods, rescheduling them if a node dies, and handling rolling updates when you change the image version. This is the object you reach for by default for basically any stateless service.

### Service — stable networking for a moving target

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-app-service
spec:
  selector:
    app: my-app
  ports:
  - port: 80
    targetPort: 3000
  type: ClusterIP
```

Pods are disposable and get new IP addresses every time they're recreated — so nothing should ever talk to a pod's IP directly. A **Service** gives a stable, unchanging name and IP that automatically load-balances across whichever pods currently match its label selector, no matter how many times those underlying pods have been recreated, rescheduled, or scaled up and down. This is the actual mechanism behind "my app can reliably talk to my database" inside a cluster, the same conceptual job Docker Compose's automatic container-name DNS did in the previous cheat sheet, just built for a much larger, constantly-shifting scale.

- `ClusterIP` (default) — reachable only from inside the cluster.
- `NodePort` — exposes the service on a static port on every node's own IP, reachable from outside.
- `LoadBalancer` — provisions an actual external cloud load balancer (on a cloud provider that supports it) pointing at the service.

### Namespace — a way to divide one cluster into logical sub-areas

```bash
kubectl create namespace staging
```

Namespaces let you separate resources — different teams, different environments (staging vs. production), different projects — inside the same physical cluster, with their own scoping for names and, optionally, their own resource quotas and access controls.

---

## 6. Essential `kubectl` Commands

```bash
kubectl get pods                          # list pods in the current namespace
kubectl get pods -o wide                     # with more detail (node, IP)
kubectl get pods -A                            # across ALL namespaces
kubectl get deployments
kubectl get services
kubectl get nodes

kubectl describe pod my-app-pod              # detailed info + recent events — the first stop when debugging
kubectl logs my-app-pod                         # view a pod's logs
kubectl logs -f my-app-pod                        # follow logs live
kubectl logs my-app-pod -c container-name           # specify which container, for multi-container pods

kubectl exec -it my-app-pod -- bash             # shell into a running pod
kubectl port-forward my-app-pod 8080:3000          # tunnel a local port to a port inside the pod, for local debugging

kubectl apply -f deployment.yaml                 # create or update resources from a YAML file — the standard way to deploy
kubectl delete -f deployment.yaml                   # remove resources described in that file
kubectl delete pod my-app-pod                          # delete a specific pod directly (a Deployment will just recreate it)

kubectl scale deployment my-app --replicas=5      # scale a deployment up or down
kubectl rollout status deployment my-app             # watch a rollout in progress
kubectl rollout undo deployment my-app                 # roll back to the previous version
kubectl rollout history deployment my-app                 # see revision history

kubectl config get-contexts                        # list known clusters
kubectl config use-context my-cluster                 # switch which cluster kubectl is talking to
```

**`kubectl apply` is the command you'll type more than any other one in this document** — it's the declarative "make the cluster match this file" command, safe to run repeatedly, and it's what turns a YAML file into the actual desired state Kubernetes' reconciliation loop then works to maintain.

---

## 7. Medium Concepts — Configuration, Storage & Health

### ConfigMap & Secret — separating configuration from the container image

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  LOG_LEVEL: "info"
  MAX_CONNECTIONS: "100"
```

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: app-secret
type: Opaque
data:
  DB_PASSWORD: cGFzc3dvcmQ=    # base64-encoded — NOT encrypted, just encoded, worth knowing plainly
```

```yaml
# using them inside a pod spec
containers:
- name: my-app
  image: myapp:1.0
  envFrom:
  - configMapRef:
      name: app-config
  env:
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: app-secret
        key: DB_PASSWORD
```

Same philosophy as the Docker cheat sheet's "never bake secrets into an image" — **ConfigMaps** hold non-sensitive configuration, **Secrets** hold sensitive values, both injected at runtime rather than baked into the image. The honest caveat worth repeating clearly: a Kubernetes Secret is **base64-encoded, not encrypted**, by default — genuinely just obfuscated, trivially decodable by anyone with read access to it. Real secret protection at rest requires enabling encryption at the etcd level or reaching for an external secrets manager (Vault, cloud provider secret managers) — the built-in Secret object alone is a convention for separating sensitive config, not a security boundary on its own.

### Liveness & Readiness Probes — how Kubernetes knows if a pod is actually healthy

```yaml
containers:
- name: my-app
  image: myapp:1.0
  livenessProbe:
    httpGet:
      path: /healthz
      port: 3000
    initialDelaySeconds: 10
    periodSeconds: 10
  readinessProbe:
    httpGet:
      path: /ready
      port: 3000
    initialDelaySeconds: 5
    periodSeconds: 5
```

A container can be **running** without being **healthy** — the process hasn't crashed, but it might be deadlocked, stuck, or simply not ready to serve traffic yet. **Liveness probes** answer "is this container alive, or should it be killed and restarted." **Readiness probes** answer a different question: "is this container ready to receive traffic right now" — a pod that fails its readiness probe gets pulled out of a Service's load-balancing rotation without being killed, which matters enormously during startup (still loading data, warming a cache) or brief overload, where restarting the container would make things worse, not better.

### PersistentVolume & PersistentVolumeClaim — storage that outlives a pod

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: my-app-storage
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
```

Same underlying need as Docker's volumes — pods are disposable, and anything that needs to survive a pod being recreated needs to live outside it — just abstracted for a multi-node cluster where "outside the pod" might mean a completely different physical machine than the one the pod eventually gets rescheduled onto. A **PersistentVolume (PV)** represents actual storage (a cloud disk, an NFS share, etc.); a **PersistentVolumeClaim (PVC)** is how a pod requests some of that storage without needing to know the underlying storage details — you claim capacity, Kubernetes matches it to available storage.

### Resource Requests & Limits — telling the scheduler what a pod actually needs

```yaml
containers:
- name: my-app
  image: myapp:1.0
  resources:
    requests:
      cpu: "250m"          # 250 millicores = a quarter of one CPU core
      memory: "256Mi"
    limits:
      cpu: "500m"
      memory: "512Mi"
```

**Requests** are what the scheduler guarantees is reserved for this pod when deciding which node to place it on. **Limits** are the hard ceiling — exceed the memory limit and the container gets killed (OOMKilled); exceed the CPU limit and it just gets throttled, not killed. Skipping this entirely is a common early mistake — an unconstrained pod can consume far more of a node's resources than intended, starving its neighbors, which is exactly the resource-isolation problem cgroups were supposed to prevent at the container level in the first place, just recreated one layer up if nobody sets these.

---

## 8. Hard Concepts — Where Real Production Clusters Live

### StatefulSet — for workloads that aren't interchangeable

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: my-database
spec:
  serviceName: my-database
  replicas: 3
  selector:
    matchLabels:
      app: my-database
  template:
    metadata:
      labels:
        app: my-database
    spec:
      containers:
      - name: db
        image: postgres:16
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 10Gi
```

A Deployment treats every pod as identical and interchangeable — kill one, start any new one, nobody cares which. That assumption breaks for databases and other stateful systems, where each instance might have its own persistent identity, its own dedicated storage, and needs to come back up as *the same instance*, not just any interchangeable replica. **StatefulSets** give each pod a stable, predictable name (`my-database-0`, `my-database-1`, `my-database-2`) and its own dedicated PersistentVolumeClaim that follows that specific pod across restarts and rescheduling — the object you reach for when "which specific instance this is" actually matters, not just "how many instances exist."

### DaemonSet — exactly one pod per node, automatically

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: log-collector
spec:
  selector:
    matchLabels:
      app: log-collector
  template:
    metadata:
      labels:
        app: log-collector
    spec:
      containers:
      - name: log-collector
        image: fluentd:latest
```

Used for infrastructure-level agents that need to run on *every* node — log collectors, monitoring agents, network plugins. As new nodes join the cluster, a DaemonSet automatically schedules a copy onto them too, with no manual intervention.

### Horizontal Pod Autoscaler (HPA) — automatic scaling based on real load

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: my-app-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: my-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

This is the reconciliation loop from section 2 applied directly to scaling — watches actual CPU (or memory, or a custom metric) usage across the Deployment's pods, and adjusts `replicas` up or down automatically to keep utilization near the target, between the min and max bounds you set. This requires the **metrics-server** component running in the cluster to actually have utilization data to act on.

### Ingress — HTTP-aware routing into the cluster

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-app-ingress
spec:
  rules:
  - host: myapp.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: my-app-service
            port:
              number: 80
```

A `LoadBalancer`-type Service gets you one external IP per service — fine for one service, expensive and awkward once you have dozens, each wanting its own cloud load balancer. **Ingress** sits in front of many Services and routes based on hostname and URL path (`myapp.example.com/api` → one service, `myapp.example.com/admin` → another), needing only a single entry point, plus TLS termination in one place instead of configuring it per service. It requires an **Ingress Controller** (nginx-ingress, Traefik, cloud-provider-specific ones) actually running in the cluster to do anything — the Ingress object itself is just the routing rule, not the thing that executes it.

### Helm — package management for Kubernetes YAML

```bash
helm install my-release bitnami/postgresql        # install a pre-packaged application ("chart") into the cluster
helm upgrade my-release bitnami/postgresql --set auth.password=newpass
helm uninstall my-release
helm list
```

Real applications often need a dozen-plus YAML files (Deployment, Service, ConfigMap, Secret, Ingress, HPA...) with values that repeat and need templating across environments (staging vs. production). **Helm** packages all of that into a reusable "chart" with configurable values — the closest Kubernetes equivalent to a package manager, letting you install, upgrade, and roll back an entire multi-resource application as one versioned unit instead of hand-managing a folder of loosely related YAML files.

---

## 9. Quick Reference: Commands You'll Actually Type Daily

```bash
kubectl get pods
kubectl get pods -A
kubectl describe pod <name>
kubectl logs -f <pod-name>
kubectl exec -it <pod-name> -- bash

kubectl apply -f <file>.yaml
kubectl delete -f <file>.yaml
kubectl scale deployment <name> --replicas=<n>

kubectl rollout status deployment <name>
kubectl rollout undo deployment <name>

kubectl get services
kubectl get deployments
kubectl config use-context <cluster>
```

---

## 10. What Kubernetes Is Actually For (and What It Isn't)

Kubernetes' actual sweet spot: **running many containers, reliably, across many machines, with self-healing, scaling, and controlled rollouts as built-in, first-class behavior** — rather than something a team scripts and re-scripts by hand every time reality (crashes, traffic spikes, dead hardware, new versions) intrudes on the plan. It's the direct, necessary answer to exactly the gap section 1 opened with — Docker gives you the portable unit, Kubernetes gives you the operational discipline to run a fleet of those units without a human personally watching all of them.

What it's genuinely **not** the right tool for: a single small application on a single server, where the operational overhead of running an entire cluster (control plane, etcd, networking layer, all of it) is dramatically more complexity than the actual problem calls for — plain Docker or Docker Compose remains the right-sized tool there, and reaching for Kubernetes purely because it's the industry-standard name is a common, genuinely costly overcorrection. It's also not a magic fix for a poorly-designed application — same honest caveat as the Docker cheat sheet's closing point: Kubernetes makes a fleet of containers operationally manageable at scale. It does not make an individual application correct, performant, or well-architected; it just makes sure whatever you did build keeps running, keeps scaling, and keeps rolling out new versions the way you declared you wanted, reliably, whether or not the thing being run was actually built well in the first place.

---

*Pairs with the Docker cheat sheet directly before it in this series — Docker answers "how do I package and run this consistently," Kubernetes answers "how do I run a fleet of these reliably, without babysitting them by hand."*
