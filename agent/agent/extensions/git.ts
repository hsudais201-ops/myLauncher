import git from "eve/extensions/git";
import { brokerGitHubLease } from "../lib/github";

const OWNER = "hsudais201-ops";
const REPOSITORY = "hsudais201-ops/myLauncher";

const READ_ONLY_GITHUB_COMMANDS = new Set([
  "repo view",
  "pr list",
  "pr view",
  "issue list",
  "issue view",
  "run list",
  "run view",
]);

function isAllowedReadOnlyCommand(argv: readonly string[]): boolean {
  const [executable, ...args] = argv;

  if (executable === "gh") {
    // Clone only this exact repository into the isolated sandbox.
    if (
      args[0] === "repo" &&
      args[1] === "clone" &&
      args[2] === REPOSITORY &&
      args.length === 3
    ) {
      return true;
    }

    return READ_ONLY_GITHUB_COMMANDS.has(args.slice(0, 2).join(" "));
  }

  if (executable !== "git" || args.length === 0) return false;

  // Permit only local inspection commands; no commit, checkout, push, fetch,
  // remote mutation, config changes, hooks, or arbitrary shell execution.
  if (args[0] === "status") {
    return args.slice(1).every((arg) =>
      ["--short", "--branch", "--porcelain=v1"].includes(arg),
    );
  }

  if (["diff", "log", "show", "ls-files"].includes(args[0])) {
    return args.length === 1;
  }

  if (
    args[0] === "rev-parse" &&
    args.length === 2 &&
    args[1] === "--show-toplevel"
  ) {
    return true;
  }

  if (
    args[0] === "branch" &&
    args.length === 2 &&
    ["--show-current", "--list"].includes(args[1])
  ) {
    return true;
  }

  return false;
}

export default git({
  github: {
    connector: "github/flame-launcher",
    broker: brokerGitHubLease,
    principalType: "app",
    permissions: [
      "contents:read",
      "pull_requests:read",
      "issues:read",
      "actions:read",
    ],
    authorize: ({ owner, repository, argv }) =>
      owner.toLowerCase() === OWNER.toLowerCase() &&
      repository?.toLowerCase() === REPOSITORY.toLowerCase() &&
      isAllowedReadOnlyCommand(argv),
  },
});
