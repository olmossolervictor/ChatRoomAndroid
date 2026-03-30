package com.example.chat.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.core.content.ContextCompat;

import com.example.chat.R;
import com.example.chat.network.ChatApiServices;
import com.example.chat.network.RetrofitClient;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

import java.util.concurrent.Executor;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private Button btnLogin, btnGoogleLogin, textResendVerification, btnVerificarCorreoLogin;
    private TextView textGoToRegister;

    private ChatApiServices api;
    private CredentialManager credentialManager;
    private Executor mainExecutor;
    private String pendingVerificationEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Aplicar modo oscuro guardado antes de inflar la vista
        boolean modoOscuro = getSharedPreferences("AjustesPrefs", MODE_PRIVATE).getBoolean("modo_oscuro", false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(modoOscuro
                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        if (pref.getInt("id_usuario", -1) != -1) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        api = RetrofitClient.getChatApiServices();
        credentialManager = CredentialManager.create(this);
        mainExecutor = ContextCompat.getMainExecutor(this);

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        textGoToRegister = findViewById(R.id.textGoToRegister);
        textResendVerification = findViewById(R.id.textResendVerification);
        btnVerificarCorreoLogin = findViewById(R.id.btnVerificarCorreoLogin);

        String prefillEmail = getIntent().getStringExtra("PREFILL_EMAIL");
        boolean showVerificationHint = getIntent().getBooleanExtra("SHOW_VERIFICATION_HINT", false);
        if (!TextUtils.isEmpty(prefillEmail)) {
            editEmail.setText(prefillEmail);
            pendingVerificationEmail = prefillEmail;
        }
        if (showVerificationHint) {
            Toast.makeText(this, R.string.register_success_check_email, Toast.LENGTH_LONG).show();
        }

        configurarEstadoGoogle();

        btnLogin.setOnClickListener(v -> login());

        btnGoogleLogin.setOnClickListener(v -> loginConGoogle());
        textResendVerification.setOnClickListener(v -> reenviarVerificacion());
        btnVerificarCorreoLogin.setOnClickListener(v -> mostrarDialogoVerificarCorreo());
        textGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    // =========================================================
    // 📜 ALERTA DE TÉRMINOS CON SCROLL OBLIGATORIO
    // =========================================================

    // =========================================================

    private void configurarEstadoGoogle() {
        String webClientId = getString(R.string.google_web_client_id);
        boolean googleDisponible = !TextUtils.isEmpty(webClientId);
        btnGoogleLogin.setEnabled(googleDisponible);
        if (!googleDisponible) {
            btnGoogleLogin.setAlpha(0.6f);
            btnGoogleLogin.setText(getString(R.string.google_not_configured));
        }
    }

    private void login() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingVerificationEmail = email;
        textResendVerification.setVisibility(TextView.GONE);

        api.loginUsuario(email, password).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                manejarRespuestaLogin(response, email, "password");
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loginConGoogle() {
        String webClientId = getString(R.string.google_web_client_id);
        if (TextUtils.isEmpty(webClientId)) {
            Toast.makeText(this, R.string.google_not_configured, Toast.LENGTH_LONG).show();
            return;
        }

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                null,
                mainExecutor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        procesarCredencialGoogle(result);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Toast.makeText(LoginActivity.this, "No se pudo iniciar con Google: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void procesarCredencialGoogle(GetCredentialResponse result) {
        Credential credential = result.getCredential();
        if (!(credential instanceof CustomCredential)) {
            Toast.makeText(this, "No se recibió una credencial válida de Google", Toast.LENGTH_SHORT).show();
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
            Toast.makeText(this, "El proveedor devolvió un tipo de credencial no soportado", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            GoogleIdTokenCredential googleCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
            api.loginConGoogle(googleCredential.getIdToken()).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    manejarRespuestaLogin(response, googleCredential.getId(), "google");
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo interpretar el token de Google", Toast.LENGTH_LONG).show();
        }
    }

    private void manejarRespuestaLogin(Response<ResponseBody> response, String email, String provider) {
        try {
            String bodyStr = response.isSuccessful() && response.body() != null
                    ? response.body().string()
                    : (response.errorBody() != null ? response.errorBody().string() : "");

            if (!response.isSuccessful()) {
                if (response.code() == 403 || contieneEmailNoVerificado(bodyStr)) {
                    mostrarEstadoNoVerificado(email);
                    return;
                }
                Toast.makeText(this, "Error del servidor (" + response.code() + "): " + bodyStr, Toast.LENGTH_LONG).show();
                return;
            }

            if (bodyStr.isEmpty()) {
                Toast.makeText(this, "Respuesta vacía del servidor", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject json = new JSONObject(bodyStr);
            String status = json.optString("status");

            if ("success".equalsIgnoreCase(status)) {
                guardarSesionYEntrar(json, provider);
                return;
            }

            if ("email_not_verified".equalsIgnoreCase(status)
                    || !json.optBoolean("email_verificado", true)
                    || contieneEmailNoVerificado(json.optString("message"))) {
                mostrarEstadoNoVerificado(email);
                return;
            }

            Toast.makeText(this, json.optString("message", "Credenciales incorrectas"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al procesar respuesta: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void guardarSesionYEntrar(JSONObject json, String provider) throws Exception {
        int idUsuario = json.getInt("id_usuario");
        String nombre = json.getString("nombre");
        String rol = json.optString("rol", "usuario").toLowerCase();

        getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit()
                .putInt("id_usuario", idUsuario)
                .putString("nombre", nombre)
                .putString("rol", rol)
                .putString("auth_provider", provider)
                .apply();

        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void mostrarEstadoNoVerificado(String email) {
        pendingVerificationEmail = email;
        Toast.makeText(this, R.string.email_not_verified, Toast.LENGTH_LONG).show();
    }

    private boolean contieneEmailNoVerificado(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase();
        return normalized.contains("email_not_verified")
                || normalized.contains("correo no verificado")
                || normalized.contains("email no verificado")
                || normalized.contains("not verified");
    }

    private void mostrarDialogoVerificarCorreo() {
        android.widget.EditText inputEmail = new android.widget.EditText(this);
        inputEmail.setHint("Correo electrónico");
        inputEmail.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        inputEmail.setPadding(48, 32, 48, 32);

        String emailActual = editEmail.getText().toString().trim();
        if (!emailActual.isEmpty()) {
            inputEmail.setText(emailActual);
            inputEmail.setSelection(emailActual.length());
        }

        new AlertDialog.Builder(this)
                .setTitle("Verificar correo")
                .setMessage("Introduce el correo al que enviar el código de verificación.")
                .setView(inputEmail)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String email = inputEmail.getText().toString().trim();
                    if (email.isEmpty()) {
                        Toast.makeText(this, "Introduce un correo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    enviarCodigoVerificacion(email);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enviarCodigoVerificacion(String email) {
        api.reenviarVerificacionEmail(email).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.isSuccessful() && response.body() != null
                            ? response.body().string()
                            : (response.errorBody() != null ? response.errorBody().string() : "");

                    if (esEmailYaVerificado(body)) {
                        Toast.makeText(LoginActivity.this, "Este correo ya está verificado", Toast.LENGTH_SHORT).show();
                    } else if (response.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Código de verificación enviado a " + email, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginActivity.this, "No se encontró ese correo o no se pudo enviar", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(LoginActivity.this, "Error al procesar la respuesta", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean esEmailYaVerificado(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        return lower.contains("already_verified")
                || lower.contains("ya verificado")
                || lower.contains("ya está verificado")
                || lower.contains("email_verified");
    }

    private void reenviarVerificacion() {
        String email = editEmail.getText().toString().trim();
        if (email.isEmpty()) {
            email = pendingVerificationEmail;
        }

        if (email.isEmpty()) {
            Toast.makeText(this, "Introduce tu correo para reenviar la verificación", Toast.LENGTH_SHORT).show();
            return;
        }

        api.reenviarVerificacionEmail(email).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, "Te hemos enviado un nuevo correo de verificación", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LoginActivity.this, "No se pudo reenviar la verificación", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
