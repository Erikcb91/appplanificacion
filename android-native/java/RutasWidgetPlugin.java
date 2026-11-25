package com.erik.rutas;

import android.content.Context;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Puente entre la app web y el widget.
 *  - actualizar({datos}): la app guarda aquí el calendario y se refresca el widget.
 *  - obtener(): la app recupera el calendario (con las visitas marcadas desde el widget).
 */
@CapacitorPlugin(name = "RutasWidget")
public class RutasWidgetPlugin extends Plugin {

    @PluginMethod
    public void actualizar(PluginCall call) {
        String datos = call.getString("datos", "[]");
        Context ctx = getContext();
        WidgetStore.guardar(ctx, datos);
        Float texto = call.getFloat("texto", 1f);
        Float check = call.getFloat("check", 1f);
        WidgetStore.guardarAjustes(ctx,
                Math.max(0.5f, Math.min(2f, texto == null ? 1f : texto)),
                Math.max(0.5f, Math.min(2f, check == null ? 1f : check)));
        RutasWidgetProvider.actualizarTodos(ctx);
        call.resolve();
    }

    @PluginMethod
    public void configurarSync(PluginCall call) {
        Double ult = call.getDouble("ultimoPull", 0d);
        WidgetSync.configurar(getContext(), call.getString("codigo", ""), call.getString("apiKey", ""),
                call.getString("proyecto", ""), ult == null ? 0L : (long) ult.doubleValue());
        RutasWidgetProvider.actualizarTodos(getContext());
        call.resolve();
    }

    @PluginMethod
    public void obtener(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("datos", WidgetStore.leer(getContext()));
        call.resolve(ret);
    }
}
