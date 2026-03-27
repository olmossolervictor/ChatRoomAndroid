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
    private Button btnLogin, btnGoogleLogin, textResendVerification;
    private TextView textGoToRegister;

    private ChatApiServices api;
    private CredentialManager credentialManager;
    private Executor mainExecutor;
    private String pendingVerificationEmail = "";

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

        api = RetrofitClient.getChatApiServices();
        credentialManager = CredentialManager.create(this);
        mainExecutor = ContextCompat.getMainExecutor(this);

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        textGoToRegister = findViewById(R.id.textGoToRegister);
        textResendVerification = findViewById(R.id.textResendVerification);

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

        textGoToRegister.setOnClickListener(v -> mostrarAlertaTerminos());
        btnGoogleLogin.setOnClickListener(v -> loginConGoogle());
        textResendVerification.setOnClickListener(v -> reenviarVerificacion());
        textGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
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
