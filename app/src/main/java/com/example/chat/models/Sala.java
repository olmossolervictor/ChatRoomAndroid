package com.example.chat.models;

public class Sala {
    private String id_sala;
    private String nombre;
    private double latitud;
    private double longitud;
    private double radio_metros;
    private int tiempo_maximo; // en minutos
    private String estado;     // "pendiente", "activo", "inactivo"

    public String getIdSala() { return id_sala; }
    public String getNombre() { return nombre; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
    public double getRadioMetros() { return radio_metros; }
    public int getTiempoMaximo() { return tiempo_maximo; }
    public String getEstado() { return estado != null ? estado : "activo"; }
}
