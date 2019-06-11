/*
 * This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */
package org.mp3transform;

import org.mp3transform.mp3.Bitstream;
import org.mp3transform.mp3.Header;
import org.mp3transform.mp3.Layer3Decoder;
import org.mp3transform.mp3.SynthesisFilter;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

public class Decoder {
    protected static final int BUFFER_SIZE = 2 * 1152;
    private static final int MAX_CHANNELS = 2;
    private static final float MIN_MASTER_GAIN = -80;

    // Fade Controls
    private static final int FADE_FRAMES = 80;
    private static final float FADE_BASE = 0.9f;
    private static final boolean FADE_ENABLED = true;

    // Debugging
    private static final boolean BENCHMARK = false;

    protected final int[] bufferPointer = new int[MAX_CHANNELS];
    protected int channels;
    private SynthesisFilter filter1;
    private SynthesisFilter filter2;
    private Layer3Decoder l3decoder;
    private boolean initialized;

    private SourceDataLine line;
    private final byte[] buffer = new byte[BUFFER_SIZE * 2];
    private boolean stop;
    private volatile boolean pause;

    public void decodeFrame(Header header, Bitstream stream) throws IOException {
        if (!initialized) {
            double scaleFactor = 32700.0f;
            int mode = header.mode();
            int channels = mode == Header.MODE_SINGLE_CHANNEL ? 1 : 2;
            filter1 = new SynthesisFilter(0, scaleFactor);
            if (channels == 2) {
                filter2 = new SynthesisFilter(1, scaleFactor);
            }
            initialized = true;
        }

        if (l3decoder == null) {
            l3decoder = new Layer3Decoder(stream, header, filter1, filter2,
                    this);
        }
        l3decoder.decodeFrame();
        writeBuffer();
    }

    protected void initOutputBuffer(SourceDataLine line, int numberOfChannels) {
        this.line = line;
        channels = numberOfChannels;
        for (int i = 0; i < channels; i++) {
            bufferPointer[i] = i + i;
        }
    }

    public void appendSamples(int channel, double[] f) {
        int p = bufferPointer[channel];
        for (int i = 0; i < 32; i++) {
            double sample = f[i];
            int s = (int) ((sample > 32767.0f) ? 32767 : ((sample < -32768.0f) ? -32768 : sample));
            buffer[p] = (byte) (s >> 8);
            buffer[p + 1] = (byte) (s & 0xff);
            p += 4;
        }
        bufferPointer[channel] = p;
    }

    protected void writeBuffer() throws IOException {
        if (line != null) {
            line.write(buffer, 0, bufferPointer[0]);
        }
        for (int i = 0; i < channels; i++) {
            bufferPointer[i] = i + i;
        }
    }

    public void play(String name, InputStream in) throws IOException {
        stop = false;
        int frameCount = Integer.MAX_VALUE;
        int fade_index = 0;

        // int testing;
        // frameCount = 100;

        Decoder decoder = new Decoder();
        Bitstream stream = new Bitstream(in);
        SourceDataLine line = null;

        int error = 0;
        for (int frame = 0; !stop && frame < frameCount; frame++) {
            if (pause) {
                if (FADE_ENABLED && line != null && fade_index > 0) {
                    fade_index--;
                    FloatControl volume = (FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);
                    volume.setValue(MIN_MASTER_GAIN * (float) Math.pow(FADE_BASE, fade_index));
                } else {
                    line.stop();
                    while (pause && !stop) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    line.flush();
                    line.start();
                }
            } else if (FADE_ENABLED && line != null && fade_index < FADE_FRAMES) {
                fade_index++;
                FloatControl volume = (FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);
                volume.setValue(MIN_MASTER_GAIN * (float) Math.pow(FADE_BASE, fade_index));
            }
            try {
                Header header = stream.readFrame();
                if (header == null) {
                    break;
                }
                if (decoder.channels == 0) {
                    int channels = (header.mode() == Header.MODE_SINGLE_CHANNEL) ? 1 : 2;
                    float sampleRate = header.frequency();
                    int sampleSize = 16;
                    AudioFormat format = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED, sampleRate,
                            sampleSize, channels, channels * (sampleSize / 8),
                            sampleRate, true);
                    // big endian
                    SourceDataLine.Info info = new DataLine.Info(
                            SourceDataLine.class, format);
                    line = (SourceDataLine) AudioSystem.getLine(info);
                    if (BENCHMARK) {
                        decoder.initOutputBuffer(null, channels);
                    } else {
                        decoder.initOutputBuffer(line, channels);
                    }
                    // TODO sometimes the line can not be opened (maybe not enough system resources?): display error message
                    // System.out.println(line.getFormat().toString());
                    line.open(format);
                    line.start();

                }
                while (line.available() < 100) {
                    Thread.yield();
                    Thread.sleep(200);
                }
                decoder.decodeFrame(header, stream);
            } catch (Exception e) {
                if (error++ > 1000) {
                    break;
                }
                // TODO should not write directly
                System.out.println("Error at: " + name + " Frame: " + frame + " Error: " + e.toString());
                // e.printStackTrace();
            } finally {
                stream.closeFrame();
            }
        }
        if (error > 0) {
            System.out.println("errors: " + error);
        }
        in.close();
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    public void stop() {
        this.stop = true;
    }
    
    public boolean pause() {
        AtomicBoolean atomicPause = new AtomicBoolean(!pause);
        pause = atomicPause.get();
        return pause;
    }

}
