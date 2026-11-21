package com.erik.rutas;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Datos y reglas del widget. Todo lo que no es Android está aquí para poder probarlo.
 * Formato JSON que envía la app: [{i,f,n,p,d,pv,v,m}]
 *   i=id, f=fecha AAAA-MM-DD, n=nombre, p=población, d=dirección,
 *   pv=provincia, v=hora de visita "HH:mm" o "" si no visitada, m=1 si es "por la mañana".
 */
final class WidgetModel {

    static final class Item {
        String i = "";
        String f = "";
        String n = "";
        String p = "";
        String d = "";
        String pv = "";
        String v = "";
        boolean m = false;

        boolean visitada() {
            return !v.isEmpty();
        }
    }

    private WidgetModel() {}

    // ---------- JSON ----------

    static List<Item> parse(String json) {
        List<Item> r = new ArrayList<Item>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                Item it = new Item();
                it.i = o.optString("i");
                it.f = o.optString("f");
                it.n = o.optString("n");
                it.p = o.optString("p");
                it.d = o.optString("d");
                it.pv = o.optString("pv");
                String v = o.optString("v");
                // formato antiguo: 0 / 1
                if ("0".equals(v)) v = "";
                else if ("1".equals(v)) v = "✓";
                it.v = v;
                String m = o.optString("m");
                it.m = "1".equals(m) || "true".equals(m);
                r.add(it);
            }
        } catch (Exception e) {
            // JSON ilegible: lista vacía
        }
        return r;
    }

    static String toJson(List<Item> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("i", it.i);
                o.put("f", it.f);
                o.put("n", it.n);
                o.put("p", it.p);
                o.put("d", it.d);
                o.put("pv", it.pv);
                o.put("v", it.v);
                o.put("m", it.m ? 1 : 0);
                arr.put(o);
            }
        } catch (Exception e) {
            // no debería ocurrir
        }
        return arr.toString();
    }

    // ---------- Fechas ----------

    static String iso(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static Calendar desdeIso(String iso) {
        try {
            String[] t = iso.split("-");
            return new GregorianCalendar(Integer.parseInt(t[0]), Integer.parseInt(t[1]) - 1, Integer.parseInt(t[2]));
        } catch (Exception e) {
            return null;
        }
    }

    /** Día automático: hoy de lunes a viernes; el lunes siguiente en sábado o domingo. */
    static Calendar diaAuto(Calendar ahora) {
        Calendar c = (Calendar) ahora.clone();
        int dow = c.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SATURDAY) {
            c.add(Calendar.DAY_OF_MONTH, 2);
        } else if (dow == Calendar.SUNDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return c;
    }

    /** Día mostrado: el elegido con las flechas (solo vale el mismo día en que se eligió) o el automático. */
    static String diaMostrado(String sel, String selEn, Calendar ahora) {
        if (sel != null && !sel.isEmpty() && iso(ahora).equals(selEn)) return sel;
        return iso(diaAuto(ahora));
    }

    static boolean esManual(String sel, String selEn, Calendar ahora) {
        return sel != null && !sel.isEmpty() && iso(ahora).equals(selEn);
    }

    static TreeSet<String> dias(List<Item> items) {
        TreeSet<String> s = new TreeSet<String>();
        for (Item it : items) {
            if (!it.f.isEmpty()) s.add(it.f);
        }
        return s;
    }

    /** Día anterior (dir < 0) o siguiente (dir > 0) que tenga oficinas; null si no hay. */
    static String vecino(List<Item> items, String actual, int dir) {
        TreeSet<String> ds = dias(items);
        return dir < 0 ? ds.lower(actual) : ds.higher(actual);
    }

    /** "Hoy · 21 nov", "Mañana · 22 nov", "Próximo lunes · 23 nov", "Martes · 24 nov" */
    static String etiquetaDia(String diaIso, Calendar ahora, boolean auto) {
        Calendar d = desdeIso(diaIso);
        if (d == null) return diaIso;
        Locale es = new Locale("es", "ES");
        String fecha = new SimpleDateFormat("d MMM", es).format(d.getTime());
        String hoy = iso(ahora);
        Calendar manana = (Calendar) ahora.clone();
        manana.add(Calendar.DAY_OF_MONTH, 1);
        Calendar ayer = (Calendar) ahora.clone();
        ayer.add(Calendar.DAY_OF_MONTH, -1);

        String nombre;
        if (diaIso.equals(hoy)) nombre = "Hoy";
        else if (diaIso.equals(iso(manana))) nombre = "Mañana";
        else if (diaIso.equals(iso(ayer))) nombre = "Ayer";
        else if (auto) nombre = "Próximo lunes";
        else {
            String w = new SimpleDateFormat("EEEE", es).format(d.getTime());
            nombre = w.isEmpty() ? w : w.substring(0, 1).toUpperCase(es) + w.substring(1);
        }
        return nombre + " · " + fecha;
    }

    // ---------- Consultas ----------

    static List<Item> delDia(List<Item> items, String dia) {
        List<Item> r = new ArrayList<Item>();
        for (Item it : items) {
            if (dia.equals(it.f)) r.add(it);
        }
        return r;
    }

    static Set<String> provincias(List<Item> items) {
        Set<String> s = new LinkedHashSet<String>();
        for (Item it : items) {
            if (!it.pv.isEmpty()) s.add(it.pv);
        }
        return s;
    }

    static int hechas(List<Item> items) {
        int n = 0;
        for (Item it : items) if (it.visitada()) n++;
        return n;
    }

    static int pendientesManana(List<Item> items) {
        int n = 0;
        for (Item it : items) if (it.m && !it.visitada()) n++;
        return n;
    }

    /** Marca como visitada (con la hora) o deshace la marca. Devuelve false si no existe el id. */
    static boolean alternarVisitada(List<Item> items, String id, String hora) {
        if (id == null || id.isEmpty()) return false;
        for (Item it : items) {
            if (id.equals(it.i)) {
                it.v = it.visitada() ? "" : hora;
                return true;
            }
        }
        return false;
    }

    // ---------- Semana ----------

    /** Lunes (AAAA-MM-DD) de la semana a la que pertenece un día; "" si la fecha no es válida. */
    static String lunesDe(String diaIso) {
        Calendar c = desdeIso(diaIso);
        if (c == null) return "";
        int dow = c.get(Calendar.DAY_OF_WEEK);            // domingo=1 ... sábado=7
        int atras = dow == Calendar.SUNDAY ? 6 : dow - Calendar.MONDAY;
        c.add(Calendar.DAY_OF_MONTH, -atras);
        return iso(c);
    }

    /** {hechas, total, pendientes "por la mañana"} de la semana (lunes a domingo) del día dado. */
    static int[] resumenSemana(List<Item> items, String diaIso) {
        String lunes = lunesDe(diaIso);
        if (lunes.isEmpty()) return new int[]{0, 0, 0};
        Calendar dom = desdeIso(lunes);
        dom.add(Calendar.DAY_OF_MONTH, 6);
        String domingo = iso(dom);
        int h = 0, t = 0, pm = 0;
        for (Item it : items) {
            if (it.f.isEmpty() || it.f.compareTo(lunes) < 0 || it.f.compareTo(domingo) > 0) continue;
            t++;
            if (it.visitada()) h++;
            else if (it.m) pm++;
        }
        return new int[]{h, t, pm};
    }

    /** "16 nov – 22 nov" */
    static String rangoSemana(String diaIso) {
        String lunes = lunesDe(diaIso);
        if (lunes.isEmpty()) return "";
        Calendar a = desdeIso(lunes);
        Calendar b = desdeIso(lunes);
        b.add(Calendar.DAY_OF_MONTH, 6);
        SimpleDateFormat f = new SimpleDateFormat("d MMM", new Locale("es", "ES"));
        return f.format(a.getTime()) + " – " + f.format(b.getTime());
    }

    // ---------- Filas de la lista del widget ----------

    static final class Fila {
        boolean cabecera = false;
        // cabecera de día
        String titulo = "";
        String provincias = "";
        int hechas = 0;
        int total = 0;
        // oficina
        Item item = null;
    }

    static String unir(Set<String> s, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String x : s) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(x);
        }
        return sb.toString();
    }

    /**
     * Desde el día de anclaje hacia delante: por cada día con oficinas PENDIENTES, una cabecera
     * y esas oficinas. Las visitadas no aparecen; un día completado tampoco. Así, cuanto más
     * alto es el widget, más días se ven.
     */
    /** Solo las oficinas pendientes del día indicado (sin cabeceras: el día va arriba). */
    static List<Fila> construirFilas(List<Item> items, String dia, Calendar ahora) {
        List<Fila> filas = new ArrayList<Fila>();
        for (Item it : delDia(items, dia)) {
            if (it.visitada()) continue;
            Fila f = new Fila();
            f.item = it;
            filas.add(f);
        }
        // Las de la mañana primero (sort estable: se respeta el orden del Excel dentro de cada grupo)
        java.util.Collections.sort(filas, new java.util.Comparator<Fila>() {
            @Override
            public int compare(Fila a, Fila b) {
                return (b.item.m ? 1 : 0) - (a.item.m ? 1 : 0);
            }
        });
        return filas;
    }

    /**
     * Factor de tamaño para que quepan cabecera + barra + 2 oficinas en la altura (dp) del widget.
     * k = 1 corresponde a unos 226 dp de alto.
     */
    static float escala(float alturaDp) {
        if (alturaDp <= 0) return 1f;
        float k = (alturaDp - 39f) / 187f;
        return Math.max(0.65f, Math.min(1.6f, k));
    }

    private static final int[] HUES = {215, 160, 275, 335, 190};

    /** Mismo tono que la app: depende del número de semana (desde el lunes). */
    static int hueSemana(String diaIso) {
        String lunes = lunesDe(diaIso);
        Calendar c = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        c.clear();
        int[] d = ymd(lunes);
        c.set(d[0], d[1] - 1, d[2]);
        long semanas = Math.floorDiv(c.getTimeInMillis(), 604800000L);
        return HUES[(int) Math.floorMod(semanas, (long) HUES.length)];
    }

    private static int[] ymd(String iso) {
        String[] p = iso.split("-");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
    }

    /** HSL (h 0-360, s y l 0-100) → color ARGB. */
    static int hsl(int h, int s, int l) {
        float S = s / 100f, L = l / 100f;
        float c = (1 - Math.abs(2 * L - 1)) * S;
        float hh = (h % 360) / 60f;
        float x = c * (1 - Math.abs(hh % 2 - 1));
        float r = 0, g = 0, b = 0;
        if (hh < 1) { r = c; g = x; }
        else if (hh < 2) { r = x; g = c; }
        else if (hh < 3) { g = c; b = x; }
        else if (hh < 4) { g = x; b = c; }
        else if (hh < 5) { r = x; b = c; }
        else { r = c; b = x; }
        float m = L - c / 2;
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** ¿Hay oficinas (aunque sean todas visitadas) desde ese día en adelante? */
    static boolean hayDesde(List<Item> items, String desde) {
        for (Item it : items) {
            if (!it.f.isEmpty() && it.f.compareTo(desde) >= 0) return true;
        }
        return false;
    }
}
