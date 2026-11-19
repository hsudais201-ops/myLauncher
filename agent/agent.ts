import { defineAgent } from "eve";

export default defineAgent({
  // OpenAI is primary. AI Gateway tries Gemini if this model/provider
  // fails or becomes unavailable, subject to the gateway's fallback rules.
  model: "openai/gpt-5.6-terra",
  modelOptions: {
    providerOptions: {
      gateway: {
        models: ["google/gemini-3.1-pro-preview"],
        only: ["openai", "google"],
        sessionAffinity: false,
      },
    },
  },
});
