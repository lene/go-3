/**
 * 
 */
package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.Appearance;
import javax.media.j3d.ColoringAttributes;
import javax.media.j3d.LineAttributes;
import javax.media.j3d.Material;
import javax.media.j3d.PolygonAttributes;
import javax.media.j3d.TransparencyAttributes;
import javax.media.j3d.Shape3D;
import javax.vecmath.Color3f;

import net.hyperspacetravel.go3.Colour;
import net.hyperspacetravel.go3.GameBase;

import com.sun.j3d.utils.geometry.Primitive;

/**
 * @author helge
 *
 */
abstract class Cursor extends Primitive {
	protected  float CURSOR_LINEWIDTH = 2.f;
	protected  float CURSOR_TRANSPARENCY = 0.5f;
	protected  int DEFAULT_COLOUR = Colour.BLUE;

	////////	PUBLIC METHODS	////////
	public int getColour () { return colour; }
	
	public void setColour (int c) {
		assert GameBase.precondition (
				(c >= 0 && c < Materials.materials.length), 
				"There are only "+Materials.materials.length+" colors defined. tried to choose color "+c);
		
		colour = c;
		this.material = Materials.materials[c];
		if (!this.material.isCompiled() && !this.material.isLive())
			this.material.setCapability(Material.ALLOW_COMPONENT_READ);
		
		this.createAppearance ();

		this.setAppearance (cAppearance);
	}
	
	////////	PRIVATE METHODS	////////
	protected void initChildObject(Primitive _object, int c) {
		this.object = _object;
		this.object.setCapability (Shape3D.ALLOW_APPEARANCE_WRITE);
		this.object.setCapability (Shape3D.ALLOW_APPEARANCE_OVERRIDE_WRITE);
		this.cAppearance.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
		this.cAppearance.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
		this.cAppearance.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_WRITE);
		this.cAppearance.setCapability(Appearance.ALLOW_POLYGON_ATTRIBUTES_WRITE);
		this.cAppearance.setCapability(Appearance.ALLOW_LINE_ATTRIBUTES_WRITE);
		this.cAppearance.setCapability(Appearance.ALLOW_TRANSPARENCY_ATTRIBUTES_WRITE);
		this.setColour (c);
		addChild(this.object);	
	}

	protected void createAppearance (/* ... */) {
		cAppearance.setMaterial (material);
		
		Color3f tmpColor = new Color3f ();
		material.getAmbientColor (tmpColor);
		ColoringAttributes ca = new ColoringAttributes ();
		ca.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);
        ca.setCapability(ColoringAttributes.ALLOW_COLOR_READ);
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

	/* (non-Javadoc)
	 * @see com.sun.j3d.utils.geometry.Primitive#getShape(int)
	 */
	@Override
	public Shape3D getShape(int arg0) {
		return object.getShape(arg0);
	}

	/* (non-Javadoc)
	 * @see com.sun.j3d.utils.geometry.Primitive#getAppearance(int)
	 */
	@Override
	public Appearance getAppearance(int arg0) {
		return object.getAppearance(arg0);
	}

	/* (non-Javadoc)
	 * @see com.sun.j3d.utils.geometry.Primitive#setAppearance(javax.media.j3d.Appearance)
	 */
	@Override
	public void setAppearance(Appearance arg0) {
		object.setAppearance(arg0);		
	}

	////////	MEMBER VARIABLES	////////
	protected int colour = 0;
	protected Material material = Materials.materials[colour];
	
	protected Appearance cAppearance = new Appearance ();
	protected TransparencyAttributes cTransparency;
	
	protected Primitive object;
}
