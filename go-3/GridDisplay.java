//

import com.sun.j3d.utils.universe.*;
import com.sun.j3d.utils.applet.MainFrame;
import com.sun.j3d.utils.behaviors.mouse.*;
import com.sun.j3d.utils.picking.PickTool;

import javax.media.j3d.*;
import javax.vecmath.*;

import java.awt.*;
import java.awt.event.*;
// import java.applet.Applet;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.KeyStroke;
//import javax.swing.JFrame;
import javax.swing.JApplet;

import java.net.InetAddress;
import java.net.UnknownHostException;


/**
 class GridDisplay represents the canvas on which the GoGrid and all actions
 are shown 
 
 TO DO:
 <ul>
 <li>replace all occurences of <tt>size</tt> with <tt>getBoardSize ()</tt>
 <li>remove <tt>size</tt>, replace with <tt>GoGrid.getBoardSize ()</tt>
 </ul>
 */
public class GridDisplay extends JApplet implements ActionListener {
	
	/**
	 Constructor for known size of the grid and hostname of the server
	 @param size the size of the grid
	 @param hostname the server host
	 @param port the port the server listens on
	 @param username the name under which the player shall be represented
	 */
	public GridDisplay (int size, String hostname, int port, String username) {
		
		//	new StartupDialog (null, true, this).show ();
		
		Utility.setDebugMode (false);
		
		setHostname (hostname);
		setBoardSize (size);
		
		G = new GoGridClient (size, hostname, port, username, this);
		
		//  for some reason known only to the designers of Java3D, this is necessary
		getContentPane ().setLayout (new BorderLayout());				
		
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);
		getContentPane ().add ("North", setupMenu ());
		
		//  create a Canvas3D to perform all subsequent drawing on
		C = new Canvas3D (SimpleUniverse.getPreferredConfiguration());
		getContentPane ().add ("Center", C);
		U = new SimpleUniverse (C);
		
		// Create a simple scene and attach it to the virtual universe
		BranchGroup scene = createSceneGraph (U);
		
		scene.compile ();
		
		//  create a SimpleUniverse with a reference to C
		//  move the ViewPlatform back a bit
		U.getViewingPlatform ().setNominalViewingTransform ();
		
		U.addBranchGraph (scene);
		
	}
	
	
	/**
	 sets a stone at the cursor position and move the cursor to that position
	 if that position is already occupied, does nothing but repainting
	 */
	void setStone () {
		Utility.debug ("begin");
		if (G.setStone ()) 
			setCursor (xc (), yc (), zc ());
		repaint ();						//  IS THIS STILL NECESSARY ?
	}
	
	
	/**
	 set a given number of stones randomly<br>
	 with num = 1, can be used as a very dumb computer opponent
	 @param numMoves number of moves to play
	 @param playerToStart player to start game
	 */
	void fakeGame (int num, int col) {
		G.fakeGame (num, col);
	}
	
	
	/**
	 sets the cursor
	 @param x x position of cursor
	 @param y y position of cursor
	 @param z z position of cursor
	 */
	void setCursor (int x, int y, int z) {
		G.setCursor (x, y, z);
		Transform3D translate = new Transform3D ();		//  ISSUE: does it have to be new ()
		translate.set (new Vector3f ((G.xc ()-1), (G.yc ()-1), (G.zc ()-1)));	//	   every time?
		cursorPos.setTransform (translate);
	}
	
	
	/**
	 draws a stone at a given position
	 @param x x position to set
	 @param y y position to set
	 @param z z position to set
	 @param p parent group
	 */
	void drawStone (int x, int y, int z, BranchGroup p) {
		int col = G.getStone (x, y, z);
		if (col != Colour.EMPTY) {
			Stone stone = new Stone (col);
			//	    stone.setPickable (false);
			//	    stone.clearCapability (Geometry.ALLOW_INTERSECT);
			//	    PickTool.setCapabilities(stone, PickTool.INTERSECT_FULL);
			TransformGroup objTranslate = translate (x, y, z);
			objTranslate.addChild (stone);
			
			BranchGroup b = new BranchGroup ();
			b.addChild (objTranslate);
			p.addChild (b);
			
			setCursor (x, y, z);
		}
	}
	
	
	/**
	 this function is called from the GoGridClient, when it is my turn.
	 enables me to set a stone, and changes the color of the cursor to green,
	 to visually signal that.
	 */
	void activate () {
		active = true;
		greenCursor = new Cursor (Colour.GREEN);
		cursor = greenCursor;
		reinitCursor ();
	}
	
	
	/**
	 this function is called from the GoGridClient, when i have set and thus
	 are not able to set a stone any more. changes the color of the cursor to
	 red, to visually signal that.
	 */
	void deactivate () {
		active = false;
		redCursor = new Cursor (Colour.RED);
		cursor = redCursor;
		reinitCursor ();
	}
	
	
	/** 
	 */
	public void repaint () {
		drawBoard ();
		super.repaint ();
	}
	
	
	/**
	 called from the pick behavior object, attempts to calculate the
	 coordinates on the untransformed grid from the intersection coordinates
	 (often fails). sets the cursor at the found point.<br>
	 remaining issue:<br>
	 inverse transform is generally quite imprecise, the more the
	 greater the user transform					     
	 */
	void processPick () {
		
		Point3d ip = Exchange.getIntersectionPoint ();		//  get picked coordinates from Exchange
		if (ip != null) {
			Transform3D t = new Transform3D ();
			objControl.getTransform (t);			//  get user transform
			t.invert ();					//  invert
			t.transform (ip);					//  apply
			objTransform.getTransform (t);			//  get base transform
			t.invert ();					//  invert
			t.transform (ip);					//  apply
			ip.add (new Vector3d ((size-1)/2+1,(size-1)/2+1, (size-1)/2+1));
			
			setCursor ((int)Math.round (ip.x), (int)Math.round (ip.y), (int)Math.round (ip.z));
		}	
	}
	
	
	void exit () {
		System.exit (0);
	}
	
	/**
	 makes the grid less visible. passes the call to the Grid object.
	 */
	void transparencyUp () { objGrid.transparencyUp(); }

	/**
	 makes the grid more visible. passes the call to the Grid object.
	 */
	void transparencyDown () { objGrid.transparencyDown(); }
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          PUBLIC SECTION ENDS                                           //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	/**
	 changes the cursor for another.<br>
	 */
	private void reinitCursor () {
		try {
			cursorBG.detach ();
			setupCursor (parentBranch);
			setCursor (xc (), yc (), zc ());
			
			repaint ();
		} catch (NullPointerException e) { }
	}
	
	
	/** create the scene graph containing lights, behaviors, transforms and
	 objects.
	 @param U the SimpleUniverse to act upon
	 @return the root of the scene graph
	 */
	private BranchGroup createSceneGraph (SimpleUniverse U) {
		BranchGroup objRoot = new BranchGroup ();		//  create the root of the branch graph
		
		setupLights (objRoot);					//  create lighting parameters
		
		objGrid = new Grid (size);				//  create grid
		objGrid.setPickable (false);
		//  set up transforms:
		Transform3D translate = new Transform3D ();		//  translation, centers grid at origin
		float cen = -(size-1)/2.f;
		translate.set (new Vector3f (cen, cen, cen));
		Transform3D rotate = new Transform3D ();		//  rotation, slightly tilts grid
		rotate.rotX (Math.PI/8);
		Transform3D scale = new Transform3D ();			//  scale to fit it on the screen
		scale.set (1./size);
		Transform3D transform = scale;				//  combine scale & rotation
		transform.mul (rotate);
		
		objTransform = new TransformGroup (transform);
		objTransform.setCapability (TransformGroup.ALLOW_TRANSFORM_READ);	//  allow reading to
		//  calculate inverse transform for picks
		objRoot.addChild (objTransform);			//  first child of root node
		
		objControl = setupBehavior (objRoot);			//  TG for mouse and keyboard behaviors
		
		objTransform.addChild (objControl);			//  grandchild of root
		
		objectParent = new TransformGroup (translate);		//  translation must be inserted here,
		objectParent.setCapability (BranchGroup.ALLOW_CHILDREN_READ);	//  must be able to read,
		objectParent.setCapability (BranchGroup.ALLOW_CHILDREN_WRITE);	//  edit,
		objectParent.setCapability (BranchGroup.ALLOW_CHILDREN_EXTEND);	//  and add children
		objControl.addChild (objectParent);			//  won't work otherwise
		
		parentBranch = new BranchGroup ();			//  BG to father all objects, 
		parentBranch.setCapability (BranchGroup.ALLOW_CHILDREN_READ);	//  must be able to read 
		parentBranch.setCapability (BranchGroup.ALLOW_CHILDREN_WRITE);	//  edit,
		parentBranch.setCapability (BranchGroup.ALLOW_CHILDREN_EXTEND);	//  and add children
		parentBranch.setCapability (BranchGroup.ALLOW_DETACH);	//  enable quick deletion
		objectParent.addChild (parentBranch);
		
		bs = new BoundingSphere ();				//  BoundingSphere contains whole scene
		
		//  add objects:
		objectParent.addChild (objGrid);			//  the grid
		createHandicaps (objectParent);				//  the handicap markers
		createPickPoints (objectParent);			//  points as aid for picking
		
		cursor = blueCursor;
		setupCursor (objRoot);					//  create the cursor
		
		if (false)						//  if a game is already loaded
			drawBoard ();					//  draw the stones
		
		return objRoot;
	}
	
	/**
	 <li>clear all stones, because they can not be edited
	 <li>loop over all positions on the grid and draw any present stones
	 */
	private void drawBoard () {
		Utility.debug ("begin");
		
		//  clear the stones which are displayed now
		try {
			BranchGroup tmpBranch = new BranchGroup ();			//  BG to father all objects, 
			tmpBranch.setCapability (BranchGroup.ALLOW_CHILDREN_READ);	//  must be able to read 
			tmpBranch.setCapability (BranchGroup.ALLOW_CHILDREN_WRITE);	//  and edit children
			tmpBranch.setCapability (BranchGroup.ALLOW_CHILDREN_EXTEND);
			tmpBranch.setCapability (BranchGroup.ALLOW_DETACH);
			
			parentBranch.detach ();
			parentBranch = tmpBranch;
			
			objectParent.addChild (parentBranch);
		} catch (NullPointerException e) { }
		
		for (int x = 1; x <= getBoardSize (); x++)
			for (int y = 1; y <= getBoardSize (); y++)
				for (int z = 1; z <= getBoardSize (); z++) {
					//  the exception is caught in the inner loop so we can
					//  continue if anything goes wrong; ineffective but secure
					try {
						if (G.getStone (x, y, z) != 0) {
							drawStone (x, y, z, parentBranch);
						} }
					catch (NullPointerException e) { }
				}
	}
	
	
	/**
	 create a translation TransformGroup to set an object at a given position
	 on the grid
	 @param x translation in x direction
	 @param y translation in y direction
	 @param z translation in z direction
	 @return TransformGroup, ready to use
	 */	
	private TransformGroup translate (int x, int y, int z) {
		Transform3D translate = new Transform3D ();
		translate.set (new Vector3f (x-1, y-1, z-1));		//  set translation
		TransformGroup objTranslate = new TransformGroup (translate);
		return objTranslate;
	}
	
	
	/**
	 set up the lighting of the scene
	 @param objRoot the scene's root node
	 */
	private void setupLights (BranchGroup objRoot) {
		// Set up the ambient light
		Color3f ambientColor = new Color3f(0.1f, 0.1f, 0.1f);
		AmbientLight ambientLightNode = new AmbientLight(ambientColor);
		ambientLightNode.setInfluencingBounds(bounds);
		objRoot.addChild(ambientLightNode);
		
		// Set up the directional lights
		Color3f light1Color = new Color3f(1.0f, 1.0f, 0.9f);
		Vector3f light1Direction  = new Vector3f(4.0f, -7.0f, -12.0f);
		Color3f light2Color = new Color3f(0.3f, 0.3f, 0.4f);
		Vector3f light2Direction  = new Vector3f(-6.0f, -2.0f, -1.0f);
		
		DirectionalLight light1
		= new DirectionalLight(light1Color, light1Direction);
		light1.setInfluencingBounds(bounds);
		objRoot.addChild(light1);
		
		DirectionalLight light2
		= new DirectionalLight(light2Color, light2Direction);
		light2.setInfluencingBounds(bounds);
		objRoot.addChild(light2);
	}
	
	
	/** 
	 set up the interaction behaviors:
	 <ul>
	 <li>the keyboard interaction behaviors
	 <li>the mouse interaction behaviors
	 <li>the picking behavior
	 </ul>
	 @param objRoot the scene's root node
	 */
	private TransformGroup setupBehavior (BranchGroup objRoot) {
		TransformGroup objControl = new TransformGroup ();
		objControl.setCapability (TransformGroup.ALLOW_TRANSFORM_WRITE);
		objControl.setCapability (TransformGroup.ALLOW_TRANSFORM_READ);
		
		BoundingSphere bs = new BoundingSphere ();
		
		SimpleBehavior myRotator = new SimpleBehavior (this, objControl);
		myRotator.setSchedulingBounds (bs);
		objRoot.addChild (myRotator);
		
		MouseRotate myMouseRotate = new MouseRotate ();
		myMouseRotate.setTransformGroup (objControl);
		myMouseRotate.setFactor (0.010f, 0.010f);
		myMouseRotate.setSchedulingBounds (bs);
		objRoot.addChild (myMouseRotate);
		
		MouseTranslate myMouseTranslate = new MouseTranslate ();
		myMouseTranslate.setTransformGroup (objControl);
		myMouseTranslate.setFactor (0.010f, 0.010f);
		myMouseTranslate.setSchedulingBounds (bs);
		objRoot.addChild (myMouseTranslate);
		
		MouseZoom myMouseZoom = new MouseZoom ();
		myMouseZoom.setTransformGroup (objControl);
		// myMouseZoom.setFactor (factor);
		myMouseZoom.setSchedulingBounds (bs);
		objRoot.addChild (myMouseZoom);
		
		IntersectInfoBehavior behavior =
			new IntersectInfoBehavior (this, C, objRoot, objectParent, 0.02f);
		behavior.setSchedulingBounds (bs);
		objRoot.addChild (behavior);
		
		return objControl;
	}
	
	
	/** 
	 translate the cursor and add it to the scene graph
	 @param objRoot
	 */
	private void setupCursor (BranchGroup objRoot) {
		cursorPos = translate (size/2, size/2, size/2);
		cursorPos.setCapability (TransformGroup.ALLOW_TRANSFORM_WRITE);
		cursorPos.setCapability (TransformGroup.ALLOW_TRANSFORM_READ);
		cursorBG = new BranchGroup ();
		cursorBG.setCapability (BranchGroup.ALLOW_DETACH);
		cursorPos.addChild (cursor);
		cursorBG.addChild (cursorPos);
		objectParent.addChild (cursorBG);
	}
	
	
	/** 
	 create the geometry of the handicap markers and add it to the scene graph
	 @param parent
	 */
	private void createHandicaps (TransformGroup parent) {
		int min = (size-1)/4+1,
		step = (size+1)/4;
		for (int x = min; x <= size-min+1; x += step) {
			for (int y = min; y <= size-min+1; y += step) {
				for (int z = min; z <= size-min+1; z += step) {
					TransformGroup objHTr = translate(x,y,z);
					parent.addChild (objHTr);
					Shape3D h = new Handicap (size, x,y,z);
					PickTool.setCapabilities (h, PickTool.INTERSECT_FULL);
					objHTr.addChild (h);
				}
			}
		}
	}
	
	
	/**
	 create the geometry of the pick points and add it to the scene graph
	 @param parent
	 */
	private void createPickPoints (TransformGroup parent) {
		if (points == null) setupPoints ();
		
		for (int x = 1; x <= size; x++)
			for (int y = 1; y <= size; y++)
				for (int z = 1; z <= size; z++) {
					TransformGroup objTranslate = translate (x, y, z);
					PointArray p = points;
					Shape3D s = new Shape3D (p);
					PickTool.setCapabilities(s, PickTool.INTERSECT_FULL);
					parent.addChild (objTranslate);
					objTranslate.addChild (s);
				}
	}
	
	
	/**
	 create the geometry of the pick points: a point
	 */
	private void setupPoints () {
		points = new PointArray (1, GeometryArray.COORDINATES);
		points.setCoordinate (0, new Point3f (0.f, 0.f, 0.f));
	}	
	
	/** 
	 calculate the number of liberties the caller has at a given point
	 @param x
	 @param y
	 @param z
	 @param current the player whose liberties are checked
	 @param shortCut 
	 @return number of liberties
	 */
	private int Liberty (int x, int y, int z, int current, boolean shortCut) {
		return G.Liberty (x, y, z,  current, shortCut);
	}
	
	/**
	 display the number of liberties at the current cursor position
	 */
	void Liberty () {
		JOptionPane.showMessageDialog (this,
				new Integer (Liberty (xc (), yc (), zc (), G.getCurrentPlayer (), false)),
				"Liberties at...",
				JOptionPane.INFORMATION_MESSAGE );
	}
	
	/** 
	 add a menu to a JMenuBar
	 @param menuBar
	 @param menuTitle
	 @param mnemonic
	 @param description
	 @return the added menu
	 */
	private JMenu addMenu (JMenuBar menuBar, String menuTitle, int mnemonic, String description) {
		JMenu menu = new JMenu(menuTitle);
		if (mnemonic != 0) 
			menu.setMnemonic(mnemonic);
		menu.getAccessibleContext().setAccessibleDescription(description);
		menuBar.add(menu);
		
		return menu;
	}
	
	/** 
	 add a JMenuItem to a JMenu
	 @param menu
	 @param ItemTitle
	 @param mnemonic
	 @param description
	 @param enabled
	 @return the added menu
	 */
	private void addMenuItem (JMenu menu, String itemTitle, int mnemonic, int actionMask, String description, boolean enabled) {
		JMenuItem menuItem = new JMenuItem(itemTitle);
		if (mnemonic != 0)
			menuItem.setAccelerator(KeyStroke.getKeyStroke(mnemonic, actionMask));
		menuItem.getAccessibleContext().setAccessibleDescription(description);
		menuItem.addActionListener(this);
		menuItem.setEnabled(enabled);
		menu.add(menuItem);
	}
	
	/**
	 set up the menu structure
	 */
	JMenuBar setupMenu () {
		//Where the GUI is created:
		JMenuBar menuBar = new JMenuBar();
		
		JMenu menu = addMenu (menuBar, "File", KeyEvent.VK_F, 
		"The only menu in this program that has menu items");
		addMenuItem (menu, "Connect", 
				KeyEvent.VK_C, ActionEvent.ALT_MASK, 
				"This doesn't really do anything yet", false);
		addMenuItem (menu, "Quit", 
				KeyEvent.VK_Q, ActionEvent.CTRL_MASK, 
				"Exit the Program", true);
		
		menu = addMenu (menuBar, "View", KeyEvent.VK_V, 
		"Adjusting the display of the board");
		addMenuItem (menu, "Make grid less visible", 
				KeyEvent.VK_G, ActionEvent.CTRL_MASK,
				"Make grid less visible", false);
		addMenuItem (menu, "Make grid more visible", 
				KeyEvent.VK_G, ActionEvent.SHIFT_MASK,
				"Make grid more visible", false);
		
		menu = addMenu (menuBar, "Navigation", KeyEvent.VK_N, 
		"Rotating and moving the board");
		addMenuItem (menu, "Cursor down one in x", 
				KeyEvent.VK_X, 0, 
				"Decrease X Coordinate of cursor by 1", false);
		addMenuItem (menu, "Cursor up one in x", 
				KeyEvent.VK_X, ActionEvent.SHIFT_MASK, 
				"Increase X Coordinate of cursor by 1", false);
		addMenuItem (menu, "Cursor down one in y", 
				KeyEvent.VK_Y, 0, 
				"Decrease Y Coordinate of cursor by 1", false);
		addMenuItem (menu, "Cursor up one in y", 
				KeyEvent.VK_Y, ActionEvent.SHIFT_MASK, 
				"Increase Y Coordinate of cursor by 1", false);
		addMenuItem (menu, "Cursor down one in z", 
				KeyEvent.VK_Z, 0, 
				"Decrease Z Coordinate of cursor by 1", false);
		addMenuItem (menu, "Cursor up one in z", 
				KeyEvent.VK_Z, ActionEvent.SHIFT_MASK, 
				"Increase Z Coordinate of cursor by 1", false);
		menu.addSeparator ();
		addMenuItem (menu, "Rotate right 5?", 
				KeyEvent.VK_RIGHT, 0,
				"Rotate 5 degrees in positive x direction (right)", false);
		addMenuItem (menu, "Rotate left 5?", 
				KeyEvent.VK_LEFT, 0,
				"Rotate 5 degrees in negative x direction (left)", false);
		addMenuItem (menu, "Rotate up 5?", 
				KeyEvent.VK_UP, 0,
				"Rotate 5 degrees in positive y direction (up)", false);
		addMenuItem (menu, "Rotate left 5?", 
				KeyEvent.VK_DOWN, 0,
				"Rotate 5 degrees in negative y direction (down)", false);
		menu.addSeparator ();
		addMenuItem (menu, "Rotate right 1?", 
				KeyEvent.VK_RIGHT, ActionEvent.ALT_MASK,
				"Rotate 1 degrees in positive x direction (right)", false);
		addMenuItem (menu, "Rotate left 1?", 
				KeyEvent.VK_LEFT, ActionEvent.ALT_MASK,
				"Rotate 1 degrees in negative x direction (left)", false);
		addMenuItem (menu, "Rotate up 1?", 
				KeyEvent.VK_UP, ActionEvent.ALT_MASK,
				"Rotate 1 degrees in positive y direction (up)", false);
		addMenuItem (menu, "Rotate left 1?", 
				KeyEvent.VK_DOWN, ActionEvent.ALT_MASK,
				"Rotate 1 degrees in negative y direction (down)", false);
		menu.addSeparator ();
		addMenuItem (menu, "Rotate right 45?", 
				KeyEvent.VK_RIGHT, ActionEvent.SHIFT_MASK,
				"Rotate 45 degrees in positive x direction (right)", false);
		addMenuItem (menu, "Rotate left 45?", 
				KeyEvent.VK_LEFT, ActionEvent.SHIFT_MASK,
				"Rotate 45 degrees in negative x direction (left)", false);
		addMenuItem (menu, "Rotate up 45?", 
				KeyEvent.VK_UP, ActionEvent.SHIFT_MASK,
				"Rotate 45 degrees in positive y direction (up)", false);
		addMenuItem (menu, "Rotate left 45?", 
				KeyEvent.VK_DOWN, ActionEvent.SHIFT_MASK,
				"Rotate 45 degrees in negative y direction (down)", false);
		menu.addSeparator ();
		addMenuItem (menu, "Default position", 
				KeyEvent.VK_NUMPAD5, 0,
				"Reset board to default position", false);
		
		menu = addMenu (menuBar, "Setting", KeyEvent.VK_N, 
		"Setting stones");
		addMenuItem (menu, "Set", 
				KeyEvent.VK_SPACE, 0, 
				"Set a stone at the current cursor location", false);
		addMenuItem (menu, "Show Liberties", 
				KeyEvent.VK_L, ActionEvent.CTRL_MASK,
				"Query liberties of grid point under cursor", false);
		
		menu = addMenu (menuBar, "Help", KeyEvent.VK_H, 
		"Rudimentary documentation about the program");
		
		addMenuItem (menu, "About", 
				KeyEvent.VK_A, ActionEvent.ALT_MASK,
				"About the Program", true);
		
		return menuBar;
	}
	
	private void about () {
		JOptionPane.showMessageDialog (this,
				"Sorry, there is no help available apart from\n"+
				"what you can deduce from the menu structure.",
				"About Go?",
				JOptionPane.INFORMATION_MESSAGE );	
	}
	
	/**
	 implementation of actionPerformed for interface ActionListener
	 
	 */    
	public void actionPerformed(ActionEvent e) {
		JMenuItem source = (JMenuItem)(e.getSource());
		String text = source.getText();
		if (text == "Quit")
			exit ();
		
		if (text == "About") {
			about ();
			return;
		}
		
		JOptionPane.showMessageDialog (this,
				text,
				"Action Event",
				JOptionPane.INFORMATION_MESSAGE );
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	String hostname = "localhost";
	void setHostname (String hostname) { 
		this.hostname = hostname; System.err.println ("Server host: "+hostname); }
	String getHostname () { return hostname; }
	
	//  GoGrid related variables, getters and setters
	/**
	 @return cursor x position
	 */
	public int xc () { return G.xc (); }
	/**
	 @return cursor x position
	 */
	public int yc () { return G.yc (); }
	/**
	 @return cursor x position
	 */
	public int zc () { return G.zc (); }
	
	/**
	 board size
	 */
	private int size = 0;
	/** 
	 @return board size
	 */
	int getBoardSize () { return size; }
	/**
	 sets the board size
	 @param s board size
	 */
	void setBoardSize (int s) { size = s; }
	
	/**
	 current player's turn?
	 */
	private boolean active = false;
	
	//    int currentMove = 0, evaluatedMove = 0;
	
	/**
	 i made this member package private, but honestly, i should make this 
	 private and use getters and setters whenever i need access
	 */
	GoGrid G;
	
	
	//  Java3D display related variables
	
	/**
	 stores the geometry of the pick points: a single simple point
	 */
	private PointArray points = null;
	
	/**
	 the geometry of the grid
	 */
//	private Shape3D objGrid = null;
	private Grid objGrid = null;
	/**
	 transform belonging to the cursor
	 */
	private TransformGroup cursorPos;
	/**
	 BranchGroup as parent of the cursor
	 */
	private BranchGroup cursorBG;
	/**
	 TG for mouse and keyboard behaviors
	 */
	private TransformGroup objControl;
	/**
	 transform node for the whole scene; first child of root node
	 */
	private TransformGroup objTransform;
	
	/**
	 the canvas on which we draw
	 */
	private Canvas3D C;
	/**
	 our scene universe
	 */
	private SimpleUniverse U;
	
	/**
	 a bounding sphere containing the whole scene,used for the lights
	 */
	private BoundingSphere bounds = new BoundingSphere(new Point3d(0.0,0.0,0.0), 1000.0);
	
	/**
	 a bounding sphere containing the whole scene,used for behaviors<br>
	 WHAT IS THE DIFFERENCE TO <tt>bounds</tt>?
	 */
	private BoundingSphere bs;
	
	/**
	 the TransformGroup which describes the transformation of the scene
	 */
	private TransformGroup objectParent;
	/**
	 @return the TransformGroup which describes the transformation of the scene
	 */
	TransformGroup getObjectParent () {
		return objectParent;
	}
	
	/**
	 the BranchGroup under which all stones are stored<br>
	 THIS IS A MISNOMER, FIND A BETTER NAME
	 */
	private BranchGroup parentBranch;
	/**
	 @return the BranchGroup under which all stones are stored
	 */
	BranchGroup getParentBranch () {
		return parentBranch;
	}
	
	/**
	 duration of one rotation in animation mode in msecs
	 */
	private int rotationPeriod = 10000;
	
	/**
	 the current cursor
	 */
	private Cursor cursor;
	/**
	 the not-yet-enabled (pre-game start) cursor
	 */
	private Cursor blueCursor = new Cursor (Colour.BLUE);
	/**
	 the inactive cursor
	 */
	private Cursor redCursor = new Cursor (Colour.RED);
	/**
	 the active cursor
	 */
	private Cursor greenCursor = new Cursor (Colour.GREEN);
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          STATIC SECTION STARTS                                         //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	/**
	 print a message on how to call the program
	 */
	protected static void help () {
		System.out.println ("usage: java GridDisplay [-s|--board-size boardsize]\n"+
				"                   [-m|--auto-moves automoves]\n"+
		    "                   [-h|--server-host serverhost]\n"+
	      "                   [-p|--server-port serverport]\n");
	}
	
	/**
	 board size
	 */
	static int s = 3;
	/**
	 number of moves to fake
	 */
	static int m = 0;
	/**
	 server port
	 */
	static int p = 6666;
	/**
	 server host
	 */
	static String h = "localhost";
	/**
	 user id
	 */
	static String u = "";
	
	
	/**
	 parse the command line and set appropriate options
	 */
	protected static void parse (String [] args) {
		
		try {
			InetAddress addr = InetAddress.getLocalHost();
      u = System.getProperty ("user.name")+"@"+addr.getHostName();
    } catch (UnknownHostException e) {
    	u = System.getProperty ("user.name");
    }    
  
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-s") || args[i].equals("--board-size")) {
				try {
					s = Integer.parseInt(args[++i]);
				} catch (NumberFormatException e) {
					help ();
				}
			}
			if (args[i].equals("-m") || args[i].equals("--auto-moves")) {
				try {
					m = Integer.parseInt(args[++i]);
				} catch (NumberFormatException e) {
					help ();
				}
			}
			if (args[i].equals("-p") || args[i].equals("--server-port")) {
				try {
					p = Integer.parseInt(args[++i]);
				} catch (NumberFormatException e) {
					help ();
				}
			}
			if (args[i].equals("-h") || args[i].equals("--server-host")) {
				h = args[++i];
			}
			if (args[i].equals("-u") || args[i].equals("--user-id")) {
				u = args[++i];
			}
		}
		Utility.debug("Board size  = "+s);
		Utility.debug("Pre-moves   = "+m);
		Utility.debug("Server port = "+p);
		Utility.debug("Server host = "+h);
		Utility.debug("Username    = "+u);
	}
	
	
	/**
	 main method; allows this class to be run as an application as well as an
	 applet
	 */
	public static void main (String[] args) {
		parse (args);
		
		ConnectionDialog conn = new ConnectionDialog (s,h,p,u);
		conn.setVisible(true);
		
		GridDisplay game = new GridDisplay (s, h, p, u);
		Frame frame = new MainFrame (game, 600, 600);
		/*	JFrame jFrame = new JFrame (frame.getGraphicsConfiguration ());
		 jFrame.setJMenuBar (game.setupMenu ());
		 jFrame.setContentPane ();
		 */
		for (int i = 0; i < m; i++) {
			game.fakeGame (2, Colour.BLACK);
			game.repaint ();
		}
		
	}
};
