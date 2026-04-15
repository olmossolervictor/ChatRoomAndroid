package com.example.chat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.chat.R;

public class AjustesActivity extends BaseActivity {

    static final String PREFS = "AjustesPrefs";

    private Switch switchGps, switchModoOscuro, switchMantenerPantalla, switchNotificaciones, switchCamara;
    private RadioGroup radioTamanoFuente;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ajustes);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        ImageButton btnBackAjustes = findViewById(R.id.btnBackAjustes);
        switchGps = findViewById(R.id.switchGps);
        switchCamara = findViewById(R.id.switchCamara); // NUEVO
        switchModoOscuro = findViewById(R.id.switchModoOscuro);
        switchMantenerPantalla = findViewById(R.id.switchMantenerPantalla);
        switchNotificaciones = findViewById(R.id.switchNotificaciones);
        radioTamanoFuente = findViewById(R.id.radioTamanoFuente);

        btnBackAjustes.setOnClickListener(v -> finish());

        // --- Cargar valores guardados (Diseño) ---
        switchModoOscuro.setChecked(prefs.getBoolean("modo_oscuro", false));
        switchMantenerPantalla.setChecked(prefs.getBoolean("mantener_pantalla", false));
        switchNotificaciones.setChecked(prefs.getBoolean("notificaciones", true));

        int tamano = prefs.getInt("tamano_fuente", 15);
        if (tamano == 13) radioTamanoFuente.check(R.id.radioFuentePequena);
        else if (tamano == 18) radioTamanoFuente.check(R.id.radioFuenteGrande);
        else radioTamanoFuente.check(R.id.radioFuenteNormal);

        // --- Listeners de Diseño ---
        switchModoOscuro.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean("modo_oscuro", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        switchMantenerPantalla.setOnCheckedChangeListener((b, isChecked) ->
                prefs.edit().putBoolean("mantener_pantalla", isChecked).apply());

        switchNotificaciones.setOnCheckedChangeListener((b, isChecked) ->
                prefs.edit().putBoolean("notificaciones", isChecked).apply());

        radioTamanoFuente.setOnCheckedChangeListener((group, checkedId) -> {
            int size = 15;
            if (checkedId == R.id.radioFuentePequena) size = 13;
            else if (checkedId == R.id.radioFuenteGrande) size = 18;
            prefs.edit().putInt("tamano_fuente", size).apply();
        });
    }

    // Usamos onResume para que, si el usuario vuelve de los ajustes de Android, los switches se actualicen solos
    @Override
    protected void onResume() {
        super.onResume();
        actualizarSwitchesPermisos();
    }

    private void actualizarSwitchesPermisos() {
        // Leemos si el sistema Android nos ha dado permiso DE VERDAD
        boolean tieneGps = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean tieneCamara = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        // Quitamos los listeners un momento para que no hagan doble ejecución
        switchGps.setOnCheckedChangeListener(null);
        switchCamara.setOnCheckedChangeListener(null);

        switchGps.setChecked(tieneGps);
        switchCamara.setChecked(tieneCamara);

        // Volvemos a poner los listeners
        switchGps.setOnCheckedChangeListener((b, isChecked) -> manejarPermiso(Manifest.permission.ACCESS_FINE_LOCATION, isChecked));
        switchCamara.setOnCheckedChangeListener((b, isChecked) -> manejarPermiso(Manifest.permission.CAMERA, isChecked));
    }

    private void manejarPermiso(String permiso, boolean isChecked) {
        if (isChecked) {
            // Si lo activa, le pedimos permiso a Android
            ActivityCompat.requestPermissions(this, new String[]{permiso}, 100);
        } else {
            // Si lo desactiva, le mandamos a la pantalla de la app en Android para que lo quite de verdad
            Toast.makeText(this, "Desactívalo manualmente desde los ajustes de la aplicación", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}