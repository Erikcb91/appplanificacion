"""Añade el receiver del widget al AndroidManifest.xml generado por Capacitor."""
import sys

ruta = sys.argv[1]
texto = open(ruta, encoding="utf-8").read()

if "RutasWidgetProvider" in texto:
    print("Manifest ya contiene el widget; no se toca")
    sys.exit(0)

receiver = """
        <receiver
            android:name=".RutasWidgetProvider"
            android:exported="true"
            android:label="Mis Rutas">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/rutas_widget_info" />
        </receiver>
    """

if "</application>" not in texto:
    sys.exit("ERROR: no se encontró </application> en el manifest")

texto = texto.replace("</application>", receiver + "</application>", 1)
open(ruta, "w", encoding="utf-8").write(texto)
print("Receiver del widget añadido al manifest")
