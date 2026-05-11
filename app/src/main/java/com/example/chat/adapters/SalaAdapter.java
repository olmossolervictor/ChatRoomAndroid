package com.example.chat.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.chat.R;
import com.example.chat.models.Sala;

import java.util.List;

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
                    color = android.graphics.Color.parseColor("#388E3C");
                } else if (minutos >= 30) {
                    color = android.graphics.Color.parseColor("#F57C00");
                } else {
                    color = android.graphics.Color.parseColor("#D32F2F");
                }
                textTiempoSala.setTextColor(color);
                textTiempoSala.setVisibility(View.VISIBLE);
            } else {
                textTiempoSala.setVisibility(View.GONE);
            }
        }

        return convertView;
    }
}
