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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.chat.R;
import com.example.chat.network.ChatApiServices;
import com.example.chat.network.RetrofitClient;
import com.example.chat.utils.AlertHelper;
import com.example.chat.utils.AlertHelper.AlertType;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

import java.util.concurrent.Executor;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends BaseActivity {

    // Componentes de la interfaz de usuario
    private EditText editEmail, editPassword;
    private Button btnLogin, btnGoogleLogin, textResendVerification, btnVerificarCorreoLogin;
    private TextView textGoToRegister;

    // Servicios de red y gestión de credenciales
    private ChatApiServices api;
    private CredentialManager credentialManager;
    private Executor mainExecutor;

    // Variables de estado temporal
    private String pendingVerificationEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Verificación de sesión activa para redirección automática
        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        if (pref.getInt("id_usuario", -1) != -1) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // Inicialización de servicios y componentes
        api = RetrofitClient.getChatApiServices();
        credentialManager = CredentialManager.create(this);
        mainExecutor = ContextCompat.getMainExecutor(this);

        inicializarVistas();
        configurarListeners();
        configurarValidacionDinamica();
        configurarEstadoGoogle();
    }

    /**
     * Vincula las variables con los componentes del layout XML.
     */
    private void inicializarVistas() {
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        textGoToRegister = findViewById(R.id.textGoToRegister);
        textResendVerification = findViewById(R.id.textResendVerification);
        btnVerificarCorreoLogin = findViewById(R.id.btnVerificarCorreoLogin);
    }

    /**
     * Configura los eventos de click para los elementos interactivos.
     */
    private void configurarListeners() {
        // Carga de datos previos si existen en el Intent
        String prefillEmail = getIntent().getStringExtra("PREFILL_EMAIL");
        boolean showVerificationHint = getIntent().getBooleanExtra("SHOW_VERIFICATION_HINT", false);

        if (!TextUtils.isEmpty(prefillEmail)) {
            if (editEmail != null) editEmail.setText(prefillEmail);
            pendingVerificationEmail = prefillEmail;
        }

        if (showVerificationHint) {
            AlertHelper.showActionAlert(btnLogin, getString(R.string.register_success_check_email), AlertType.SUCCESS);
        }

        if (btnLogin != null) btnLogin.setOnClickListener(v -> login());
        if (btnGoogleLogin != null) btnGoogleLogin.setOnClickListener(v -> loginConGoogle());
        if (textResendVerification != null) textResendVerification.setOnClickListener(v -> reenviarVerificacion());
        if (btnVerificarCorreoLogin != null) btnVerificarCorreoLogin.setOnClickListener(v -> mostrarDialogoVerificarCorreo());

        if (textGoToRegister != null) {
            textGoToRegister.setOnClickListener(v ->
                    startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        }
    }

    /**
     * Limpia los estados de error de forma reactiva mientras el usuario escribe.
     */
    private void configurarValidacionDinamica() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (editEmail != null && editEmail.getError() != null) editEmail.setError(null);
                if (editPassword != null && editPassword.getError() != null) editPassword.setError(null);
            }
        };

        if (editEmail != null) editEmail.addTextChangedListener(watcher);
        if (editPassword != null) editPassword.addTextChangedListener(watcher);

        if (btnLogin != null) {
            btnLogin.setEnabled(true);
            btnLogin.setAlpha(1.0f);
        }
    }

    /**
     * Habilita o deshabilita el acceso por Google según la configuración del cliente.
     */
    private void configurarEstadoGoogle() {
        String webClientId = getString(R.string.google_web_client_id);
        boolean googleDisponible = !TextUtils.isEmpty(webClientId);

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setEnabled(googleDisponible);
            if (!googleDisponible) {
                btnGoogleLogin.setAlpha(0.6f);
                btnGoogleLogin.setText(getString(R.string.google_not_configured));
            }
        boolean googleDisponible = !TextUtils.isEmpty(webClientId); // Si está vacío, es false
        btnGoogleLogin.setEnabled(googleDisponible);
        if (!googleDisponible) {
            btnGoogleLogin.setAlpha(0.6f);
            btnGoogleLogin.setText(getString(R.string.google_not_configured)); // Aquí se pone el "Próximamente"
        }
    }

    /**
     * Ejecuta el flujo de autenticación estándar por correo y contraseña.
     */
    private void login() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            AlertHelper.showActionAlert(btnLogin, "Por favor, rellene todos los campos para continuar", AlertType.WARNING);
            if (email.isEmpty()) editEmail.setError("Campo obligatorio");
            if (password.isEmpty()) editPassword.setError("Campo obligatorio");
            return;
        }

        pendingVerificationEmail = email;
        if (textResendVerification != null) textResendVerification.setVisibility(View.GONE);

        api.loginUsuario(email, password).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                manejarRespuestaLogin(response, email, "password");
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                AlertHelper.showActionAlert(btnLogin, "Fallo de conexión. Compruebe su red.", AlertType.ERROR);
            }
        });
    }

    private void loginConGoogle() {
        String webClientId = getString(R.string.google_web_client_id);
        if (TextUtils.isEmpty(webClientId)) {
            Toast.makeText(this, R.string.google_not_configured, Toast.LENGTH_LONG).show();
            return;
        }

        GetSignInWithGoogleOption googleIdOption = new GetSignInWithGoogleOption.Builder(webClientId)
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

        Intent intent;
        if ("google".equalsIgnoreCase(provider)) {
            intent = new Intent(this, RegisterActivity.class);
            intent.putExtra("MODO_EDICION", true);
            intent.putExtra("MODO_GOOGLE_COMPLETAR_PERFIL", true);
        } else {
            intent = new Intent(this, HomeActivity.class);
        }
        startActivity(intent);
        finish();
    }

    private void mostrarEstadoNoVerificado(String email) {
        pendingVerificationEmail = email;
        textResendVerification.setVisibility(View.VISIBLE);
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
                    AlertHelper.showActionAlert(btnLogin, "Hubo un problema al procesar los datos", AlertType.ERROR);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                AlertHelper.showActionAlert(btnLogin, "Sin conexión. Revisa tu red.", AlertType.ERROR);
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
