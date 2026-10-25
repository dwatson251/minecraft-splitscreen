// A player's tile while their Minecraft starts (about 30 s): AppRun opens it as soon as the player presses START,
// titled like their game window ("P<n> Loading"), so the tiler splits the screen at once. The tiler closes it when
// that player's game window appears.  qml6 loading.qml -- <player number>
import QtQuick
import QtQuick.Window

Window {
    id: root
    readonly property string player: Qt.application.arguments[Qt.application.arguments.length - 1]
    title: "P" + player + " Loading"
    visible: true
    width: 640
    height: 400
    color: "#1b1b1f"

    Column {
        anchors.centerIn: parent
        spacing: Math.round(root.height / 24)

        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "Player " + root.player + " is joining"
            color: "#ffffff"
            font.pixelSize: Math.max(16, Math.round(root.height / 12))
            font.bold: true
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "Starting Minecraft…"
            color: "#a0a0a8"
            font.pixelSize: Math.max(12, Math.round(root.height / 22))
        }
        Row {
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: Math.round(root.height / 40)
            Repeater {
                model: 3
                Rectangle {
                    required property int index
                    width: Math.round(root.height / 28)
                    height: width
                    color: "#62b04a"   // grass green
                    SequentialAnimation on opacity {
                        loops: Animation.Infinite
                        PauseAnimation { duration: index * 200 }
                        NumberAnimation { from: 0.2; to: 1; duration: 400 }
                        NumberAnimation { from: 1; to: 0.2; duration: 400 }
                        PauseAnimation { duration: (2 - index) * 200 }
                    }
                }
            }
        }
    }
}
