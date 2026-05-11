package com.example.chat.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
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
import androidx.core.app.ActivityCompat;

import com.example.chat.R;
import com.example.chat.adapters.MensajeAdapter;
import com.example.chat.models.Mensaje;
import com.example.chat.network.RetrofitClient;
import com.example.chat.utils.PrivateChatConversationPolicy;
import com.example.chat.utils.PrivateChatGeofenceStore;
import com.example.chat.utils.PrivateChatHistoryStore;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PrivateChatActivity extends BaseActivity {

    private static final int LOCATION_PERMISSION_PRIVATE_CHAT_REQUEST = 4100;

    private ListView listMessagesPrivate;
    private EditText editMessagePrivate;
    private Button btnSendPrivate;

    private MensajeAdapter adapter;
    private List<Mensaje> listaMensajes = new ArrayList<>();
    private Handler handler = new Handler();
    private Runnable refreshRunnable;
    private FusedLocationProviderClient fusedLocationClient;

    private int currentUserId;
    private int otherUserId;
    private String otherUserName;
    private String idSalaOrigen;
    private double salaLatitud = 0;
    private double salaLongitud = 0;
    private double salaRadioMetros = 0;
    private long ultimoCheckGeofence = 0L;
    private boolean chatCerradoPorGeofence = false;

    // VARIABLES PARA LA ANIMACIÃ“N
    private long ultimoAvisoEscribiendo = 0;
    private LinearLayout layoutTyping;
    private View layoutSolicitudPrivada;
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
        idSalaOrigen = getIntent().getStringExtra(PrivateChatGeofenceStore.EXTRA_ID_SALA_ORIGEN);
        salaLatitud = getIntent().getDoubleExtra(PrivateChatGeofenceStore.EXTRA_SALA_LATITUD, 0);
        salaLongitud = getIntent().getDoubleExtra(PrivateChatGeofenceStore.EXTRA_SALA_LONGITUD, 0);
        salaRadioMetros = getIntent().getDoubleExtra(PrivateChatGeofenceStore.EXTRA_SALA_RADIO, 0);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
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

        // Ajuste automÃ¡tico cuando sale el teclado
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
        cargarInfoGeofenceSiHaceFalta();
        verificarUbicacionPrivada(true);
        iniciarAutoRefresco();
    }

    private void iniciarAutoRefresco() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                obtenerMensajesPrivados();
                comprobarSiElOtroEscribe(); // NUEVO: Pregunta si debe mostrar la animacion
                verificarUbicacionPrivada(false);
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(refreshRunnable, 3000);
    }

    // --- NUEVO MÃ‰TODO: NOTIFICAR AL SERVIDOR QUE YO ESCRIBO ---
    private void notificarEscribiendoAlServidor() {
        RetrofitClient.getChatApiServices()
                .notificarEscribiendo(currentUserId, otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    // --- NUEVO MÃ‰TODO: PREGUNTAR SI EL OTRO ESTÃ ESCRIBIENDO ---
    private void comprobarSiElOtroEscribe() {
        RetrofitClient.getChatApiServices()
                .getEstadoEscribiendoPath(currentUserId, otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (layoutTyping != null && response.isSuccessful() && response.body() != null) {
                            layoutTyping.setVisibility(parsearEstadoEscribiendo(response.body()) ? View.VISIBLE : View.GONE);
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
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

            JSONObject json = new JSONObject(value);
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

    private void cargarInfoGeofenceSiHaceFalta() {
        if (!necesitaInfoGeofenceRemota()) {
            return;
        }

        RetrofitClient.getChatApiServices()
                .getInfoChatPrivado(currentUserId, otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            return;
                        }

                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.optBoolean("eliminado", false)) {
                                cerrarChatPrivadoPorGeofence("Este chat privado ya no esta disponible.");
                                return;
                            }

                            String salaRemota = json.optString("id_sala_origen", "").trim();
                            if (!salaRemota.isEmpty()) {
                                idSalaOrigen = salaRemota;
                            }

                            double latitudRemota = extraerPrimerDouble(json, "latitud", "latitude", "sala_latitud", "lat");
                            if (!Double.isNaN(latitudRemota)) {
                                salaLatitud = latitudRemota;
                            }

                            double longitudRemota = extraerPrimerDouble(json, "longitud", "longitude", "sala_longitud", "lon", "lng");
                            if (!Double.isNaN(longitudRemota)) {
                                salaLongitud = longitudRemota;
                            }

                            double radioRemoto = extraerPrimerDouble(json, "radio_metros", "radio", "radioMetros", "sala_radio", "sala_radio_metros");
                            if (!Double.isNaN(radioRemoto) && radioRemoto >= 0) {
                                salaRadioMetros = radioRemoto;
                            }

                            if (tieneGeofencePrivada()) {
                                PrivateChatGeofenceStore.save(
                                        PrivateChatActivity.this,
                                        currentUserId,
                                        otherUserId,
                                        idSalaOrigen,
                                        salaLatitud,
                                        salaLongitud,
                                        salaRadioMetros
                                );
                                verificarUbicacionPrivada(true);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private boolean necesitaInfoGeofenceRemota() {
        return idSalaOrigen == null
                || idSalaOrigen.trim().isEmpty()
                || !tieneCoordenadasPrivadas();
    }

    private void verificarUbicacionPrivada(boolean force) {
        if (!tieneGeofencePrivada() || chatCerradoPorGeofence) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && now - ultimoCheckGeofence < 3_000L) {
            return;
        }
        ultimoCheckGeofence = now;

        ejecutarConUbicacionValida(null);
    }

    private boolean tieneGeofencePrivada() {
        return idSalaOrigen != null
                && !idSalaOrigen.trim().isEmpty()
                && tieneCoordenadasPrivadas();
    }

    private boolean tieneCoordenadasPrivadas() {
        return salaLatitud != 0 || salaLongitud != 0;
    }

    private double getRadioPrivadoEfectivo() {
        return salaRadioMetros > 0 ? salaRadioMetros : 100.0;
    }

    private void ejecutarConUbicacionValida(Runnable accionSiValida) {
        if (!tieneGeofencePrivada()) {
            if (accionSiValida != null) accionSiValida.run();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_PRIVATE_CHAT_REQUEST
            );
            Toast.makeText(this, "Necesito ubicacion para mantener este chat privado activo", Toast.LENGTH_SHORT).show();
            return;
        }

        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(5000)
                .build();

        fusedLocationClient.getCurrentLocation(request, null)
                .addOnSuccessListener(this, location -> {
                    if (location == null) {
                        if (accionSiValida != null) {
                            Toast.makeText(this, "No se pudo comprobar tu ubicacion", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    float[] resultado = new float[1];
                    Location.distanceBetween(
                            location.getLatitude(),
                            location.getLongitude(),
                            salaLatitud,
                            salaLongitud,
                            resultado
                    );

                    if (resultado[0] > getRadioPrivadoEfectivo()) {
                        cerrarChatPrivadoPorGeofence("Has salido del area de la sala. Chat privado eliminado.");
                        return;
                    }

                    if (accionSiValida != null) accionSiValida.run();
                });
    }

    private void cerrarChatPrivadoPorGeofence(String mensaje) {
        if (chatCerradoPorGeofence) {
            return;
        }
        chatCerradoPorGeofence = true;

        handler.removeCallbacks(refreshRunnable);
        List<Integer> usuariosDeLaSala = PrivateChatGeofenceStore.getOtherUserIdsForSala(this, currentUserId, idSalaOrigen);
        for (Integer usuarioId : usuariosDeLaSala) {
            if (usuarioId == null || usuarioId <= 0) continue;
            PrivateChatHistoryStore.removeChat(this, currentUserId, usuarioId);
            PrivateChatGeofenceStore.remove(this, currentUserId, usuarioId);
        }
        PrivateChatHistoryStore.removeChat(this, currentUserId, otherUserId);
        PrivateChatGeofenceStore.remove(this, currentUserId, otherUserId);

        RetrofitClient.getChatApiServices()
                .eliminarChatPrivado(currentUserId, otherUserId, idSalaOrigen, mensaje)
                .enqueue(new Callback<ResponseBody>() {
                    @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
                    @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
        RetrofitClient.getChatApiServices()
                .eliminarChatsPrivadosDeSala(currentUserId, idSalaOrigen, mensaje)
                .enqueue(new Callback<ResponseBody>() {
                    @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
                    @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });

        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_PRIVATE_CHAT_REQUEST
                && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)
                && tieneGeofencePrivada()) {
            cerrarChatPrivadoPorGeofence("Sin ubicacion no se puede mantener este chat privado.");
        }
    }

    private double extraerPrimerDouble(JSONObject json, String... keys) {
        for (String key : keys) {
            if (!json.has(key)) continue;
            try {
                return json.getDouble(key);
            } catch (Exception ignored) {
            }
            try {
                String value = json.getString(key);
                if (value != null && !value.trim().isEmpty()) {
                    return Double.parseDouble(value.trim().replace(",", "."));
                }
            } catch (Exception ignored) {
            }
        }
        return Double.NaN;
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
                        } else if (response.code() == 410) {
                            cerrarChatPrivadoPorGeofence("Este chat privado ha sido eliminado.");
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Mensaje>> call, Throwable t) {}
                });
    }

    private void enviarMensajePrivado() {
        String mensaje = editMessagePrivate.getText().toString().trim();
        if (mensaje.isEmpty()) return;
        if (chatCerradoPorGeofence) return;
        if (!PrivateChatConversationPolicy.canSendMessage(conversationState)) {
            Toast.makeText(this, "Tienes que esperar a que se acepte la conversacion", Toast.LENGTH_SHORT).show();
            return;
        }

        ejecutarConUbicacionValida(() -> enviarMensajePrivadoValidado(mensaje));
    }

    private void enviarMensajePrivadoValidado(String mensaje) {
        Call<ResponseBody> call = tieneGeofencePrivada()
                ? RetrofitClient.getChatApiServices()
                        .enviarMensajePrivadoDesdeSala(currentUserId, otherUserId, currentUserId, mensaje, idSalaOrigen)
                : RetrofitClient.getChatApiServices()
                        .enviarMensajePrivado(currentUserId, otherUserId, currentUserId, mensaje);

        call.enqueue(new Callback<ResponseBody>() {
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
                        } else if (response.code() == 410) {
                            cerrarChatPrivadoPorGeofence("Este chat privado ha sido eliminado.");
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

        Call<ResponseBody> call = tieneGeofencePrivada()
                ? RetrofitClient.getChatApiServices()
                        .enviarMensajePrivadoDesdeSala(currentUserId, otherUserId, currentUserId, controlMessage, idSalaOrigen)
                : RetrofitClient.getChatApiServices()
                        .enviarMensajePrivado(currentUserId, otherUserId, currentUserId, controlMessage);

        call.enqueue(new Callback<ResponseBody>() {
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
                        } else if (response.code() == 410) {
                            cerrarChatPrivadoPorGeofence("Este chat privado ha sido eliminado.");
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
        final String[] tiposDenuncia = {"InformaciÃ³n falsa", "Comentario obsceno", "Otro"};
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
                        editRazon[0].setHint("Explica la razÃ³n de tu denuncia");
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

