package com.example.chat.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.chat.R;
import com.example.chat.models.Mensaje;
import com.example.chat.utils.PrivateChatConversationPolicy;

import java.util.List;

public class MensajeAdapter extends ArrayAdapter<Mensaje> {

    private int currentUserId;
    private int tamanoFuente;

    public interface OnNombreClickListener {
        void onNombreClick(Mensaje mensaje);
    }

    private OnNombreClickListener listener;

    public void setOnNombreClickListener(OnNombreClickListener listener) {
        this.listener = listener;
    }
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
            if (PrivateChatConversationPolicy.isControlMessage(mensaje.getMensaje())) {
                textNombre.setVisibility(View.GONE);
                textFecha.setVisibility(View.GONE);
                textMensaje.setText(PrivateChatConversationPolicy.getSystemText(mensaje, currentUserId));
                textMensaje.setTextSize(13);
                textMensaje.setTextColor(ContextCompat.getColor(getContext(), R.color.chat_system_text));
                textMensaje.setGravity(Gravity.CENTER);
                textNombre.setOnClickListener(null);
                container.setBackgroundColor(Color.TRANSPARENT);
                params.gravity = Gravity.CENTER_HORIZONTAL;
                container.setLayoutParams(params);
                return convertView;
            }

            textNombre.setText(mensaje.getNombre());
            textFecha.setVisibility(View.VISIBLE);
            textMensaje.setGravity(Gravity.START);

            textNombre.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNombreClick(mensaje);
                }
            });

            textMensaje.setText(mensaje.getMensaje());

            String fechaCompleta = mensaje.getFechaHora();

            if (fechaCompleta != null) {
                try {

                    java.text.SimpleDateFormat sdfOriginal =
                            new java.text.SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss",
                                    java.util.Locale.getDefault()
                            );

                    java.util.Date date = sdfOriginal.parse(fechaCompleta);

                    java.util.Calendar calendar = java.util.Calendar.getInstance();
                    calendar.setTime(date);
                    calendar.add(java.util.Calendar.HOUR_OF_DAY, 2);
                    java.text.SimpleDateFormat sdfNuevo =
                            new java.text.SimpleDateFormat(
                                    "HH:mm",
                                    java.util.Locale.getDefault()
                            );
                    textFecha.setText(sdfNuevo.format(calendar.getTime()));
                } catch (Exception e) {
                    if (fechaCompleta.length() >= 16) {
                        textFecha.setText(fechaCompleta.substring(11, 16));
                    } else {
                        textFecha.setText(fechaCompleta);
                    }
                }
            }

            textMensaje.setTextSize(tamanoFuente);

            if (mensaje.getIdUsuario() == currentUserId) {
                container.setBackgroundResource(R.drawable.bubble_me);
                params.gravity = Gravity.END;
                textNombre.setVisibility(View.GONE);
                textMensaje.setTextColor(ContextCompat.getColor(getContext(), R.color.chat_text_sent));
                textFecha.setTextColor(ContextCompat.getColor(getContext(), R.color.chat_time_sent));
            } else {
                container.setBackgroundResource(R.drawable.bubble_others);
                params.gravity = Gravity.START;
                textNombre.setVisibility(View.VISIBLE);
                textMensaje.setTextColor(ContextCompat.getColor(getContext(), R.color.chat_text_received));
                textFecha.setTextColor(ContextCompat.getColor(getContext(), R.color.chat_time_received));
            }
            container.setLayoutParams(params);
        }

        return convertView;
    }
}
