package com.example.chat.activities;

import android.content.IntentSender;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
    private Switch switchGps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ajustes);

        ImageButton btnBackAjustes = findViewById(R.id.btnBackAjustes);
        switchGps = findViewById(R.id.switchGps);

        // Volver atrás
        btnBackAjustes.setOnClickListener(v -> finish());

        // Lógica del interruptor
        switchGps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                verificarYActivarGPS();
            } else {
                Toast.makeText(this, "Aviso: Android solo permite desactivar el GPS manualmente desde los Ajustes de tu teléfono.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void verificarYActivarGPS() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, r -> {
            Toast.makeText(this, "El GPS ya está activado", Toast.LENGTH_SHORT).show();
            switchGps.setChecked(true); // Mantenemos el switch activo
        });

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException) e).startResolutionForResult(this, GPS_SETTINGS_REQUEST_CODE);
                } catch (IntentSender.SendIntentException ignored) {
                    switchGps.setChecked(false); // Si falla, apagamos el switch
                }
            } else {
                switchGps.setChecked(false);
            }
        });
    }
}