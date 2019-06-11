package org.mp3transform.awt;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.prefs.Preferences;

import org.mp3transform.alarm.AlarmTask;
import org.mp3transform.alarm.Scheduler;
import org.mp3transform.alarm.ShellTask;
import org.mp3transform.alarm.Task;
import org.mp3transform.alarm.Scheduler.Job;

public class Player {

    private static final String MP3_SUFFIX = ".mp3";
    private static final String PREF_DIR = "dir", PREF_LISTENER_PORT = "listenerPort";
    private static final String TITLE = "MP3 Player";
    private static final int FIRST_PORT = 11100;

    // Events Listener
    private MouseListener mouseListener;
    private ActionListener actionListener;

    private Label playingLabel;
    private Frame frame;

    private boolean useSystemTray;
    private ServerSocket serverSocket;

    private Font font;
    private Image icon;
    private File dir;
    private File[] files;
    private List list;
    private PlayerThread thread;
    private String playingText = "";
    private Preferences prefs = Preferences.userNodeForPackage(getClass());

    /**
     * The command line interface for this tool.
     * The command line options are the same as in the Server tool.
     * 
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new Player().run();
    }

    private Player() {
        mouseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    open();
                }
            }
        };
        actionListener = e -> {
                String command = e.getActionCommand();
                if ("exit".equals(command)) {
                    exit();
                } else if ("back".equals(command)) {
                    if (dir == null) {
                        readFiles(null);
                    } else {
                        readFiles(dir.getParentFile());
                    }
                } else if ("play".equals(command)) {
                    File f = getSelectedFile();
                    if (f != null) {
                        play(f);
                    }
                } else if ("stop".equals(command)) {
                    if (thread != null) {
                        thread.stopPlaying();
                    }
                } else if ("pause".equals(command)) {
                    if (thread != null) {
                        boolean paused = thread.pause();
                        playingLabel.setText((paused ? "paused - " : "") + playingText);
                        frame.setTitle(playingLabel.getText());
                    } else {
                        frame.setTitle(TITLE);
                    }
                } else if ("next".equals(command)) {
                    if (thread != null) {
                        thread.playNext();
                    }
                } else if ("open".equals(command)) {
                    open();
                }
        };
    }
    
    private void schedule() {
        try {
            if (!prefs.nodeExists("tasks")) {
                return;
            }
            Scheduler scheduler = Scheduler.getInstance();
            Preferences tasks = prefs.node("tasks");
            String[] children = tasks.childrenNames();
            for (String name : children) {
                Preferences task = tasks.node(name);
                String when = task.get("when", "* * * * *");
                task.put("when", when);
                String type = task.get("type", "alarm");
                Task t;
                if ("alarm".equals(type)) {
                    t = new AlarmTask(task.get("message", ""));
                } else if ("shell".equals(type)) {
                    t = new ShellTask(task.get("command", ""));
                } else {
                    t = new AlarmTask(task.get("message", ""));
                }
                Job job = scheduler.createJob(when, t);
                scheduler.schedule(job);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            // ignore
        }
    }

    private void run() {
        schedule();
        try {
            int port = prefs.getInt(PREF_LISTENER_PORT, 0);
            if (port != 0) {
                Socket socket = new Socket(InetAddress.getLocalHost(), port);
                socket.close();
                // could connect, that means the application already runs
                System.out.println("Already running, listening on port " + port);
                return;
            }
        } catch (Exception e) {
            // ignore - in this case the application does not run
        }
        startListener();
        if (!GraphicsEnvironment.isHeadless()) {
            font = new Font("Dialog", Font.PLAIN, 11);
            try {
                InputStream in = getClass().getResourceAsStream("/mp3.png");
                if (in != null) {
                    byte[] imageData = readBytesAndClose(in, -1);
                    icon = Toolkit.getDefaultToolkit().createImage(imageData);
                }
                useSystemTray = createTrayIcon();
                readDirectory();
                createFrame();
                open();
                readFiles(dir);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startListener() {
        int port = 0;
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
            return;
        }
        prefs.putInt(PREF_LISTENER_PORT, port);
        Runnable runnable = () -> {
            while (serverSocket != null) {
                try {
                    Socket s = serverSocket.accept();
                    s.close();
                    open();
                } catch (IOException e) {
                    // ignore
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.setName(getClass().getName() + " network listener");
        thread.start();
    }

    private boolean createTrayIcon() {
        try {
            PopupMenu menuConsole = new PopupMenu();
            MenuItem itemConsole = new MenuItem(TITLE);
            itemConsole.setActionCommand("open");
            itemConsole.addActionListener(actionListener);
            itemConsole.setFont(font);
            menuConsole.add(itemConsole);
            
            MenuItem itemNext = new MenuItem("Next");
            itemNext.setActionCommand("next");
            itemNext.addActionListener(actionListener);
            itemNext.setFont(font);
            menuConsole.add(itemNext);
            
            MenuItem itemPause = new MenuItem("Pause");
            itemPause.setActionCommand("pause");
            itemPause.addActionListener(actionListener);
            itemPause.setFont(font);
            menuConsole.add(itemPause);

            MenuItem itemStop = new MenuItem("Stop");
            itemStop.setActionCommand("stop");
            itemStop.addActionListener(actionListener);
            itemStop.setFont(font);
            menuConsole.add(itemStop);

            MenuItem itemExit = new MenuItem("Exit");
            itemExit.setFont(font);
            itemExit.setActionCommand("exit");
            itemExit.addActionListener(actionListener);
            menuConsole.add(itemExit);

            TrayIcon trayIcon = new TrayIcon(icon, TITLE, menuConsole);
            SystemTray.getSystemTray().add(trayIcon);

            trayIcon.addMouseListener(mouseListener);
             return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
    
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
    
    private static void addCovers(ArrayList<Cover> list, File[] files) {
        for (File f : files) {
            if (isCoverImageFile(f)) {
                list.add(new Cover(f));
            } else if (f.isDirectory()) {
                File[] filesInDir = f.listFiles();
                if (filesInDir != null) {
                    addCovers(list, filesInDir);
                }
            }
        }
    }
    
    private Cover[] getCoverList() {
        ArrayList<Cover> list = new ArrayList<>();
        addCovers(list, files);
        Cover[] array = new Cover[list.size()];
        list.toArray(array);
        return array;
    }
    
    private File getSelectedFile() {
        int index = list.getSelectedIndex();
        if (index < 0 || index >= files.length) {
            return null;
        }
        return files[index];
    }

    private void createFrame() {
        frame = new Frame(TITLE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                if (useSystemTray) {
                    frame.setVisible(false);
                } else {
                    exit();
                }
            }
        });
        if (icon != null) {
            frame.setIconImage(icon);
        }
        frame.setResizable(true);
        frame.setBackground(SystemColor.control);
        
        GridBagLayout layout = new GridBagLayout();
        frame.setLayout(layout);

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.CENTER;
        c.insets.left = 2;
        c.insets.right = 2;
        c.insets.top = 2;
        c.insets.bottom = 2;
      
        list = new List(10, false) {
            private static final long serialVersionUID = 1L;
            public Dimension getMinimumSize() {
                return new Dimension(250, 200);
            }
            public Dimension getPreferredSize() {
                return getMinimumSize();
            }
        };
        list.addActionListener(e -> {
            File f = getSelectedFile();
            if (f != null) {
                if (f.isDirectory()) {
                    readFiles(f);
                } else if (isMp3(f)) {
                    play(f);
                }
            }
        });

        Button play = new Button("> Play >");
        play.setFocusable(false);
        play.setActionCommand("play");
        play.addActionListener(actionListener);
        play.setFont(font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.EAST;
        frame.add(play, c);
        
//        Button next = new Button(web ? "\u003a" : ">>");
//        next.setFocusable(false);
//        next.setActionCommand("next");
//        next.addActionListener(this);
//        next.setFont(web ? fontWebdings : font);
//        c.anchor = GridBagConstraints.EAST;
//        c.gridwidth = GridBagConstraints.EAST;
//        frame.add(next, c);

        Button pause = new Button("Pause");
        pause.setFocusable(false);
        pause.setActionCommand("pause");
        pause.addActionListener(actionListener);
        pause.setFont(font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.NORTH;
        frame.add(pause, c);

//        Button covers = new Button("Covers");
//        covers.setFocusable(false);
//        covers.setActionCommand("covers");
//        covers.addActionListener(this);
//        covers.setFont(font);
//        c.anchor = GridBagConstraints.EAST;
//        c.gridwidth = GridBagConstraints.WEST;
//        frame.add(covers, c);

        Button stop = new Button("Stop");
        stop.setFocusable(false);
        stop.setActionCommand("stop");
        stop.addActionListener(actionListener);
        stop.setFont(font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.REMAINDER;
        frame.add(stop, c);

        Button back = new Button("Up");
        back.setFocusable(false);
        back.setActionCommand("back");
        back.addActionListener(actionListener);
        back.setFont(font);
        c.anchor = GridBagConstraints.CENTER;
        c.gridwidth = GridBagConstraints.EAST;
        frame.add(back, c);

        list.setFont(font);
        c.anchor = GridBagConstraints.CENTER;
        c.gridwidth = GridBagConstraints.REMAINDER;
        frame.add(list, c);

        c.anchor = GridBagConstraints.WEST;
        c.gridwidth = GridBagConstraints.REMAINDER;
        playingLabel = new Label() {
            private static final long serialVersionUID = 1L;
            public Dimension getMinimumSize() {
                Dimension d = super.getMinimumSize();
                d.width = 350;
                return d;
            }
            public Dimension getPreferredSize() {
                return getMinimumSize();
            }
        };
        playingLabel.setAlignment(Label.LEFT);
        playingLabel.setFont(font);
        frame.add(playingLabel, c);

        int width = 350, height = 320;
        frame.setSize(width, height);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((screenSize.width - width) / 2, (screenSize.height - height) / 2);
        
        readFiles(dir);
        
    }
    
    private void open() {
        frame.setVisible(true);
    }

    private void readFiles(File dir) {
        File[] allFiles;
        boolean roots = dir == null;
        if (roots) {
            allFiles = File.listRoots();
        } else {
            allFiles = dir.listFiles();
        }
        if (allFiles == null || allFiles.length == 0) {
            return;
        }

        // Sort files by name
        Arrays.sort(allFiles, Comparator.comparing(File::getName));
        // must at least contain one directory or one mp3 file
        ArrayList<File> fileList = new ArrayList<>();
        for (File file : allFiles) {
            if (roots || isMp3(file) || file.isDirectory()) {
                fileList.add(file);
            }
        }
        if (fileList.size() == 0) {
            return;
        }
        this.files = new File[fileList.size()];
        fileList.toArray(files);
        this.dir = dir;
        if (roots) {
            prefs.remove(PREF_DIR);
        } else {
            prefs.put(PREF_DIR, dir.getAbsolutePath());
        }
        Color fg = list.getForeground();
        list.setForeground(list.getBackground());
        list.setFocusable(false);
        list.removeAll();
        for (File f2 : files) {
            if (roots || isMp3(f2) || f2.isDirectory()) {
                String name = f2.getName().trim();
                if (name.length() == 0) {
                    name = f2.getAbsolutePath();
                }
                list.add(getTitle(name));
            }
        }
        list.setForeground(fg);
        list.setFocusable(true);
        list.requestFocus();
    }
    
    private void readDirectory() {
        String s = prefs.get(PREF_DIR, null);
        if (s != null) {
            File f = new File(s);
            if (f.exists()) {
                dir = f;
            }
        }
    }

    void play(File f) {
        if (isMp3(f)) {
            if (thread != null) {
                thread.stopPlaying();
                thread = null;
            }
            thread = PlayerThread.startPlaying(this, f, null);
        } else if (f.isDirectory()) {
            ArrayList<File> files = new ArrayList<>();
            addAll(files, f);
            if (files.size() > 0) {
                for (int i = 0; i < files.size(); i++) {
                    File temp = files.get(i);
                    int x = (int) (Math.random() * files.size());
                    files.set(i, files.get(x));
                    files.set(x, temp);
                }
                if (thread != null) {
                    thread.stopPlaying();
                    thread = null;
                }
                thread = PlayerThread.startPlaying(this, null, files);
            }
        }
    }

    private void addAll(ArrayList<File> arrayList, File file) {
        if (file != null && file.isDirectory()) {
            for (File f : file.listFiles()) {
                addAll(arrayList, f);
            }
        } else if (isMp3(file)) {
            arrayList.add(file);
        }
    }

    private boolean isMp3(File f) {
        return f.getName().toLowerCase().endsWith(MP3_SUFFIX);
    }

    private static boolean isCoverImageFile(File f) {
        int todoSupportPngBmpJpegGif;
        return f.getName().toLowerCase().endsWith(".jpg");
    }

    void setCurrentFile(File file) {
        String name = file == null ? "" : file.getName();
        playingText = getTitle(name);
        playingLabel.setText(playingText);
        frame.setTitle(file == null ? TITLE : playingText);
    }
    
    private static byte[] readBytesAndClose(InputStream in, int length) throws IOException {
        try {
            if (length <= 0) {
                length = Integer.MAX_VALUE;
            }
            int block = Math.min(4 * 1024, length);
            ByteArrayOutputStream out = new ByteArrayOutputStream(block);
            byte[] buff = new byte[block];
            while (length > 0) {
                int len = Math.min(block, length);
                len = in.read(buff, 0, len);
                if (len < 0) {
                    break;
                }
                out.write(buff, 0, len);
                length -= len;
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
    
    private String getTitle(String name) {
        if (name.toLowerCase().endsWith(MP3_SUFFIX)) {
            name = name.substring(0, name.length() - MP3_SUFFIX.length());
        }
        return name;
    }

}
