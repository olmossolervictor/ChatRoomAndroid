package com.example.chat;

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

import java.util.List;

public class MensajeAdapter extends ArrayAdapter<Mensaje> {

    private int currentUserId;

    public MensajeAdapter(@NonNull Context context, @NonNull List<Mensaje> objects) {
        super(context, 0, objects);
        SharedPreferences pref = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE);
        currentUserId = pref.getInt("id_usuario", -1);
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
            textMensaje.setText(mensaje.getMensaje());
            textFecha.setText(mensaje.getFechaHora());

            // Si el mensaje lo envié YO
            if (mensaje.getIdUsuario() == currentUserId) {
                container.setBackgroundResource(R.drawable.bubble_me);
                params.gravity = Gravity.END;
                textNombre.setVisibility(View.GONE); // No necesito ver mi propio nombre
            } else {
                // Si el mensaje es de OTROS
                container.setBackgroundResource(R.drawable.bubble_others);
                params.gravity = Gravity.START;
                textNombre.setVisibility(View.VISIBLE);
            }
            container.setLayoutParams(params);
        }

        return convertView;
    }
}
