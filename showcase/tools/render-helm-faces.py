import sys, os, glob, time
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
os.environ.setdefault("QT_QUICK_BACKEND", "software")
from PySide6.QtGui import QGuiApplication, QColor
from PySide6.QtQuick import QQuickView
from PySide6.QtCore import QUrl, QTimer, QEventLoop

app = QGuiApplication(sys.argv)
out = sys.argv[1]
faces = sys.argv[2:]
def wait(ms):
    loop = QEventLoop(); QTimer.singleShot(ms, loop.quit); loop.exec()
for face in faces:
    name = face.split("/")[-4] if "/contents/ui/" in face else os.path.basename(face)
    v = QQuickView()
    v.setColor(QColor("#090002"))
    v.setResizeMode(QQuickView.SizeViewToRootObject)
    v.setSource(QUrl.fromLocalFile(face))
    errs = v.errors()
    if errs or v.rootObject() is None:
        print("FAIL", name, [e.toString() for e in errs][:2]); continue
    r = v.rootObject()
    w = int(r.property("implicitWidth") or r.width() or 480); h = int(r.property("implicitHeight") or r.height() or 320)
    if w < 50 or h < 50: w, h = 480, 320
    v.setResizeMode(QQuickView.SizeRootObjectToView)
    v.resize(w, h); v.show()
    wait(2600)
    img = v.grabWindow()
    p = os.path.join(out, name + ".png"); img.save(p)
    print("OK", name, w, h)
    v.close(); v.deleteLater()
