package com.example.chat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Importa Toolbar de androidx
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

import org.json.JSONObject;

import java.util.ArrayList;
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

        Toolbar toolbar = findViewById(R.id.toolbarScanner);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressScanner);
        cameraExecutor = Executors.newSingleThreadExecutor();

        currentUserId = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .getInt("id_usuario", -1);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
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

        CameraSelector cameraSelector;
        if (!cameraProvider.getAvailableCameraInfos().isEmpty()) {
            boolean tieneCamaraTrasera = false;
            try {
                tieneCamaraTrasera = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
            } catch (Exception ignored) {}

            cameraSelector = tieneCamaraTrasera
                    ? CameraSelector.DEFAULT_BACK_CAMERA
                    : CameraSelector.DEFAULT_FRONT_CAMERA;
        } else {
            cameraSelector = new CameraSelector.Builder()
                    .addCameraFilter(ArrayList::new)
                    .build();
        }

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysisRef);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo enlazar la cámara: " + e.getMessage());
            Toast.makeText(this, "No se pudo iniciar la cámara", Toast.LENGTH_LONG).show();
        }
    }

    private void activarAnalizador() {
        imageAnalysisRef.setAnalyzer(cameraExecutor, this::analizarFrame);
    }

    private void analizarFrame(@NonNull ImageProxy imageProxy) {
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
                            // Bloqueamos para que no lea más códigos mientras procesa este
                            if (procesando.compareAndSet(false, true)) {
                                String idSala = rawValue.trim().toUpperCase();
                                Log.d(TAG, "QR detectado: " + idSala);

                                runOnUiThread(() -> {
                                    imageAnalysisRef.clearAnalyzer();
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
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (response.code() == 403) {
                            try {
                                String body = response.errorBody() != null ? response.errorBody().string() : "";
                                JSONObject json = new JSONObject(body);
                                String motivo = json.optString("motivo", "");
                                String msg = motivo.isEmpty()
                                        ? "Has sido expulsado de esta sala"
                                        : "Expulsado de la sala: " + motivo;
                                mostrarError(msg);
                            } catch (Exception e) {
                                mostrarError("Has sido expulsado de esta sala");
                            }
                            return;
                        }
                        obtenerInfoSalaYFinalizar(idSala);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        mostrarError("Error de red: Comprueba tu conexión");
                    }
                });
    }

    private void obtenerInfoSalaYFinalizar(String idSala) {
        RetrofitClient.getChatApiServices()
                .getSalaInfo(idSala)
                .enqueue(new Callback<Sala>() {
                    @Override
                    public void onResponse(@NonNull Call<Sala> call, @NonNull Response<Sala> response) {
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
                    public void onFailure(@NonNull Call<Sala> call, @NonNull Throwable t) {
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
            activarAnalizador(); // Reactivamos el escáner si hubo un error (ej: sala incorrecta)
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permiso de cámara denegado. No se puede escanear.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
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