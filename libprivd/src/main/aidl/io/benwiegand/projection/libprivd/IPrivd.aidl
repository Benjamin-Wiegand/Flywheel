package io.benwiegand.projection.libprivd;

interface IPrivd {
    void ping();

    boolean injectInputEvent(in InputEvent event);

    boolean injectInputEventWithDisplayId(in InputEvent event, int displayId);

    int launchActivity(in ComponentName component, int displayId);

    int createVirtualDisplay(in String name, int width, int height, int densityDpi, in Surface surface, int flags);

    void releaseVirtualDisplay(int displayId);

    void virtualDisplayResize(int displayId, int width, int height, int densityDpi);

    void virtualDisplaySetSurface(int displayId, in Surface surface);
}