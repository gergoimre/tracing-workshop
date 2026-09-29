# Tracing Workshop

A fully local Kotlin/Spring distributed tracing workshop. You will investigate
real performance problems using OpenTelemetry traces — the kind that are
invisible in logs and impossible to reproduce with breakpoints in production.

## Prerequisites

| Requirement | Version |
|---|---|
| Colima | any recent |
| Docker CLI | ≥ 24 |
| Docker Compose plugin | ≥ 2 |
| curl | any |

### Start Colima with enough resources

The stack runs 13 JVM services plus Tempo, Grafana, Loki, and the OTel Collector.
Default Colima limits (2 CPU / 2 GB) are not enough.

```bash
colima start --cpu 4 --memory 8 --disk 40
```

## Quick start

```bash
git clone <repository-url>
cd tracing-workshop
./start.sh
```

Once all health checks pass, you will see:

```
╔══════════════════════════════════════════╗
║      Workshop environment is ready!      ║
╠══════════════════════════════════════════╣
║  API:     http://localhost:8080          ║
║  Grafana: http://localhost:3000          ║
╚══════════════════════════════════════════╝
```

Grafana opens directly on the **Workshop — Trace Explorer** dashboard.

## Verify the environment

```bash
./scripts/verify-environment.sh
```

Checks all 13 application services and the 3 observability components.

## Generate traffic

```bash
./scripts/generate-traffic.sh
```

## Tracing stack

Tracing uses **Spring Boot's native Micrometer Observation** (`@Observed` annotation)
bridged to the OpenTelemetry SDK via `micrometer-tracing-bridge-otel` — no Java agent.
Spans are exported via OTLP to the OTel Collector, stored in Tempo, and visualised in Grafana.

## Services

### Application services

| Service | Port | Role |
|---|---|---|
| transfer | 8080 | Public API entry point |
| pricing | 8081 | Route strategy + FX aggregation |
| fx | 8082 | Simulated exchange-rate provider |
| support | 8083 | Customer limits + compliance + routing |
| auth | 8084 | Token validation → session |
| session | 8085 | Session store |
| risk | 8086 | Risk scoring → device + screening |
| device | 8087 | Device fingerprinting |
| ledger | 8088 | Fund reservation + commit |
| accounts | 8089 | Account balances |
| notification | 8090 | Email / push notifications |
| audit | 8091 | Audit event log |
| screening | 8092 | Watchlist screening |

### Observability stack

| Component | Port | Role |
|---|---|---|
| Grafana | 3000 | Dashboards + trace UI |
| Tempo | 3200 | Trace storage + TraceQL |
| Loki | 3100 | Log storage |
| OTel Collector | 4317 / 4318 | Span ingestion (gRPC / HTTP) |

## Architecture

```
                          ┌─────────────────┐
                          │     Grafana     │
                          └────────▲────────┘
                                   │
                     ┌─────────────┴─────────────┐
                     │      Tempo   │    Loki     │
                     └─────────────┬─────────────┘
                                   │
                          ┌────────┴────────┐
                          │ OTel Collector  │
                          └────────▲────────┘
                                   │ OTLP (HTTP :4318)
   ┌───────────────────────────────┼──────────────────────────────────┐
   │                               │                                  │
┌──┴──────────┐           ┌────────┴───────┐          ┌──────────────┴──────┐
│  transfer   │──────────►│    pricing     │─────────►│  fx  (×3 FX calls)  │
└──┬──────────┘           └────────────────┘          └─────────────────────┘
   │
   ├──► auth ──► session
   ├──► support  (limits / compliance / routing)
   ├──► risk ──► device
   │         └──► screening
   ├──► notification ──► audit
   └──► audit
```

## Troubleshooting

**Services not starting / OOM**
Restart Colima with more memory: `colima stop && colima start --cpu 4 --memory 8`

**Port already in use**
Check for conflicting local processes on ports 8080–8092, 3000, 3100, 3200, 4317, 4318.

**Traces not appearing in Grafana**
Wait ~30s after the first request. The OTel Collector batches before forwarding.
Check `docker compose logs otel-collector` for errors.

**Build fails**
Make sure Colima is running before `./start.sh`.

**Image already exists error**
The `start.sh` script handles this automatically. If you see it outside of `start.sh`:
```bash
docker rmi tracing-workshop-app:latest --force
docker build -f docker/Dockerfile -t tracing-workshop-app:latest .
docker compose up -d
```
