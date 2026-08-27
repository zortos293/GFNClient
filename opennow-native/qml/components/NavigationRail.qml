import QtQuick
import QtQuick.Controls
import OpenNOW

Rectangle {
    id: rail
    property int currentIndex: 0
    signal navigate(int index)
    signal profileRequested()

    width: Theme.railWidth
    color: Theme.canvas
    border.color: "#18201c"
    border.width: 1

    BrandMark {
        anchors.top: parent.top
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.topMargin: 28
        width: 40
        height: 40
    }

    Column {
        anchors.top: parent.top
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.topMargin: 365
        spacing: 8

        Repeater {
            model: ["\uf015", "\uf02d", "\uf002", "\uf080", "\uf185"]

            Button {
                id: navButton
                required property string modelData
                required property int index
                width: 56
                height: 56
                hoverEnabled: true
                focusPolicy: Qt.StrongFocus
                onClicked: rail.navigate(index)

                contentItem: Item {
                    Text {
                        anchors.centerIn: parent
                        text: navButton.modelData
                        color: rail.currentIndex === navButton.index ? Theme.accent : Theme.inkMuted
                        font.family: "FontAwesome"
                        font.pixelSize: 20
                        font.weight: Font.Light
                    }
                }

                background: Rectangle {
                    radius: 14
                    color: "transparent"
                    border.width: rail.currentIndex === navButton.index || navButton.activeFocus ? 2 : 0
                    border.color: Theme.accent
                }
            }
        }
    }

    Button {
        id: profileButton
        anchors.bottom: parent.bottom
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottomMargin: 28
        width: 44
        height: 44
        onClicked: rail.profileRequested()
        contentItem: Item {
            DitherAvatar {
                anchors.fill: parent
                anchors.margins: 6
                name: authEngine.accountName.length > 0 ? authEngine.accountName : appState.profileName
            }
        }
        background: Rectangle {
            radius: 22
            color: profileButton.activeFocus ? "#213329" : "#18211c"
            border.width: profileButton.activeFocus ? 2 : 1
            border.color: profileButton.activeFocus ? Theme.accent : "#344039"
        }
    }
}
