export const SECONDARY_INSTANCE_SWITCH = "--secondary";

export interface AppInstanceProfile {
  isSecondary: boolean;
  userDataPath: string;
  windowTitle?: string;
}

export function isSecondaryInstance(argv: readonly string[]): boolean {
  return argv.includes(SECONDARY_INSTANCE_SWITCH);
}

export function orderPortsForAppInstance(
  ports: readonly number[],
  secondary: boolean,
): number[] {
  return secondary ? [...ports].reverse() : [...ports];
}

export function resolveAppInstanceProfile(
  argv: readonly string[],
  primaryUserDataPath: string,
): AppInstanceProfile {
  if (!isSecondaryInstance(argv)) {
    return {
      isSecondary: false,
      userDataPath: primaryUserDataPath,
    };
  }

  return {
    isSecondary: true,
    userDataPath: `${primaryUserDataPath}-secondary`,
    windowTitle: "OpenNOW — Secondary",
  };
}
