package com.example.chat.network;

import com.example.chat.models.Mensaje;
import com.example.chat.models.Sala;

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
    @POST("usuarios/registrar")
    Call<ResponseBody> registrarUsuario(
            @Field("nombre") String nombre,
            @Field("apellidos") String apellidos,
            @Field("fecha_nacimiento") String fecha_nacimiento,
            @Field("email") String email,
            @Field("telefono") String telefono,
            @Field("password") String password,
            @Field("foto") String foto
    );

    @FormUrlEncoded
    @POST("usuarios/actualizar/{id_usuario}")
    Call<ResponseBody> actualizarUsuario(
            @Path("id_usuario") int idUsuario,
            @Field("nombre") String nombre,
            @Field("apellidos") String apellidos,
            @Field("fecha_nacimiento") String fecha_nacimiento,
            @Field("email") String email,
            @Field("telefono") String telefono,
            @Field("password") String password,
            @Field("foto") String foto
    );

    @GET("usuarios/{id_usuario}")
    Call<ResponseBody> getUsuario(
            @Path("id_usuario") int idUsuario
    );

    @FormUrlEncoded
    @POST("usuarios/login")
    Call<ResponseBody> loginUsuario(
            @Field("email") String email,
            @Field("password") String password
    );

    @FormUrlEncoded
    @POST("usuarios/login-google")
    Call<ResponseBody> loginConGoogle(
            @Field("id_token") String idToken
    );

    @FormUrlEncoded
    @POST("usuarios/reenviar-verificacion")
    Call<ResponseBody> reenviarVerificacionEmail(
            @Field("email") String email
    );

    // --- SALAS ---

    @GET("salas/mis-salas")
    Call<List<Sala>> getMisSalas(
            @Query("id_usuario") int idUsuario
    );

    @GET("salas/{id_sala}")
    Call<Sala> getSalaInfo(
            @Path("id_sala") String idSala
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

    @GET("chat/verificar-sesion")
    Call<ResponseBody> verificarSesionSala(
            @Query("id_usuario") int idUsuario,
            @Query("id_sala") String idSala
    );

    @GET("usuarios/{id_usuario}/rol")
    Call<ResponseBody> getRolUsuario(
            @Path("id_usuario") int idUsuario
    );

    @GET("usuarios/check-email")
    Call<ResponseBody> checkEmailExistente(
            @Query("email") String email
    );

    @GET("usuarios/buscar")
    Call<ResponseBody> buscarUsuarioPorEmail(
            @Query("email") String email
    );

    @FormUrlEncoded
    @POST("usuarios/cambiar-rol")
    Call<ResponseBody> cambiarRolUsuario(
            @Field("id_usuario") int idUsuario,
            @Field("nuevo_rol") String nuevoRol
    );

    @FormUrlEncoded
    @POST("salas/salir")
    Call<ResponseBody> salirDeSala(
            @Field("id_usuario") int idUsuario,
            @Field("id_sala") String idSala
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

    // --- EMAIL VERIFICATION ---

    @FormUrlEncoded
    @POST("usuarios/verificar-codigo")
    Call<ResponseBody> verificarCodigo(
            @Field("email") String email,
            @Field("codigo") String codigo
    );

    @FormUrlEncoded
    @POST("usuarios/verificar-google")
    Call<ResponseBody> verificarConGoogle(
            @Field("id_token") String idToken
    );
}
