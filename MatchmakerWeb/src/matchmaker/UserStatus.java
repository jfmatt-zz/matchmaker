package matchmaker;

public class UserStatus {

	String id;
	public int stepsDone;
	public String db1;
	public String db2;
	public String commands;
	
	public UserStatus() {
		stepsDone = 0;
		id = "";
	}
	
	public UserStatus(String i, int s, String d1, String d2, String c) {
		id = i;
		stepsDone = s;
		db1 = d1;
		db2 = d2;
		commands = c;
	}
	
}
