import type { JSX, ReactNode } from "react";

interface SettingRowProps {
  label: ReactNode;
  description?: ReactNode;
  htmlFor?: string;
  children: ReactNode;
  className?: string;
}

export function SettingRow({
  label,
  description,
  htmlFor,
  children,
  className = "",
}: SettingRowProps): JSX.Element {
  return (
    <div className={`settings-row ${className}`.trim()}>
      <label className="settings-label settings-label--wrap" htmlFor={htmlFor}>
        <span className="settings-label-title">{label}</span>
        {description ? <span className="settings-hint">{description}</span> : null}
      </label>
      <div className="settings-row-control">{children}</div>
    </div>
  );
}

interface SettingToggleRowProps extends Omit<SettingRowProps, "children"> {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
}

export function SettingToggleRow({
  checked,
  onChange,
  disabled = false,
  ...rowProps
}: SettingToggleRowProps): JSX.Element {
  return (
    <SettingRow {...rowProps}>
      <label className="settings-toggle">
        <input
          id={rowProps.htmlFor}
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(event) => onChange(event.target.checked)}
        />
        <span className="settings-toggle-track" />
      </label>
    </SettingRow>
  );
}
