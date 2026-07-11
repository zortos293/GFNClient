import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
  children: ReactNode;
  label?: string;
}

interface State {
  error: Error | null;
}

/** Dev-facing boundary so page crashes surface instead of a blank shell. */
export class PageErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error(`[PageErrorBoundary:${this.props.label ?? "page"}]`, error, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        <div style={{ padding: 32, color: "#fff", fontFamily: "monospace", whiteSpace: "pre-wrap" }}>
          <h2 style={{ color: "#ff6b6b" }}>UI crashed{this.props.label ? `: ${this.props.label}` : ""}</h2>
          <p>{this.state.error.message}</p>
          <pre style={{ opacity: 0.8, fontSize: 12 }}>{this.state.error.stack}</pre>
        </div>
      );
    }
    return this.props.children;
  }
}
