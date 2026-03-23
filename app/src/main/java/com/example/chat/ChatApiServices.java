package com.example.chat;

import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ChatApiServices {

    // --- USUARIOS ---

    @FormUrlEncoded
    @POST("usuarios/registrar") // Antes era registro.php
    Call<ResponseBody> registrarUsuario(
            @Field("nombre") String nombre,
            @Field("apellidos") String apellidos,
            @Field("edad") int edad,
            @Field("email") String email,
            @Field("telefono") String telefono,
            @Field("password") String password,
            @Field("foto") String foto
    );

    @FormUrlEncoded
    @POST("usuarios/actualizar/{id_usuario}") // Ruta dinámica
    Call<ResponseBody> actualizarUsuario(
            @Path("id_usuario") int idUsuario,
            @Field("nombre") String nombre,
            @Field("apellidos") String apellidos,
            @Field("edad") int edad,
            @Field("email") String email,
            @Field("telefono") String telefono,
            @Field("password") String password,
            @Field("foto") String foto
    );

    @GET("usuarios/{id_usuario}") // Antes era get_usuario.php
    Call<ResponseBody> getUsuario(
            @Path("id_usuario") int idUsuario
    );

    @FormUrlEncoded
    @POST("usuarios/login") // Antes era login.php
    Call<ResponseBody> loginUsuario(
            @Field("email") String email,
            @Field("password") String password
    );

    // --- CHAT GRUPAL (SALA) ---

    @FormUrlEncoded
    @POST("chat/unirse")
    Call<ResponseBody> unirseASala(
            @Field("id_usuario") int idUsuario,
            @Field("id_sala") String idSala
    );

    @GET("chat/mensajes/{id_sala}")
    Call<List<Mensaje>> getMensajesGrupal(
            @Path("id_sala") String idSala
    );

    @FormUrlEncoded
    @POST("chat/enviar")
    Call<ResponseBody> enviarMensajeGrupal(
            @Field("id_sala") String idSala,
            @Field("id_usuario") int idUsuario,
            @Field("mensaje") String mensaje
    );

    // --- CHAT PRIVADO ---

    @FormUrlEncoded
    @POST("chat/privado/crear")
    Call<ResponseBody> crearChatPrivado(
            @Field("id_usuario_1") int idUsuario1,
            @Field("id_usuario_2") int idUsuario2
    );

    @GET("chat/privado/mensajes")
    Call<List<Mensaje>> getMensajesPrivados(
            @Query("id_chat_privado") int idChatPrivado
    );

    @FormUrlEncoded
    @POST("chat/privado/enviar")
    Call<ResponseBody> enviarMensajePrivado(
            @Field("id_chat_privado") int idChatPrivado,
            @Field("id_usuario_emisor") int idUsuarioEmisor,
            @Field("mensaje") String mensaje
    );

    @GET("chat/verificar-sesion")
    Call<ResponseBody> verificarSesionSala(
            @Query("id_usuario") int idUsuario,
            @Query("id_sala") String idSala
    );
}
