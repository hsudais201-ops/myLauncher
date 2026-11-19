---
cron: "*/15 * * * *"
---

Run a bounded Flame Launcher maintenance heartbeat.

If the repository integration and required permissions are not configured, do not guess or attempt unauthenticated writes. Report that setup is incomplete.

When tools are available:
1. Inspect repository health, recent commits, open pull requests, open issues, and latest CI results.
2. Identify at most one actionable, low-risk maintenance task.
3. Do not duplicate work already covered by an open pull request or issue.
4. Prefer a read-only audit and a concise report until GitHub write permissions and sandbox test execution have been explicitly configured.
5. Never merge, release, publish, deploy production, delete, change permissions, or spend money.
6. If a code change is warranted and authorized, work on a feature branch, run the relevant checks, and open a pull request for human review.
7. Persist a compact checkpoint so repeated heartbeats do not redo completed work.
8. Report evidence, failures, and blockers. Never claim a check passed unless it actually ran.
