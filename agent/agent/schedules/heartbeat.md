---
cron: "0 3 * * *"
---

Run one bounded Flame Launcher maintenance check per day (03:00 UTC / 08:00 Pakistan time).

This daily cadence is a deliberate low-activity choice while model access, checkpoint persistence, and notifications are being verified. It is not a claim that Vercel Hobby only allows daily schedules: the existing Production deployment was previously configured for a 15-minute schedule and did receive invocations. Keep this branch daily for now because a 15-minute heartbeat can produce up to 96 model attempts each day.

For now, perform a read-only repository audit using the attached GitHub connector. The connector is restricted to hsudais201-ops/myLauncher and the authenticated GitHub command allows only repository/PR/issue/workflow reads plus cloning this repository into the isolated sandbox. Do not attempt remote writes, commits, pushes, PR creation, merging, releases, or production deployment.

At each run:
1. Inspect the repository's recent changes, open pull requests, open issues, and latest CI runs.
2. Identify at most one actionable, low-risk maintenance concern.
3. Avoid duplicating work already covered by an open PR or issue.
4. Run only checks available in the isolated sandbox; report unavailable Android emulator/device checks honestly.
5. Do not save or publish code changes. Provide a proposed patch summary only.
6. Never merge, release, publish, deploy production, delete, change permissions, or spend money.
7. Report evidence, failures, and blockers. Never claim a check passed unless it actually ran.

The old Production deployment is a separate version and has been observed failing before the model response because Vercel AI Gateway returned HTTP 403 (customer_verification_required). This Preview branch schedule does not replace or disable that Production schedule.

Persistent checkpoints and notifications are not configured yet, so do not claim durable progress between separate scheduled runs or successful notifications.
