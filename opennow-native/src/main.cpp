#include "authengine.h"
#include "controllerinput.h"
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
    QQmlApplicationEngine engine;
    engine.rootContext()->setContextProperty(QStringLiteral("streamEngine"), &streamEngine);
    engine.rootContext()->setContextProperty(QStringLiteral("controllerInput"), &controllerInput);
    engine.rootContext()->setContextProperty(QStringLiteral("authEngine"), &authEngine);
    engine.load(QUrl(QStringLiteral("qrc:/OpenNOW/qml/Main.qml")));

    if (engine.rootObjects().isEmpty()) {
        return EXIT_FAILURE;
    }

    return app.exec();
}
