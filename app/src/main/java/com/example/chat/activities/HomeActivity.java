package com.example.chat.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.chat.R;
import com.example.chat.adapters.SalaAdapter;
import com.example.chat.models.Mensaje;
import com.example.chat.models.PrivateChatHistoryItem;
import com.example.chat.models.Sala;
import com.example.chat.network.RetrofitClient;
import com.example.chat.utils.PrivateChatConversationPolicy;
import com.example.chat.utils.PrivateChatGeofenceStore;
import com.example.chat.utils.PrivateChatHistoryStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends BaseActivity {

    private static final int SCAN_QR_REQUEST_CODE = 2000;

    private DrawerLayout drawerLayout;
    private ListView listSalas;
    private com.google.android.material.button.MaterialButton btnMenuDrawer, drawerAjustes, drawerGestionUsuarios, drawerTerminos;

    private LinearLayout layoutConSalas, layoutSinSalas;
    private Button btnScanQRHomeAbajo, btnScanQRHomeCentro;

    private ImageView imgDrawerFoto;
    private TextView textDrawerNombre;

    private com.google.android.material.button.MaterialButton drawerEditarPerfil;
    private com.google.android.material.button.MaterialButton drawerCerrarSesion;
    private com.google.android.material.button.MaterialButton btnAbandonarGlobal;

    private RelativeLayout drawerNotificaciones;
    private TextView drawerNotifBadge;

    private SalaAdapter salaAdapter;
    private List<Sala> listaMisSalas = new ArrayList<>();
    private int currentUserId;
    private LinearLayout layoutHistorialPrivadoItems;
    private TextView textHistorialPrivadoVacio;
    private final Handler historialHandler = new Handler(Looper.getMainLooper());
    private Runnable historialRefreshRunnable;
    private long ultimoClickTime = 0;

    private static class NotificacionPrivada {
        String contenido;
        String nombreRemitente;
        int idRemitente;
        int idMensaje;
        PrivateChatConversationPolicy.State estado = PrivateChatConversationPolicy.State.EMPTY;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);

        drawerLayout = findViewById(R.id.drawerLayout);
        listSalas = findViewById(R.id.listSalas);
        btnMenuDrawer = findViewById(R.id.btnMenuDrawer);

        layoutConSalas = findViewById(R.id.layoutConSalas);
        layoutSinSalas = findViewById(R.id.layoutSinSalas);
        btnScanQRHomeAbajo = findViewById(R.id.btnScanQRHomeAbajo);
        btnScanQRHomeCentro = findViewById(R.id.btnScanQRHomeCentro);

        imgDrawerFoto = findViewById(R.id.imgDrawerFoto);
        textDrawerNombre = findViewById(R.id.textDrawerNombre);
        drawerEditarPerfil = findViewById(R.id.drawerEditarPerfil);
        drawerAjustes = findViewById(R.id.drawerAjustes);
        drawerGestionUsuarios = findViewById(R.id.drawerGestionUsuarios);
        drawerTerminos = findViewById(R.id.drawerTerminos);
        drawerCerrarSesion = findViewById(R.id.drawerCerrarSesion);
        drawerNotificaciones = findViewById(R.id.drawerNotificaciones);
        drawerNotifBadge = findViewById(R.id.drawerNotifBadge);

        configurarFooterHistorialPrivado();

        salaAdapter = new SalaAdapter(this, listaMisSalas);
        listSalas.setAdapter(salaAdapter);

        listSalas.setOnItemClickListener((parent, view, position, id) -> {
            if (esClickRapido()) return;
            Sala sala = listaMisSalas.get(position);
            abrirSala(sala.getIdSala() != null ? sala.getIdSala() : sala.getNombre());
        });

        btnMenuDrawer.setOnClickListener(v -> drawerLayout.openDrawer(androidx.core.view.GravityCompat.START));

        View.OnClickListener scanAction = v -> {
            if (esClickRapido()) return;
            boolean faltaCamara = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean faltaUbicacion = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED;

            if (faltaCamara || faltaUbicacion) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.ACCESS_FINE_LOCATION},
                        3000);
            } else {
                startActivityForResult(new Intent(this, ScannerActivity.class), SCAN_QR_REQUEST_CODE);
            }
        };
        btnScanQRHomeAbajo.setOnClickListener(scanAction);
        btnScanQRHomeCentro.setOnClickListener(scanAction);

        drawerEditarPerfil.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra("MODO_EDICION", true);
            startActivity(intent);
        });

        btnAbandonarGlobal = findViewById(R.id.btnAbandonarGlobal);
        btnAbandonarGlobal.setOnClickListener(v -> {
            if (listaMisSalas != null && !listaMisSalas.isEmpty()) {
                Sala sala = listaMisSalas.get(0);
                String idSala = sala.getIdSala() != null ? sala.getIdSala() : sala.getNombre();
                confirmarAbandonoTotal(idSala);
            }
        });

        drawerAjustes.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            startActivity(new Intent(this, AjustesActivity.class));
        });

        drawerGestionUsuarios.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            startActivity(new Intent(this, UserManagementActivity.class));
        });

        drawerTerminos.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            mostrarDialogoTerminos();
        });

        drawerNotificaciones.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            // 🚀 EXTRA SEGURIDAD: Si logran hacer clic, comprobamos que haya sala
            if (listaMisSalas.isEmpty()) {
                Toast.makeText(this, "Debes entrar a una sala general primero", Toast.LENGTH_SHORT).show();
                return;
            }
            mostrarNotificacionesConSolicitud();
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
        cargarPerfilDrawer();

        // 🚀 OJO AQUÍ: cargarMisSalas ahora es el que dispara notificaciones e historial
        // cuando responde el servidor, para saber seguro si estamos en una sala o no.
        cargarMisSalas();
        iniciarRefrescoHistorialPrivado();
    }

    @Override
    protected void onPause() {
        super.onPause();
        historialHandler.removeCallbacksAndMessages(null);
    }

    private void actualizarVistasSalas() {
        if (listaMisSalas.isEmpty()) {
            layoutConSalas.setVisibility(View.GONE);
            layoutSinSalas.setVisibility(View.VISIBLE);
        } else {
            layoutSinSalas.setVisibility(View.GONE);
            layoutConSalas.setVisibility(View.VISIBLE);
        }
    }

    private void cargarPerfilDrawer() {
        RetrofitClient.getChatApiServices().getUsuario(currentUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                String nombreUsuario = json.optString("nombre_usuario", "");
                                String nombre = json.optString("nombre", "");
                                String apellidos = json.optString("apellidos", "");

                                String displayName = !nombreUsuario.isEmpty() ? nombreUsuario : (nombre + " " + apellidos).trim();
                                textDrawerNombre.setText(displayName);

                                String foto = json.optString("foto", "");
                                if (!foto.isEmpty() && !foto.equalsIgnoreCase("null")) {
                                    try {
                                        byte[] decoded = Base64.decode(foto, Base64.DEFAULT);
                                        imgDrawerFoto.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                                    } catch (Exception e) {
                                        imgDrawerFoto.setImageResource(R.drawable.defecto);
                                    }
                                } else {
                                    imgDrawerFoto.setImageResource(R.drawable.defecto);
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
                            for (Sala sala : response.body()) {
                                if (debeMostrarSala(sala)) {
                                    listaMisSalas.add(sala);
                                }
                            }
                        }

                        // 🚀 REGLA DE ORO: SI NO HAY SALA GENERAL...
                        if (listaMisSalas.isEmpty()) {
                            btnAbandonarGlobal.setVisibility(View.GONE);
                            drawerNotificaciones.setVisibility(View.GONE); // Desaparecen notificaciones del menú
                        } else {
                            btnAbandonarGlobal.setVisibility(View.VISIBLE);
                            drawerNotificaciones.setVisibility(View.VISIBLE); // Aparecen notificaciones
                        }

                        salaAdapter.notifyDataSetChanged();
                        actualizarVistasSalas();

                        // 🚀 Lanzamos esto solo DESPUÉS de saber si estamos en una sala
                        actualizarBadgeNotificaciones();
                        refrescarHistorialPrivado();
                    }

                    @Override
                    public void onFailure(Call<List<Sala>> call, Throwable t) {
                        actualizarVistasSalas();
                        btnAbandonarGlobal.setVisibility(View.GONE);
                        drawerNotificaciones.setVisibility(View.GONE);
                    }
                });
    }

    private boolean debeMostrarSala(Sala sala) {
        if (sala == null) return false;
        String estado = sala.getEstado();
        if ("finalizada".equalsIgnoreCase(estado) || "expirada".equalsIgnoreCase(estado) || "cerrada".equalsIgnoreCase(estado) || "inactiva".equalsIgnoreCase(estado)) {
            return false;
        }
        long minutosRestantes = sala.getMinutosRestantes();
        return minutosRestantes == -1 || minutosRestantes > 0;
    }

    private void configurarFooterHistorialPrivado() {
        View footerView = LayoutInflater.from(this).inflate(R.layout.footer_historial_privado, listSalas, false);
        layoutHistorialPrivadoItems = footerView.findViewById(R.id.layoutHistorialPrivadoItems);
        textHistorialPrivadoVacio = footerView.findViewById(R.id.textHistorialPrivadoVacio);
        listSalas.addFooterView(footerView, null, false);
    }

    private void iniciarRefrescoHistorialPrivado() {
        historialHandler.removeCallbacksAndMessages(null);
        historialRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                cargarMisSalas(); // Cargar salas dispara todo lo demás en cadena
                historialHandler.postDelayed(this, 30_000L);
            }
        };
        historialHandler.post(historialRefreshRunnable);
    }

    private void refrescarHistorialPrivado() {
        if (layoutHistorialPrivadoItems == null || textHistorialPrivadoVacio == null) return;
        layoutHistorialPrivadoItems.removeAllViews();

        // 🚀 CORTAFUEGOS: Si no hay sala, no se procesa ni se muestra el historial
        if (listaMisSalas.isEmpty()) {
            textHistorialPrivadoVacio.setVisibility(View.GONE);
            return;
        }

        List<PrivateChatHistoryItem> historial = PrivateChatHistoryStore.getActiveHistory(this, currentUserId);
        if (historial.isEmpty()) {
            textHistorialPrivadoVacio.setVisibility(View.VISIBLE);
            return;
        }

        textHistorialPrivadoVacio.setVisibility(View.GONE);
        for (PrivateChatHistoryItem item : historial) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_historial_privado, layoutHistorialPrivadoItems, false);
            TextView textNombre = row.findViewById(R.id.textNombreHistorialPrivado);
            TextView textInicial = row.findViewById(R.id.textInicialHistorialPrivado);

            // 🚀 NUEVO: Enlazamos el ImageView de la foto (debe existir en el XML)
            ImageView imgFoto = row.findViewById(R.id.imgFotoHistorialPrivado);

            String nombreUsuario = item.getOtherUserName();
            if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
                textInicial.setText(nombreUsuario.substring(0, 1).toUpperCase());
            }
            textNombre.setText(item.getOtherUserName());

            // 🚀 LÓGICA PARA CARGAR LA FOTO DE PERFIL
            if (imgFoto != null) {
                // Por defecto, ocultamos la foto y mostramos la letra inicial
                imgFoto.setVisibility(View.GONE);
                textInicial.setVisibility(View.VISIBLE);

                // Pedimos los datos del usuario al servidor para ver si tiene foto
                RetrofitClient.getChatApiServices().getUsuario(item.getOtherUserId())
                        .enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    try {
                                        JSONObject json = new JSONObject(response.body().string());
                                        String foto = json.optString("foto", "");

                                        // Si tiene foto, la decodificamos y la mostramos
                                        if (!foto.isEmpty() && !foto.equalsIgnoreCase("null")) {
                                            byte[] decoded = Base64.decode(foto, Base64.DEFAULT);
                                            imgFoto.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));

                                            // Hacemos visible la foto y ocultamos el círculo de la letra
                                            imgFoto.setVisibility(View.VISIBLE);
                                            textInicial.setVisibility(View.GONE);
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

            row.setOnClickListener(v -> {
                if (esClickRapido()) return;
                abrirChatPrivadoDesdeHistorial(item);
            });
            layoutHistorialPrivadoItems.addView(row);
        }
    }

    private void abrirChatPrivadoDesdeHistorial(PrivateChatHistoryItem item) {
        PrivateChatHistoryStore.touchChat(this, currentUserId, item.getOtherUserId(), item.getOtherUserName());
        RetrofitClient.getChatApiServices().crearChatPrivado(currentUserId, item.getOtherUserId())
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        Intent intent = new Intent(HomeActivity.this, PrivateChatActivity.class);
                        intent.putExtra("CURRENT_USER_ID", currentUserId);
                        intent.putExtra("OTHER_USER_ID", item.getOtherUserId());
                        intent.putExtra("OTHER_USER_NAME", item.getOtherUserName());
                        PrivateChatGeofenceStore.attachExtrasIfAvailable(HomeActivity.this, intent, currentUserId, item.getOtherUserId());
                        startActivity(intent);
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(HomeActivity.this, "Error al abrir chat", Toast.LENGTH_SHORT).show();
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
            if (idSala != null) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("ID_SALA_QR", idSala);
                intent.putExtra("SALA_LATITUD", data.getDoubleExtra("SALA_LATITUD", 0));
                intent.putExtra("SALA_LONGITUD", data.getDoubleExtra("SALA_LONGITUD", 0));
                intent.putExtra("SALA_RADIO", data.getDoubleExtra("SALA_RADIO", 0));
                startActivity(intent);
            }
        }
    }

    private void comprobarRolUsuario() {
        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        String rolGuardado = pref.getString("rol", "").toLowerCase();
        if ("owner".equals(rolGuardado)) drawerGestionUsuarios.setVisibility(View.VISIBLE);

        RetrofitClient.getChatApiServices().getRolUsuario(currentUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                int idRol = json.optInt("id_rol", 3);
                                String nombreRol = json.optString("rol", "usuario").toLowerCase();
                                pref.edit().putString("rol", nombreRol).apply();
                                drawerGestionUsuarios.setVisibility((idRol == 1 || "owner".equals(nombreRol)) ? View.VISIBLE : View.GONE);
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private void mostrarDialogoTerminos() {
        CharSequence terminosFormateados;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            terminosFormateados = android.text.Html.fromHtml(getString(R.string.terminos_legales), android.text.Html.FROM_HTML_MODE_COMPACT);
        } else {
            terminosFormateados = android.text.Html.fromHtml(getString(R.string.terminos_legales));
        }
        new AlertDialog.Builder(this).setTitle("Términos y Condiciones").setMessage(terminosFormateados).setPositiveButton("Aceptar", (dialog, which) -> dialog.dismiss()).show();
    }

    private void actualizarBadgeNotificaciones() {
        // 🚀 CORTAFUEGOS: Si no hay sala, cortamos la petición a la API
        if (listaMisSalas.isEmpty() || !getSharedPreferences("AjustesPrefs", MODE_PRIVATE).getBoolean("notificaciones", true)) {
            drawerNotifBadge.setVisibility(View.GONE);
            return;
        }

        RetrofitClient.getChatApiServices().getResumenNotificaciones(currentUserId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONArray array = new JSONArray(response.body().string());
                        int count = array.length();
                        drawerNotifBadge.setText(String.valueOf(count));
                        drawerNotifBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                    } catch (Exception e) { drawerNotifBadge.setVisibility(View.GONE); }
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {}
        });
    }

    private void mostrarNotificacionesConSolicitud() {
        RetrofitClient.getChatApiServices().getResumenNotificaciones(currentUserId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    JSONArray array = new JSONArray(response.body().string());
                    List<NotificacionPrivada> notificaciones = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject n = array.getJSONObject(i);
                        NotificacionPrivada notificacion = new NotificacionPrivada();
                        notificacion.contenido = n.optString("contenido", "");
                        String nombreUsuario = n.optString("nombre_usuario", "");
                        notificacion.nombreRemitente = !nombreUsuario.isEmpty() ? nombreUsuario : n.optString("nombre", "Alguien");
                        notificacion.idRemitente = n.optInt("remitente_id", -1);
                        notificacion.idMensaje = n.optInt("id_mensaje", -1);
                        notificacion.estado = estadoDesdeServidor(n.optString("estado", "PENDIENTE"));
                        if (notificacion.idRemitente != -1) notificaciones.add(notificacion);
                    }
                    if (notificaciones.isEmpty()) {
                        new AlertDialog.Builder(HomeActivity.this).setTitle("Notificaciones").setMessage("No tienes notificaciones").setPositiveButton("Cerrar", null).show();
                    } else {
                        mostrarDialogoNotificacionesPrivadas(notificaciones);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Error al cargar notificaciones", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private PrivateChatConversationPolicy.State estadoDesdeServidor(String estado) {
        if ("ACEPTADO".equalsIgnoreCase(estado)) return PrivateChatConversationPolicy.State.ACCEPTED;
        if ("RECHAZADO".equalsIgnoreCase(estado)) return PrivateChatConversationPolicy.State.REJECTED;
        return PrivateChatConversationPolicy.State.PENDING_INCOMING;
    }

    private void mostrarDialogoNotificacionesPrivadas(List<NotificacionPrivada> notificaciones) {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 8);
        scrollView.addView(container);
        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (NotificacionPrivada notificacion : notificaciones) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, 12, 0, 16);
            TextView text = new TextView(this);
            text.setText(notificacion.nombreRemitente + ": " + notificacion.contenido);
            text.setTextSize(15);
            text.setTextColor(android.graphics.Color.parseColor("#222222"));
            item.addView(text);
            LinearLayout acciones = new LinearLayout(this);
            acciones.setGravity(android.view.Gravity.END);
            acciones.setOrientation(LinearLayout.HORIZONTAL);
            if (notificacion.estado == PrivateChatConversationPolicy.State.PENDING_INCOMING) {
                Button btnRechazar = new Button(this);
                btnRechazar.setText("Rechazar");
                btnRechazar.setOnClickListener(v -> responderNotificacionPrivada(dialogRef[0], notificacion, false));
                acciones.addView(btnRechazar);
                Button btnAceptar = new Button(this);
                btnAceptar.setText("Aceptar");
                btnAceptar.setOnClickListener(v -> responderNotificacionPrivada(dialogRef[0], notificacion, true));
                acciones.addView(btnAceptar);
            } else {
                Button btnAbrir = new Button(this);
                btnAbrir.setText("Abrir");
                btnAbrir.setOnClickListener(v -> abrirNotificacionPrivada(dialogRef[0], notificacion));
                acciones.addView(btnAbrir);
            }
            item.addView(acciones);
            container.addView(item);
        }
        dialogRef[0] = new AlertDialog.Builder(this).setTitle("Notificaciones").setView(scrollView).setPositiveButton("Cerrar", null).create();
        dialogRef[0].show();
    }

    private void abrirNotificacionPrivada(AlertDialog dialog, NotificacionPrivada notificacion) {
        if (dialog != null) dialog.dismiss();
        marcarMensajePrivadoLeido(notificacion.idMensaje);
        abrirChatPrivado(notificacion.idRemitente, notificacion.nombreRemitente);
    }

    private void responderNotificacionPrivada(AlertDialog dialog, NotificacionPrivada notificacion, boolean aceptada) {
        if (dialog != null) dialog.dismiss();
        String controlMessage = aceptada ? PrivateChatConversationPolicy.acceptedMessage() : PrivateChatConversationPolicy.rejectedMessage();
        RetrofitClient.getChatApiServices().enviarMensajePrivado(currentUserId, notificacion.idRemitente, currentUserId, controlMessage).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    marcarMensajePrivadoLeido(notificacion.idMensaje);
                    actualizarBadgeNotificaciones();
                    PrivateChatHistoryStore.touchChat(HomeActivity.this, currentUserId, notificacion.idRemitente, notificacion.nombreRemitente);
                    if (aceptada) abrirChatPrivado(notificacion.idRemitente, notificacion.nombreRemitente);
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {}
        });
    }

    private void marcarMensajePrivadoLeido(int idMensaje) {
        if (idMensaje != -1) RetrofitClient.getChatApiServices().marcarLeidoPrivado(idMensaje).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
        });
    }

    private void abrirChatPrivado(int idRemitente, String nombreRemitente) {
        RetrofitClient.getChatApiServices().crearChatPrivado(currentUserId, idRemitente).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                PrivateChatHistoryStore.touchChat(HomeActivity.this, currentUserId, idRemitente, nombreRemitente);
                Intent intent = new Intent(HomeActivity.this, PrivateChatActivity.class);
                intent.putExtra("CURRENT_USER_ID", currentUserId);
                intent.putExtra("OTHER_USER_ID", idRemitente);
                intent.putExtra("OTHER_USER_NAME", nombreRemitente);
                PrivateChatGeofenceStore.attachExtrasIfAvailable(HomeActivity.this, intent, currentUserId, idRemitente);
                startActivity(intent);
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {}
        });
    }

    private boolean esClickRapido() {
        long tiempoActual = System.currentTimeMillis();
        if (tiempoActual - ultimoClickTime < 1000) return true;
        ultimoClickTime = tiempoActual;
        return false;
    }

    private void confirmarAbandonoTotal(String idSala) {
        new AlertDialog.Builder(this)
                .setTitle("¿Cerrar sesión en la sala?")
                .setMessage("Se borrarán tus mensajes y chats privados de esta sala. Volverás al estado inicial.")
                .setPositiveButton("SÍ, SALIR", (dialog, which) -> {
                    RetrofitClient.getChatApiServices().salirDeSala(currentUserId, idSala)
                            .enqueue(new Callback<ResponseBody>() {
                                @Override
                                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                    if (response.isSuccessful()) {
                                        List<Integer> otrosUsuarios = PrivateChatGeofenceStore.getOtherUserIdsForSala(HomeActivity.this, currentUserId, idSala);
                                        for (Integer otherId : otrosUsuarios) {
                                            PrivateChatHistoryStore.removeChat(HomeActivity.this, currentUserId, otherId);
                                            PrivateChatGeofenceStore.remove(HomeActivity.this, currentUserId, otherId);
                                        }

                                        Toast.makeText(HomeActivity.this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
                                        cargarMisSalas();
                                    }
                                }
                                @Override
                                public void onFailure(Call<ResponseBody> call, Throwable t) {
                                    Toast.makeText(HomeActivity.this, "Error al conectar", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }
}
