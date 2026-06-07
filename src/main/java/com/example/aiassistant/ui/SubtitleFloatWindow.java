package com.example.aiassistant.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import org.glassfish.tyrus.client.ClientManager;

@ClientEndpoint
public class SubtitleFloatWindow extends JFrame {
    private final JLabel textLabel;
    private int offsetX;
    private int offsetY;
    // 最小窗口宽度，防止文字过短缩成一条
    private static final int MIN_WIDTH = 200;
    private static final int FIX_HEIGHT = 90;

    public SubtitleFloatWindow() {
        setUndecorated(true);
        setAlwaysOnTop(true);
        // 初始先设最小尺寸
        setSize(MIN_WIDTH, FIX_HEIGHT);
        setLocationRelativeTo(null);
        setBackground(new Color(0, 0, 0, 80));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        textLabel = new JLabel("等待连接字幕服务...");
        textLabel.setForeground(Color.WHITE);
        textLabel.setFont(new Font("微软雅黑", Font.PLAIN, 24));
        textLabel.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        getContentPane().add(textLabel);

        // 拖动逻辑
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                offsetX = e.getX();
                offsetY = e.getY();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int x = getX() + e.getX() - offsetX;
                int y = getY() + e.getY() - offsetY;
                setLocation(x, y);
            }
        });

        setVisible(true);
        connectWebSocket();
        // 初始化适配一次文字宽度
        adjustWindowSize();
    }

    /**
     * 根据文字自动调整窗口大小
     */
    private void adjustWindowSize() {
        // 获取文字所需宽度
        Dimension labelSize = textLabel.getPreferredSize();
        int targetWidth = labelSize.width;
        // 不小于最小宽度
        if (targetWidth < MIN_WIDTH) {
            targetWidth = MIN_WIDTH;
        }
        // 重新设置窗口尺寸，高度固定
        setSize(targetWidth, FIX_HEIGHT);
    }

    private void connectWebSocket() {
        try {
            WebSocketContainer container = ClientManager.createClient();
            container.connectToServer(this, new URI("ws://localhost:8080/trans/ws"));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                textLabel.setText("WebSocket 连接失败");
                adjustWindowSize();
            });
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onReceiveMsg(String msg) {
        SwingUtilities.invokeLater(() -> {
            textLabel.setText(msg);
            // 每次更新文字 → 自动适配窗口
            adjustWindowSize();
        });
    }

    public static void startWindow() {
        SwingUtilities.invokeLater(SubtitleFloatWindow::new);
    }
}