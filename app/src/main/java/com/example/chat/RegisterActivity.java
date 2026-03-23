package com.example.chat;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText editNombre, editApellidos, editFechaNacimiento, editEmail, editTelefono, editPassword;
    private ImageView imgUser;
    private CheckBox checkTerminos;
    private TextView textVerTerminos, textBackToLogin, textTitle;
    private Button btnSelectPhoto, btnRegister;

    private static final int CAMERA_REQUEST = 1888;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private String encodedImage = "";
    private boolean isEditMode = false;
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        textTitle = findViewById(R.id.textTitle);
        editNombre = findViewById(R.id.editRegNombre);
        editApellidos = findViewById(R.id.editRegApellidos);
        editFechaNacimiento = findViewById(R.id.editRegFechaNacimiento);
        editEmail = findViewById(R.id.editRegEmail);
        editTelefono = findViewById(R.id.editRegTelefono);
        editPassword = findViewById(R.id.editRegPassword);
        imgUser = findViewById(R.id.imgUser);
        checkTerminos = findViewById(R.id.checkTerminos);
        textVerTerminos = findViewById(R.id.textVerTerminos);
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnRegister = findViewById(R.id.btnRegister);
        textBackToLogin = findViewById(R.id.textBackToLogin);

        isEditMode = getIntent().getBooleanExtra("MODO_EDICION", false);

        if (isEditMode) {
            setupEditMode();
            findViewById(R.id.layoutTerminos).setVisibility(View.GONE);
        }

        btnSelectPhoto.setText("Hacer Foto");
        btnSelectPhoto.setOnClickListener(v -> checkPermissionAndOpenCamera());
        
        textVerTerminos.setOnClickListener(v -> mostrarDialogoTerminos());

        btnRegister.setOnClickListener(v -> {
            if (isEditMode) {
                actualizar();
            } else {
                if (checkTerminos.isChecked()) {
                    registrar();
                } else {
                    Toast.makeText(this, "Debes aceptar los términos legales", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        textBackToLogin.setOnClickListener(v -> finish());
    }

    private void checkPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
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
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void mostrarDialogoTerminos() {
        new AlertDialog.Builder(this)
                .setTitle("Términos y Condiciones")
                .setMessage(getString(R.string.terminos_legales))
                .setPositiveButton("Entendido", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void setupEditMode() {
        if (textTitle != null) textTitle.setText("Modificar mi Perfil");
        btnRegister.setText("Guardar Cambios");
        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);
        cargarDatosUsuario();
    }

    private void cargarDatosUsuario() {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.getUsuario(currentUserId);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String result = response.body().string();
                        JSONObject json = new JSONObject(result);
                        editNombre.setText(json.getString("nombre"));
                        editApellidos.setText(json.getString("apellidos"));
                        editFechaNacimiento.setText(json.getString("fecha_nacimiento"));
                        editEmail.setText(json.getString("email"));
                        editTelefono.setText(json.getString("telefono"));
                        editPassword.setText(json.getString("password"));
                        String fotoBase64 = json.getString("foto");
                        if (fotoBase64 != null && !fotoBase64.isEmpty()) {
                            encodedImage = fotoBase64;
                            byte[] decodedString = Base64.decode(fotoBase64, Base64.DEFAULT);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            imgUser.setImageBitmap(decodedByte);
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

    private void registrar() {
        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String fechaNac = editFechaNacimiento.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (nombre.isEmpty() || apellidos.isEmpty() || fechaNac.isEmpty() || email.isEmpty() || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.registrarUsuario(nombre, apellidos, fechaNac, email, telefono, password, encodedImage, 1);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Usuario registrado con éxito", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(RegisterActivity.this, "Error en el registro", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void actualizar() {
        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String fechaNac = editFechaNacimiento.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (nombre.isEmpty() || apellidos.isEmpty() || fechaNac.isEmpty() || email.isEmpty() || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.actualizarUsuario(currentUserId, nombre, apellidos, fechaNac, email, telefono, password, encodedImage);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Perfil actualizado", Toast.LENGTH_SHORT).show();
                    SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
                    pref.edit().putString("nombre", nombre).apply();
                    finish();
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                Bitmap imageBitmap = (Bitmap) extras.get("data");
                imgUser.setImageBitmap(imageBitmap);
                encodedImage = encodeImage(imageBitmap);
            }
        }
    }

    private String encodeImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }
}
