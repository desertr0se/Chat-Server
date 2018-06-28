package cs;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import com.google.gson.annotations.Expose;

public class User extends Thread {

	@Expose
	private String username;
	@Expose
	private String downloadPath;

	private DataInputStream inputStream;
	private DataOutputStream outputStream;

	public User(String un, DataInputStream dis, DataOutputStream dos) {
		username = un;
		inputStream = dis;
		outputStream = dos;
        downloadPath = "";
	}
	
	@Override
	public String toString() {
		return username;
	}

	public void setDownloadPath(String downloadPath) {
		this.downloadPath = downloadPath;
	}

	public String getDownloadPath() {
		return this.downloadPath;
	}
	
	public DataInputStream getInputStream() {
		return inputStream;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((inputStream == null) ? 0 : inputStream.hashCode());
		result = prime * result + ((outputStream == null) ? 0 : outputStream.hashCode());
		result = prime * result + ((username == null) ? 0 : username.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		User other = (User) obj;
		if (username == null) {
			if (other.username != null)
				return false;
		} else if (!username.equals(other.username))
			return false;
		return true;
	}

	public DataOutputStream getOutputStream() {
		return outputStream;
	}

	public void setInputStream(DataInputStream dis) {
		this.inputStream = dis;
	}

	public void setOutputStream(DataOutputStream dos) {
		this.outputStream = dos;
	}

	public String getUsername() {
		return username;
	}

	public void setUserName(String username) {
		this.username = username;
		
	}
}
