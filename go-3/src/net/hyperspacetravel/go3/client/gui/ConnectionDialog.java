package net.hyperspacetravel.go3.client.gui;

/* Created on Feb 6, 2006 by helge
 */

/** select and adjust the server settings
    TODO: when entering an IP manually in the Server Host field, the last letter
    of the IP is not passed. This does not happen with resolved host names.
    Strange. see getJTextField1().
 
 @author helge
*/

import java.awt.event.*;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JSlider;
import javax.swing.JDialog;
import javax.swing.JCheckBox;
import javax.swing.BoxLayout;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.WindowConstants;

import net.hyperspacetravel.go3.ConnectionData;
import net.hyperspacetravel.go3.GameBase;
//import net.hyperspacetravel.go3.Utility;


public class ConnectionDialog extends JDialog {

	private static final long serialVersionUID = 7388416474815978781L;

	private JPanel contentPane = null;

	private JLabel serverHostLabel = null;
	private JComboBox serverHostComboBox = null;

	private JLabel serverPortLabel = null;
	private JTextField serverPortTextField = null;

	private JLabel usernameLabel = null;
	private JTextField usernameTextField = null;

	private JCheckBox startGameCheckBox = null;
	
	private JLabel sizeLabel = null;
	private JSlider sizeSlider = null;

	private JButton connectButton = null;

	private String serverHost = "";
	private int serverPort = 6666;
	private String username = "";
	private int boardSize = 3;
	private ConnectionData connectionData;
	
	public ConnectionDialog(ConnectionData c) {
		super ();
		this.connectionData = c;
		this.boardSize = c.getBoardSize();
		this.serverHost = c.getServerHost();
		this.serverPort = ConnectionData.getServerPort();
		this.username = c.getUsername();
		
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

			contentPane.add(getServerHostLabel(), null);
			contentPane.add(getServerHostComboBox(), null);
			contentPane.add(getServerPortLabel());
			contentPane.add(getServerPortTextField(), null);
			contentPane.add(getUsernameLabel());
			contentPane.add(getUsernameTextField(), null);
			contentPane.add(getConnectButton());
		}
		return contentPane;
	}

	/**
	 * Return the serverHostLabel property value.
	 * @return JLabel
	 */
	private JLabel getServerHostLabel() {
		if (serverHostLabel == null) {
			serverHostLabel = new JLabel();
			serverHostLabel.setName("serverHostLabel");
			serverHostLabel.setText("Server Host");
			serverHostLabel.setToolTipText("The address of the server.");
		}
		return serverHostLabel;
	}
	
	/**
	 * Return the serverHostComboBox property value.
	 * @return JComboBox
	 */
	private JComboBox getServerHostComboBox() {
		if (serverHostComboBox == null) {
			String[] presetHosts = { "localhost", "hyperspace-travel.de"};
			serverHostComboBox = new JComboBox(presetHosts);
			serverHostComboBox.setName("serverHostTextField");
			serverHostComboBox.setEditable(true);
			serverHostComboBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
				        JComboBox cb = (JComboBox)e.getSource();
				        connectionData.setServerHost((String)cb.getSelectedItem());	
				    }						
				});
			serverHostComboBox.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					connectionData.setServerHost((String)serverHostComboBox.getSelectedItem());					
				}
			});
			serverHostComboBox.setToolTipText("The address of the server.");
		}
		return serverHostComboBox;
	}

	/**
	 * Return the serverPortLabel property value.
	 * @return JLabel
	 */
	private JLabel getServerPortLabel() {
		if (serverPortLabel == null) {
			serverPortLabel = new JLabel();
			serverPortLabel.setName("serverPortLabel");
			serverPortLabel.setText("Server Port");
			serverPortLabel.setToolTipText("The port on which the " +
			"server listens. You usually shouldn't need to change this.");
		}
		return serverPortLabel;
	}

	/**
	 * Return the serverPortTextField property value.
	 * @return JTextField
	 */
	private JTextField getServerPortTextField() {
		if (serverPortTextField == null) {
			serverPortTextField = new JTextField();
			serverPortTextField.setName("JTextField2");
			serverPortTextField.setText(String.valueOf(this.serverPort));
			serverPortTextField.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					GameBase.setServerPort(Integer.valueOf(serverPortTextField.getText()).intValue());					
				}
			});
			serverPortTextField.setToolTipText("The port on which the " +
					"server listens. You usually shouldn't need to change this.");
			
		}
		return serverPortTextField;
	}

	/**
	 * Return the usernameLabel property value.
	 * @return JLabel
	 */
	private JLabel getUsernameLabel() {
		if (usernameLabel == null) {
			usernameLabel = new JLabel();
			usernameLabel.setName("JLabel3");
			usernameLabel.setText("Username");
			usernameLabel.setToolTipText("Enter the name under which you want" +
					" to connect to the server. You can only connect once" +
					" under any name.");
		}
		return usernameLabel;
	}

	/**
	 * Return the usernameTextField property value.
	 * @return JTextField
	 */
	private JTextField getUsernameTextField() {
		if (usernameTextField == null) {
			usernameTextField = new JTextField();
			usernameTextField.setName("JTextField3");
			usernameTextField.setText(this.username);
			usernameTextField.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					connectionData.setUsername(usernameTextField.getText());					
				}
			});
			usernameTextField.setToolTipText("Enter the name under which you want" +
					" to connect to the server. You can only connect once" +
					" under any name.");
		}
		return usernameTextField;
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
						System.out.println("actionPerformed(): "+e.getActionCommand()); // TODO Auto-generated Event stub actionPerformed()
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
}  //  @jve:decl-index=0:visual-constraint="10,10"
