# Flame Launcher Cloud Agent

This folder contains the separate eve-based cloud agent for maintaining the Flame Launcher repository.

## What it is

- A durable cloud agent, independent of the owner's phone or Chromebook.
- A scheduled maintenance heartbeat (15-minute cadence on Vercel Hobby, subject to platform limits).
- A bounded engineering workflow: inspect, plan, implement on a feature branch, test, and open a pull request.
- Human approval remains required for merging, releases, production deployments, destructive actions, permission changes, and spending.

A scheduled agent is not a process continuously generating code every second. It wakes on its schedule, performs bounded work, and parks until the next run. The hosted service and durable state are managed by the platform.

## Project

- Product: Flame Launcher
- Owner: MFLegend
- Source repository: https://github.com/hsudais201-ops/myLauncher
- Runtime/framework: eve
- Intended hosting: Vercel

## Local development

Requires a supported Node.js version and an authenticated Vercel/eve setup.

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

1. Link the Vercel project to this folder as its root directory.
2. Configure an authenticated model through Vercel AI Gateway.
3. Configure a scoped GitHub integration for the exact repository.
4. Add typed GitHub inspection and pull-request tools; keep default-branch writes and merge/release actions disabled.
5. Configure an isolated sandbox for tests and builds.
6. Configure persistent checkpoint storage and a notification destination.
7. Verify the health route and manually dispatch a test heartbeat before enabling the recurring schedule.

Do not put tokens or API keys in this repository. Use Vercel-managed connections or encrypted environment secrets. Do not enable broad GitHub organization access when repository-scoped access is sufficient.

## Current scaffold status

The instructions and schedule are present. The GitHub tool connection, sandbox test runner, persistent checkpoint provider, notifications, Vercel project, and production deployment still require configuration and verification. This scaffold is not yet a running 24/7 agent.
