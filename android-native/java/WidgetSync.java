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

    static void configurar(Context c, String codigo, String apiKey, String proyecto, long ultimoPullApp) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (codigo == null || codigo.isEmpty()) {
            e.clear();
        } else {
            e.putString("codigo", codigo).putString("key", apiKey).putString("proyecto", proyecto);
            if (!codigo.equals(prefs(c).getString("codigo", ""))) e.remove("pull").remove("cola");
            // lo que la app ya ha leído no hace falta volver a descargarlo
            if (ultimoPullApp > prefs(c).getLong("pull", 0L)) e.putLong("pull", ultimoPullApp);
        }
        e.apply();
    }

    static boolean activo(Context c) {
        return !prefs(c).getString("codigo", "").isEmpty();
    }

    // ---------- cola de subidas pendientes ----------

    private static JSONArray cola(Context c) {
        try { return new JSONArray(prefs(c).getString("cola", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private static void guardarCola(Context c, JSONArray a) {
        prefs(c).edit().putString("cola", a.toString()).apply();
    }

    static void encolar(Context c, String id) {
        JSONArray a = cola(c);
        for (int k = 0; k < a.length(); k++) if (id.equals(a.optString(k))) return;
        a.put(id);
        guardarCola(c, a);
    }

    // ---------- lógica pura (probada en el PC) ----------

    private static JSONObject campo(String tipo, String valor) throws Exception {
        return new JSONObject().put(tipo, valor);
    }

    /** Cuerpo del :commit que sube v (y quita pendiente si hay visita) de una oficina. */
    static String cuerpoVisita(String proyecto, String codigo, String id, String v, long ts) throws Exception {
        JSONObject campos = new JSONObject();
        JSONArray mask = new JSONArray();
        campos.put("v", campo("stringValue", v)); mask.put("v");
        if (!v.isEmpty()) { campos.put("pe", campo("integerValue", "0")); mask.put("pe"); }
        campos.put("ts", campo("integerValue", String.valueOf(ts))); mask.put("ts");
        JSONObject up = new JSONObject()
                .put("name", "projects/" + proyecto + "/databases/(default)/documents/grupos/" + codigo + "/estado/" + id)
                .put("fields", campos);
        JSONObject w = new JSONObject().put("update", up).put("updateMask", new JSONObject().put("fieldPaths", mask));
        return new JSONObject().put("writes", new JSONArray().put(w)).toString();
    }

    private static String sv(JSONObject f, String k) {
        JSONObject o = f.optJSONObject(k);
        return o == null ? null : (o.has("stringValue") ? o.optString("stringValue") : null);
    }

    private static Long iv(JSONObject f, String k) {
        JSONObject o = f.optJSONObject(k);
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
                Long ts = iv(f, "ts");
                if (ts != null && ts > max) max = ts;
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

    /** Sube las visitas en cola con su valor actual en el widget. true si no queda nada pendiente. */
    static boolean vaciarCola(Context c) {
        if (!activo(c)) return true;
        JSONArray a = cola(c), resto = new JSONArray();
        List<WidgetModel.Item> items = WidgetModel.parse(WidgetStore.leer(c));
        String cod = prefs(c).getString("codigo", ""), proy = prefs(c).getString("proyecto", "");
        for (int k = 0; k < a.length(); k++) {
            String id = a.optString(k);
            String v = null;
            for (WidgetModel.Item it : items) if (id.equals(it.i)) v = it.v;
            if (v == null) continue;
            try {
                http(base(c) + ":commit" + clave(c), cuerpoVisita(proy, cod, id, v, System.currentTimeMillis()));
            } catch (Exception e) {
                resto.put(id);
            }
        }
        guardarCola(c, resto);
        return resto.length() == 0;
    }

    /** Marca/desmarca una visita en la nube. Si no hay red, queda en cola para el próximo toque en ⟳. */
    static void subirVisita(Context c, String id) {
        if (!activo(c) || id == null) return;
        encolar(c, id);
        vaciarCola(c);
    }

    /** Descarga lo nuevo y lo aplica al widget. Devuelve nº de oficinas cambiadas, o -1 si falló. */
    static int bajar(Context c) {
        if (!activo(c)) return -1;
        try {
            long desde = Math.max(0L, prefs(c).getLong("pull", 0L) - 120000L);   // margen por relojes
            String q = "{\"structuredQuery\":{\"from\":[{\"collectionId\":\"estado\"}],\"where\":{\"fieldFilter\":"
                    + "{\"field\":{\"fieldPath\":\"ts\"},\"op\":\"GREATER_THAN\",\"value\":{\"integerValue\":\"" + desde + "\"}}}}}";
            String resp = http(base(c) + "/grupos/" + prefs(c).getString("codigo", "") + ":runQuery" + clave(c), q);
            List<WidgetModel.Item> items = WidgetModel.parse(WidgetStore.leer(c));
            JSONArray q0 = cola(c);
            java.util.Set<String> enCola = new java.util.HashSet<String>();
            for (int k = 0; k < q0.length(); k++) enCola.add(q0.optString(k));
            long[] r = aplicar(items, resp, enCola);
            if (r[0] > 0) WidgetStore.guardar(c, WidgetModel.toJson(items));
            if (r[1] > prefs(c).getLong("pull", 0L)) prefs(c).edit().putLong("pull", r[1]).apply();
            return (int) r[0];
        } catch (Exception e) {
            return -1;
        }
    }

    /** Botón ⟳: primero sube lo que quedó en cola (solo tics del widget) y luego baja lo nuevo. */
    static int refrescar(Context c) {
        vaciarCola(c);
        return bajar(c);
    }
}
