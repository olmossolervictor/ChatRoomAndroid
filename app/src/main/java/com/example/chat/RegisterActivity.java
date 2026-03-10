package com.example.chat;

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

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText editNombre, editApellidos, editEdad, editEmail, editTelefono, editPassword;
    private ImageView imgUser;
    private Button btnSelectPhoto, btnRegister;
    private TextView textBackToLogin, textTitle;

    private static final int PICK_IMAGE_REQUEST = 1;
    private String encodedImage = "";
    private boolean isEditMode = false;
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Referencias
        textTitle = findViewById(R.id.textTitle); // Corregido ID
        editNombre = findViewById(R.id.editRegNombre);
        editApellidos = findViewById(R.id.editRegApellidos);
        editEdad = findViewById(R.id.editRegEdad);
        editEmail = findViewById(R.id.editRegEmail);
        editTelefono = findViewById(R.id.editRegTelefono);
        editPassword = findViewById(R.id.editRegPassword);
        imgUser = findViewById(R.id.imgUser);
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnRegister = findViewById(R.id.btnRegister);
        textBackToLogin = findViewById(R.id.textBackToLogin);

        // Detectar si venimos de "Modificar Registro"
        isEditMode = getIntent().getBooleanExtra("MODO_EDICION", false);

        if (isEditMode) {
            setupEditMode();
        }

        btnSelectPhoto.setOnClickListener(v -> openGallery());
        btnRegister.setOnClickListener(v -> {
            if (isEditMode) actualizar();
            else registrar();
        });
        
        textBackToLogin.setOnClickListener(v -> finish());
    }

    private void setupEditMode() {
        // Cambiar textos visuales
        if (textTitle != null) textTitle.setText("Modificar mi Perfil");
        btnRegister.setText("Guardar Cambios");
        
        SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);

        // Cargar datos actuales del servidor
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
                        editEdad.setText(String.valueOf(json.getInt("edad")));
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

    private void actualizar() {
        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String edadStr = editEdad.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (nombre.isEmpty() || apellidos.isEmpty() || edadStr.isEmpty() || email.isEmpty() || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.actualizarUsuario(currentUserId, nombre, apellidos, Integer.parseInt(edadStr), email, telefono, password, encodedImage);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Perfil actualizado", Toast.LENGTH_SHORT).show();
                    
                    // Actualizar nombre en SharedPreferences por si cambió
                    SharedPreferences pref = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
                    pref.edit().putString("nombre", nombre).apply();
                    
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

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                imgUser.setImageBitmap(bitmap);
                encodedImage = encodeImage(bitmap);
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private String encodeImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }

    private void registrar() {
        String nombre = editNombre.getText().toString().trim();
        String apellidos = editApellidos.getText().toString().trim();
        String edadStr = editEdad.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String telefono = editTelefono.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (nombre.isEmpty() || apellidos.isEmpty() || edadStr.isEmpty() || email.isEmpty() || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.registrarUsuario(nombre, apellidos, Integer.parseInt(edadStr), email, telefono, password, encodedImage);

        call.enqueue(new Callback<ResponseBody>() {
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
}
