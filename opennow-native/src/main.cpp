#include "appstate.h"
#include "authengine.h"
#include "controllerinput.h"
#include "gfn/catalogengine.h"
#include "gfn/sessionengine.h"
#include "gfn/signalingclient.h"
#include "streamengine.h"

#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickWindow>
#include <QUrl>

int main(int argc, char *argv[])
{
    QGuiApplication::setApplicationName(QStringLiteral("OpenNOW Native"));
    QGuiApplication::setOrganizationName(QStringLiteral("OpenCloudGaming"));
    QGuiApplication::setApplicationVersion(QStringLiteral("0.1.0"));

    QGuiApplication app(argc, argv);
    QQuickWindow::setGraphicsApi(QSGRendererInterface::OpenGLRhi);

    StreamEngine streamEngine;
    ControllerInput controllerInput;
    AuthEngine authEngine;
    CatalogEngine catalogEngine;
    SessionEngine sessionEngine;
    SignalingClient signalingClient;
    AppState appState;
    QQuickWindow *streamWindow = nullptr;
    const auto updateStreamSurface = [&] {
        if (!streamWindow) {
            return;
        }
        streamEngine.updateSurface({
            {QStringLiteral("windowHandle"),
             QString::number(static_cast<qulonglong>(streamWindow->winId()))},
            {QStringLiteral("rect"),
             QJsonObject{{QStringLiteral("x"), 0},
                         {QStringLiteral("y"), 0},
                         {QStringLiteral("width"), streamWindow->width()},
                         {QStringLiteral("height"), streamWindow->height()}}},
            {QStringLiteral("visible"), true},
            {QStringLiteral("deviceScaleFactor"), streamWindow->devicePixelRatio()},
            {QStringLiteral("showStats"), false},
        });
    };

    const auto configureAuthenticatedEngines = [&] {
        if (!authEngine.hasUsableSession()) {
            return;
        }
        const auto provider = authEngine.activeProvider();
        catalogEngine.setTokens(authEngine.idToken(), authEngine.accessToken());
        catalogEngine.setUserId(authEngine.userId());
        catalogEngine.setProviderStreamingUrl(provider.streamingServiceUrl);
        sessionEngine.setCredentials(authEngine.sessionToken(), provider.streamingServiceUrl);
        catalogEngine.refreshAll();
    };
    QObject::connect(&authEngine, &AuthEngine::authorized, &app, configureAuthenticatedEngines);
    QObject::connect(&authEngine, &AuthEngine::sessionReady, &app, configureAuthenticatedEngines);
    QObject::connect(&authEngine, &AuthEngine::sessionInvalidated, &app, [&](const QString &) {
        catalogEngine.clear();
        sessionEngine.clearCredentials();
        sessionEngine.cancel();
        signalingClient.disconnectFromServer();
        streamEngine.stop(QStringLiteral("authentication ended"));
    });

    QObject::connect(&sessionEngine, &SessionEngine::connectionReady, &app, [&] {
        const auto context = sessionEngine.streamerContext();
        if (context.value(QStringLiteral("session")).toObject().isEmpty()) {
            sessionEngine.stopSession();
            return;
        }
        streamEngine.startRemoteSession(context);
        updateStreamSurface();
        signalingClient.connectToServer(sessionEngine.signalingServer(),
                                        sessionEngine.signalingSessionId(),
                                        sessionEngine.signalingUrl());
    });
    QObject::connect(&signalingClient, &SignalingClient::offerReceived,
                     &streamEngine, qOverload<const QString &>(&StreamEngine::handleOffer));
    QObject::connect(&signalingClient, &SignalingClient::remoteIceCandidateReceived,
                     &streamEngine, &StreamEngine::addRemoteIce);
    QObject::connect(&streamEngine, &StreamEngine::answerReady, &app,
                     [&](const QString &, const QString &sdp, const QString &nvstSdp) {
                         signalingClient.sendAnswer(sdp, nvstSdp);
                     });
    QObject::connect(&streamEngine, &StreamEngine::localIceCandidate,
                     &signalingClient, qOverload<const QJsonObject &>(&SignalingClient::sendIceCandidate));
    QObject::connect(&streamEngine, &StreamEngine::inputReady, &app,
                     [&](int protocolVersion) { controllerInput.setStreaming(true, protocolVersion); });
    QObject::connect(&streamEngine, &StreamEngine::runtimeReady, &app,
                     [&](const QJsonObject &) { updateStreamSurface(); });
    QObject::connect(&controllerInput, &ControllerInput::inputPacket, &app,
                     [&](const QByteArray &payload, bool partiallyReliable) {
                         streamEngine.sendInput(payload, partiallyReliable);
                     });
    QObject::connect(&sessionEngine, &SessionEngine::sessionStopped, &app, [&] {
        signalingClient.disconnectFromServer();
        controllerInput.setStreaming(false);
        streamEngine.stop(QStringLiteral("session ended"));
    });
    QObject::connect(&streamEngine, &StreamEngine::streamerError, &app,
                     [&](const QString &, const QString &) {
                         signalingClient.disconnectFromServer();
                         controllerInput.setStreaming(false);
                         sessionEngine.stopSession();
                     });

    QQmlApplicationEngine engine;
    engine.rootContext()->setContextProperty(QStringLiteral("streamEngine"), &streamEngine);
    engine.rootContext()->setContextProperty(QStringLiteral("controllerInput"), &controllerInput);
    engine.rootContext()->setContextProperty(QStringLiteral("authEngine"), &authEngine);
    engine.rootContext()->setContextProperty(QStringLiteral("catalogEngine"), &catalogEngine);
    engine.rootContext()->setContextProperty(QStringLiteral("sessionEngine"), &sessionEngine);
    engine.rootContext()->setContextProperty(QStringLiteral("appState"), &appState);
    engine.load(QUrl(QStringLiteral("qrc:/OpenNOW/qml/Main.qml")));

    if (engine.rootObjects().isEmpty()) {
        return EXIT_FAILURE;
    }
    streamWindow = qobject_cast<QQuickWindow *>(engine.rootObjects().constFirst());
    if (streamWindow) {
        QObject::connect(streamWindow, &QQuickWindow::widthChanged, &app, updateStreamSurface);
        QObject::connect(streamWindow, &QQuickWindow::heightChanged, &app, updateStreamSurface);
    }

    return app.exec();
}
