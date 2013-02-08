package matchmaker;

import java.io.*;
import java.net.*;

import javax.swing.SwingUtilities;

public class UploadThread extends Thread {

	public static final int BUFFER_SIZE = 8192;
	public static final int MAX_CHUNK_SIZE = 1000 * BUFFER_SIZE; //~8MB

	public static final String FILE_NAME_HEADER = "Transfer-Name";
	public static final String ID_HEADER = "Transfer-ID";
	public static final String CHUNK_HEADER ="Transfer-Chunk";
	public static final String CHUNK_COUNT_HEADER = "Transfer-Chunk-Count";
	public static final String ROLE_HEADER = "Transfer-Role";
	public enum Role {DB1, DB2, COMMANDS};
	
	FileInputStream in;
	OutputStream out;
	public String id;
	public String fName;
	public String role;
	URL url;
	int nChunks;
	byte[] buf;
	long lastChunkSize;
	UploadListener listener;
	
	public UploadThread(File f, String r, URL u, String id) {

		//Instantiate members
		this.id = id;
		this.role = r;
		this.fName = f.getName();
		try {
			in = new FileInputStream(f);
			this.url = u;
		} catch(Exception e) {e.printStackTrace(); }
		buf = new byte[BUFFER_SIZE];
		listener = null;
		

		//Figure out how many chunks we're going to need
		//If it's too big to be an int, we have issues
		long fileSize = f.length();
		nChunks = (int)(fileSize / MAX_CHUNK_SIZE);
		if(fileSize % MAX_CHUNK_SIZE > 0) {
			nChunks++;
		}

		//Figure out how big the ragged end is going to be
		lastChunkSize = fileSize - ((nChunks - 1) * MAX_CHUNK_SIZE);
		System.out.println(fName + fileSize + " " + nChunks + " chunks");
	}

	public void addUploadListener(UploadListener l) {
		if (l != null)
			listener = l;
	}
	
	public void processEvent(UploadEvent e) {
		if (listener == null)
			return;
		final UploadEvent eFinal = e;
		try {
		SwingUtilities.invokeAndWait(new Runnable() {
			public void run() {
				listener.uploadEventPerformed(eFinal);
			}
		});} catch(Exception ex) {ex.printStackTrace();}
	}
	
	public void run() {
		try {
			HttpURLConnection conn;
			
			for (int i = 0; i < nChunks; i++) {
				//Decide how big this chunk will be, depending on
				//whether or not it's the last one
				int thisChunkSize = (int) ((i == nChunks - 1) ?
						lastChunkSize : MAX_CHUNK_SIZE);
				//Set up connection - a new one for each chunk
				conn = (HttpURLConnection)url.openConnection();
				if (conn != null)
					System.out.println(conn);
				conn.setDoInput(true);
				conn.setDoOutput(true);
				conn.setUseCaches(false);

				//HTTP headers, so the servlet knows what it's in for
				conn.setRequestMethod("PUT");
				conn.setRequestProperty("Content-Type", "multipart;application/octet-stream");
				conn.setRequestProperty("Content-Length", "" + thisChunkSize);
				conn.setRequestProperty(FILE_NAME_HEADER, fName);
				conn.setRequestProperty(ID_HEADER, id);
				conn.setRequestProperty(CHUNK_HEADER, "" + i);
				conn.setRequestProperty(CHUNK_COUNT_HEADER, "" + nChunks);
				conn.setRequestProperty(ROLE_HEADER, role);
				
				//Prepare chunk's worth of data
				OutputStream out = conn.getOutputStream();
				int bytesRead = 0;
				while (bytesRead < thisChunkSize) {
					int read = in.read(buf);
					if (read == -1)
						break;
					bytesRead += read;
					out.write(buf, 0, read);
				}

				out.flush();
				
				//Disregard response body
				if (conn.getResponseCode() != 200) {
					processEvent(new UploadEvent(this, fName, UploadEvent.UPLOAD_ERROR));
					break;
				}
				InputStream response = conn.getInputStream();
				response.skip(response.available());
				
				//Clean up and send
				out.close();
				conn.disconnect();
			}//End for : chunks
		} catch(Exception e) {e.printStackTrace();}
		
		//Let applet know we're done
		processEvent(new UploadEvent(this, fName, UploadEvent.UPLOAD_FINISHED));
		
	}//End run()
	
}
