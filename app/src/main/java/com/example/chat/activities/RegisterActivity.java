package com.example.chat.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
    private ImageView imgUser;
    private Button btnSelectPhoto, btnRegister;
    private TextView textBackToLogin, textTitle;

    private static final int PICK_IMAGE_REQUEST = 1;
    private String encodedImage = "";
    private boolean isEditMode = false;
    private int currentUserId;
    private String fechaSeleccionada = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        textTitle = findViewById(R.id.textTitle);
        editNombre = findViewById(R.id.editRegNombre);
        editApellidos = findViewById(R.id.editRegApellidos);
        editFechaNac = findViewById(R.id.editRegEdad);
        editEmail = findViewById(R.id.editRegEmail);
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

        btnSelectPhoto.setOnClickListener(v -> openGallery());
        btnRegister.setOnClickListener(v -> { if (isEditMode) actualizar(); else registrar(); });
        textBackToLogin.setOnClickListener(v -> finish());
    }

    private void mostrarCalendario() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            String mesF = (month + 1) < 10 ? "0" + (month + 1) : String.valueOf(month + 1);
            String diaF = day < 10 ? "0" + day : String.valueOf(day);
            fechaSeleccionada = year + "-" + mesF + "-" + diaF;
            editFechaNac.setText(fechaSeleccionada);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
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
                        editEmail.setText(json.getString("email"));
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

    private void actualizar() {
        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (nombre.isEmpty() || apellidos.isEmpty() || fechaSeleccionada.isEmpty() || email.isEmpty() || telefono.isEmpty()) {
            Toast.makeText(this, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

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
        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (nombre.isEmpty() || apellidos.isEmpty() || fechaSeleccionada.isEmpty() || email.isEmpty() || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.getChatApiServices().registrarUsuario(
                nombre, apellidos, fechaSeleccionada, email, telefono, password, encodedImage
        ).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Usuario registrado", Toast.LENGTH_SHORT).show();
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
