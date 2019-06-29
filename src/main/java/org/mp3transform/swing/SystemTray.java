package org.mp3transform.swing;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SystemTray {
    private Player player;
    private Boolean systemTrayAvailable;
    private Image icon;

    Boolean isSystemTrayAvailable() {
        return systemTrayAvailable;
    }

    private void readIcon() {
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                String fileName = getClass().getResource("/mp3-play.png").getFile();
                byte[] imageData = Files.readAllBytes(Paths.get(fileName));
                icon = Toolkit.getDefaultToolkit().createImage(imageData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    SystemTray(Player player) {
        this.player = player;
        readIcon();
        systemTrayAvailable = createTrayIcon();
    }

    private boolean createTrayIcon() {
        try {
            PopupMenu menuConsole = new PopupMenu();
            MenuItem itemConsole = new MenuItem(Player.TITLE);
            itemConsole.setActionCommand("open");
            itemConsole.addActionListener(player.getActionListener());
            itemConsole.setFont(player.getFont());
            menuConsole.add(itemConsole);

            MenuItem itemNext = new MenuItem("Next");
            itemNext.setActionCommand("next");
            itemNext.addActionListener(player.getActionListener());
            itemNext.setFont(player.getFont());
            menuConsole.add(itemNext);

            MenuItem itemPause = new MenuItem("Pause");
            itemPause.setActionCommand("pause");
            itemPause.addActionListener(player.getActionListener());
            itemPause.setFont(player.getFont());
            menuConsole.add(itemPause);

            MenuItem itemStop = new MenuItem("Stop");
            itemStop.setActionCommand("stop");
            itemStop.addActionListener(player.getActionListener());
            itemStop.setFont(player.getFont());
            menuConsole.add(itemStop);

            MenuItem itemExit = new MenuItem("Exit");
            itemExit.setFont(player.getFont());
            itemExit.setActionCommand("exit");
            itemExit.addActionListener(player.getActionListener());
            menuConsole.add(itemExit);

            TrayIcon trayIcon = new TrayIcon(icon, Player.TITLE, menuConsole);
            java.awt.SystemTray.getSystemTray().add(trayIcon);

            trayIcon.addMouseListener(player.getMouseListener());
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
}
