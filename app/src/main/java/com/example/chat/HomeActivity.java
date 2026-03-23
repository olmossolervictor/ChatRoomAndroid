package com.example.chat;

import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

public class HomeActivity extends AppCompatActivity {

    private TextView textWelcome;
    private Button btnScanQR, btnGPS, btnEditProfile, btnLogout;
    
    private static final int SCAN_QR_REQUEST_CODE = 2000;
    private static final int GPS_SETTINGS_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        String nombre = pref.getString("nombre", "Usuario");

        textWelcome = findViewById(R.id.textWelcome);
        btnScanQR = findViewById(R.id.btnScanQRHome);
        btnGPS = findViewById(R.id.btnGPSHome);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnLogout = findViewById(R.id.btnLogout);

        textWelcome.setText("Bienvenido, " + nombre);


        btnScanQR.setOnClickListener(v -> {
            // COMENTAMOS EL SALTO AL ESCÁNER:
            // Intent intent = new Intent(HomeActivity.this, ScannerActivity.class);
            // startActivityForResult(intent, SCAN_QR_REQUEST_CODE);

            // FORZAMOS EL SALTO DIRECTO AL CHAT (MainActivity):
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);

            // Le pasamos "GENERAL" como si el QR hubiera devuelto eso
            intent.putExtra("ID_SALA_QR", "GENERAL");

            startActivity(intent);
        });

        btnGPS.setOnClickListener(v -> verificarYActivarGPS());

        btnEditProfile.setOnClickListener(v -> {
            // Reutilizamos RegisterActivity para edición (pasando un flag)
            Intent intent = new Intent(HomeActivity.this, RegisterActivity.class);
            intent.putExtra("MODO_EDICION", true);
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> {
            pref.edit().clear().apply();
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void verificarYActivarGPS() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, locationSettingsResponse -> {
            Toast.makeText(HomeActivity.this, "El GPS ya está activado", Toast.LENGTH_SHORT).show();
        });

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(HomeActivity.this, GPS_SETTINGS_REQUEST_CODE);
                } catch (IntentSender.SendIntentException sendEx) {
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCAN_QR_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String qrValue = data.getStringExtra("QR_CONTENT");
            if (qrValue != null) {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                intent.putExtra("ID_SALA_QR", qrValue);
                startActivity(intent);
            }
        }
    }
}
