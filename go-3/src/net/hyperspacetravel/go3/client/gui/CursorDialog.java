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
	private JPanel contentPane = null;

	private JLabel cursorXLabel = null;
	private JComboBox cursorXComboBox = null;
	private JSlider cursorXSlider = null;
	private JCheckBox cursorCheckBox = null;
	

	private JButton connectButton = null;

	public CursorDialog() {		
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
			contentPane.add(getServerPortLabel());
			contentPane.add(getServerPortTextField(), null);
			contentPane.add(getUsernameLabel());
			contentPane.add(getUsernameTextField(), null);
			contentPane.add(getConnectButton());
		}
		return contentPane;
	}

	/**
	 * Return the cursorXLabel property value.
	 * @return JLabel
	 */
	private JLabel getCursorXLabel() {
		if (cursorXLabel == null) {
			cursorXLabel = new JLabel();
			cursorXLabel.setName("cursorXLabel");
			cursorXLabel.setText("Server Host");
			cursorXLabel.setToolTipText("The address of the server.");
		}
		return cursorXLabel;
	}
	
	/**
	 * Return the cursorXSlider property value.
	 * @return JSlider
	 */
	private JSlider getcursorXSlider() {
		if (cursorXSlider == null) {
			cursorXSlider = new JSlider();
			cursorXSlider.setName("cursorXSlider");
			cursorXSlider.setMinimum(GameBase.MIN_GRID_SIZE);
			cursorXSlider.setMaximum(GameBase.MAX_GRID_SIZE);
			cursorXSlider.setMinorTickSpacing(2);
			cursorXSlider.setValue(this.boardSize);
			cursorXSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					String boardSizeString = String.valueOf(cursorXSlider.getValue());
					sizeLabel.setText("Board Size: "+boardSizeString+"x"
							+boardSizeString+"x"+boardSizeString);
					connectionData.setBoardSize(cursorXSlider.getValue());
				}
			});
			
			cursorXSlider.setToolTipText("The size of the board on which you want" +
					" to play. This is not adjustable yet, you have to take" +
					" whichever size the server offers you.");
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
			String[] presetHosts = { "localhost", "hyperspace-travel.de"};
			cursorXComboBox = new JComboBox(presetHosts);
			cursorXComboBox.setName("serverHostTextField");
			cursorXComboBox.setEditable(true);
			cursorXComboBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
				        JComboBox cb = (JComboBox)e.getSource();
				        connectionData.setServerHost((String)cb.getSelectedItem());	
				    }						
				});
			cursorXComboBox.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					connectionData.setServerHost((String)cursorXComboBox.getSelectedItem());					
				}
			});
			cursorXComboBox.setToolTipText("The address of the server.");
		}
		return cursorXComboBox;
	}


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

	/**														 */
	private static final long serialVersionUID = 1590600506585137608L;
}
