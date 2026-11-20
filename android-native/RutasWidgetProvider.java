package com.erik.rutas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Widget de pantalla de inicio: resumen de las oficinas de HOY.
 * Lee el calendario guardado por RutasWidgetPlugin y calcula "hoy" al pintarse,
 * así cambia solo de día aunque no se abra la app.
 */
public class RutasWidgetProvider extends AppWidgetProvider {

    static final String PREFS = "rutas_widget";
    static final String KEY = "datos";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            mgr.updateAppWidget(id, construir(ctx));
        }
    }

    public static void actualizarTodos(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, RutasWidgetProvider.class));
        for (int id : ids) {
            mgr.updateAppWidget(id, construir(ctx));
        }
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    private static RemoteViews construir(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_rutas);

        Date ahora = new Date();
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(ahora);
        String titulo = capitalizar(new SimpleDateFormat("EEEE d MMM", new Locale("es", "ES")).format(ahora));

        int total = 0;
        int hechas = 0;
        int pendManana = 0;
        String proxNombre = null;
        String proxDetalle = "";
        boolean proxManana = false;

        try {
            String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!hoy.equals(o.optString("f"))) continue;
                total++;
                boolean visitada = o.optInt("v") == 1;
                boolean manana = o.optInt("m") == 1;
                if (visitada) {
                    hechas++;
                } else {
                    if (manana) pendManana++;
                    if (proxNombre == null) {
                        proxNombre = o.optString("n");
                        String d = o.optString("d");
                        String p = o.optString("p");
                        proxDetalle = d.isEmpty() ? p : (p.isEmpty() ? d : d + ", " + p);
                        proxManana = manana;
                    }
                }
            }
        } catch (Exception e) {
            // datos ilegibles: se muestra el estado vacío
        }

        v.setTextViewText(R.id.widget_titulo, titulo);

        if (total == 0) {
            v.setTextViewText(R.id.widget_resumen, "Hoy no tienes oficinas");
            v.setViewVisibility(R.id.widget_progreso, View.GONE);
            v.setViewVisibility(R.id.widget_proxima, View.GONE);
            v.setViewVisibility(R.id.widget_detalle, View.GONE);
        } else if (proxNombre == null) {
            v.setTextViewText(R.id.widget_resumen, "✓ Ruta completada (" + total + ")");
            v.setViewVisibility(R.id.widget_progreso, View.VISIBLE);
            v.setProgressBar(R.id.widget_progreso, total, total, false);
            v.setViewVisibility(R.id.widget_proxima, View.GONE);
            v.setViewVisibility(R.id.widget_detalle, View.GONE);
        } else {
            String resumen = hechas + " de " + total + " visitadas";
            if (pendManana > 0) resumen += "  ·  ☀ " + pendManana + " mañana";
            v.setTextViewText(R.id.widget_resumen, resumen);
            v.setViewVisibility(R.id.widget_progreso, View.VISIBLE);
            v.setProgressBar(R.id.widget_progreso, total, hechas, false);
            v.setViewVisibility(R.id.widget_proxima, View.VISIBLE);
            v.setTextViewText(R.id.widget_proxima, (proxManana ? "☀ " : "Próxima: ") + proxNombre);
            if (proxDetalle.isEmpty()) {
                v.setViewVisibility(R.id.widget_detalle, View.GONE);
            } else {
                v.setViewVisibility(R.id.widget_detalle, View.VISIBLE);
                v.setTextViewText(R.id.widget_detalle, proxDetalle);
            }
        }

        // Al tocar el widget se abre la app
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (launch != null) {
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_root, pi);
        }
        return v;
    }
}
