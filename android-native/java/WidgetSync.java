package com.erik.rutas;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Sincronización mínima desde el widget con Firestore (API REST), sin abrir la app:
 *  - subirVisita: sube SOLO la oficina que acabas de marcar o desmarcar (si falla, queda en cola).
 *  - bajar: descarga SOLO lo cambiado en la nube desde la última vez y lo aplica al widget.
 * Nunca sube el estado completo (no es una copia de seguridad). La app sigue siendo la que manda:
 * al abrirla hace su sincronización completa.
 */
final class WidgetSync {

    private static final String P = "rutas_sync";
    private static final int T_CONEXION = 3000, T_LECTURA = 4000;

    private WidgetSync() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    // ---------- configuración (la manda la app) ----------

    static void configurar(Context c, String codigo, String apiKey, String proyecto, String appId, long ultimoPullApp) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (codigo == null || codigo.isEmpty()) {
            e.clear();
        } else {
            e.putString("codigo", codigo).putString("key", apiKey).putString("proyecto", proyecto).putString("appid", appId == null ? "" : appId);
            if (!codigo.equals(prefs(c).getString("codigo", ""))) e.remove("pull").remove("cola");
            // lo que la app ya ha leído no hace falta volver a descargarlo
            if (ultimoPullApp > prefs(c).getLong("pull", 0L)) e.putLong("pull", ultimoPullApp);
        }
        e.apply();
    }

    /** ¿Debe mantenerse el servicio en segundo plano? (se guarda aparte para que sobreviva a «desactivar») */
    static boolean segundoPlano(Context c) {
        return c.getSharedPreferences("rutas_bg", Context.MODE_PRIVATE).getBoolean("on", false);
    }

    static void ponerSegundoPlano(Context c, boolean on) {
        c.getSharedPreferences("rutas_bg", Context.MODE_PRIVATE).edit().putBoolean("on", on).apply();
    }

    static String codigo(Context c) { return prefs(c).getString("codigo", ""); }
    static String apiKey(Context c) { return prefs(c).getString("key", ""); }
    static String proyecto(Context c) { return prefs(c).getString("proyecto", ""); }
    static String appId(Context c) { return prefs(c).getString("appid", ""); }
    static long pull(Context c) { return prefs(c).getLong("pull", 0L); }

    static boolean activo(Context c) {
        return !prefs(c).getString("codigo", "").isEmpty();
    }

    // ---------- cola de subidas pendientes ----------

    private static final Object LOCK_COLA = new Object();

    private static JSONArray cola(Context c) {
        try { return new JSONArray(prefs(c).getString("cola", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private static void guardarCola(Context c, JSONArray a) {
        prefs(c).edit().putString("cola", a.toString()).apply();
    }

    /** Una subida pendiente por oficina: se guarda siempre la más reciente. */
    static void encolar(Context c, String id, String v, long t) {
      synchronized (LOCK_COLA) {
        JSONArray a = cola(c), nueva = new JSONArray();
        try {
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.optJSONObject(k);
                if (o != null && !id.equals(o.optString("i"))) nueva.put(o);
            }
            nueva.put(new JSONObject().put("i", id).put("v", v).put("t", String.valueOf(t)));
        } catch (Exception e) {
            return;
        }
        guardarCola(c, nueva);
      }
    }

    // ---------- lógica pura (probada en el PC) ----------

    private static JSONObject campo(String tipo, String valor) throws Exception {
        return new JSONObject().put(tipo, valor);
    }

    /**
     * Cuerpo del :commit que sube v (y quita pendiente si hay visita) de una oficina.
     * t_v/t_pe llevan la hora real del toque; ts lo pone el servidor.
     */
    static String cuerpoVisita(String proyecto, String codigo, String id, String v, long t) throws Exception {
        JSONObject campos = new JSONObject();
        JSONArray mask = new JSONArray();
        campos.put("v", campo("stringValue", v)); mask.put("v");
        campos.put("t_v", campo("integerValue", String.valueOf(t))); mask.put("t_v");
        if (!v.isEmpty()) {
            campos.put("pe", campo("integerValue", "0")); mask.put("pe");
            campos.put("t_pe", campo("integerValue", String.valueOf(t))); mask.put("t_pe");
        }
        JSONObject up = new JSONObject()
                .put("name", "projects/" + proyecto + "/databases/(default)/documents/grupos/" + codigo + "/estado/" + id)
                .put("fields", campos);
        JSONObject w = new JSONObject().put("update", up).put("updateMask", new JSONObject().put("fieldPaths", mask))
                .put("updateTransforms", new JSONArray().put(
                        new JSONObject().put("fieldPath", "ts").put("setToServerValue", "REQUEST_TIME")));
        return new JSONObject().put("writes", new JSONArray().put(w)).toString();
    }

    /** Milisegundos de un instante RFC 3339 de Firestore (2026-11-26T09:20:00.123456Z). */
    static long parseIso(String s) {
        try {
            String base = s.substring(0, 19);
            long ms = 0;
            int punto = s.indexOf('.');
            if (punto > 0) {
                String frac = s.substring(punto + 1).replaceAll("[^0-9]", "");
                while (frac.length() < 3) frac += "0";
                ms = Long.parseLong(frac.substring(0, 3));
            }
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(base).getTime() + ms;
        } catch (Exception e) {
            return 0L;
        }
    }

    static String iso(long ms) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date(Math.max(0L, ms)));
    }

    private static String sv(JSONObject f, String k) {
        JSONObject o = f.optJSONObject(k);
        return o == null ? null : (o.has("stringValue") ? o.optString("stringValue") : null);
    }

    private static Long iv(JSONObject f, String k) {
        JSONObject o = f.optJSONObject(k);
        if (o != null && o.has("timestampValue")) return parseIso(o.optString("timestampValue"));
        if (o == null || !o.has("integerValue")) return null;
        try { return Long.parseLong(o.optString("integerValue")); } catch (Exception e) { return null; }
    }

    /**
     * Aplica al widget la respuesta de runQuery. Devuelve {cambios, tsMax}.
     * Solo toca visita, mañana, pendiente, nota y día movido; el orden lo trae la app al abrirse.
     */
    static long[] aplicar(List<WidgetModel.Item> items, String respuesta, java.util.Set<String> enCola) {
        long cambios = 0, max = 0;
        try {
            JSONArray arr = new JSONArray(respuesta);
            for (int k = 0; k < arr.length(); k++) {
                JSONObject d = arr.getJSONObject(k).optJSONObject("document");
                if (d == null) continue;
                String nombre = d.optString("name");
                String id = nombre.substring(nombre.lastIndexOf('/') + 1);
                JSONObject f = d.optJSONObject("fields");
                if (f == null) continue;
                JSONObject tsObj = f.optJSONObject("ts");
                if (tsObj != null && tsObj.has("timestampValue")) {   // solo la hora del servidor marca hasta dónde se ha leído
                    long ts = parseIso(tsObj.optString("timestampValue"));
                    if (ts > max) max = ts;
                }
                for (WidgetModel.Item it : items) {
                    if (!id.equals(it.i)) continue;
                    if (enCola != null && enCola.contains(id)) continue;   // tic tuyo aún sin subir: manda el tuyo
                    boolean cambio = false;
                    String v = sv(f, "v");
                    if (v != null && !v.equals(it.v)) { it.v = v; cambio = true; }
                    Long m = iv(f, "m");
                    if (m != null && (m == 1) != it.m) { it.m = m == 1; cambio = true; }
                    Long pe = iv(f, "pe");
                    if (pe != null && (pe == 1) != it.pe) { it.pe = pe == 1; cambio = true; }
                    String nt = sv(f, "nt");
                    if (nt != null && !nt.equals(it.nt)) { it.nt = nt; cambio = true; }
                    String mv = sv(f, "mv");
                    if (mv != null && !mv.isEmpty() && !mv.equals(it.f)) { it.f = mv; cambio = true; }
                    if (!it.v.isEmpty()) it.pe = false;
                    if (cambio) cambios++;
                }
            }
        } catch (Exception e) {
            // respuesta ilegible: no se cambia nada
        }
        return new long[]{cambios, max};
    }

    // ---------- red ----------

    private static String http(String url, String cuerpo) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(T_CONEXION);
            c.setReadTimeout(T_LECTURA);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(cuerpo.getBytes("UTF-8"));
            os.close();
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            InputStream is = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static String base(Context c) {
        SharedPreferences p = prefs(c);
        return "https://firestore.googleapis.com/v1/projects/" + p.getString("proyecto", "")
                + "/databases/(default)/documents";
    }

    private static String clave(Context c) {
        return "?key=" + prefs(c).getString("key", "");
    }

    /** GET de un documento de estado; null si no existe. */
    private static JSONObject leerEstado(Context c, String id) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(base(c) + "/grupos/" + prefs(c).getString("codigo", "")
                + "/estado/" + id + clave(c)).openConnection();
        try {
            h.setConnectTimeout(T_CONEXION);
            h.setReadTimeout(T_LECTURA);
            int code = h.getResponseCode();
            if (code == 404) return null;
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            InputStream is = h.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            return new JSONObject(bo.toString("UTF-8"));
        } finally {
            h.disconnect();
        }
    }

    /**
     * Sube los tics del widget en cola. Si en la nube ya hay un cambio MÁS RECIENTE de esa oficina
     * (hecho en otro teléfono después de tu toque), no se sube: manda el más reciente.
     * true si no queda nada pendiente.
     */
    static boolean vaciarCola(Context c) {
        if (!activo(c)) return true;
        JSONArray a;
        synchronized (LOCK_COLA) { a = cola(c); }
        java.util.Set<String> hechos = new java.util.HashSet<String>();
        String cod = prefs(c).getString("codigo", ""), proy = prefs(c).getString("proyecto", "");
        for (int k = 0; k < a.length(); k++) {
            JSONObject e = a.optJSONObject(k);
            if (e == null) continue;
            String id = e.optString("i"), v = e.optString("v");
            long t;
            try { t = Long.parseLong(e.optString("t", "0")); } catch (Exception x) { t = 0; }
            try {
                JSONObject doc = leerEstado(c, id);
                long remoto = 0;
                if (doc != null && doc.optJSONObject("fields") != null) {
                    Long tv = iv(doc.getJSONObject("fields"), "t_v");
                    if (tv != null) remoto = tv;
                }
                if (remoto > t) { hechos.add(id + "|" + e.optString("t")); continue; }   // en la nube hay algo más reciente: se descarta
                http(base(c) + ":commit" + clave(c), cuerpoVisita(proy, cod, id, v, t));
                hechos.add(id + "|" + e.optString("t"));
            } catch (Exception x) {
                // se queda en la cola
            }
        }
        synchronized (LOCK_COLA) {
            JSONArray actual = cola(c), resto = new JSONArray();
            for (int k = 0; k < actual.length(); k++) {
                JSONObject e = actual.optJSONObject(k);
                if (e != null && !hechos.contains(e.optString("i") + "|" + e.optString("t"))) resto.put(e);
            }
            guardarCola(c, resto);
            return resto.length() == 0;
        }
    }

    /** Marca/desmarca una visita en la nube. Si no hay red, queda en cola para el próximo toque en ⟳. */
    static void subirVisita(Context c, String id, String v, long t) {
        if (!activo(c) || id == null) return;
        encolar(c, id, v, t);
        vaciarCola(c);
    }

    /** Descarga lo nuevo y lo aplica al widget. Devuelve nº de oficinas cambiadas, o -1 si falló. */
    static int bajar(Context c) {
        if (!activo(c)) return -1;
        try {
            long desde = Math.max(0L, prefs(c).getLong("pull", 0L) - 5000L);   // hora del servidor: solo un pequeño margen
            String q = "{\"structuredQuery\":{\"from\":[{\"collectionId\":\"estado\"}],\"where\":{\"fieldFilter\":"
                    + "{\"field\":{\"fieldPath\":\"ts\"},\"op\":\"GREATER_THAN\",\"value\":{\"timestampValue\":\"" + iso(desde) + "\"}}}}}";
            String resp = http(base(c) + "/grupos/" + prefs(c).getString("codigo", "") + ":runQuery" + clave(c), q);
            long[] r = aplicarRespuesta(c, resp);
            return (int) r[0];
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Aplica al widget una respuesta con documentos de estado (de runQuery o de la escucha en tiempo real).
     * Devuelve {cambios, tsMax} y adelanta el punto de lectura.
     */
    static long[] aplicarRespuesta(Context c, String resp) {
        JSONArray q0;
        synchronized (LOCK_COLA) { q0 = cola(c); }
        java.util.Set<String> enCola = new java.util.HashSet<String>();
        for (int k = 0; k < q0.length(); k++) { JSONObject eo = q0.optJSONObject(k); if (eo != null) enCola.add(eo.optString("i")); }
        long[] r;
        synchronized (WidgetStore.LOCK) {   // sin pisar un tic hecho en el widget mientras tanto
            List<WidgetModel.Item> items = WidgetModel.parse(WidgetStore.leer(c));
            r = aplicar(items, resp, enCola);
            if (r[0] > 0) WidgetStore.guardar(c, WidgetModel.toJson(items));
        }
        if (r[1] > prefs(c).getLong("pull", 0L)) prefs(c).edit().putLong("pull", r[1]).apply();
        return r;
    }

    /** Pasa un documento de la librería de Firestore al mismo formato JSON que devuelve la API REST. */
    static JSONObject documentoJson(String proyecto, String codigo, String id, java.util.Map<String, Object> datos) throws Exception {
        JSONObject campos = new JSONObject();
        for (java.util.Map.Entry<String, Object> e : datos.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) campos.put(e.getKey(), campo("stringValue", (String) v));
            else if (v instanceof Long || v instanceof Integer) campos.put(e.getKey(), campo("integerValue", String.valueOf(((Number) v).longValue())));
            else if (v instanceof java.util.Date) campos.put(e.getKey(), campo("timestampValue", iso(((java.util.Date) v).getTime())));
        }
        JSONObject d = new JSONObject()
                .put("name", "projects/" + proyecto + "/databases/(default)/documents/grupos/" + codigo + "/estado/" + id)
                .put("fields", campos);
        return new JSONObject().put("document", d);
    }

    /** Botón ⟳: primero sube lo que quedó en cola (solo tics del widget) y luego baja lo nuevo. */
    static int refrescar(Context c) {
        vaciarCola(c);
        return bajar(c);
    }
}
