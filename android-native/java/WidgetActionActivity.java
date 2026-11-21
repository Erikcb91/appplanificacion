package com.erik.rutas;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Recibe los toques sobre las filas del widget, hace la acción y se cierra al instante
 * (tema sin pantalla). Se usa una actividad, y no un receptor, porque desde una actividad
 * Android sí permite abrir Waze sin restricciones.
 */
public class WidgetActionActivity extends Activity {

    static final String EXTRA_ACCION = "accion";
    static final String EXTRA_ID = "id";
    static final String EXTRA_URL = "url";
    static final String ACCION_VISITAR = "visitar";
    static final String ACCION_WAZE = "waze";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent i = getIntent();
            String accion = i.getStringExtra(EXTRA_ACCION);
            if (ACCION_VISITAR.equals(accion)) {
                WidgetStore.alternarVisitada(this, i.getStringExtra(EXTRA_ID));
                RutasWidgetProvider.actualizarTodos(this);
            } else if (ACCION_WAZE.equals(accion)) {
                String url = i.getStringExtra(EXTRA_URL);
                if (url != null) {
                    Intent ver = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    ver.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(ver);
                }
            }
        } catch (Exception e) {
            // no hacer nada: la actividad se cierra igualmente
        }
        finish();
    }
}
