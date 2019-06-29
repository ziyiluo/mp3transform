package org.mp3transform.swing;

import org.mp3transform.Decoder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class PlayerThread implements Runnable {

    private Decoder decoder = new Decoder();
    private File currentFile;
    private List<File> fileList;
    private Thread thread;
    private boolean stop;
    private Player player;

    public void stopPlaying() {
        stop = true;
        decoder.stop();
        try {
            thread.join();
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public boolean pause() {
        return decoder.pause();
    }
    
    public static PlayerThread startPlaying(Player player, List<File> file) {
        PlayerThread t = new PlayerThread();
        t.player = player;
        t.currentFile = null;
        t.fileList = file;
        Thread thread = new Thread(t);
        thread.setName(t.getClass().getName());
        thread.setPriority(Thread.MAX_PRIORITY);
        t.thread = thread;
        thread.start();
        return t;
    }
    
    public void run() {
        try {
            while (!stop) {
                if (currentFile == null) {
                    if (fileList != null && fileList.size() > 0) {
                        currentFile = fileList.remove(0);
                    }
                }
                if (currentFile == null) {
                    break;
                }
                play(currentFile);
            }
            player.setPlayStatus(null, PlayStatus.STOP);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void play(File file) throws IOException {
        player.setPlayStatus(file, PlayStatus.PLAYING);
        stop = false;
        if (!file.getName().endsWith(".mp3")) {
            return;
        }
        System.out.println("playing: " + file);
        FileInputStream in = new FileInputStream(file);
        BufferedInputStream bin = new BufferedInputStream(in, 128 * 1024);
        decoder.play(file.getName(), bin);
        currentFile = null;
    }

    public void playNext() {
        decoder.stop();
        currentFile = null;
    }
    
}
