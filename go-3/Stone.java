
//

import javax.media.j3d.*;
import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;
import javax.vecmath.*;

//


public class Stone extends Sphere {
    private static float radius = 0.4f;

    public Stone () {
	super (radius, Primitive.GENERATE_NORMALS, 16);
	material = materials[Colour.BLACK];
	sAppearance = createAppearance ();
	this.setAppearance (sAppearance);
    }

    public Stone (int c) {
	super (radius, Primitive.GENERATE_NORMALS, 16);
	color = c;
	material = materials[color];
	//	material.setCapability (Material.ALLOW_COMPONENT_READ);
	sAppearance = createAppearance ();
	this.setAppearance (sAppearance);
    }

    public int getColour () {
	return color;
    }

    public static void setSegments (int s) {
	segments = s;
	//  redraw spheres, repaint board
    }

    public static int getSegments () {
	return segments;
    }

    private int color;
    private Material material = materials[color];
    private static int segments;

    private Appearance sAppearance;

    private Appearance createAppearance (/* ... */) {
	Appearance app = new Appearance ();

	app.setMaterial (material);
	/*	
	ColoringAttributes ca = new ColoringAttributes ();
	Color3f tmpColor = new Color3f ();
	material.getAmbientColor (tmpColor);
	ca.setColor (tmpColor);
	app.setColoringAttributes (ca);
	*/
	if (false) {
	    PolygonAttributes pa = new PolygonAttributes ();
	    pa.setCullFace (PolygonAttributes.CULL_NONE);
	    //	    pa.setPolygonMode (PolygonAttributes.POLYGON_LINE);
	    app.setPolygonAttributes (pa);
	}

	if (false) {
	    TransparencyAttributes ta = new TransparencyAttributes (TransparencyAttributes.NICEST, 0.5f);
	    app.setTransparencyAttributes (ta);
	}
	
	
	return app;
    }

    private static Material[] materials = {
	new Material (),				//  EMPTY	//
	new Material (					//  BLACK	//
		      new Color3f (0.05f, 0.05f, 0.05f),//  ambient
		      new Color3f (0,0,0),		//  emissive
		      new Color3f (0.1f, 0.1f, 0.1f),	//  diffuse
		      new Color3f (0.8f, 0.8f, 0.8f),	//  specular
		      20.f),				//  shininess
	new Material (					//  WHITE	//
		      new Color3f (0.3f, 0.3f, 0.3f),	//  ambient
		      new Color3f (0,0,0),		//  emissive
		      new Color3f (0.8f, 0.8f, 0.8f),	//  diffuse
		      new Color3f (1.0f, 1.0f, 1.0f),	//  specular
		      20.f),				//  shininess
	new Material (					//  RED		//
		      new Color3f (0.3f, 0.1f, 0.1f),	//  ambient
		      new Color3f (0,0,0),		//  emissive
		      new Color3f (0.8f, 0.1f, 0.1f),	//  diffuse
		      new Color3f (1.0f, 0.8f, 0.8f),	//  specular
		      20.f),				//  shininess
	new Material (					//  GREEN	//
		      new Color3f (0.1f, 0.3f, 0.1f),	//  ambient
		      new Color3f (0,0,0),		//  emissive
		      new Color3f (0.1f, 0.8f, 0.1f),	//  diffuse
		      new Color3f (0.8f, 1.0f, 0.8f),	//  specular
		      20.f),				//  shininess
	new Material (					//  BLUE	//
		      new Color3f (0.1f, 0.1f, 0.3f),	//  ambient
		      new Color3f (0,0,0),		//  emissive
		      new Color3f (0.1f, 0.1f, 0.8f),	//  diffuse
		      new Color3f (0.8f, 0.8f, 1.0f),	//  specular
		      20.f)				//  shininess
    };
}

