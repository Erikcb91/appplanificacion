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
        WidgetStore.guardar(ctx, WidgetStore.aplicarToques(ctx, datos));   // sin perder tics del widget aún no importados
        Float texto = call.getFloat("texto", 1f);
        Float check = call.getFloat("check", 1f);
        WidgetStore.guardarAjustes(ctx,
                Math.max(0.5f, Math.min(2f, texto == null ? 1f : texto)),
                Math.max(0.5f, Math.min(2f, check == null ? 1f : check)));
        WidgetStore.guardarNav(ctx, call.getString("nav", "waze"));
        RutasWidgetProvider.actualizarTodos(ctx);
        call.resolve();
    }

    @PluginMethod
    public void configurarSync(PluginCall call) {
        Double ult = call.getDouble("ultimoPull", 0d);
        WidgetSync.configurar(getContext(), call.getString("codigo", ""), call.getString("apiKey", ""),
                call.getString("proyecto", ""), call.getString("appId", ""), ult == null ? 0L : (long) ult.doubleValue());
        RutasWidgetProvider.actualizarTodos(getContext());
        call.resolve();
    }

    /** Activa o desactiva la sincronización en segundo plano (servicio con notificación mínima). */
    @PluginMethod
    public void servicioSync(PluginCall call) {
        Boolean activo = call.getBoolean("activo", false);
        Context ctx = getContext();
        boolean on = activo != null && activo;
        WidgetSync.ponerSegundoPlano(ctx, on);
        try {
            if (on) BootReceiver.arrancar(ctx);
            else ctx.stopService(new android.content.Intent(ctx, SyncService.class));
        } catch (Exception e) {
            // Android no deja arrancarlo ahora: se reintenta al abrir la app
        }
        call.resolve();
    }

    @PluginMethod
    public void obtener(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("datos", WidgetStore.leer(getContext()));
        ret.put("toques", WidgetStore.toques(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void limpiarToques(PluginCall call) {
        Double hasta = call.getDouble("hasta", 0d);
        WidgetStore.limpiarToques(getContext(), hasta == null ? 0L : (long) hasta.doubleValue());
        call.resolve();
    }
}
