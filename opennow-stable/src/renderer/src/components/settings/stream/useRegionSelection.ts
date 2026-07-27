import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type RefObject,
} from "react";
import type { StreamRegion } from "@shared/gfn";
import {
  clearStoredRegionPingResults,
  loadStoredRegionPingResults,
  saveStoredRegionPingResults,
} from "../../../utils/pingResultsStorage";
import {
  filterAndSortRegions,
  findBestRegionUrl,
  type RegionPingResults,
} from "./regionSelection";

const REGION_LISTBOX_ID = "settings-stream-region-listbox";

interface UseRegionSelectionOptions {
  regions: StreamRegion[];
  selectedRegion: string;
  onSelectRegion: (regionUrl: string) => void;
}

interface RegionSelectionState {
  activeRegionIndex: number;
  activeRegionOptionId: string | undefined;
  bestRegionUrl: string | null;
  filteredRegions: StreamRegion[];
  handleRegionSearchKeyDown: (event: ReactKeyboardEvent<HTMLInputElement>) => void;
  handleRegionTriggerKeyDown: (event: ReactKeyboardEvent<HTMLButtonElement>) => void;
  isPinging: boolean;
  listboxId: string;
  openRegionDropdown: (preferredIndex?: number) => void;
  pingResults: RegionPingResults;
  regionDropdownOpen: boolean;
  regionSearch: string;
  regionSearchInputRef: RefObject<HTMLInputElement | null>;
  regionSelectorRef: RefObject<HTMLDivElement | null>;
  regionTriggerRef: RefObject<HTMLButtonElement | null>;
  runPingTest: () => Promise<void>;
  selectRegion: (regionUrl: string) => void;
  setActiveRegionValue: (regionUrl: string) => void;
  setRegionSearch: (query: string) => void;
  toggleRegionDropdown: () => void;
}

export function useRegionSelection({
  regions,
  selectedRegion,
  onSelectRegion,
}: UseRegionSelectionOptions): RegionSelectionState {
  const [regionSearch, setRegionSearch] = useState("");
  const [regionDropdownOpen, setRegionDropdownOpen] = useState(false);
  const [activeRegionValue, setActiveRegionValue] = useState("");
  const regionSelectorRef = useRef<HTMLDivElement | null>(null);
  const regionTriggerRef = useRef<HTMLButtonElement | null>(null);
  const regionSearchInputRef = useRef<HTMLInputElement | null>(null);
  const initialPingResults = useMemo(() => loadStoredRegionPingResults(), []);
  const [pingResults, setPingResults] = useState<Map<string, number | null>>(
    initialPingResults ?? new Map(),
  );
  const [isPinging, setIsPinging] = useState(false);
  const [bestRegionUrl, setBestRegionUrl] = useState<string | null>(
    () => initialPingResults ? findBestRegionUrl(initialPingResults) : null,
  );

  const runPingTest = useCallback(async (): Promise<void> => {
    if (regions.length === 0) return;
    setIsPinging(true);
    try {
      const results = await window.openNow.pingRegions(regions);
      const nextPingResults = new Map<string, number | null>();
      for (const result of results) {
        nextPingResults.set(result.url, result.pingMs);
      }

      setPingResults(nextPingResults);
      setBestRegionUrl(findBestRegionUrl(nextPingResults));
      saveStoredRegionPingResults(nextPingResults);
    } catch (error) {
      console.error("Ping test failed:", error);
    } finally {
      setIsPinging(false);
    }
  }, [regions]);

  useEffect(() => {
    if (regions.length > 0 && pingResults.size > 0) {
      const allRegionsCached = regions.every((region) => pingResults.has(region.url));
      if (!allRegionsCached) {
        setPingResults(new Map());
        setBestRegionUrl(null);
        clearStoredRegionPingResults();
      }
    }
  }, [pingResults, regions]);

  useEffect(() => {
    if (regions.length > 0 && pingResults.size === 0 && !isPinging) {
      void runPingTest();
    }
  }, [isPinging, pingResults.size, regions, runPingTest]);

  const filteredRegions = useMemo(
    () => filterAndSortRegions(regions, regionSearch, pingResults),
    [pingResults, regionSearch, regions],
  );
  const regionOptionValues = useMemo(
    () => ["", ...filteredRegions.map((region) => region.url)],
    [filteredRegions],
  );
  const activeRegionIndex = Math.max(0, regionOptionValues.indexOf(activeRegionValue));
  const activeRegionOptionId = regionDropdownOpen
    && regionOptionValues[activeRegionIndex] !== undefined
    ? `${REGION_LISTBOX_ID}-option-${activeRegionIndex}`
    : undefined;

  const openRegionDropdown = useCallback((preferredIndex?: number): void => {
    const selectedIndex = regionOptionValues.indexOf(selectedRegion);
    const nextIndex = Math.max(
      0,
      Math.min(
        regionOptionValues.length - 1,
        preferredIndex ?? (selectedIndex >= 0 ? selectedIndex : 0),
      ),
    );
    setActiveRegionValue(regionOptionValues[nextIndex] ?? "");
    setRegionDropdownOpen(true);
  }, [regionOptionValues, selectedRegion]);

  const selectRegion = useCallback((regionUrl: string): void => {
    onSelectRegion(regionUrl);
    setActiveRegionValue(regionUrl);
    setRegionDropdownOpen(false);
    setRegionSearch("");
    regionTriggerRef.current?.focus({ preventScroll: true });
  }, [onSelectRegion]);

  const toggleRegionDropdown = useCallback((): void => {
    if (regionDropdownOpen) {
      setRegionDropdownOpen(false);
      setRegionSearch("");
    } else {
      openRegionDropdown();
    }
  }, [openRegionDropdown, regionDropdownOpen]);

  const handleRegionTriggerKeyDown = useCallback(
    (event: ReactKeyboardEvent<HTMLButtonElement>): void => {
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        const selectedIndex = regionOptionValues.indexOf(selectedRegion);
        const fallbackIndex = event.key === "ArrowDown" ? 0 : regionOptionValues.length - 1;
        openRegionDropdown(selectedIndex >= 0 ? selectedIndex : fallbackIndex);
      } else if (event.key === "Escape" && regionDropdownOpen) {
        event.preventDefault();
        event.stopPropagation();
        setRegionDropdownOpen(false);
        setRegionSearch("");
      }
    },
    [openRegionDropdown, regionDropdownOpen, regionOptionValues, selectedRegion],
  );

  const handleRegionSearchKeyDown = useCallback(
    (event: ReactKeyboardEvent<HTMLInputElement>): void => {
      if (regionOptionValues.length === 0) return;

      const currentIndex = Math.max(0, regionOptionValues.indexOf(activeRegionValue));
      switch (event.key) {
        case "ArrowDown":
          event.preventDefault();
          setActiveRegionValue(
            regionOptionValues[(currentIndex + 1) % regionOptionValues.length] ?? "",
          );
          break;
        case "ArrowUp":
          event.preventDefault();
          setActiveRegionValue(
            regionOptionValues[
              (currentIndex - 1 + regionOptionValues.length) % regionOptionValues.length
            ] ?? "",
          );
          break;
        case "Home":
          event.preventDefault();
          setActiveRegionValue(regionOptionValues[0] ?? "");
          break;
        case "End":
          event.preventDefault();
          setActiveRegionValue(regionOptionValues.at(-1) ?? "");
          break;
        case "Enter":
          event.preventDefault();
          selectRegion(
            regionOptionValues.includes(activeRegionValue)
              ? activeRegionValue
              : (regionOptionValues[0] ?? ""),
          );
          break;
        case "Escape":
          event.preventDefault();
          event.stopPropagation();
          setRegionDropdownOpen(false);
          setRegionSearch("");
          regionTriggerRef.current?.focus({ preventScroll: true });
          break;
        default:
          break;
      }
    },
    [activeRegionValue, regionOptionValues, selectRegion],
  );

  useEffect(() => {
    if (!regionDropdownOpen) return;

    const focusFrame = window.requestAnimationFrame(() => {
      regionSearchInputRef.current?.focus({ preventScroll: true });
    });
    const closeRegionDropdownWhenOutside = (target: EventTarget | null): void => {
      if (!regionSelectorRef.current?.contains(target as Node)) {
        setRegionDropdownOpen(false);
        setRegionSearch("");
      }
    };
    const handlePointerDown = (event: PointerEvent): void => {
      closeRegionDropdownWhenOutside(event.target);
    };
    const handleFocusIn = (event: FocusEvent): void => {
      closeRegionDropdownWhenOutside(event.target);
    };

    document.addEventListener("pointerdown", handlePointerDown, true);
    document.addEventListener("focusin", handleFocusIn);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener("pointerdown", handlePointerDown, true);
      document.removeEventListener("focusin", handleFocusIn);
    };
  }, [regionDropdownOpen]);

  useEffect(() => {
    if (!regionDropdownOpen) return;
    if (regionOptionValues.includes(activeRegionValue)) return;
    setActiveRegionValue(
      regionOptionValues.includes(selectedRegion)
        ? selectedRegion
        : (regionOptionValues[0] ?? ""),
    );
  }, [activeRegionValue, regionDropdownOpen, regionOptionValues, selectedRegion]);

  useEffect(() => {
    if (!regionDropdownOpen || !activeRegionOptionId) return;
    document.getElementById(activeRegionOptionId)?.scrollIntoView({ block: "nearest" });
  }, [activeRegionOptionId, regionDropdownOpen]);

  return {
    activeRegionIndex,
    activeRegionOptionId,
    bestRegionUrl,
    filteredRegions,
    handleRegionSearchKeyDown,
    handleRegionTriggerKeyDown,
    isPinging,
    listboxId: REGION_LISTBOX_ID,
    openRegionDropdown,
    pingResults,
    regionDropdownOpen,
    regionSearch,
    regionSearchInputRef,
    regionSelectorRef,
    regionTriggerRef,
    runPingTest,
    selectRegion,
    setActiveRegionValue,
    setRegionSearch,
    toggleRegionDropdown,
  };
}
