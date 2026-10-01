import QtQuick
QtObject {
    property string engine: ""
    property var connectedSources: []
    property int interval: 0
    property var data: ({})
    signal newData(string sourceName, var data)
    function connectSource(s) {}
    function disconnectSource(s) {}
}
