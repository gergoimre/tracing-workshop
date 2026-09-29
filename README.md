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

The stack runs 4 JVM services plus Tempo, Grafana, and the OTel Collector.
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

## Verify the environment

```bash
./scripts/verify-environment.sh
```

## Generate traffic

```bash
./scripts/generate-traffic.sh
```

## Tracing stack

Tracing uses **Spring Boot's native Micrometer Tracing** (`micrometer-tracing-bridge-otel`) — no Java agent. The OTel SDK is managed by Spring auto-config; spans are exported via OTLP to the collector, stored in Tempo, and visualised in Grafana.

## Services

| Service | URL | Role |
|---|---|---|
| Transfer | http://localhost:8080 | Public API entry point |
| Pricing  | http://localhost:8081 | Route strategy + FX aggregation |
| FX       | http://localhost:8082 | Simulated exchange-rate provider |
| Support  | http://localhost:8083 | Customer / limits / compliance / routing |
| Grafana  | http://localhost:3000 | Trace UI |
| Tempo    | http://localhost:3200 | Trace storage |

## Architecture

```
                          ┌─────────────────┐
                          │     Grafana     │
                          └────────▲────────┘
                                   │
                          ┌────────┴────────┐
                          │      Tempo      │
                          └────────▲────────┘
                                   │
                          ┌────────┴────────┐
                          │ OTel Collector  │
                          └────────▲────────┘
                                   │ OTLP
   ┌───────────────────────────────┼──────────────────────────┐
   │                               │                          │
┌──┴───────────┐          ┌────────┴───────┐         ┌────────┴───────┐
│  Transfer    │          │   Pricing      │         │  FX / Support  │
│  Service     │─────────►│   Service      │────────►│  Services      │
└──────────────┘          └────────────────┘         └────────────────┘
```

## Troubleshooting

**Services not starting / OOM**
Restart Colima with more memory: `colima stop && colima start --cpu 4 --memory 8`

**Port already in use**
Check for conflicting local processes on 8080–8083, 3000, 3200, 4317, 4318.

**Traces not appearing in Grafana**
Wait ~30 s after the first request. The OTel Collector batches before forwarding.
Check `docker compose logs otel-collector` for errors.

**Build fails**
Make sure Colima is running before `./start.sh`.
