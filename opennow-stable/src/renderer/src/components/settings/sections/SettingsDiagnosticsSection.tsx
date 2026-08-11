import { type JSX } from "react";
import type { Settings } from "@shared/gfn";
import type { CodecTestResult } from "../../../lib/codecDiagnostics";
import { useTranslation } from "../../../i18n";
import { CodecDiagnosticsSection } from "../stream/CodecDiagnosticsSection";
import { StatsOverlayControls } from "../stream/StatsOverlayControls";

interface SettingsDiagnosticsSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  handlePreview: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  codecResults: CodecTestResult[] | null;
  codecTesting: boolean;
  onRunCodecTest: () => Promise<void>;
}

export function SettingsDiagnosticsSection({
  settings,
  showAll,
  handleChange,
  handlePreview,
  codecResults,
  codecTesting,
  onRunCodecTest,
}: SettingsDiagnosticsSectionProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <section className="settings-section">
      {showAll && (
        <div className="settings-section-context">
          {t("settings.sections.diagnostics")}
        </div>
      )}
      <div className="settings-section-header settings-section-header--with-copy">
        <div>
          <h2>{t("settings.diagnostics.title")}</h2>
          <p className="settings-section-description">
            {t("settings.diagnostics.description")}
          </p>
        </div>
      </div>
      <div className="settings-rows settings-rows--grouped">
        <div className="settings-group">
          <div className="settings-group-header">
            <h3>{t("settings.diagnostics.frameStats")}</h3>
            <p>{t("settings.diagnostics.frameStatsHint")}</p>
          </div>
          <div className="settings-group-rows">
            <StatsOverlayControls
              settings={settings}
              handleChange={handleChange}
              handlePreview={handlePreview}
            />
          </div>
        </div>
        <CodecDiagnosticsSection
          settings={settings}
          handleChange={handleChange}
          codecResults={codecResults}
          codecTesting={codecTesting}
          onRunCodecTest={onRunCodecTest}
        />
      </div>
    </section>
  );
}
