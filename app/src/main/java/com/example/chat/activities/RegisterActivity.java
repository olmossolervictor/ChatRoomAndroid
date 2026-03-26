package com.example.chat.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;

import com.example.chat.R;
import com.example.chat.network.ChatApiServices;
import com.example.chat.network.RetrofitClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText editNombre, editApellidos, editFechaNac, editEmail, editTelefono, editPassword;
    private TextView textEmailError;
    private ImageView imgUser;
    private Button btnSelectPhoto, btnRegister;
    private TextView textBackToLogin, textTitle;

    private static final int PICK_IMAGE_REQUEST = 1;
    private String encodedImage = "";
    private boolean isEditMode = false;
    private int currentUserId;
    private String fechaSeleccionada = "";
    private String emailOriginal = "";

    private final Handler debounceHandler = new Handler();
    private Runnable emailCheckRunnable;
    private boolean emailYaExiste = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        textTitle = findViewById(R.id.textTitle);
        editNombre = findViewById(R.id.editRegNombre);
        editApellidos = findViewById(R.id.editRegApellidos);
        editFechaNac = findViewById(R.id.editRegEdad);
        editEmail = findViewById(R.id.editRegEmail);
        textEmailError = findViewById(R.id.textEmailError);
        editTelefono = findViewById(R.id.editRegTelefono);
        editPassword = findViewById(R.id.editRegPassword);
        imgUser = findViewById(R.id.imgUser);
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnRegister = findViewById(R.id.btnRegister);
        textBackToLogin = findViewById(R.id.textBackToLogin);

        editFechaNac.setFocusable(false);
        editFechaNac.setClickable(true);
        editFechaNac.setOnClickListener(v -> mostrarCalendario());

        isEditMode = getIntent().getBooleanExtra("MODO_EDICION", false);
        if (isEditMode) setupEditMode();

        configurarTextWatcherEmail();

        btnSelectPhoto.setOnClickListener(v -> openGallery());
        btnRegister.setOnClickListener(v -> { if (isEditMode) actualizar(); else registrar(); });
        textBackToLogin.setOnClickListener(v -> finish());
    }

    private void mostrarCalendario() {
        Calendar c = Calendar.getInstance();
        // Máximo: hace 18 años
        Calendar maxCal = Calendar.getInstance();
        maxCal.add(Calendar.YEAR, -18);

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            String mesF = (month + 1) < 10 ? "0" + (month + 1) : String.valueOf(month + 1);
            String diaF = day < 10 ? "0" + day : String.valueOf(day);
            fechaSeleccionada = year + "-" + mesF + "-" + diaF;
            editFechaNac.setText(fechaSeleccionada);
        }, maxCal.get(Calendar.YEAR), maxCal.get(Calendar.MONTH), maxCal.get(Calendar.DAY_OF_MONTH));

        dialog.getDatePicker().setMaxDate(maxCal.getTimeInMillis());
        dialog.show();
    }

    private void configurarTextWatcherEmail() {
        editEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (emailCheckRunnable != null) debounceHandler.removeCallbacks(emailCheckRunnable);
                textEmailError.setVisibility(View.GONE);
                emailYaExiste = false;
            }
            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString().trim();
                if (email.contains("@") && email.length() > 4) {
                    emailCheckRunnable = () -> verificarEmailExistente(email);
                    debounceHandler.postDelayed(emailCheckRunnable, 800);
                }
            }
        });
    }

    private void verificarEmailExistente(String email) {
        if (isEditMode && email.equalsIgnoreCase(emailOriginal)) return;
        RetrofitClient.getChatApiServices().checkEmailExistente(email)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                emailYaExiste = json.optBoolean("existe", false);
                                textEmailError.setVisibility(emailYaExiste ? View.VISIBLE : View.GONE);
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }
                    @Override public void onFailure(Call<ResponseBody> call, Throwable t) {}
                });
    }

    private void setupEditMode() {
        if (textTitle != null) textTitle.setText("Modificar mi Perfil");
        btnRegister.setText("Guardar Cambios");
        currentUserId = getSharedPreferences("ChatPrefs", MODE_PRIVATE).getInt("id_usuario", -1);
        cargarDatosUsuario();
    }

    private void cargarDatosUsuario() {
        RetrofitClient.getChatApiServices().getUsuario(currentUserId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        editNombre.setText(json.getString("nombre"));
                        editApellidos.setText(json.getString("apellidos"));
                        fechaSeleccionada = json.getString("fechaNacimiento");
                        editFechaNac.setText(fechaSeleccionada);
                        emailOriginal = json.getString("email");
                        editEmail.setText(emailOriginal);
                        editTelefono.setText(json.getString("telefono"));
                        editPassword.setText("");
                        String fotoBase64 = json.optString("foto", "");
                        if (!fotoBase64.isEmpty()) {
                            encodedImage = fotoBase64;
                            byte[] decoded = Base64.decode(fotoBase64, Base64.DEFAULT);
                            imgUser.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Error al cargar datos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean validarCampos(boolean passwordObligatoria) {
        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (nombre.isEmpty()) {
            editNombre.setError("El nombre es obligatorio");
            editNombre.requestFocus();
            return false;
        }
        if (!nombre.matches("[a-zA-ZáéíóúÁÉÍÓÚàèìòùÀÈÌÒÙñÑüÜ\\s]+")) {
            editNombre.setError("Solo se permiten letras");
            editNombre.requestFocus();
            return false;
        }
        if (nombre.length() > 20) {
            editNombre.setError("Máximo 20 caracteres");
            editNombre.requestFocus();
            return false;
        }

        if (apellidos.isEmpty()) {
            editApellidos.setError("Los apellidos son obligatorios");
            editApellidos.requestFocus();
            return false;
        }
        if (!apellidos.matches("[a-zA-ZáéíóúÁÉÍÓÚàèìòùÀÈÌÒÙñÑüÜ\\s]+")) {
            editApellidos.setError("Solo se permiten letras");
            editApellidos.requestFocus();
            return false;
        }

        if (fechaSeleccionada.isEmpty()) {
            editFechaNac.setError("Selecciona tu fecha de nacimiento");
            return false;
        }

        if (email.isEmpty() || !email.contains("@")) {
            editEmail.setError("Introduce un correo electrónico válido");
            editEmail.requestFocus();
            return false;
        }
        if (emailYaExiste) {
            textEmailError.setVisibility(View.VISIBLE);
            editEmail.requestFocus();
            return false;
        }

        if (!telefono.matches("[0-9]{9}")) {
            editTelefono.setError("El teléfono debe tener exactamente 9 dígitos (solo números)");
            editTelefono.requestFocus();
            return false;
        }

        if (passwordObligatoria && password.isEmpty()) {
            editPassword.setError("La contraseña es obligatoria");
            editPassword.requestFocus();
            return false;
        }

        return true;
    }

    private void actualizar() {
        if (!validarCampos(false)) return;

        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        RetrofitClient.getChatApiServices().actualizarUsuario(
                currentUserId, nombre, apellidos, fechaSeleccionada, email, telefono, password, encodedImage
        ).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Perfil actualizado", Toast.LENGTH_SHORT).show();
                    getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit().putString("nombre", nombre).apply();
                    finish();
                } else {
                    Toast.makeText(RegisterActivity.this, "Error al actualizar", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void registrar() {
        if (!validarCampos(true)) return;

        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        RetrofitClient.getChatApiServices().registrarUsuario(
                nombre, apellidos, fechaSeleccionada, email, telefono, password, encodedImage
        ).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Snackbar.make(findViewById(android.R.id.content), R.string.codigo_enviado, Snackbar.LENGTH_LONG).show();
                    Intent intent = new Intent(RegisterActivity.this, VerificacionEmailActivity.class);
                    intent.putExtra("VERIFICACION_EMAIL", email);
                    startActivity(intent);
                    finish();
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "sin detalle";
                        android.util.Log.e("REGISTER_ERROR", "HTTP " + response.code() + ": " + errorBody);
                        Toast.makeText(RegisterActivity.this, "Error " + response.code() + ": " + errorBody, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(RegisterActivity.this, "Error en el registro (código " + response.code() + ")", Toast.LENGTH_LONG).show();
                    }
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openGallery() {
        startActivityForResult(
                new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                PICK_IMAGE_REQUEST
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                imgUser.setImageBitmap(bitmap);
                encodedImage = encodeImage(bitmap);
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private String encodeImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }
}
