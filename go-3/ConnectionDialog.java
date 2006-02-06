
/* Created on Feb 6, 2006 by helge
 */

/**
 @author helge
 
 TODO
 */
/**
 * Example of components laid out in a grid
 */
public class ConnectionDialog extends javax.swing.JFrame {

	private javax.swing.JPanel contentPane = null;

	private javax.swing.JLabel serverHostLabel = null;
	private javax.swing.JTextField serverHostTextField = null;

	private javax.swing.JLabel serverPortLabel = null;
	private javax.swing.JTextField serverPortTextField = null;

	private javax.swing.JLabel usernameLabel = null;
	private javax.swing.JTextField usernameTextField = null;

	private javax.swing.JLabel sizeLabel = null;
	private javax.swing.JSlider sizeSlider = null;

	private javax.swing.JButton connectButton = null;

	public ConnectionDialog() {
		super();
		initialize();
	}


	/**
	 * Return the JFrameContentPane property value.
	 * @return javax.swing.JPanel
	 */
	private javax.swing.JPanel getJContentPane() {
		if (contentPane == null) {
			contentPane = new javax.swing.JPanel();
			contentPane.setName("contentPane");
			contentPane.setLayout(new java.awt.GridLayout(0,2));
//			contentPane.setLayout(new java.awt.GridBagLayout());
			getContentPane().add(getJLabel1(), getJLabel1().getName());
			getContentPane().add(getJTextField1(),
					getJTextField1().getName());
			contentPane.add(getJLabel2(), getJLabel2().getName());
						getContentPane().add(getJTextField2(),
					getJTextField2().getName());
						contentPane.add(getJLabel3(), getJLabel3().getName());
			getContentPane().add(getJTextField3(),
					getJTextField3().getName());
			
			getContentPane().add(getJLabel4(), getJLabel4().getName());
			contentPane.add(getJSlider1(), getJSlider1().getName());
			
			contentPane.add(getJButton1(), getJButton1().getName());
		}
		return contentPane;
	}

	/**
	 * Return the JLabel1 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel1() {
		if (serverHostLabel == null) {
			serverHostLabel = new javax.swing.JLabel();
			serverHostLabel.setName("JLabel1");
			serverHostLabel.setText("Server Host");
		}
		return serverHostLabel;
	}
	
	/**
	 * Return the JTextField1 property value.
	 * @return javax.swing.JTextField
	 */
	private javax.swing.JTextField getJTextField1() {
		if (serverHostTextField == null) {
			serverHostTextField = new javax.swing.JTextField();
			serverHostTextField.setName("JTextField1");
			serverHostTextField.setText("localhost");
		}
		return serverHostTextField;
	}

	/**
	 * Return the JLabel2 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel2() {
		if (serverPortLabel == null) {
			serverPortLabel = new javax.swing.JLabel();
			serverPortLabel.setName("JLabel2");
			serverPortLabel.setText("Server Port");
		}
		return serverPortLabel;
	}

	/**
	 * Return the JTextField2 property value.
	 * @return javax.swing.JTextField
	 */
	private javax.swing.JTextField getJTextField2() {
		if (serverPortTextField == null) {
			serverPortTextField = new javax.swing.JTextField();
			serverPortTextField.setName("JTextField2");
			serverPortTextField.setText("6666");
		}
		return serverPortTextField;
	}

	/**
	 * Return the JLabel3 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel3() {
		if (usernameLabel == null) {
			usernameLabel = new javax.swing.JLabel();
			usernameLabel.setName("JLabel3");
			usernameLabel.setText("Username");
		}
		return usernameLabel;
	}

	/**
	 * Return the JTextField3 property value.
	 * @return javax.swing.JTextField
	 */
	private javax.swing.JTextField getJTextField3() {
		if (usernameTextField == null) {
			usernameTextField = new javax.swing.JTextField();
			usernameTextField.setName("JTextField3");
			usernameTextField.setText("...");
		}
		return usernameTextField;
	}

	/**
	 * Return the JLabel2 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel4() {
		if (sizeLabel == null) {
			sizeLabel = new javax.swing.JLabel();
			sizeLabel.setName("JLabel4");
			sizeLabel.setText("Board size");
		}
		return sizeLabel;
	}
	/**
	 * Return the JSlider1 property value.
	 * @return javax.swing.JSlider
	 */
	private javax.swing.JSlider getJSlider1() {
		if (sizeSlider == null) {
			sizeSlider = new javax.swing.JSlider();
			sizeSlider.setName("JSlider1");
		}
		return sizeSlider;
	}

	/**
	 * Return the JButton1 property value.
	 * @return javax.swing.JButton
	 */
	private javax.swing.JButton getJButton1() {
		if (connectButton == null) {
			connectButton = new javax.swing.JButton();
			connectButton.setName("JButton1");
			connectButton.setText("Connect to Server");
		}
		return connectButton;
	}

	/**
	 * Initialize the class.
	 */
	private void initialize() {

		this.setName("JFrame1");
		this
				.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		this.setBounds(45, 25, 317, 273);
		this.setTitle("Connect to Go? Server");
		this.setContentPane(getJContentPane());

	}
}
