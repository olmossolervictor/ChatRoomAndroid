package com.example.chat.models;

public class Mensaje {
    private int id;
    private int id_usuario;
    private String nombre;
    private String nombre_usuario;
    private String mensaje;
    private String fecha_hora;

    public Mensaje(int id, int id_usuario, String nombre, String mensaje, String fecha_hora) {
        this.id = id;
        this.id_usuario = id_usuario;
        this.nombre = nombre;
        this.nombre_usuario = nombre;
        this.mensaje = mensaje;
        this.fecha_hora = fecha_hora;
    }

    public int getId() { return id; }
    public int getIdUsuario() { return id_usuario; }
    public String getNombre() {
        // Priorizar nombre_usuario si está disponible, fallback a nombre
        return (nombre_usuario != null && !nombre_usuario.isEmpty()) ? nombre_usuario : nombre;
    }
    public String getMensaje() { return mensaje; }
    public String getFechaHora() { return fecha_hora; }
}
