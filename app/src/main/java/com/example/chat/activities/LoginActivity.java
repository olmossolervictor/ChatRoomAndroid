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
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        if (json.getString("status").equals("success")) {
                            int idUsuario = json.getInt("id_usuario");
                            String nombre = json.getString("nombre");
                            getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit()
                                    .putInt("id_usuario", idUsuario)
                                    .putString("nombre", nombre)
                                    .apply();
                            startActivity(new Intent(LoginActivity.this, HomeActivity.class));
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
