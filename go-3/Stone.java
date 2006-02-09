
//

import javax.media.j3d.*;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;
import javax.vecmath.*;

//


public class Stone extends Sphere {
	////////	CONSTANTS	////////
	private static float RADIUS = 0.4f;
	private static int NUM_SEGMENTS = 32;
	private static float STONE_TRANSPARENCY = 0.1f;

	////////	C'TORS		////////
    public Stone () {
    	super (RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_OVERRIDE_WRITE);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_WRITE);
		setColour(Colour.BLACK);
//		material = Materials.materials[Colour.BLACK];
 //   	sAppearance = createAppearance ();
  //  	this.setAppearance (sAppearance);
    }

    public Stone (int c) {
    	super (RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS);
    	colour = c;
		this.setCapability (Shape3D.ALLOW_APPEARANCE_OVERRIDE_WRITE);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_WRITE);
		setColour(c);
/*		material = Materials.materials[colour];
    	sAppearance = createAppearance ();
    	this.setAppearance (sAppearance);
*/    }

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

    	    PolygonAttributes pa = new PolygonAttributes ();
    	    pa.setCullFace (PolygonAttributes.CULL_NONE);
    	    //	    pa.setPolygonMode (PolygonAttributes.POLYGON_LINE);
    	    app.setPolygonAttributes (pa);

    	    TransparencyAttributes ta = new TransparencyAttributes (TransparencyAttributes.NICEST, 0.5f);
    	    app.setTransparencyAttributes (ta);    	
    	
    	return app;
    }

    private int colour;
    private Material material = Materials.materials[colour];

    private Appearance sAppearance;

}

