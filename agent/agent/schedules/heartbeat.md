---
cron: "*/15 * * * *"
---

Run a bounded Flame Launcher maintenance heartbeat.

The GitHub agent tools, isolated build sandbox, persistent checkpoint store, and notification channel are not yet configured. Until they are verified, perform no repository writes and do not claim to have inspected repository health. Report the missing capabilities and stop.

When the required integrations are available:
1. Inspect repository health, recent commits, open pull requests, open issues, and latest CI results.
2. Identify at most one actionable, low-risk maintenance task.
3. Avoid duplicating work already covered by an open pull request or issue.
4. Prefer a read-only audit until GitHub tools and sandbox test execution are configured.
5. Never merge, release, publish, deploy production, delete, change permissions, or spend money.
6. If a code change is warranted and authorized, work on a feature branch, run relevant checks, and open a pull request for human review.
7. Persist a compact checkpoint so repeated heartbeats do not redo completed work.
8. Report evidence, failures, and blockers. Never claim a check passed unless it actually ran.
