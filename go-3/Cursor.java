
import javax.media.j3d.*;
//import com.sun.j3d.utils.universe.*;
import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;
//import com.sun.j3d.utils.applet.MainFrame;
//import java.awt.*;
import javax.vecmath.*;


public class Cursor extends Sphere {
	
	////////	CONSTANTS	////////
	private static float RADIUS = 0.5f;
	private static int NUM_SEGMENTS = 16;
	private static float CURSOR_SHININESS = 20.f;
	private static float CURSOR_LINEWIDTH = 2.f;
	private static float CURSOR_TRANSPARENCY = 0.5f;
	private static int DEFAULT_COLOUR = Colour.BLUE;
	
	////////	C'TORS		////////
	/** default constructor: creates a cursor of default color */
	public Cursor () {
		super (RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_OVERRIDE_WRITE);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_WRITE);
		setColour (DEFAULT_COLOUR);
	}
	
	public Cursor (int c) {
		super (RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_OVERRIDE_WRITE);
		this.setCapability (Shape3D.ALLOW_APPEARANCE_WRITE);
		setColour (c);
	}
	
	
	////////	PUBLIC METHODS	////////
	public int getColour () {
		return colour;
	}
	
	public void setColour (int c) {
		assert GameBase.precondition ((c >= 0 && c < materials.length), 
		"There are only "+materials.length+" colors defined. tried to choose color "+c);
		
		colour = c;
		material = materials[c];
		createAppearance ();

		this.setAppearance (cAppearance);
	}

	
	////////	PROTECTED METHODS	////////
	protected void createAppearance (/* ... */) {
				
		cAppearance.setMaterial (material);
		
		Color3f tmpColor = new Color3f ();
		material.getAmbientColor (tmpColor);
		ColoringAttributes ca = new ColoringAttributes ();
		ca.setColor (tmpColor);
		cAppearance.setColoringAttributes (ca);

		setLineStyle ();
		
		setTransparency (CURSOR_TRANSPARENCY);
	}
	
	private void setLineStyle () {
		//	set draw mode to wireframe
		PolygonAttributes pa = new PolygonAttributes ();
		pa.setPolygonMode (PolygonAttributes.POLYGON_LINE);
		cAppearance.setPolygonAttributes (pa);
		
		LineAttributes la = new LineAttributes ();
		la.setLineAntialiasingEnable(true);
		la.setLineWidth (CURSOR_LINEWIDTH);
		cAppearance.setLineAttributes (la);		
	}

	private void setTransparency (float tVal) {
		cTransparency = new TransparencyAttributes (TransparencyAttributes.NICEST, tVal);
		cTransparency.setCapability(TransparencyAttributes.ALLOW_VALUE_READ);
		cTransparency.setCapability(TransparencyAttributes.ALLOW_VALUE_WRITE);
		cAppearance.setTransparencyAttributes (cTransparency);		
	}


	////////	MEMBER VARIABLES	////////
	private int colour = 0;
	private Material material = materials[colour];
	
	protected Appearance cAppearance = new Appearance ();
	protected TransparencyAttributes cTransparency;

	
	////////	MORE CONSTANTS		////////
	private static Material[] materials = {
		new Material (),				//	dummy entry because colours start at 1
		new Material (					//  BLACK	//
				new Color3f (0.05f, 0.05f, 0.05f),//  ambient
				new Color3f (0,0,0),		//  emissive
				new Color3f (0.1f, 0.1f, 0.1f),	//  diffuse
				new Color3f (0.8f, 0.8f, 0.8f),	//  specular
				CURSOR_SHININESS),				//  shininess
		new Material (					//  WHITE	//
				new Color3f (0.3f, 0.3f, 0.3f),	//  ambient
				new Color3f (0,0,0),		//  emissive
				new Color3f (0.8f, 0.8f, 0.8f),	//  diffuse
				new Color3f (1.0f, 1.0f, 1.0f),	//  specular
				CURSOR_SHININESS),				//  shininess
		new Material (					//  RED		//
				new Color3f (0.3f, 0.1f, 0.1f),	//  ambient
				new Color3f (0,0,0),		//  emissive
				new Color3f (0.8f, 0.1f, 0.1f),	//  diffuse
				new Color3f (1.0f, 0.8f, 0.8f),	//  specular
				CURSOR_SHININESS),				//  shininess
		new Material (					//  GREEN	//
				new Color3f (0.1f, 0.3f, 0.1f),	//  ambient
				new Color3f (0,0,0),		//  emissive
				new Color3f (0.1f, 0.8f, 0.1f),	//  diffuse
				new Color3f (0.8f, 1.0f, 0.8f),	//  specular
				CURSOR_SHININESS),				//  shininess
		new Material (					//  BLUE	//
				new Color3f (0.1f, 0.1f, 0.3f),	//  ambient
				new Color3f (0,0,0),		//  emissive
				new Color3f (0.1f, 0.1f, 0.8f),	//  diffuse
				new Color3f (0.8f, 0.8f, 1.0f),	//  specular
				CURSOR_SHININESS)				//  shininess
	};
}
