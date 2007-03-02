class DisplayAttributes {
    private boolean enableAntiAlias = false;
    private boolean displayGrid = true;
    private boolean displayHandicaps = true;
    private boolean enableTransparency = false;
    private float transparencyValue = 0.5f;
    private boolean moveCursor = false;
    
    public void setAntiAlias (boolean aa) {
	enableAntiAlias = aa;
    }
    public void setDisplayGrid (boolean dg) {
	displayGrid = dg;
    }
    public void setDisplayHandicaps (boolean dh) {
	displayHandicaps = dh;
    }
    public void setTransparency (boolean tr) {
	enableTransparency = tr;
    }
    public void setTransparencyValue (float tv) {
	transparencyValue = tv;
    }
}
