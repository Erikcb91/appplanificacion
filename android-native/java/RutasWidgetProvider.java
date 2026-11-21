package com.erik.rutas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.List;

/**
 * Widget "mini app": cabecera con día y flechas, barra de progreso de la semana y una lista
 * con las oficinas pendientes de ese día, con los colores de la semana de la app.
 * Marcar una oficina como visitada la quita de la lista. Sábado y domingo parte del lunes.
 */
public class RutasWidgetProvider extends AppWidgetProvider {

    static final String ACCION_PREV = "com.erik.rutas.WIDGET_PREV";
    static final String ACCION_NEXT = "com.erik.rutas.WIDGET_NEXT";
    static final String ACCION_HOY = "com.erik.rutas.WIDGET_HOY";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        refrescar(ctx, mgr, ids);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, android.os.Bundle opciones) {
        super.onAppWidgetOptionsChanged(ctx, mgr, id, opciones);
        refrescar(ctx, mgr, new int[]{id});
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String accion = intent.getAction();
        if (accion == null) return;

        if (ACCION_PREV.equals(accion) || ACCION_NEXT.equals(accion)) {
            Calendar ahora = Calendar.getInstance();
            List<WidgetModel.Item> items = WidgetModel.parse(WidgetStore.leer(ctx));
            String actual = WidgetModel.diaMostrado(WidgetStore.diaSel(ctx), WidgetStore.diaSelEn(ctx), ahora);
            String destino = WidgetModel.vecino(items, actual, ACCION_PREV.equals(accion) ? -1 : 1);
            if (destino != null) {
                WidgetStore.elegirDia(ctx, destino, WidgetModel.iso(ahora));
            }
            actualizarTodos(ctx);
        } else if (ACCION_HOY.equals(accion)) {
            WidgetStore.limpiarDia(ctx);
            actualizarTodos(ctx);
        }
    }

    public static void actualizarTodos(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, RutasWidgetProvider.class));
        refrescar(ctx, mgr, ids);
    }

    private static void refrescar(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            mgr.updateAppWidget(id, construir(ctx, mgr, id));
        }
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_lista);
    }

    private static PendingIntent broadcast(Context ctx, int codigo, String accion) {
        Intent i = new Intent(ctx, RutasWidgetProvider.class);
        i.setAction(accion);
        return PendingIntent.getBroadcast(ctx, codigo, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static RemoteViews construir(Context ctx, AppWidgetManager mgr, int widgetId) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_rutas);

        Calendar ahora = Calendar.getInstance();
        List<WidgetModel.Item> items = WidgetModel.parse(WidgetStore.leer(ctx));
        String sel = WidgetStore.diaSel(ctx);
        String selEn = WidgetStore.diaSelEn(ctx);
        String dia = WidgetModel.diaMostrado(sel, selEn, ahora);
        boolean manual = WidgetModel.esManual(sel, selEn, ahora);

        // ----- cabecera -----
        v.setTextViewText(R.id.widget_titulo, WidgetModel.etiquetaDia(dia, ahora, dia.equals(WidgetModel.iso(WidgetModel.diaAuto(ahora)))));
        v.setViewVisibility(R.id.widget_prev,
                WidgetModel.vecino(items, dia, -1) != null ? View.VISIBLE : View.INVISIBLE);
        v.setViewVisibility(R.id.widget_next,
                WidgetModel.vecino(items, dia, 1) != null ? View.VISIBLE : View.INVISIBLE);
        v.setViewVisibility(R.id.widget_hoy, manual ? View.VISIBLE : View.GONE);

        // ----- tamaño según la altura real del widget -----
        float k = 1f;
        try {
            android.os.Bundle op = mgr.getAppWidgetOptions(widgetId);
            if (op != null) {
                boolean horizontal = ctx.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                int h = op.getInt(horizontal ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                        : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
                k = WidgetModel.escala(h);
            }
        } catch (Exception e) {
            k = 1f;
        }
        WidgetStore.guardarEscala(ctx, k);
        final int sp = android.util.TypedValue.COMPLEX_UNIT_SP;
        final float t = WidgetStore.textoMul(ctx);
        v.setTextViewTextSize(R.id.widget_titulo, sp, 13 * k * t);
        v.setTextViewTextSize(R.id.widget_prov, sp, 9.5f * k * t);
        v.setTextViewTextSize(R.id.widget_resumen, sp, 9.5f * k * t);
        v.setTextViewTextSize(R.id.widget_hoy, sp, 12 * k);
        v.setTextViewTextSize(R.id.widget_prev, sp, 28 * k);
        v.setTextViewTextSize(R.id.widget_next, sp, 28 * k);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            final int dp = android.util.TypedValue.COMPLEX_UNIT_DIP;
            v.setViewLayoutWidth(R.id.widget_prev, 38 * k, dp);
            v.setViewLayoutHeight(R.id.widget_prev, 38 * k, dp);
            v.setViewLayoutWidth(R.id.widget_next, 38 * k, dp);
            v.setViewLayoutHeight(R.id.widget_next, 38 * k, dp);
        }

        // ----- colores del día: los mismos que en la app (tono por semana) -----
        boolean noche = (ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int hue = WidgetModel.hueSemana(dia);
        int cabBg = noche ? WidgetModel.hsl(hue, 34, 27) : WidgetModel.hsl(hue, 76, 88);
        int cabFg = noche ? WidgetModel.hsl(hue, 80, 90) : WidgetModel.hsl(hue, 55, 22);
        int cuerpoBg = noche ? WidgetModel.hsl(hue, 20, 17) : WidgetModel.hsl(hue, 35, 95);
        int acento = noche ? WidgetModel.hsl(hue, 70, 70) : WidgetModel.hsl(hue, 68, 40);
        v.setInt(R.id.widget_fondo, "setColorFilter", cuerpoBg);
        v.setInt(R.id.widget_cab_fondo, "setColorFilter", cabBg);
        v.setTextColor(R.id.widget_titulo, cabFg);
        v.setTextColor(R.id.widget_prov, cabFg);
        v.setTextColor(R.id.widget_prev, acento);
        v.setTextColor(R.id.widget_next, acento);
        List<WidgetModel.Item> delDia = WidgetModel.delDia(items, dia);
        String prov = WidgetModel.unir(WidgetModel.provincias(delDia), " · ");
        v.setTextViewText(R.id.widget_prov, (prov.isEmpty() ? "" : "📍 " + prov + "  ·  ")
                + WidgetModel.hechas(delDia) + "/" + delDia.size());

        // ----- progreso de la semana del día mostrado -----
        int[] sw = WidgetModel.resumenSemana(items, dia);   // {hechas, total, pendientes mañana}
        if (sw[1] == 0) {
            v.setViewVisibility(R.id.widget_progreso, View.INVISIBLE);
            v.setTextViewText(R.id.widget_resumen, "Sin oficinas esta semana");
        } else {
            v.setViewVisibility(R.id.widget_progreso, View.VISIBLE);
            v.setProgressBar(R.id.widget_progreso, sw[1], sw[0], false);
            String texto = "Semana " + WidgetModel.rangoSemana(dia) + ": " + sw[0] + " de " + sw[1];
            if (sw[2] > 0) texto += "  ·  ☀ " + sw[2];
            v.setTextViewText(R.id.widget_resumen, texto);
        }

        // ----- texto cuando la lista está vacía -----
        boolean hay = !delDia.isEmpty();
        v.setTextViewText(R.id.widget_vacio, hay ? "✓ ¡Todo hecho!" : "No hay oficinas");

        // ----- lista -----
        Intent svc = new Intent(ctx, RutasWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.widget_lista, svc);
        v.setEmptyView(R.id.widget_lista, R.id.widget_vacio);

        // Toques en las filas: plantilla hacia la actividad intermedia (cada fila rellena
        // si es "visitar" o "waze").
        Intent plantilla = new Intent(ctx, WidgetActionActivity.class);
        PendingIntent piPlantilla = PendingIntent.getActivity(ctx, 10, plantilla,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        v.setPendingIntentTemplate(R.id.widget_lista, piPlantilla);

        // ----- botones -----
        v.setOnClickPendingIntent(R.id.widget_prev, broadcast(ctx, 1, ACCION_PREV));
        v.setOnClickPendingIntent(R.id.widget_next, broadcast(ctx, 2, ACCION_NEXT));
        v.setOnClickPendingIntent(R.id.widget_hoy, broadcast(ctx, 3, ACCION_HOY));

        Intent abrir = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (abrir != null) {
            PendingIntent piAbrir = PendingIntent.getActivity(ctx, 4, abrir,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_centro, piAbrir);
            v.setOnClickPendingIntent(R.id.widget_resumen, piAbrir);
        }
        return v;
    }
}
