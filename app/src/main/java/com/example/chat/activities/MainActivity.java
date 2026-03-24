package com.example.chat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chat.R;
import com.example.chat.adapters.MensajeAdapter;
import com.example.chat.models.Mensaje;
import com.example.chat.network.ChatApiServices;
import com.example.chat.network.RetrofitClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private EditText editMessage;
    private Button btnSend;
    private ListView listMessages;

    private MensajeAdapter adapter;
    private List<Mensaje> listaMensajes = new ArrayList<>();

    private Handler handler = new Handler();
    private Runnable refreshRunnable;

    private FusedLocationProviderClient fusedLocationClient;

    private int currentUserId;
    private String currentSalaId;

    // Datos de geovalla de la sala (0 = sin restricción)
    private double salaLatitud = 0;
    private double salaLongitud = 0;
    private double salaRadioMetros = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);

        if (currentUserId == -1) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentSalaId = getIntent().getStringExtra("ID_SALA_QR");
        if (currentSalaId == null || currentSalaId.isEmpty()) {
            Toast.makeText(this, "Error: No se especificó sala", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        salaLatitud = getIntent().getDoubleExtra("SALA_LATITUD", 0);
        salaLongitud = getIntent().getDoubleExtra("SALA_LONGITUD", 0);
        salaRadioMetros = getIntent().getDoubleExtra("SALA_RADIO", 0);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editMessage = findViewById(R.id.editMessage);
        btnSend = findViewById(R.id.btnSend);
        listMessages = findViewById(R.id.listMessages);

        adapter = new MensajeAdapter(this, listaMensajes);
        listMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> {
            String mensaje = editMessage.getText().toString().trim();
            if (!mensaje.isEmpty()) {
                obtenerUbicacionYEnviar(mensaje);
            }
        });

        listMessages.setOnItemClickListener((parent, view, position, id) -> {
            Mensaje msg = listaMensajes.get(position);
            if (msg.getIdUsuario() != currentUserId) {
                abrirChatPrivado(msg.getIdUsuario(), msg.getNombre());
            }
        });

        unirseASalaEnServidor(currentSalaId);
        obtenerMensajes();
        iniciarAutoRefresco();
    }

    private void iniciarAutoRefresco() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                obtenerMensajes();
                verificarTiempoSesion();
                verificarUbicacion();
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(refreshRunnable, 3000);
    }

    // ─── Verificación de tiempo ───────────────────────────────────────────────

    private void verificarTiempoSesion() {
        RetrofitClient.getChatApiServices()
                .verificarSesionSala(currentUserId, currentSalaId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                if (json.has("expirado") && json.getBoolean("expirado")) {
                                    expulsarUsuario("Tu tiempo en la sala ha terminado");
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    // ─── Verificación de ubicación ────────────────────────────────────────────

    private void verificarUbicacion() {
        // Sin datos de geovalla o sin permiso → no verificar
        if (salaRadioMetros <= 0) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location == null) return;

            float[] resultado = new float[1];
            Location.distanceBetween(
                    location.getLatitude(), location.getLongitude(),
                    salaLatitud, salaLongitud,
                    resultado
            );

            float distanciaMetros = resultado[0];
            if (distanciaMetros > salaRadioMetros) {
                expulsarUsuario("Has salido del área de la sala");
            }
        });
    }

    private void expulsarUsuario(String motivo) {
        handler.removeCallbacks(refreshRunnable);
        Toast.makeText(this, motivo.toUpperCase(), Toast.LENGTH_LONG).show();
        finish();
    }

    // ─── Mensajes ─────────────────────────────────────────────────────────────

    private void obtenerMensajes() {
        RetrofitClient.getChatApiServices()
                .getMensajesGrupal(currentSalaId)
                .enqueue(new Callback<List<Mensaje>>() {
                    @Override
                    public void onResponse(Call<List<Mensaje>> call, Response<List<Mensaje>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            listaMensajes.clear();
                            listaMensajes.addAll(response.body());
                            adapter.notifyDataSetChanged();
                            listMessages.setSelection(adapter.getCount() - 1);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Mensaje>> call, Throwable t) {}
                });
    }

    private void obtenerUbicacionYEnviar(String mensaje) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            String mensajeConUbicacion = mensaje;
            if (location != null) {
                mensajeConUbicacion += "\n(Lat: " + location.getLatitude()
                        + ", Lon: " + location.getLongitude() + ")";
            }
            enviarMensajeAlServidor(mensajeConUbicacion);
        });
    }

    private void enviarMensajeAlServidor(String mensaje) {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        android.util.Log.d("DEBUG_CHAT", "Enviando a Sala: [" + currentSalaId + "] Usuario: " + currentUserId);

        api.enviarMensajeGrupal(currentSalaId.trim(), currentUserId, mensaje)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            editMessage.setText("");
                            obtenerMensajes();
                        } else {
                            int code = response.code();
                            android.util.Log.e("ERROR_SERVER", "Código: " + code
                                    + " | URL: " + call.request().url());
                            Toast.makeText(MainActivity.this,
                                    "Error " + code + ": El servidor rechazó el mensaje",
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        android.util.Log.e("ERROR_RED", "Fallo total: " + t.getMessage());
                        Toast.makeText(MainActivity.this,
                                "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─── Chat privado ─────────────────────────────────────────────────────────

    private void abrirChatPrivado(int otherUserId, String otherUserName) {
        RetrofitClient.getChatApiServices()
                .crearChatPrivado(currentUserId, otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                int idChatPrivado = json.getInt("id_chat_privado");
                                Intent intent = new Intent(MainActivity.this, PrivateChatActivity.class);
                                intent.putExtra("ID_CHAT_PRIVADO", idChatPrivado);
                                intent.putExtra("CURRENT_USER_ID", currentUserId);
                                intent.putExtra("OTHER_USER_ID", otherUserId);
                                intent.putExtra("OTHER_USER_NAME", otherUserName);
                                startActivity(intent);
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(MainActivity.this, "Error al conectar", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void unirseASalaEnServidor(String idSala) {
        RetrofitClient.getChatApiServices()
                .unirseASala(currentUserId, idSala)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }
}
