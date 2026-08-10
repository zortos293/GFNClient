import {
  SETTINGS_SCOPE_SEARCH_TERMS,
  type SettingsSearchScopeId,
} from "./settingsTypes";

function tokenMatchesWord(token: string, word: string): boolean {
  return token === word || word.startsWith(token);
}

export function settingsScopeMatchesSearch(
  scopeId: SettingsSearchScopeId,
  search: string,
): boolean {
  const searchTokens = search
    .trim()
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter((token) => token.length > 0);
  if (searchTokens.length === 0) {
    return true;
  }

  const searchableWords = Array.from(
    new Set(
      SETTINGS_SCOPE_SEARCH_TERMS[scopeId]
        .join(" ")
        .toLowerCase()
        .split(/[^a-z0-9]+/)
        .filter((word) => word.length > 0),
    ),
  );
  return searchTokens.every((token) =>
    searchableWords.some((word) => tokenMatchesWord(token, word)),
  );
}
