package com.example.chat.utils;

import com.example.chat.models.Mensaje;

import java.util.List;

public final class PrivateChatConversationPolicy {

    private static final String CONTROL_PREFIX = "[[PRIVATE_CHAT_STATUS:";
    private static final String CONTROL_ACCEPTED = CONTROL_PREFIX + "ACCEPTED]]";
    private static final String CONTROL_REJECTED = CONTROL_PREFIX + "REJECTED]]";

    public enum State {
        EMPTY,
        PENDING_INCOMING,
        PENDING_OUTGOING,
        ACCEPTED,
        REJECTED
    }

    private PrivateChatConversationPolicy() {
    }

    public static String acceptedMessage() {
        return CONTROL_ACCEPTED;
    }

    public static String rejectedMessage() {
        return CONTROL_REJECTED;
    }

    public static boolean isControlMessage(String message) {
        return message != null && message.startsWith(CONTROL_PREFIX);
    }

    public static boolean isAcceptedControl(String message) {
        return CONTROL_ACCEPTED.equals(message);
    }

    public static boolean isRejectedControl(String message) {
        return CONTROL_REJECTED.equals(message);
    }

    public static State resolveState(List<Mensaje> messages, int currentUserId) {
        if (messages == null || messages.isEmpty()) {
            return State.EMPTY;
        }

        Mensaje firstNormalMessage = null;
        State lastControlState = null;

        for (Mensaje message : messages) {
            if (message == null) continue;

            String text = message.getMensaje();
            if (isAcceptedControl(text)) {
                lastControlState = State.ACCEPTED;
                continue;
            }
            if (isRejectedControl(text)) {
                lastControlState = State.REJECTED;
                continue;
            }
            if (!isControlMessage(text) && firstNormalMessage == null) {
                firstNormalMessage = message;
            }
        }

        if (lastControlState != null) {
            return lastControlState;
        }

        if (firstNormalMessage == null) {
            return State.EMPTY;
        }

        return firstNormalMessage.getIdUsuario() == currentUserId
                ? State.PENDING_OUTGOING
                : State.PENDING_INCOMING;
    }

    public static boolean canSendMessage(State state) {
        return state == State.EMPTY || state == State.ACCEPTED;
    }

    public static String getSystemText(Mensaje message, int currentUserId) {
        if (message == null) return "";

        boolean mine = message.getIdUsuario() == currentUserId;
        String nombre = message.getNombre() == null || message.getNombre().trim().isEmpty()
                ? "La otra persona"
                : message.getNombre();
        String actor = mine ? "Has" : nombre + " ha";

        if (isAcceptedControl(message.getMensaje())) {
            return actor + " aceptado la conversacion. Ya se puede hablar.";
        }
        if (isRejectedControl(message.getMensaje())) {
            return actor + " rechazado la conversacion.";
        }
        return "";
    }
}
