package com.example.chat.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.example.chat.R;
import com.example.chat.adapters.MensajeAdapter;
import com.example.chat.models.Mensaje;
import com.example.chat.network.RetrofitClient;
import com.example.chat.utils.PrivateChatConversationPolicy;
import com.example.chat.utils.PrivateChatHistoryStore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PrivateChatActivity extends BaseActivity {

    private ListView listMessagesPrivate;
    private EditText editMessagePrivate;
    private Button btnSendPrivate;

    private MensajeAdapter adapter;
    private List<Mensaje> listaMensajes = new ArrayList<>();
    private Handler handler = new Handler();
    private Runnable refreshRunnable;

    private int currentUserId;
    private int otherUserId;
    private String otherUserName; // Esta variable ahora guardará el nombre de usuario tras la carga

    private LinearLayout layoutSolicitudPrivada;
    private LinearLayout layoutBotonesSolicitudPrivada;
    private TextView textEstadoConversacionPrivada;
    private Button btnAcceptPrivateRequest;
    private Button btnRejectPrivateRequest;
    private PrivateChatConversationPolicy.State conversationState = PrivateChatConversationPolicy.State.EMPTY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat);

        currentUserId = getIntent().getIntExtra("CURRENT_USER_ID", -1);
        otherUserId = getIntent().getIntExtra("OTHER_USER_ID", -1);
        otherUserName = getIntent().getStringExtra("OTHER_USER_NAME");
        PrivateChatHistoryStore.touchChat(this, currentUserId, otherUserId, otherUserName);

        // --- CONFIGURACIÓN DE LA TOOLBAR PERSONALIZADA ---
        Toolbar toolbar = findViewById(R.id.toolbarPrivateChat);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            View customToolbar = getLayoutInflater().inflate(R.layout.toolbar_perfil, null);
            getSupportActionBar().setCustomView(customToolbar);
            getSupportActionBar().setDisplayShowCustomEnabled(true);

            ImageView imgFoto = customToolbar.findViewById(R.id.imgToolbarFoto);
            TextView txtNombre = customToolbar.findViewById(R.id.textToolbarNombreUser);

            // Mostrar inicialmente lo que viene del intent
            txtNombre.setText(otherUserName);

            // Cargar datos reales para mostrar el NOMBRE DE USUARIO
            RetrofitClient.getChatApiServices().getUsuario(otherUserId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject json = new JSONObject(response.body().string());

                            // BUSCAMOS EL NOMBRE DE USUARIO
                            String username = json.optString("nombre_usuario", "");

                            // Si el nombre de usuario existe, lo ponemos arriba y actualizamos la variable global
                            if (!username.isEmpty()) {
                                txtNombre.setText(username);
                                otherUserName = username; // Actualizamos para que los textos de solicitud cambien también
                            }

                            // Carga de foto (se mantiene igual)
                            String fotoB64 = json.optString("foto", "");
                            if (!fotoB64.isEmpty()) {
                                byte[] decodedBytes = Base64.decode(fotoB64, Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                                imgFoto.setImageBitmap(bitmap);
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
                @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
            });
        }

        toolbar.setOnClickListener(v -> mostrarDialogoDenuncias(otherUserId));

        listMessagesPrivate = findViewById(R.id.listMessagesPrivate);
        editMessagePrivate = findViewById(R.id.editMessagePrivate);
        btnSendPrivate = findViewById(R.id.btnSendPrivate);
        layoutSolicitudPrivada = findViewById(R.id.layoutSolicitudPrivada);
        layoutBotonesSolicitudPrivada = findViewById(R.id.layoutBotonesSolicitudPrivada);
        textEstadoConversacionPrivada = findViewById(R.id.textEstadoConversacionPrivada);
        btnAcceptPrivateRequest = findViewById(R.id.btnAcceptPrivateRequest);
        btnRejectPrivateRequest = findViewById(R.id.btnRejectPrivateRequest);

        adapter = new MensajeAdapter(this, listaMensajes);
        listMessagesPrivate.setAdapter(adapter);

        btnSendPrivate.setOnClickListener(v -> enviarMensajePrivado());
        btnAcceptPrivateRequest.setOnClickListener(v -> responderSolicitudConversacion(true));
        btnRejectPrivateRequest.setOnClickListener(v -> responderSolicitudConversacion(false));

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
                            aplicarEstadoConversacion();
                        }
                    }
                    @Override public void onFailure(Call<List<Mensaje>> call, Throwable t) {}
                });
    }

    private void enviarMensajePrivado() {
        String mensaje = editMessagePrivate.getText().toString().trim();
        if (mensaje.isEmpty()) return;
        if (!PrivateChatConversationPolicy.canSendMessage(conversationState)) {
            Toast.makeText(this, "Tienes que esperar a que se acepte la conversacion", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.getChatApiServices()
                .enviarMensajePrivado(currentUserId, otherUserId, currentUserId, mensaje)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            editMessagePrivate.setText("");
                            obtenerMensajesPrivados();
                        }
                    }
                    @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private void aplicarEstadoConversacion() {
        conversationState = PrivateChatConversationPolicy.resolveState(listaMensajes, currentUserId);
        switch (conversationState) {
            case EMPTY:
            case ACCEPTED:
                layoutSolicitudPrivada.setVisibility(View.GONE);
                setInputPrivadoEnabled(true, "Escribe un mensaje privado...");
                break;
            case PENDING_INCOMING:
                layoutSolicitudPrivada.setVisibility(View.VISIBLE);
                layoutBotonesSolicitudPrivada.setVisibility(View.VISIBLE);
                textEstadoConversacionPrivada.setText(otherUserName + " quiere iniciar una conversacion.");
                setInputPrivadoEnabled(false, "Acepta para responder");
                break;
            case PENDING_OUTGOING:
                layoutSolicitudPrivada.setVisibility(View.VISIBLE);
                layoutBotonesSolicitudPrivada.setVisibility(View.GONE);
                textEstadoConversacionPrivada.setText("Esperando a que " + otherUserName + " acepte.");
                setInputPrivadoEnabled(false, "Esperando aceptacion");
                break;
            case REJECTED:
                layoutSolicitudPrivada.setVisibility(View.VISIBLE);
                layoutBotonesSolicitudPrivada.setVisibility(View.GONE);
                textEstadoConversacionPrivada.setText("Conversacion rechazada.");
                setInputPrivadoEnabled(false, "Conversacion rechazada");
                break;
        }
    }

    private void setInputPrivadoEnabled(boolean enabled, String hint) {
        editMessagePrivate.setEnabled(enabled);
        btnSendPrivate.setEnabled(enabled);
        editMessagePrivate.setHint(hint);
    }

    private void responderSolicitudConversacion(boolean aceptada) {
        String controlMessage = aceptada ? PrivateChatConversationPolicy.acceptedMessage() : PrivateChatConversationPolicy.rejectedMessage();
        RetrofitClient.getChatApiServices()
                .enviarMensajePrivado(currentUserId, otherUserId, currentUserId, controlMessage)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            obtenerMensajesPrivados();
                        }
                    }
                    @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void mostrarDialogoDenuncias(int idUsuarioDenunciado) {
        final String[] tiposDenuncia = {"Información falsa", "Comentario obsceno", "Otro"};
        final String[] tipoDenunciaSeleccionado = {""};
        final EditText[] editRazon = {null};

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Enviar Denuncia");

        android.widget.LinearLayout layoutDenuncia = new android.widget.LinearLayout(this);
        layoutDenuncia.setOrientation(android.widget.LinearLayout.VERTICAL);
        layoutDenuncia.setPadding(16, 16, 16, 16);

        android.widget.Spinner spinnerTipo = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapterSpinner = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tiposDenuncia);
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTipo.setAdapter(adapterSpinner);
        spinnerTipo.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                tipoDenunciaSeleccionado[0] = tiposDenuncia[position];
                if (position == 2) {
                    if (editRazon[0] == null) {
                        editRazon[0] = new EditText(PrivateChatActivity.this);
                        editRazon[0].setHint("Explica la razón de tu denuncia");
                        layoutDenuncia.addView(editRazon[0]);
                    }
                    editRazon[0].setVisibility(View.VISIBLE);
                } else if (editRazon[0] != null) {
                    editRazon[0].setVisibility(View.GONE);
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        layoutDenuncia.addView(spinnerTipo);
        builder.setView(layoutDenuncia);
        builder.setPositiveButton("Enviar", (dialog, which) -> {
            String razon = (tipoDenunciaSeleccionado[0].equals("Otro") && editRazon[0] != null) ? editRazon[0].getText().toString().trim() : "";
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
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable);
    }
}
