package com.mzp.capturesdk;

import java.io.Serializable;

public class CaptureImageConfig implements Serializable {
    public final int width;
    public final int height;

    public CaptureImageConfig(int width, int height) {
        this.width = width;
        this.height = height;
    }
}
