package com.erik.rutas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Reinicia la sincronización en segundo plano al encender el teléfono o actualizar la app. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            if (WidgetSync.activo(ctx) && WidgetSync.segundoPlano(ctx)) arrancar(ctx);
        } catch (Exception e) {
            // si Android no lo permite ahora, se arrancará al abrir la app
        }
    }

    static void arrancar(Context ctx) {
        Intent s = new Intent(ctx, SyncService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(s); else ctx.startService(s);
    }
}
