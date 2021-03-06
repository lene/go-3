package net.hyperspacetravel.go3.client.gui;


import javax.media.j3d.*;
import javax.vecmath.*;

import net.hyperspacetravel.go3.GameBase;

/** a base class for displaying the grid in 3 dimensions
    TODO derive Point class (for pick points) from this
    @author helge */
abstract public class GridObject extends Shape3D {
	
	public GridObject (int s) {
		assert GameBase.precondition ((s >= GameBase.MIN_GRID_SIZE && s <= GameBase.MAX_GRID_SIZE), 
				"Board size must lie between "+GameBase.MIN_GRID_SIZE+" and "+GameBase.MAX_GRID_SIZE);
		
		setSize (s);
		gGeometry = createGeometry ();
		createAppearance ();
		this.setGeometry (gGeometry);
		this.setAppearance (gAppearance);
	}
	
	public static void setSize (int s) {
		assert GameBase.precondition ((s >= GameBase.MIN_GRID_SIZE && s <= GameBase.MAX_GRID_SIZE), 
				"Board size must lie between "+GameBase.MIN_GRID_SIZE+" and "+GameBase.MAX_GRID_SIZE);

		size = s;
	}
	
	public static int getSize () { return size;	}
	
	void transparencyUp () {
		float transparency = gTransparency.getTransparency();
		transparency *= TRANSPARENCY_STEP; 
		if (transparency > MAX_TRANSPARENCY) transparency = MAX_TRANSPARENCY;
		gTransparency.setTransparency (transparency);
	}

	void transparencyDown () {
		float transparency = gTransparency.getTransparency();
		transparency /= TRANSPARENCY_STEP; 
		if (transparency < MIN_TRANSPARENCY) transparency = MIN_TRANSPARENCY;
		gTransparency.setTransparency (transparency);
	}

	
	protected void createAppearance (/* ... */) {
		gAppearance = new Appearance ();
		
		Material m = new Material ();
		gAppearance.setMaterial (m);
		
		setColor (DEFAULT_GRIDCOLOR);
		
		setAntialiasing (true);
		
		setCulling (PolygonAttributes.CULL_NONE);
		
		setTransparency (DEFAULT_TRANSPARENCY);
	}
	
	protected void setColor (Color3f col) {
		ColoringAttributes ca = new ColoringAttributes ();
		gColor = col;
		ca.setColor (gColor);
		gAppearance.setColoringAttributes (ca);		
	}
	
	protected void setAntialiasing (boolean enable) {
		LineAttributes la = new LineAttributes ();
		la.setLineAntialiasingEnable(enable);
		gAppearance.setLineAttributes (la);		
	}
	
	protected void setCulling (int mode) {
		PolygonAttributes pa = new PolygonAttributes ();
		pa.setCullFace (mode);
		//	    pa.setPolygonMode (PolygonAttributes.POLYGON_LINE);
		gAppearance.setPolygonAttributes (pa);		
	}

	protected void setTransparency (float tVal) {
		gTransparency = new TransparencyAttributes (TransparencyAttributes.NICEST, tVal);
		gTransparency.setCapability(TransparencyAttributes.ALLOW_VALUE_READ);
		gTransparency.setCapability(TransparencyAttributes.ALLOW_VALUE_WRITE);
		gAppearance.setTransparencyAttributes (gTransparency);		
	}
		
	
	abstract protected Geometry createGeometry ();

	
	protected static int size;
	protected Color3f gColor;
	protected Appearance gAppearance;
	protected TransparencyAttributes gTransparency;
	protected Geometry gGeometry;

	
	protected static Color3f DEFAULT_GRIDCOLOR = new Color3f (0.8f,0.8f,0.8f);
	protected static float DEFAULT_TRANSPARENCY = 0.5f;
	protected static float MIN_TRANSPARENCY = 0.1f;
	protected static float MAX_TRANSPARENCY = 0.98f;
	protected static float TRANSPARENCY_STEP = 1.1f;
}

