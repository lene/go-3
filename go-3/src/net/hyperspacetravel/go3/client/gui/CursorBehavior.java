package net.hyperspacetravel.go3.client.gui;
//

import javax.media.j3d.*;

//import com.sun.j3d.utils.universe.*;
//import com.sun.j3d.utils.geometry.*;
//import com.sun.j3d.utils.applet.MainFrame;
//import com.sun.j3d.utils.behaviors.keyboard.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Enumeration;
//import javax.vecmath.*;
//import java.applet.Applet;

//

class CursorBehavior extends Behavior {

    CursorBehavior (J3DApp J, int x, int y, int z) {
	this.J = J;
	this.x = x;
	this.y = y;
	this.z = z;
    }

    public void initialize () {
	//	wakeupOn (new WakeupOnAWTEvent (KeyEvent.KEY_PRESSED));
	wakeupOn (new WakeupOnAWTEvent (KeyEvent.KEY_PRESSED));
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
		    if (event[i] instanceof KeyEvent) {
			KeyEvent eventKey = (KeyEvent) event[i];
			if (eventId == KeyEvent.KEY_PRESSED) {
			    int keyCode = eventKey.getKeyCode();
			    int delta = -1;
			    if (eventKey.isShiftDown ()) {
				delta = 1;
			    }

			    int xc = J.xc (), yc = J.yc (), zc = J.zc ();
			    if (keyCode == KeyEvent.VK_X)
				xc += delta;
			    else if (keyCode == KeyEvent.VK_Y)
				yc += delta;
			    else if (keyCode == KeyEvent.VK_Z)
				zc += delta;

			    J.setCursor (xc, yc, zc);
			}
		    }
		}
	    }
	}
	wakeupOn (new WakeupOnAWTEvent (KeyEvent.KEY_PRESSED));
    }
	
    int x, y, z;
    private J3DApp J;
}

