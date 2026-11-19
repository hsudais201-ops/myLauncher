# Flame Launcher Cloud Agent

This folder contains the Eve-based cloud agent for maintaining the Flame Launcher Android repository.

## Identity and hosting

- AI agent: `flame-launcher`
- Product: Flame Launcher
- Owner: MFLegend
- GitHub repository: https://github.com/hsudais201-ops/myLauncher
- GitHub App: `flamelauncher1`
- Vercel Connect connector UID: `github/flame-launcher`
- Connector display name: `flame-launcher-github`
- Vercel project: `flame-launcher-agent-connected`
- Vercel project root directory: `agent/`
- Runtime: Eve on Vercel

## Eve layout

Because Vercel's project root is the repository's `agent/` directory, Eve's own agent files live under the nested `agent/` directory in this repository:

- `agent.ts` — main Eve agent definition (repository path `agent/agent.ts`)
- `agent/instructions.md` — agent mission and permissions
- `agent/extensions/code.ts` — Eve coding extension
- `agent/extensions/git.ts` — GitHub integration with a server-side read-only allowlist
- `agent/lib/github.ts` — short-lived GitHub credential lease broker
- `agent/sandbox.ts` — Vercel Sandbox setup and coding-tool installation
- `agent/schedules/heartbeat.md` — daily maintenance schedule

## Current permission boundary

The GitHub connector is attached to the Vercel project's **Preview** environment only. The Eve Git extension uses the GitHub App installation token, scopes it to `hsudais201-ops/myLauncher`, and restricts commands with a server-side `authorize` allowlist. The allowlist permits read-only repository/PR/issue/workflow queries and cloning this exact repository into the isolated sandbox. It does **not** permit commits, pushes, branch writes, PR creation, merges, releases, or other remote changes.

The coding extension can analyze and edit files inside its isolated sandbox. Those edits are temporary and are not saved to GitHub. This limitation is intentional until the interactive approval and branch-publishing workflow is implemented and tested.

This is not yet a verified autonomous coding service. Actual Eve session execution, authenticated read-only tool calls, sandbox command execution, checkpoint persistence, notification delivery, and scheduled invocation still require runtime verification.

## Schedule limits

The current Vercel team is on Hobby. Its cron schedule is once per day with approximate timing. The current heartbeat is set for 03:00 UTC / 08:00 Pakistan time. A 15-minute maintenance cadence requires a plan that supports minute-level cron precision.

A scheduled agent wakes on its configured schedule, performs bounded work, and parks until the next run. It does not continuously generate code every second.

## Local development

Requires Node.js 26 and an authenticated Vercel/Eve setup.

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

## Required verification before enabling code publishing

1. Confirm a running Eve session loads both extensions.
2. Verify the GitHub extension can read only the named repository and cannot perform write commands.
3. Verify the sandbox can clone the repository and run harmless inspection commands.
4. Configure and test durable checkpoint storage and a notification channel.
5. Design and review a separate branch-only publish flow with explicit approval for any GitHub writes.
6. Keep merge, release, production deployment, destructive actions, permission changes, and spending behind explicit human approval.

Never put tokens, API keys, or private keys in this repository. Use Vercel-managed connections and short-lived credentials.
