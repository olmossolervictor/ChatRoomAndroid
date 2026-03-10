package com.example.chat;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ChatApiServices {

    @FormUrlEncoded
    @POST("registro.php")
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
    @POST("actualizar_usuario.php")
    Call<ResponseBody> actualizarUsuario(
            @Field("id_usuario") int idUsuario,
            @Field("nombre") String nombre,
            @Field("apellidos") String apellidos,
            @Field("edad") int edad,
            @Field("email") String email,
            @Field("telefono") String telefono,
            @Field("password") String password,
            @Field("foto") String foto
    );

    @GET("get_usuario.php")
    Call<ResponseBody> getUsuario(
            @Query("id_usuario") int idUsuario
    );

    @FormUrlEncoded
    @POST("login.php")
    Call<ResponseBody> loginUsuario(
            @Field("email") String email,
            @Field("password") String password
    );

    @FormUrlEncoded
    @POST("unirse_sala.php")
    Call<ResponseBody> unirseASala(
            @Field("id_usuario") int idUsuario,
            @Field("id_sala") String idSala
    );

    @GET("get_mensajes_grupal.php")
    Call<List<Mensaje>> getMensajesGrupal(
            @Query("id_sala") String idSala
    );

    @FormUrlEncoded
    @POST("enviar_mensaje_grupal.php")
    Call<ResponseBody> enviarMensajeGrupal(
            @Field("id_sala") String idSala,
            @Field("id_usuario") int idUsuario,
            @Field("mensaje") String mensaje
    );

    @FormUrlEncoded
    @POST("crear_chat_privado.php")
    Call<ResponseBody> crearChatPrivado(
            @Field("id_usuario_1") int idUsuario1,
            @Field("id_usuario_2") int idUsuario2
    );

    @GET("get_mensajes_privados.php")
    Call<List<Mensaje>> getMensajesPrivados(
            @Query("id_chat_privado") int idChatPrivado
    );

    @FormUrlEncoded
    @POST("enviar_mensaje_privado.php")
    Call<ResponseBody> enviarMensajePrivado(
            @Field("id_chat_privado") int idChatPrivado,
            @Field("id_usuario_emisor") int idUsuarioEmisor,
            @Field("mensaje") String mensaje
    );

    @GET("verificar_sesion_sala.php")
    Call<ResponseBody> verificarSesionSala(
            @Query("id_usuario") int idUsuario,
            @Query("id_sala") String idSala
    );
}
