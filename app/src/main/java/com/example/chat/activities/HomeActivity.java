package com.example.chat.activities;

import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chat.R;
import com.example.chat.adapters.SalaAdapter;
import com.example.chat.models.Sala;
import com.example.chat.network.ChatApiServices;
import com.example.chat.network.RetrofitClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private static final int SCAN_QR_REQUEST_CODE = 2000;
    private static final int GPS_SETTINGS_REQUEST_CODE = 1001;

    private TextView textWelcome;
    private ListView listSalas;
    private TextView textEmptySalas;
    private Button btnScanQR, btnGPS, btnEditProfile, btnLogout;

    private SalaAdapter salaAdapter;
    private List<Sala> listaMisSalas = new ArrayList<>();
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);
        String nombre = pref.getString("nombre", "Usuario");

        textWelcome = findViewById(R.id.textWelcome);
        listSalas = findViewById(R.id.listSalas);
        textEmptySalas = findViewById(R.id.textEmptySalas);
        btnScanQR = findViewById(R.id.btnScanQRHome);
        btnGPS = findViewById(R.id.btnGPSHome);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnLogout = findViewById(R.id.btnLogout);

        textWelcome.setText("Bienvenido, " + nombre);

        salaAdapter = new SalaAdapter(this, listaMisSalas);
        listSalas.setAdapter(salaAdapter);
        listSalas.setEmptyView(textEmptySalas);

        listSalas.setOnItemClickListener((parent, view, position, id) -> {
            Sala sala = listaMisSalas.get(position);
            abrirSala(sala.getIdSala() != null ? sala.getIdSala() : sala.getNombre());
        });

        btnScanQR.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ScannerActivity.class);
            startActivityForResult(intent, SCAN_QR_REQUEST_CODE);
        });

        btnGPS.setOnClickListener(v -> verificarYActivarGPS());

        btnEditProfile.setOnClickListener(v -> {
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

    @Override
    protected void onResume() {
        super.onResume();
        cargarMisSalas();
    }

    private void cargarMisSalas() {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        api.getMisSalas(currentUserId).enqueue(new Callback<List<Sala>>() {
            @Override
            public void onResponse(Call<List<Sala>> call, Response<List<Sala>> response) {
                listaMisSalas.clear();
                if (response.isSuccessful() && response.body() != null) {
                    listaMisSalas.addAll(response.body());
                }
                salaAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(Call<List<Sala>> call, Throwable t) {
                // Silencioso: la lista quedará vacía
            }
        });
    }

    private void abrirSala(String idSala) {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        api.getSalaInfo(idSala).enqueue(new Callback<Sala>() {
            @Override
            public void onResponse(Call<Sala> call, Response<Sala> response) {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                intent.putExtra("ID_SALA_QR", idSala);
                if (response.isSuccessful() && response.body() != null) {
                    Sala sala = response.body();
                    intent.putExtra("SALA_LATITUD", sala.getLatitud());
                    intent.putExtra("SALA_LONGITUD", sala.getLongitud());
                    intent.putExtra("SALA_RADIO", sala.getRadioMetros());
                }
                startActivity(intent);
            }

            @Override
            public void onFailure(Call<Sala> call, Throwable t) {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                intent.putExtra("ID_SALA_QR", idSala);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCAN_QR_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String idSala = data.getStringExtra("ID_SALA");
            double latitud = data.getDoubleExtra("SALA_LATITUD", 0);
            double longitud = data.getDoubleExtra("SALA_LONGITUD", 0);
            double radio = data.getDoubleExtra("SALA_RADIO", 0);

            if (idSala != null) {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                intent.putExtra("ID_SALA_QR", idSala);
                intent.putExtra("SALA_LATITUD", latitud);
                intent.putExtra("SALA_LONGITUD", longitud);
                intent.putExtra("SALA_RADIO", radio);
                startActivity(intent);
            }
        }
    }

    private void verificarYActivarGPS() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, r ->
                Toast.makeText(HomeActivity.this, "El GPS ya está activado", Toast.LENGTH_SHORT).show());

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException) e).startResolutionForResult(HomeActivity.this, GPS_SETTINGS_REQUEST_CODE);
                } catch (IntentSender.SendIntentException ignored) {}
            }
        });
    }
}
