#pragma once

class AppController;
class QQuickWindow;

// Isolated smoke-only exercise of production popup lifetimes and transforms.
void startMotionAcceptance(QQuickWindow *window, AppController *controller,
                           const bool *qmlWarningOccurred, bool fullscreen);

void startStoreNavigationAcceptance(QQuickWindow *window, const bool *qmlWarningOccurred);
