package org.mp3transform.swing;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.filechooser.FileSystemView;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class MusicListPane extends JPanel {
    private static String PATH_KEY = "musicListPanePaths";
    private Player player;
    private List<Tab> tabContents = new ArrayList<>();
    private JTabbedPane playListPane;

    private ActionListener playListActionListener;
    private MouseAdapter jListMouseAdapter;

    private static final String MP3_SUFFIX = ".mp3";

    private static String getTitle(String name) {
        if (name.toLowerCase().endsWith(MP3_SUFFIX)) {
            name = name.substring(0, name.length() - MP3_SUFFIX.length());
        }
        return name;
    }

    private class Tab {
        private List<File> jListFile;
        private JList<String> jListStr;
        private Path currentDir;

        Tab(List<File> jListFile, Path currentDir) {
            this.jListFile = jListFile;
            this.currentDir = currentDir;
            updatejListStr();
        }

        void updatejListStr() {
            jListStr = new JList<>(generateListModelFromFileList(jListFile));
            jListStr.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            jListStr.setLayoutOrientation(JList.VERTICAL);
            jListStr.setVisibleRowCount(-1);
            jListStr.addMouseListener(jListMouseAdapter);
        }
    }

    private static boolean isMp3(File f) {
        return f.getName().toLowerCase().endsWith(MP3_SUFFIX);
    }

    private static List<File> readFiles(Path dir) {
        File[] allFiles;
        boolean roots = dir == null;
        if (roots) {
            allFiles = File.listRoots();
        } else {
            allFiles = dir.toFile().listFiles();
        }
        if (allFiles == null || allFiles.length == 0) {
            return new ArrayList<>();
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
        return fileList;
    }

    private enum TabbedPaneAction {
        OPEN_FILE_CHOOSER("openFileChooser"),
        ADD_TAB("addTab"),
        DELETE_TAB("deleteTab");

        private String name;

        TabbedPaneAction(String name) {
            this.name = name;
        }

        private static TabbedPaneAction getByName(String name) {
            for (TabbedPaneAction value : TabbedPaneAction.values()) {
                if (value.name.equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    private void initActionListener() {
        playListActionListener = e -> {
            TabbedPaneAction action = TabbedPaneAction.getByName(e.getActionCommand());
            if (action == null) {
                System.err.println("Unknown action " + e.getActionCommand());
                return;
            }
            switch (action) {
                case OPEN_FILE_CHOOSER:
                    int currentIndex = playListPane.getSelectedIndex();
                    Path currentDir = tabContents.get(currentIndex).currentDir;
                    Path newPath = initDirectoryChooser(currentDir);
                    if (newPath != null && ! newPath.equals(currentDir)) {
                        refreshCurrentTab(newPath);
                    }
                    break;
                case ADD_TAB:
                    initOneTab();
                    break;
                case DELETE_TAB:
                    deleteCurrentTab();
                    break;
            }
        };
        jListMouseAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() < 2) {
                    return;
                }
                super.mouseClicked(e);
                Tab currentTab = tabContents.get(playListPane.getSelectedIndex());
                int currentSongIndex = currentTab.jListStr.getSelectedIndex();

                File f = currentTab.jListFile.get(currentSongIndex);

                if (f.isDirectory()) {
                    refreshCurrentTab(f.toPath());
                } else if (isMp3(f)) {
                    List<File> musicList = currentTab.jListFile.subList(currentSongIndex, currentTab.jListFile.size())
                            .stream().filter(MusicListPane::isMp3).collect(Collectors.toList());
                    player.playList(musicList);
                }
            }
        };
    }

    private Path initDirectoryChooser(Path currentDir) {
        JFileChooser fileChooser = new JFileChooser(currentDir.toFile());
        fileChooser.setDialogTitle("Choose Directory");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().toPath();
        } else {
            return null;
        }
    }

    private void initPane() {
        JButton selectDir;
        JButton addTab;
        JButton deleteTab;

        GridBagLayout layout = new GridBagLayout();
        setLayout(layout);
        GridBagConstraints gbc = new GridBagConstraints();

        selectDir = new JButton("...");
        selectDir.setFocusable(true);
        selectDir.setFont(player.getFont());
        selectDir.setActionCommand(TabbedPaneAction.OPEN_FILE_CHOOSER.name);
        selectDir.addActionListener(playListActionListener);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        add(selectDir, gbc);

        addTab = new JButton("Add");
        addTab.setFocusable(true);
        addTab.setFont(player.getFont());
        addTab.setActionCommand(TabbedPaneAction.ADD_TAB.name);
        addTab.addActionListener(playListActionListener);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(addTab, gbc);

        deleteTab = new JButton("Delete");
        deleteTab.setFocusable(true);
        deleteTab.setFont(player.getFont());
        deleteTab.setActionCommand(TabbedPaneAction.DELETE_TAB.name);
        deleteTab.addActionListener(playListActionListener);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(deleteTab, gbc);

        playListPane = new JTabbedPane(JTabbedPane.BOTTOM);
        playListPane.setPreferredSize(ComponentDimension.PLAY_LIST_PANE.getDimension());
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 5;
        add(playListPane, gbc);
    }

    private void initTabs(Preferences prefs) {
        String paths = prefs.get(PATH_KEY, "");
        for (String path : paths.split(":")) {
            Path dir = Paths.get(path);
            if (Files.exists(dir) && dir.toFile().isDirectory()) {
                initOneTab();
                refreshCurrentTab(dir);
            }
        }
        if (playListPane.getTabCount() == 0) {
            initOneTab();
        }
    }

    private void updatePref(Preferences prefs) {
        List<String> paths = tabContents.stream().map(tab -> tab.currentDir.toString()).collect(Collectors.toList());
        prefs.put(PATH_KEY, String.join(":", paths));
    }

    MusicListPane(Player player) {
        super();
        this.player = player;

        initActionListener();
        initPane();
        initTabs(player.getPrefs());
    }

    private void refreshCurrentTab(Path currentDir) {
        int tabIndex = playListPane.getSelectedIndex();
        Tab tabToBeRefreshed = tabContents.get(tabIndex);
        tabToBeRefreshed.currentDir = currentDir;
        tabToBeRefreshed.jListFile = readFiles(currentDir);
        tabToBeRefreshed.updatejListStr();
        addOneTabToPane(tabContents, tabIndex, true);
        updatePref(player.getPrefs());
    }

    private void initOneTab() {
        Path currentPath = FileSystemView.getFileSystemView().getHomeDirectory().toPath();
        List<File> allFiles = readFiles(currentPath);
        tabContents.add(new Tab(allFiles, currentPath));
        addOneTabToPane(tabContents, playListPane.getTabCount(), false);
        updatePref(player.getPrefs());
    }

    private void addOneTabToPane(List<Tab> _tabContents, int tabIndex, boolean deleteOld) {

        if (playListPane.getTabCount() < tabIndex) {
            throw new IllegalArgumentException("Tab index must not be greater than the total tab count.");
        }

        if (_tabContents.size() < tabIndex) {
            throw new RuntimeException("You must insert Tab before adding it.");
        }

        if (deleteOld) {
            playListPane.removeTabAt(tabIndex);
        }

        JScrollPane pane = new JScrollPane(_tabContents.get(tabIndex).jListStr,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        String playListTitle = String.format("Play List #%d", tabIndex + 1);
        playListPane.insertTab(playListTitle, null, pane, playListTitle, tabIndex);
        playListPane.setSelectedIndex(tabIndex);
    }

    private void deleteCurrentTab() {
        int tabIndex = playListPane.getSelectedIndex();
        if (tabIndex < 0) {
            return;
        }

        playListPane.removeTabAt(tabIndex);
        tabContents.remove(tabIndex);
        updatePref(player.getPrefs());
    }

    private ListModel<String> generateListModelFromFileList(List<File> files) {
        return new AbstractListModel<String>() {

            @Override
            public int getSize() {
                return files.size();
            }

            @Override
            public String getElementAt(int index) {
                File file = files.get(index);
                if (file.isDirectory()) {
                    return file.getName();
                } else {
                    return getTitle(file.getName());
                }
            }
        };
    }
}
