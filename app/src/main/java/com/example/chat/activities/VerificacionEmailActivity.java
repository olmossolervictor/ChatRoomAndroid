package com.example.chat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.material.snackbar.Snackbar;

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

public class VerificacionEmailActivity extends BaseActivity {

    private String verificacionEmail = "";
    private EditText[] digitFields = new EditText[6];
    private Button btnVerificarCodigo, btnVerificarGoogle;
    private TextView textEmail, textReenviarCodigo;
    private TextView tvGeneralError;
    private ChatApiServices api;
    private CredentialManager credentialManager;
    private Executor mainExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verificacion_email);

        verificacionEmail = getIntent().getStringExtra("VERIFICACION_EMAIL");
        if (TextUtils.isEmpty(verificacionEmail)) {
            Toast.makeText(this, "Error: email no especificado", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        api = RetrofitClient.getChatApiServices();
        credentialManager = CredentialManager.create(this);
        mainExecutor = ContextCompat.getMainExecutor(this);

        textEmail = findViewById(R.id.textVerificacionEmail);
        textEmail.setText(String.format(getString(R.string.verificacion_subtitulo), verificacionEmail));

        tvGeneralError = findViewById(R.id.tvGeneralError);

        digitFields[0] = findViewById(R.id.digit1);
        digitFields[1] = findViewById(R.id.digit2);
        digitFields[2] = findViewById(R.id.digit3);
        digitFields[3] = findViewById(R.id.digit4);
        digitFields[4] = findViewById(R.id.digit5);
        digitFields[5] = findViewById(R.id.digit6);

        configurarDigitFields();

        btnVerificarCodigo = findViewById(R.id.btnVerificarCodigo);
        btnVerificarGoogle = findViewById(R.id.btnVerificarGoogle);
        textReenviarCodigo = findViewById(R.id.textReenviarCodigo);

        configurarEstadoGoogle();

        btnVerificarCodigo.setOnClickListener(v -> verificarCodigoIngresado());
        btnVerificarGoogle.setOnClickListener(v -> loginConGoogle());
        textReenviarCodigo.setOnClickListener(v -> reenviarCodigo());
    }

    private void mostrarErrorGeneral(String mensaje) {
        if (tvGeneralError != null) {
            tvGeneralError.setText(mensaje);
            tvGeneralError.setVisibility(View.VISIBLE);
        }
    }

    private void limpiarErrorGeneral() {
        if (tvGeneralError != null && tvGeneralError.getVisibility() == View.VISIBLE) {
            tvGeneralError.setVisibility(View.GONE);
        }
    }

    private void configurarDigitFields() {
        for (int i = 0; i < 6; i++) {
            final int index = i;
            EditText field = digitFields[i];

            field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            field.setAutofillHints("");

            field.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    EditText currentField = (EditText) v;
                    if (currentField.getText().length() == 0 && index > 0) {
                        digitFields[index - 1].requestFocus();
                        digitFields[index - 1].setText("");
                        limpiarErrorGeneral();
                    }
                    return false;
                }
                return false;
            });

            field.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    limpiarErrorGeneral();

                    if (s.length() == 1 && index < 5) {
                        digitFields[index + 1].requestFocus();
                    } else if (s.length() == 0 && before == 1 && index > 0) {
                        digitFields[index - 1].requestFocus();
                    }
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    private String obtenerCodigoCompleto() {
        StringBuilder codigo = new StringBuilder();
        for (EditText field : digitFields) {
            codigo.append(field.getText().toString());
        }
        return codigo.toString();
    }

    private void verificarCodigoIngresado() {
        String codigo = obtenerCodigoCompleto();

        if (codigo.length() != 6) {
            mostrarErrorGeneral("Por favor, rellena todos los huecos del código.");
            return;
        }

        if (!codigo.matches("\\d{6}")) {
            mostrarErrorGeneral("El código debe contener solo números.");
            return;
        }

        api.verificarCodigo(verificacionEmail, codigo)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Snackbar.make(findViewById(android.R.id.content), R.string.verificacion_exitosa, Snackbar.LENGTH_LONG).show();
                            irALogin();
                        } else {
                            mostrarErrorGeneral("El código introducido es incorrecto o ha expirado.");
                            limpiarCodigo();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(VerificacionEmailActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void limpiarCodigo() {
        for (EditText field : digitFields) {
            field.setText("");
        }
        digitFields[0].requestFocus();
    }

    private void configurarEstadoGoogle() {
        String webClientId = getString(R.string.google_web_client_id);
        boolean googleDisponible = !TextUtils.isEmpty(webClientId);
        btnVerificarGoogle.setEnabled(googleDisponible);
        if (!googleDisponible) {
            btnVerificarGoogle.setAlpha(0.6f);
            btnVerificarGoogle.setText(getString(R.string.google_not_configured));
        }
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
                        Toast.makeText(VerificacionEmailActivity.this, "Error con Google: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void procesarCredencialGoogle(GetCredentialResponse result) {
        Credential credential = result.getCredential();
        if (!(credential instanceof CustomCredential)) {
            mostrarErrorGeneral("Credencial de Google inválida");
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
            mostrarErrorGeneral("Tipo de credencial no soportado");
            return;
        }

        try {
            GoogleIdTokenCredential googleCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
            api.verificarConGoogle(googleCredential.getIdToken()).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        Snackbar.make(findViewById(android.R.id.content), R.string.verificacion_exitosa, Snackbar.LENGTH_LONG).show();
                        irALogin();
                    } else {
                        try {
                            String errorBody = response.errorBody() != null ? response.errorBody().string() : "";
                            mostrarErrorGeneral("Verificación fallida. Inténtalo de nuevo.");
                        } catch (Exception ignored) {}
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(VerificacionEmailActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error al procesar token de Google: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void reenviarCodigo() {
        api.reenviarVerificacionEmail(verificacionEmail).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(VerificacionEmailActivity.this, "Te hemos reenviado un nuevo código", Toast.LENGTH_LONG).show();
                    limpiarCodigo();
                    limpiarErrorGeneral();
                } else {
                    mostrarErrorGeneral("Error al reenviar código. Inténtalo más tarde.");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(VerificacionEmailActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void irALogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("PREFILL_EMAIL", verificacionEmail);
        startActivity(intent);
        finish();
    }
}