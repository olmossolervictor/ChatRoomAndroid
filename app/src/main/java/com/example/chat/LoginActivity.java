package com.example.chat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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

        textGoToRegister.setOnClickListener(v -> {
            // Primero mostramos los términos
            mostrarTerminosAntesDeRegistro();
        });
    }

    private void mostrarTerminosAntesDeRegistro() {
        // Creamos un TextView para el texto largo
        TextView textView = new TextView(this);
        textView.setText(getString(R.string.terminos_legales));
        textView.setPadding(50, 40, 50, 40);
        textView.setTextSize(14);
        textView.setTextColor(ContextCompat.getColor(this, android.R.color.black));

        // Lo metemos en un ScrollView para que se pueda mover
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView);

        new AlertDialog.Builder(this)
                .setTitle("TÉRMINOS Y CONDICIONES LEGALES")
                .setView(scrollView) // Establecemos el scrollview como vista del diálogo
                .setCancelable(false)
                .setNegativeButton("Rechazar", (dialog, which) -> {
                    Toast.makeText(this, "Debes aceptar los términos para registrarte", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setPositiveButton("Aceptar y Continuar", (dialog, which) -> {
                    Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                    startActivity(intent);
                })
                .show();
    }

    private void login() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.loginUsuario(email, password);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String result = response.body().string();
                        JSONObject json = new JSONObject(result);

                        if (json.getString("status").equals("success")) {
                            int idUsuario = json.getInt("id_usuario");
                            String nombre = json.getString("nombre");

                            SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
                            pref.edit().putInt("id_usuario", idUsuario).putString("nombre", nombre).apply();

                            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, json.getString("message"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
