package com.erik.rutas;

import android.content.Context;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Recibe desde la app web el calendario de rutas (JSON) y refresca el widget.
 */
@CapacitorPlugin(name = "RutasWidget")
public class RutasWidgetPlugin extends Plugin {

    @PluginMethod
    public void actualizar(PluginCall call) {
        String datos = call.getString("datos", "[]");
        Context ctx = getContext();
        ctx.getSharedPreferences(RutasWidgetProvider.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(RutasWidgetProvider.KEY, datos)
                .apply();
        RutasWidgetProvider.actualizarTodos(ctx);
        call.resolve();
    }
}
