package edu.american;

public class Header implements Comparable<Header> {

	public static final String TEXT = "text";
	public static final String NUMERIC = "numeric";
	public static final String BOOLEAN = "boolean";
	
	//Public members are poor practice, use getters and setters, blah blah whatever
	public String name;
	public int dbnum;
	public String type;
	public int colNum;
	
	public Header(String n, String type, int d) {
		name = n.toLowerCase();
		dbnum = d;
		this.type = type.toLowerCase();
		colNum = -1;
	}
	
	public int compareTo(Header other) {
		//ignore case not necessary because name is already
		//lowercase from constructor
		return this.name.compareTo(other.name);
	}
	
	public String getSqlType() {
		if (type.equals(TEXT))
			return "VARCHAR(100)";
		if (type.equals(NUMERIC))
			return "INT";
		if (type.equals(BOOLEAN))
			return "BOOLEAN";
		return "";
	}
	
}
