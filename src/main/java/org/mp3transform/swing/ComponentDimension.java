package org.mp3transform.swing;

import java.awt.Dimension;

public enum ComponentDimension {
    FRAME_INIT(350, 400),
    FRAME_MIN(350, 350),
    BUTTON_1(100, 40),
    PLAY_LIST_PANE(250, 200),
    EMPTY_PANEL_1(20, 70);

    private Dimension dimension;

    ComponentDimension(int width, int height) {
        dimension = new Dimension(width, height);
    }

    public Dimension getDimension() {
        return dimension;
    }
}
