package org.mp3transform.swing;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class Player {
    private static final String PREF_LISTENER_PORT = "listenerPort";
    static final String TITLE = "MP3 Player";
    private static final int FIRST_PORT = 11100;

    // Events Listener
    private MouseListener mouseListener;
    private ActionListener actionListener;

    private JButton playButton;
    private JButton stopButton;
    private JFrame mainFrame;
    private SystemTray systemTray;
    private MusicListPane musicListPane;

    private Font font;
    private ServerSocket serverSocket;
    private String playingText;
    private PlayerThread thread;

    private File currentPlaying;
    private PlayStatus currentStatus;

    private Preferences prefs = Preferences.userNodeForPackage(getClass());

    private void exit() {
        if (thread != null) {
            thread.stopPlaying();
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            // ignore
        }
        serverSocket = null;
        prefs.remove(PREF_LISTENER_PORT);
        System.exit(0);
    }

    public static void main(String[] args) {
        new Player().run();
    }


    public void run() {
        if (alreadyStarted()) {
            return;
        }
        initEventListener();
        initFrame();
        systemTray = new SystemTray(this);
        currentStatus = PlayStatus.STOP;
    }

    void playList(List<File> fileList) {
        if (thread != null) {
            thread.stopPlaying();
            thread = null;
        }
        thread = PlayerThread.startPlaying(this, fileList);
    }

    private void initEventListener() {
        mouseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    mainFrame.setVisible(true);
                }
            }
        };
        actionListener = e -> {
            String command = e.getActionCommand();
            if ("exit".equals(command)) {
                exit();
            } else if ("play".equals(command)) {
                playList(new ArrayList<>(musicListPane.getCurrentSelected()));
            } else if ("stop".equals(command)) {
                if (thread != null) {
                    thread.stopPlaying();
                }
            } else if ("pause".equals(command)) {
                if (thread != null) {
                    thread.pause();
                    setPlayStatus(currentPlaying, currentStatus == PlayStatus.PLAYING ? PlayStatus.PAUSE : PlayStatus.PLAYING);
                } else {
                    mainFrame.setTitle(TITLE);
                }
            } else if ("next".equals(command)) {
                if (thread != null) {
                    thread.playNext();
                }
            } else if ("open".equals(command)) {
                mainFrame.setVisible(true);
            }
        };
    }

    Font getFont() {
        return font;
    }

    ActionListener getActionListener() {
        return actionListener;
    }

    MouseListener getMouseListener() {
        return mouseListener;
    }

    private void initFrame() {
        font = new Font("Dialog", Font.PLAIN, 12);
        mainFrame = new JFrame(TITLE);
        mainFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                if (systemTray.isSystemTrayAvailable()) {
                    mainFrame.setVisible(false);
                } else {
                    exit();
                }
            }
        });
        Dimension mainFrameDimension = ComponentDimension.FRAME_INIT.getDimension();
        mainFrame.setResizable(true);
        mainFrame.setBackground(SystemColor.control);
        mainFrame.setSize(mainFrameDimension);
        mainFrame.setMinimumSize(ComponentDimension.FRAME_MIN.getDimension());
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        mainFrame.setLocation((int)(screenSize.width - mainFrameDimension.getWidth()) / 2,
                (int)(screenSize.height - mainFrameDimension.getHeight()) / 2);

        JPanel panel = new JPanel();
        GridBagLayout layout = new GridBagLayout();
        panel.setLayout(layout);
        GridBagConstraints gbc = new GridBagConstraints();

        playButton = new JButton(PlayStatus.STOP.getNextStep());
        playButton.setFocusable(true);
        playButton.setPreferredSize(ComponentDimension.BUTTON_1.getDimension());
        playButton.addActionListener(actionListener);
        playButton.setActionCommand("play");
        playButton.setFont(font);
        playButton.setEnabled(false);
        // Layout
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(playButton, gbc);

        stopButton = new JButton("Stop");
        stopButton.setFocusable(true);
        stopButton.setPreferredSize(ComponentDimension.BUTTON_1.getDimension());
        stopButton.addActionListener(actionListener);
        stopButton.setActionCommand("stop");
        stopButton.setFont(font);
        stopButton.setEnabled(false);
        // Layout
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(stopButton, gbc);

        JButton nextButton = new JButton("Next");
        nextButton.setFocusable(true);
        nextButton.setPreferredSize(ComponentDimension.BUTTON_1.getDimension());
        nextButton.addActionListener(actionListener);
        nextButton.setActionCommand("next");
        nextButton.setFont(font);
        // Layout
        gbc.gridx = 2;
        gbc.gridy = 0;
        panel.add(nextButton, gbc);

        JPanel emptyPanel = new JPanel();
        emptyPanel.setPreferredSize(ComponentDimension.EMPTY_PANEL_1.getDimension());
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(emptyPanel, gbc);

        musicListPane = new MusicListPane(this);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        panel.add(musicListPane, gbc);


        // Show mainFrame
        mainFrame.add(panel);
        mainFrame.setVisible(true);
    }

    Preferences getPrefs() {
        return prefs;
    }

    private boolean alreadyStarted() {
        int port = 0;
        try {
            port = prefs.getInt(PREF_LISTENER_PORT, 0);
            if (port != 0) {
                Socket socket = new Socket(InetAddress.getLocalHost(), port);
                socket.close();
                // could connect, that means the application already runs
                System.err.println("Already running, listening on port " + port);
                return true;
            }
        } catch (Exception e) {
            System.err.println("No running Mp3 player is detected.");
        }
        for (int i = 0; i < 100; i++) {
            try {
                int p = FIRST_PORT + i;
                serverSocket = new ServerSocket(p);
                port = p;
                break;
            } catch (IOException e) {
                // ignore
            }
        }
        if (port == 0) {
            // did not work, probably TCP/IP is broken
            throw new RuntimeException("Fail to create a port listener. Please try again.");
        }
        prefs.putInt(PREF_LISTENER_PORT, port);
        Runnable runnable = () -> {
            while (serverSocket != null) {
                try {
                    Socket s = serverSocket.accept();
                    s.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.setName(getClass().getName() + " network listener");
        thread.start();
        return false;
    }

    void setPlayStatus(File file, PlayStatus status) {
        currentPlaying = file;
        currentStatus = status;
        String name = file == null ? "" : file.getName();
        switch (status) {
            case PLAYING:
                playButton.setEnabled(true);
                playButton.setText(PlayStatus.PLAYING.getNextStep());
                playButton.setActionCommand("pause");
                playingText = "Playing - " + name;
                stopButton.setEnabled(true);
                break;
            case PAUSE:
                playButton.setEnabled(true);
                playButton.setText(PlayStatus.PAUSE.getNextStep());
                playButton.setActionCommand("pause");
                playingText = "Paused - " + name;
                break;
            case STOP:
                playButton.setText(PlayStatus.STOP.getNextStep());
                playButton.setActionCommand("play");
                playButton.setEnabled(!musicListPane.getCurrentSelected().isEmpty());
                stopButton.setEnabled(false);
        }
        mainFrame.setTitle(currentPlaying == null ? TITLE : playingText);
    }

    PlayStatus getPlayStatus() {
        return currentStatus;
    }
}
