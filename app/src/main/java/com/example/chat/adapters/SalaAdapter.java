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
            String nombre = sala.getNombre() != null ? sala.getNombre() : sala.getIdSala();
            textNombreSala.setText(nombre);
        }

        return convertView;
    }
}
