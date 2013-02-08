package matchmaker;

import java.io.*;
import java.net.*;

public class MatchmakerServletConnector {

	public static final String ID_CHECK = "ic";
	public static final String ID_NEW = "in";
	
	boolean alreadyGotNewId;
	int newIdCache;
	
	HttpURLConnection conn;
	int id;
	URL url;

	public MatchmakerServletConnector(String servletURL, int id) {

		this.id = id;
		try {
			url = new URL(servletURL);
		}catch(Exception e){e.printStackTrace();}	

		alreadyGotNewId = false;
	}
	
	public void setID(int id) {
		this.id = id;
	}
	
	public UserStatus getStatus() throws IOException {
		try {
//		if (id.equals("dev"))
//			return 4;
		
		UserStatus u = new UserStatus();
		
		conn = (HttpURLConnection)url.openConnection();
		conn.setRequestMethod("GET");
		conn.setDoOutput(true);
		conn.setRequestProperty("Matchmaker-Command", ID_CHECK);
		conn.setRequestProperty("Matchmaker-ID", "" + id);
		
		u.stepsDone = conn.getHeaderFieldInt("steps", -1);
		u.db1 = conn.getHeaderField("db1");
		u.db2 = conn.getHeaderField("db2");
		u.commands = conn.getHeaderField("commands");

		if (u.db1 == null) u.db1 = "";
		if (u.db2 == null) u.db2 = "";
		if (u.commands == null) u.commands = "";
			
		InputStream in = conn.getInputStream();
		in.skip(in.available());

		return u;
		} catch(IOException e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	public int generateNewID() throws IOException {

		//Prevents spamming of database with unused id numbers
		if (alreadyGotNewId)
			return newIdCache;
		
		int newId = 0;
		conn = (HttpURLConnection)url.openConnection();
		conn.setRequestMethod("GET");
		conn.setDoOutput(true);
		conn.setRequestProperty("Matchmaker-Command", ID_NEW);
		
		newId = conn.getHeaderFieldInt(ID_NEW, 0);
		InputStream in = conn.getInputStream();
		in.skip(in.available());

		//Cache the result
		alreadyGotNewId = true;
		newIdCache = newId;
		return newId;
	}

	public void doCommand(int cNum) throws IOException {

		conn = (HttpURLConnection)url.openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setRequestProperty("Matchmaker-Command", "step" + cNum);
		conn.setRequestProperty("Matchmaker-ID", "" + id);
			
		InputStream in = conn.getInputStream();
		in.skip(in.available());
	}
	
	public void upload(File f, String role, UploadListener listener) {
		UploadThread u = new UploadThread(f, role, url, "" + id);
		u.addUploadListener(listener);
		System.out.println("Uploading " + f.getName());
		u.start();
	}


	public URL getResults(int i) throws MalformedURLException, IOException {
		conn = (HttpURLConnection)url.openConnection();
		conn.setRequestMethod("GET");
		conn.setDoOutput(true);
		conn.setRequestProperty("Matchmaker-Command", "results");
		conn.setRequestProperty("Matchmaker-ID", "" + id);
		
		URL ret = new URL(conn.getHeaderField("resultUrl"));
		
		InputStream in = conn.getInputStream();
		in.skip(in.available());
		
		return ret;
	}	
}
