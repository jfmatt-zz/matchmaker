package edu.american;

public class SelectExpression {
	
	private Header header;
	private String operator;
	private String value; 
	
	public SelectExpression() {
		header = null;
		operator = null;
		value = null;
	}
	
	public SelectExpression(Header h, String o, String v) {
		header = h;
		operator = o;
		value = v;
	}
	
	//Tests whether or not the types of the header, operator, and value all match.
	public boolean isValid() {

		if (header.equals(null) || operator.equals(null) || value.equals(null))
			return false;
		
		if (header.type.equals(Header.TEXT))
			return (operator.equals("=") || operator.equals("!="));
		else if (header.type.equals(Header.NUMERIC))
			try {
				Double.parseDouble(value);
				return (operator.equals("=") || operator.equals("!=") 
						|| operator.equals(">") || operator.equals("<") 
						|| operator.equals(">=") || operator.equals("<="));
			} catch(NumberFormatException e) {return false;}
		else if (header.type.equals(Header.BOOLEAN))
			return ((operator.equals("=") || operator.equals("!=")) && (value.equals("1") || value.equals("0")));
		else
			return false;
	}
	
	public String toString() {
		return header.name + " " + operator + " '" + value + "'";
	}

	//Getters and setters
	public Header getHeader() { return header;}
	public void setHeader(Header h) { header = h; }
	public String getOperator() { return operator; }
	public void setOperator(String o) {operator = o; }
	public String getValue() { return value; }
	public void setValue(String v) { value = v; }
}
