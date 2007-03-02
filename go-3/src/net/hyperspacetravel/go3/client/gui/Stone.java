package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.*;
import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;
import javax.vecmath.*;

import net.hyperspacetravel.go3.Colour;
import net.hyperspacetravel.go3.GameBase;


public class Stone extends Sphere {
	////////	CONSTANTS	////////
	private static float RADIUS = 0.4f;
	private static int NUM_SEGMENTS = 32;
	private static float STONE_TRANSPARENCY = 0.01f;

	////////	C'TORS		////////
    public Stone () {
    	super (RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_OVERRIDE_WRITE);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_WRITE);
		setColour(Colour.BLACK);
    }

    public Stone (int c) {
    	super (RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS);
    	colour = c;
		this.setCapability (Shape3D.ALLOW_APPEARANCE_OVERRIDE_WRITE);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_WRITE);
		setColour(c);
    }

	////////	PUBLIC METHODS	////////
    public int getColour () { return colour; }

    public void setColour (int c) {
		assert GameBase.precondition ((c >= 0 && c < Materials.materials.length), 
				"There are only "+Materials.materials.length+" colors defined. tried to choose color "+c);
				
		colour = c;
		material = Materials.materials[c];
    	sAppearance = createAppearance ();
    	
    	this.setAppearance (sAppearance);
    }

    private Appearance createAppearance (/* ... */) {
    	Appearance app = new Appearance();
    	app.setMaterial (material);

    	if (false) {
    	PolygonAttributes pa = new PolygonAttributes ();
    	pa.setCullFace (PolygonAttributes.CULL_NONE);
    	app.setPolygonAttributes (pa);
    	}
//    	setTransparency (STONE_TRANSPARENCY, app);
    	
    	return app;
    }

    private void setTransparency (float tVal, Appearance app) {
		cTransparency = new TransparencyAttributes (TransparencyAttributes.NICEST, tVal);
		cTransparency.setCapability(TransparencyAttributes.ALLOW_VALUE_READ);
		cTransparency.setCapability(TransparencyAttributes.ALLOW_VALUE_WRITE);
		app.setTransparencyAttributes (cTransparency);		
	}

    private int colour;
    private Material material = Materials.materials[colour];

    private Appearance sAppearance;
	private TransparencyAttributes cTransparency;
}