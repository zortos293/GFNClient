import { Check, ChevronDown } from "lucide-react";
import { useEffect, useId, useMemo, useRef, useState } from "react";
import type { JSX, KeyboardEvent, ReactNode } from "react";

export interface SelectDropdownOption {
  value: string;
  label: ReactNode;
  disabled?: boolean;
}

interface SelectDropdownProps {
  id?: string;
  value: string;
  options: SelectDropdownOption[];
  onChange: (value: string) => void;
  disabled?: boolean;
  placeholder?: ReactNode;
  ariaLabel?: string;
  className?: string;
  triggerClassName?: string;
  menuClassName?: string;
}

function cx(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(" ");
}

function findEnabledIndex(options: SelectDropdownOption[], startIndex: number, direction: 1 | -1): number {
  if (options.length === 0) return -1;

  for (let step = 0; step < options.length; step += 1) {
    const index = (startIndex + (step * direction) + options.length) % options.length;
    if (!options[index]?.disabled) return index;
  }

  return -1;
}

function findFirstEnabledIndex(options: SelectDropdownOption[]): number {
  return options.findIndex((option) => !option.disabled);
}

function findLastEnabledIndex(options: SelectDropdownOption[]): number {
  for (let index = options.length - 1; index >= 0; index -= 1) {
    if (!options[index]?.disabled) return index;
  }
  return -1;
}

export function SelectDropdown({
  id,
  value,
  options,
  onChange,
  disabled = false,
  placeholder,
  ariaLabel,
  className,
  triggerClassName,
  menuClassName,
}: SelectDropdownProps): JSX.Element {
  const generatedId = useId();
  const buttonId = id ?? `${generatedId}-button`;
  const listboxId = `${buttonId}-listbox`;
  const rootRef = useRef<HTMLDivElement | null>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const [open, setOpen] = useState(false);
  const hasEnabledOptions = options.some((option) => !option.disabled);
  const effectiveDisabled = disabled || !hasEnabledOptions;
  const selectedIndex = options.findIndex((option) => option.value === value);
  const selectedOption = selectedIndex >= 0 ? options[selectedIndex] : undefined;
  const [activeIndex, setActiveIndex] = useState(() => (selectedIndex >= 0 ? selectedIndex : findFirstEnabledIndex(options)));

  const activeOptionId = open && activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined;
  const label = selectedOption?.label ?? placeholder ?? "Select";

  const enabledOptionsKey = useMemo(
    () => options.map((option) => `${option.value}:${option.disabled ? "disabled" : "enabled"}`).join("|"),
    [options],
  );

  useEffect(() => {
    optionRefs.current = optionRefs.current.slice(0, options.length);
  }, [options.length]);

  useEffect(() => {
    if (effectiveDisabled) {
      setOpen(false);
      return;
    }

    setActiveIndex((current) => {
      if (current >= 0 && current < options.length && !options[current]?.disabled) return current;
      if (selectedIndex >= 0 && !options[selectedIndex]?.disabled) return selectedIndex;
      return findFirstEnabledIndex(options);
    });
  }, [effectiveDisabled, enabledOptionsKey, options, selectedIndex]);

  useEffect(() => {
    if (!open) return;

    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener("pointerdown", handlePointerDown, true);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown, true);
    };
  }, [open]);

  useEffect(() => {
    if (!open || activeIndex < 0) return;
    optionRefs.current[activeIndex]?.scrollIntoView({ block: "nearest" });
  }, [activeIndex, open]);

  const openDropdown = (nextActiveIndex?: number) => {
    if (effectiveDisabled) return;
    const fallbackIndex = selectedIndex >= 0 && !options[selectedIndex]?.disabled ? selectedIndex : findFirstEnabledIndex(options);
    setActiveIndex(nextActiveIndex ?? fallbackIndex);
    setOpen(true);
  };

  const selectOption = (index: number) => {
    const option = options[index];
    if (!option || option.disabled) return;
    onChange(option.value);
    setOpen(false);
  };

  const moveActive = (direction: 1 | -1) => {
    const startIndex = activeIndex >= 0 ? activeIndex + direction : selectedIndex + direction;
    const nextIndex = findEnabledIndex(options, startIndex, direction);
    if (nextIndex >= 0) setActiveIndex(nextIndex);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (effectiveDisabled) return;

    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        if (!open) {
          const nextIndex = findEnabledIndex(options, Math.max(selectedIndex + 1, 0), 1);
          openDropdown(nextIndex);
        } else {
          moveActive(1);
        }
        break;
      case "ArrowUp":
        event.preventDefault();
        if (!open) {
          const nextIndex = findEnabledIndex(options, selectedIndex > 0 ? selectedIndex - 1 : options.length - 1, -1);
          openDropdown(nextIndex);
        } else {
          moveActive(-1);
        }
        break;
      case "Home":
        event.preventDefault();
        openDropdown(findFirstEnabledIndex(options));
        break;
      case "End":
        event.preventDefault();
        openDropdown(findLastEnabledIndex(options));
        break;
      case "Enter":
        event.preventDefault();
        if (open) {
          selectOption(activeIndex);
        } else {
          openDropdown();
        }
        break;
      case " ":
        event.preventDefault();
        if (open) {
          selectOption(activeIndex);
        } else {
          openDropdown();
        }
        break;
      case "Escape":
        if (open) {
          event.preventDefault();
          setOpen(false);
        }
        break;
      case "Tab":
        setOpen(false);
        break;
      default:
        break;
    }
  };

  return (
    <div ref={rootRef} className={cx("select-dropdown", open && "select-dropdown--open", effectiveDisabled && "select-dropdown--disabled", className)}>
      <button
        id={buttonId}
        type="button"
        className={cx("select-dropdown__trigger", triggerClassName)}
        disabled={effectiveDisabled}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-activedescendant={activeOptionId}
        onClick={() => {
          if (open) {
            setOpen(false);
          } else {
            openDropdown();
          }
        }}
        onKeyDown={handleKeyDown}
      >
        <span className="select-dropdown__value">{label}</span>
        <ChevronDown size={14} className="select-dropdown__chevron" aria-hidden="true" />
      </button>

      {open && (
        <div id={listboxId} className={cx("select-dropdown__menu", menuClassName)} role="listbox" aria-labelledby={buttonId}>
          {options.map((option, index) => {
            const selected = option.value === value;
            const active = index === activeIndex;
            return (
              <button
                key={option.value}
                id={`${listboxId}-option-${index}`}
                ref={(element) => {
                  optionRefs.current[index] = element;
                }}
                type="button"
                role="option"
                aria-selected={selected}
                disabled={option.disabled}
                tabIndex={-1}
                className={cx("select-dropdown__option", selected && "is-selected", active && "is-active")}
                onClick={() => selectOption(index)}
                onMouseEnter={() => {
                  if (!option.disabled) setActiveIndex(index);
                }}
              >
                <span className="select-dropdown__option-label">{option.label}</span>
                {selected && <Check size={14} className="select-dropdown__check" aria-hidden="true" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
