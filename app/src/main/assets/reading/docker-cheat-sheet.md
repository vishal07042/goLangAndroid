# The Docker Cheat Sheet: Containers to Commands

A reference doc — starts with why this exists at all, then becomes the thing you actually keep open in a terminal tab.

---

## 1. What Problem Docker Actually Solves

The pitch everyone's heard: **"it works on my machine."** The actual problem underneath that meme is more specific — an application depends on a whole stack of things beyond its own code: a particular language runtime version, specific library versions, OS-level packages, environment variables, file paths, configuration files sitting in the right place. Get any one of those wrong between your laptop, your teammate's laptop, the CI server, and production, and you get behavior that's inconsistent in ways that are miserable to debug — code that's provably correct, running on an environment that's subtly different from the one it was tested on.

Docker's actual solution: **package the application together with its entire runtime environment — code, dependencies, system libraries, configuration — into a single, portable unit that runs identically anywhere Docker itself runs.** Not "should run the same," not "runs the same if you set it up right" — the same filesystem, the same dependency versions, the same environment, byte for byte, whether that's your laptop, a coworker's laptop, a CI runner, or a production server on the other side of the planet.

This is the whole value proposition in one sentence: **Docker doesn't make your code more correct. It makes your environment reproducible, so "it works on my machine" stops being a meaningful distinction from "it works everywhere."**

---

## 2. How It Actually Solves It — Containers, Not Virtual Machines

The mechanism is **OS-level virtualization**, using features already built into the Linux kernel — **namespaces** and **cgroups** — rather than emulating an entire computer from scratch.

- **Namespaces** give a process its own isolated view of the system — its own process list, its own network interfaces, its own filesystem mount points, its own hostname — so a process running inside a container genuinely cannot see or touch what's happening in other containers or on the host, even though it's still, underneath, an ordinary process on the same physical machine.
- **cgroups (control groups)** limit and account for how much CPU, memory, disk I/O, and network bandwidth a process (or group of processes) is allowed to consume — the mechanism that stops one container from starving every other container on the same host of resources.

Put those two together and you get **isolation without duplication**: every container shares the host machine's actual kernel, but each one believes it has its own private, self-contained operating system environment. Nothing is being emulated. Nothing is being translated. It's the same kernel, doing the same syscalls, just with a set of walls built around what each process is allowed to see and use.

---

## 3. Docker vs. a Hypervisor (VM) — Why This Wasn't Just "Better VMs"

This is the part worth actually understanding, not just memorizing as trivia.

**A hypervisor-based virtual machine** (VMware, VirtualBox, KVM, Hyper-V) virtualizes the entire hardware layer — CPU, memory, disk, network interfaces — and then boots a **complete, independent operating system kernel** on top of that virtual hardware, for every single VM. Want to run five isolated environments? You're booting five full kernels, each with its own OS, each consuming its own chunk of RAM and disk just to exist before your actual application has done anything at all.

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   App A     │  │   App B     │  │   App C     │
│  Guest OS   │  │  Guest OS   │  │  Guest OS   │   <- full kernel per VM
├─────────────┴──┴─────────────┴──┴─────────────┤
│                  Hypervisor                      │
├───────────────────────────────────────────────┤
│                 Host OS + Hardware               │
└───────────────────────────────────────────────┘
```

**A container** shares the host's single kernel across every container running on that machine. There's no guest OS to boot, no virtual hardware to emulate — just a process, isolated by namespaces and constrained by cgroups, that happens to believe it's the only thing running.

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   App A     │  │   App B     │  │   App C     │
│  (+ libs)   │  │  (+ libs)   │  │  (+ libs)   │      <- no separate kernel per container
├─────────────┴──┴─────────────┴──┴─────────────┤
│                Docker Engine                      │
├───────────────────────────────────────────────┤
│                 Host OS + Hardware               │
└───────────────────────────────────────────────┘
```

**Why the hypervisor approach wasn't good enough on its own** — it's not that VMs are bad, they solve real problems (strong security isolation, running genuinely different OS kernels side by side, hosting untrusted multi-tenant workloads). The problem is cost and speed for the specific job of "I just need to run my application in a consistent environment":

- **Boot time:** a VM boots an entire OS kernel — tens of seconds, sometimes minutes. A container starts as fast as launching an ordinary process — often under a second, because there's no kernel to boot, just a process to start.
- **Resource overhead:** every VM reserves its own chunk of RAM and disk for its own OS, whether or not the application inside is doing anything. A container has no separate OS to keep resident in memory — the overhead is close to just the application itself.
- **Density:** a physical server might comfortably run a dozen VMs. That same server can often run hundreds of containers, because you're not paying the "full OS tax" per unit of isolation.
- **Image size & portability:** a VM image includes an entire OS — gigabytes, easily. A container image can be a genuinely tiny handful of megabytes, because it only needs to include what your specific application actually depends on, not a whole standalone operating system.

The honest summary: hypervisors solve isolation by duplicating the entire machine, which is thorough but expensive. Containers solve the *specific* problem of "reproducible, portable application environments" by isolating at the process level instead, which is dramatically cheaper and faster precisely because it's solving a narrower, more common problem — and for the workload most people actually have (deploy my app consistently, run many of them densely, start them instantly), that narrower solution is the better fit. VMs didn't get replaced — they're still the right tool when you genuinely need a different kernel or hard security boundaries between tenants. Containers just turned out to be the right tool for a much more common, much less demanding job that VMs had been (over-)solving for years.

---

## 4. Core Concepts & Vocabulary

- **Image** — a read-only template: your application code, its dependencies, and instructions for how to run it, all bundled together. Think of it as a class in OOP terms.
- **Container** — a running (or stopped) instance of an image. Think of it as an object — an actual instantiated, live thing, with its own state, created from that class.
- **Dockerfile** — a text file of instructions describing how to build an image, step by step.
- **Registry** — a place images are stored and pulled from — Docker Hub is the public default, but private registries (ECR, GCR, self-hosted) are the norm in real companies.
- **Volume** — a mechanism for persisting data outside a container's own writable layer, so data survives even after the container is deleted.
- **Network** — a virtual network Docker creates so containers can talk to each other (and to the outside world) in a controlled way.

---

## 5. Your First Dockerfile

```dockerfile
FROM node:20-alpine          # base image — start from something, don't build from scratch every time

WORKDIR /app                  # sets the working directory inside the container

COPY package*.json ./          # copy dependency manifests FIRST (see layer caching, below)
RUN npm install                  # install dependencies

COPY . .                          # copy the rest of the application code

EXPOSE 3000                        # documents which port the app listens on (doesn't actually publish it — see -p below)

CMD ["node", "server.js"]           # the default command run when the container starts
```

```bash
docker build -t myapp:1.0 .        # build an image from the Dockerfile in the current directory, tag it myapp:1.0
docker run -p 3000:3000 myapp:1.0   # run it, mapping host port 3000 to container port 3000
```

### Why `COPY package*.json` happens before `COPY . .` — layer caching, explained properly

Docker builds an image as a stack of **layers**, one per instruction, and caches each layer. If a layer's inputs haven't changed since the last build, Docker reuses the cached result instead of redoing the work. Dependency manifests (`package.json`, `requirements.txt`, `go.mod`) change far less often than your actual application code — so copying them first and running the install step *before* copying the rest of your code means that as long as your dependencies haven't changed, Docker skips re-running the entire (often slow) install step on every single build, even if you just changed one line of application logic. Get this ordering backwards and every code change forces a full dependency reinstall, which is the single most common "why is my Docker build so slow" complaint, with a one-line fix.

### CMD vs. ENTRYPOINT — the distinction everyone mixes up once

```dockerfile
ENTRYPOINT ["python", "app.py"]     # the fixed, always-run command
CMD ["--port", "8080"]                # default ARGUMENTS to that command — easily overridden
```

`CMD` alone sets the default command, fully overridable by anything passed to `docker run`. `ENTRYPOINT` sets a command that's much harder to override (you'd need `--entrypoint` explicitly), with `CMD`'s contents supplied as its default arguments. Use `CMD` alone for "here's what runs by default." Use `ENTRYPOINT` + `CMD` together when you want a fixed executable with overridable default flags.

---

## 6. Essential Commands: Images

```bash
docker build -t myapp:1.0 .              # build an image, tag it
docker images                              # list local images
docker rmi myapp:1.0                        # remove an image
docker pull nginx:latest                      # download an image from a registry
docker push myrepo/myapp:1.0                    # upload an image to a registry
docker tag myapp:1.0 myrepo/myapp:1.0             # add another tag to an existing image
docker history myapp:1.0                            # show the layer-by-layer build history
```

---

## 7. Essential Commands: Containers

```bash
docker run myapp:1.0                    # create + start a new container
docker run -d myapp:1.0                    # detached — runs in the background, returns your terminal
docker run -it ubuntu bash                   # interactive + tty — drop into a shell inside a container
docker run --name myapp-c1 myapp:1.0            # give the container a memorable name instead of a random one
docker run --rm myapp:1.0                          # auto-remove the container when it stops — great for one-offs

docker ps                                 # list running containers
docker ps -a                                # list ALL containers, including stopped ones

docker stop myapp-c1                        # graceful stop (sends SIGTERM, then SIGKILL after a timeout)
docker start myapp-c1                         # start a previously stopped container
docker restart myapp-c1                         # stop then start

docker rm myapp-c1                          # remove a stopped container
docker rm -f myapp-c1                          # force-remove, even if running

docker logs myapp-c1                        # view a container's stdout/stderr
docker logs -f myapp-c1                        # follow logs live, like `tail -f`

docker exec -it myapp-c1 bash                # run an additional command inside an ALREADY-running container
docker inspect myapp-c1                         # full JSON metadata — IP address, mounts, env vars, everything

docker stats                                  # live resource usage (CPU, memory, network) across containers
```

**`docker run` vs `docker exec`, the distinction that trips people up:** `run` creates a brand-new container from an image. `exec` runs an additional process inside a container that's already running. Trying to `docker exec` your way into a container that's already stopped will just fail — there's no running process to attach to.

---

## 8. Port Mapping & Environment Variables

```bash
docker run -p 8080:80 nginx           # host_port:container_port — host 8080 -> container's port 80
docker run -p 127.0.0.1:8080:80 nginx    # bind only to localhost on the host, not all interfaces

docker run -e NODE_ENV=production myapp    # set a single environment variable
docker run --env-file .env myapp              # load a whole file of environment variables
```

`EXPOSE` in a Dockerfile is documentation, not enforcement — it doesn't actually publish anything to the host by itself. `-p` at `docker run` time is what actually creates the mapping. People confuse these constantly and then wonder why their `EXPOSE 3000` didn't make anything reachable from the browser.

---

## 9. Volumes & Persistence — Containers Are Meant to Be Disposable

By default, anything written inside a container's filesystem disappears the moment that container is removed — this is intentional, not a bug. Containers are meant to be treated as disposable, replaceable, stateless-by-default units. Anything you actually need to survive a container's death needs to live outside it.

```bash
docker volume create mydata               # create a named, Docker-managed volume
docker run -v mydata:/app/data myapp          # mount it into the container at /app/data

docker run -v $(pwd)/localfolder:/app/data myapp   # bind mount — maps an actual host directory in directly

docker volume ls                             # list volumes
docker volume rm mydata                        # remove a volume
docker volume prune                              # remove all unused volumes
```

**Named volumes** (managed entirely by Docker, living somewhere under Docker's own storage area) are the right choice for things like database data directories — portable, easy to back up, not tied to a specific host path. **Bind mounts** (a direct path on the host filesystem, mapped straight in) are the right choice for local development — edit a file on your host machine, see the change reflected instantly inside the running container, without rebuilding anything.

---

## 10. Networking Between Containers

```bash
docker network create mynetwork              # create a custom network
docker run --network mynetwork --name db postgres     # attach a container to it
docker run --network mynetwork --name api myapp          # attach another container to the same network

docker network ls                                # list networks
docker network inspect mynetwork                    # see what's attached
```

Containers on the same custom network can reach each other **by container name** as a hostname — `api` can connect to `db:5432` directly, no IP addresses or manual DNS setup required, because Docker runs an internal DNS resolver for exactly this. This is the actual mechanism underneath most "my app talks to my database" multi-container setups, before Compose comes along and does the wiring for you automatically.

---

## 11. Docker Compose — Running Multiple Containers as One Unit

Real applications are rarely a single container — an API, a database, a cache, maybe a message queue, all needing to run together, talk to each other, and start up in a sane order. **Compose** describes that whole stack in one YAML file instead of a pile of individually-typed `docker run` commands.

```yaml
# docker-compose.yml
version: "3.9"

services:
  api:
    build: .
    ports:
      - "3000:3000"
    environment:
      - DATABASE_URL=postgres://user:pass@db:5432/mydb
    depends_on:
      - db
    volumes:
      - .:/app

  db:
    image: postgres:16
    environment:
      - POSTGRES_USER=user
      - POSTGRES_PASSWORD=pass
      - POSTGRES_DB=mydb
    volumes:
      - dbdata:/var/lib/postgresql/data

volumes:
  dbdata:
```

```bash
docker compose up                # build (if needed) and start everything defined in the file
docker compose up -d               # detached
docker compose down                  # stop and remove containers, networks (add -v to also remove volumes)
docker compose ps                      # list the services and their status
docker compose logs -f api                # follow logs for one specific service
docker compose exec api bash                # exec into a running service by name
docker compose build                          # rebuild images without starting containers
```

Compose automatically creates a shared network for every service defined in the file, so `api` can reach `db` by that exact name — same underlying networking mechanism as the manual `docker network create` above, just handled for you. `depends_on` controls **start order**, not "wait until the database is actually ready to accept connections" — a genuinely common source of race-condition bugs on first boot, usually solved with an app-level retry-on-connect loop or a dedicated healthcheck.

---

## 12. Multi-Stage Builds — Small, Production-Ready Images

A naive Dockerfile often bundles your entire build toolchain — compilers, dev dependencies, source maps — into the final image, when production only actually needs the compiled output. **Multi-stage builds** solve this by using multiple `FROM` statements in one Dockerfile, where later stages selectively copy only what they need from earlier ones.

```dockerfile
# Stage 1: build
FROM golang:1.22 AS builder
WORKDIR /app
COPY . .
RUN go build -o server .

# Stage 2: run — a much smaller, minimal final image
FROM alpine:latest
COPY --from=builder /app/server /server
CMD ["/server"]
```

The final image contains just the compiled binary and a minimal base OS — not the entire Go toolchain that was needed to build it. This is routinely the difference between a several-hundred-megabyte image and one that's a handful of megabytes, with zero change to the actual application behavior — purely a build-process optimization.

---

## 13. Image Size & Build Optimization

```dockerfile
FROM node:20-alpine        # alpine variants are dramatically smaller than the default full-OS base images
```

```
# .dockerignore
node_modules
.git
*.log
.env
```

- Prefer **slim/alpine base images** over full OS images when your application allows it — the difference between a few hundred MB and a few dozen MB, purely from base image choice.
- Use a **`.dockerignore`** file, same idea as `.gitignore` — without it, `COPY . .` happily drags your entire `node_modules`, `.git` history, and stray log files into the build context, bloating both build time and image size for no benefit.
- **Combine `RUN` instructions** where it makes sense (`RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*`) — each `RUN` creates a new layer, and cleanup done in a *separate* later `RUN` doesn't actually shrink the earlier layer, it just adds a new layer on top that hides the files without reclaiming the space.
- **Order instructions from least-to-most frequently changing** (as covered in the layer caching section) to maximize cache hits on rebuilds.

---

## 14. Debugging & Troubleshooting

```bash
docker logs -f --tail 100 mycontainer         # last 100 lines, then follow live
docker exec -it mycontainer sh                    # drop into a shell (sh, not bash, on alpine-based images)
docker inspect mycontainer                            # full container metadata as JSON
docker inspect --format='{{.NetworkSettings.IPAddress}}' mycontainer   # extract just one field

docker system df                                # disk space used by images, containers, volumes
docker system prune                                # clean up unused containers, networks, dangling images
docker system prune -a                               # also remove unused images, not just dangling ones
docker system prune --volumes                          # also remove unused volumes — use with real caution

docker cp mycontainer:/app/logs/error.log ./error.log    # copy a file OUT of a container to inspect locally
```

A container that exits immediately after `docker run` almost always means the main process itself crashed or exited on its own — `docker logs` on that (now-stopped) container is the very first place to look, not a sign that Docker itself is broken.

---

## 15. Security Basics Worth Actually Doing

- **Don't run as root inside the container** if you can help it — add a `USER` instruction in the Dockerfile to drop privileges before the app actually runs.
- **Never bake secrets into an image** — API keys and passwords `COPY`'d or `ENV`'d directly into a Dockerfile live forever in that image's layer history, retrievable by anyone who can pull it, even if you "remove" them in a later layer. Use environment variables passed at runtime, secrets managers, or Docker/Compose's dedicated secrets handling instead.
- **Pin specific image versions** (`node:20.11-alpine`) rather than floating tags like `latest` — `latest` is a moving target, not a version, and it can silently change what you're actually building on top of between builds.
- **Scan images for known vulnerabilities** — `docker scout` (built into modern Docker CLI) or third-party tools like Trivy check your image's dependencies against known CVE databases before you ship it.

```dockerfile
FROM node:20-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

---

## 16. Quick Reference: The Commands You'll Actually Type Daily

```bash
docker build -t name:tag .
docker run -d -p host:container --name mycontainer name:tag
docker ps
docker ps -a
docker logs -f mycontainer
docker exec -it mycontainer bash
docker stop mycontainer
docker rm mycontainer
docker images
docker rmi name:tag

docker compose up -d
docker compose down
docker compose logs -f
docker compose exec service bash

docker system prune
```

---

## 17. What Docker Is Actually For (and What It Isn't)

Docker's actual sweet spot is exactly what it was built to solve: **consistent, portable, reproducible application environments, packaged and shipped as a single unit** — development-to-production parity, CI pipelines that behave identically to a developer's laptop, and dense, fast-starting deployment of many small services (microservices, batch jobs, anything cloud-native — this is the same reason Docker and Go's ecosystem overlap so heavily, since a static Go binary in a minimal container image is about as small and fast-starting as this pattern gets).

What it's genuinely **not** the right tool for: strong multi-tenant security isolation between mutually untrusted workloads (containers share a kernel — a serious container-escape vulnerability is a bigger blast radius than a hypervisor-level VM escape, historically rarer but not impossible), running an entirely different OS kernel than the host (Windows containers on a Linux host, for instance, genuinely need a VM layer underneath to bridge that gap), and — despite how often people reach for it this way — a substitute for actually understanding what your application depends on. Docker makes an inconsistent environment reproducible. It does not make a fundamentally fragile application robust; it just makes sure everyone gets to run the same fragile application identically, everywhere, which is a smaller, more honest promise than the marketing sometimes implies, and exactly the promise that made "it works on my machine" stop being an acceptable sentence to say out loud in a standup.

---

*Pairs well with the Go, Rust, and Python cheat sheets in this series — the language you write the app in, and the box you ship it in, are two different problems, and this is the second one.*
