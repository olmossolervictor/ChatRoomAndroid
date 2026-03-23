package com.example.chat;

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

    private EditText editMessage;
    private Button btnSend;
    private ListView listMessages;
    
    private MensajeAdapter adapter;
    private List<Mensaje> listaMensajes = new ArrayList<>();

    private Handler handler = new Handler();
    private Runnable refreshRunnable;

    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private int currentUserId;
    private String currentSalaId;

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

        // Obtener ID de sala del Intent (enviado desde HomeActivity tras el QR)
        currentSalaId = getIntent().getStringExtra("ID_SALA_QR");
        if (currentSalaId == null || currentSalaId.isEmpty()) {
            Toast.makeText(this, "Error: No se especificó sala", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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
            Mensaje msgSeleccionado = listaMensajes.get(position);
            if (msgSeleccionado.getIdUsuario() != currentUserId) {
                abrirChatPrivado(msgSeleccionado.getIdUsuario(), msgSeleccionado.getNombre());
            }
        });

        // Al entrar, notificamos al servidor para el control de tiempo
        unirseASalaEnServidor(currentSalaId);
        
        obtenerMensajes();
        iniciarAutoRefresco();
    }

    private void abrirChatPrivado(int otherUserId, String otherUserName) {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.crearChatPrivado(currentUserId, otherUserId);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String result = response.body().string();
                        JSONObject json = new JSONObject(result);
                        int idChatPrivado = json.getInt("id_chat_privado");

                        Intent intent = new Intent(MainActivity.this, PrivateChatActivity.class);
                        intent.putExtra("ID_CHAT_PRIVADO", idChatPrivado);
                        intent.putExtra("CURRENT_USER_ID", currentUserId);
                        intent.putExtra("OTHER_USER_ID", otherUserId);
                        intent.putExtra("OTHER_USER_NAME", otherUserName);
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error al conectar", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void unirseASalaEnServidor(String idSala) {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.unirseASala(currentUserId, idSala);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {}
        });
    }

    private void iniciarAutoRefresco() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                obtenerMensajes();
                verificarTiempoSesion();
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(refreshRunnable, 3000);
    }

    private void verificarTiempoSesion() {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.verificarSesionSala(currentUserId, currentSalaId);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonString = response.body().string();
                        JSONObject json = new JSONObject(jsonString);
                        if (json.has("expirado") && json.getBoolean("expirado")) {
                            expulsarUsuario();
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {}
        });
    }

    private void expulsarUsuario() {
        handler.removeCallbacks(refreshRunnable);
        Toast.makeText(this, "TU TIEMPO EN LA SALA HA TERMINADO", Toast.LENGTH_LONG).show();
        finish(); // Volver al Home
    }

    private void obtenerMensajes() {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<List<Mensaje>> call = api.getMensajesGrupal(currentSalaId);
        call.enqueue(new Callback<List<Mensaje>>() {
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            String mensajeConUbicacion = mensaje;
            if (location != null) {
                mensajeConUbicacion += "\n(Lat: " + location.getLatitude() + ", Lon: " + location.getLongitude() + ")";
            }
            enviarMensajeAlServidor(mensajeConUbicacion);
        });
    }

    private void enviarMensajeAlServidor(String mensaje) {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.enviarMensajeGrupal(currentSalaId, currentUserId, mensaje);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    editMessage.setText(""); 
                    obtenerMensajes();
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
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
