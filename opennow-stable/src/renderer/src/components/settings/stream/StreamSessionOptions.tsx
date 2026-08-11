import { type JSX } from "react";
import type { Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SettingToggleRow } from "../SettingRow";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

interface StreamSessionOptionsProps {
  settings: Settings;
  handleChange: SettingsChangeHandler;
}

export function StreamSessionOptions({
  settings,
  handleChange,
}: StreamSessionOptionsProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <>
      <SettingToggleRow
        htmlFor="settings-stream-enable-l4s"
        label={<>{t("settings.video.experimentalL4SRequest")}<span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.beta")}</span></>}
        description={t("settings.video.experimentalL4SRequestHint")}
        checked={settings.enableL4S}
        onChange={(checked) => handleChange("enableL4S", checked)}
      />
      <SettingToggleRow
        htmlFor="settings-stream-identify-steam-deck"
        label={<>{t("settings.video.identifyAsSteamDeck")}<span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.experimental")}</span></>}
        description={t("settings.video.identifyAsSteamDeckHint")}
        checked={settings.identifyAsSteamDeck}
        onChange={(checked) => handleChange("identifyAsSteamDeck", checked)}
      />

    </>
  );
}
