package net.hyperspacetravel.go3.client.gui;
class DisplayAttributes {
    private boolean enableAntiAlias = false;
    private boolean displayGrid = true;
    private boolean displayHandicaps = true;
    private boolean enableTransparency = false;
    private float transparencyValue = 0.5f;
    
    public void setAntiAlias (boolean aa) { enableAntiAlias = aa; }
	boolean isAntiAliased() { return enableAntiAlias; }
	
    public void setDisplayGrid (boolean dg) { displayGrid = dg; }
	boolean getDisplayGrid() { return displayGrid; }
	
    public void setDisplayHandicaps (boolean dh) { displayHandicaps = dh; }
	boolean getDisplayHandicaps() { return displayHandicaps; }
	
    public void setTransparency (boolean tr) { enableTransparency = tr; }
	boolean isTransparent() { return enableTransparency; }
	
    public void setTransparencyValue (float tv) { transparencyValue = tv; }
	float getTransparencyValue() { return transparencyValue; }
}
