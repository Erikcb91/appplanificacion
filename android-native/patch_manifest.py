"""Añade al AndroidManifest.xml generado por Capacitor el widget, el servicio de su lista,
la actividad (sin pantalla) que recibe los toques, y el servicio de sincronización en segundo plano."""
import sys

ruta = sys.argv[1]
texto = open(ruta, encoding="utf-8").read()

if "</application>" not in texto:
    sys.exit("ERROR: no se encontró </application> en el manifest")

widget = """
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

segundo_plano = """
        <service
            android:name=".SyncService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
    """

permisos = """
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
"""

if "RutasWidgetProvider" not in texto:
    texto = texto.replace("</application>", widget + "</application>", 1)
    print("Widget, servicio y actividad añadidos al manifest")
if ".SyncService" not in texto:
    texto = texto.replace("</application>", segundo_plano + "</application>", 1)
    if "FOREGROUND_SERVICE\"" not in texto:
        i = texto.index("<application")
        texto = texto[:i] + permisos.strip("\n") + "\n\n    " + texto[i:]
    print("Servicio de sincronización en segundo plano añadido al manifest")

open(ruta, "w", encoding="utf-8").write(texto)
