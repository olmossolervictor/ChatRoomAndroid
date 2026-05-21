package com.example.chat.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
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
import com.example.chat.models.Sala;
import com.example.chat.network.RetrofitClient;
import com.example.chat.utils.PrivateChatClosureStore;
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

import de.hdodenhof.circleimageview.CircleImageView;

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
    private Runnable closedExpirationRunnable;
    private FusedLocationProviderClient fusedLocationClient;

    private int currentUserId;
    private int otherUserId;
    private String otherUserName;
    private String idSalaOrigen;
    private double salaLatitud = 0;
    private double salaLongitud = 0;
    private double salaRadioMetros = 0;
    private long ultimoCheckGeofence = 0L;
    private long ultimoCheckParticipacionSala = 0L;
    private boolean chatCerradoPorGeofence = false;
    private boolean checkParticipacionEnCurso = false;

    private boolean isSolicitandoPermiso = false;

    // VARIABLES PARA LA ANIMACIÓN
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
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setOnClickListener(v -> mostrarDialogoDenuncias(otherUserId));

        TextView textToolbarPrivateTitle = findViewById(R.id.textToolbarPrivateTitle);
        if (textToolbarPrivateTitle != null) {
            textToolbarPrivateTitle.setText(otherUserName);
        }

        CircleImageView imgToolbarFoto = findViewById(R.id.imgToolbarFoto);
        cargarFotoOtroUsuario(imgToolbarFoto);

        listMessagesPrivate = findViewById(R.id.listMessagesPrivate);
        editMessagePrivate = findViewById(R.id.editMessagePrivate);
        btnSendPrivate = findViewById(R.id.btnSendPrivate);
        layoutTyping = findViewById(R.id.layoutTyping);
        layoutSolicitudPrivada = findViewById(R.id.layoutSolicitudPrivada);
        layoutBotonesSolicitudPrivada = findViewById(R.id.layoutBotonesSolicitudPrivada);
        textEstadoConversacionPrivada = findViewById(R.id.textEstadoConversacionPrivada);
        btnAcceptPrivateRequest = findViewById(R.id.btnAcceptPrivateRequest);
        btnRejectPrivateRequest = findViewById(R.id.btnRejectPrivateRequest);

        adapter = new MensajeAdapter(this, listaMensajes);
        listMessagesPrivate.setAdapter(adapter);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);

            if (listMessagesPrivate != null && adapter != null && adapter.getCount() > 0) {
                listMessagesPrivate.postDelayed(() -> listMessagesPrivate.setSelection(adapter.getCount() - 1), 100);
            }
            return insets;
        });

        editMessagePrivate.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    long tiempoActual = System.currentTimeMillis();
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

        if (aplicarCierreGuardadoSiExiste()) {
            obtenerMensajesPrivados();
            return;
        }

        obtenerMensajesPrivados();
        cargarInfoGeofenceSiHaceFalta();
        verificarUbicacionPrivada(true);
        iniciarAutoRefresco();
    }

    // 🔥 EL BYPASS DEFINITIVO CONTRA BASEACTIVITY 🔥
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                View bottomContainer = findViewById(R.id.layoutInputMessageContainer);

                if (bottomContainer != null) {
                    android.graphics.Rect containerRect = new android.graphics.Rect();
                    bottomContainer.getGlobalVisibleRect(containerRect);

                    if (!containerRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                        v.clearFocus();
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                        }
                    }
                }
            }
        }

        // Llamada directa al sistema operativo, evitando el BaseActivity
        if (getWindow().superDispatchTouchEvent(ev)) {
            return true;
        }
        return onTouchEvent(ev);
    }

    private void cargarFotoOtroUsuario(CircleImageView imageView) {
        if (imageView == null || otherUserId == -1) return;

        RetrofitClient.getChatApiServices().getUsuario(otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                String fotoBase64 = json.optString("foto", "");

                                if (!fotoBase64.isEmpty() && !fotoBase64.equalsIgnoreCase("null")) {
                                    byte[] decoded = Base64.decode(fotoBase64, Base64.DEFAULT);
                                    imageView.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                                } else {
                                    imageView.setImageResource(R.drawable.defecto);
                                }
                            } catch (Exception e) {
                                imageView.setImageResource(R.drawable.defecto);
                            }
                        } else {
                            imageView.setImageResource(R.drawable.defecto);
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        imageView.setImageResource(R.drawable.defecto);
                    }
                });
    }

    private void iniciarAutoRefresco() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                obtenerMensajesPrivados();
                comprobarSiElOtroEscribe();
                verificarParticipacionSalaPrivada(false, null);
                verificarUbicacionPrivada(false);
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(refreshRunnable, 3000);
    }

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
                                bloquearChatPrivadoTemporalmente(remplazarSiVacio(
                                        extraerMotivoCierre(json),
                                        "Este chat privado ya no esta disponible."
                                ), System.currentTimeMillis() + 30L * 60L * 1000L, true);
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
                            verificarParticipacionSalaPrivada(true, null);
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

    private boolean tieneSalaOrigenPrivada() {
        return idSalaOrigen != null && !idSalaOrigen.trim().isEmpty();
    }

    private boolean aplicarCierreGuardadoSiExiste() {
        PrivateChatClosureStore.Closure closure = PrivateChatClosureStore.getActiveClosure(this, currentUserId, otherUserId);
        if (closure == null) {
            return false;
        }

        if (!tieneSalaOrigenPrivada() && closure.getSalaOrigenId() != null && !closure.getSalaOrigenId().trim().isEmpty()) {
            idSalaOrigen = closure.getSalaOrigenId();
        }

        bloquearChatPrivadoTemporalmente(
                remplazarSiVacio(closure.getMessage(), mensajeCierrePorAbandono(nombreOtroUsuarioSeguro())),
                closure.getExpiresAtMs(),
                false
        );
        return true;
    }

    private boolean tieneCoordenadasPrivadas() {
        return salaLatitud != 0 || salaLongitud != 0;
    }

    private double getRadioPrivadoEfectivo() {
        return salaRadioMetros > 0 ? salaRadioMetros : 100.0;
    }

    private void verificarParticipacionSalaPrivada(boolean force, Runnable accionSiActiva) {
        if (chatCerradoPorGeofence) {
            return;
        }

        if (!tieneSalaOrigenPrivada()) {
            if (accionSiActiva != null) accionSiActiva.run();
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && now - ultimoCheckParticipacionSala < 3_000L) {
            if (accionSiActiva != null) accionSiActiva.run();
            return;
        }

        if (checkParticipacionEnCurso && accionSiActiva == null) {
            return;
        }

        checkParticipacionEnCurso = true;
        ultimoCheckParticipacionSala = now;

        RetrofitClient.getChatApiServices()
                .getMisSalas(currentUserId)
                .enqueue(new Callback<List<Sala>>() {
                    @Override
                    public void onResponse(Call<List<Sala>> call, Response<List<Sala>> response) {
                        boolean currentUserEnSala = response.isSuccessful()
                                && usuarioPermaneceEnSala(response.body());
                        verificarOtroUsuarioEnSala(currentUserEnSala, accionSiActiva);
                    }

                    @Override
                    public void onFailure(Call<List<Sala>> call, Throwable t) {
                        checkParticipacionEnCurso = false;
                        if (accionSiActiva != null) {
                            Toast.makeText(PrivateChatActivity.this, "No se pudo verificar la sala. Intentalo de nuevo.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void verificarOtroUsuarioEnSala(boolean currentUserEnSala, Runnable accionSiActiva) {
        RetrofitClient.getChatApiServices()
                .getMisSalas(otherUserId)
                .enqueue(new Callback<List<Sala>>() {
                    @Override
                    public void onResponse(Call<List<Sala>> call, Response<List<Sala>> response) {
                        checkParticipacionEnCurso = false;
                        boolean otherUserEnSala = response.isSuccessful()
                                && usuarioPermaneceEnSala(response.body());

                        if (!currentUserEnSala || !otherUserEnSala) {
                            String mensaje = currentUserEnSala
                                    ? mensajeCierrePorAbandono(nombreOtroUsuarioSeguro())
                                    : mensajeCierrePorAbandono("Has");
                            cerrarChatPrivadoPorSalidaSala(mensaje);
                            return;
                        }

                        if (accionSiActiva != null) {
                            accionSiActiva.run();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Sala>> call, Throwable t) {
                        checkParticipacionEnCurso = false;
                        if (accionSiActiva != null) {
                            Toast.makeText(PrivateChatActivity.this, "No se pudo verificar la sala. Intentalo de nuevo.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private boolean usuarioPermaneceEnSala(List<Sala> salas) {
        if (salas == null || idSalaOrigen == null) return false;

        String salaEsperada = idSalaOrigen.trim();
        for (Sala sala : salas) {
            if (sala == null || !salaActiva(sala)) continue;

            String idSala = sala.getIdSala();
            String nombreSala = sala.getNombre();
            if (salaEsperada.equalsIgnoreCase(idSala != null ? idSala.trim() : "")
                    || salaEsperada.equalsIgnoreCase(nombreSala != null ? nombreSala.trim() : "")) {
                return true;
            }
        }
        return false;
    }

    private boolean salaActiva(Sala sala) {
        String estado = sala.getEstado();
        if ("finalizada".equalsIgnoreCase(estado)
                || "expirada".equalsIgnoreCase(estado)
                || "cerrada".equalsIgnoreCase(estado)
                || "inactiva".equalsIgnoreCase(estado)) {
            return false;
        }
        long minutosRestantes = sala.getMinutosRestantes();
        return minutosRestantes == -1 || minutosRestantes > 0;
    }

    private String nombreOtroUsuarioSeguro() {
        return otherUserName == null || otherUserName.trim().isEmpty()
                ? "El usuario"
                : otherUserName.trim();
    }

    private String mensajeCierrePorAbandono(String nombre) {
        if ("Has".equals(nombre)) {
            return "Has abandonado la sala principal. No se pueden enviar mas mensajes en este chat privado.";
        }
        return nombre + " ha abandonado la sala principal. No se pueden enviar mas mensajes en este chat privado.";
    }

    private void ejecutarConUbicacionValida(Runnable accionSiValida) {
        if (!tieneGeofencePrivada()) {
            if (accionSiValida != null) accionSiValida.run();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!isSolicitandoPermiso) {
                isSolicitandoPermiso = true;
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_PRIVATE_CHAT_REQUEST
                );
            }
            return;
        }

        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(5000)
                .build();

        fusedLocationClient.getCurrentLocation(request, null)
                .addOnSuccessListener(this, location -> {
                    if (location == null) return;

                    float[] resultado = new float[1];
                    Location.distanceBetween(
                            location.getLatitude(),
                            location.getLongitude(),
                            salaLatitud,
                            salaLongitud,
                            resultado
                    );

                    if (resultado[0] > getRadioPrivadoEfectivo()) {
                        cerrarChatPrivadoPorGeofence("Has salido del área de la sala. Chat privado eliminado.");
                        return;
                    }

                    if (accionSiValida != null) accionSiValida.run();
                });
    }

    private void cerrarChatPrivadoPorGeofence(String mensaje) {
        cerrarChatPrivado(mensaje, true);
    }

    private void cerrarChatPrivadoPorSalidaSala(String mensaje) {
        bloquearChatPrivadoTemporalmente(mensaje, System.currentTimeMillis() + 30L * 60L * 1000L, true);
    }

    private void bloquearChatPrivadoTemporalmente(String mensaje, long expiresAtMs, boolean guardarCierre) {
        chatCerradoPorGeofence = true;
        checkParticipacionEnCurso = false;

        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
        if (layoutTyping != null) {
            layoutTyping.setVisibility(View.GONE);
        }
        if (layoutSolicitudPrivada != null) {
            layoutSolicitudPrivada.setVisibility(View.VISIBLE);
        }
        if (layoutBotonesSolicitudPrivada != null) {
            layoutBotonesSolicitudPrivada.setVisibility(View.GONE);
        }
        if (textEstadoConversacionPrivada != null) {
            textEstadoConversacionPrivada.setText(mensaje + "\nEste chat se eliminara en 30 minutos.");
        }
        if (editMessagePrivate != null) {
            editMessagePrivate.setText("");
        }
        setInputPrivadoEnabled(false, "Chat cerrado");

        if (guardarCierre) {
            PrivateChatClosureStore.closeForThirtyMinutes(
                    this,
                    currentUserId,
                    otherUserId,
                    idSalaOrigen,
                    mensaje
            );
            PrivateChatHistoryStore.touchChat(this, currentUserId, otherUserId, otherUserName);
        }

        programarEliminacionChatCerrado(expiresAtMs);
    }

    private void programarEliminacionChatCerrado(long expiresAtMs) {
        if (closedExpirationRunnable != null) {
            handler.removeCallbacks(closedExpirationRunnable);
        }

        long delay = Math.max(1_000L, expiresAtMs - System.currentTimeMillis());
        closedExpirationRunnable = () -> {
            PrivateChatClosureStore.remove(PrivateChatActivity.this, currentUserId, otherUserId);
            PrivateChatHistoryStore.removeChat(PrivateChatActivity.this, currentUserId, otherUserId);
            PrivateChatGeofenceStore.remove(PrivateChatActivity.this, currentUserId, otherUserId);
            Toast.makeText(PrivateChatActivity.this, "Chat privado eliminado", Toast.LENGTH_SHORT).show();
            finish();
        };
        handler.postDelayed(closedExpirationRunnable, delay);
    }

    private void cerrarChatPrivado(String mensaje, boolean notificarServidor) {
        if (chatCerradoPorGeofence) {
            return;
        }
        chatCerradoPorGeofence = true;

        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
        List<Integer> usuariosDeLaSala = PrivateChatGeofenceStore.getOtherUserIdsForSala(this, currentUserId, idSalaOrigen);
        for (Integer usuarioId : usuariosDeLaSala) {
            if (usuarioId == null || usuarioId <= 0) continue;
            PrivateChatHistoryStore.removeChat(this, currentUserId, usuarioId);
            PrivateChatGeofenceStore.remove(this, currentUserId, usuarioId);
        }
        PrivateChatHistoryStore.removeChat(this, currentUserId, otherUserId);
        PrivateChatGeofenceStore.remove(this, currentUserId, otherUserId);

        if (notificarServidor) {
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
        }

        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
        finish();
    }

    private String extraerMotivoCierre(Response<?> response, String fallback) {
        try {
            String raw = response.errorBody() != null ? response.errorBody().string() : "";
            if (raw == null || raw.trim().isEmpty()) {
                return fallback;
            }

            String value = raw.trim();
            if (value.startsWith("{")) {
                return remplazarSiVacio(extraerMotivoCierre(new JSONObject(value)), fallback);
            }
            return value.length() <= 160 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String extraerMotivoCierre(JSONObject json) {
        if (json == null) return "";
        return extraerPrimerString(json, "motivo", "message", "mensaje", "detalle", "reason");
    }

    private String extraerPrimerString(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }

    private String remplazarSiVacio(String value, String fallback) {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())
                ? fallback
                : value.trim();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_PRIVATE_CHAT_REQUEST) {
            isSolicitandoPermiso = false;
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                cerrarChatPrivadoPorGeofence("Se requiere permiso de ubicación para este chat.");
            }
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
                        } else if (response.code() == 410) {
                            bloquearChatPrivadoTemporalmente(
                                    extraerMotivoCierre(response, "Este chat privado ha sido cerrado."),
                                    System.currentTimeMillis() + 30L * 60L * 1000L,
                                    true
                            );
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Mensaje>> call, Throwable t) {}
                });
    }

    private void enviarMensajePrivado() {
        String mensaje = editMessagePrivate.getText().toString().trim();
        if (mensaje.isEmpty()) return;
        if (chatCerradoPorGeofence) {
            Toast.makeText(this, "Este chat privado esta cerrado", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PrivateChatConversationPolicy.canSendMessage(conversationState)) {
            Toast.makeText(this, "Tienes que esperar a que se acepte la conversación", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!tieneSalaOrigenPrivada() && necesitaInfoGeofenceRemota()) {
            cargarInfoGeofenceSiHaceFalta();
            Toast.makeText(this, "Verificando que el chat sigue activo...", Toast.LENGTH_SHORT).show();
            return;
        }

        verificarParticipacionSalaPrivada(true,
                () -> ejecutarConUbicacionValida(() -> enviarMensajePrivadoValidado(mensaje)));
    }

    private void enviarMensajePrivadoValidado(String mensaje) {
        if (chatCerradoPorGeofence) return;
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
                    bloquearChatPrivadoTemporalmente(
                            extraerMotivoCierre(response, "Este chat privado ha sido cerrado."),
                            System.currentTimeMillis() + 30L * 60L * 1000L,
                            true
                    );
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(PrivateChatActivity.this, "Error al enviar", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void aplicarEstadoConversacion() {
        if (chatCerradoPorGeofence) {
            return;
        }

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
                textEstadoConversacionPrivada.setText(otherUserName + " quiere iniciar una conversación privada. Acepta para poder responder o rechaza la conversación.");
                setInputPrivadoEnabled(false, "Acepta la conversación para responder");
                break;
            case PENDING_OUTGOING:
                layoutSolicitudPrivada.setVisibility(View.VISIBLE);
                layoutBotonesSolicitudPrivada.setVisibility(View.GONE);
                textEstadoConversacionPrivada.setText("Esperando a que " + otherUserName + " acepte la conversación. Cuando acepte, podréis hablar.");
                setInputPrivadoEnabled(false, "Esperando aceptación");
                break;
            case REJECTED:
                layoutSolicitudPrivada.setVisibility(View.VISIBLE);
                layoutBotonesSolicitudPrivada.setVisibility(View.GONE);
                textEstadoConversacionPrivada.setText("La conversación ha sido rechazada. No se pueden enviar más mensajes.");
                setInputPrivadoEnabled(false, "Conversación rechazada");
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
                    bloquearChatPrivadoTemporalmente(
                            extraerMotivoCierre(response, "Este chat privado ha sido cerrado."),
                            System.currentTimeMillis() + 30L * 60L * 1000L,
                            true
                    );
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
        if (refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
        if (closedExpirationRunnable != null) {
            handler.removeCallbacks(closedExpirationRunnable);
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
}
