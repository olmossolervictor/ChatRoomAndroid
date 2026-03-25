package com.example.chat.activities;

import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.chat.R;
import com.example.chat.adapters.SalaAdapter;
import com.example.chat.models.Sala;
import com.example.chat.network.RetrofitClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private static final int SCAN_QR_REQUEST_CODE = 2000;
    private static final int GPS_SETTINGS_REQUEST_CODE = 1001;

    private DrawerLayout drawerLayout;
    private ListView listSalas;
    private TextView textEmptySalas;
    private Button btnScanQR, btnGPS;
    private ImageButton btnMenuDrawer;

    // Drawer views
    private ImageView imgDrawerFoto;
    private TextView textDrawerNombre;
    private TextView drawerEditarPerfil, drawerAjustes, drawerGestionUsuarios;
    private TextView drawerTerminos, drawerCerrarSesion;

    private SalaAdapter salaAdapter;
    private List<Sala> listaMisSalas = new ArrayList<>();
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);

        drawerLayout = findViewById(R.id.drawerLayout);
        listSalas = findViewById(R.id.listSalas);
        textEmptySalas = findViewById(R.id.textEmptySalas);
        btnScanQR = findViewById(R.id.btnScanQRHome);
        btnGPS = findViewById(R.id.btnGPSHome);
        btnMenuDrawer = findViewById(R.id.btnMenuDrawer);

        imgDrawerFoto = findViewById(R.id.imgDrawerFoto);
        textDrawerNombre = findViewById(R.id.textDrawerNombre);
        drawerEditarPerfil = findViewById(R.id.drawerEditarPerfil);
        drawerAjustes = findViewById(R.id.drawerAjustes);
        drawerGestionUsuarios = findViewById(R.id.drawerGestionUsuarios);
        drawerTerminos = findViewById(R.id.drawerTerminos);
        drawerCerrarSesion = findViewById(R.id.drawerCerrarSesion);

        salaAdapter = new SalaAdapter(this, listaMisSalas);
        listSalas.setAdapter(salaAdapter);
        listSalas.setEmptyView(textEmptySalas);

        listSalas.setOnItemClickListener((parent, view, position, id) -> {
            Sala sala = listaMisSalas.get(position);
            abrirSala(sala.getIdSala() != null ? sala.getIdSala() : sala.getNombre());
        });

        btnMenuDrawer.setOnClickListener(v -> drawerLayout.openDrawer(
                androidx.core.view.GravityCompat.START));

        btnScanQR.setOnClickListener(v ->
                startActivityForResult(new Intent(this, ScannerActivity.class), SCAN_QR_REQUEST_CODE));

        btnGPS.setOnClickListener(v -> verificarYActivarGPS());

        drawerEditarPerfil.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra("MODO_EDICION", true);
            startActivity(intent);
        });

        drawerAjustes.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Toast.makeText(this, "Ajustes — próximamente", Toast.LENGTH_SHORT).show();
        });

        drawerGestionUsuarios.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            startActivity(new Intent(this, UserManagementActivity.class));
        });

        drawerTerminos.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Toast.makeText(this, "Términos y condiciones — próximamente", Toast.LENGTH_SHORT).show();
        });

        drawerCerrarSesion.setOnClickListener(v -> {
            pref.edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        cargarPerfilDrawer();
        comprobarRolUsuario();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMisSalas();
    }

    private void cargarPerfilDrawer() {
        RetrofitClient.getChatApiServices().getUsuario(currentUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                String nombre = json.optString("nombre", "");
                                String apellidos = json.optString("apellidos", "");
                                textDrawerNombre.setText((nombre + " " + apellidos).trim());

                                String foto = json.optString("foto", "");
                                if (!foto.isEmpty()) {
                                    byte[] decoded = Base64.decode(foto, Base64.DEFAULT);
                                    imgDrawerFoto.setImageBitmap(
                                            BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private void cargarMisSalas() {
        RetrofitClient.getChatApiServices().getMisSalas(currentUserId)
                .enqueue(new Callback<List<Sala>>() {
                    @Override
                    public void onResponse(Call<List<Sala>> call, Response<List<Sala>> response) {
                        listaMisSalas.clear();
                        if (response.isSuccessful() && response.body() != null) {
                            listaMisSalas.addAll(response.body());
                        } else {
                            try {
                                String err = response.errorBody() != null ? response.errorBody().string() : "";
                                android.util.Log.e("SALAS_DEBUG", "HTTP " + response.code() + ": " + err);
                            } catch (Exception ignored) {}
                        }
                        salaAdapter.notifyDataSetChanged();
                    }
                    @Override
                    public void onFailure(Call<List<Sala>> call, Throwable t) {
                        android.util.Log.e("SALAS_DEBUG", "Fallo de red: " + t.getMessage());
                    }
                });
    }

    private void abrirSala(String idSala) {
        RetrofitClient.getChatApiServices().getSalaInfo(idSala)
                .enqueue(new Callback<Sala>() {
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
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("ID_SALA_QR", idSala);
                intent.putExtra("SALA_LATITUD", latitud);
                intent.putExtra("SALA_LONGITUD", longitud);
                intent.putExtra("SALA_RADIO", radio);
                startActivity(intent);
            }
        }
    }

    private void comprobarRolUsuario() {
        RetrofitClient.getChatApiServices().getRolUsuario(currentUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                int idRol = json.optInt("id_rol", 3);
                                String nombreRol = json.optString("rol", "usuario").toLowerCase();
                                getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit()
                                        .putString("rol", nombreRol).apply();
                                if (idRol == 1 || "owner".equals(nombreRol)) {
                                    drawerGestionUsuarios.setVisibility(View.VISIBLE);
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private void verificarYActivarGPS() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        task.addOnSuccessListener(this, r ->
                Toast.makeText(this, "El GPS ya está activado", Toast.LENGTH_SHORT).show());
        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException) e).startResolutionForResult(this, GPS_SETTINGS_REQUEST_CODE);
                } catch (IntentSender.SendIntentException ignored) {}
            }
        });
    }
}
