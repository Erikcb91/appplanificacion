"""Añade al AndroidManifest.xml generado por Capacitor el widget, el servicio de su lista
y la actividad (sin pantalla) que recibe los toques sobre las filas."""
import sys

ruta = sys.argv[1]
texto = open(ruta, encoding="utf-8").read()

if "RutasWidgetProvider" in texto:
    print("Manifest ya contiene el widget; no se toca")
    sys.exit(0)

bloque = """
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

        <service
            android:name=".RutasWidgetService"
            android:exported="false"
            android:permission="android.permission.BIND_REMOTEVIEWS" />

        <activity
            android:name=".WidgetActionActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:taskAffinity=""
            android:theme="@android:style/Theme.NoDisplay" />
    """

if "</application>" not in texto:
    sys.exit("ERROR: no se encontró </application> en el manifest")

texto = texto.replace("</application>", bloque + "</application>", 1)
open(ruta, "w", encoding="utf-8").write(texto)
print("Widget, servicio y actividad añadidos al manifest")
