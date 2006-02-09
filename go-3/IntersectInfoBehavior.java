
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.picking.*;
import com.sun.j3d.utils.geometry.*;

/**
 Used to respond to mouse pick and drag events in the 3D window. 
 If Utility.DEBUG is set, displays information about the pick.
 @author helge
 */
public class IntersectInfoBehavior extends Behavior {
	
	public IntersectInfoBehavior(GridDisplay J,
			Canvas3D canvas3D, BranchGroup branchGroup) {
		assert GameBase.precondition (J != null, "GridDisplay must exist!");		
		assert GameBase.precondition (canvas3D != null, "Canvas3D must exist!");
		assert GameBase.precondition (branchGroup != null, "BranchGroup must exist!");
		
		pickCanvas = new PickCanvas (canvas3D, branchGroup);
		pickCanvas.setTolerance (PICK_TOLERANCE);
		pickCanvas.setMode (PickCanvas.GEOMETRY_INTERSECT_INFO);
		this.J = J;
		if (Utility.getDebugMode()) {
			createSpheres (branchGroup);
		}
	}
	
	public void initialize() {
		wakeupOn (new WakeupOnAWTEvent(MouseEvent.MOUSE_PRESSED));
	}
	
	public void processStimulus (Enumeration criteria) {
		WakeupCriterion wakeup;
		AWTEvent[] event;
		int eventId;
		
		while (criteria.hasMoreElements()) {
			wakeup = (WakeupCriterion) criteria.nextElement();
			if (wakeup instanceof WakeupOnAWTEvent) {
				event = ((WakeupOnAWTEvent)wakeup).getAWTEvent();
				for (int i=0; i<event.length; i++) { 
					eventId = event[i].getID();
					if (eventId == MouseEvent.MOUSE_PRESSED) {
						int x = ((MouseEvent)event[i]).getX();
						int y = ((MouseEvent)event[i]).getY();
						pickCanvas.setShapeLocation(x, y);
						
						Point3d eyePos = pickCanvas.getStartPosition ();
						try {
							pickResult = pickCanvas.pickAllSorted();
						} catch (CapabilityNotSetException e) {
							continue;
						}
						
						if (pickResult != null) {
							// Get closest intersection results
							PickIntersection pi = 
								pickResult[0].getClosestIntersection(eyePos);
							
							// Position sphere at intersection point
							Vector3d v = new Vector3d();
							Point3d intPt = pi.getPointCoordinatesVW();
							v.set(intPt);
							
							setDebugSpheres(v);
							
							Exchange.setIntersectionPoint (intPt);
							J.processPick ();
						}
					}
				}
			}
		}
		wakeupOn (new WakeupOnAWTEvent(MouseEvent.MOUSE_PRESSED));
	}
	
	
	private void createSpheres (Group group) {
		createAppearances ();
		
		sph[0] = new Sphere(PICKSPHERESIZE*1.15f, redlook);
		sph[1] = new Sphere(PICKSPHERESIZE*1.1f, greenlook);
		
		for (int i = 0; i < 2; i++) {
			sph[i].setPickable (false);
			sphTrans[i] = new TransformGroup ();
			sphTrans[i].setCapability (TransformGroup.ALLOW_TRANSFORM_READ);
			sphTrans[i].setCapability (TransformGroup.ALLOW_TRANSFORM_WRITE);
			
			// Add sphere, transform
			group.addChild (sphTrans[i]);
			sphTrans[i].addChild (sph[i]);
		}
	}
	
	private void createAppearances () {
		redlook = new Appearance();
		
		redlook.setMaterial(new Material(red, black, red, white, SHININESS));
		redlook.setCapability (Appearance.ALLOW_MATERIAL_WRITE);
		
		greenlook = new Appearance();
		greenlook.setMaterial(new Material(green, black, green, white, SHININESS));
	}

	private void setDebugSpheres(Vector3d v) {
		if (Utility.getDebugMode()) {
			spht3.setTranslation (v);
			sphTrans[0].setTransform (spht3);
		}		
	}
	
	
	GridDisplay J;

	PickCanvas pickCanvas;
	PickResult[] pickResult;
	
	Appearance redlook, greenlook;  
	TransformGroup[] sphTrans = new TransformGroup [6];
	Sphere[] sph = new Sphere [6];
	Transform3D spht3 = new Transform3D();
	
	private static float PICK_TOLERANCE = 8.0f;

	
	private static Color3f red = new Color3f (0.5f, 0.0f, 0.0f);
	private static Color3f green = new Color3f(0.0f, 0.8f, 0.0f);
	private static Color3f black = new Color3f(0.0f, 0.0f, 0.0f);
	private static Color3f white = new Color3f(1.0f, 1.0f, 1.0f);
	private static float SHININESS = 50.f;
	private static float PICKSPHERESIZE = 0.1f;
}
