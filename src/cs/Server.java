package cs;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Server {

	private  File userInformation = new File("userInfo.txt");
	private  Map<String, User> activeUsers = new ConcurrentHashMap<String, User>();
	private  Map<String, String> allUsers = new ConcurrentHashMap<String, String>();
	private  Map<String, ChatRoom> chatRooms = new ConcurrentHashMap<String, ChatRoom>();
	private  ServerSocket serverSocket;
	private  int port = 5051;

	public static void main(String[] args) {
		Server server = new Server();
		server.start(args);
	}
	public void start(String[] args) {
		
		if(args.length>0) {
			port = Integer.parseInt(args[0]);
		}
		try {
			serverSocket = new ServerSocket(port);
		} catch (IOException e) {
			e.printStackTrace();
		}

		loadUsers();

		clientAdding();
	}

	private  void clientAdding() {
		while (true) {
			try {
				Socket clientSocket = serverSocket.accept();
				System.out.println("New client request received : " + clientSocket);

				ClientHandler clientHandler = new ClientHandler(clientSocket, activeUsers, allUsers, chatRooms, this);
				Thread clientThread = new Thread(clientHandler);
				System.out.println("Adding this client to active client list");

				clientThread.start();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private  void loadUsers() {
		if (!userInformation.exists()) {
			try {
				userInformation.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try (Stream<String> stream = Files.lines(Paths.get(userInformation.toURI()))) {
			stream.forEach(x -> {
				String y[] = x.split("#");
				allUsers.put(y[0], y[1]);
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public File getUserInfo() {
		return userInformation;
	}
}
