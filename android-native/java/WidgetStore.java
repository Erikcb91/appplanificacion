package com.erik.rutas;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Almacén del widget: calendario enviado por la app y día elegido con las flechas. */
final class WidgetStore {

    static final String PREFS = "rutas_widget";
    static final String KEY = "datos";
    private static final String KEY_DIA = "dia_sel";
    private static final String KEY_DIA_EN = "dia_sel_en";

    static final Object LOCK = new Object();

    private WidgetStore() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String leer(Context ctx) {
        return prefs(ctx).getString(KEY, "[]");
    }

    static void guardar(Context ctx, String json) {
        prefs(ctx).edit().putString(KEY, json).apply();
    }

    /** Multiplicadores elegidos en el menú de la app (1 = normal). */
    static float textoMul(Context ctx) {
        return prefs(ctx).getFloat("texto_mul", 1f);
    }

    static float checkMul(Context ctx) {
        return prefs(ctx).getFloat("check_mul", 1f);
    }

    static void guardarAjustes(Context ctx, float texto, float check) {
        prefs(ctx).edit().putFloat("texto_mul", texto).putFloat("check_mul", check).apply();
    }

    static String nav(Context ctx) {
        return prefs(ctx).getString("nav", "waze");
    }

    static void guardarNav(Context ctx, String nav) {
        prefs(ctx).edit().putString("nav", "maps".equals(nav) ? "maps" : "waze").apply();
    }

    static float escala(Context ctx) {
        return prefs(ctx).getFloat("escala", 1f);
    }

    static void guardarEscala(Context ctx, float k) {
        prefs(ctx).edit().putFloat("escala", k).apply();
    }

    static String diaSel(Context ctx) {
        return prefs(ctx).getString(KEY_DIA, "");
    }

    static String diaSelEn(Context ctx) {
        return prefs(ctx).getString(KEY_DIA_EN, "");
    }

    static void elegirDia(Context ctx, String dia, String hoy) {
        prefs(ctx).edit().putString(KEY_DIA, dia).putString(KEY_DIA_EN, hoy).apply();
    }

    static void limpiarDia(Context ctx) {
        prefs(ctx).edit().remove(KEY_DIA).remove(KEY_DIA_EN).apply();
    }

    /** Marca/desmarca una oficina como visitada desde el widget. */
    static boolean alternarVisitada(Context ctx, String id) {
      synchronized (LOCK) {
        List<WidgetModel.Item> items = WidgetModel.parse(leer(ctx));
        String hora = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        boolean ok = WidgetModel.alternarVisitada(items, id, hora);
        if (ok) guardar(ctx, WidgetModel.toJson(items));
        return ok;
      }
    }

    // ---------- toques hechos en el widget (con su hora real) ----------
    // La app solo importa estos toques; no copia el estado entero del widget, que puede traer datos
    // descargados de la nube y no son cambios hechos por ti.
    private static final String KEY_TOQUES = "toques";

    static String visitaDe(Context ctx, String id) {
        for (WidgetModel.Item it : WidgetModel.parse(leer(ctx))) if (id.equals(it.i)) return it.v;
        return "";
    }

    static synchronized void registrarToque(Context ctx, String id, String v, long t) {
        try {
            JSONArray a = new JSONArray(prefs(ctx).getString(KEY_TOQUES, "[]"));
            a.put(new JSONObject().put("i", id).put("v", v).put("t", String.valueOf(t)));
            prefs(ctx).edit().putString(KEY_TOQUES, a.toString()).apply();
        } catch (Exception e) {
            // sin registro: la app no importará este toque
        }
    }

    static String toques(Context ctx) {
        return prefs(ctx).getString(KEY_TOQUES, "[]");
    }

    static synchronized void limpiarToques(Context ctx, long hasta) {
        try {
            JSONArray a = new JSONArray(prefs(ctx).getString(KEY_TOQUES, "[]")), resto = new JSONArray();
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                long t = Long.parseLong(o.optString("t", "0"));
                if (t > hasta) resto.put(o);
            }
            prefs(ctx).edit().putString(KEY_TOQUES, resto.toString()).apply();
        } catch (Exception e) {
            // se dejan como están
        }
    }

    /** Reaplica sobre unos datos recién enviados por la app los toques que todavía no ha importado. */
    static String aplicarToques(Context ctx, String datos) {
        try {
            JSONArray a = new JSONArray(prefs(ctx).getString(KEY_TOQUES, "[]"));
            if (a.length() == 0) return datos;
            List<WidgetModel.Item> items = WidgetModel.parse(datos);
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                for (WidgetModel.Item it : items) {
                    if (it.i.equals(o.optString("i"))) {
                        it.v = o.optString("v");
                        if (!it.v.isEmpty()) it.pe = false;
                    }
                }
            }
            return WidgetModel.toJson(items);
        } catch (Exception e) {
            return datos;
        }
    }
}
