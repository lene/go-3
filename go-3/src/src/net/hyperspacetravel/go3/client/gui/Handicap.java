package net.hyperspacetravel.go3.client.gui;
import javax.media.j3d.Geometry;
import javax.media.j3d.Shape3D;


import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;

class Handicap extends GridObject {
	
	////////	CONSTANTS	////////
	private static float RADIUS = 0.1f;
	private static int NUM_SEGMENTS = 8;

	/** unneccessary parameters for backward compatibilit (TODO) */
	public Handicap (int s, int x, int y, int z) {
		super (s);
	}
	
	protected Geometry createGeometry () {
		Sphere sph = new Sphere (RADIUS, Primitive.ENABLE_GEOMETRY_PICKING, NUM_SEGMENTS);
		Shape3D s = sph.getShape ();
		return s.getGeometry ();
	}
	
}