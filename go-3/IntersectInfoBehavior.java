
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.picking.*;
import com.sun.j3d.utils.geometry.*;

/**
 * Class:       IntersectInfoBehavior
 * 
 * Description: Used to respond to mouse pick and drag events
 *              in the 3D window. Displays information about the pick.
 *
 * Version:     1.0
 *
 */
public class IntersectInfoBehavior extends Behavior {

    float size;
    PickCanvas pickCanvas;
    PickResult[] pickResult;
    Appearance redlook, greenlook;  
    Node oldNode = null;
    GeometryArray oldGeom = null;
    Color3f redColor = new Color3f (1.0f, 0.0f, 0.0f);
    TransformGroup[] sphTrans = new TransformGroup [6];
    Sphere[] sph = new Sphere [6];
    Transform3D spht3 = new Transform3D();
    J3DApp J;

    public IntersectInfoBehavior(J3DApp J,
				 Canvas3D canvas3D, BranchGroup branchGroup,
				 TransformGroup transformGroup,
				 float size) {
	pickCanvas = new PickCanvas (canvas3D, branchGroup);
	pickCanvas.setTolerance (8.0f);
	pickCanvas.setMode (PickCanvas.GEOMETRY_INTERSECT_INFO);
	this.size = size;
	this.J = J;
	// Create an Appearance.
	createAppearances ();
	createSpheres (branchGroup);
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
			    spht3.setTranslation (v);
			    sphTrans[0].setTransform (spht3);

			    Exchange.setIntersectionPoint (intPt);
			    J.processPick ();
			}
		    }
		}
	    }
	}
	wakeupOn (new WakeupOnAWTEvent(MouseEvent.MOUSE_PRESSED));
    }

    private void createAppearances () {
	redlook = new Appearance();
	Color3f objColor = new Color3f(0.5f, 0.0f, 0.0f);
	Color3f black = new Color3f(0.0f, 0.0f, 0.0f);
	Color3f white = new Color3f(1.0f, 1.0f, 1.0f);
	redlook.setMaterial(new Material(objColor, black, objColor, white, 50.0f));
	redlook.setCapability (Appearance.ALLOW_MATERIAL_WRITE);

	greenlook = new Appearance();
	objColor = new Color3f(0.0f, 0.8f, 0.0f);
	greenlook.setMaterial(new Material(objColor, black, objColor, white, 50.0f));
    }

    private void createSpheres (Group group) {
	for (int i=0;i<2;i++) {
	    switch (i) {
	    case 0:
		sph[i] = new Sphere(size*1.15f, redlook);
		break;
	    case 1:
		sph[i] = new Sphere(size*1.1f, greenlook);
		break;
	    }
	    sph[i].setPickable (false);
	    sphTrans[i] = new TransformGroup ();
	    sphTrans[i].setCapability (TransformGroup.ALLOW_TRANSFORM_READ);
	    sphTrans[i].setCapability (TransformGroup.ALLOW_TRANSFORM_WRITE);

	    // Add sphere, transform
	    group.addChild (sphTrans[i]);
	    sphTrans[i].addChild (sph[i]);
	}
    }
}
