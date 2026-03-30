package com.example.chat.activities;

import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.chat.R;
import com.example.chat.adapters.SalaAdapter;
import com.example.chat.models.Sala;
import com.example.chat.network.RetrofitClient;

import org.json.JSONArray;
import org.json.JSONObject;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private static final int SCAN_QR_REQUEST_CODE = 2000;

    private DrawerLayout drawerLayout;
    private ListView listSalas;
    private TextView textEmptySalas;
    private Button btnScanQR;
    private ImageButton btnMenuDrawer;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);

        drawerLayout = findViewById(R.id.drawerLayout);
        listSalas = findViewById(R.id.listSalas);
        textEmptySalas = findViewById(R.id.textEmptySalas);
        btnScanQR = findViewById(R.id.btnScanQRHome);
        btnMenuDrawer = findViewById(R.id.btnMenuDrawer);

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

        salaAdapter = new SalaAdapter(this, listaMisSalas);
        listSalas.setAdapter(salaAdapter);
        listSalas.setEmptyView(textEmptySalas);

        listSalas.setOnItemClickListener((parent, view, position, id) -> {
            Sala sala = listaMisSalas.get(position);
            abrirSala(sala.getIdSala() != null ? sala.getIdSala() : sala.getNombre());
        });

        btnMenuDrawer.setOnClickListener(v -> drawerLayout.openDrawer(
                androidx.core.view.GravityCompat.START));

        btnScanQR.setOnClickListener(v ->
                startActivityForResult(new Intent(this, ScannerActivity.class), SCAN_QR_REQUEST_CODE));

        drawerEditarPerfil.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra("MODO_EDICION", true);
            startActivity(intent);
        });

        // MAGIA AQUÍ: Redirige a la nueva pantalla de Ajustes
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
            mostrarNotificaciones();
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

                                // Priorizar nombre_usuario si está disponible
                                String displayName = !nombreUsuario.isEmpty() ? nombreUsuario : (nombre + " " + apellidos).trim();
                                textDrawerNombre.setText(displayName);

                                String foto = json.optString("foto", "");
                                if (!foto.isEmpty()) {
                                    byte[] decoded = Base64.decode(foto, Base64.DEFAULT);
                                    imgDrawerFoto.setImageBitmap(
                                            BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
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
                    }
                    @Override
                    public void onFailure(Call<List<Sala>> call, Throwable t) {}
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
        RetrofitClient.getChatApiServices().getRolUsuario(currentUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                int idRol = json.optInt("id_rol", 3);
                                String nombreRol = json.optString("rol", "usuario").toLowerCase();
                                getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit()
                                        .putString("rol", nombreRol).apply();
                                if (idRol == 1 || "owner".equals(nombreRol)) {
                                    drawerGestionUsuarios.setVisibility(View.VISIBLE);
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }
    private void mostrarDialogoAjustes() {
        String[] opciones = {
                "Notificaciones",
                "Privacidad",
                "Almacenamiento en caché",
                "Acerca de la aplicación"
        };

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Ajustes");
        builder.setItems(opciones, (dialog, which) -> {
            switch (which) {
                case 0:
                    Toast.makeText(this, "Notificaciones — próximamente", Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    Toast.makeText(this, "Privacidad — próximamente", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    Toast.makeText(this, "Almacenamiento en caché — próximamente", Toast.LENGTH_SHORT).show();
                    break;
                case 3:
                    Toast.makeText(this, "v1.0 - ChatRoom App", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
        builder.show();
    }

    private void mostrarDialogoTerminos() {
        String terminosTexto = "TÉRMINOS Y CONDICIONES\n\n" +
                "1. Uso del Servicio\n" +
                "Al utilizar esta aplicación, aceptas estos términos y condiciones. Si no estás de acuerdo, no uses la aplicación.\n\n" +
                "2. Cuentas de Usuario\n" +
                "Eres responsable de mantener la confidencialidad de tu contraseña y de toda la actividad que ocurra bajo tu cuenta.\n\n" +
                "3. Contenido\n" +
                "No debes publicar contenido ofensivo, ilegal o que infrinja derechos de terceros.\n\n" +
                "4. Privacidad\n" +
                "Tu información personal será tratada según nuestra política de privacidad.\n\n" +
                "5. Limitación de Responsabilidad\n" +
                "No somos responsables por daños indirectos o pérdida de datos.\n\n" +
                "6. Cambios en los Términos\n" +
                "Podemos modificar estos términos en cualquier momento. El uso continuado de la aplicación implica aceptación de los cambios.\n\n" +
                "7. Terminación\n" +
                "Podemos suspender tu cuenta por violación de estos términos.\n\n" +
                "8. Ley Aplicable\n" +
                "Estos términos se rigen por la ley aplicable en tu país.";

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Términos y Condiciones");
        builder.setMessage(terminosTexto);
        builder.setPositiveButton("Aceptar", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void actualizarBadgeNotificaciones() {
        RetrofitClient.getChatApiServices()
                .obtenerNotificacionesNoLeidas(currentUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONArray array = new JSONArray(response.body().string());
                                int count = array.length();
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
                .obtenerNotificaciones(currentUserId)
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

                                // Construir listas paralelas: texto visible e info de navegación
                                List<String> items = new ArrayList<>();
                                List<Integer> remitenteIds = new ArrayList<>();
                                List<String> remitenteNombres = new ArrayList<>();
                                List<Integer> notifIds = new ArrayList<>();

                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject n = array.getJSONObject(i);
                                    String tipo = n.optString("tipo_notificacion", "");
                                    String contenido = n.optString("contenido", "");
                                    boolean leida = n.optBoolean("leida", false);
                                    String nombreRemitente = n.optString("nombre_remitente", "Alguien");
                                    int idRemitente = n.optInt("id_usuario_remitente", -1);
                                    int idNotif = n.optInt("id_notificacion", -1);
                                    String prefijo = leida ? "" : "● ";

                                    if ("mensaje_privado".equals(tipo)) {
                                        items.add(prefijo + nombreRemitente + ": " + contenido);
                                    } else {
                                        items.add(prefijo + contenido);
                                    }
                                    remitenteIds.add(idRemitente);
                                    remitenteNombres.add(nombreRemitente);
                                    notifIds.add(idNotif);
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
                                    int idNotif = notifIds.get(position);

                                    // Marcar como leída
                                    if (idNotif != -1) {
                                        RetrofitClient.getChatApiServices()
                                                .marcarNotificacionComoLeida(idNotif)
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

}