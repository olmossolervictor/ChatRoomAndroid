package com.example.chat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.RadioGroup;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.chat.R;
import com.example.chat.utils.AlertHelper;
import com.example.chat.utils.AlertHelper.AlertType;

public class AjustesActivity extends BaseActivity {

    private static final String PREFS = "AjustesPrefs";

    private Switch switchGps;
    private Switch switchCamara;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ajustes);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        androidx.appcompat.widget.Toolbar toolbarAjustes = findViewById(R.id.toolbarAjustes);
        toolbarAjustes.setNavigationOnClickListener(v -> finish());

        switchGps = findViewById(R.id.switchGps);
        switchCamara = findViewById(R.id.switchCamara);

        RadioGroup radioTema = findViewById(R.id.radioTema);
        RadioGroup radioTamanoFuente = findViewById(R.id.radioTamanoFuente);

        int modoNoche = prefs.getInt("modo_noche", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (modoNoche == AppCompatDelegate.MODE_NIGHT_YES) radioTema.check(R.id.radioTemaOscuro);
        else if (modoNoche == AppCompatDelegate.MODE_NIGHT_NO) radioTema.check(R.id.radioTemaClaro);
        else radioTema.check(R.id.radioTemaSistema);

        int tamano = prefs.getInt("tamano_fuente", 15);
        if (tamano == 13) radioTamanoFuente.check(R.id.radioFuentePequena);
        else if (tamano == 18) radioTamanoFuente.check(R.id.radioFuenteGrande);
        else radioTamanoFuente.check(R.id.radioFuenteNormal);

        radioTema.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.radioTemaOscuro) mode = AppCompatDelegate.MODE_NIGHT_YES;
            else if (checkedId == R.id.radioTemaClaro) mode = AppCompatDelegate.MODE_NIGHT_NO;
            else mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

            prefs.edit().putInt("modo_noche", mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        });

        radioTamanoFuente.setOnCheckedChangeListener((group, checkedId) -> {
            int size = 15;
            if (checkedId == R.id.radioFuentePequena) size = 13;
            else if (checkedId == R.id.radioFuenteGrande) size = 18;
            prefs.edit().putInt("tamano_fuente", size).apply();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualizarSwitchesPermisos();
    }

    private void actualizarSwitchesPermisos() {
        boolean tieneGps = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean tieneCamara = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        switchGps.setOnCheckedChangeListener(null);
        switchCamara.setOnCheckedChangeListener(null);

        switchGps.setChecked(tieneGps);
        switchCamara.setChecked(tieneCamara);

        switchGps.setOnCheckedChangeListener((b, isChecked) -> manejarPermiso(Manifest.permission.ACCESS_FINE_LOCATION, isChecked));
        switchCamara.setOnCheckedChangeListener((b, isChecked) -> manejarPermiso(Manifest.permission.CAMERA, isChecked));
    }

    private void manejarPermiso(String permiso, boolean isChecked) {
        if (isChecked) {
            ActivityCompat.requestPermissions(this, new String[]{permiso}, 100);
        } else {
            AlertHelper.showActionAlert(switchGps, "Para mayor seguridad, desactívalo desde los ajustes del sistema", AlertType.INFO);
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}