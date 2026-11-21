package com.erik.rutas;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Filas de la lista del widget: por cada día (desde el mostrado hacia delante) una cabecera
 * y sus oficinas pendientes. Las visitadas no aparecen.
 */
public class RutasWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Fabrica(getApplicationContext());
    }

    static String urlWaze(WidgetModel.Item it) {
        String destino = it.d.isEmpty() ? it.p : (it.p.isEmpty() ? it.d : it.d + ", " + it.p);
        try {
            return "https://waze.com/ul?q=" + URLEncoder.encode(destino, "UTF-8") + "&navigate=yes";
        } catch (Exception e) {
            return "https://waze.com/ul?q=" + destino + "&navigate=yes";
        }
    }

    static class Fabrica implements RemoteViewsFactory {
        private final Context ctx;
        private List<WidgetModel.Fila> filas = new ArrayList<WidgetModel.Fila>();

        Fabrica(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            Calendar ahora = Calendar.getInstance();
            List<WidgetModel.Item> items = WidgetModel.parse(WidgetStore.leer(ctx));
            String dia = WidgetModel.diaMostrado(WidgetStore.diaSel(ctx), WidgetStore.diaSelEn(ctx), ahora);
            filas = WidgetModel.construirFilas(items, dia, ahora);
        }

        @Override
        public void onDestroy() {
            filas.clear();
        }

        @Override
        public int getCount() {
            return filas.size();
        }

        @Override
        public RemoteViews getViewAt(int pos) {
            if (pos < 0 || pos >= filas.size()) {
                return new RemoteViews(ctx.getPackageName(), R.layout.widget_dia);
            }
            WidgetModel.Fila f = filas.get(pos);
            return f.cabecera ? vistaDia(f) : vistaOficina(f.item);
        }

        private RemoteViews vistaDia(WidgetModel.Fila f) {
            RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_dia);
            r.setTextViewText(R.id.dia_titulo, f.titulo);
            r.setTextViewText(R.id.dia_prov, f.provincias.isEmpty() ? "" : "📍 " + f.provincias);
            r.setTextViewText(R.id.dia_cuenta, f.hechas + "/" + f.total);
            return r;
        }

        private RemoteViews vistaOficina(WidgetModel.Item it) {
            RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
            float k = WidgetStore.escala(ctx);
            float t = WidgetStore.textoMul(ctx);
            float c = WidgetStore.checkMul(ctx);
            int pad = Math.round(11 * k * ctx.getResources().getDisplayMetrics().density);
            r.setViewPadding(R.id.item_root, Math.round(12 * k * ctx.getResources().getDisplayMetrics().density),
                    pad, Math.round(8 * k * ctx.getResources().getDisplayMetrics().density), pad);
            r.setTextViewTextSize(R.id.item_nombre, android.util.TypedValue.COMPLEX_UNIT_SP, 12 * k * t);
            r.setTextViewTextSize(R.id.item_detalle, android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f * k * t);
            r.setTextViewTextSize(R.id.item_check, android.util.TypedValue.COMPLEX_UNIT_SP, 14 * k * c);
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                r.setViewLayoutWidth(R.id.item_check, 28 * k * c, android.util.TypedValue.COMPLEX_UNIT_DIP);
                r.setViewLayoutHeight(R.id.item_check, 28 * k * c, android.util.TypedValue.COMPLEX_UNIT_DIP);
            }
            r.setTextViewText(R.id.item_nombre, (it.m ? "☀ " : "") + it.n);
            String detalle = it.d.isEmpty() ? it.p : (it.p.isEmpty() ? it.d : it.d + ", " + it.p);
            r.setTextViewText(R.id.item_detalle, detalle);
            boolean noche = (ctx.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int hue = WidgetModel.hueSemana(it.f);
            int fondo = it.m ? ContextCompat.getColor(ctx, R.color.w_manana)
                    : (noche ? WidgetModel.hsl(hue, 22, 25) : 0xFFFFFFFF);
            r.setInt(R.id.item_root, "setBackgroundColor", fondo);

            // ✓ marca como visitada (y la oficina desaparece); el resto de la fila abre Waze
            Intent visitar = new Intent();
            visitar.putExtra(WidgetActionActivity.EXTRA_ACCION, WidgetActionActivity.ACCION_VISITAR);
            visitar.putExtra(WidgetActionActivity.EXTRA_ID, it.i);
            r.setOnClickFillInIntent(R.id.item_check, visitar);

            Intent waze = new Intent();
            waze.putExtra(WidgetActionActivity.EXTRA_ACCION, WidgetActionActivity.ACCION_WAZE);
            waze.putExtra(WidgetActionActivity.EXTRA_URL, urlWaze(it));
            r.setOnClickFillInIntent(R.id.item_info, waze);
            return r;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
