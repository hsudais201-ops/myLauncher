import type { GitHubLeaseBroker } from "eve/extensions/git";
import type { SandboxNetworkPolicy } from "eve/sandbox";
import type { VercelSandboxSession } from "eve/sandbox/vercel";

/**
 * Installs one repository-scoped GitHub credential lease for a command,
 * then clears it when called with null. The sandbox sees only a placeholder
 * token; Vercel's sandbox firewall injects the real credential on GitHub
 * requests. Preserve the lease rules supplied by the Git extension.
 */
export const brokerGitHubLease: GitHubLeaseBroker = async (sandbox, rules) => {
  if (!("setNetworkPolicy" in sandbox)) {
    throw new Error(
      "GitHub credential leases require a sandbox with a mutable network policy.",
    );
  }

  const policy = { allow: { "*": [], ...rules } } as SandboxNetworkPolicy;
  await (sandbox as VercelSandboxSession).setNetworkPolicy(policy);
};
