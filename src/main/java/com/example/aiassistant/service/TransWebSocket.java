package com.example.aiassistant.service;

import org.springframework.stereotype.Component;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint("/trans/ws")
public class TransWebSocket {
    private static final CopyOnWriteArraySet<TransWebSocket> clients = new CopyOnWriteArraySet<>();
    private Session session;
    public static String currLang = "en";

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        clients.add(this);
    }

    @OnClose
    public void onClose() {
        clients.remove(this);
    }

    @OnMessage
    public void onMsg(String msg) {
        currLang = msg;
    }

    public static void sendMsg(String content) {
        for (TransWebSocket c : clients) {
            try {
                c.session.getBasicRemote().sendText(content);
            } catch (IOException ignored) {}
        }
    }

    public static boolean isOnline() {
        return !clients.isEmpty();
    }
}