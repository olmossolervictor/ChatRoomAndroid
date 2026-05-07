package com.example.chat.activities;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable; // NUEVO
import android.text.TextWatcher; // NUEVO
import android.view.MenuItem;
import android.view.View; // NUEVO
import android.widget.Button;
import android.widget.EditText;
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
    private String otherUserName;

    // VARIABLES PARA LA ANIMACIÓN
    private long ultimoAvisoEscribiendo = 0;
    private LinearLayout layoutTyping;
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
        layoutTyping = findViewById(R.id.layoutTyping); // Vinculamos el ID del XML
        layoutSolicitudPrivada = findViewById(R.id.layoutSolicitudPrivada);
        layoutBotonesSolicitudPrivada = findViewById(R.id.layoutBotonesSolicitudPrivada);
        textEstadoConversacionPrivada = findViewById(R.id.textEstadoConversacionPrivada);
        btnAcceptPrivateRequest = findViewById(R.id.btnAcceptPrivateRequest);
        btnRejectPrivateRequest = findViewById(R.id.btnRejectPrivateRequest);

        adapter = new MensajeAdapter(this, listaMensajes);
        listMessagesPrivate.setAdapter(adapter);

        // Ajuste automático cuando sale el teclado
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);

            if (listMessagesPrivate != null && adapter != null && adapter.getCount() > 0) {
                listMessagesPrivate.postDelayed(() -> listMessagesPrivate.setSelection(adapter.getCount() - 1), 100);
            }
            return insets;
        });

        // --- NUEVO: ESCUCHADOR DE ESCRITURA PARA AVISAR AL OTRO ---
        editMessagePrivate.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    long tiempoActual = System.currentTimeMillis();
                    // Solo avisamos al servidor una vez cada 2 segundos para no saturarlo
                    if (tiempoActual - ultimoAvisoEscribiendo > 2000) {
                        ultimoAvisoEscribiendo = tiempoActual;
                        notificarEscribiendoAlServidor();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

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
                comprobarSiElOtroEscribe(); // NUEVO: Pregunta si debe mostrar la animación
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(refreshRunnable, 3000);
    }

    // --- NUEVO MÉTODO: NOTIFICAR AL SERVIDOR QUE YO ESCRIBO ---
    private void notificarEscribiendoAlServidor() {
        RetrofitClient.getChatApiServices()
                .notificarEscribiendo(currentUserId, otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (!response.isSuccessful()) {
                            notificarEscribiendoAlServidorPorQuery();
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        notificarEscribiendoAlServidorPorQuery();
                    }
                });
    }

    private void notificarEscribiendoAlServidorPorQuery() {
        RetrofitClient.getChatApiServices()
                .notificarEscribiendoQuery(currentUserId, otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
                    @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    // --- NUEVO MÉTODO: PREGUNTAR SI EL OTRO ESTÁ ESCRIBIENDO ---
    private void comprobarSiElOtroEscribe() {
        RetrofitClient.getChatApiServices()
                .getEstadoEscribiendoPath(currentUserId, otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            actualizarIndicadorEscritura(parsearEstadoEscribiendo(response.body()));
                        } else {
                            comprobarSiElOtroEscribePorQuery();
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        comprobarSiElOtroEscribePorQuery();
                    }
                });
    }

    private void comprobarSiElOtroEscribePorQuery() {
        RetrofitClient.getChatApiServices()
                .getEstadoEscribiendoQuery(currentUserId, otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            actualizarIndicadorEscritura(parsearEstadoEscribiendo(response.body()));
                        } else {
                            actualizarIndicadorEscritura(false);
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        actualizarIndicadorEscritura(false);
                    }
                });
    }

    private void actualizarIndicadorEscritura(boolean estaEscribiendo) {
        layoutTyping.setVisibility(estaEscribiendo ? View.VISIBLE : View.GONE);
    }

    private boolean parsearEstadoEscribiendo(ResponseBody body) {
        if (body == null) return false;
        try {
            String raw = body.string();
            if (raw == null) return false;
            String value = raw.trim();
            if (value.isEmpty()) return false;

            if ("true".equalsIgnoreCase(value) || "1".equals(value)) return true;
            if ("false".equalsIgnoreCase(value) || "0".equals(value)) return false;

            org.json.JSONObject json = new org.json.JSONObject(value);
            if (json.has("escribiendo")) return json.optBoolean("escribiendo", false);
            if (json.has("typing")) return json.optBoolean("typing", false);
            if (json.has("estado")) {
                String estado = json.optString("estado", "");
                return "escribiendo".equalsIgnoreCase(estado) || "typing".equalsIgnoreCase(estado);
            }
        } catch (Exception ignored) {
        }
        return false;
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
                            // listMessagesPrivate.setSelection(adapter.getCount() - 1); // Lo comentamos para que no salte todo el rato
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Mensaje>> call, Throwable t) {}
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
                            PrivateChatHistoryStore.touchChat(
                                    PrivateChatActivity.this,
                                    currentUserId,
                                    otherUserId,
                                    otherUserName
                            );
                            obtenerMensajesPrivados();
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(PrivateChatActivity.this, "Error al enviar", Toast.LENGTH_SHORT).show();
                    }
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
                textEstadoConversacionPrivada.setText(otherUserName + " quiere iniciar una conversacion privada. Acepta para poder responder o rechaza la conversacion.");
                setInputPrivadoEnabled(false, "Acepta la conversacion para responder");
                break;
            case PENDING_OUTGOING:
                layoutSolicitudPrivada.setVisibility(View.VISIBLE);
                layoutBotonesSolicitudPrivada.setVisibility(View.GONE);
                textEstadoConversacionPrivada.setText("Esperando a que " + otherUserName + " acepte la conversacion. Cuando acepte, podreis hablar.");
                setInputPrivadoEnabled(false, "Esperando aceptacion");
                break;
            case REJECTED:
                layoutSolicitudPrivada.setVisibility(View.VISIBLE);
                layoutBotonesSolicitudPrivada.setVisibility(View.GONE);
                textEstadoConversacionPrivada.setText("La conversacion ha sido rechazada. No se pueden enviar mas mensajes.");
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
        btnAcceptPrivateRequest.setEnabled(false);
        btnRejectPrivateRequest.setEnabled(false);

        String controlMessage = aceptada
                ? PrivateChatConversationPolicy.acceptedMessage()
                : PrivateChatConversationPolicy.rejectedMessage();

        RetrofitClient.getChatApiServices()
                .enviarMensajePrivado(currentUserId, otherUserId, currentUserId, controlMessage)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        btnAcceptPrivateRequest.setEnabled(true);
                        btnRejectPrivateRequest.setEnabled(true);
                        if (response.isSuccessful()) {
                            PrivateChatHistoryStore.touchChat(
                                    PrivateChatActivity.this,
                                    currentUserId,
                                    otherUserId,
                                    otherUserName
                            );
                            marcarMensajesEntrantesLeidos();
                            obtenerMensajesPrivados();
                        } else {
                            Toast.makeText(PrivateChatActivity.this, "No se pudo responder la solicitud", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        btnAcceptPrivateRequest.setEnabled(true);
                        btnRejectPrivateRequest.setEnabled(true);
                        Toast.makeText(PrivateChatActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void marcarMensajesEntrantesLeidos() {
        for (Mensaje mensaje : listaMensajes) {
            if (mensaje.getIdUsuario() == otherUserId
                    && !PrivateChatConversationPolicy.isControlMessage(mensaje.getMensaje())) {
                RetrofitClient.getChatApiServices()
                        .marcarLeidoPrivado(mensaje.getId())
                        .enqueue(new Callback<ResponseBody>() {
                            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
                            @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                        });
            }
        }
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
