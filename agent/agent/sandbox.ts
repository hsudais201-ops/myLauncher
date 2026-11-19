import { installCodeTooling } from "eve/extensions/code/sandbox";
import { defineSandbox } from "eve/sandbox";
import { VercelSandbox } from "eve/sandbox/vercel";

// Vercel Sandbox supplies the mutable network policy used by the GitHub
// credential broker; real GitHub tokens stay outside the sandbox process.
export const environment = VercelSandbox.environment({
  prepare: (sandbox) => installCodeTooling(sandbox),
});

export default defineSandbox(() => environment.open());
