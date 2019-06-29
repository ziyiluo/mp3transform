package org.mp3transform.swing;

public enum PlayStatus {
    STOP("Play"),
    PLAYING("Pause"),
    PAUSE("Resume");

    private String nextStep;

    public String getNextStep() {
        return nextStep;
    }

    PlayStatus(String nextStep) {
        this.nextStep = nextStep;
    }
}
