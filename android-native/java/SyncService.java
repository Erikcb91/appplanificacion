package com.erik.rutas;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Mantiene el widget al día aunque la app esté cerrada: cada pocos segundos baja de la nube lo que
 * han cambiado los otros teléfonos (solo lo nuevo, nunca sube el estado completo) y refresca el widget.
 *
 * Android obliga a que un servicio así tenga una notificación. Es de prioridad mínima y silenciosa;
 * en Android 13 o superior, si no se concede el permiso de notificaciones, no se ve en ningún sitio.
 */
public class SyncService extends Service {

    static final String CANAL = "rutas_sync";
    private static final int ID_NOTIF = 4201;
    private static final long CADA_ACTIVO = 5000L;       // pantalla encendida
    private static final long CADA_APAGADO = 60000L;     // pantalla apagada
    private static final long CADA_RESPALDO = 45000L;    // con la escucha en tiempo real activa, solo como seguro
    private static final long MAX_ESPERA = 120000L;

    private final Object lock = new Object();
    private volatile boolean corriendo = false;
    private volatile boolean despertar = false;
    private Thread hilo;
    private BroadcastReceiver pantalla;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            ponerEnPrimerPlano();
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (hilo == null || !hilo.isAlive()) {
            corriendo = true;
            registrarPantalla();
            hilo = new Thread(new Runnable() {
                @Override
                public void run() {
                    bucle();
                }
            }, "rutas-sync");
            hilo.start();
        } else {
            despertar();
        }
        return START_STICKY;
    }

    private void ponerEnPrimerPlano() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CANAL, "Sincronización", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            if (nm != null) nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CANAL);
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_MIN);
        }
        b.setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Mis Rutas")
                .setContentText("Sincronizado")
                .setOngoing(true)
                .setShowWhen(false);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID_NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ID_NOTIF, n);
        }
    }

    /** Al encender la pantalla o desbloquear se sincroniza enseguida. */
    private void registrarPantalla() {
        if (pantalla != null) return;
        pantalla = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                despertar();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(pantalla, f);
    }

    private void despertar() {
        synchronized (lock) {
            despertar = true;
            lock.notifyAll();
        }
    }

    private long intervalo() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return CADA_APAGADO;
        } catch (Exception e) {
            // se usa el intervalo normal
        }
        return CADA_ACTIVO;
    }

    private void bucle() {
        long espera = 0;
        Context app = getApplicationContext();
        while (corriendo) {
            if (!WidgetSync.activo(app) || !WidgetSync.segundoPlano(app)) {
                break;
            }
            boolean escucha = false;
            try {
                SdkEscucha.asegurar(app);   // escucha en tiempo real (~1 s)
                escucha = SdkEscucha.activa();
            } catch (Throwable t) {
                escucha = false;            // sin librería o sin permiso: se consulta cada pocos segundos
            }
            try {
                int n = WidgetSync.refrescar(app);
                if (n > 0) RutasWidgetProvider.actualizarTodos(app);
                espera = n < 0 ? Math.min(MAX_ESPERA, espera == 0 ? 30000L : espera * 2) : 0;   // sin red: se espacia
            } catch (Exception e) {
                espera = Math.min(MAX_ESPERA, espera == 0 ? 30000L : espera * 2);
            }
            long ms = Math.max(escucha ? CADA_RESPALDO : intervalo(), espera);
            synchronized (lock) {
                if (!despertar) {
                    try {
                        lock.wait(ms);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                despertar = false;
            }
        }
        corriendo = false;
        try { SdkEscucha.parar(); } catch (Throwable t) { /* sin librería */ }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        corriendo = false;
        despertar();
        try { SdkEscucha.parar(); } catch (Throwable t) { /* sin librería */ }
        if (pantalla != null) {
            try {
                unregisterReceiver(pantalla);
            } catch (Exception e) {
                // ya estaba sin registrar
            }
            pantalla = null;
        }
        super.onDestroy();
    }
}
