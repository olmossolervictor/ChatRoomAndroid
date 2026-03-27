package com.example.chat.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chat.R;
import com.example.chat.network.RetrofitClient;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserManagementActivity extends AppCompatActivity {

    private EditText editBuscarEmail;
    private Button btnBuscar;
    private LinearLayout layoutUserInfo, layoutListaResultados;
    private TextView textNombreUser, textApellidosUser, textEmailUser,
            textTelefonoUser, textFechaNacUser, textRolActual, textNoEncontrado;
    private Button btnHacerAdmin, btnHacerUsuario;

    private int foundUserId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        editBuscarEmail = findViewById(R.id.editBuscarEmail);
        btnBuscar = findViewById(R.id.btnBuscar);
        layoutUserInfo = findViewById(R.id.layoutUserInfo);
        layoutListaResultados = findViewById(R.id.layoutListaResultados);
        textNombreUser = findViewById(R.id.textNombreUser);
        textApellidosUser = findViewById(R.id.textApellidosUser);
        textEmailUser = findViewById(R.id.textEmailUser);
        textTelefonoUser = findViewById(R.id.textTelefonoUser);
        textFechaNacUser = findViewById(R.id.textFechaNacUser);
        textRolActual = findViewById(R.id.textRolActual);
        textNoEncontrado = findViewById(R.id.textNoEncontrado);
        btnHacerAdmin = findViewById(R.id.btnHacerAdmin);
        btnHacerUsuario = findViewById(R.id.btnHacerUsuario);

        btnBuscar.setOnClickListener(v -> {
            ocultarTeclado();
            buscarUsuario();
        });

        editBuscarEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                ocultarTeclado();
                buscarUsuario();
                return true;
            }
            return false;
        });

        btnHacerAdmin.setOnClickListener(v -> cambiarRol("admin"));
        btnHacerUsuario.setOnClickListener(v -> cambiarRol("usuario"));
    }

    private void buscarUsuario() {
        String email = editBuscarEmail.getText().toString().trim();
        if (email.isEmpty()) {
            Toast.makeText(this, "Escribe un correo para buscar", Toast.LENGTH_SHORT).show();
            return;
        }

        layoutUserInfo.setVisibility(View.GONE);
        layoutListaResultados.setVisibility(View.GONE);
        layoutListaResultados.removeAllViews(); // Limpiar resultados anteriores
        textNoEncontrado.setVisibility(View.GONE);
        foundUserId = -1;

        RetrofitClient.getChatApiServices()
                .buscarUsuarioPorEmail(email)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String respuestaStr = response.body().string();
                                JSONArray jsonArray = new JSONArray(respuestaStr);

                                if (jsonArray.length() == 0) {
                                    textNoEncontrado.setVisibility(View.VISIBLE);
                                } else if (jsonArray.length() == 1) {
                                    // Solo hay uno, lo mostramos directamente
                                    JSONObject jsonUsuario = jsonArray.getJSONObject(0);
                                    mostrarUsuario(jsonUsuario);
                                } else {
                                    // Hay varios, mostramos la lista para que elija
                                    procesarMultiplesResultados(jsonArray);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                textNoEncontrado.setVisibility(View.VISIBLE);
                            }
                        } else {
                            textNoEncontrado.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(UserManagementActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void procesarMultiplesResultados(JSONArray jsonArray) {
        layoutListaResultados.setVisibility(View.VISIBLE);
        
        // Añadimos el título de nuevo (porque removeAllViews lo borró si estaba en el XML, 
        // pero mejor lo manejamos dinámicamente o dejamos el título fuera del contenedor que se limpia)
        TextView tvTitulo = new TextView(this);
        tvTitulo.setText("RESULTADOS ENCONTRADOS (" + jsonArray.length() + "):");
        tvTitulo.setTextSize(12);
        tvTitulo.setPadding(0, 0, 0, 16);
        layoutListaResultados.addView(tvTitulo);

        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject user = jsonArray.getJSONObject(i);
                String nombre = user.optString("nombre", "Sin nombre");
                String email = user.optString("email", "");

                // Crear un "botón" o vista para cada usuario
                TextView item = new TextView(this);
                item.setText(nombre + "\n" + email);
                item.setPadding(20, 20, 20, 20);
                item.setBackgroundResource(android.R.drawable.list_selector_background);
                item.setClickable(true);
                item.setFocusable(true);
                
                // Separación entre items
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, 10);
                item.setLayoutParams(params);

                item.setOnClickListener(v -> {
                    layoutListaResultados.setVisibility(View.GONE);
                    mostrarUsuario(user);
                });

                layoutListaResultados.addView(item);

                // Línea divisoria simple
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(Color.LTGRAY);
                layoutListaResultados.addView(divider);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void mostrarUsuario(JSONObject json) {
        try {
            foundUserId = json.optInt("id_usuario", -1);

            textNombreUser.setText("Nombre: " + json.optString("nombre", "-"));
            textApellidosUser.setText("Apellidos: " + json.optString("apellidos", "-"));
            textEmailUser.setText("Email: " + json.optString("email", "-"));
            textTelefonoUser.setText("Teléfono: " + json.optString("telefono", "-"));
            textFechaNacUser.setText("Fecha de nacimiento: " + json.optString("fechaNacimiento", "-"));

            String rol = json.optString("rol", "usuario");
            textRolActual.setText("Rol actual: " + rol.toUpperCase());

            // Mostrar solo el botón que tiene sentido (no el rol que ya tiene)
            btnHacerAdmin.setVisibility("admin".equals(rol) ? View.GONE : View.VISIBLE);
            btnHacerUsuario.setVisibility("usuario".equals(rol) ? View.GONE : View.VISIBLE);

            layoutUserInfo.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cambiarRol(String nuevoRol) {
        if (foundUserId == -1) return;

        RetrofitClient.getChatApiServices()
                .cambiarRolUsuario(foundUserId, nuevoRol)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(UserManagementActivity.this,
                                    "Rol cambiado a " + nuevoRol.toUpperCase(), Toast.LENGTH_SHORT).show();
                            textRolActual.setText("Rol actual: " + nuevoRol.toUpperCase());
                            btnHacerAdmin.setVisibility("admin".equals(nuevoRol) ? View.GONE : View.VISIBLE);
                            btnHacerUsuario.setVisibility("usuario".equals(nuevoRol) ? View.GONE : View.VISIBLE);
                        } else {
                            Toast.makeText(UserManagementActivity.this, "Error al cambiar el rol", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(UserManagementActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void ocultarTeclado() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(editBuscarEmail.getWindowToken(), 0);
    }
}
