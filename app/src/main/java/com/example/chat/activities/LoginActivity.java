package com.example.chat.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.chat.R;
import com.example.chat.network.ChatApiServices;
import com.example.chat.network.RetrofitClient;

import org.json.JSONObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private Button btnLogin;
    private TextView textGoToRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        if (pref.getInt("id_usuario", -1) != -1) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        textGoToRegister = findViewById(R.id.textGoToRegister);

        btnLogin.setOnClickListener(v -> login());

        textGoToRegister.setOnClickListener(v -> mostrarAlertaTerminos());
    }

    // =========================================================
    // 📜 ALERTA DE TÉRMINOS CON SCROLL OBLIGATORIO
    // =========================================================
    private void mostrarAlertaTerminos() {
        // 1. Creamos nuestra propia vista con scroll
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(getString(R.string.terminos_legales));
        textView.setPadding(50, 40, 50, 40);
        textView.setTextSize(14);
        textView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        scrollView.addView(textView);

        // 2. Construimos la alerta pero NO la mostramos todavía (.create en vez de .show)
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Términos y Condiciones")
                .setView(scrollView) // Le metemos nuestro ScrollView
                .setCancelable(false)
                .setPositiveButton("Aceptar", (d, which) -> {
                    startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
                })
                .setNegativeButton("Rechazar", (d, which) -> {
                    d.dismiss();
                    Toast.makeText(LoginActivity.this, "Debes aceptar los términos para registrarte", Toast.LENGTH_SHORT).show();
                })
                .create();

        // 3. Mostramos el diálogo para que Android dibuje los botones
        dialog.show();

        // 4. Capturamos el botón "Aceptar" y lo bloqueamos por defecto
        Button btnAceptar = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        btnAceptar.setEnabled(false);

        // 5. Escuchamos el scroll del usuario
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            View view = scrollView.getChildAt(scrollView.getChildCount() - 1);
            // Calculamos la diferencia entre el fondo del texto y la pantalla actual
            int diff = (view.getBottom() - (scrollView.getHeight() + scrollView.getScrollY()));

            // Si la diferencia es 0 (hemos llegado al final), habilitamos el botón
            if (diff <= 0) {
                btnAceptar.setEnabled(true);
            }
        });

        // Caso especial: ¿Y si tu texto de términos es tan corto que no necesita hacer scroll?
        // Revisamos si cabe entero en la pantalla, y si es así, lo habilitamos de golpe.
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            View view = scrollView.getChildAt(scrollView.getChildCount() - 1);
            if (view.getBottom() <= scrollView.getHeight()) {
                btnAceptar.setEnabled(true);
            }
        });
    }
    // =========================================================

    private void login() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatApiServices api = RetrofitClient.getChatApiServices();
        api.loginUsuario(email, password).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String bodyStr = response.isSuccessful() && response.body() != null
                            ? response.body().string()
                            : (response.errorBody() != null ? response.errorBody().string() : "");
                    android.util.Log.d("LOGIN_DEBUG", "HTTP " + response.code() + ": " + bodyStr);

                    if (!response.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Error del servidor (" + response.code() + "): " + bodyStr, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (bodyStr.isEmpty()) {
                        Toast.makeText(LoginActivity.this, "Respuesta vacía del servidor", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JSONObject json = new JSONObject(bodyStr);
                    if (json.getString("status").equals("success")) {
                        int idUsuario = json.getInt("id_usuario");
                        String nombre = json.getString("nombre");
                        String rol = json.optString("rol", "usuario").toLowerCase();
                        getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit()
                                .putInt("id_usuario", idUsuario)
                                .putString("nombre", nombre)
                                .putString("rol", rol)
                                .apply();
                        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, json.optString("message", "Credenciales incorrectas"), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    android.util.Log.e("LOGIN_ERROR", "Excepción: " + e.getMessage());
                    Toast.makeText(LoginActivity.this, "Error al procesar respuesta: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}