package com.example.chat.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
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
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.chat.R;
import com.example.chat.adapters.SalaAdapter;
import com.example.chat.models.PrivateChatHistoryItem;
import com.example.chat.models.Sala;
import com.example.chat.network.RetrofitClient;
import com.example.chat.utils.PrivateChatClosureStore;
import com.example.chat.utils.PrivateChatConversationPolicy;
import com.example.chat.utils.PrivateChatGeofenceStore;
import com.example.chat.utils.PrivateChatHistoryStore;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends BaseActivity {

    private static final String TAG = "HomeActivity";
    private static final int SCAN_QR_REQUEST_CODE = 2000;

    // Solo quedan las vistas que se modifican en otros métodos
    private com.google.android.material.button.MaterialButton drawerGestionUsuarios;
    private com.google.android.material.button.MaterialButton btnAbandonarGlobal;

    private LinearLayout layoutConSalas, layoutSinSalas;
    private ImageView imgDrawerFoto;
    private TextView textDrawerNombre;

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

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        ListView listSalas = findViewById(R.id.listSalas);

        layoutConSalas = findViewById(R.id.layoutConSalas);
        layoutSinSalas = findViewById(R.id.layoutSinSalas);

        imgDrawerFoto = findViewById(R.id.imgDrawerFoto);
        textDrawerNombre = findViewById(R.id.textDrawerNombre);
        drawerGestionUsuarios = findViewById(R.id.drawerGestionUsuarios);
        btnAbandonarGlobal = findViewById(R.id.btnAbandonarGlobal);
        drawerNotificaciones = findViewById(R.id.drawerNotificaciones);
        drawerNotifBadge = findViewById(R.id.drawerNotifBadge);

        com.google.android.material.button.MaterialButton btnMenuDrawer = findViewById(R.id.btnMenuDrawer);
        Button btnScanQRHomeAbajo = findViewById(R.id.btnScanQRHomeAbajo);
        Button btnScanQRHomeCentro = findViewById(R.id.btnScanQRHomeCentro);
        com.google.android.material.button.MaterialButton drawerEditarPerfil = findViewById(R.id.drawerEditarPerfil);
        com.google.android.material.button.MaterialButton drawerAjustes = findViewById(R.id.drawerAjustes);
        com.google.android.material.button.MaterialButton drawerTerminos = findViewById(R.id.drawerTerminos);
        com.google.android.material.button.MaterialButton drawerCerrarSesion = findViewById(R.id.drawerCerrarSesion);

        configurarFooterHistorialPrivado(listSalas);

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
                                        Log.e(TAG, "Error decodificando foto de perfil", e);
                                        imgDrawerFoto.setImageResource(R.drawable.defecto);
                                    }
                                } else {
                                    imgDrawerFoto.setImageResource(R.drawable.defecto);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error leyendo JSON del perfil", e);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                    }
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

                        if (listaMisSalas.isEmpty()) {
                            btnAbandonarGlobal.setVisibility(View.GONE);
                            drawerNotificaciones.setVisibility(View.GONE);
                        } else {
                            btnAbandonarGlobal.setVisibility(View.VISIBLE);
                            drawerNotificaciones.setVisibility(View.VISIBLE);
                        }

                        salaAdapter.notifyDataSetChanged();
                        actualizarVistasSalas();
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
    private void configurarFooterHistorialPrivado(ListView listSalas) {
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
                cargarMisSalas();
                historialHandler.postDelayed(this, 30_000L);
            }
        };
        historialHandler.post(historialRefreshRunnable);
    }
    private void refrescarHistorialPrivado() {
        if (layoutHistorialPrivadoItems == null || textHistorialPrivadoVacio == null) return;
        layoutHistorialPrivadoItems.removeAllViews();

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
        boolean hayHistorialVisible = false;
        for (PrivateChatHistoryItem item : historial) {
            if (PrivateChatClosureStore.isExpired(this, currentUserId, item.getOtherUserId())) {
                PrivateChatHistoryStore.removeChat(this, currentUserId, item.getOtherUserId());
                PrivateChatGeofenceStore.remove(this, currentUserId, item.getOtherUserId());
                continue;
            }

            View row = LayoutInflater.from(this).inflate(R.layout.item_historial_privado, layoutHistorialPrivadoItems, false);
            TextView textNombre = row.findViewById(R.id.textNombreHistorialPrivado);
            TextView textInicial = row.findViewById(R.id.textInicialHistorialPrivado);
            ImageView btnEliminarHistorial = row.findViewById(R.id.btnEliminarHistorialPrivado);
            ImageView imgFoto = row.findViewById(R.id.imgFotoHistorialPrivado);

            String nombreUsuario = item.getOtherUserName();
            if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
                textInicial.setText(nombreUsuario.substring(0, 1).toUpperCase());
            }
            textNombre.setText(item.getOtherUserName());

            if (imgFoto != null) {
                imgFoto.setVisibility(View.GONE);
                textInicial.setVisibility(View.VISIBLE);

                RetrofitClient.getChatApiServices().getUsuario(item.getOtherUserId())
                        .enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    try {
                                        JSONObject json = new JSONObject(response.body().string());
                                        String foto = json.optString("foto", "");

                                        if (!foto.isEmpty() && !foto.equalsIgnoreCase("null")) {
                                            byte[] decoded = Base64.decode(foto, Base64.DEFAULT);
                                            imgFoto.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                                            imgFoto.setVisibility(View.VISIBLE);
                                            textInicial.setVisibility(View.GONE);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error al cargar la foto de historial", e);
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Call<ResponseBody> call, Throwable t) {
                            }
                        });
            }

            row.setOnClickListener(v -> {
                if (esClickRapido()) return;
                abrirChatPrivadoDesdeHistorial(item);
            });
            row.setOnLongClickListener(v -> {
                mostrarConfirmacionEliminarHistorialPrivado(item);
                return true;
            });
            if (btnEliminarHistorial != null) {
                btnEliminarHistorial.setOnClickListener(v -> mostrarConfirmacionEliminarHistorialPrivado(item));
            }
            layoutHistorialPrivadoItems.addView(row);
            hayHistorialVisible = true;
        }

        if (!hayHistorialVisible) {
            textHistorialPrivadoVacio.setVisibility(View.VISIBLE);
        }
    }
    private void mostrarConfirmacionEliminarHistorialPrivado(PrivateChatHistoryItem item) {
        if (item == null) return;

        String nombre = item.getOtherUserName() == null || item.getOtherUserName().trim().isEmpty()
                ? "este usuario"
                : item.getOtherUserName().trim();

        new AlertDialog.Builder(this)
                .setTitle("Eliminar historial")
                .setMessage("Se eliminara el acceso a tu historial privado con " + nombre + ".")
                .setPositiveButton("ELIMINAR", (dialog, which) -> eliminarHistorialPrivado(item))
                .setNegativeButton("CANCELAR", null)
                .show();
    }
    private void eliminarHistorialPrivado(PrivateChatHistoryItem item) {
        if (item == null) return;

        PrivateChatHistoryStore.removeChat(this, currentUserId, item.getOtherUserId());
        PrivateChatGeofenceStore.remove(this, currentUserId, item.getOtherUserId());
        PrivateChatClosureStore.remove(this, currentUserId, item.getOtherUserId());
        Toast.makeText(this, "Historial eliminado", Toast.LENGTH_SHORT).show();
        refrescarHistorialPrivado();
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
                            } catch (Exception e) {
                                Log.e(TAG, "Error interpretando el rol", e);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                    }
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
        if (listaMisSalas.isEmpty()) {
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
                    } catch (Exception e) {
                        Log.e(TAG, "Error parseando número de notificaciones", e);
                        drawerNotifBadge.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
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
                } catch (Exception e) {
                    Log.e(TAG, "Error procesando el array de notificaciones", e);
                }
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
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.dialog_background));
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(12), dp(18), dp(8));
        scrollView.addView(container);
        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (NotificacionPrivada notificacion : notificaciones) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(16), dp(14), dp(16), dp(14));
            item.setBackgroundResource(R.drawable.bg_notification_card);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);

            View accent = new View(this);
            LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(4), dp(22));
            accentParams.setMargins(0, 0, dp(10), 0);
            accent.setLayoutParams(accentParams);
            accent.setBackgroundResource(R.drawable.bg_notification_accent);
            header.addView(accent);

            TextView sender = new TextView(this);
            sender.setText(notificacion.nombreRemitente);
            sender.setTextSize(15);
            sender.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            sender.setTextColor(ContextCompat.getColor(this, R.color.notification_text_primary));
            sender.setSingleLine(false);
            header.addView(sender, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            item.addView(header);

            TextView message = new TextView(this);
            message.setText(notificacion.contenido);
            message.setTextSize(14);
            message.setLineSpacing(dp(2), 1.0f);
            message.setTextColor(ContextCompat.getColor(this, R.color.notification_text_secondary));
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            messageParams.setMargins(dp(14), dp(8), 0, 0);
            item.addView(message, messageParams);

            LinearLayout acciones = new LinearLayout(this);
            acciones.setGravity(android.view.Gravity.END);
            acciones.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams accionesParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            accionesParams.setMargins(0, dp(12), 0, 0);
            if (notificacion.estado == PrivateChatConversationPolicy.State.PENDING_INCOMING) {
                MaterialButton btnRechazar = crearBotonNotificacion(false);
                btnRechazar.setText("Rechazar");
                btnRechazar.setOnClickListener(v -> responderNotificacionPrivada(dialogRef[0], notificacion, false));
                acciones.addView(btnRechazar);
                MaterialButton btnAceptar = crearBotonNotificacion(true);
                btnAceptar.setText("Aceptar");
                btnAceptar.setOnClickListener(v -> responderNotificacionPrivada(dialogRef[0], notificacion, true));
                acciones.addView(btnAceptar);
            } else {
                MaterialButton btnAbrir = crearBotonNotificacion(true);
                btnAbrir.setText("Abrir");
                btnAbrir.setOnClickListener(v -> abrirNotificacionPrivada(dialogRef[0], notificacion));
                acciones.addView(btnAbrir);
            }
            item.addView(acciones, accionesParams);

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            itemParams.setMargins(0, 0, 0, dp(12));
            container.addView(item, itemParams);
        }
        dialogRef[0] = new AlertDialog.Builder(this).setTitle("Notificaciones").setView(scrollView).setPositiveButton("Cerrar", null).create();
        dialogRef[0].show();
    }

    private MaterialButton crearBotonNotificacion(boolean primary) {
        MaterialButton button = new MaterialButton(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(40)
        );
        params.setMargins(dp(8), 0, 0, 0);
        button.setLayoutParams(params);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(14));
        button.setStrokeWidth(primary ? 0 : dp(1));
        button.setTextColor(ContextCompat.getColor(this, primary ? R.color.white : R.color.notification_text_primary));
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                this,
                primary ? R.color.notification_accent : R.color.notification_button_secondary_bg
        )));
        button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.notification_card_border)));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
        });
    }
    private void marcarMensajePrivadoLeido(int idMensaje) {
        if (idMensaje != -1) RetrofitClient.getChatApiServices().marcarLeidoPrivado(idMensaje).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
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
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
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
                .setMessage("Los chats privados de esta sala quedaran bloqueados y se eliminaran en 30 minutos. Volveras al estado inicial.")
                .setPositiveButton("SÍ, SALIR", (dialog, which) -> {
                    marcarChatsPrivadosCerradosPorSala(idSala, crearMensajeAbandonoSala());
                    RetrofitClient.getChatApiServices().salirDeSala(currentUserId, idSala)
                            .enqueue(new Callback<ResponseBody>() {
                                @Override
                                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                    if (response.isSuccessful()) {
                                        List<Integer> otrosUsuarios = PrivateChatGeofenceStore.getOtherUserIdsForSala(HomeActivity.this, currentUserId, idSala);
                                        for (Integer otherId : otrosUsuarios) {
                                            PrivateChatClosureStore.closeForThirtyMinutes(
                                                    HomeActivity.this,
                                                    currentUserId,
                                                    otherId,
                                                    idSala,
                                                    crearMensajeAbandonoSala()
                                            );
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
    private void marcarChatsPrivadosCerradosPorSala(String idSala, String motivo) {
        List<Integer> otrosUsuarios = PrivateChatGeofenceStore.getOtherUserIdsForSala(this, currentUserId, idSala);
        for (Integer otherId : otrosUsuarios) {
            if (otherId == null || otherId <= 0) continue;
            PrivateChatClosureStore.closeForThirtyMinutes(this, currentUserId, otherId, idSala, motivo);
        }
    }
    private String crearMensajeAbandonoSala() {
        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        String nombre = pref.getString("nombre", "");
        if (nombre == null || nombre.trim().isEmpty()) {
            nombre = "El usuario";
        }
        return nombre.trim() + " ha abandonado la sala principal. No se pueden enviar mas mensajes en este chat privado.";
    }
}
