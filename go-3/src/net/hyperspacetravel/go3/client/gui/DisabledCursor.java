package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.Appearance;
import javax.media.j3d.Shape3D;

import net.hyperspacetravel.go3.GameBase;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;


public class DisabledCursor extends Cursor {
	
	////////	CONSTANTS	////////
	private static float RADIUS = 0.5f;
	private static int NUM_SEGMENTS = 16;
	
	////////	C'TORS		////////
	/** default constructor: creates a cursor of default color */
	public DisabledCursor () {
	}
	
	public DisabledCursor (int c) {
	}
}
