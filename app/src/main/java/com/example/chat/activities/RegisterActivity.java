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
import java.util.Locale;
import android.widget.ScrollView;
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
import com.example.chat.utils.AlertHelper;
import com.example.chat.utils.AlertHelper.AlertType;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.util.TimeZone;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends BaseActivity {

    // Componentes de la interfaz de usuario
    private EditText editNombre, editApellidos, editFechaNac, editEmail, editTelefono, editPassword, editNombreUsuario;
    private TextView textEmailError, textNombreUsuarioError;
    private ImageView imgUser;
    private Button btnRegister;
    private View btnSelectPhoto;
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
        editFechaNac = findViewById(R.id.editRegEdad);
        editEmail = findViewById(R.id.editRegEmail);
        textEmailError = findViewById(R.id.textEmailError);
        editTelefono = findViewById(R.id.editRegTelefono);
        editPassword = findViewById(R.id.editRegPassword);
        imgUser = findViewById(R.id.imgUser);
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnRegister = findViewById(R.id.btnRegister);
        textBackToLogin = findViewById(R.id.textBackToLogin);

        checkTerminos = findViewById(R.id.checkTerminos);
        layoutTerminos = findViewById(R.id.layoutTerminos);
        textVerTerminos = findViewById(R.id.textVerTerminos);

        // Configuración de listeners y comportamiento inicial
        if (checkTerminos != null) {
            checkTerminos.setClickable(false);
        }
        
        if (textVerTerminos != null) {
            textVerTerminos.setOnClickListener(v -> mostrarAlertaTerminos());
        }

        editNombreUsuario = findViewById(R.id.editRegNombreUsuario);
        textNombreUsuarioError = findViewById(R.id.textNombreUsuarioError);
        layoutSugerencias = findViewById(R.id.layoutSugerencias);
        layoutBotonesSugerencias = findViewById(R.id.layoutBotonesSugerencias);

        if (editNombreUsuario != null) {
            editNombreUsuario.addTextChangedListener(new TextWatcher() {
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

        if (editFechaNac != null) {
            editFechaNac.setFocusable(false);
            editFechaNac.setClickable(true);
            editFechaNac.setOnClickListener(v -> mostrarCalendario());
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

    /**
     * Establece listeners para limpiar los estados de error visual de forma dinámica.
     */
    private void configurarValidacionDinamica() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (editNombre != null && editNombre.getError() != null) editNombre.setError(null);
                if (editEmail != null && editEmail.getError() != null) editEmail.setError(null);
                if (editPassword != null && editPassword.getError() != null) editPassword.setError(null);
            }
        };

        if (editNombre != null) editNombre.addTextChangedListener(watcher);
        if (editEmail != null) editEmail.addTextChangedListener(watcher);
        if (editPassword != null) editPassword.addTextChangedListener(watcher);
        
        if (btnRegister != null) {
            btnRegister.setEnabled(true);
            btnRegister.setAlpha(1.0f);
        }
    }

    /**
     * Muestra el diálogo de lectura de términos y condiciones con validación de scroll.
     */
    private void mostrarAlertaTerminos() {
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);

        // AQUÍ ESTÁ LA CORRECCIÓN: Le decimos que lea el XML como HTML
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

        // Bloqueamos el botón de aceptar por defecto
        Button btnAceptar = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        btnAceptar.setEnabled(false);

        // Escuchamos el scroll para desbloquear el botón cuando llegue al final
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            View view = scrollView.getChildAt(scrollView.getChildCount() - 1);
            int diff = (view.getBottom() - (scrollView.getHeight() + scrollView.getScrollY()));
            if (diff <= 0) {
                btnAceptar.setEnabled(true);
            }
        });

        // Por si el texto es tan corto que no hace falta scrollear
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            View view = scrollView.getChildAt(scrollView.getChildCount() - 1);
            if (view.getBottom() <= scrollView.getHeight()) {
                btnAceptar.setEnabled(true);
            }
        });
    }

    // 📷 LÓGICA DE FOTO Y PERMISOS
    private void mostrarOpcionesFoto() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_photo_options, null);

        // Controlamos si mostramos la opción de eliminar
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
        imgUser.setImageResource(android.R.drawable.ic_menu_camera); // Ponemos el icono por defecto
        encodedImage = ""; // Vaciamos el texto para que al servidor le llegue en blanco
        Toast.makeText(this, "Foto eliminada", Toast.LENGTH_SHORT).show();
    }
    private void prepararPantallaParaEdicion() {
        if (textTitle != null) textTitle.setText("Editar Mi Perfil");
        btnRegister.setText("Guardar Cambios");

        if (layoutTerminos != null) layoutTerminos.setVisibility(View.GONE);
        if (textBackToLogin != null) textBackToLogin.setVisibility(View.GONE);
        if (layoutSugerencias != null) layoutSugerencias.setVisibility(View.GONE);

        // BLOQUEO DE CAMPOS
        editNombre.setEnabled(false);
        editApellidos.setEnabled(false);
        editFechaNac.setEnabled(false);
        editFechaNac.setClickable(false);
        editTelefono.setEnabled(false);
        editEmail.setEnabled(false);

        if (isGoogleProfileSetup) {
            editNombre.setEnabled(true);
            editApellidos.setEnabled(true);
            editFechaNac.setEnabled(true);
            editFechaNac.setClickable(true);
            editTelefono.setEnabled(true);
        }

        // CAMPOS EDITABLES
        if (editNombreUsuario != null) editNombreUsuario.setEnabled(true);
        editPassword.setEnabled(true);

        // RECUPERAMOS TU ID DE USUARIO (Súper importante)
        currentUserId = getSharedPreferences("ChatPrefs", MODE_PRIVATE).getInt("id_usuario", -1);

        cargarDatosUsuario();
    }

    private void prepararPantallaParaRegistro() {
        // Configuraciones normales de registro por si acaso
        textTitle.setText("Registro de Usuario");
        btnRegister.setText("Registrarse");
        layoutTerminos.setVisibility(View.VISIBLE);
        textBackToLogin.setVisibility(View.VISIBLE);
        editPassword.setHint("Contraseña");
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
        // Configuramos la restricción: máximo hoy hace 18 años
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
            editFechaNac.setText(fechaMostrar);
            editFechaNac.setError(null);
        });

        datePicker.show(getSupportFragmentManager(), "MATERIAL_DATE_PICKER");
    }

    // Método que ya no usaremos pero mantenemos la estructura limpia
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
        if (currentUserId == -1) return; // Si no hay ID, no hacemos la petición

        RetrofitClient.getChatApiServices().getUsuario(currentUserId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());

                        editNombre.setText(limpiarValor(json.optString("nombre", "")));
                        editApellidos.setText(limpiarValor(json.optString("apellidos", "")));

                        fechaSeleccionada = limpiarValor(json.optString("fechaNacimiento", ""));
                        editFechaNac.setText(fechaSeleccionada);

                        emailOriginal = limpiarValor(json.optString("email", ""));
                        editEmail.setText(emailOriginal);

                        editTelefono.setText(limpiarValor(json.optString("telefono", "")));

                        // CARGAMOS EL NOMBRE DE USUARIO (EL NUEVO CAMPO)
                        if (editNombreUsuario != null) {
                            editNombreUsuario.setText(limpiarValor(json.optString("nombre_usuario", "")));
                        }

                        editPassword.setText(""); // Dejamos la contraseña en blanco por seguridad

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
        String nombreUsuario = editNombreUsuario != null ? editNombreUsuario.getText().toString().trim() : "";
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        // VALIDACIÓN GENERAL: Si falta algún campo obligatorio, mostramos el aviso inferior
        boolean faltanCampos = nombre.isEmpty() || apellidos.isEmpty() || nombreUsuario.isEmpty() ||
                             email.isEmpty() || telefono.isEmpty() || (passwordObligatoria && password.isEmpty()) ||
                             fechaSeleccionada.isEmpty();

        if (faltanCampos) {
            AlertHelper.showActionAlert(btnRegister, "Por favor, rellene todos los campos para continuar", AlertType.WARNING);
        }

        // 1. TÉRMINOS LEGALES
        if (!isEditMode && checkTerminos != null && !checkTerminos.isChecked()) {
            AlertHelper.showActionAlert(checkTerminos, "Es necesario aceptar los términos para continuar", AlertType.WARNING);
            return false;
        }


        // 2. NOMBRE DE USUARIO
        if (nombreUsuario.isEmpty()) {
            if (editNombreUsuario != null) {
                editNombreUsuario.setError("El nombre de usuario es obligatorio");
                editNombreUsuario.requestFocus();
            }
            return false;
        }
        if (nombreUsuario.length() < 4) {
            if (editNombreUsuario != null) {
                editNombreUsuario.setError("Usa al menos 4 caracteres para tu usuario");
                editNombreUsuario.requestFocus();
            }
            return false;
        }

        // 3. NOMBRE Y APELLIDOS
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

        // 4. FECHA DE NACIMIENTO
        if (fechaSeleccionada.isEmpty()) {
            editFechaNac.setError("Selecciona tu fecha de nacimiento");
            AlertHelper.showActionAlert(editFechaNac, "Indica tu fecha de nacimiento", AlertType.INFO);
            return false;
        }

        // 5. EMAIL
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Introduce un correo electrónico válido");
            editEmail.requestFocus();
            return false;
        }
        if (emailYaExiste) {
            textEmailError.setVisibility(View.VISIBLE);
            editEmail.requestFocus();
            return false;
        }

        // 6. TELÉFONO
        if (!telefono.matches("^[0-9]{9}$")) {
            editTelefono.setError("El teléfono debe tener 9 dígitos numéricos");
            editTelefono.requestFocus();
            return false;
        }

        // 7. CONTRASEÑA
        if (passwordObligatoria || (!passwordObligatoria && !password.isEmpty())) {
            if (password.isEmpty()) {
                editPassword.setError("Crea una contraseña para proteger tu cuenta");
                editPassword.requestFocus();
                return false;
            }
            if (password.length() < 6) {
                editPassword.setError("Usa al menos 6 caracteres");
                editPassword.requestFocus();
                return false;
            }
        }

        // VALIDACIÓN DE LOS TÉRMINOS LEGALES (Solo si es un registro nuevo)
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
        String nombreUsuario = editNombreUsuario != null ? editNombreUsuario.getText().toString().trim() : "";
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        RetrofitClient.getChatApiServices().actualizarUsuario(
                currentUserId, nombre, apellidos, nombreUsuario, fechaSeleccionada, email, telefono, password, encodedImage
        ).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Perfil actualizado", Toast.LENGTH_SHORT).show();
                    getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit().putString("nombre", nombre).apply();
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
