# Flame Launcher Cloud Agent

This folder contains the separate eve-based cloud agent for maintaining the Flame Launcher repository.

## Current implementation

- `agent/agent.ts` defines the Eve agent and selects a model through Vercel AI Gateway.
- `agent/instructions.md` defines its Android-first Flame Launcher mission and strict human-approval boundaries.
- `agent/schedules/heartbeat.md` defines a 15-minute maintenance heartbeat. The schedule is in source control but has not yet passed a successful deployment or runtime test.
- The Git repository is connected to a Vercel project whose root directory is `agent/`.

This is an initial agent definition, not yet a verified autonomous coding service. GitHub agent tools, sandbox test execution, persistent checkpoint behavior, notification delivery, and production deployment still need implementation and verification.

## Project

- Product: Flame Launcher
- Owner: MFLegend
- Source repository: https://github.com/hsudais201-ops/myLauncher
- Runtime/framework: eve
- Intended hosting: Vercel

A scheduled agent wakes on its configured schedule, performs bounded work, and parks until the next run. It does not continuously generate code every second.

## Local development

Requires Node.js 24 or newer and an authenticated Vercel/eve setup.

```bash
cd agent
npm install
npm run dev
```

Build:

```bash
npm run build
```

Deploy only after reviewing authentication, tool permissions, secrets, schedule configuration, and the deployment preview:

```bash
npm run deploy
```

## Required setup before autonomous repository work

1. Verify the Eve agent build and HTTP session API.
2. Configure an authenticated model through Vercel AI Gateway.
3. Configure a scoped GitHub integration for this exact repository and add typed, least-privilege inspection and pull-request tools.
4. Configure an isolated sandbox for tests and builds.
5. Configure durable checkpoint storage and a notification channel.
6. Verify the scheduled heartbeat and its manual test run before enabling recurring work.
7. Verify the health route and confirm runtime logs before treating the service as operational.

Do not put tokens or API keys in this repository. Use Vercel-managed connections or encrypted environment secrets. Do not enable broad GitHub organization access when repository-scoped access is sufficient.

## Safety policy

The agent may inspect repository content and prepare changes on feature branches. It must never merge pull requests, push to the default branch, publish releases, deploy production app releases, delete resources, change account permissions, or spend money without explicit human approval.
