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

# ---- Librería de Firestore (escucha en tiempo real) en android/app/build.gradle ----
import os
gradle = os.path.normpath(os.path.join(os.path.dirname(ruta), "..", "..", "build.gradle"))
if os.path.exists(gradle):
    g = open(gradle, encoding="utf-8").read()
    if "firebase-firestore" not in g:
        dep = ("dependencies {\n"
               "    implementation platform('com.google.firebase:firebase-bom:32.2.3')\n"
               "    implementation 'com.google.firebase:firebase-firestore'\n")
        i = g.index("\ndependencies {")
        g = g[:i + 1] + dep + g[i + len("\ndependencies {") + 1:]
        pk = ("android {\n    packagingOptions {\n        resources {\n"
              "            excludes += ['META-INF/INDEX.LIST', 'META-INF/DEPENDENCIES']\n        }\n    }\n")
        j = g.index("android {")
        g = g[:j] + pk + g[j + len("android {") + 1:]
        open(gradle, "w", encoding="utf-8").write(g)
        print("Firestore añadido a", gradle)
    else:
        print("Firestore ya estaba en", gradle)
else:
    sys.exit("ERROR: no se encontró " + gradle)
