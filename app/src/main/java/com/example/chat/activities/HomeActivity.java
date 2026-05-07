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
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.chat.R;
import com.example.chat.adapters.SalaAdapter;
import com.example.chat.models.Mensaje;
import com.example.chat.models.PrivateChatHistoryItem;
import com.example.chat.models.Sala;
import com.example.chat.network.RetrofitClient;
import com.example.chat.utils.PrivateChatConversationPolicy;
import com.example.chat.utils.PrivateChatHistoryStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends BaseActivity {

    private static final int SCAN_QR_REQUEST_CODE = 2000;

    private DrawerLayout drawerLayout;
    private ListView listSalas;
    private ImageButton btnMenuDrawer;

    // Vistas para gestionar los estados vacío/con salas
    private LinearLayout layoutConSalas, layoutSinSalas;
    private Button btnScanQRHomeAbajo, btnScanQRHomeCentro;

    // Drawer views
    private ImageView imgDrawerFoto;
    private TextView textDrawerNombre;
    private TextView drawerEditarPerfil, drawerAjustes, drawerGestionUsuarios;
    private TextView drawerTerminos, drawerDenuncias, drawerCerrarSesion;
    private RelativeLayout drawerNotificaciones;
    private TextView drawerNotifBadge;

    private SalaAdapter salaAdapter;
    private List<Sala> listaMisSalas = new ArrayList<>();
    private int currentUserId;
    private LinearLayout layoutHistorialPrivadoItems;
    private TextView textHistorialPrivadoVacio;
    private final Handler historialHandler = new Handler(Looper.getMainLooper());
    private Runnable historialRefreshRunnable;

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

        // Instanciamos los nuevos layouts
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
        drawerDenuncias = findViewById(R.id.drawerDenuncias);
        drawerCerrarSesion = findViewById(R.id.drawerCerrarSesion);
        drawerNotificaciones = findViewById(R.id.drawerNotificaciones);
        drawerNotifBadge = findViewById(R.id.drawerNotifBadge);

        configurarFooterHistorialPrivado();

        salaAdapter = new SalaAdapter(this, listaMisSalas);
        listSalas.setAdapter(salaAdapter);

        // Click en lista de salas
        listSalas.setOnItemClickListener((parent, view, position, id) -> {
            Sala sala = listaMisSalas.get(position);
            abrirSala(sala.getIdSala() != null ? sala.getIdSala() : sala.getNombre());
        });

        // Click en menú lateral
        btnMenuDrawer.setOnClickListener(v -> drawerLayout.openDrawer(androidx.core.view.GravityCompat.START));

        View.OnClickListener scanAction = v -> {
            boolean faltaCamara = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean faltaUbicacion = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED;

            if (faltaCamara || faltaUbicacion) {
                // Si falta alguno, le pedimos LOS DOS a la vez
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.ACCESS_FINE_LOCATION},
                        3000);
            } else {
                // Si ya tiene los dos, entra directo al escaner
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

        drawerDenuncias.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Toast.makeText(this, "Para denunciar a un usuario, haz clic en su nombre en los chats", Toast.LENGTH_LONG).show();
        });

        drawerNotificaciones.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
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
        cargarMisSalas();
        actualizarBadgeNotificaciones();
        refrescarHistorialPrivado();
        iniciarRefrescoHistorialPrivado();
    }

    @Override
    protected void onPause() {
        super.onPause();
        historialHandler.removeCallbacksAndMessages(null);
    }

    private void actualizarVistasSalas() {
        boolean hayHistorialPrivado = !PrivateChatHistoryStore.getActiveHistory(this, currentUserId).isEmpty();
        if (listaMisSalas.isEmpty() && !hayHistorialPrivado) {
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
                                if (!foto.isEmpty()) {
                                    byte[] decoded = Base64.decode(foto, Base64.DEFAULT);
                                    imgDrawerFoto.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                                } else {
                                    imgDrawerFoto.setImageResource(android.R.drawable.ic_menu_camera);
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
                        }
                        salaAdapter.notifyDataSetChanged();
                        actualizarVistasSalas();
                    }
                    @Override
                    public void onFailure(Call<List<Sala>> call, Throwable t) {
                        actualizarVistasSalas();
                    }
                });
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
                refrescarHistorialPrivado();
                historialHandler.postDelayed(this, 30_000L);
            }
        };
        historialHandler.post(historialRefreshRunnable);
    }

    private void refrescarHistorialPrivado() {
        if (layoutHistorialPrivadoItems == null || textHistorialPrivadoVacio == null) {
            return;
        }

        List<PrivateChatHistoryItem> historial = PrivateChatHistoryStore.getActiveHistory(this, currentUserId);
        layoutHistorialPrivadoItems.removeAllViews();

        if (historial.isEmpty()) {
            textHistorialPrivadoVacio.setVisibility(View.VISIBLE);
            return;
        }

        textHistorialPrivadoVacio.setVisibility(View.GONE);

        for (PrivateChatHistoryItem item : historial) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_historial_privado, layoutHistorialPrivadoItems, false);

            TextView textNombre = row.findViewById(R.id.textNombreHistorialPrivado);
            TextView textTiempo = row.findViewById(R.id.textTiempoHistorialPrivado);

            textNombre.setText(item.getOtherUserName());
            textTiempo.setVisibility(View.GONE);

            row.setOnClickListener(v -> abrirChatPrivadoDesdeHistorial(item));
            layoutHistorialPrivadoItems.addView(row);
        }
    }

    private void abrirChatPrivadoDesdeHistorial(PrivateChatHistoryItem item) {
        PrivateChatHistoryStore.touchChat(this, currentUserId, item.getOtherUserId(), item.getOtherUserName());

        RetrofitClient.getChatApiServices()
                .crearChatPrivado(currentUserId, item.getOtherUserId())
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        Intent intent = new Intent(HomeActivity.this, PrivateChatActivity.class);
                        intent.putExtra("CURRENT_USER_ID", currentUserId);
                        intent.putExtra("OTHER_USER_ID", item.getOtherUserId());
                        intent.putExtra("OTHER_USER_NAME", item.getOtherUserName());
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
        // Comprobación inmediata con lo guardado en SharedPrefs
        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        String rolGuardado = pref.getString("rol", "").toLowerCase();
        if ("owner".equals(rolGuardado)) {
            drawerGestionUsuarios.setVisibility(View.VISIBLE);
        }

        // Refresco desde la API por si el rol cambió
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
                                if (idRol == 1 || "owner".equals(nombreRol)) {
                                    drawerGestionUsuarios.setVisibility(View.VISIBLE);
                                } else {
                                    drawerGestionUsuarios.setVisibility(View.GONE);
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private void mostrarDialogoTerminos() {
        // 1. Obtenemos el texto del strings.xml y lo convertimos a formato visual HTML
        CharSequence terminosFormateados;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            terminosFormateados = android.text.Html.fromHtml(getString(R.string.terminos_legales), android.text.Html.FROM_HTML_MODE_COMPACT);
        } else {
            terminosFormateados = android.text.Html.fromHtml(getString(R.string.terminos_legales));
        }

        // 2. Construimos el diálogo igual que antes, pero pasándole la variable formateada
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Términos y Condiciones");
        builder.setMessage(terminosFormateados);
        builder.setPositiveButton("Aceptar", (dialog, which) -> dialog.dismiss());

        // Al mostrar un texto largo en .setMessage(), Android le pone scroll automáticamente
        builder.show();
    }

    private void actualizarBadgeNotificaciones() {
        if (!getSharedPreferences("AjustesPrefs", MODE_PRIVATE).getBoolean("notificaciones", true)) {
            drawerNotifBadge.setVisibility(View.GONE);
            return;
        }
        RetrofitClient.getChatApiServices()
                .getNoLeidosPrivados(currentUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONArray array = new JSONArray(response.body().string());
                                int count = 0;
                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject item = array.optJSONObject(i);
                                    if (item == null) continue;
                                    if (!PrivateChatConversationPolicy.isControlMessage(item.optString("mensaje", ""))) {
                                        count++;
                                    }
                                }
                                if (count > 0) {
                                    drawerNotifBadge.setText(String.valueOf(count));
                                    drawerNotifBadge.setVisibility(View.VISIBLE);
                                } else {
                                    drawerNotifBadge.setVisibility(View.GONE);
                                }
                            } catch (Exception e) {
                                drawerNotifBadge.setVisibility(View.GONE);
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private void mostrarNotificaciones() {
        RetrofitClient.getChatApiServices()
                .getNoLeidosPrivados(currentUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONArray array = new JSONArray(response.body().string());
                                if (array.length() == 0) {
                                    new AlertDialog.Builder(HomeActivity.this)
                                            .setTitle("Notificaciones")
                                            .setMessage("No tienes notificaciones")
                                            .setPositiveButton("Cerrar", null)
                                            .show();
                                    return;
                                }

                                List<String> items = new ArrayList<>();
                                List<Integer> remitenteIds = new ArrayList<>();
                                List<String> remitenteNombres = new ArrayList<>();
                                List<Integer> mensajeIds = new ArrayList<>();

                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject n = array.getJSONObject(i);
                                    String contenido = n.optString("mensaje", "");
                                    String nombreUsuario = n.optString("nombre_usuario", "");
                                    String nombre = n.optString("nombre", "Alguien");
                                    String nombreRemitente = !nombreUsuario.isEmpty() ? nombreUsuario : nombre;
                                    int idRemitente = n.optInt("id_usuario", -1);
                                    int idMensaje = n.optInt("id", -1);

                                    items.add("● " + nombreRemitente + ": " + contenido);
                                    remitenteIds.add(idRemitente);
                                    remitenteNombres.add(nombreRemitente);
                                    mensajeIds.add(idMensaje);
                                }

                                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                        HomeActivity.this,
                                        android.R.layout.simple_list_item_1,
                                        items);

                                ListView listView = new ListView(HomeActivity.this);
                                listView.setAdapter(adapter);
                                listView.setPadding(24, 16, 24, 0);

                                AlertDialog dialog = new AlertDialog.Builder(HomeActivity.this)
                                        .setTitle("Notificaciones")
                                        .setView(listView)
                                        .setPositiveButton("Cerrar", null)
                                        .create();

                                listView.setOnItemClickListener((parent, view, position, id) -> {
                                    dialog.dismiss();
                                    int idRemitente = remitenteIds.get(position);
                                    String nombreRemitente = remitenteNombres.get(position);
                                    int idMensaje = mensajeIds.get(position);

                                    // Marcar mensaje como leído
                                    if (idMensaje != -1) {
                                        RetrofitClient.getChatApiServices()
                                                .marcarLeidoPrivado(idMensaje)
                                                .enqueue(new Callback<ResponseBody>() {
                                                    @Override public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {}
                                                    @Override public void onFailure(Call<ResponseBody> c, Throwable t) {}
                                                });
                                    }

                                    // Abrir chat privado con el remitente
                                    if (idRemitente != -1) {
                                        RetrofitClient.getChatApiServices()
                                                .crearChatPrivado(currentUserId, idRemitente)
                                                .enqueue(new Callback<ResponseBody>() {
                                                    @Override
                                                    public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                                                        Intent intent = new Intent(HomeActivity.this, PrivateChatActivity.class);
                                                        intent.putExtra("CURRENT_USER_ID", currentUserId);
                                                        intent.putExtra("OTHER_USER_ID", idRemitente);
                                                        intent.putExtra("OTHER_USER_NAME", nombreRemitente);
                                                        startActivity(intent);
                                                    }
                                                    @Override public void onFailure(Call<ResponseBody> c, Throwable t) {
                                                        Toast.makeText(HomeActivity.this, "Error al abrir chat", Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                    }
                                });

                                dialog.show();
                                drawerNotifBadge.setVisibility(View.GONE);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(HomeActivity.this, "Error al cargar notificaciones", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void mostrarNotificacionesConSolicitud() {
        RetrofitClient.getChatApiServices()
                .getNoLeidosPrivados(currentUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (!response.isSuccessful() || response.body() == null) return;

                        try {
                            JSONArray array = new JSONArray(response.body().string());
                            List<NotificacionPrivada> notificaciones = new ArrayList<>();

                            for (int i = 0; i < array.length(); i++) {
                                JSONObject n = array.getJSONObject(i);
                                String contenido = n.optString("mensaje", "");
                                if (PrivateChatConversationPolicy.isControlMessage(contenido)) {
                                    continue;
                                }

                                String nombreUsuario = n.optString("nombre_usuario", "");
                                String nombre = n.optString("nombre", "Alguien");

                                NotificacionPrivada notificacion = new NotificacionPrivada();
                                notificacion.contenido = contenido;
                                notificacion.nombreRemitente = !nombreUsuario.isEmpty() ? nombreUsuario : nombre;
                                notificacion.idRemitente = n.optInt("id_usuario", -1);
                                notificacion.idMensaje = n.optInt("id", -1);

                                if (notificacion.idRemitente != -1) {
                                    notificaciones.add(notificacion);
                                }
                            }

                            if (notificaciones.isEmpty()) {
                                new AlertDialog.Builder(HomeActivity.this)
                                        .setTitle("Notificaciones")
                                        .setMessage("No tienes notificaciones")
                                        .setPositiveButton("Cerrar", null)
                                        .show();
                                return;
                            }

                            cargarEstadosNotificaciones(notificaciones);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(HomeActivity.this, "Error al cargar notificaciones", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void cargarEstadosNotificaciones(List<NotificacionPrivada> notificaciones) {
        final int[] pendientes = {notificaciones.size()};

        for (NotificacionPrivada notificacion : notificaciones) {
            RetrofitClient.getChatApiServices()
                    .getMensajesPrivados(currentUserId, notificacion.idRemitente)
                    .enqueue(new Callback<List<Mensaje>>() {
                        @Override
                        public void onResponse(Call<List<Mensaje>> call, Response<List<Mensaje>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                notificacion.estado = PrivateChatConversationPolicy.resolveState(response.body(), currentUserId);
                            } else {
                                notificacion.estado = PrivateChatConversationPolicy.State.PENDING_INCOMING;
                            }
                            mostrarDialogoCuandoTerminen(notificaciones, pendientes);
                        }

                        @Override
                        public void onFailure(Call<List<Mensaje>> call, Throwable t) {
                            notificacion.estado = PrivateChatConversationPolicy.State.PENDING_INCOMING;
                            mostrarDialogoCuandoTerminen(notificaciones, pendientes);
                        }
                    });
        }
    }

    private void mostrarDialogoCuandoTerminen(List<NotificacionPrivada> notificaciones, int[] pendientes) {
        pendientes[0]--;
        if (pendientes[0] == 0) {
            mostrarDialogoNotificacionesPrivadas(notificaciones);
        }
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
            acciones.setPadding(0, 10, 0, 0);

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
                item.setOnClickListener(v -> abrirNotificacionPrivada(dialogRef[0], notificacion));
            }

            item.addView(acciones);
            container.addView(item);
        }

        dialogRef[0] = new AlertDialog.Builder(this)
                .setTitle("Notificaciones")
                .setView(scrollView)
                .setPositiveButton("Cerrar", null)
                .create();
        dialogRef[0].show();
    }

    private void abrirNotificacionPrivada(AlertDialog dialog, NotificacionPrivada notificacion) {
        if (dialog != null) dialog.dismiss();
        marcarMensajePrivadoLeido(notificacion.idMensaje);
        abrirChatPrivado(notificacion.idRemitente, notificacion.nombreRemitente);
    }

    private void responderNotificacionPrivada(AlertDialog dialog, NotificacionPrivada notificacion, boolean aceptada) {
        if (dialog != null) dialog.dismiss();

        String controlMessage = aceptada
                ? PrivateChatConversationPolicy.acceptedMessage()
                : PrivateChatConversationPolicy.rejectedMessage();

        RetrofitClient.getChatApiServices()
                .enviarMensajePrivado(currentUserId, notificacion.idRemitente, currentUserId, controlMessage)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (!response.isSuccessful()) {
                            Toast.makeText(HomeActivity.this, "Error al responder la solicitud", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        marcarMensajePrivadoLeido(notificacion.idMensaje);
                        actualizarBadgeNotificaciones();
                        PrivateChatHistoryStore.touchChat(HomeActivity.this, currentUserId, notificacion.idRemitente, notificacion.nombreRemitente);
                        if (aceptada) {
                            abrirChatPrivado(notificacion.idRemitente, notificacion.nombreRemitente);
                        } else {
                            Toast.makeText(HomeActivity.this, "Conversacion rechazada", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(HomeActivity.this, "Error al responder la solicitud", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void marcarMensajePrivadoLeido(int idMensaje) {
        if (idMensaje == -1) return;
        RetrofitClient.getChatApiServices()
                .marcarLeidoPrivado(idMensaje)
                .enqueue(new Callback<ResponseBody>() {
                    @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
                    @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private void abrirChatPrivado(int idRemitente, String nombreRemitente) {
        RetrofitClient.getChatApiServices()
                .crearChatPrivado(currentUserId, idRemitente)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        PrivateChatHistoryStore.touchChat(HomeActivity.this, currentUserId, idRemitente, nombreRemitente);
                        Intent intent = new Intent(HomeActivity.this, PrivateChatActivity.class);
                        intent.putExtra("CURRENT_USER_ID", currentUserId);
                        intent.putExtra("OTHER_USER_ID", idRemitente);
                        intent.putExtra("OTHER_USER_NAME", nombreRemitente);
                        startActivity(intent);
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(HomeActivity.this, "Error al abrir chat", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 3000) {
            boolean camaraAceptada = false;
            boolean ubicacionAceptada = false;

            // Revisamos qué ha respondido a cada cosa
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(android.Manifest.permission.CAMERA) && grantResults[i] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    camaraAceptada = true;
                }
                if (permissions[i].equals(android.Manifest.permission.ACCESS_FINE_LOCATION) && grantResults[i] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    ubicacionAceptada = true;
                }
            }

            // Si ha aceptado AMBOS, le dejamos pasar
            if (camaraAceptada && ubicacionAceptada) {
                startActivityForResult(new Intent(this, ScannerActivity.class), SCAN_QR_REQUEST_CODE);
            } else {
                // Si ha rechazado alguno, bloqueamos el acceso
                Toast.makeText(this, "Para acceder al escáner necesitas aceptar los permisos de Cámara y Ubicación", Toast.LENGTH_LONG).show();
            }
        }
    }

}
