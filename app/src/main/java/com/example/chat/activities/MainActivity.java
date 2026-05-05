package com.example.chat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chat.R;
import com.example.chat.adapters.MensajeAdapter;
import com.example.chat.models.Mensaje;
import com.example.chat.network.ChatApiServices;
import com.example.chat.network.RetrofitClient;
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

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private androidx.drawerlayout.widget.DrawerLayout drawerLayoutMain;
    private EditText editMessage;
    private Button btnSend;
    private ListView listMessages;
    private LinearLayout layoutInputMessage;

    private boolean isAdmin = false;
    private TextView textTiempoRestante;

    private MensajeAdapter adapter;
    private List<Mensaje> listaMensajes = new ArrayList<>();

    private Handler handler = new Handler();
    private Runnable refreshRunnable;

    private FusedLocationProviderClient fusedLocationClient;

    private int currentUserId;
    private String currentSalaId;

    // Datos de geovalla de la sala (0 = sin restricción)
    private double salaLatitud = 0;
    private double salaLongitud = 0;
    private double salaRadioMetros = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);
        String rol = pref.getString("rol", "usuario");
        isAdmin = "admin".equals(rol);

        if (currentUserId == -1) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentSalaId = getIntent().getStringExtra("ID_SALA_QR");
        if (currentSalaId == null || currentSalaId.isEmpty()) {
            Toast.makeText(this, "Error: No se especificó sala", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        salaLatitud = getIntent().getDoubleExtra("SALA_LATITUD", 0);
        salaLongitud = getIntent().getDoubleExtra("SALA_LONGITUD", 0);
        salaRadioMetros = getIntent().getDoubleExtra("SALA_RADIO", 0);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (getSharedPreferences("AjustesPrefs", MODE_PRIVATE).getBoolean("mantener_pantalla", false)) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);

            if (listMessages != null && adapter != null && adapter.getCount() > 0) {
                listMessages.postDelayed(() -> listMessages.setSelection(adapter.getCount() - 1), 100);
            }
            return insets;
        });

        drawerLayoutMain = findViewById(R.id.drawerLayoutMain);
        android.widget.ImageButton btnMenuDrawerMain = findViewById(R.id.btnMenuDrawerMain);
        TextView drawerVolverInicio = findViewById(R.id.drawerVolverInicio);
        TextView drawerSalirSala = findViewById(R.id.drawerSalirSala);

        btnMenuDrawerMain.setOnClickListener(v -> drawerLayoutMain.openDrawer(androidx.core.view.GravityCompat.START));

        drawerVolverInicio.setOnClickListener(v -> finish());

        drawerSalirSala.setOnClickListener(v -> salirDeSalaManualmente());

        editMessage = findViewById(R.id.editMessage);
        btnSend = findViewById(R.id.btnSend);
        listMessages = findViewById(R.id.listMessages);
        layoutInputMessage = findViewById(R.id.layoutInputMessage);
        textTiempoRestante = findViewById(R.id.textTiempoRestante);

        // --- INICIALIZACIÓN DEL ADAPTER CON EL NUEVO CLIC ---
        adapter = new MensajeAdapter(this, listaMensajes);

        adapter.setOnNombreClickListener(msg -> {
            if (msg.getIdUsuario() != currentUserId) {
                if (isAdmin) {
                    mostrarDialogoExpulsion(msg.getIdUsuario(), msg.getNombre());
                } else {
                    mostrarPerfilUsuario(msg.getIdUsuario(), msg.getNombre());
                }
            }
        });

        listMessages.setAdapter(adapter);

        // --- CONFIGURACIÓN DE VISTAS SEGÚN ROL ---
        if (isAdmin) {
            layoutInputMessage.setVisibility(View.GONE);
        } else {
            btnSend.setOnClickListener(v -> {
                String mensaje = editMessage.getText().toString().trim();
                if (!mensaje.isEmpty()) {
                    obtenerUbicacionYEnviar(mensaje);
                }
            });
        }

        unirseASalaEnServidor(currentSalaId);
        obtenerMensajes();
        iniciarAutoRefresco();
        solicitarPermisoUbicacionSiNecesario();
    }

    private void solicitarPermisoUbicacionSiNecesario() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void salirDeSalaManualmente() {
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }

        Toast.makeText(this, "Saliendo y borrando datos...", Toast.LENGTH_SHORT).show();

        RetrofitClient.getChatApiServices().salirDeSala(currentUserId, currentSalaId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        finish();
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        finish();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                if (salaLatitud != 0 || salaLongitud != 0) {
                    Toast.makeText(this,
                            "Sin permiso de ubicación no se puede verificar si estás dentro del área de la sala",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void iniciarAutoRefresco() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                obtenerMensajes();
                verificarTiempoSesion();
                verificarUbicacion();
                handler.postDelayed(this, 60000);
            }
        };
        handler.postDelayed(refreshRunnable, 60000);
    }

    private void verificarTiempoSesion() {
        RetrofitClient.getChatApiServices()
                .verificarSesionSala(currentUserId, currentSalaId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                if (json.has("expirado") && json.getBoolean("expirado")) {
                                    String motivo = json.optString("motivo", "");
                                    String msg = motivo.isEmpty()
                                            ? "Tu tiempo en la sala ha terminado"
                                            : "Has sido expulsado de la sala por: " + motivo;
                                    expulsarUsuario(msg);
                                    return;
                                }
                                long minutos = json.optLong("minutos_restantes", -1);
                                actualizarTimerSesion(minutos);
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private void actualizarTimerSesion(long minutos) {
        if (minutos < 0) {
            textTiempoRestante.setVisibility(View.GONE);
            return;
        }
        textTiempoRestante.setVisibility(View.VISIBLE);

        long horas = minutos / 60;
        long mins = minutos % 60;
        String texto = horas > 0
                ? String.format("Tiempo restante: %dh %02dm", horas, mins)
                : String.format("Tiempo restante: %dm", mins);
        textTiempoRestante.setText(texto);

        if (minutos >= 60) {
            textTiempoRestante.setBackgroundColor(Color.parseColor("#388E3C"));
        } else if (minutos >= 30) {
            textTiempoRestante.setBackgroundColor(Color.parseColor("#F57C00"));
        } else {
            textTiempoRestante.setBackgroundColor(Color.parseColor("#D32F2F"));
        }
    }

    private void verificarUbicacion() {
        if (salaLatitud == 0 && salaLongitud == 0) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        double radioEfectivo = salaRadioMetros > 0 ? salaRadioMetros : 100.0;

        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(5000)
                .build();

        fusedLocationClient.getCurrentLocation(request, null)
                .addOnSuccessListener(this, location -> {
                    if (location == null) return;

                    float[] resultado = new float[1];
                    Location.distanceBetween(
                            location.getLatitude(), location.getLongitude(),
                            salaLatitud, salaLongitud,
                            resultado
                    );

                    float distanciaMetros = resultado[0];
                    if (distanciaMetros > radioEfectivo) {
                        expulsarUsuario("Has salido del área de la sala");
                    }
                });
    }

    private void expulsarUsuario(String motivo) {
        handler.removeCallbacks(refreshRunnable);
        Toast.makeText(this, motivo.toUpperCase(), Toast.LENGTH_LONG).show();
        RetrofitClient.getChatApiServices().salirDeSala(currentUserId, currentSalaId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
                    @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
        finish();
    }

    private void obtenerMensajes() {
        RetrofitClient.getChatApiServices()
                .getMensajesGrupal(currentSalaId)
                .enqueue(new Callback<List<Mensaje>>() {
                    @Override
                    public void onResponse(Call<List<Mensaje>> call, Response<List<Mensaje>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            int index = listMessages.getFirstVisiblePosition();
                            View v = listMessages.getChildAt(0);
                            int top = (v == null) ? 0 : (v.getTop() - listMessages.getPaddingTop());

                            boolean estabaAbajoDelTodo = false;
                            if (listMessages.getLastVisiblePosition() >= adapter.getCount() - 1) {
                                estabaAbajoDelTodo = true;
                            }

                            listaMensajes.clear();
                            listaMensajes.addAll(response.body());
                            adapter.notifyDataSetChanged();

                            if (estabaAbajoDelTodo) {
                                listMessages.setSelection(adapter.getCount() - 1);
                            } else {
                                listMessages.setSelectionFromTop(index, top);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Mensaje>> call, Throwable t) {}
                });
    }

    private void obtenerUbicacionYEnviar(String mensaje) {
        enviarMensajeAlServidor(mensaje);
    }
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                v.getGlobalVisibleRect(outRect);
                // Si el toque es FUERA de la caja de texto, ocultamos el teclado
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    v.clearFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
    private void enviarMensajeAlServidor(String mensajeLimpio) {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        btnSend.setEnabled(false);

        api.enviarMensajeGrupal(currentSalaId.trim(), currentUserId, mensajeLimpio)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        btnSend.setEnabled(true);

                        if (response.isSuccessful()) {
                            editMessage.setText("");
                            obtenerMensajes();
                        } else {
                            Toast.makeText(MainActivity.this, "Error " + response.code() + " del servidor", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        btnSend.setEnabled(true);
                        Toast.makeText(MainActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void mostrarPerfilUsuario(int otherUserId, String otherUserName) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_profile, null);
        ImageView imgFoto = dialogView.findViewById(R.id.dialogImgFoto);
        TextView textNombre = dialogView.findViewById(R.id.dialogTextNombre);
        TextView textApellidos = dialogView.findViewById(R.id.dialogTextApellidos);
        Button btnChatPrivado = dialogView.findViewById(R.id.dialogBtnChatPrivado);
        Button btnDenunciar = dialogView.findViewById(R.id.dialogBtnDenunciar);

        textNombre.setText(otherUserName);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        RetrofitClient.getChatApiServices().getUsuario(otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(response.body().string());
                                String apellidos = json.optString("apellidos", "");
                                textApellidos.setText(apellidos);
                                String foto = json.optString("foto", "");
                                if (!foto.isEmpty()) {
                                    byte[] decoded = Base64.decode(foto, Base64.DEFAULT);
                                    imgFoto.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });

        btnChatPrivado.setOnClickListener(v -> {
            dialog.dismiss();
            abrirChatPrivado(otherUserId, otherUserName);
        });

        btnDenunciar.setOnClickListener(v -> {
            dialog.dismiss();
            mostrarDialogoDenuncias(otherUserId);
        });

        dialog.show();
    }

    private void abrirChatPrivado(int otherUserId, String otherUserName) {
        RetrofitClient.getChatApiServices()
                .crearChatPrivado(currentUserId, otherUserId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String bodyStr = response.body().string();
                                JSONObject json = new JSONObject(bodyStr);
                                if (!json.has("id_chat_privado")) {
                                    Toast.makeText(MainActivity.this, "Respuesta inesperada: " + bodyStr, Toast.LENGTH_LONG).show();
                                    return;
                                }
                                int idChatPrivado = json.getInt("id_chat_privado");
                                Intent intent = new Intent(MainActivity.this, PrivateChatActivity.class);
                                intent.putExtra("ID_CHAT_PRIVADO", idChatPrivado);
                                intent.putExtra("CURRENT_USER_ID", currentUserId);
                                intent.putExtra("OTHER_USER_ID", otherUserId);
                                intent.putExtra("OTHER_USER_NAME", otherUserName);
                                startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "Error al parsear respuesta: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        } else {
                            try {
                                String errorBody = response.errorBody() != null ? response.errorBody().string() : "sin detalle";
                                Toast.makeText(MainActivity.this, "Error " + response.code() + ": " + errorBody, Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "Error " + response.code(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(MainActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void unirseASalaEnServidor(String idSala) {
        RetrofitClient.getChatApiServices()
                .unirseASala(currentUserId, idSala)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {}
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
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

        LinearLayout layoutDenuncia = new LinearLayout(this);
        layoutDenuncia.setOrientation(LinearLayout.VERTICAL);
        layoutDenuncia.setPadding(16, 16, 16, 16);

        Spinner spinnerTipo = new Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, tiposDenuncia);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTipo.setAdapter(adapter);
        spinnerTipo.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                tipoDenunciaSeleccionado[0] = tiposDenuncia[position];
                if (position == 2) {
                    if (editRazon[0] == null) {
                        editRazon[0] = new EditText(MainActivity.this);
                        editRazon[0].setHint("Explica la razón de tu denuncia");
                        editRazon[0].setVisibility(View.VISIBLE);
                        layoutDenuncia.addView(editRazon[0]);
                    } else {
                        editRazon[0].setVisibility(View.VISIBLE);
                    }
                } else {
                    if (editRazon[0] != null) {
                        editRazon[0].setVisibility(View.GONE);
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
                Toast.makeText(MainActivity.this, "Selecciona un tipo de denuncia", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(MainActivity.this, "Denuncia registrada correctamente", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Error al registrar denuncia", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(MainActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void mostrarDialogoExpulsion(int idUsuarioExpulsado, String nombreUsuario) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);

        android.widget.EditText editMotivo = new android.widget.EditText(this);
        editMotivo.setHint("Motivo de la expulsión");
        editMotivo.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(editMotivo);

        String[] opciones = {"Permanente", "10 minutos", "30 minutos", "1 hora", "2 horas", "24 horas"};
        int[] minutos =      {0,            10,           30,           60,       120,       1440};

        android.widget.Spinner spinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, opciones);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        android.widget.LinearLayout.LayoutParams spinnerParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        spinnerParams.topMargin = 24;
        layout.addView(spinner, spinnerParams);

        new AlertDialog.Builder(this)
                .setTitle("Expulsar a " + nombreUsuario)
                .setView(layout)
                .setPositiveButton("Expulsar", (dialog, which) -> {
                    String motivo = editMotivo.getText().toString().trim();
                    if (motivo.isEmpty()) {
                        Toast.makeText(this, "Indica el motivo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int duracion = minutos[spinner.getSelectedItemPosition()];
                    RetrofitClient.getChatApiServices()
                            .expulsarDeChat(currentUserId, idUsuarioExpulsado, currentSalaId, motivo, duracion)
                            .enqueue(new Callback<ResponseBody>() {
                                @Override
                                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                    if (response.isSuccessful()) {
                                        String durStr = duracion == 0 ? "permanentemente" : "por " + opciones[spinner.getSelectedItemPosition()];
                                        Toast.makeText(MainActivity.this,
                                                nombreUsuario + " expulsado " + durStr, Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "Error al expulsar", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                @Override
                                public void onFailure(Call<ResponseBody> call, Throwable t) {
                                    Toast.makeText(MainActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }
}