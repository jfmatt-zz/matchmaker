package edu.american;

public class MatchUtil {	
	
	//START_IN and START_OUT hold, respectively, the characters that should be transformed
	//immediately when they appear at the start of a name and their respective
	//translations for NYSIIS
	private static final String[] START_IN = {
		"K", "E", "I", "O", "U", "KN", "PH", "PF", "WR", "RH", "DG", "MAC", "SCH"
	};
	private static final String[] START_OUT = {
		"C", "A", "A", "A", "A", "N",  "F",  "F",  "R",  "R",  "G",  "MC",  "S"
	};
	
	//Same as START_IN and START_OUT, but for ends of names
	private static final String[] END_IN = {
		"EE", "IE", "YE", "DT", "RT", "RD", "NT", "ND", "IX",  "EX"
	};
	private static final String[] END_OUT = {
		"Y",  "Y",  "Y",  "T",  "D",  "D",  "N",  "N",  "ICK", "ECK"
	};
	
	//Converts name to NYSIIS phonetic spelling
	//@param str: The name to be translated
	public static String nysiis(String str) {
	
		int len = str.length();
		if (len < 2) return str;
		str = str.toUpperCase();

		//Replace beginning
		for (int i = 0; i < 13; i++)
			if (len >= START_IN[i].length() && START_IN[i].equals(str.substring(0, START_IN[i].length()))) {
				str = START_OUT[i] + str.substring(START_IN[i].length(), str.length());
				len = str.length();
				break;
			}

		//Replace end
		for (int i = 0; i < 10; i++)
			if (len >= END_IN[i].length() && END_IN[i].equals(str.substring(len - END_IN[i].length(), len))) {
				str = str.substring(0, len - END_IN[i].length()) + END_OUT[i];
				len = str.length();
				break;
			}
		
		//Now go through string following main rules

		//Vars to be used in the loop
		String ret = str.substring(0, 1);
		int retLen = 1; //Current length of solution
		String curr = ""; //Current character being examined
		String next = ""; //Character next to be examined, or "" if looking at last character
		String next2 = ""; //Next 2 characters or "" if looking at last or second-to-last character
		String currCode = ""; //Translation of current character
		for (int i = 1; i < len; i++) {
			curr = str.substring(i, i + 1);
			currCode = "";

			retLen = ret.length();
			String prev = ret.substring(retLen - 1, retLen);
			
			//Get 'next' strings ready - if this is the end of
			//the name, one or both will just stay as the empty string
			next = "";
			next2 = "";
			try {
				next = str.substring(i + 1, i + 2);
				next2 = str.substring(i + 1, i + 3);
			} catch(Exception e){}

			//Find what this code should be based on rules
			if (curr.equals("A") || curr.equals("E") || curr.equals("I") || curr.equals("O") || curr.equals("U")) {
				if (!prev.equals("A"))
					currCode = "A";
				//Special case - EV => AF
				if (curr.equals("E") && next.equals("V")) {
					currCode += "F";
					i++;
				}
			} else if (curr.equals("Y") && i < len - 1)
				currCode = "A";
			else if (curr.equals("Q"))
				currCode = "G";
			else if (curr.equals("Z"))
				currCode = "S";
			else if (curr.equals("M"))
				currCode = "N";
			else if (curr.equals("K"))
				//KN => N, else K => C
				if (next.equals("N")) {
					currCode = "N";
					i++;
				}
				else
					currCode = "C";
			else if (curr.equals("S")) {
				//Any of these 3 scenarios will end up with 1-3 S's being added,
				//and the repeats disappear anyway
				//All that matters is how far we jump along the name
				if (next2.equals("CH"))
					i += 2; //This rule replaces three letters
				else if (next.equals("H"))
					i++; //This rule replaces two letters
				currCode = "S";
			} else if (curr.equals("P") && next.equals("H")) {
				//PH => F
				currCode = "F";
				i++;
			} else if (curr.equals("G") && next2.equals("HT")) {
				//GHT => TTT => T
				currCode = "T";
				i += 2;
			} else if (curr.equals("D") && next.equals("G")) {
				//DG => GG => G
				currCode = "G";
				i++;
			} else if (curr.equals("W") && next.equals("R")) {
				//WR => RR => R
				currCode = "R";
				i++;
			} else if (curr.equals("H")) {
				//If prev or next is nonvowel, H => prev, so it will be removed anyway
				if ( !prev.equals("A") || (!next.equals("A") && !next.equals("E") && 
					!next.equals("I") && !next.equals("O") && !next.equals("U")) )
					currCode = ""; 
				else
					currCode = "H";
			} else if (curr.equals("W")) {
				//AW => AA => A, so skip the middleman
				if (prev.equals("A")) 
					currCode = "";
				else
					currCode = "W";
			} else
				currCode = curr; //If no rules match, letter is its own code

			//Check for duplicates and add new codes
			if (!currCode.equals("") && !currCode.substring(0, 1).equals(prev))
				ret += currCode;
		}//end for : letters

		//Remove final S
		if (ret.substring(ret.length() - 1, ret.length()).equals("S"))
			ret = ret.substring(0, ret.length() - 1);
		
		//Change final AY to Y
		if (ret.length() > 1 && ret.substring(ret.length() - 2, ret.length()).equals("AY"))
			ret = ret.substring(0, ret.length() - 2) + "Y";

		//Remove final vowel
		if (ret.substring(ret.length() - 1, ret.length()).equals("A"))
			ret = ret.substring(0, ret.length() - 1);
		
		return ret;
	}
	
	public static void main(String[] args) {

		String input = "smythe";
		System.out.println(nysiis(input));

		System.out.println(input);
	}	
}
