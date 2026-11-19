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

## AI provider routing

The agent is configured to use two different providers through Vercel AI Gateway:

1. **Primary:** `openai/gpt-5.6-terra`
2. **Fallback:** `google/gemini-3.1-pro-preview`

The routing is configured in `agent/agent.ts` with `modelOptions.providerOptions.gateway.models`. Gateway tries the OpenAI model first and advances to Gemini when the primary request fails or is unavailable under Gateway's fallback behavior. The allowed providers are restricted to OpenAI and Google, and session affinity is disabled so it does not reorder the intended route.

### Required provider credentials

Add your own keys in the Vercel team's AI Gateway dashboard, not in this repository:

1. Open Vercel Dashboard → AI Gateway → Bring Your Own Key (BYOK).
2. Add an **OpenAI** credential, enable it, and use **Test Key**.
3. Add a **Google** credential (Gemini API key), enable it, and use **Test Key**.
4. Keep both keys private. Never paste them into chat, GitHub files, or client-side code.

Important: BYOK does not remove the AI Gateway account requirement. Vercel's documentation says the team must have AI Gateway credits available even when BYOK is used; if BYOK fails, Gateway may fall back to system credentials and charge Gateway credits. The previously observed `customer_verification_required` error may therefore still require the Vercel billing/payment-method step. Provider quota and Gateway account credit are separate limits.

Automatic fallback is not a guarantee that every failure can be recovered. Invalid credentials, unsupported requests, model incompatibility, account-level Gateway blocks, or errors not eligible for fallback can still fail the run. Verify with a real Preview run and inspect the routing metadata/logs to confirm which provider served it.

## Eve layout

Because Vercel's project root is the repository's `agent/` directory, Eve's own agent files live under the nested `agent/` directory in this repository:

- `agent.ts` — main Eve agent definition and model routing (repository path `agent/agent.ts`)
- `agent/instructions.md` — agent mission and permissions
- `agent/extensions/code.ts` — Eve coding extension
- `agent/extensions/git.ts` — GitHub integration with a server-side read-only allowlist
- `agent/lib/github.ts` — short-lived GitHub credential lease broker
- `agent/sandbox.ts` — Vercel Sandbox setup and coding-tool installation
- `agent/schedules/heartbeat.md` — maintenance schedule

## Current permission boundary

The GitHub connector is attached to the Vercel project's **Preview** environment only. The Eve Git extension uses the GitHub App installation token, scopes it to `hsudais201-ops/myLauncher`, and restricts commands with a server-side `authorize` allowlist. The allowlist permits read-only repository/PR/issue/workflow queries and cloning this exact repository into the isolated sandbox. It does **not** permit commits, pushes, branch writes, PR creation, merges, releases, or other remote changes.

The coding extension can analyze and edit files inside its isolated sandbox. Those edits are temporary and are not saved to GitHub. This limitation is intentional until the interactive approval and branch-publishing workflow is implemented and tested.

## Current validation and known blocker

- The latest Preview deployment before the provider-routing change built successfully and reached READY; the new commit must complete its own build.
- The Vercel Connect GitHub connector is attached to Preview.
- There are currently no confirmed successful Preview Agent Runs, so runtime tool loading, provider failover, authenticated GitHub calls, and sandbox commands remain unverified.
- An older Production deployment from commit `cb75d2411688d8b5ed2bb023b7fa917e51b62bcd` is still active. Its older 15-minute scheduled runs fail before the model responds.
- Vercel runtime logs show AI Gateway returning HTTP 403 with `customer_verification_required`: a valid payment method is required to unlock the included AI Gateway credits. No successful model response was recorded for those failed runs.
- The current branch does not replace that old Production deployment. Do not claim the old Production schedule has been fixed.

This is not yet a verified autonomous coding service. Actual Preview Eve session execution, provider fallback, authenticated tool calls, sandbox command execution, checkpoint persistence, notifications, and successful model responses still require verification.

## Schedule choice

The current branch uses a daily heartbeat at 03:00 UTC / 08:00 Pakistan time as a conservative, low-activity setting while model access, persistence, and notifications are being verified. It is not a statement that Vercel Hobby only permits daily schedules: the older Production Eve schedule was configured for every 15 minutes and did receive scheduled invocations.

A scheduled agent wakes on its configured schedule, performs bounded work, and parks until the next run. It does not continuously generate code every second. A 15-minute heartbeat can produce up to 96 model attempts per day, so do not raise the frequency until model access, cost limits, and checkpointing have been tested.

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

1. Add and test OpenAI and Google/Gemini BYOK credentials in Vercel AI Gateway.
2. Resolve the AI Gateway 403 if it still blocks requests.
3. Confirm a running Eve Preview session loads both extensions and successfully calls the OpenAI primary model.
4. Verify a controlled failure or exhausted OpenAI quota causes a Gemini fallback, and inspect routing metadata.
5. Verify the GitHub extension can read only the named repository and cannot perform write commands.
6. Verify sandbox inspection commands, durable checkpoints, and notification delivery.
7. Design and review a separate branch-only publishing flow with explicit approval for any GitHub writes.
8. Keep merge, release, production deployment, destructive actions, permission changes, and spending behind explicit human approval.

Never put tokens, API keys, or private keys in this repository. Use Vercel-managed BYOK connections and short-lived credentials.
