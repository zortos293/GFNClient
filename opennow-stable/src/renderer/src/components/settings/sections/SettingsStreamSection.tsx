import { type JSX } from "react";
import type { EntitledResolution, Settings, StreamRegion } from "@shared/gfn";
import type { CodecTestResult } from "../../../lib/codecDiagnostics";
import { CodecDiagnosticsSection } from "../stream/CodecDiagnosticsSection";
import { RegionSelectionSection } from "../stream/RegionSelectionSection";
import { StreamVideoSection } from "../stream/StreamVideoSection";

export interface SettingsStreamSectionProps {
  settings: Settings;
  regions: StreamRegion[];
  showAll: boolean;
  showStreamRegion: boolean;
  showStreamVideo: boolean;
  showStreamCodecDiagnostics: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  handlePreview: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  codecResults: CodecTestResult[] | null;
  codecTesting: boolean;
  onRunCodecTest: () => Promise<void>;
  entitledResolutions: EntitledResolution[];
  subscriptionInfoLoaded: boolean;
  subscriptionLoading: boolean;
  onBlockingOverlayChange?: (blocking: boolean) => void;
}

export function SettingsStreamSection({
  settings,
  regions,
  showAll,
  showStreamRegion,
  showStreamVideo,
  showStreamCodecDiagnostics,
  handleChange,
  handlePreview,
  codecResults,
  codecTesting,
  onRunCodecTest,
  entitledResolutions,
  subscriptionInfoLoaded,
  subscriptionLoading,
  onBlockingOverlayChange,
}: SettingsStreamSectionProps): JSX.Element {
  return (
    <>
      <RegionSelectionSection
        settings={settings}
        regions={regions}
        showAll={showAll}
        visible={showStreamRegion}
        handleChange={handleChange}
      />
      {showStreamVideo && (
        <StreamVideoSection
          settings={settings}
          showAll={showAll}
          handleChange={handleChange}
          handlePreview={handlePreview}
          codecResults={codecResults}
          codecTesting={codecTesting}
          entitledResolutions={entitledResolutions}
          subscriptionInfoLoaded={subscriptionInfoLoaded}
          subscriptionLoading={subscriptionLoading}
          onBlockingOverlayChange={onBlockingOverlayChange}
        />
      )}
      <CodecDiagnosticsSection
        settings={settings}
        showAll={showAll}
        showStreamVideo={showStreamVideo}
        showStreamCodecDiagnostics={showStreamCodecDiagnostics}
        handleChange={handleChange}
        codecResults={codecResults}
        codecTesting={codecTesting}
        onRunCodecTest={onRunCodecTest}
      />
    </>
  );
}
