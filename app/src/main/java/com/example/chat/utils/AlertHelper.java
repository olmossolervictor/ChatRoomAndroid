package com.example.chat.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Vibrator;
import android.view.View;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.example.chat.R;
import com.google.android.material.snackbar.Snackbar;

public class AlertHelper {

    public enum AlertType {
        ERROR, WARNING, SUCCESS, INFO
    }

    /**
     * Despliega un Snackbar con estilos personalizados según el tipo de alerta.
     * 
     * @param view    Vista de referencia para el anclaje del Snackbar.
     * @param message Mensaje descriptivo a mostrar.
     * @param type    Nivel de severidad de la alerta (ERROR, WARNING, SUCCESS, INFO).
     */
    public static void showActionAlert(View view, String message, AlertType type) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();
        
        int bgColor;
        int textColor;

        switch (type) {
            case ERROR:
                bgColor = ContextCompat.getColor(view.getContext(), R.color.error_bg);
                textColor = ContextCompat.getColor(view.getContext(), R.color.error_main);
                ejecutarVibracion(view.getContext());
                break;
            case WARNING:
                bgColor = ContextCompat.getColor(view.getContext(), R.color.warning_bg);
                textColor = ContextCompat.getColor(view.getContext(), R.color.warning_main);
                break;
            case SUCCESS:
                bgColor = ContextCompat.getColor(view.getContext(), R.color.success_bg);
                textColor = ContextCompat.getColor(view.getContext(), R.color.success_main);
                break;
            case INFO:
            default:
                bgColor = ContextCompat.getColor(view.getContext(), R.color.info_bg);
                textColor = ContextCompat.getColor(view.getContext(), R.color.info_main);
                break;
        }

        snackbarView.setBackgroundColor(bgColor);
        TextView tv = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextColor(textColor);
            tv.setTextSize(15);
            tv.setMaxLines(3);
        }

        snackbar.show();
    }

    /**
     * Proporciona feedback háptico mediante una vibración corta de sistema.
     * 
     * @param context Contexto de la aplicación para acceder al servicio de vibración.
     */
    private static void ejecutarVibracion(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            v.vibrate(50);
        }
    }
}
