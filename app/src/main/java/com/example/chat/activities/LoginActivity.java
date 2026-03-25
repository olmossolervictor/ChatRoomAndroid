package com.example.chat.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
        textGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

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
