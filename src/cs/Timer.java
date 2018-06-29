package cs;

import java.io.IOException;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Timer {

	public static void main(String[] args) throws MalformedURLException, IOException {
		String clientargs[] = { "localhost", "8888" };
		for (int i = 0; i < 32; i++) {
			Thread t = new X(i, clientargs);
			t.start();
		}
	}
}

class X extends Thread {
	public static final Double NANO_TO_MILLIS = 1000000.0;
	private int x;
	private String[] clientargs;
	public X(int s, String[] ss) {
		x = s;
		clientargs = ss;
	}

	@Override
	public void run() {
		try {
			String commands = "connect\r\nregister>g" + x + ">b\r\nlogin>g" + x
					+ ">b\r\njoin-room>testt\r\nsend>a>h\r\nsend>a>h\r\nsend>a>h\r\ndisconnect\r\n\r\n";
			InputStream stream = new ByteArrayInputStream(commands.getBytes());
			Client client = new Client();
			long nanoStart = System.nanoTime();
			client.start(clientargs, stream);
			long nanoEnd = System.nanoTime();
			long nanoTime = (nanoEnd - nanoStart);
			String nanoFormatted = String.format("%,.3f", nanoTime / NANO_TO_MILLIS);
			System.out.println("Milliseconds for " + x + " using nanoTime(): " + nanoFormatted);
		} catch (Exception v) {
			System.out.println(v);
		}
	}
}
