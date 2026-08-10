import React from "react";
import ReactDOM from "react-dom/client";

import { initLogCapture } from "@shared/logger";
import { App } from "./App";
import { MotionProvider } from "./components/MotionProvider";
import { initializeLocale } from "./i18n";
import "./styles.css";

// Initialize log capture for renderer process
initLogCapture("renderer");
void initializeLocale();

if (import.meta.env.DEV) {
  void import("react-scan").then(({ scan }) => scan());
}

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <MotionProvider>
      <App />
    </MotionProvider>
  </React.StrictMode>,
);
