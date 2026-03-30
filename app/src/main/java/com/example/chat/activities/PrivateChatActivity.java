package com.example.chat.activities;

import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.example.chat.R;
import com.example.chat.adapters.MensajeAdapter;
import com.example.chat.models.Mensaje;
import com.example.chat.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PrivateChatActivity extends AppCompatActivity {

    private ListView listMessagesPrivate;
    private EditText editMessagePrivate;
    private Button btnSendPrivate;

    private MensajeAdapter adapter;
    private List<Mensaje> listaMensajes = new ArrayList<>();
    private Handler handler = new Handler();
    private Runnable refreshRunnable;

    private int currentUserId;
    private int otherUserId;
    private String otherUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat);

        currentUserId = getIntent().getIntExtra("CURRENT_USER_ID", -1);
        otherUserId = getIntent().getIntExtra("OTHER_USER_ID", -1);
        otherUserName = getIntent().getStringExtra("OTHER_USER_NAME");

        Toolbar toolbar = findViewById(R.id.toolbarPrivateChat);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Chat con: " + otherUserName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setOnClickListener(v -> mostrarDialogoDenuncias(otherUserId));

        listMessagesPrivate = findViewById(R.id.listMessagesPrivate);
        editMessagePrivate = findViewById(R.id.editMessagePrivate);
        btnSendPrivate = findViewById(R.id.btnSendPrivate);

        adapter = new MensajeAdapter(this, listaMensajes);
        listMessagesPrivate.setAdapter(adapter);

        btnSendPrivate.setOnClickListener(v -> enviarMensajePrivado());

        obtenerMensajesPrivados();
        iniciarAutoRefresco();
    }

    private void iniciarAutoRefresco() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                obtenerMensajesPrivados();
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(refreshRunnable, 3000);
    }

    private void obtenerMensajesPrivados() {
        RetrofitClient.getChatApiServices()
                .getMensajesPrivados(currentUserId, otherUserId)
                .enqueue(new Callback<List<Mensaje>>() {
                    @Override
                    public void onResponse(Call<List<Mensaje>> call, Response<List<Mensaje>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            listaMensajes.clear();
                            listaMensajes.addAll(response.body());
                            adapter.notifyDataSetChanged();
                            listMessagesPrivate.setSelection(adapter.getCount() - 1);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Mensaje>> call, Throwable t) {}
                });
    }

    private void enviarMensajePrivado() {
        String mensaje = editMessagePrivate.getText().toString().trim();
        if (mensaje.isEmpty()) return;

        RetrofitClient.getChatApiServices()
                .enviarMensajePrivado(currentUserId, otherUserId, currentUserId, mensaje)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            editMessagePrivate.setText("");
                            // Crear notificación para el usuario receptor
                            crearNotificacionMensajePrivado(mensaje);
                            obtenerMensajesPrivados();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(PrivateChatActivity.this, "Error al enviar", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void crearNotificacionMensajePrivado(String contenido) {
        RetrofitClient.getChatApiServices()
                .crearNotificacion(otherUserId, currentUserId, "mensaje_privado", contenido)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        // Notificación creada silenciosamente
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        // Error silencioso para no interrumpir al usuario
                    }
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void mostrarDialogoDenuncias(int idUsuarioDenunciado) {
        final String[] tiposDenuncia = {
            "Información falsa",
            "Comentario obsceno",
            "Otro"
        };
        final String[] tipoDenunciaSeleccionado = {""};
        final EditText[] editRazon = {null};

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Enviar Denuncia");

        android.widget.LinearLayout layoutDenuncia = new android.widget.LinearLayout(this);
        layoutDenuncia.setOrientation(android.widget.LinearLayout.VERTICAL);
        layoutDenuncia.setPadding(16, 16, 16, 16);

        android.widget.Spinner spinnerTipo = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, tiposDenuncia);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTipo.setAdapter(adapter);
        spinnerTipo.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                tipoDenunciaSeleccionado[0] = tiposDenuncia[position];
                if (position == 2) {
                    if (editRazon[0] == null) {
                        editRazon[0] = new EditText(PrivateChatActivity.this);
                        editRazon[0].setHint("Explica la razón de tu denuncia");
                        editRazon[0].setVisibility(android.view.View.VISIBLE);
                        layoutDenuncia.addView(editRazon[0]);
                    } else {
                        editRazon[0].setVisibility(android.view.View.VISIBLE);
                    }
                } else {
                    if (editRazon[0] != null) {
                        editRazon[0].setVisibility(android.view.View.GONE);
                    }
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        layoutDenuncia.addView(spinnerTipo);

        builder.setView(layoutDenuncia);
        builder.setPositiveButton("Enviar", (dialog, which) -> {
            if (tipoDenunciaSeleccionado[0].isEmpty()) {
                Toast.makeText(PrivateChatActivity.this, "Selecciona un tipo de denuncia", Toast.LENGTH_SHORT).show();
                return;
            }

            String razon = "";
            if (tipoDenunciaSeleccionado[0].equals("Otro") && editRazon[0] != null) {
                razon = editRazon[0].getText().toString().trim();
            }

            enviarDenuncia(idUsuarioDenunciado, tipoDenunciaSeleccionado[0], razon);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void enviarDenuncia(int idUsuarioDenunciado, String tipo, String razon) {
        RetrofitClient.getChatApiServices()
            .crearDenuncia(currentUserId, idUsuarioDenunciado, tipo, razon)
            .enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(PrivateChatActivity.this, "Denuncia registrada correctamente", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(PrivateChatActivity.this, "Error al registrar denuncia", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(PrivateChatActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                }
            });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable);
    }
}
