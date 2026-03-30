package com.example.chat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.chat.R;
import com.example.chat.models.Sala;
import com.example.chat.network.RetrofitClient;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
public class ScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 1001;
    private static final String TAG = "ScannerActivity";

    private PreviewView previewView;
    private ProgressBar progressBar;
    private ExecutorService cameraExecutor;
    private ImageAnalysis imageAnalysisRef;
    private ProcessCameraProvider cameraProviderRef;

    // Instancia única del scanner reutilizada para reactivar sin conflictos
    private final BarcodeScanner barcodeScanner = BarcodeScanning.getClient(
            new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
    );

    private final AtomicBoolean procesando = new AtomicBoolean(false);
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressScanner);
        cameraExecutor = Executors.newSingleThreadExecutor();

        currentUserId = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .getInt("id_usuario", -1);

        EditText editSalaManual = findViewById(R.id.editSalaManual);
        Button btnEntrarManual = findViewById(R.id.btnEntrarManual);

        btnEntrarManual.setOnClickListener(v -> {
            String sala = editSalaManual.getText().toString().trim().toUpperCase();
            if (sala.isEmpty()) {
                Toast.makeText(this, "Escribe el nombre de la sala", Toast.LENGTH_SHORT).show();
                return;
            }
            ocultarTeclado(editSalaManual);
            if (procesando.compareAndSet(false, true)) {
                imageAnalysisRef = imageAnalysisRef != null ? imageAnalysisRef : null;
                if (imageAnalysisRef != null) imageAnalysisRef.clearAnalyzer();
                progressBar.setVisibility(View.VISIBLE);
                procesarQR(sala);
            }
        });

        editSalaManual.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnEntrarManual.performClick();
                return true;
            }
            return false;
        });

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        // Delay para que la instancia anterior de la cámara se libere completamente
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
            future.addListener(() -> {
                try {
                    bindPreview(future.get());
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Error al iniciar cámara", e);
                }
            }, ContextCompat.getMainExecutor(this));
        }, 600);
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProviderRef = cameraProvider;
        androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageAnalysisRef = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetResolution(new android.util.Size(1280, 720))
                .build();

        activarAnalizador();

        cameraProvider.unbindAll();

        // Intentar primero cámara trasera, luego frontal, luego cualquiera disponible
        CameraSelector cameraSelector;
        if (!cameraProvider.getAvailableCameraInfos().isEmpty()) {
            boolean tieneTraser = false;
            try { tieneTraser = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA); } catch (Exception ignored) {}
            cameraSelector = tieneTraser
                    ? CameraSelector.DEFAULT_BACK_CAMERA
                    : CameraSelector.DEFAULT_FRONT_CAMERA;
        } else {
            // Emulador u otros dispositivos sin cámara estándar: intentar con filtro abierto
            cameraSelector = new CameraSelector.Builder()
                    .addCameraFilter(list -> new ArrayList<>(list))
                    .build();
        }

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysisRef);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo enlazar la cámara: " + e.getMessage());
            Toast.makeText(this, "No se pudo iniciar la cámara", Toast.LENGTH_LONG).show();
        }
    }

    /** Asigna (o reasigna) el analizador de QR al ImageAnalysis existente. */
    private void activarAnalizador() {
        imageAnalysisRef.setAnalyzer(cameraExecutor, this::analizarFrame);
    }

    private void analizarFrame(@NonNull ImageProxy imageProxy) {
        // Descartar frames si ya hay un QR en proceso
        if (procesando.get()) {
            imageProxy.close();
            return;
        }

        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String rawValue = barcode.getRawValue();
                        if (rawValue != null && !rawValue.isEmpty()) {
                            if (procesando.compareAndSet(false, true)) {
                                String idSala = rawValue.trim().toUpperCase();
                                Log.d(TAG, "QR detectado: " + idSala);
                                runOnUiThread(() -> {
                                    imageAnalysisRef.clearAnalyzer();
                                    Toast.makeText(this, "QR leído: " + idSala, Toast.LENGTH_SHORT).show();
                                    progressBar.setVisibility(View.VISIBLE);
                                });
                                procesarQR(idSala);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error ML Kit: " + e.getMessage()))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void procesarQR(String idSala) {
        RetrofitClient.getChatApiServices()
                .unirseASala(currentUserId, idSala)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        obtenerInfoSalaYFinalizar(idSala);
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        mostrarError("Error de red: " + t.getMessage());
                    }
                });
    }

    private void obtenerInfoSalaYFinalizar(String idSala) {
        RetrofitClient.getChatApiServices()
                .getSalaInfo(idSala)
                .enqueue(new Callback<Sala>() {
                    @Override
                    public void onResponse(Call<Sala> call, Response<Sala> response) {
                        Intent result = new Intent();
                        result.putExtra("ID_SALA", idSala);
                        if (response.isSuccessful() && response.body() != null) {
                            Sala sala = response.body();
                            result.putExtra("SALA_LATITUD", sala.getLatitud());
                            result.putExtra("SALA_LONGITUD", sala.getLongitud());
                            result.putExtra("SALA_RADIO", sala.getRadioMetros());
                        }
                        setResult(RESULT_OK, result);
                        finish();
                    }

                    @Override
                    public void onFailure(Call<Sala> call, Throwable t) {
                        Intent result = new Intent();
                        result.putExtra("ID_SALA", idSala);
                        setResult(RESULT_OK, result);
                        finish();
                    }
                });
    }

    private void mostrarError(String mensaje) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
            procesando.set(false);
            activarAnalizador(); // Reactiva para reintentar
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) startCamera();
            else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void ocultarTeclado(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProviderRef != null) {
            cameraProviderRef.unbindAll();
        }
        barcodeScanner.close();
        cameraExecutor.shutdown();
    }
}
