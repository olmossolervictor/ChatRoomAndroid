package com.example.chat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.LinearLayout;
import java.util.Locale;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.chat.R;
import com.example.chat.network.ChatApiServices;
import com.example.chat.network.RetrofitClient;
import com.example.chat.utils.AlertHelper;
import com.example.chat.utils.AlertHelper.AlertType;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.util.TimeZone;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends BaseActivity {

    private static final String PREF_GOOGLE_PROFILE_SETUP_DONE = "google_profile_setup_done_";

    // Componentes de la interfaz de usuario
    private com.google.android.material.textfield.TextInputEditText editNombre, editApellidos, editRegFechaNac, editRegEmail, editRegTelefono, editRegPassword, editRegNombreUsuario;
    private TextView textEmailError, textNombreUsuarioError;
    private de.hdodenhof.circleimageview.CircleImageView imgUser;
    private com.google.android.material.button.MaterialButton btnRegister;
    private com.google.android.material.card.MaterialCardView btnSelectPhoto;
    private TextView textBackToLogin, textTitle;
    private LinearLayout layoutSugerencias, layoutBotonesSugerencias;

    // Gestión de términos y condiciones
    private CheckBox checkTerminos;
    private LinearLayout layoutTerminos;
    private TextView textVerTerminos;

    // Identificadores de solicitud para resultados de actividad y permisos
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAMERA_REQUEST = 2;
    private static final int PERMISSION_GALLERY_CODE = 100;
    private static final int PERMISSION_CAMERA_CODE = 101;

    // Variables de estado de la actividad
    private String encodedImage = "";
    private boolean isEditMode = false;
    private boolean isGoogleProfileSetup = false;
    private int currentUserId;
    private String fechaSeleccionada = "";
    private String emailOriginal = "";

    // Control de validación asíncrona y temporizadores
    private final Handler debounceHandler = new Handler();
    private Runnable emailCheckRunnable;
    private boolean emailYaExiste = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Inicialización de componentes y configuración de la interfaz
        textTitle = findViewById(R.id.textTitle);
        editNombre = findViewById(R.id.editRegNombre);
        editApellidos = findViewById(R.id.editRegApellidos);
        editRegFechaNac = findViewById(R.id.editRegEdad);
        editRegEmail = findViewById(R.id.editRegEmail);
        textEmailError = findViewById(R.id.textEmailError);
        editRegTelefono = findViewById(R.id.editRegTelefono);
        editRegPassword = findViewById(R.id.editRegPassword);
        imgUser = findViewById(R.id.imgUser);
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnRegister = findViewById(R.id.btnRegister);
        textBackToLogin = findViewById(R.id.textBackToLogin);

        checkTerminos = findViewById(R.id.checkTerminos);
        layoutTerminos = findViewById(R.id.layoutTerminos);
        textVerTerminos = findViewById(R.id.textVerTerminos);

        if (checkTerminos != null) {
            checkTerminos.setClickable(false);
        }

        if (textVerTerminos != null) {
            textVerTerminos.setOnClickListener(v -> mostrarAlertaTerminos());
        }

        editRegNombreUsuario = findViewById(R.id.editRegNombreUsuario);
        textNombreUsuarioError = findViewById(R.id.textNombreUsuarioError);
        layoutSugerencias = findViewById(R.id.layoutSugerencias);
        layoutBotonesSugerencias = findViewById(R.id.layoutBotonesSugerencias);

        if (editRegNombreUsuario != null) {
            editRegNombreUsuario.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (textNombreUsuarioError != null) textNombreUsuarioError.setVisibility(View.GONE);
                    if (layoutSugerencias != null) layoutSugerencias.setVisibility(View.GONE);
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

        if (editRegFechaNac != null) {
            editRegFechaNac.setFocusable(false);
            editRegFechaNac.setClickable(true);
            editRegFechaNac.setOnClickListener(v -> mostrarCalendario());
        }

        isEditMode = getIntent().getBooleanExtra("MODO_EDICION", false);
        isGoogleProfileSetup = getIntent().getBooleanExtra("MODO_GOOGLE_COMPLETAR_PERFIL", false);

        if (isEditMode) {
            prepararPantallaParaEdicion();
        } else {
            prepararPantallaParaRegistro();
        }

        configurarTextWatcherEmail();

        if (btnSelectPhoto != null) {
            btnSelectPhoto.setOnClickListener(v -> mostrarOpcionesFoto());
        }

        if (btnRegister != null) {
            btnRegister.setOnClickListener(v -> { if (isEditMode) actualizar(); else registrar(); });
        }

        if (textBackToLogin != null) {
            textBackToLogin.setOnClickListener(v -> finish());
        }
        configurarValidacionDinamica();
    }

    private void configurarValidacionDinamica() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (editNombre != null && editNombre.getError() != null) editNombre.setError(null);
                if (editRegEmail != null && editRegEmail.getError() != null) editRegEmail.setError(null);
                if (editRegPassword != null && editRegPassword.getError() != null) editRegPassword.setError(null);
            }
        };

        if (editNombre != null) editNombre.addTextChangedListener(watcher);
        if (editRegEmail != null) editRegEmail.addTextChangedListener(watcher);
        if (editRegPassword != null) editRegPassword.addTextChangedListener(watcher);

        if (btnRegister != null) {
            btnRegister.setEnabled(true);
            btnRegister.setAlpha(1.0f);
        }
    }

    private void mostrarAlertaTerminos() {
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            textView.setText(android.text.Html.fromHtml(getString(R.string.terminos_legales), android.text.Html.FROM_HTML_MODE_COMPACT));
        } else {
            textView.setText(android.text.Html.fromHtml(getString(R.string.terminos_legales)));
        }

        textView.setPadding(50, 40, 50, 40);
        textView.setTextSize(14);
        scrollView.addView(textView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Términos y Condiciones")
                .setView(scrollView)
                .setCancelable(false)
                .setPositiveButton("Aceptar", (d, which) -> {
                    checkTerminos.setChecked(true);
                })
                .setNegativeButton("Rechazar", (d, which) -> {
                    checkTerminos.setChecked(false);
                    Toast.makeText(RegisterActivity.this, "Debes aceptar los términos para registrarte", Toast.LENGTH_SHORT).show();
                })
                .create();

        dialog.show();

        Button btnAceptar = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        btnAceptar.setEnabled(false);

        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            View view = scrollView.getChildAt(scrollView.getChildCount() - 1);
            int diff = (view.getBottom() - (scrollView.getHeight() + scrollView.getScrollY()));
            if (diff <= 0) {
                btnAceptar.setEnabled(true);
            }
        });

        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            View view = scrollView.getChildAt(scrollView.getChildCount() - 1);
            if (view.getBottom() <= scrollView.getHeight()) {
                btnAceptar.setEnabled(true);
            }
        });
    }

    private void mostrarOpcionesFoto() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_photo_options, null);

        boolean mostrarEliminar = isEditMode || !encodedImage.isEmpty();
        View btnDelete = view.findViewById(R.id.btnDeletePhoto);
        btnDelete.setVisibility(mostrarEliminar ? View.VISIBLE : View.GONE);

        view.findViewById(R.id.btnCamera).setOnClickListener(v -> {
            dialog.dismiss();
            checkPermissionAndOpenCamera();
        });

        view.findViewById(R.id.btnGallery).setOnClickListener(v -> {
            dialog.dismiss();
            checkPermissionAndOpenGallery();
        });

        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            eliminarFoto();
        });

        dialog.setContentView(view);
        dialog.show();
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

    public void eliminarFoto(){
        imgUser.setImageResource(R.drawable.defecto);
        encodedImage = "";
        Toast.makeText(this, "Foto eliminada", Toast.LENGTH_SHORT).show();
    }

    // =========================================================
    // 🚀 AQUÍ SE CONFIGURA LA BARRA SÓLO PARA MODO EDICIÓN
    // =========================================================
    private void prepararPantallaParaEdicion() {
        androidx.appcompat.widget.Toolbar toolbarRegister = findViewById(R.id.toolbarRegister);
        if (toolbarRegister != null) {
            toolbarRegister.setVisibility(View.VISIBLE); // Lo hacemos visible al editar
            toolbarRegister.setNavigationOnClickListener(v -> finish()); // Flecha hacia atrás cierra la pantalla
        }

        if (textTitle != null) textTitle.setText("Editar Mi Perfil");
        btnRegister.setText("Guardar Cambios");

        if (layoutTerminos != null) layoutTerminos.setVisibility(View.GONE);
        if (textBackToLogin != null) textBackToLogin.setVisibility(View.GONE);
        if (layoutSugerencias != null) layoutSugerencias.setVisibility(View.GONE);

        // BLOQUEO DE CAMPOS
        editNombre.setEnabled(false);
        editApellidos.setEnabled(false);
        editRegFechaNac.setEnabled(false);
        editRegFechaNac.setClickable(false);
        editRegTelefono.setEnabled(false);
        editRegEmail.setEnabled(false);

        if (isGoogleProfileSetup) {
            editNombre.setEnabled(true);
            editApellidos.setEnabled(true);
            editRegFechaNac.setEnabled(true);
            editRegFechaNac.setClickable(true);
            editRegTelefono.setEnabled(true);
        }

        // CAMPOS EDITABLES
        if (editRegNombreUsuario != null) editRegNombreUsuario.setEnabled(true);
        editRegPassword.setEnabled(true);

        currentUserId = getSharedPreferences("ChatPrefs", MODE_PRIVATE).getInt("id_usuario", -1);

        cargarDatosUsuario();
    }

    private void prepararPantallaParaRegistro() {
        // Aseguramos que la barra superior no salga en el registro
        androidx.appcompat.widget.Toolbar toolbarRegister = findViewById(R.id.toolbarRegister);
        if (toolbarRegister != null) {
            toolbarRegister.setVisibility(View.GONE);
        }

        textTitle.setText("Registro de Usuario");
        btnRegister.setText("Registrarse");
        layoutTerminos.setVisibility(View.VISIBLE);
        textBackToLogin.setVisibility(View.VISIBLE);

        imgUser.setImageResource(R.drawable.defecto);
    }
    // =========================================================

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

    private void mostrarCalendario() {
        Calendar constraintsCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        constraintsCal.add(Calendar.YEAR, -18);
        long maxDate = constraintsCal.getTimeInMillis();

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.before(maxDate))
                .setEnd(maxDate)
                .build();

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecciona tu fecha de nacimiento")
                .setSelection(maxDate)
                .setCalendarConstraints(constraints)
                .setTheme(R.style.ThemeOverlay_App_MaterialCalendar)
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(selection);

            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            String diaF = String.format(Locale.getDefault(), "%02d", day);
            String mesF = String.format(Locale.getDefault(), "%02d", month + 1);

            fechaSeleccionada = year + "-" + mesF + "-" + diaF;
            String fechaMostrar = diaF + "/" + mesF + "/" + year;
            editRegFechaNac.setText(fechaMostrar);
            editRegFechaNac.setError(null);
        });

        datePicker.show(getSupportFragmentManager(), "MATERIAL_DATE_PICKER");
    }

    private void configurarTextWatcherEmail() {
        editRegEmail.addTextChangedListener(new TextWatcher() {
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

    private void cargarDatosUsuario() {
        if (currentUserId == -1) return;

        RetrofitClient.getChatApiServices().getUsuario(currentUserId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());

                        editNombre.setText(limpiarValor(json.optString("nombre", "")));
                        editApellidos.setText(limpiarValor(json.optString("apellidos", "")));

                        fechaSeleccionada = limpiarValor(json.optString("fechaNacimiento",
                                json.optString("fecha_nacimiento", json.optString("fecha_nac", ""))));
                        editRegFechaNac.setText(fechaSeleccionada);

                        emailOriginal = limpiarValor(json.optString("email", ""));
                        editRegEmail.setText(emailOriginal);

                        editRegTelefono.setText(limpiarValor(json.optString("telefono", "")));

                        if (editRegNombreUsuario != null) {
                            editRegNombreUsuario.setText(limpiarValor(json.optString("nombre_usuario", "")));
                        }

                        editRegPassword.setText("");

                        String fotoBase64 = json.optString("foto", "");
                        if (!fotoBase64.isEmpty()) {
                            encodedImage = fotoBase64;
                            byte[] decoded = Base64.decode(fotoBase64, Base64.DEFAULT);
                            imgUser.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Error al cargar datos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String limpiarValor(String value) {
        if (value == null || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }
        return value;
    }

    private boolean validarCampos(boolean passwordObligatoria) {
        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String nombreUsuario = editRegNombreUsuario != null ? editRegNombreUsuario.getText().toString().trim() : "";
        String email = editRegEmail.getText().toString().trim();
        String telefono = editRegTelefono.getText().toString().trim();
        String password = editRegPassword.getText().toString().trim();

        boolean faltanCampos = nombre.isEmpty() || apellidos.isEmpty() || nombreUsuario.isEmpty() ||
                email.isEmpty() || telefono.isEmpty() || (passwordObligatoria && password.isEmpty()) ||
                fechaSeleccionada.isEmpty();

        if (faltanCampos) {
            AlertHelper.showActionAlert(btnRegister, "Por favor, rellene todos los campos para continuar", AlertType.WARNING);
        }

        if (!isEditMode && checkTerminos != null && !checkTerminos.isChecked()) {
            AlertHelper.showActionAlert(checkTerminos, "Es necesario aceptar los términos para continuar", AlertType.WARNING);
            return false;
        }

        if (nombreUsuario.isEmpty()) {
            if (editRegNombreUsuario != null) {
                editRegNombreUsuario.setError("El nombre de usuario es obligatorio");
                editRegNombreUsuario.requestFocus();
            }
            return false;
        }
        if (nombreUsuario.length() < 4) {
            if (editRegNombreUsuario != null) {
                editRegNombreUsuario.setError("Usa al menos 4 caracteres para tu usuario");
                editRegNombreUsuario.requestFocus();
            }
            return false;
        }

        if (nombre.isEmpty()) {
            editNombre.setError("Tu nombre es obligatorio");
            editNombre.requestFocus();
            return false;
        }
        if (!nombre.matches("[a-zA-ZáéíóúÁÉÍÓÚàèìòùÀÈÌÒÙñÑüÜ\\s]+")) {
            editNombre.setError("Por favor, usa solo letras");
            editNombre.requestFocus();
            return false;
        }

        if (apellidos.isEmpty()) {
            editApellidos.setError("Tus apellidos son obligatorios");
            editApellidos.requestFocus();
            return false;
        }

        if (fechaSeleccionada.isEmpty()) {
            editRegFechaNac.setError("Selecciona tu fecha de nacimiento");
            AlertHelper.showActionAlert(editRegFechaNac, "Indica tu fecha de nacimiento", AlertType.INFO);
            return false;
        }

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editRegEmail.setError("Introduce un correo electrónico válido");
            editRegEmail.requestFocus();
            return false;
        }
        if (emailYaExiste) {
            textEmailError.setVisibility(View.VISIBLE);
            editRegEmail.requestFocus();
            return false;
        }

        if (!telefono.matches("^[0-9]{9}$")) {
            editRegTelefono.setError("El teléfono debe tener 9 dígitos numéricos");
            editRegTelefono.requestFocus();
            return false;
        }

        if (passwordObligatoria || (!passwordObligatoria && !password.isEmpty())) {
            if (password.isEmpty()) {
                editRegPassword.setError("Crea una contraseña para proteger tu cuenta");
                editRegPassword.requestFocus();
                return false;
            }
            if (password.length() < 6) {
                editRegPassword.setError("Usa al menos 6 caracteres");
                editRegPassword.requestFocus();
                return false;
            }
        }

        if (!isEditMode && checkTerminos != null && !checkTerminos.isChecked()) {
            Toast.makeText(this, "Debes aceptar los términos y condiciones legales", Toast.LENGTH_LONG).show();
            checkTerminos.requestFocus();
            return false;
        }

        return true;
    }

    private void actualizar() {
        if (!validarCampos(false)) return;

        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String nombreUsuario = editRegNombreUsuario != null ? editRegNombreUsuario.getText().toString().trim() : "";
        String email = editRegEmail.getText().toString().trim();
        String telefono = editRegTelefono.getText().toString().trim();
        String password = editRegPassword.getText().toString().trim();

        RetrofitClient.getChatApiServices().actualizarUsuario(
                currentUserId, nombre, apellidos, nombreUsuario, fechaSeleccionada, email, telefono, password, encodedImage
        ).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Perfil actualizado", Toast.LENGTH_SHORT).show();
                    SharedPreferences.Editor editor = getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit()
                            .putString("nombre", nombre);
                    if (isGoogleProfileSetup) {
                        editor.putBoolean(PREF_GOOGLE_PROFILE_SETUP_DONE + currentUserId, true);
                    }
                    editor.apply();
                    if (isGoogleProfileSetup) {
                        startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                    }
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

        String email = editRegEmail.getText().toString().trim();

        RetrofitClient.getChatApiServices()
                .iniciarRegistro(email)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Intent intent = new Intent(RegisterActivity.this, VerificacionEmailActivity.class);
                            intent.putExtra("VERIFICACION_EMAIL", email);
                            intent.putExtra("VERIFICACION_NOMBRE_USUARIO", editRegNombreUsuario.getText().toString().trim());
                            intent.putExtra("VERIFICACION_NOMBRE", editNombre.getText().toString().trim());
                            intent.putExtra("VERIFICACION_APELLIDOS", editApellidos.getText().toString().trim());
                            intent.putExtra("VERIFICACION_FECHA", fechaSeleccionada);
                            intent.putExtra("VERIFICACION_TELEFONO", editRegTelefono.getText().toString().trim());
                            intent.putExtra("VERIFICACION_PASSWORD", editRegPassword.getText().toString().trim());
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
                                    textNombreUsuarioError.setVisibility(View.GONE);
                                    layoutSugerencias.setVisibility(View.GONE);
                                } else {
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
                                            editRegNombreUsuario.setText(sugerencia);
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