const RUNTIME_GITHUB_TOKEN_ENV_KEYS = ["OPENNOW_GH_TOKEN", "GH_TOKEN"] as const;

export function pickRuntimeGitHubToken(env: NodeJS.ProcessEnv = process.env): string | null {
  for (const key of RUNTIME_GITHUB_TOKEN_ENV_KEYS) {
    const value = env[key]?.trim();
    if (value) {
      return value;
    }
  }

  return null;
}
