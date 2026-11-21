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
        RutasWidgetProvider.actualizarTodos(ctx);
        call.resolve();
    }

    @PluginMethod
    public void obtener(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("datos", WidgetStore.leer(getContext()));
        call.resolve(ret);
    }
}
