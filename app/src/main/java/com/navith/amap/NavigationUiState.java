package com.navith.amap;

/**
 * UI state machine only. Not a navigation engine and never a substitute for
 * provider map-matching or guidance callbacks.
 */
public final class NavigationUiState {
    public enum Phase { HOME, PREVIEW, STARTING, GUIDING_TEST, REROUTING, ARRIVED, ERROR }
    private Phase phase=Phase.HOME;
    public Phase phase(){return phase;}
    public void preview(){phase=Phase.PREVIEW;}
    public void startPending(){
        if(phase!=Phase.PREVIEW)throw new IllegalStateException("Route preview required");
        phase=Phase.STARTING;
    }
    public void started(){
        if(phase!=Phase.STARTING)throw new IllegalStateException("Test start not requested");
        phase=Phase.GUIDING_TEST;
    }
    public void rerouting(){
        if(phase==Phase.GUIDING_TEST)phase=Phase.REROUTING;
    }
    public void rerouted(){
        if(phase==Phase.REROUTING)phase=Phase.GUIDING_TEST;
    }
    public void arrived(){if(isGuiding())phase=Phase.ARRIVED;}
    public void failed(){phase=Phase.ERROR;}
    public void stop(){phase=Phase.PREVIEW;}
    public void clear(){phase=Phase.HOME;}
    public boolean isGuiding(){return phase==Phase.GUIDING_TEST||phase==Phase.REROUTING;}
}
