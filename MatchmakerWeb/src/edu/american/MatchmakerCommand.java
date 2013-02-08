package edu.american;

import java.util.ArrayList;

public class MatchmakerCommand {

	public static String[] VALID_FUNCTIONS = {"exact","within","first","last","nysiis","soundex"};
	
	//Simple class that holds the textual function (EXACT, etc) and the parameter 
	//(if it has one) for each sub-command
	public class Line {
		private String function;
		private double param;
		private boolean hasParam;
		
		public Line(String f, double p) {
			function = f.toLowerCase();
			param = p;
			hasParam = true;
		}
		
		public Line(String f) {
			function = f.toLowerCase();
			hasParam = false;
		}
		
		public String toString() {
			return hasParam?
					function + " " + param : function;
		}
	
		public String getFunction() { return function; }
		public void setFunction(String f) { function = f; }
		public double getParam() {return param;}
		public void setParam(double p) { param = p; hasParam = true; }
		public boolean hasParam() {return hasParam;}

	}
	
	//References to this MatchmakerCommand can be used to indicate that
	//there was no command specified, as an alternative to throwing errors
	public static MatchmakerCommand EMPTY_COMMAND = new MatchmakerCommand();
	
	Header[] headers;
	ArrayList<Line> lines;
	
	public MatchmakerCommand() {
		headers = new Header[2];
		lines = new ArrayList<Line>();
	}
	public MatchmakerCommand(Header[] h) {
		headers = h;
		lines = new ArrayList<Line>();
	}
	
	//These three functions allow different ways to add lines to the command
	//depending on whether or not there is a parameter and whether or not the
	//caller has already built the Line. All 3 return a reference to the implicit
	//argument and thus are chainable.
	public MatchmakerCommand addLine(Line l) { lines.add(l); return this;}
	public MatchmakerCommand addLine(String f, double p) {addLine(new Line(f, p)); return this; }
	public MatchmakerCommand addLine(String f) {addLine(new Line(f)); return this; }
	
	
	public Line getLine(int n) {
		if (n >= lines.size())
			return null;
		return lines.get(n);
	}
	public int getLineCount() {return lines.size();	}
	
	//Formats the line for MySQL
	public String getSqlFunction(String mysql_db, String prefix, int dbnum, int lineNum) {
		Line line = lines.get(lineNum);
		System.out.println(line);
		String func = line.getFunction();
		Header head = headers[dbnum];
		String column = prefix + "db" + (dbnum + 1) + "." + head.name;

		if (func.equals("exact") || func.equals("within"))
			return head.name;
		else if (func.equals("first"))
			return ("LEFT(" + column + "," + line.getParam() + ")");
		else if (func.equals("last"))
			return ("RIGHT(" + column + "," + line.getParam() + ")");
		else if (func.equals("nysiis")) {
			if (!line.hasParam())
				return (mysql_db + ".NYSIIS(" + column + ")");
			else
				return ("LEFT(NYSIIS(" + column + ")," + line.getParam() + ")");
		}
		else if (func.equals("soundex")) {
			if (!line.hasParam())
				return ("SOUNDEX(" + column + ")");
			else
				return ("LEFT(SOUNDEX(" + column + ")," + line.getParam() + ")");
		}
		
		//Hopefully this will never be executed, but it makes Java happy
		return "";
	}
	
	//Getters and setters for headers
	public void setHeaders(Header[] h) {headers = h;}
	public void setHeader(Header h, int n) {headers[n] = h;}
	public Header[] getHeaders() {return headers;}
	public Header getHeader(int n) {return headers[n]; }
	
	public boolean isValid() {
		
		return true;
	}
	
}
