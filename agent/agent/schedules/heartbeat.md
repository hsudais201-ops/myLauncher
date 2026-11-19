# Planned Flame Launcher maintenance heartbeat

**Status: design note only. This file does not register an active Eve schedule.**

Before enabling an autonomous heartbeat, implement it with Eve's supported TypeScript schedule API and connect a real authenticated channel. The intended cadence is every 15 minutes, subject to Vercel plan limits and schedule support.

When implemented, the heartbeat should:

1. Inspect repository health, recent commits, open pull requests, open issues, and latest CI results.
2. Identify at most one actionable, low-risk maintenance task.
3. Avoid duplicating work already covered by an open pull request or issue.
4. Prefer a read-only audit until GitHub tools and sandbox test execution are configured.
5. Never merge, release, publish, deploy production, delete, change permissions, or spend money.
6. If a code change is warranted and authorized, work on a feature branch, run relevant checks, and open a pull request for human review.
7. Persist a compact checkpoint so repeated heartbeats do not redo completed work.
8. Report evidence, failures, and blockers. Never claim a check passed unless it actually ran.

Do not activate recurring execution until the channel, permissions, checkpoint behavior, notification delivery, and manual test run have all been verified.
