
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;

public class ChooseGameDialog extends JDialog {

	class GameData {
		private String name;
		private int size;
		
		GameData(String inputLine) throws NumberFormatException {
			name = Utility.getArg (inputLine, 1);
			size = Integer.parseInt (Utility.getArg (inputLine, 2));
		}
		public String toString() { 
			return ""+name+"("+size+"x"+size+"x"+size+")"; }
		public String getName() { return name; }
		public int getSize() { return size;	}
	}
	
	class UpdateThread extends Thread {
		private int delay;
		public UpdateThread(int d) { delay = d; }
		public void run() {
			while (!stopped) {
				try { sleep (delay); } catch (InterruptedException e) { return; }
				updateGameList();
			}
		}
	}
	
	public ChooseGameDialog(ConnectedPlayer p, ConnectionData c) {
		super ();
		this.connectionData = c;
		this.player = p;
		
		initialize();
	}
	

	private GameData[] gameData = {};
	
	ArrayList<GameData> getGames() {
		player.out.println("game list");
		ArrayList<GameData> games = new ArrayList<GameData>();
		
		try {
			String input = "";
			while (!input.startsWith("game list")) {
				input = player.in.readLine();
				if (!input.startsWith("game list")) 
					games.add(new GameData(input));
			}
		} 
		catch (IOException e) { Utility.debug("IOException!"); }		//	TODO
		catch (NumberFormatException e) { Utility.debug("NumberFormatException!"); }
	
		return games;
	}
	
	GameData[] setGameList() {
		gameData = getGames().toArray(gameData);
		return gameData;		
	}
	
	void updateGameList () {
/* the sophisticated way to update the game list does not work.
 * model.addElement() / model.remove() and subsequent 
 * gameList.ensureIndexIsVisible(index) do not update the list (visibly).
 * thus, i have to replace the whole list. that works, at least, but has the
 * downside that the current selection gets lost. 
 * alternative would be not to update at all. choose your poison.			  */
		
		ArrayList<GameData> tempData = getGames();
		if (tempData.equals(gameData)) return;				//	nothing changed
		
		if (false) {
		//	first check the current game list, whether there are games not yet in the ListModel
		for (int i = 0; i < tempData.size(); i++) {
			GameData current = tempData.get(i);
			Utility.debug(current.toString());
			if (!model.contains(current)) {
				int index = gameList.getSelectedIndex(); //get selected index
				Utility.debug(""+index);
			    if (index == -1) { index = 0; }	 //no selection, insert at beginning
			    else { index++; }                //add after the selected item
				Utility.debug(""+index);
//			    model.insertElementAt(current, index);
			    model.addElement(current);
			    gameList.setSelectedIndex(index);
			    gameList.ensureIndexIsVisible(index);
			}
		}
		
		//	now check whether there are old data in the ListModel, which are not in the game list any more
		for (int i = 0; i < model.getSize(); i++) {
			GameData current = (GameData)model.getElementAt(i);
			boolean found = false;
			for (int j = 0; j < tempData.size(); j++) {
				if (current.equals(tempData.get(j))) {
					Utility.debug(current.toString());
					found = true; break;
				}
			}
			if (!found) {
				model.remove(i);
				gameList.ensureIndexIsVisible(model.getSize());
			}
		}
		return;
		}
		else {
			gameList.setListData(setGameList());
		}
	}

	/**
	 * Return the JFrameContentPane property value.
	 * @return javax.swing.JPanel
	 */
	private JPanel getJContentPane() {
		if (contentPane == null) {
			contentPane = new JPanel();
			contentPane.setName("contentPane");
			contentPane.setLayout(
					new javax.swing.BoxLayout(contentPane,
							javax.swing.BoxLayout.Y_AXIS));

			contentPane.add(getJLabel1(), null);
			contentPane.add(getJList1(), null);
			
			contentPane.add(getJButton1());
		}
		return contentPane;
	}

	/**
	 * Return the JLabel1 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel1() {
		if (gameListLabel == null) {
			gameListLabel = new javax.swing.JLabel();
			gameListLabel.setName("gameListLabel");
			gameListLabel.setText("Choose a Game");
			gameListLabel.setToolTipText("The games in the list are currently " +
					"running on the server and waiting for a partner. " +
					"Choose one to join.");
		}
		return gameListLabel;
	}
	
	private JScrollPane getJList1() {
		if (gameList == null) {
			model = new DefaultListModel();
			gameList = new JList(model);

	        gameList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
	        gameList.setLayoutOrientation(JList.VERTICAL);
	        gameList.setVisibleRowCount(-1);

	        gameList.setListData(setGameList());
	        
	        listScroller = new JScrollPane(gameList);
	        listScroller.setAlignmentX(LEFT_ALIGNMENT);
	        
//	        new UpdateThread (5000).start();
	    }
		return listScroller;
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
			connectButton.addActionListener(
					new java.awt.event.ActionListener() {
						public void actionPerformed(java.awt.event.ActionEvent e) {
							if (e.getActionCommand().equals (connectButton.getText())) {
								int index = gameList.getSelectedIndex();
								
								if (index >= 0 && index < gameData.length) {
									Utility.debug(index+" "+gameData[index]);
									connectionData.setGame(gameData[index].getName());
									connectionData.setBoardSize(gameData[index].getSize());
									
									stopped = true;
									setVisible (false);
								}
							}
						}
					});
		}
		return connectButton;
	}

	/**
	 * This method initializes this
	 * 
	 */
	private void initialize() {
        this.setName("Game Chooser Dialog");
		this.setForeground(java.awt.SystemColor.textHighlight);
		this.setModal(true);
		this.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(320, 240);
		this.setTitle("Choose a Game");
		this.setContentPane(getJContentPane());
	}
	
	private ConnectionData connectionData;
	private ConnectedPlayer player;
	
	private JPanel contentPane = null;

	private JLabel gameListLabel = null;
	DefaultListModel model = null;
	private JList gameList = null;
	private JScrollPane listScroller;
	
	private JButton connectButton = null;

	private boolean stopped = false;
	
	public static void main(String[] args) {
//		new ChooseGameDialog(new ConnectionData()).setVisible(true);
	}

}  //  @jve:decl-index=0:visual-constraint="10,10"
