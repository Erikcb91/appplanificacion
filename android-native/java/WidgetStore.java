package com.erik.rutas;

import android.content.Context;
import android.content.SharedPreferences;

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
        List<WidgetModel.Item> items = WidgetModel.parse(leer(ctx));
        String hora = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        boolean ok = WidgetModel.alternarVisitada(items, id, hora);
        if (ok) guardar(ctx, WidgetModel.toJson(items));
        return ok;
    }
}
