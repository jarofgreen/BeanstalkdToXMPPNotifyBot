/*
 */

package uk.co.jarofgreen.BeanstalkdToXMPPNotifyBot;

import org.jivesoftware.smack.*;
import org.jivesoftware.smackx.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;

import com.trendrr.beanstalk.*;
import java.util.Collection;

import java.io.*;
import java.util.*;
/**
 *
 * @license BSD License
 * @author James Baster
 */
public class Main implements MessageListener {

	protected  XMPPConnection xmppConnection;
	protected  ChatManager xmppConnectionChatManager;
	protected  Roster xmppConnectionRoster;

	protected  BeanstalkClient beanstalkClient;

	protected  String xmppServer;
	protected  int xmppPort;
	protected  String xmppServiceName;
	protected  String xmppUserName;
	protected  String xmppPassword;
	protected  String xmppResource;
	protected  String beanstalkServer;
	protected  int beanstalkPort;
	protected  String beanstalkQue;
	
	
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {

		if (args.length < 1) {
			throw new Exception("No Propertiy File Passed?");
		}

		Main m = new Main();
		m.loadFromPropertiesFile(args[0]);
		m.run();
    }

	public void loadFromPropertiesFile(String fileName) {

		Properties props = new Properties();
		try {
			props.load(new FileInputStream(fileName));
			this.xmppServer = props.getProperty("xmppServer");
			this.xmppPort = Integer.parseInt(props.getProperty("xmppPort"));
			this.xmppServiceName = props.getProperty("xmppServiceName");
			this.xmppUserName = props.getProperty("xmppUserName");
			this.xmppPassword = props.getProperty("xmppPassword");
			this.xmppResource = props.getProperty("xmppResource");
			this.beanstalkServer = props.getProperty("beanstalkServer");
			this.beanstalkPort = Integer.parseInt(props.getProperty("beanstalkPort"));
			this.beanstalkQue = props.getProperty("beanstalkQue");
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public void run() {
		ConnectionConfiguration config = new ConnectionConfiguration(this.xmppServer, this.xmppPort, this.xmppServiceName);

		try {
			this.xmppConnection =  new XMPPConnection(config);
			this.xmppConnection.connect();
			this.xmppConnection.login(this.xmppUserName, this.xmppPassword, this.xmppResource);

			this.xmppConnectionChatManager = this.xmppConnection.getChatManager();
			
			this.beanstalkClient = new BeanstalkClient(this.beanstalkServer, this.beanstalkPort, this.beanstalkQue);

			// Found some stack overflow pages that claimed a delay was neccisary to allow the Roster to get data.
			Thread.sleep(10000);

			this.xmppConnectionRoster = this.xmppConnection.getRoster();
			this.xmppConnectionRoster.setSubscriptionMode(Roster.SubscriptionMode.accept_all);

			System.out.println("Ready");

			while(true) {

				BeanstalkJob job = this.beanstalkClient.reserve(60);

				if (job != null) {

					String[] data = new String(job.getData()).split(" ", 2);
					String email = data[0];
					String message = data[1];

					Collection<RosterEntry> entries = this.xmppConnectionRoster.getEntries();
					Boolean sendToUser = false;
					for (RosterEntry entry : entries) {
						if (entry.getUser().compareTo(email) == 0) {
							Presence presence = this.xmppConnectionRoster.getPresence(entry.getUser());
							if(presence.isAvailable() || presence.isAway()){
								sendToUser = true;
							}
						}
					}

					if (sendToUser) {
						System.out.println("Sending to "+email+": "+message);
						Chat newChat = this.xmppConnectionChatManager.createChat(email,this);
						newChat.sendMessage(message);
					}

					this.beanstalkClient.deleteJob(job);

				}
				
			}

		} catch (Exception e) {
			System.out.println("ERROR");
			System.out.println(e.getMessage());
		}
	
	}

	public void processMessage(Chat chat, Message message) {
		//if(message.getType() == Message.Type.chat) System.out.println(chat.getParticipant() + " says: " + message.getBody());
    }


}
