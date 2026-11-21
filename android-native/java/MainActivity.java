package com.erik.rutas;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // El plugin propio del widget debe registrarse antes de super.onCreate
        registerPlugin(RutasWidgetPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
