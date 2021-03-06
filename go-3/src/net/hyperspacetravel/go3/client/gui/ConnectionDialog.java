package net.hyperspacetravel.go3.client.gui;

/* Created on Feb 6, 2006 by helge
 */

/** select and adjust the server settings
    TODO: when entering an IP manually in the Server Host field, the last letter
    of the IP is not passed. This does not happen with resolved host names.
    Strange. see getJTextField1().
 
 @author helge
*/

import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.BoxLayout;
import javax.swing.WindowConstants;

import net.hyperspacetravel.go3.ConnectionData;
import net.hyperspacetravel.go3.GameBase;
import net.hyperspacetravel.go3.Utility;

public class ConnectionDialog extends JDialog {

	public ConnectionDialog(ConnectionData c) {
		super ();
		this.connectionData = c;
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
			contentPane.add(getButtonPanel());
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
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
				        connectionData.setServerHost((String)serverHostComboBox.getSelectedItem());	
				    }						
				});
			serverHostComboBox.addKeyListener(new KeyAdapter() {
				public void keyTyped(KeyEvent e) {
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
			serverPortTextField.addKeyListener(new KeyAdapter() {
				public void keyTyped(KeyEvent e) {
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
			usernameTextField.addKeyListener(new KeyAdapter() {
				public void keyTyped(KeyEvent e) {
					connectionData.setUsername(usernameTextField.getText()+e.getKeyChar());					
				}
			});
			usernameTextField.setToolTipText("Enter the name under which you want" +
					" to connect to the server. You can only connect once" +
					" under any name.");
		}
		return usernameTextField;
	}

	private JPanel getButtonPanel() {
		if(buttonPanel == null) {
			buttonPanel = new JPanel();
			buttonPanel.setName("buttonPanel");
			buttonPanel.setLayout(
					new BoxLayout(buttonPanel,
							BoxLayout.X_AXIS));

			buttonPanel.add(getCancelButton(), null);
			buttonPanel.add(Box.createHorizontalGlue());
			buttonPanel.add(getConnectButton());
		}
		return buttonPanel;
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
			connectButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
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
	 * Return the cancelButton property value.
	 * @return JButton
	 */
	private JButton getCancelButton() {
		if (cancelButton == null) {
			cancelButton = new JButton();
			cancelButton.setName("cancelButton");
			cancelButton.setText("Cancel");
			cancelButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (e.getActionCommand().equals (cancelButton.getText())) {
						System.exit(0);
					}
				}
			});
		}
		return cancelButton;
	}

	/**
	 * Initialize the class.
	 */
	private void initialize() {

		this.setName("Connection Dialog");
		this.setForeground(SystemColor.textHighlight);
		this.setModal(true);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(240, 170);
		this.setTitle("Connect to Go Server");
		this.setContentPane(getJContentPane());
	}
	static public void main (String args[]) {
		new ConnectionDialog (new ConnectionData ()).setVisible(true);
	}

	private static final long serialVersionUID = 7388416474815978781L;

	private JPanel contentPane = null;

	private JLabel serverHostLabel = null;
	private JComboBox serverHostComboBox = null;

	private JLabel serverPortLabel = null;
	private JTextField serverPortTextField = null;

	private JLabel usernameLabel = null;
	private JTextField usernameTextField = null;

	private JPanel buttonPanel = null;
	private JButton connectButton = null;
	private JButton cancelButton = null;

	private int serverPort = 6666;
	private String username = "";
	private ConnectionData connectionData;
	
}  //  @jve:decl-index=0:visual-constraint="10,10"
