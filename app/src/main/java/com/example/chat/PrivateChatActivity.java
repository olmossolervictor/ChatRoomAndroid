package com.example.chat;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PrivateChatActivity extends AppCompatActivity {

    private TextView textChatCon;
    private ListView listMessagesPrivate;
    private EditText editMessagePrivate;
    private Button btnSendPrivate;

    private MensajeAdapter adapter;
    private List<Mensaje> listaMensajes = new ArrayList<>();
    private Handler handler = new Handler();
    private Runnable refreshRunnable;

    private int idChatPrivado;
    private int currentUserId;
    private int otherUserId;
    private String otherUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat);

        // Obtener datos del Intent
        idChatPrivado = getIntent().getIntExtra("ID_CHAT_PRIVADO", -1);
        currentUserId = getIntent().getIntExtra("CURRENT_USER_ID", -1);
        otherUserId = getIntent().getIntExtra("OTHER_USER_ID", -1);
        otherUserName = getIntent().getStringExtra("OTHER_USER_NAME");

        textChatCon = findViewById(R.id.textChatCon);
        listMessagesPrivate = findViewById(R.id.listMessagesPrivate);
        editMessagePrivate = findViewById(R.id.editMessagePrivate);
        btnSendPrivate = findViewById(R.id.btnSendPrivate);

        textChatCon.setText("Chat con: " + otherUserName);

        adapter = new MensajeAdapter(this, listaMensajes);
        listMessagesPrivate.setAdapter(adapter);

        btnSendPrivate.setOnClickListener(v -> enviarMensajePrivado());

        obtenerMensajesPrivados();
        iniciarAutoRefresco();
    }

    private void iniciarAutoRefresco() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                obtenerMensajesPrivados();
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(refreshRunnable, 3000);
    }

    private void obtenerMensajesPrivados() {
        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<List<Mensaje>> call = api.getMensajesPrivados(idChatPrivado);

        call.enqueue(new Callback<List<Mensaje>>() {
            @Override
            public void onResponse(Call<List<Mensaje>> call, Response<List<Mensaje>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listaMensajes.clear();
                    listaMensajes.addAll(response.body());
                    adapter.notifyDataSetChanged();
                    listMessagesPrivate.setSelection(adapter.getCount() - 1);
                }
            }

            @Override
            public void onFailure(Call<List<Mensaje>> call, Throwable t) {}
        });
    }

    private void enviarMensajePrivado() {
        String mensaje = editMessagePrivate.getText().toString().trim();
        if (mensaje.isEmpty()) return;

        ChatApiServices api = RetrofitClient.getChatApiServices();
        Call<ResponseBody> call = api.enviarMensajePrivado(idChatPrivado, currentUserId, mensaje);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    editMessagePrivate.setText("");
                    obtenerMensajesPrivados();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(PrivateChatActivity.this, "Error al enviar", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable);
    }
}
