package com.example.chat.activities;

import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.chat.R;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

public class AjustesActivity extends AppCompatActivity {

    private static final int GPS_SETTINGS_REQUEST_CODE = 1001;
    static final String PREFS = "AjustesPrefs";

    private Switch switchGps, switchModoOscuro, switchMantenerPantalla, switchNotificaciones;
    private RadioGroup radioTamanoFuente;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ajustes);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        ImageButton btnBackAjustes = findViewById(R.id.btnBackAjustes);
        switchGps = findViewById(R.id.switchGps);
        switchModoOscuro = findViewById(R.id.switchModoOscuro);
        switchMantenerPantalla = findViewById(R.id.switchMantenerPantalla);
        switchNotificaciones = findViewById(R.id.switchNotificaciones);
        radioTamanoFuente = findViewById(R.id.radioTamanoFuente);

        btnBackAjustes.setOnClickListener(v -> finish());

        // --- Cargar valores guardados ---
        switchModoOscuro.setChecked(prefs.getBoolean("modo_oscuro", false));
        switchMantenerPantalla.setChecked(prefs.getBoolean("mantener_pantalla", false));
        switchNotificaciones.setChecked(prefs.getBoolean("notificaciones", true));

        int tamano = prefs.getInt("tamano_fuente", 15);
        if (tamano == 13) radioTamanoFuente.check(R.id.radioFuentePequena);
        else if (tamano == 18) radioTamanoFuente.check(R.id.radioFuenteGrande);
        else radioTamanoFuente.check(R.id.radioFuenteNormal);

        // --- Listeners ---

        switchModoOscuro.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean("modo_oscuro", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
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

        switchGps.setOnCheckedChangeListener((b, isChecked) -> {
            if (isChecked) verificarYActivarGPS();
            else Toast.makeText(this,
                    "Para desactivar el GPS ve a los ajustes del teléfono.",
                    Toast.LENGTH_LONG).show();
        });
    }

    private void verificarYActivarGPS() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, r -> {
            Toast.makeText(this, "El GPS ya está activado", Toast.LENGTH_SHORT).show();
            switchGps.setChecked(true);
        });

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException) e).startResolutionForResult(this, GPS_SETTINGS_REQUEST_CODE);
                } catch (IntentSender.SendIntentException ignored) {
                    switchGps.setChecked(false);
                }
            } else {
                switchGps.setChecked(false);
            }
        });
    }
}
