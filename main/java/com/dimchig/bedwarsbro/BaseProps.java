package com.dimchig.bedwarsbro;

import java.util.ArrayList;
import java.util.Random;

import com.dimchig.bedwarsbro.serializer.MySerializer;

public class BaseProps {
	private String mod_last_version; 
	private String mod_update_link;
	private String discord_link;
	private String patch_author;
	private String mod_author;
	private String mod_autor_prefix;
	private ArrayList<String> banned_users;
	private ArrayList<String> admin_users;
	
	public class MyMessage {
		public String trigger;
		public ArrayList<String> messages;
		
		public MyMessage(String trigger,  ArrayList<String> messages) {
			this.trigger = trigger.trim();
			this.messages = messages;
		}
		
		public String getRndMessage() {
			if (this.messages.size() == 0) return null;
			if (this.messages.size() == 1) return messages.get(0);
			return this.messages.get(new Random().nextInt(messages.size()));
		}
	}	
	public ArrayList<MyMessage> my_messages = new ArrayList<MyMessage>();
	
	public BaseProps() {
		this.mod_last_version = null; 
		this.mod_update_link = null; 
		this.discord_link = null; 
		this.patch_author = "Supergrafiti";
		this.mod_author = "DimCh1g";
		this.mod_autor_prefix = "&c&l[&6&lС&e&lо&a&lз&b&lд&d&lа&c&lт&6&lе&e&lл&a&lь&c&l]&r"; 
		this.banned_users = new ArrayList<String>(); 
		this.admin_users = new ArrayList<String>(); 
	}
	
	public String getModLastVersion() { return this.mod_last_version; }
	public String getModUpdateLink() { return this.mod_update_link; }
	public String getDiscordLink() { return this.discord_link; }
	public String getPatchAuthor() { return this.patch_author; }
	public String getModAuthor() { return this.mod_author; }
	public boolean isPatchAuthor(String playerName) {
		return playerName != null && patch_author != null && patch_author.equalsIgnoreCase(playerName);
	}
	public boolean isModAuthor(String playerName) {
		return playerName != null && mod_author != null && mod_author.equalsIgnoreCase(playerName);
	}
	public String getModAuthorPrefix() { return this.mod_autor_prefix; }
	public ArrayList<String> getModBannedUsers() { return this.banned_users; }
	public boolean isUserBanned(String player_name) {
		for (String n: this.banned_users) if (n.equals(player_name)) return true;
		return false;
	}
	public ArrayList<String> getModAdminUsers() { return this.admin_users; }
	public boolean isUserAdmin(String player_name) {
		for (String n: this.admin_users) if (n.equals(player_name)) return true;
		return false;
	}
	
	public void printProps() {
		ChatSender.addText("&5=====&dBASE PROPS&5=====");
		ChatSender.addText(" &5mod_last_version &5▸ &d" + mod_last_version);
		ChatSender.addText(" &5yt &5▸ &d" + mod_update_link);
		ChatSender.addText(" &5discord_link &5▸ &d" + discord_link);
		ChatSender.addText(" &5patch_author &5▸ &d" + patch_author);
		ChatSender.addText(" &5mod_author &5▸ &d" + mod_author);
		ChatSender.addText(" &5autor_prefix &5▸ &d" + mod_autor_prefix);
		ChatSender.addText(" &5admin users:");
		for (String a: admin_users) ChatSender.addText(" &5• &d" + a);
		ChatSender.addText(" &5banned users:");
		for (String b: banned_users) ChatSender.addText(" &5• &d" + b);
		ChatSender.addText("&5======================");
	}
	
	public void printMessages() {
		ChatSender.addText("&2=====&aMESSAGES&2=====");
		for (MyMessage m: my_messages) {
			ChatSender.addText(" &a" + m.trigger);
			for (String a: m.messages) {
				ChatSender.addText("  &2• " + a);
			}
		}
		ChatSender.addText("&2==================");
	}
	
	public void readMessages() {
		ClientScheduler.runAsync(new Runnable() {
		    @Override
		    public void run() {
		    	try {
		    		String data_text = Main.mySerializer.readMessages();
		    		if (data_text == null) return;
		    		String separator_thirdly = ";=BRO2=;";
		    		if (data_text.length() < 10) return;
		    		String[] split = data_text.split(MySerializer.separator);
		    		if (split.length == 0) return;
		    		
		    		final ArrayList<MyMessage> parsedMessages = new ArrayList<MyMessage>();
		    		
		    		for (String line: split) {
		    			if (line.length() < 3) continue;

		    			String[] split2 = line.split(MySerializer.separator_secondary);
		    			if (split2.length < 2) continue;
		    			String trigger = split2[0].trim();
		    			ArrayList<String> messages = new ArrayList<String>();
		    			for (String s2: split2[1].trim().split(separator_thirdly)) {
		    				s2 = s2.trim();
		    				if (s2.length() < 1) continue;
		    				messages.add(s2);
		    			}
		    			
		    			MyMessage msg = new MyMessage(trigger, messages);		    			
		    			parsedMessages.add(msg);
		    		}		    		    		    		
		    		ClientScheduler.runOnClientThread(new Runnable() {
		    			@Override
		    			public void run() {
		    				my_messages = parsedMessages;
		    			}
		    		});
				} catch (Exception e) {
					e.printStackTrace();					
				} 
		    }
		});
	}
	
	public void readProps() {
		ClientScheduler.runAsync(new Runnable() {
		    @Override
		    public void run() {
		    	try {
		    		String data_text = Main.mySerializer.readProps();
		    		
		    		if (data_text == null) return;
		    		String[] split = data_text.split(MySerializer.separator);
					if (split.length < 7) return;
					boolean hasSeparateAuthors = split.length >= MySerializer.PROPS_FIELDS_WITH_SEPARATE_AUTHORS;
					
					final String parsedLastVersion = split[0].trim();
					final String parsedUpdateLink = split[1].replace("xyzqwerty", ".").trim();
					final String parsedDiscordLink = split[2].replace("xyzqwerty", ".").trim();
					final String parsedPatchAuthor = split[3].trim();
					final String parsedModAuthor = hasSeparateAuthors ? split[4].trim() : "DimCh1g";
					int prefixIndex = hasSeparateAuthors ? 5 : 4;
					int adminsIndex = hasSeparateAuthors ? 6 : 5;
					int bannedIndex = hasSeparateAuthors ? 7 : 6;
					final String parsedAuthorPrefix = split[prefixIndex].replace("x.y.z", "&").trim();
					final ArrayList<String> parsedAdmins = new ArrayList<String>();
					
					String[] mod_admin_users = split[adminsIndex].trim().split(Main.mySerializer.separator_secondary);	
		    		for (String l: mod_admin_users) {
		    			if (l.length() <= 1) continue;
		    			parsedAdmins.add(l.trim());
		    		}
		    		final ArrayList<String> parsedBannedUsers = new ArrayList<String>();
		    		
					String[] mod_banned_users = split[bannedIndex].trim().split(Main.mySerializer.separator_secondary);	
		    		for (String l: mod_banned_users) {
		    			if (l.length() <= 1) continue;
		    			parsedBannedUsers.add(l.trim());
		    		}
		    		ClientScheduler.runOnClientThread(new Runnable() {
		    			@Override
		    			public void run() {
		    				mod_last_version = parsedLastVersion;
		    				mod_update_link = parsedUpdateLink;
		    				discord_link = parsedDiscordLink;
							patch_author = parsedPatchAuthor;
							mod_author = parsedModAuthor;
		    				mod_autor_prefix = parsedAuthorPrefix;
		    				admin_users = parsedAdmins;
		    				banned_users = parsedBannedUsers;
		    				Main.updateAllBooleans();
		    			}
		    		});
		    		
				} catch (Exception e) {
					//ChatSender.addText("&cEXCEPTION TREAD" + e);
					//e.printStackTrace();
					return;
					
				} 
		    }
		});
	}
}
