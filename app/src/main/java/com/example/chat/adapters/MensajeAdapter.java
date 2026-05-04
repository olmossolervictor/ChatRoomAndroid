package com.example.chat.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.chat.R;
import com.example.chat.models.Mensaje;

import java.util.List;

public class MensajeAdapter extends ArrayAdapter<Mensaje> {

    private int currentUserId;
    private int tamanoFuente;

    // --- NUEVO: INTERFAZ Y SETTER PARA EL CLIC DEL NOMBRE ---
    public interface OnNombreClickListener {
        void onNombreClick(Mensaje mensaje);
    }

    private OnNombreClickListener listener;

    public void setOnNombreClickListener(OnNombreClickListener listener) {
        this.listener = listener;
    }
    // --------------------------------------------------------

    public MensajeAdapter(@NonNull Context context, @NonNull List<Mensaje> objects) {
        super(context, 0, objects);
        SharedPreferences pref = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);
        tamanoFuente = context.getSharedPreferences("AjustesPrefs", Context.MODE_PRIVATE)
                .getInt("tamano_fuente", 15);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_mensaje, parent, false);
        }

        Mensaje mensaje = getItem(position);

        LinearLayout container = convertView.findViewById(R.id.containerMensaje);
        TextView textNombre = convertView.findViewById(R.id.textNombre);
        TextView textMensaje = convertView.findViewById(R.id.textMensaje);
        TextView textFecha = convertView.findViewById(R.id.textFecha);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) container.getLayoutParams();

        if (mensaje != null) {
            textNombre.setText(mensaje.getNombre());

            // --- NUEVO: DETECTAR CLIC SOLO EN EL NOMBRE ---
            textNombre.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNombreClick(mensaje);
                }
            });
            // ----------------------------------------------

            textMensaje.setText(mensaje.getMensaje());

            // --- MAGIA DEL FORMATO DE HORA ---
            String fechaCompleta = mensaje.getFechaHora();
            if (fechaCompleta != null) {
                try {
                    // 1. Leemos el formato completo que manda tu servidor
                    java.text.SimpleDateFormat sdfOriginal = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                    java.util.Date date = sdfOriginal.parse(fechaCompleta);

                    // 2. Lo pasamos al formato corto (solo HH:mm)
                    java.text.SimpleDateFormat sdfNuevo = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                    textFecha.setText(sdfNuevo.format(date));

                } catch (Exception e) {
                    // Plan B: Si falla, recortamos las letras a la fuerza (Ej: de "2026-03-30 16:45:22" coge "16:45")
                    if (fechaCompleta.length() >= 16) {
                        textFecha.setText(fechaCompleta.substring(11, 16));
                    } else {
                        textFecha.setText(fechaCompleta); // Si no se puede hacer nada, lo pone tal cual
                    }
                }
            } else {
                textFecha.setText("");
            }
            // ---------------------------------

            // Aplicamos el tamaño de fuente al texto del mensaje (¡pero NO sobrescribimos la fecha!)
            textMensaje.setTextSize(tamanoFuente);

            if (mensaje.getIdUsuario() == currentUserId) {
                container.setBackgroundResource(R.drawable.bubble_me);
                params.gravity = Gravity.END;
                textNombre.setVisibility(View.GONE);
            } else {
                container.setBackgroundResource(R.drawable.bubble_others);
                params.gravity = Gravity.START;
                textNombre.setVisibility(View.VISIBLE);
            }
            container.setLayoutParams(params);
        }

        return convertView;
    }
}