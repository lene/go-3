package net.hyperspacetravel.go3.client.gui;

import javax.swing.*;

import net.hyperspacetravel.go3.ConnectedPlayer;
import net.hyperspacetravel.go3.ConnectionData;
import net.hyperspacetravel.go3.Utility;

import java.io.IOException;
import java.util.ArrayList;

public class ChooseGameDialog extends JDialog {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7199599938553022819L;

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
 * pendingList.ensureIndexIsVisible(index) do not update the list (visibly).
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
				int index = pendingList.getSelectedIndex(); //get selected index
				Utility.debug(""+index);
			    if (index == -1) { index = 0; }	 //no selection, insert at beginning
			    else { index++; }                //add after the selected item
				Utility.debug(""+index);
//			    model.insertElementAt(current, index);
			    model.addElement(current);
			    pendingList.setSelectedIndex(index);
			    pendingList.ensureIndexIsVisible(index);
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
				pendingList.ensureIndexIsVisible(model.getSize());
			}
		}
		return;
		}
		else {
			pendingList.setListData(setGameList());
		}
	}


	/**
	 * @return the tabbedPane
	 */
	private JTabbedPane getTabbedPane() {
		if (tabbedPane == null) {
			tabbedPane = new JTabbedPane();

			tabbedPane.addTab("Pending Games", null, getPendingListContentPane(),
					"The games in the list are currently running on the server. Choose one to watch.");
			if (false) {
				tabbedPane.addTab("Connected Players", null, getPlayersListContentPane(),
						"These players are currently connected to the server. Choose one to propose a game.");
				tabbedPane.addTab("Started Games", null, getGamesListContentPane(),
						"The games in the list are currently running on the server. Choose one to watch.");
				tabbedPane.addTab("New Game", null, getNewGameContentPane(),
						"Enter the size of the board you want to play on in this form.");
			}
		}
		return tabbedPane;
	}

	private JPanel getPendingListContentPane() {
		if (pendingListContentPane == null) {
			pendingListContentPane = new JPanel();
			pendingListContentPane.setName("pendingListContentPane");
			pendingListContentPane.setLayout(
					new javax.swing.BoxLayout(pendingListContentPane,
							javax.swing.BoxLayout.Y_AXIS));

			pendingListContentPane.add(getPendingListLabel(), null);
			pendingListContentPane.add(getPendingList(), null);
			
			pendingListContentPane.add(getConnectButton());
		}
		return pendingListContentPane;
	}

	private JPanel getPlayersListContentPane() {
		if (playersListContentPane == null) {
			playersListContentPane = new JPanel();
			playersListContentPane.setName("playersListContentPane");
			playersListContentPane.setLayout(
					new javax.swing.BoxLayout(playersListContentPane,
							javax.swing.BoxLayout.Y_AXIS));

			playersListContentPane.add(getPlayersListLabel(), null);
//			playersListContentPane.add(getPendingList(), null);
//			
//			playersListContentPane.add(getConnectButton());			
		}
		return playersListContentPane;
	}

	private JPanel getGamesListContentPane() {
		if (gamesListContentPane == null) {
			gamesListContentPane = new JPanel();
			gamesListContentPane.setName("gamesListContentPane");
			gamesListContentPane.setLayout(
					new javax.swing.BoxLayout(gamesListContentPane,
							javax.swing.BoxLayout.Y_AXIS));

			gamesListContentPane.add(getGamesListLabel(), null);
//			gamesListContentPane.add(getPendingList(), null);
//			
//			gamesListContentPane.add(getConnectButton());
		}
		return gamesListContentPane;
	}

	private JPanel getNewGameContentPane() {
		if (newGameContentPane == null) {
			newGameContentPane = new JPanel();
			newGameContentPane.setName("newGameContentPane");
			newGameContentPane.setLayout(
					new javax.swing.BoxLayout(newGameContentPane,
							javax.swing.BoxLayout.Y_AXIS));

			newGameContentPane.add(getNewGameLabel(), null);
//			newGameContentPane.add(getPendingList(), null);
//			
//			newGameContentPane.add(getConnectButton());
		}
		return newGameContentPane;
	}
	/**
	 * @return Returns the gamesListLabel.
	 */
	private JLabel getGamesListLabel() {
		if (gamesListLabel == null) {
			gamesListLabel = new javax.swing.JLabel();
			gamesListLabel.setName("gamesListLabel");
			gamesListLabel.setText("Choose a Game");
			gamesListLabel.setToolTipText("The games in the list are currently " +
					"running on the server. Choose one to watch.");
		}
		return gamesListLabel;
	}

	/**
	 * @return Returns the newGameLabel.
	 */
	private JLabel getNewGameLabel() {
		if (newGameLabel == null) {
			newGameLabel = new javax.swing.JLabel();
			newGameLabel.setName("newGameLabel");
			newGameLabel.setText("Start a new Game");
			newGameLabel.setToolTipText("Enter the size of the board you want " +
					"to play on in this form.");
		}
		return newGameLabel;
	}

	/**
	 * @return Returns the playersListLabel.
	 */
	private JLabel getPlayersListLabel() {
		if (playersListLabel == null) {
			playersListLabel = new javax.swing.JLabel();
			playersListLabel.setName("playersListLabel");
			playersListLabel.setText("Players currently connected");
			playersListLabel.setToolTipText("These players are currently "+
					"connected to the server. Choose one to propose a game.");
		}
		return playersListLabel;
	}

	/**
	 * Return the pendingListLabel property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getPendingListLabel() {
		if (pendingListLabel == null) {
			pendingListLabel = new javax.swing.JLabel();
			pendingListLabel.setName("pendingListLabel");
			pendingListLabel.setText("Choose a Game");
			pendingListLabel.setToolTipText("The games in the list are currently " +
					"running on the server and waiting for a partner. " +
					"Choose one to join.");
		}
		return pendingListLabel;
	}
	
	private JScrollPane getPendingList() {
		if (pendingList == null) {
			model = new DefaultListModel();
			pendingList = new JList(model);

	        pendingList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
	        pendingList.setLayoutOrientation(JList.VERTICAL);
	        pendingList.setVisibleRowCount(-1);

	        pendingList.setListData(setGameList());
	        
	        listScroller = new JScrollPane(pendingList);
	        listScroller.setAlignmentX(LEFT_ALIGNMENT);
	        
//	        new UpdateThread (5000).start();
	    }
		return listScroller;
	}
	
	/**
	 * Return the JButton1 property value.
	 * @return javax.swing.JButton
	 */
	private javax.swing.JButton getConnectButton() {
		if (connectButton == null) {
			connectButton = new javax.swing.JButton();
			connectButton.setName("connectButton");
			connectButton.setText("Connect to Server");
			connectButton.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
						if (e.getActionCommand().equals (connectButton.getText())) {
							int index = pendingList.getSelectedIndex();
							
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
		this.setSize(480, 300);
		this.setTitle("Choose a Game");
		this.setContentPane(getTabbedPane());
	}
	
	private ConnectionData connectionData;
	private ConnectedPlayer player;
	
	private JPanel pendingListContentPane = null;
	private JPanel playersListContentPane = null;
	private JPanel gamesListContentPane = null;
	private JPanel newGameContentPane = null;
	
	private JTabbedPane tabbedPane = null;

	private JLabel pendingListLabel = null;
	DefaultListModel model = null;
	private JList pendingList = null;
	private JScrollPane listScroller;
	
	private JLabel gamesListLabel = null;
	private JLabel newGameLabel = null;
	private JLabel playersListLabel = null;
	
	
	private JButton connectButton = null;

	private boolean stopped = false;
	
	public static void main(String[] args) {
//		new ChooseGameDialog(new ConnectionData()).setVisible(true);
	}

}  //  @jve:decl-index=0:visual-constraint="10,10"
