package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.Appearance;
import javax.media.j3d.Shape3D;

import net.hyperspacetravel.go3.GameBase;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;


public class SphereCursor extends Cursor {
	
	////////	CONSTANTS	////////
	private static float RADIUS = 0.5f;
	private static int NUM_SEGMENTS = 16;
	
	////////	C'TORS		////////
	/** default constructor: creates a cursor of default color */
	public SphereCursor () {
		sphere = new Sphere(RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS);
		sphere.setCapability (Shape3D.ALLOW_APPEARANCE_OVERRIDE_WRITE);
		sphere.setCapability (Shape3D.ALLOW_APPEARANCE_WRITE);
		setColour (DEFAULT_COLOUR);
		addChild(sphere);
	}
	
	public SphereCursor (int c) {
		sphere = new Sphere(RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS);
		sphere.setCapability (Shape3D.ALLOW_APPEARANCE_OVERRIDE_WRITE);
		sphere.setCapability (Shape3D.ALLOW_APPEARANCE_WRITE);
		setColour (c);
		addChild(sphere);
	}
	
	public void setColour (int c) {
		assert GameBase.precondition (
				(c >= 0 && c < Materials.materials.length), 
				"There are only "+Materials.materials.length+" colors defined. tried to choose color "+c);
		
		System.out.println("SphereCursor.setColour("+c+")");
		colour = c;
		material = Materials.materials[c];
		createAppearance ();

		setAppearance (cAppearance);
	}


	/* (non-Javadoc)
	 * @see com.sun.j3d.utils.geometry.Primitive#getShape(int)
	 */
	@Override
	public Shape3D getShape(int arg0) {
		return sphere.getShape(arg0);
	}

	/* (non-Javadoc)
	 * @see com.sun.j3d.utils.geometry.Primitive#getAppearance(int)
	 */
	@Override
	public Appearance getAppearance(int arg0) {
		return sphere.getAppearance(arg0);
	}

	/* (non-Javadoc)
	 * @see com.sun.j3d.utils.geometry.Primitive#setAppearance(javax.media.j3d.Appearance)
	 */
	@Override
	public void setAppearance(Appearance arg0) {
		sphere.setAppearance(arg0);
		
	}

	////////	MEMBER VARIABLES	////////
	private Sphere sphere;
}
