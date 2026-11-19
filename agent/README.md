# Flame Launcher Cloud Agent

This folder contains the separate eve-based cloud agent for maintaining the Flame Launcher repository.

## Current implementation

- `agent/agent.ts` defines the Eve agent and selects a model through Vercel AI Gateway.
- `agent/instructions.md` defines its Android-first Flame Launcher mission and strict human-approval boundaries.
- `agent/tools/github.ts` adds the GitHub Tools read-only `code-review` preset. It requires a separate Vercel Connect GitHub connector named `flame-launcher`.
- `agent/schedules/heartbeat.md` defines a daily maintenance heartbeat at 03:00 UTC (08:00 Pakistan time), appropriate for the current Vercel Hobby plan.
- The Git repository is connected to a Vercel project whose root directory is `agent/`.

This is an initial agent deployment, not yet a verified autonomous coding service. GitHub Connect authorization, sandbox command execution, persistent checkpoint behavior, notification delivery, live session API, and production deployment still need verification.

## Plan limits

The current Vercel team is on Hobby. Its cron schedule is once per day with approximate timing. A 15-minute maintenance cadence requires a plan with minute-level cron precision. The current source therefore uses a daily heartbeat rather than promising an unsupported 15-minute schedule.

A scheduled agent wakes on its configured schedule, performs bounded work, and parks until the next run. It does not continuously generate code every second.

## Project

- Product: Flame Launcher
- Owner: MFLegend
- Source repository: https://github.com/hsudais201-ops/myLauncher
- Runtime/framework: eve
- Intended hosting: Vercel

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

1. Create the Vercel Connect GitHub connector named `flame-launcher` and attach it to the Preview environment of this project.
2. Verify the Eve HTTP session API and authenticated GitHub code-review tools.
3. Configure sandbox command execution and test it against a harmless read-only command.
4. Configure durable checkpoint storage and a notification channel.
5. Run a manual read-only heartbeat and verify its report and schedule registration.
6. Only then consider enabling bounded feature-branch changes. Keep merge, release, production deployment, destructive actions, permission changes, and spending behind human approval.

Do not put tokens or API keys in this repository. Use Vercel-managed connections or encrypted environment secrets. Do not enable broad GitHub organization access when repository-scoped access is sufficient.

## Safety policy

The agent may inspect repository content and prepare changes on feature branches. It must never merge pull requests, push to the default branch, publish releases, deploy production app releases, delete resources, change account permissions, or spend money without explicit human approval.
