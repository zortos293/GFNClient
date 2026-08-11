import React from "react";
import ReactDOM from "react-dom/client";

import { initLogCapture } from "@shared/logger";
import { App } from "./App";
import { MotionProvider } from "./components/MotionProvider";
import { initializeLocale } from "./i18n";
import "./styles.css";

// Initialize log capture for renderer process
initLogCapture("renderer");

const root = ReactDOM.createRoot(document.getElementById("root") as HTMLElement);
const localeReady = initializeLocale().catch((error) => {
  console.warn("[i18n] Failed to initialize locale; using English.", error);
});
const devToolsReady = import.meta.env.DEV
  ? import("react-scan").then(({ scan }) => scan()).catch((error) => {
      console.warn("[React Scan] Failed to initialize.", error);
    })
  : Promise.resolve();

void Promise.all([localeReady, devToolsReady]).then(() => {
  root.render(
    <React.StrictMode>
      <MotionProvider>
        <App />
      </MotionProvider>
    </React.StrictMode>,
  );
});
