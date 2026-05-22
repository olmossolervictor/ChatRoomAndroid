package com.example.chat.models;

import com.google.gson.annotations.SerializedName;

public class Sala {
    private String id_sala;
    private String nombre;
    private double latitud;
    private double longitud;
    private double radio_metros;
    private int tiempo_maximo;
    private String estado;
    @SerializedName(value = "minutos_restantes", alternate = {"minutosRestantes", "tiempo_restante", "tiempoRestante", "remaining_minutes"})
    private long minutos_restantes = -1;

    public String getIdSala() { return id_sala; }
    public String getNombre() { return nombre; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
    public double getRadioMetros() { return radio_metros; }
    public int getTiempoMaximo() { return tiempo_maximo; }
    public String getEstado() { return estado != null ? estado : "activo"; }
    public long getMinutosRestantes() { return minutos_restantes; }
    public void setMinutosRestantes(long minutosRestantes) { this.minutos_restantes = minutosRestantes; }
}
