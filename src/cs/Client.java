package cs;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.Socket;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Client {

	private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
	public String SERVER_HOSTNAME = "localhost";
	public int SERVER_PORT = 5051;
	private boolean disconnected;
	private Socket serverSocket;
	private DataInputStream clientDataInputStream;
	private DataOutputStream clientDataOutputStream;
	private User user;
	private Command command;

	public static void main(String args[]) throws IOException {
		Client client = new Client();
		client.start(args, System.in);
	}

	public void start(String args[], InputStream inputStream) throws IOException {

		if (args.length == 2) {
			SERVER_HOSTNAME = args[0];
			SERVER_PORT = Integer.parseInt(args[1]);
		}

		
		BufferedReader scn = new BufferedReader(new InputStreamReader(inputStream , "UTF-8"));

		connectToServer(scn);

		// establish the connection
		try {
			serverSocket = new Socket(SERVER_HOSTNAME, SERVER_PORT);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("You are now connected.");

		try {
			// obtaining input and out streams
			clientDataInputStream = new DataInputStream(serverSocket.getInputStream());
			clientDataOutputStream = new DataOutputStream(serverSocket.getOutputStream());

			loginOrRegisterClient(scn);

			System.out.println("To see available commands type list-commands");

			Thread rensponseThread = obtainingResponseFromServer();
			rensponseThread.start();

			sendInstructionsToServer(scn);
			rensponseThread.interrupt();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				clientDataInputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				clientDataOutputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void loginOrRegisterClient(BufferedReader scn) throws IOException {
		while (true) {
			System.out.println("You can register or login. For registration type: register>username>password."
					+ " For login type login>username>password, with correct username and possward.");

			String inputInstructions = scn.readLine();
			String instructions[] = inputInstructions.split(">");
			if (instructions.length < 3) {
				System.out.println("Invalid command or arguments. Please try again");
			} else {
				user = new User(instructions[1], clientDataInputStream, clientDataOutputStream);
				command = new Command(user, instructions[0], instructions[1], instructions[2]);
				Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
				String string = gson.toJson(command, Command.class);
				writeToServer(clientDataOutputStream, string);
				try {
					String message = clientDataInputStream.readUTF();
					System.out.println(message);
					if (message.equals("You successfully logged in.")) {
						disconnected = false;
						break;
					}
				} catch (IOException e) {
					LOGGER.log(Level.CONFIG, e.toString(), e);
					e.printStackTrace();
				}
			}
		}
	}

	private Thread obtainingResponseFromServer() {
		Thread responseThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						if(clientDataInputStream.available()<=0)continue;
						String message = clientDataInputStream.readUTF();
						System.out.println(message);
						if (message.equals("Downloading has started.")) {

							OutputStream out = null;
							byte[] bytes = new byte[8 * 1024];
							int count;
							try {
								String filePathDir = clientDataInputStream.readUTF();
								System.out.println(filePathDir);
								long totalLengthOfFile = clientDataInputStream.readLong();
								File saveOnClientFile = new File(filePathDir);
								out = new FileOutputStream(saveOnClientFile);
								long runningTotal = 0;
								while (runningTotal < totalLengthOfFile && (count = clientDataInputStream.read(bytes, 0,
										(int) Math.min(bytes.length, totalLengthOfFile - runningTotal))) > 0) {
									out.write(bytes, 0, count);
									runningTotal += count;
								}
								System.out.println("Download finished.");

							} catch (IOException ex) {
								ex.printStackTrace();
							} finally {
								try {
									out.close();
								} catch (IOException e) {
									e.printStackTrace();
									return;
								}
							}

						}
						if (message.equals("You successfully disconnect.")) {
							disconnected = true;
							System.out.println("Click /Enter/ to close the program.");
							break;

						}
					} catch (IOException e) {
						LOGGER.log(Level.CONFIG, e.toString(), e);
						e.printStackTrace();
						return;
					}
				}
			}
		});
		return responseThread;
	}

	private void sendInstructionsToServer(BufferedReader scn) {
		String inputInstructions;
		try {
			while (!disconnected) {
				if(!scn.ready())continue;
				inputInstructions = scn.readLine();
				if(inputInstructions==null)continue;
				if (inputInstructions.equals("list-commands")) {
					System.out.println("- disconnect\n" + "- list-users\n" + "- send>username>message\n"
							+ "- send-file>username>file_location\n" + "- accept-file>username>file_location\n"
							+ "- set-download-dir>download_location\n" + "- create-room>room_name\n"
							+ "- delete-room>room_name\n" + "- join-room>room_name\n" + "- leave-room>room_name\n"
							+ "- list-rooms\n" + "- list-users>room\n" + "- list-users-room>room_name\n"
							+ "- send-room>room_name>message\n" + "- logout\n");
				} else {

					sendCommandToServer(inputInstructions);

				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendCommandToServer(String inputInstructions) throws FileNotFoundException, IOException {
		String instructions[] = inputInstructions.split(">");
		if (instructions.length > 3 || instructions.length < 1) {
			System.out.println("Invalid number of arguments.");
		} else if (instructions.length == 1) {
			command = new Command(user, instructions[0], null, null);
		} else if (instructions.length == 2) {
			command = new Command(user, instructions[0], instructions[1], null);
		} else {
			if(instructions[0].equals("login")) {
				user.setUserName(instructions[1]);
			}
			command = new Command(user, instructions[0], instructions[1], instructions[2]);
		}
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
		String string = gson.toJson(command, Command.class);
		writeToServer(clientDataOutputStream, string);
		if (instructions[0].equals("send-file")) {
			File file = new File(instructions[2]);
			// Get the size of the file
			byte[] bytes = new byte[8192];
			FileInputStream in = new FileInputStream(file);
			long length = file.length();
			clientDataOutputStream.writeLong(length);
			int count;
			while ((count = in.read(bytes)) > 0) {
				clientDataOutputStream.write(bytes, 0, count);
			}
			clientDataOutputStream.flush();
			in.close();
		}
	}

	private void connectToServer(BufferedReader scn) throws IOException {
		String connection;
		do {
			System.out.println("You are curently not connected. To connect to the servet type connect.");
			connection = scn.readLine();
		} while (!connection.equals("connect"));
	}

	private void writeToServer(DataOutputStream dos, String string) {
		try {
			dos.writeUTF(string);
		} catch (IOException e) {
			LOGGER.log(Level.CONFIG, e.toString(), e);
			e.printStackTrace();
		}
	}
}
