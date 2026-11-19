# Flame Launcher Cloud Agent

This folder contains the separate eve-based cloud agent for maintaining the Flame Launcher repository.

## Current implementation

- `agent/agent.ts` defines the Eve agent and selects a model through Vercel AI Gateway.
- `agent/instructions.md` defines its Android-first Flame Launcher mission and strict human-approval boundaries.
- `agent/extensions/github.ts` mounts the GitHub Tools Eve extension using the Vercel Connect connector UID `github/flame-launcher` and the read-only `code-review` preset.
- `agent/schedules/heartbeat.md` defines a daily maintenance heartbeat at 03:00 UTC (08:00 Pakistan time), appropriate for the current Vercel Hobby plan.
- The Git repository is connected to a Vercel project whose root directory is `agent/`.

The GitHub Connect connector is attached to this project's Preview environment. This is still an initial agent deployment, not yet a verified autonomous coding service. The live session API, authenticated tool calls from a running Eve session, sandbox command execution, persistent checkpoint behavior, notification delivery, and scheduled invocation still need verification.

## Plan limits

The current Vercel team is on Hobby. Its cron schedule is once per day with approximate timing. A 15-minute maintenance cadence requires a plan with minute-level cron precision. The current source therefore uses a daily heartbeat rather than promising an unsupported 15-minute schedule.

A scheduled agent wakes on its configured schedule, performs bounded work, and parks until the next run. It does not continuously generate code every second.

## Project

- AI agent name: `flame-launcher`
- Product: Flame Launcher
- Owner: MFLegend
- Source repository: https://github.com/hsudais201-ops/myLauncher
- GitHub App: `flamelauncher1`
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

## Remaining verification before autonomous repository work

1. Verify that a running Eve session loads the GitHub extension and can perform read-only repository and pull-request operations through the attached Preview connector.
2. Verify sandbox command execution against a harmless read-only command.
3. Configure and test durable checkpoint storage and a notification channel.
4. Run a manual read-only heartbeat and verify its report and schedule registration.
5. Only then consider bounded feature-branch changes. Keep merge, release, production deployment, destructive actions, permission changes, and spending behind human approval.

Do not put tokens or API keys in this repository. Use Vercel-managed connections or encrypted environment secrets. Do not enable broad GitHub organization access when repository-scoped access is sufficient.

## Safety policy

The agent may inspect repository content and prepare changes on feature branches. It must never merge pull requests, push to the default branch, publish releases, deploy production app releases, delete resources, change account permissions, or spend money without explicit human approval.
