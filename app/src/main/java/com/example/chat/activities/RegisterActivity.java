package com.example.chat.activities;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONArray;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

    private EditText editNombre, editApellidos, editFechaNac, editEmail, editTelefono, editPassword, editNombreUsuario;
    private TextView textEmailError, textNombreUsuarioError;
    private ImageView imgUser;
    private Button btnSelectPhoto, btnRegister;
    private TextView textBackToLogin, textTitle;
    private LinearLayout layoutSugerencias, layoutBotonesSugerencias;

    // AÑADIMOS EL CHECKBOX Y SU CONTENEDOR
    private CheckBox checkTerminos;
    private LinearLayout layoutTerminos;
    private TextView textVerTerminos;

    // Constantes para permisos e intenciones
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAMERA_REQUEST = 2;
    private static final int PERMISSION_GALLERY_CODE = 100;
    private static final int PERMISSION_CAMERA_CODE = 101;

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

        // VINCULAMOS EL CHECKBOX Y SU LAYOUT
        checkTerminos = findViewById(R.id.checkTerminos);
        layoutTerminos = findViewById(R.id.layoutTerminos);
        textVerTerminos = findViewById(R.id.textVerTerminos);

        // Agregar listener para mostrar términos al hacer clic
        if (textVerTerminos != null) {
            textVerTerminos.setOnClickListener(v -> mostrarDialogoTerminos());
        }

        // Vinculamos nombre de usuario y sus elementos
        editNombreUsuario = findViewById(R.id.editRegNombreUsuario);
        textNombreUsuarioError = findViewById(R.id.textNombreUsuarioError);
        layoutSugerencias = findViewById(R.id.layoutSugerencias);
        layoutBotonesSugerencias = findViewById(R.id.layoutBotonesSugerencias);

        // Agregar listener para validar nombre de usuario
        if (editNombreUsuario != null) {
            editNombreUsuario.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    textNombreUsuarioError.setVisibility(View.GONE);
                    layoutSugerencias.setVisibility(View.GONE);
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String nombreUsuario = s.toString().trim();
                    if (nombreUsuario.length() > 3) {
                        validarNombreUsuario(nombreUsuario);
                    }
                }
            });
        }

        editFechaNac.setFocusable(false);
        editFechaNac.setClickable(true);
        editFechaNac.setOnClickListener(v -> mostrarCalendario());

        isEditMode = getIntent().getBooleanExtra("MODO_EDICION", false);
        if (isEditMode) {
            setupEditMode();
            // Si estamos editando perfil, ocultamos la caja de los términos
            if (layoutTerminos != null) {
                layoutTerminos.setVisibility(View.GONE);
            }
        }

        configurarTextWatcherEmail();

        btnSelectPhoto.setOnClickListener(v -> mostrarOpcionesFoto());

        btnRegister.setOnClickListener(v -> { if (isEditMode) actualizar(); else registrar(); });
        textBackToLogin.setOnClickListener(v -> finish());
    }

    // =========================================================
    // 📷 LÓGICA DE FOTO Y PERMISOS
    // =========================================================

    private void mostrarOpcionesFoto() {
        String[] opciones = {"Hacer foto con la Cámara", "Elegir de la Galería"};
        new AlertDialog.Builder(this)
                .setTitle("Elige una foto de perfil")
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) checkPermissionAndOpenCamera();
                    else checkPermissionAndOpenGallery();
                })
                .show();
    }

    private void checkPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_CODE);
        } else {
            openCamera();
        }
    }

    private void checkPermissionAndOpenGallery() {
        String permiso = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permiso = Manifest.permission.READ_MEDIA_IMAGES;
        }

        if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permiso}, PERMISSION_GALLERY_CODE);
        } else {
            openGallery();
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST);
        } else {
            Toast.makeText(this, "No se encontró aplicación de cámara", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openCamera();
            else Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
        }
        else if (requestCode == PERMISSION_GALLERY_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openGallery();
            else Toast.makeText(this, "Permiso de galería denegado", Toast.LENGTH_SHORT).show();
        }
    }


    // =========================================================

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

        // VALIDACIÓN DE LOS TÉRMINOS LEGALES (Solo si es un registro nuevo)
        if (!isEditMode && checkTerminos != null && !checkTerminos.isChecked()) {
            Toast.makeText(this, "Debes aceptar los términos y condiciones legales", Toast.LENGTH_LONG).show();
            // Le damos un pequeño efecto visual para que se fije
            checkTerminos.requestFocus();
            return false;
        }

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

        String email = editEmail.getText().toString().trim();

        RetrofitClient.getChatApiServices()
                .iniciarRegistro(email)
                .enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    // Guardar datos del usuario para la verificación
                    Intent intent = new Intent(RegisterActivity.this, VerificacionEmailActivity.class);
                    intent.putExtra("VERIFICACION_EMAIL", email);
                    intent.putExtra("VERIFICACION_NOMBRE_USUARIO", editNombreUsuario.getText().toString().trim());
                    intent.putExtra("VERIFICACION_NOMBRE", editNombre.getText().toString().trim());
                    intent.putExtra("VERIFICACION_APELLIDOS", editApellidos.getText().toString().trim());
                    intent.putExtra("VERIFICACION_FECHA", fechaSeleccionada);
                    intent.putExtra("VERIFICACION_TELEFONO", editTelefono.getText().toString().trim());
                    intent.putExtra("VERIFICACION_PASSWORD", editPassword.getText().toString().trim());
                    intent.putExtra("VERIFICACION_FOTO", encodedImage);
                    startActivity(intent);
                    finish();
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "sin detalle";
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

    private void mostrarDialogoTerminos() {
        String terminosTexto = "TÉRMINOS Y CONDICIONES\n\n" +
                "1. Uso del Servicio\n" +
                "Al utilizar esta aplicación, aceptas estos términos y condiciones. Si no estás de acuerdo, no uses la aplicación.\n\n" +
                "2. Cuentas de Usuario\n" +
                "Eres responsable de mantener la confidencialidad de tu contraseña y de toda la actividad que ocurra bajo tu cuenta.\n\n" +
                "3. Contenido\n" +
                "No debes publicar contenido ofensivo, ilegal o que infrinja derechos de terceros.\n\n" +
                "4. Privacidad\n" +
                "Tu información personal será tratada según nuestra política de privacidad.\n\n" +
                "5. Limitación de Responsabilidad\n" +
                "No somos responsables por daños indirectos o pérdida de datos.\n\n" +
                "6. Cambios en los Términos\n" +
                "Podemos modificar estos términos en cualquier momento. El uso continuado de la aplicación implica aceptación de los cambios.\n\n" +
                "7. Terminación\n" +
                "Podemos suspender tu cuenta por violación de estos términos.\n\n" +
                "8. Ley Aplicable\n" +
                "Estos términos se rigen por la ley aplicable en tu país.";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Términos y Condiciones");
        builder.setMessage(terminosTexto);
        builder.setPositiveButton("Acepto", (dialog, which) -> {
            checkTerminos.setChecked(true);
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            checkTerminos.setChecked(false);
            dialog.dismiss();
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void validarNombreUsuario(String nombreUsuario) {
        RetrofitClient.getChatApiServices().sugerirNombreUsuario(nombreUsuario)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                boolean disponible = json.optBoolean("disponible", true);
                                JSONArray sugerencias = json.optJSONArray("sugerencias");

                                if (disponible) {
                                    // Nombre disponible
                                    textNombreUsuarioError.setVisibility(View.GONE);
                                    layoutSugerencias.setVisibility(View.GONE);
                                } else {
                                    // Nombre no disponible, mostrar sugerencias
                                    textNombreUsuarioError.setVisibility(View.VISIBLE);
                                    layoutSugerencias.setVisibility(View.VISIBLE);
                                    layoutBotonesSugerencias.removeAllViews();

                                    for (int i = 0; i < sugerencias.length(); i++) {
                                        String sugerencia = sugerencias.getString(i);
                                        Button btnSugerencia = new Button(RegisterActivity.this);
                                        btnSugerencia.setText(sugerencia);
                                        btnSugerencia.setTextSize(12);
                                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT);
                                        params.setMargins(4, 0, 4, 0);
                                        btnSugerencia.setLayoutParams(params);
                                        btnSugerencia.setOnClickListener(v -> {
                                            editNombreUsuario.setText(sugerencia);
                                        });
                                        layoutBotonesSugerencias.addView(btnSugerencia);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                    }
                });
    }
}
