package cs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ClientHandler extends Thread {

	private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
	private Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
	private Socket serverSocket;
	private Map<String, User> activeUsers;
	private Map<String, String> allUsers;
	private Map<String, ChatRoom> chatRooms;
	private boolean loggedIn = false;
	private File historyFile;
	private Server server;

	public ClientHandler(Socket s, Map<String, User> activeUsers, Map<String, String> allUsers,
			Map<String, ChatRoom> chatRooms, Server server) {
		this.serverSocket = s;
		this.activeUsers = activeUsers;
		this.allUsers = allUsers;
		this.chatRooms = chatRooms;
		this.server = server;
	}

	@Override
	public void run() {
		Command command = null;

		try (DataInputStream dis = new DataInputStream(serverSocket.getInputStream());
				DataOutputStream dos = new DataOutputStream(serverSocket.getOutputStream())) {

			while (true) {

				command = gson.fromJson(dis.readUTF(), Command.class);

				String commandName = command.getCommand();
				String argument1 = command.getArgument1();
				String argument2 = command.getArgument2();
				if (commandName.equals("register") && argument1 != null && argument2 != null) {
					registration(command, dis, dos);
				} else if (commandName.equals("login") && argument1 != null && argument2 != null) {
					logging(command, dis, dos);
				} else if (commandName.equals("list-users") && argument1 == null && argument2 == null) {
					listUsers(command, dis, dos);
				} else if (commandName.equals("send") && argument1 != null && argument2 != null) {
					sendMessage(command, dis, dos);
				} else if (commandName.equals("create-room") && argument1 != null && argument2 == null) {
					createRoom(command, dis, dos);
				} else if (commandName.equals("join-room") && argument1 != null && argument2 == null) {
					joinRoom(command, dis, dos);
				} else if (commandName.equals("leave-room") && argument1 != null && argument2 == null) {
					leaveRoom(command, dis, dos);
				} else if (commandName.equals("delete-room") && argument1 != null && argument2 == null) {
					deleteRoom(command, dis, dos);
				} else if (commandName.equals("list-rooms") && argument1 == null && argument2 == null) {
					listRooms(command, dis, dos);
				} else if (commandName.equals("list-users-room") && argument1 != null && argument2 == null) {
					listUsersRooms(command, dis, dos);
				} else if (commandName.equals("send-room") && argument1 != null && argument2 != null) {
					sendMessageToRoom(command, dis, dos);
				} else if (commandName.equals("send-file") && argument1 != null && argument2 != null) {
					sendFile(command, dis, dos);
				} else if (commandName.equals("accept-file") && argument1 != null && argument2 != null) {
					acceptFile(command, dis, dos);
				} else if (commandName.equals("set-download-dir") && argument1 != null && argument2 == null) {
					setDownloadDir(command, dis, dos);
				} else if (commandName.equals("logout") && argument1 == null && argument2 == null) {
					logout(command, dis, dos);
				} else if (commandName.equals("disconnect") && argument1 == null && argument2 == null) {
					disconnect(command, dis, dos);
					break;
				} else {
					writeToClient(dos, "Invalid command. Type list-commands to see available commands.");
				}
			}
		} catch (IOException e) {
			LOGGER.log(Level.CONFIG, e.toString(), e);
			e.printStackTrace();
		} finally {
			try {
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void setDownloadDir(Command command, DataInputStream dis, DataOutputStream dos) {
		String downloadPath = command.getArgument1();
		String senderName = command.getUser().getUsername();
		activeUsers.get(senderName).setDownloadPath(downloadPath);
		writeToClient(dos, "You successfully changed the download path to: " + downloadPath + ".");
	}

	private synchronized void disconnect(Command command, DataInputStream dis, DataOutputStream dos) {
		User sender = command.getUser();
		logOut(sender);
		System.out.println(sender + " left the system.");
		writeToClient(dos, "You successfully disconnect.");

	}

	private synchronized void logout(Command command, DataInputStream dis, DataOutputStream dos) {
		User sender = command.getUser();
		logOut(sender);
		System.out.println(sender + " logged out");
		writeToClient(dos, "You successfully loged out.");

	}

	private synchronized void  logOut(User sender) {
		String senderName = sender.getUsername();
		activeUsers.remove(senderName);
		loggedIn = false;
		for (ChatRoom chatroom : chatRooms.values()) {
			chatroom.removeUser(sender);
		}
		activeUsers.remove(senderName);
		for (User activeUser : activeUsers.values()) {
			writeToClient(activeUser.getOutputStream(), senderName + " has left the system.");
		}
	}

	private void acceptFile(Command command, DataInputStream dis, DataOutputStream dos) {
		String filePath = command.getArgument2();
		String recipient = command.getArgument1();
		String sender = command.getUser().getUsername();

		File cut = new File(filePath);
		File sendToClientFile = new File(cut.getName());

		String sending = "File accepted.";
		writeToClient(activeUsers.get(recipient).getOutputStream(), sending);
		writeToClient(activeUsers.get(sender).getOutputStream(), "Downloading has started.");
		writeToClient(activeUsers.get(sender).getOutputStream(),
				activeUsers.get(sender).getDownloadPath() + File.separatorChar + sendToClientFile);

		byte[] bytes = new byte[8192];
		FileInputStream in = null;
		try {
			in = new FileInputStream(sendToClientFile);
			long length = sendToClientFile.length();
			activeUsers.get(sender).getOutputStream().writeLong(length);
			int count;
			while ((count = in.read(bytes)) > 0) {
				activeUsers.get(sender).getOutputStream().write(bytes, 0, count);
			}
			activeUsers.get(sender).getOutputStream().flush();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private void sendFile(Command command, DataInputStream dis, DataOutputStream dos) {
		String filePath = command.getArgument2();
		String recipient = command.getArgument1();
		String sender = command.getUser().getUsername();
		if (!activeUsers.containsKey(recipient)) {
			writeToClient(dos, recipient + " is not active.");
		} else {
			String sending = sender + " want to send you: " + filePath + "." + "Set download directory first. "
					+ " To accept the file type accept-file>from_who>file_path.";
			writeToClient(activeUsers.get(recipient).getOutputStream(), sending);

			OutputStream out = null;
			byte[] bytes = new byte[8 * 1024];
			int count;
			try {
				long totalLengthOfFile = dis.readLong();
				File cut = new File(filePath);
				File saveOnServerFile = new File(cut.getName());
				out = new FileOutputStream(saveOnServerFile);
				long runningTotal = 0;
				while (runningTotal < totalLengthOfFile && (count = dis.read(bytes, 0,
						(int) Math.min(bytes.length, totalLengthOfFile - runningTotal))) > 0) {
					out.write(bytes, 0, count);
					runningTotal += count;
				}

			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private synchronized void sendMessageToRoom(Command command, DataInputStream dis, DataOutputStream dos) {
		String message = command.getArgument2();
		String roomName = command.getArgument1();
		String sender = command.getUser().getUsername();

		if (!chatRooms.containsKey(roomName)) {
			writeToClient(dos, roomName + " is not active.");
		}
		File room = new File(roomName + ".txt");
		try (FileWriter fileWriter = new FileWriter(room, true);
				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {

			String messageToSend = sender + ": " + roomName + ": " + message;
			for (User recipient : chatRooms.get(roomName).getUsers()) {
				writeToClient(activeUsers.get(recipient.getUsername()).getOutputStream(), messageToSend);
				bufferedWriter.write("\n" + messageToSend);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void listUsersRooms(Command command, DataInputStream dis, DataOutputStream dos) {
		String active = chatRooms.get(command.getArgument1()).getActiveUsers();
		writeToClient(dos, "Active users in " + command.getArgument1() + " are: " + active + ".");
	}

	public String listRooms(Command command, DataInputStream dis, DataOutputStream dos) {
		String activeRooms = chatRooms.keySet().stream().collect(Collectors.joining(", "));

		writeToClient(dos, "Active rooms:  " + activeRooms);
		return ("Active rooms:  " + activeRooms);
	}

	private synchronized void deleteRoom(Command command, DataInputStream dis, DataOutputStream dos) {

		String roomName = command.getArgument1();
		if (!chatRooms.containsKey(roomName)) {
			writeToClient(dos, "Room does not exist or you have not joined it yet!");
		} else if (chatRooms.containsKey(roomName) && !(chatRooms.get(roomName).isAdmin(command.getUser()))) {
			writeToClient(dos, "You are not allowed to delete " + roomName + ".");
		} else {
			Path roomPath = Paths.get(roomName + ".txt");
			chatRooms.remove(roomName);
			try {
				Files.deleteIfExists(roomPath);
			} catch (IOException e) {
				LOGGER.log(Level.CONFIG, e.toString(), e);
				e.printStackTrace();
			}
			System.out.println("Deleted  " + roomName + ".");
			writeToClient(dos, roomName + " deleted" + ".");
		}
	}

	private void leaveRoom(Command command, DataInputStream dis, DataOutputStream dos) {
		String roomName = command.getArgument1();
		if (!chatRooms.containsKey(roomName)) {
			writeToClient(dos, "Room does not exist or you have not joined it yet!");

		} else if (chatRooms.containsKey(roomName) && !(chatRooms.get(roomName).containsUser(command.getUser()))) {
			writeToClient(dos, "You have not joined " + roomName + " yet!");

		} else {
			chatRooms.get(roomName).removeUser(command.getUser());
			System.out.println(command.getUser() + " have left " + roomName + ".");
			writeToClient(dos, "You have left " + roomName + ".");
		}
	}

	private void joinRoom(Command command, DataInputStream dis, DataOutputStream dos) {
		String roomName = command.getArgument1();
		historyFile = new File(roomName + ".txt");
		try (BufferedReader br = new BufferedReader(new FileReader(roomName + ".txt"))) {
			if (!historyFile.exists()) {
				writeToClient(dos, "This room does not exsist!");
				br.close();

			} else if (chatRooms.containsKey(roomName)) {
				chatRooms.get(roomName).addUser(command.getUser());

				System.out.println(command.getUser() + " have joined " + roomName + ".");
				String s = null;

				writeToClient(dos, "You have joined " + roomName + ".");
				br.readLine();
				while ((s = br.readLine()) != null) {
					writeToClient(dos, s);
				}

			} else {
				String s = null;
				s = br.readLine();
				ChatRoom cr = gson.fromJson(s, ChatRoom.class);
				cr.setActiveUsers(new HashSet<User>());
				cr.addUser(command.getUser());
				chatRooms.putIfAbsent(roomName, cr);

				System.out.println(command.getUser() + " have joined " + roomName + ".");
				writeToClient(dos, "You have joined " + roomName + ".");
				while ((s = br.readLine()) != null) {
					writeToClient(dos, s);
				}

			}
		} catch (IOException ex) {
			LOGGER.log(Level.CONFIG, ex.toString(), ex);
			ex.printStackTrace();
		}

	}

	private synchronized void createRoom(Command command, DataInputStream dis, DataOutputStream dos) {
		String roomName = command.getArgument1();
		User sender = command.getUser();
		if (chatRooms.containsKey(roomName)) {
			writeToClient(dos, "This name for chat room is taken.");

		} else {
			historyFile = new File(roomName + ".txt");
			if (!historyFile.exists()) {
				try {
					historyFile.createNewFile();
					initializeRoom(roomName, sender, dis, dos);

				} catch (IOException e) {
					LOGGER.log(Level.CONFIG, e.toString(), e);
					e.printStackTrace();
				}
			}
		}

	}

	private void initializeRoom(String roomName, User sender, DataInputStream dis, DataOutputStream dos) {
		Set<User> usr = new HashSet<User>();
		usr.add(sender);
		ChatRoom chatroom = new ChatRoom(sender, roomName, usr);
		chatRooms.putIfAbsent(roomName, chatroom);

		writeToClient(dos, roomName + " room created.");
		System.out.println(roomName + " room created.");

		String saveChatRoom = gson.toJson(chatroom, ChatRoom.class);

		try (FileWriter fileWriter = new FileWriter(historyFile, true);
				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);) {

			bufferedWriter.write(saveChatRoom);

		} catch (IOException e) {
			LOGGER.log(Level.CONFIG, e.toString(), e);
			e.printStackTrace();
		}

	}

	private void sendMessage(Command command, DataInputStream dis, DataOutputStream dos) {
		String message = "";
		message = command.getArgument2();
		String recipient = command.getArgument1();
		String sender = command.getUser().getUsername();
		if (!activeUsers.containsKey(recipient)) {
			writeToClient(dos, recipient + " is not active.");

		} else {
			writeToClient(activeUsers.get(recipient).getOutputStream(), sender + ": " + message);

		}
	}

	private void listUsers(Command command, DataInputStream dis, DataOutputStream dos) {
		String activeUsers = this.activeUsers.keySet().stream().collect(Collectors.joining("\n"));

		writeToClient(dos, activeUsers);
	}

	private void logging(Command command, DataInputStream dis, DataOutputStream dos) {
		String username = command.getArgument1();
		String password = command.getArgument2();
		try {
			if (loggedIn) {
				dos.writeUTF("You have already logged in.");
			} else if (!allUsers.containsKey(username)) {
				writeToClient(dos, "You do not have registration");
			} else if (allUsers.get(username).equals(password)) {
				writeToClient(dos, "You successfully logged in.");
				User user = command.getUser();
				user.setInputStream(dis);
				user.setOutputStream(dos);
				activeUsers.put(username, user);
				loggedIn = true;
				System.out.println("This user logged in: " + username);
			} else {
				writeToClient(dos, "Wrong username or password. Please try again");
			}

		} catch (IOException e) {
			LOGGER.log(Level.CONFIG, e.toString(), e);
			e.printStackTrace();
		}
	}

	private synchronized void registration(Command command, DataInputStream dis, DataOutputStream dos) {
		try (FileWriter fileWriter = new FileWriter(server.getUserInfo(), true);
				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {

			String username = command.getArgument1();
			String password = command.getArgument2();

			if (allUsers.containsKey(username)) {
				writeToClient(dos, "This username is taken.");

			} else {
				bufferedWriter.write("\n" + username + "#" + password);
				allUsers.putIfAbsent(username, password);
				System.out.println("Done with registrating: " + command.getArgument1());
				System.out.println("Number of register users: " + allUsers.size());
				writeToClient(dos, "You were successfully registred!");

			}
		} catch (IOException e) {
			LOGGER.log(Level.CONFIG, e.toString(), e);
			e.printStackTrace();
		}

	}

	private void writeToClient(DataOutputStream d, String message) {
		try {
			d.writeUTF(message);
		} catch (IOException e) {
			LOGGER.log(Level.CONFIG, e.toString(), e);
			e.printStackTrace();
		}
	}
}
