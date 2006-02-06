

import javax.media.j3d.*;
//import com.sun.j3d.utils.geometry.Primitive;
//import com.sun.j3d.utils.geometry.Sphere;
//import com.sun.j3d.utils.applet.MainFrame;
import javax.vecmath.*;

abstract public class GridObject extends Shape3D {
	
	public GridObject (int s) {
		setSize (s);
		gGeometry = createGeometry ();
		gAppearance = createAppearance ();
		this.setGeometry (gGeometry);
		this.setAppearance (gAppearance);
	}
	
	public static void setSize (int s) {
		size = s;
	}
	
	public static int getSize () {
		return size;
	}
	
	protected static int size;
	protected Color3f gColor = new Color3f (0.8f,0.8f,0.8f);
	protected Appearance gAppearance;
	protected Geometry gGeometry;
	
	protected Appearance createAppearance (/* ... */) {
		Appearance app = new Appearance ();
		
		Material m = new Material ();
		app.setMaterial (m);
		
		ColoringAttributes ca = new ColoringAttributes ();
		ca.setColor (gColor);
		app.setColoringAttributes (ca);
		
		if (true) {
			LineAttributes la = new LineAttributes ();
			la.setLineAntialiasingEnable(true);
			app.setLineAttributes (la);
		}
		
		if (true) {
			PolygonAttributes pa = new PolygonAttributes ();
			pa.setCullFace (PolygonAttributes.CULL_NONE);
			//	    pa.setPolygonMode (PolygonAttributes.POLYGON_LINE);
			app.setPolygonAttributes (pa);
		}
		
		if (true) {
			TransparencyAttributes ta = new TransparencyAttributes (TransparencyAttributes.NICEST, 0.5f);
			app.setTransparencyAttributes (ta);
		}
		
		return app;
	}
	
	abstract protected Geometry createGeometry ();
	
}

