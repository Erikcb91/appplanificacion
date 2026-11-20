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
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Widget de pantalla de inicio: resumen de las oficinas del día en curso.
 * Sábado y domingo muestra las del lunes siguiente.
 * Lee el calendario guardado por RutasWidgetPlugin y calcula el día al pintarse,
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

    /**
     * Día que debe mostrar el widget: el mismo día de lunes a viernes;
     * el lunes siguiente si hoy es sábado o domingo. No modifica el parámetro.
     */
    static Calendar diaObjetivo(Calendar ahora) {
        Calendar c = (Calendar) ahora.clone();
        int dow = c.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SATURDAY) {
            c.add(Calendar.DAY_OF_MONTH, 2);
        } else if (dow == Calendar.SUNDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return c;
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    private static RemoteViews construir(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_rutas);

        Calendar ahora = Calendar.getInstance();
        Calendar objetivo = diaObjetivo(ahora);
        boolean adelantado = objetivo.get(Calendar.DAY_OF_YEAR) != ahora.get(Calendar.DAY_OF_YEAR)
                || objetivo.get(Calendar.YEAR) != ahora.get(Calendar.YEAR);

        String dia = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(objetivo.getTime());
        String fecha = new SimpleDateFormat("EEEE d MMM", new Locale("es", "ES")).format(objetivo.getTime());

        int total = 0;
        int hechas = 0;
        int pendManana = 0;
        String proxNombre = null;
        String proxDetalle = "";
        boolean proxManana = false;
        Set<String> provincias = new LinkedHashSet<String>();

        try {
            String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!dia.equals(o.optString("f"))) continue;
                total++;
                String pv = o.optString("pv");
                if (!pv.isEmpty()) provincias.add(pv);
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

        String titulo = adelantado ? "Próximo " + fecha : capitalizar(fecha);
        if (!provincias.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String p : provincias) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p);
            }
            titulo += "  ·  " + sb;
        }
        v.setTextViewText(R.id.widget_titulo, titulo);

        if (total == 0) {
            v.setTextViewText(R.id.widget_resumen, adelantado ? "El lunes no tienes oficinas" : "Hoy no tienes oficinas");
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
