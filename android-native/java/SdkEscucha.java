package com.erik.rutas;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Escucha en tiempo real de los cambios de estado del grupo (librería oficial de Firestore).
 * Cuando otro teléfono cambia algo, llega en ~1 s y se aplica al widget con la misma lógica de siempre.
 * Si algo falla (red, permisos…), el servicio sigue consultando cada pocos segundos como respaldo.
 */
final class SdkEscucha {

    private static final String NOMBRE = "rutas";
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static ListenerRegistration registro;
    private static String codigoActual = "";
    private static boolean ajustado = false;

    private SdkEscucha() {}

    static synchronized boolean activa() {
        return registro != null;
    }

    /** Abre la escucha si hace falta (o la cierra si ya no hay grupo). */
    static synchronized void asegurar(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (!WidgetSync.activo(app)) { parar(); return; }
        String cod = WidgetSync.codigo(app);
        if (registro != null && cod.equals(codigoActual)) return;
        parar();
        FirebaseApp fa;
        try {
            fa = FirebaseApp.getInstance(NOMBRE);
        } catch (IllegalStateException e) {
            FirebaseOptions o = new FirebaseOptions.Builder()
                    .setApiKey(WidgetSync.apiKey(app))
                    .setApplicationId(WidgetSync.appId(app))
                    .setProjectId(WidgetSync.proyecto(app))
                    .build();
            fa = FirebaseApp.initializeApp(app, o, NOMBRE);
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance(fa);
        if (!ajustado) {
            try {
                db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
            } catch (Throwable t) {
                // ya estaba configurado
            }
            ajustado = true;
        }
        long desde = Math.max(0L, WidgetSync.pull(app) - 5000L);
        Query q = db.collection("grupos").document(cod).collection("estado")
                .whereGreaterThan("ts", new Timestamp(new Date(desde)));
        final String proyecto = WidgetSync.proyecto(app), codigo = cod;
        codigoActual = cod;
        registro = q.addSnapshotListener(EX, new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot snap, FirebaseFirestoreException error) {
                if (error != null || snap == null) {
                    cerrarPorError();   // el servicio la volverá a abrir en su siguiente vuelta
                    return;
                }
                try {
                    JSONArray arr = new JSONArray();
                    for (DocumentChange ch : snap.getDocumentChanges()) {
                        if (ch.getType() == DocumentChange.Type.REMOVED) continue;
                        QueryDocumentSnapshot d = ch.getDocument();
                        Map<String, Object> datos = new java.util.HashMap<String, Object>(d.getData());
                        // la hora del servidor llega como Timestamp: se pasa a Date para unificar
                        for (Map.Entry<String, Object> e : datos.entrySet()) {
                            if (e.getValue() instanceof Timestamp) e.setValue(((Timestamp) e.getValue()).toDate());
                        }
                        arr.put(WidgetSync.documentoJson(proyecto, codigo, d.getId(), datos));
                    }
                    if (arr.length() == 0) return;
                    long[] r = WidgetSync.aplicarRespuesta(app, arr.toString());
                    if (r[0] > 0) RutasWidgetProvider.actualizarTodos(app);
                } catch (Throwable t) {
                    // un aviso ilegible no debe parar la escucha; el respaldo lo recoge
                }
            }
        });
    }

    private static synchronized void cerrarPorError() {
        parar();
    }

    static synchronized void parar() {
        if (registro != null) {
            try {
                registro.remove();
            } catch (Throwable t) {
                // ya cerrada
            }
            registro = null;
        }
        codigoActual = "";
    }
}
