package com.example.chat.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.chat.R;
import com.example.chat.models.Sala;
import com.example.chat.network.RetrofitClient;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SalaAdapter extends ArrayAdapter<Sala> {

    public SalaAdapter(@NonNull Context context, @NonNull List<Sala> salas) {
        super(context, 0, salas);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_sala, parent, false);
        }

        Sala sala = getItem(position);
        if (sala != null) {
            TextView textNombreSala = convertView.findViewById(R.id.textNombreSala);
            TextView textTiempoSala = convertView.findViewById(R.id.textTiempoSala);

            // Esta es la capa superior que vamos a deslizar
            View capaFrente = convertView.findViewById(R.id.capaFrenteSala);

            // ⚠️ SÚPER IMPORTANTE: Reiniciar la posición de la capa.
            // Como ListView recicla las filas, si no hacemos esto, algunas filas
            // aparecerán ya deslizadas cuando hagas scroll hacia abajo.
            capaFrente.setTranslationX(0);

            String nombre = sala.getNombre() != null ? sala.getNombre() : sala.getIdSala();
            textNombreSala.setText(nombre);

            long minutos = sala.getMinutosRestantes();
            if (minutos >= 0) {
                String tiempoTexto;
                if (minutos >= 60) {
                    tiempoTexto = (minutos / 60) + "h " + (minutos % 60) + "min restantes";
                } else {
                    tiempoTexto = minutos + " min restantes";
                }
                textTiempoSala.setText(tiempoTexto);
                int color;
                if (minutos >= 60) {
                    color = ContextCompat.getColor(getContext(), R.color.success_main);
                } else if (minutos >= 30) {
                    color = ContextCompat.getColor(getContext(), R.color.warning_main);
                } else {
                    color = ContextCompat.getColor(getContext(), R.color.error_main);
                }
                textTiempoSala.setTextColor(color);
                textTiempoSala.setVisibility(View.VISIBLE);
            } else {
                textTiempoSala.setVisibility(View.GONE);
            }

            // --- LÓGICA DE GESTO: DESLIZAR PARA ABANDONAR SALA ---
            capaFrente.setOnTouchListener(new View.OnTouchListener() {
                float startX, startY;
                boolean isSwiping = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            startY = event.getRawY();
                            isSwiping = false;
                            break;

                        case MotionEvent.ACTION_MOVE:
                            float deltaX = event.getRawX() - startX;
                            float deltaY = Math.abs(event.getRawY() - startY);

                            // Si se mueve horizontalmente hacia la derecha
                            if (deltaX > 20 && deltaX > deltaY) {
                                isSwiping = true;
                                // Bloqueamos el scroll vertical de la lista principal
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                                v.setTranslationX(deltaX);
                                return true;
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (isSwiping) {
                                float width = v.getWidth();
                                // Si arrastramos más de un tercio de la pantalla...
                                if (v.getTranslationX() > width / 3f) {
                                    // Animación de salida hacia la derecha
                                    v.animate().translationX(width).setDuration(250).withEndAction(() -> {

                                        // Obtener el ID del usuario actual de SharedPreferences
                                        SharedPreferences pref = getContext().getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE);
                                        int currentUserId = pref.getInt("id_usuario", -1);
                                        String idSalaAAbandonar = sala.getIdSala() != null ? sala.getIdSala() : sala.getNombre();

                                        // Llamada a la API para salir de la sala
                                        RetrofitClient.getChatApiServices().salirDeSala(currentUserId, idSalaAAbandonar)
                                                .enqueue(new Callback<ResponseBody>() {
                                                    @Override
                                                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                                        // Borramos la sala de la lista visualmente
                                                        remove(sala);
                                                        notifyDataSetChanged();
                                                        Toast.makeText(getContext(), "Has abandonado la sala", Toast.LENGTH_SHORT).show();
                                                    }

                                                    @Override
                                                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                                                        // Si falla, devolvemos la tarjeta a su sitio original
                                                        v.animate().translationX(0).setDuration(250).start();
                                                        Toast.makeText(getContext(), "Error de conexión", Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                    }).start();
                                } else {
                                    // No arrastramos lo suficiente -> Efecto rebote (vuelve a la izquierda)
                                    v.animate().translationX(0).setDuration(250).start();
                                }
                                return true; // El toque se consumió deslizando
                            }
                            break;
                    }
                    return false; // Si no deslizó, permitimos que el clic normal siga funcionando
                }
            });
        }

        return convertView;
    }
}