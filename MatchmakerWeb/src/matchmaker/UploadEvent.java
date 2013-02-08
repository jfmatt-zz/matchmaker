package matchmaker;
import java.util.EventObject;

public class UploadEvent extends EventObject {
	static final long serialVersionUID = 1L;
	
	protected int message;
	protected String fName;
	final static int NO_MESSAGE = -1;
	final static int UPLOAD_FINISHED = 0;
	final static int UPLOAD_INTERRUPTED = 1;
	final static int UPLOAD_ERROR = 2;
	
	public UploadEvent(Object source, String name) {
		super(source);
		fName = name;
		message = NO_MESSAGE;
	}
	
	public UploadEvent(Object source, String name, int m) {
		super(source);
		message = m;
	}
	
	public int getMessage() {
		return message;
	}
		
	public String getFileName() {
		return fName;
	}
}
