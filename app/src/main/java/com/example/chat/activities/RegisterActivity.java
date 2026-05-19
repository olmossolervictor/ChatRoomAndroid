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
import com.google.android.material.textfield.TextInputLayout;
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

    // Componentes TextInputLayout (Para gestionar los bordes y textos rojos de forma nativa)
    private TextInputLayout tilRegNombre, tilRegApellidos, tilRegEdad, tilRegNombreUsuario, tilRegTelefono, tilRegEmail, tilRegPassword;

    // Campos de entrada
    private com.google.android.material.textfield.TextInputEditText editNombre, editApellidos, editRegFechaNac, editRegEmail, editRegTelefono, editRegPassword, editRegNombreUsuario;
    private TextView textEmailError, textNombreUsuarioError;
    private de.hdodenhof.circleimageview.CircleImageView imgUser;
    private com.google.android.material.button.MaterialButton btnRegister;
    private com.google.android.material.card.MaterialCardView btnSelectPhoto;
    private TextView textBackToLogin, textTitle;
    private LinearLayout layoutSugerencias, layoutBotonesSugerencias, layoutYaTienesCuenta;

    // Componentes de alertas generales y links
    private TextView tvGeneralError;
    private LinearLayout layoutRegLoginLink;

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
    private String nombreUsuarioOriginal = "";
    private boolean datosUsuarioCargados = false;

    // Control de validación asíncrona y temporizadores
    private final Handler debounceHandler = new Handler();
    private Runnable emailCheckRunnable;
    private Runnable usernameCheckRunnable;
    private boolean emailYaExiste = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Inicializar TextInputLayouts (Para colorearlos de rojo al haber error)
        tilRegNombre = findViewById(R.id.tilRegNombre);
        tilRegApellidos = findViewById(R.id.tilRegApellidos);
        tilRegEdad = findViewById(R.id.tilRegEdad);
        tilRegNombreUsuario = findViewById(R.id.tilRegNombreUsuario);
        tilRegTelefono = findViewById(R.id.tilRegTelefono);
        tilRegEmail = findViewById(R.id.tilRegEmail);
        tilRegPassword = findViewById(R.id.tilRegPassword);

        // Quitar el icono de error nativo por si acaso para igualarlo al Login
        if (tilRegNombre != null) tilRegNombre.setErrorIconDrawable(null);
        if (tilRegApellidos != null) tilRegApellidos.setErrorIconDrawable(null);
        if (tilRegEdad != null) tilRegEdad.setErrorIconDrawable(null);
        if (tilRegNombreUsuario != null) tilRegNombreUsuario.setErrorIconDrawable(null);
        if (tilRegTelefono != null) tilRegTelefono.setErrorIconDrawable(null);
        if (tilRegEmail != null) tilRegEmail.setErrorIconDrawable(null);
        if (tilRegPassword != null) tilRegPassword.setErrorIconDrawable(null);

        // Inicialización de componentes
        textTitle = findViewById(R.id.textTitle);
        editNombre = findViewById(R.id.editRegNombre);
        editApellidos = findViewById(R.id.editRegApellidos);
        editRegFechaNac = findViewById(R.id.editRegEdad);
        editRegEmail = findViewById(R.id.editRegEmail);
        textEmailError = findViewById(R.id.textEmailError);
        editRegTelefono = findViewById(R.id.editRegTelefono);
        editRegPassword = findViewById(R.id.editRegPassword);
        editRegNombreUsuario = findViewById(R.id.editRegNombreUsuario);
        imgUser = findViewById(R.id.imgUser);
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnRegister = findViewById(R.id.btnRegister);

        tvGeneralError = findViewById(R.id.tvGeneralError);
        textBackToLogin = findViewById(R.id.textBackToLogin);
        layoutYaTienesCuenta = findViewById(R.id.layoutYaTienesCuenta);
        layoutRegLoginLink = findViewById(R.id.layoutRegLoginLink);

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
                    if (usernameCheckRunnable != null) debounceHandler.removeCallbacks(usernameCheckRunnable);
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String input = s.toString().trim();

                    // SI ES EL NOMBRE PROPIO, OCULTAMOS ERROR Y NO HACEMOS PETICIÓN
                    if (isEditMode && !nombreUsuarioOriginal.isEmpty() && input.equalsIgnoreCase(nombreUsuarioOriginal)) {
                        textNombreUsuarioError.setVisibility(View.GONE);
                        layoutSugerencias.setVisibility(View.GONE);
                        return;
                    }

                    // Limpiar errores mientras escribe algo nuevo
                    if (textNombreUsuarioError != null) textNombreUsuarioError.setVisibility(View.GONE);
                    if (layoutSugerencias != null) layoutSugerencias.setVisibility(View.GONE);

                    if (input.length() > 3) {
                        usernameCheckRunnable = () -> validarNombreUsuario(input);
                        debounceHandler.postDelayed(usernameCheckRunnable, 600);
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
            datosUsuarioCargados = true;
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

    // 🚀 LÓGICA DE ERROR SILENCIOSO (Igual que en LoginActivity)
    private void setSilentError(TextInputLayout layout, boolean active) {
        if (active) {
            layout.setError(" "); // Activa el estado de error (borde rojo y hint rojo)
            // El 'indicador' es el contenedor del texto de error (hijo índice 1)
            if (layout.getChildCount() > 1) {
                layout.getChildAt(1).setVisibility(View.GONE); // Lo ocultamos para que no ocupe espacio y deforme el diseño
            }
        } else {
            layout.setError(null); // Quita el error visual por completo
        }
    }

    private void configurarValidacionDinamica() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (tvGeneralError != null) tvGeneralError.setVisibility(View.GONE);

                // Si el usuario escribe algo nuevo, quitamos el error rojo de ese campo específico
                if (editNombre.hasFocus()) setSilentError(tilRegNombre, false);
                if (editApellidos.hasFocus()) setSilentError(tilRegApellidos, false);
                if (editRegNombreUsuario.hasFocus()) setSilentError(tilRegNombreUsuario, false);
                if (editRegEmail.hasFocus()) setSilentError(tilRegEmail, false);
                if (editRegTelefono.hasFocus()) setSilentError(tilRegTelefono, false);
                if (editRegPassword.hasFocus()) setSilentError(tilRegPassword, false);
            }
        };

        editNombre.addTextChangedListener(watcher);
        editApellidos.addTextChangedListener(watcher);
        editRegNombreUsuario.addTextChangedListener(watcher);
        editRegEmail.addTextChangedListener(watcher);
        editRegTelefono.addTextChangedListener(watcher);
        editRegPassword.addTextChangedListener(watcher);
    }

    private void mostrarErrorGeneral(String mensaje) {
        if (tvGeneralError != null) {
            tvGeneralError.setText(mensaje);
            tvGeneralError.setVisibility(View.VISIBLE);
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
                    if (tvGeneralError != null) tvGeneralError.setVisibility(View.GONE);
                })
                .setNegativeButton("Rechazar", (d, which) -> {
                    checkTerminos.setChecked(false);
                    mostrarErrorGeneral("Debes aceptar los términos para registrarte");
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

    private void prepararPantallaParaEdicion() {
        androidx.appcompat.widget.Toolbar toolbarRegister = findViewById(R.id.toolbarRegister);
        if (toolbarRegister != null) {
            toolbarRegister.setVisibility(View.VISIBLE);
            toolbarRegister.setNavigationOnClickListener(v -> finish());
        }

        if (textTitle != null) textTitle.setText("Editar Mi Perfil");
        btnRegister.setText("Guardar Cambios");

        if (layoutTerminos != null) layoutTerminos.setVisibility(View.GONE);
        if (layoutRegLoginLink != null) layoutRegLoginLink.setVisibility(View.GONE);
        if (layoutSugerencias != null) layoutSugerencias.setVisibility(View.GONE);
        if (layoutYaTienesCuenta != null) layoutYaTienesCuenta.setVisibility(View.GONE);

        editNombre.setEnabled(false);
        editApellidos.setEnabled(false);
        editRegFechaNac.setEnabled(false);
        editRegFechaNac.setClickable(false);
        editRegTelefono.setEnabled(false);
        editRegEmail.setEnabled(false);
        // BLOQUEO DE CAMPOS (Solo los que no se pueden editar)
        // Usamos setFocusable(false) en vez de setEnabled(false) para que el color sea negro/nítido
        bloquearLectura(editNombre);
        bloquearLectura(editApellidos);
        bloquearLectura(editRegFechaNac);
        bloquearLectura(editRegTelefono);
        bloquearLectura(editRegEmail);

        // LA FOTO SÍ SE PUEDE EDITAR
        if (btnSelectPhoto != null) {
            btnSelectPhoto.setVisibility(View.VISIBLE);
        }

        if (isGoogleProfileSetup) {
            habilitarEscritura(editNombre);
            habilitarEscritura(editApellidos);
            habilitarEscritura(editRegFechaNac);
            habilitarEscritura(editRegTelefono);
        }

        if (editRegNombreUsuario != null) editRegNombreUsuario.setEnabled(true);
        editRegPassword.setEnabled(true);
        // CAMPOS EDITABLES
        if (editRegNombreUsuario != null) habilitarEscritura(editRegNombreUsuario);
        habilitarEscritura(editRegPassword);

        currentUserId = getSharedPreferences("ChatPrefs", MODE_PRIVATE).getInt("id_usuario", -1);

        cargarDatosUsuario();
    }

    private void bloquearLectura(com.google.android.material.textfield.TextInputEditText campo) {
        if (campo == null) return;
        campo.setFocusable(false);
        campo.setFocusableInTouchMode(false);
        campo.setClickable(false);
        campo.setCursorVisible(false);
        campo.setAlpha(1.0f); // Opacidad total para que la letra no sea gris
    }

    private void habilitarEscritura(com.google.android.material.textfield.TextInputEditText campo) {
        if (campo == null) return;
        campo.setFocusable(true);
        campo.setFocusableInTouchMode(true);
        campo.setClickable(true);
        campo.setCursorVisible(true);
        campo.setAlpha(1.0f);
    }

    private void prepararPantallaParaRegistro() {
        androidx.appcompat.widget.Toolbar toolbarRegister = findViewById(R.id.toolbarRegister);
        if (toolbarRegister != null) {
            toolbarRegister.setVisibility(View.GONE);
        }

        textTitle.setText("Registro de Usuario");
        btnRegister.setText("Registrarse");
        layoutTerminos.setVisibility(View.VISIBLE);

        if (layoutRegLoginLink != null) layoutRegLoginLink.setVisibility(View.VISIBLE);
        if (layoutTerminos != null) layoutTerminos.setVisibility(View.VISIBLE);
        if (textBackToLogin != null) textBackToLogin.setVisibility(View.VISIBLE);
        if (layoutYaTienesCuenta != null) layoutYaTienesCuenta.setVisibility(View.VISIBLE);

        imgUser.setImageResource(R.drawable.defecto);
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

            // Limpia el error rojo de la fecha
            setSilentError(tilRegEdad, false);
            if (tvGeneralError != null) tvGeneralError.setVisibility(View.GONE);
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
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);

                        editNombre.setText(limpiarValor(json.optString("nombre", "")));
                        editApellidos.setText(limpiarValor(json.optString("apellidos", "")));

                        fechaSeleccionada = limpiarValor(json.optString("fechaNacimiento",
                                json.optString("fecha_nacimiento", json.optString("fecha_nac", ""))));

                        if (fechaSeleccionada.contains("-")) {
                            try {
                                String[] parts = fechaSeleccionada.split("-");
                                editRegFechaNac.setText(parts[2] + "/" + parts[1] + "/" + parts[0]);
                            } catch (Exception e) {
                                editRegFechaNac.setText(fechaSeleccionada);
                            }
                        } else {
                            editRegFechaNac.setText(fechaSeleccionada);
                        }

                        emailOriginal = limpiarValor(json.optString("email", "")).trim();
                        editRegEmail.setText(emailOriginal);

                        editRegTelefono.setText(limpiarValor(json.optString("telefono", "")));

                        String uName = json.optString("nombre_usuario",
                                       json.optString("nombreUsuario",
                                       json.optString("username", "")));

                        nombreUsuarioOriginal = limpiarValor(uName).trim();

                        if (editRegNombreUsuario != null) {
                            editRegNombreUsuario.setText(nombreUsuarioOriginal);
                        }

                        editRegPassword.setText("");

                        String fotoBase64 = json.optString("foto", "");
                        if (!fotoBase64.isEmpty() && !fotoBase64.equals("null")) {
                            encodedImage = fotoBase64;
                            byte[] decoded = Base64.decode(fotoBase64, Base64.DEFAULT);
                            imgUser.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                        }

                        datosUsuarioCargados = true;
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
        return value.trim();
    }

    // 🚀 LÓGICA DE VALIDACIÓN CON LOS BORDES EN ROJO SILENCIOSOS
    private boolean validarCampos(boolean passwordObligatoria) {
        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String nombreUsuario = editRegNombreUsuario != null ? editRegNombreUsuario.getText().toString().trim() : "";
        String email = editRegEmail.getText().toString().trim();
        String telefono = editRegTelefono.getText().toString().trim();
        String password = editRegPassword.getText().toString().trim();

        // 1. Limpiar todos los errores visuales nativos previos
        if (tvGeneralError != null) tvGeneralError.setVisibility(View.GONE);
        setSilentError(tilRegNombre, false);
        setSilentError(tilRegApellidos, false);
        if (tilRegNombreUsuario != null) setSilentError(tilRegNombreUsuario, false);
        setSilentError(tilRegEmail, false);
        setSilentError(tilRegTelefono, false);
        setSilentError(tilRegPassword, false);
        setSilentError(tilRegEdad, false);

        boolean hasEmptyFields = false;

        // 2. Comprobar vacíos y subrayarlos (borde rojo)
        if (nombre.isEmpty()) { setSilentError(tilRegNombre, true); hasEmptyFields = true; }
        if (apellidos.isEmpty()) { setSilentError(tilRegApellidos, true); hasEmptyFields = true; }
        if (nombreUsuario.isEmpty() && tilRegNombreUsuario != null) { setSilentError(tilRegNombreUsuario, true); hasEmptyFields = true; }
        if (email.isEmpty()) { setSilentError(tilRegEmail, true); hasEmptyFields = true; }
        if (telefono.isEmpty()) { setSilentError(tilRegTelefono, true); hasEmptyFields = true; }
        if (fechaSeleccionada.isEmpty()) { setSilentError(tilRegEdad, true); hasEmptyFields = true; }
        if (passwordObligatoria && password.isEmpty()) { setSilentError(tilRegPassword, true); hasEmptyFields = true; }

        if (hasEmptyFields) {
            mostrarErrorGeneral("Rellena todos los campos vacíos.");
            return false;
        }

        // 3. Comprobar Términos y Condiciones
        if (!isEditMode && checkTerminos != null && !checkTerminos.isChecked()) {
            mostrarErrorGeneral("Debes aceptar los términos y condiciones legales.");
            return false;
        }

        // 4. Comprobaciones de reglas de negocio específicas (El primero que falle lanza su borde rojo y la alerta general)
        if (!nombre.matches("[a-zA-ZáéíóúÁÉÍÓÚàèìòùÀÈÌÒÙñÑüÜ\\s]+")) {
            setSilentError(tilRegNombre, true);
            mostrarErrorGeneral("El nombre solo debe contener letras");
            editNombre.requestFocus();
            return false;
        }

        if (nombreUsuario.length() < 4) {
            if (tilRegNombreUsuario != null) setSilentError(tilRegNombreUsuario, true);
            mostrarErrorGeneral("El usuario debe tener al menos 4 caracteres");
            if (editRegNombreUsuario != null) editRegNombreUsuario.requestFocus();
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setSilentError(tilRegEmail, true);
            mostrarErrorGeneral("Introduce un correo electrónico válido");
            editRegEmail.requestFocus();
            return false;
        }

        if (emailYaExiste) {
            setSilentError(tilRegEmail, true);
            mostrarErrorGeneral("Este correo electrónico ya está registrado");
            editRegEmail.requestFocus();
            return false;
        }

        if (textNombreUsuarioError.getVisibility() == View.VISIBLE) {
            // COMPROBACIÓN DEFINITIVA AL GUARDAR: Si es el suyo, limpiamos error y permitimos guardar
            if (isEditMode && !nombreUsuarioOriginal.isEmpty() && nombreUsuario.equalsIgnoreCase(nombreUsuarioOriginal)) {
                textNombreUsuarioError.setVisibility(View.GONE);
                if (layoutSugerencias != null) layoutSugerencias.setVisibility(View.GONE);
            } else {
                if (editRegNombreUsuario != null) editRegNombreUsuario.requestFocus();
                return false;
            }
        }

        if (!telefono.matches("^[0-9]{9}$")) {
            setSilentError(tilRegTelefono, true);
            mostrarErrorGeneral("El teléfono debe tener 9 dígitos exactos");
            editRegTelefono.requestFocus();
            return false;
        }

        if (passwordObligatoria || (!passwordObligatoria && !password.isEmpty())) {
            if (password.length() < 8) {
                setSilentError(tilRegPassword, true);
                mostrarErrorGeneral("La contraseña debe tener al menos 8 caracteres");
                editRegPassword.requestFocus();
                return false;
            }
            if (!password.matches(".*[A-Z].*")) {
                setSilentError(tilRegPassword, true);
                mostrarErrorGeneral("La contraseña debe contener al menos una mayúscula");
                editRegPassword.requestFocus();
                return false;
            }
            if (!password.matches(".*[^a-zA-Z0-9ñÑáéíóúÁÉÍÓÚ].*")) {
                setSilentError(tilRegPassword, true);
                mostrarErrorGeneral("Debe tener un carácter especial (ej: @, #, $, -)");
                editRegPassword.requestFocus();
                return false;
            }
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

                    nombreUsuarioOriginal = nombreUsuario;
                    emailOriginal = email;

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
                    mostrarErrorGeneral("Error al actualizar el perfil.");
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                mostrarErrorGeneral("Error de red. Inténtalo de nuevo más tarde.");
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
                                mostrarErrorGeneral("Error " + response.code() + ": " + errorBody);
                            } catch (Exception e) {
                                mostrarErrorGeneral("Error en el registro (código " + response.code() + ")");
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

    // 🚀 AQUI ESTÁ LA SOLUCIÓN AL BUG DE LA CÁMARA
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            // Caso 1: Viene de la galería
            if (requestCode == PICK_IMAGE_REQUEST && data.getData() != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                    imgUser.setImageBitmap(bitmap);
                    encodedImage = encodeImage(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Caso 2: Viene de la cámara
            else if (requestCode == CAMERA_REQUEST && data.getExtras() != null) {
                Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                if (bitmap != null) {
                    imgUser.setImageBitmap(bitmap);
                    encodedImage = encodeImage(bitmap);
                }
            }
        }
    }

    private String encodeImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    private void validarNombreUsuario(String nombreUsuario) {
        String input = nombreUsuario.trim();

        if (isEditMode && !nombreUsuarioOriginal.isEmpty() && input.equalsIgnoreCase(nombreUsuarioOriginal)) {
            if (textNombreUsuarioError != null) textNombreUsuarioError.setVisibility(View.GONE);
            if (layoutSugerencias != null) layoutSugerencias.setVisibility(View.GONE);
            return;
        }

        RetrofitClient.getChatApiServices().sugerirNombreUsuario(input)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        String currentInput = editRegNombreUsuario.getText().toString().trim();
                        if (isEditMode && !nombreUsuarioOriginal.isEmpty() && currentInput.equalsIgnoreCase(nombreUsuarioOriginal)) {
                            textNombreUsuarioError.setVisibility(View.GONE);
                            if (layoutSugerencias != null) layoutSugerencias.setVisibility(View.GONE);
                            return;
                        }

                        if (!currentInput.equalsIgnoreCase(input)) return;

                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                boolean disponible = json.optBoolean("disponible", true);
                                JSONArray sugerencias = json.optJSONArray("sugerencias");

                                if (disponible) {
                                    textNombreUsuarioError.setVisibility(View.GONE);
                                    layoutSugerencias.setVisibility(View.GONE);
                                } else {
                                    if (isEditMode && !nombreUsuarioOriginal.isEmpty() && input.equalsIgnoreCase(nombreUsuarioOriginal)) {
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
