
import javax.media.j3d.*;
import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;
import javax.vecmath.*;


public class Cursor extends Sphere {
	
	////////	CONSTANTS	////////
	private static float RADIUS = 0.5f;
	private static int NUM_SEGMENTS = 16;
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
	public int getColour () { return colour; }
	
	public void setColour (int c) {
		assert GameBase.precondition ((c >= 0 && c < Materials.materials.length), 
		"There are only "+Materials.materials.length+" colors defined. tried to choose color "+c);
		
		colour = c;
		material = Materials.materials[c];
		createAppearance ();

		this.setAppearance (cAppearance);
	}

	
	////////	PRIVATE METHODS	////////
	private void createAppearance (/* ... */) {
				
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
		
		//	turn antialiasing on
		LineAttributes la = new LineAttributes ();
		la.setLineAntialiasingEnable(true);
		//	set line width
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
	private Material material = Materials.materials[colour];
	
	protected Appearance cAppearance = new Appearance ();
	protected TransparencyAttributes cTransparency;
}
