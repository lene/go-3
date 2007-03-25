/**
 * 
 */
package net.hyperspacetravel.go3.client.gui;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.hyperspacetravel.go3.GoGrid;
import net.hyperspacetravel.go3.ConnectionData;
import net.hyperspacetravel.go3.GameBase;
import net.hyperspacetravel.go3.Utility;
import net.hyperspacetravel.go3.client.CursorListener;

/**
 * @author helge
 *
 */
public class CursorDialog extends JDialog implements CursorListener {

	/* (non-Javadoc)
	 * @see net.hyperspacetravel.go3.client.CursorListener#notifyCursor(int, int, int)
	 */
	public void notifyCursor(int x, int y, int z) {
		// TODO Auto-generated method stub

	}
	
	
	public CursorDialog(GoGrid _grid) {
		this.grid = _grid; 
		initialize();
	}


	/**
	 * Return the contentPane property value.
	 * @return JPanel
	 */
	private JPanel getJContentPane() {
		if (contentPane == null) {
			contentPane = new JPanel();
			contentPane.setName("contentPane");
			contentPane.setLayout(
					new BoxLayout(contentPane,
							BoxLayout.Y_AXIS));

			contentPane.add(getCursorXLabel(), null);
			contentPane.add(getCursorXComboBox(), null);

			contentPane.add(getConnectButton());
		}
		return contentPane;
	}

	////////////////////////////////////////////////////////////////////////////
	//	X coordinate
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Return the cursorXLabel property value.
	 * @return JLabel
	 */
	private JLabel getCursorXLabel() {
		if (cursorXLabel == null) {
			cursorXLabel = new JLabel();
			cursorXLabel.setName("cursorXLabel");
			cursorXLabel.setText("x");
			cursorXLabel.setToolTipText("X coordinate of the cursor");
		}
		return cursorXLabel;
	}
	
	/**
	 * Return the cursorXSlider property value.
	 * @return JSlider
	 */
	private JSlider getCursorXSlider() {
		if (cursorXSlider == null) {
			cursorXSlider = new JSlider();
			cursorXSlider.setName("cursorXSlider");
			cursorXSlider.setMinimum(1);
			cursorXSlider.setMaximum(this.grid.getBoardSize());
			cursorXSlider.setMinorTickSpacing(1);
			cursorXSlider.setValue((this.grid.getBoardSize()+1)/2);
			cursorXSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					grid.setCursor(cursorXSlider.getValue(), 
										cursorYSlider.getValue(), 
										cursorZSlider.getValue());
					cursorXComboBox.setValue(cursorXSlider.getValue());
				}
			});
			
			cursorXSlider.setToolTipText("X coordinate of the cursor");
			cursorXSlider.setEnabled(true);
		}
		return cursorXSlider;
	}
	
	/**
	 * Return the cursorXComboBox property value.
	 * @return JComboBox
	 */
	private JComboBox getCursorXComboBox() {
		if (cursorXComboBox == null) {
			cursorXComboBox = new JComboBox();
			cursorXComboBox.setName("cursorXComboBox");
			cursorXComboBox.setEditable(true);
			cursorXComboBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
				        JComboBox cb = (JComboBox)e.getSource();
				        cursorXSlider.setValue((Integer)cb.getSelectedItem());
				    }						
				});
			cursorXComboBox.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
			        JComboBox cb = (JComboBox)e.getSource();
			        cursorXSlider.setValue((Integer)cb.getSelectedItem());
				}
			});
			cursorXComboBox.setToolTipText("X coordinate of the cursor");
		}
		return cursorXComboBox;
	}

	private JCheckBox getCursorXCheckBox() {
		if (cursorXCheckBox == null) {
			cursorXCheckBox = new JCheckBox();
			cursorXCheckBox.setName("cursorXCheckBox");
			cursorXCheckBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
							//	TODO
						}						
				});
			cursorXCheckBox.setToolTipText("Enable X coordinate of the cursor");
		}
		return cursorXCheckBox;		
	}

	////////////////////////////////////////////////////////////////////////////
	//	Y coordinate
	////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Return the cursorYLabel property value.
	 * @return JLabel
	 */
	private JLabel getCursorYLabel() {
		if (cursorYLabel == null) {
			cursorYLabel = new JLabel();
			cursorYLabel.setName("cursorYLabel");
			cursorYLabel.setText("y");
			cursorYLabel.setToolTipText("Y coordinate of the cursor");
		}
		return cursorYLabel;
	}
	
	/**
	 * Return the cursorYSlider property value.
	 * @return JSlider
	 */
	private JSlider getCursorYSlider() {
		if (cursorYSlider == null) {
			cursorYSlider = new JSlider();
			cursorYSlider.setName("cursorYSlider");
			cursorYSlider.setMinimum(1);
			cursorYSlider.setMaximum(this.grid.getBoardSize());
			cursorYSlider.setMinorTickSpacing(1);
			cursorYSlider.setValue((this.grid.getBoardSize()+1)/2);
			cursorYSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					grid.setCursor(cursorXSlider.getValue(), 
								   cursorYSlider.getValue(), 
								   cursorZSlider.getValue());
					cursorYComboBox.setValue(cursorYSlider.getValue());
				}
			});
			
			cursorYSlider.setToolTipText("Y coordinate of the cursor");
			cursorYSlider.setEnabled(true);
		}
		return cursorYSlider;
	}
	
	/**
	 * Return the cursorYComboBox property value.
	 * @return JComboBox
	 */
	private JComboBox getCursorYComboBox() {
		if (cursorYComboBox == null) {
			cursorYComboBox = new JComboBox();
			cursorYComboBox.setName("cursorYComboBox");
			cursorYComboBox.setEditable(true);
			cursorYComboBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
				        JComboBox cb = (JComboBox)e.getSource();
				        cursorYSlider.setValue((Integer)cb.getSelectedItem());
				    }						
				});
			cursorYComboBox.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
			        JComboBox cb = (JComboBox)e.getSource();
			        cursorYSlider.setValue((Integer)cb.getSelectedItem());
				}
			});
			cursorYComboBox.setToolTipText("Y coordinate of the cursor");
		}
		return cursorYComboBox;
	}

	private JCheckBox getCursorYCheckBox() {
		if (cursorYCheckBox == null) {
			cursorYCheckBox = new JCheckBox();
			cursorYCheckBox.setName("cursorYCheckBox");
			cursorYCheckBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
							//	TODO
						}						
				});
			cursorYCheckBox.setToolTipText("Enable Y coordinate of the cursor");
		}
		return cursorYCheckBox;		
	}

	////////////////////////////////////////////////////////////////////////////
	//	Z coordinate
	////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Return the cursorZLabel property value.
	 * @return JLabel
	 */
	private JLabel getCursorZLabel() {
		if (cursorZLabel == null) {
			cursorZLabel = new JLabel();
			cursorZLabel.setName("cursorZLabel");
			cursorZLabel.setText("z");
			cursorZLabel.setToolTipText("Z coordinate of the cursor");
		}
		return cursorZLabel;
	}
	
	/**
	 * Return the cursorZSlider property value.
	 * @return JSlider
	 */
	private JSlider getCursorZSlider() {
		if (cursorZSlider == null) {
			cursorZSlider = new JSlider();
			cursorZSlider.setName("cursorZSlider");
			cursorZSlider.setMinimum(1);
			cursorZSlider.setMaximum(this.grid.getBoardSize());
			cursorZSlider.setMinorTickSpacing(1);
			cursorZSlider.setValue((this.grid.getBoardSize()+1)/2);
			cursorZSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					grid.setCursor(cursorXSlider.getValue(), 
								   cursorYSlider.getValue(), 
								   cursorZSlider.getValue());
					cursorZComboBox.setValue(cursorZSlider.getValue());
				}
			});
			
			cursorZSlider.setToolTipText("Z coordinate of the cursor");
			cursorZSlider.setEnabled(true);
		}
		return cursorZSlider;
	}
	
	/**
	 * Return the cursorZComboBox property value.
	 * @return JComboBox
	 */
	private JComboBox getCursorZComboBox() {
		if (cursorZComboBox == null) {
			cursorZComboBox = new JComboBox();
			cursorZComboBox.setName("cursorZComboBox");
			cursorZComboBox.setEditable(true);
			cursorZComboBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
				        JComboBox cb = (JComboBox)e.getSource();
				        cursorZSlider.setValue((Integer)cb.getSelectedItem());
				    }						
				});
			cursorZComboBox.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
			        JComboBox cb = (JComboBox)e.getSource();
			        cursorZSlider.setValue((Integer)cb.getSelectedItem());
				}
			});
			cursorZComboBox.setToolTipText("Z coordinate of the cursor");
		}
		return cursorZComboBox;
	}

	private JCheckBox getCursorZCheckBox() {
		if (cursorZCheckBox == null) {
			cursorZCheckBox = new JCheckBox();
			cursorZCheckBox.setName("cursorZCheckBox");
			cursorZCheckBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
							//	TODO
						}						
				});
			cursorZCheckBox.setToolTipText("Enable Z coordinate of the cursor");
		}
		return cursorZCheckBox;		
	}

	////////////////////////////////////////////////////////////////////////////
	//	other elements
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Return the connectButton property value.
	 * @return JButton
	 */
	private JButton getConnectButton() {
		if (connectButton == null) {
			connectButton = new JButton();
			connectButton.setName("connectButton");
			connectButton.setText("Connect to Server");
			connectButton.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					if (e.getActionCommand().equals (connectButton.getText())) {
						Utility.debug("actionPerformed(): "+e.getActionCommand()); // TODO Auto-generated Event stub actionPerformed()
						setVisible (false);
						
					}
				}
			});
		}
		return connectButton;
	}

	/**
	 * Initialize the class.
	 */
	private void initialize() {

		this.setName("Connection Dialog");
		this.setForeground(java.awt.SystemColor.textHighlight);
		this.setModal(true);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(240, 170);
		this.setTitle("Connect to Go Server");
		this.setContentPane(getJContentPane());

	}
	static public void main (String args[]) {
		new ConnectionDialog (new ConnectionData ()).setVisible(true);
	}

	private JPanel contentPane = null;

	private JLabel cursorXLabel = null;
	private JComboBox cursorXComboBox = null;
	private JSlider cursorXSlider = null;
	private JCheckBox cursorXCheckBox = null;
	
	private JLabel cursorYLabel = null;
	private JComboBox cursorYComboBox = null;
	private JSlider cursorYSlider = null;
	private JCheckBox cursorYCheckBox = null;
	
	private JLabel cursorZLabel = null;
	private JComboBox cursorZComboBox = null;
	private JSlider cursorZSlider = null;
	private JCheckBox cursorZCheckBox = null;
	
	private JButton connectButton = null;

	
	private GoGrid grid;

	/**														 */
	private static final long serialVersionUID = 1590600506585137608L;
}
