/** Game language codes for in-game localization (sent to GFN servers) */
export type GameLanguage =
  | "en_US" | "en_GB" | "de_DE" | "fr_FR" | "es_ES" | "es_MX" | "it_IT"
  | "pt_PT" | "pt_BR" | "ru_RU" | "pl_PL" | "tr_TR" | "ar_SA" | "ja_JP"
  | "ko_KR" | "zh_CN" | "zh_TW" | "th_TH" | "vi_VN" | "id_ID" | "cs_CZ"
  | "el_GR" | "hu_HU" | "ro_RO" | "uk_UA" | "nl_NL" | "sv_SE" | "da_DK"
  | "fi_FI" | "no_NO";

/** Keyboard layout codes for physical key mapping in remote sessions */
export type KeyboardLayout =
  | "en-US" | "en-GB" | "tr-TR" | "de-DE" | "fr-FR" | "es-ES" | "es-MX" | "it-IT"
  | "pt-PT" | "pt-BR" | "pl-PL" | "ru-RU" | "ja-JP" | "ko-KR" | "zh-CN" | "zh-TW";

export interface KeyboardLayoutOption {
  value: KeyboardLayout;
  label: string;
  macValue?: string;
}

export const DEFAULT_KEYBOARD_LAYOUT: KeyboardLayout = "en-US";

export const keyboardLayoutOptions: readonly KeyboardLayoutOption[] = [
  { value: "en-US", label: "English (US)", macValue: "m-us" },
  { value: "en-GB", label: "English (UK)", macValue: "m-brit" },
  { value: "tr-TR", label: "Turkish Q", macValue: "m-tr-qty" },
  { value: "de-DE", label: "German" },
  { value: "fr-FR", label: "French" },
  { value: "es-ES", label: "Spanish" },
  { value: "es-MX", label: "Spanish (Latin America)" },
  { value: "it-IT", label: "Italian" },
  { value: "pt-PT", label: "Portuguese (Portugal)" },
  { value: "pt-BR", label: "Portuguese (Brazil)" },
  { value: "pl-PL", label: "Polish" },
  { value: "ru-RU", label: "Russian" },
  { value: "ja-JP", label: "Japanese" },
  { value: "ko-KR", label: "Korean" },
  { value: "zh-CN", label: "Chinese (Simplified)" },
  { value: "zh-TW", label: "Chinese (Traditional)" },
] as const;

export function resolveGfnKeyboardLayout(layout: KeyboardLayout, platform: string): string {
  const option = keyboardLayoutOptions.find((candidate) => candidate.value === layout);
  if (platform === "darwin" && option?.macValue) {
    return option.macValue;
  }
  return option?.value ?? DEFAULT_KEYBOARD_LAYOUT;
}
