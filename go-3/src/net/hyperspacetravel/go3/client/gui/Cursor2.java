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
import javax.vecmath.Color3f;

import net.hyperspacetravel.go3.Colour;
import net.hyperspacetravel.go3.GameBase;

import com.sun.j3d.utils.geometry.Primitive;

/**
 * @author helge
 *
 */
abstract class Cursor2 extends Primitive {
	protected static float CURSOR_LINEWIDTH = 2.f;
	protected static float CURSOR_TRANSPARENCY = 0.5f;
	protected static int DEFAULT_COLOUR = Colour.BLUE;

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
	protected int colour = 0;
	protected Material material = Materials.materials[colour];
	
	protected Appearance cAppearance = new Appearance ();
	protected TransparencyAttributes cTransparency;

}
