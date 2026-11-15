export type Version = {
  id: string;
  type: "release" | "snapshot" | "old_beta" | "old_alpha";
  url: string;
  time: string;
  releaseTime: string;
};
export type JavaRuntime = { path: string; version: string; architecture: string };
export type Preferences = {
  selectedVersion: string;
  selectedJavaPath: string;
  ramMb: number;
  gameDirectory: string;
  accent: string;
  theme: "dark" | "light";
};

export type InstallResult = {
  versionId: string;
  gameDirectory: string;
  downloadedFiles: number;
  skippedFiles: number;
  assetsDownloaded: number;
};

export type LaunchResult = { processId: number };
