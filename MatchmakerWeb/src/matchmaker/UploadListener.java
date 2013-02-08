package matchmaker;

import java.util.EventListener;

public interface UploadListener extends EventListener {
	
	public void uploadEventPerformed(UploadEvent e);
	
}
