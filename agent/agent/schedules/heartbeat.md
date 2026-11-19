---
cron: "0 3 * * *"
---

Run one bounded Flame Launcher maintenance check per day (03:00 UTC / 08:00 Pakistan time).

The current Vercel team is on the Hobby plan, so use a daily schedule rather than a 15-minute heartbeat. A 15-minute cadence requires a plan that supports minute-level cron precision.

For now, perform a read-only repository audit using the attached GitHub connector. The connector is restricted to hsudais201-ops/myLauncher and the authenticated GitHub command allows only repository/PR/issue/workflow reads plus cloning this repository into the isolated sandbox. Do not attempt remote writes, commits, pushes, PR creation, merging, releases, or production deployment.

At each run:
1. Inspect the repository's recent changes, open pull requests, open issues, and latest CI runs.
2. Identify at most one actionable, low-risk maintenance concern.
3. Avoid duplicating work already covered by an open PR or issue.
4. Run only checks available in the isolated sandbox; report unavailable Android emulator/device checks honestly.
5. Do not save or publish code changes. Provide a proposed patch summary only.
6. Never merge, release, publish, deploy production, delete, change permissions, or spend money.
7. Report evidence, failures, and blockers. Never claim a check passed unless it actually ran.

Persistent checkpoints and notifications are not configured yet, so do not claim durable progress between separate scheduled runs or successful notifications.
