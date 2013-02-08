package edu.american;

import java.io.File;
import java.io.IOException;

import javax.xml.xpath.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

//Class to handle all interaction with XML command file
//Reads instructions from XML and returns them in Matchmaker formats - 
//i.e., Headers, MatchmakerCommands, and SelectExpressions
public class CommandFileProcessor {

	private Document doc;
	private XPath xpath;
	
	public CommandFileProcessor(File f) throws SAXException, ParserConfigurationException, IOException {
		doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
		xpath = XPathFactory.newInstance().newXPath();
	}
	
	//Parses all database headers out of a command file
	//By doing this first, only those columns that will be used
	//have to be put into MySQL, instead of the whole thing
	//@return: This function catches its own errors, and will return null if an
	//exception is met
	public Header[][] getHeaders() {
		return CommandFileProcessor.getHeaders(doc);
	}

	//Finds all headers that are children of the supplied node
	//The return array is organized the same way as the member method above:
	//the first dimension is always 2, and the 2-D array is non-rectangular, with each
	//array holding the headers for the corresponding database.
	//IMPORTANT - THIS METHOD REMOVES DUPLICATES
	public static Header[][] getHeaders(Node doc) {

		XPath xpath = XPathFactory.newInstance().newXPath();
		try {
			//Get Header nodes of all headers out of XML
			XPathExpression xpression = xpath.compile("descendant::header[dbnum/text()='1']");
			NodeList db1Headers = (NodeList) xpression.evaluate(doc, XPathConstants.NODESET);
			xpression = xpath.compile("descendant::header[dbnum/text()='2']");
			NodeList db2Headers = (NodeList) xpression.evaluate(doc, XPathConstants.NODESET);

			//Expressions for header info
			xpression = xpath.compile("child::name/text()");
			XPathExpression xTypeExpr = xpath.compile("child::type/text()");
			XPathExpression xDbnumExpr = xpath.compile("child::dbnum/text()");
			
			//Transfer to Header arrays
			Header[][] withDupes = new Header[2][];
			withDupes[0] = new Header[db1Headers.getLength()];
			withDupes[1] = new Header[db2Headers.getLength()];
			for(int ii = 0, jj = Math.max(withDupes[0].length, withDupes[1].length); ii < jj; ii++) {
				if (ii < withDupes[0].length) {
					withDupes[0][ii] = new Header(
						(String)xpression.evaluate(db1Headers.item(ii), XPathConstants.STRING),
						(String)xTypeExpr.evaluate(db1Headers.item(ii), XPathConstants.STRING),
						((Double)xDbnumExpr.evaluate(db1Headers.item(ii), XPathConstants.NUMBER)).intValue()
					);				
				}
				if (ii < withDupes[1].length) {
					withDupes[1][ii] = new Header(
							(String)xpression.evaluate(db2Headers.item(ii), XPathConstants.STRING),
							(String)xTypeExpr.evaluate(db2Headers.item(ii), XPathConstants.STRING),
							((Double)xDbnumExpr.evaluate(db2Headers.item(ii), XPathConstants.NUMBER)).intValue()
						);				
				}
			}
			
			//Filter out duplicates
			//Right now this is an ugly job with arrays - should move to sets or hashtables
			//later if time allows
			int[] removed = {0,0};
			for (int ii = 0; ii < 2; ii++) {
				for (int jj = 0, len = withDupes[ii].length; jj < len; jj++) {
					for (int kk = 0; kk < jj; kk++) {
						if (withDupes[ii][kk] != null && withDupes[ii][jj] != null &&
								withDupes[ii][jj].name.equals(withDupes[ii][kk].name)) {
							System.out.println("Found duplicate of " + withDupes[ii][jj].name + " in XML");
							withDupes[ii][jj] = null;
							removed[ii]++;
						}
					}
				}
			}
			Header[][] noDupes = new Header[2][];
			for (int ii = 0; ii < 2; ii++) {
				noDupes[ii] = new Header[withDupes[ii].length - removed[ii]];
				int index = 0;
				for (Header h : withDupes[ii]) {
					if (h != null) {
						noDupes[ii][index] = h;
						index++;
					}
				}
			}
			
			return noDupes;
			
		} catch(Exception e) {e.printStackTrace(); return null; }
		
	}
	
	//Gets SELECT instructions
	//@return: all criteria to be used in the SELECT function
	//ret[0] refers to expressions for the first database
	//ret[1] refers to expressions for the second database
	public SelectExpression[][] getSelect() {
		try {

			SelectExpression[][] ret = new SelectExpression[2][];

			//Get XPaths ready
			//The first will get the <select>s and must be generated for each db
			//The others are all used relative to a <select> and thus are static
			XPathExpression selectXpr;
			XPathExpression hNameXpr = xpath.compile("child::header/name/text()");
			XPathExpression hTypeXpr = xpath.compile("child::header/type/text()");
			XPathExpression hDbnumXpr = xpath.compile("child::header/dbnum/text()");
			XPathExpression opXpr = xpath.compile("child::operator/text()");
			XPathExpression valueXpr = xpath.compile("child::value/text()");
			
			for (int ii = 0; ii < 2; ii++) {
				//Get NodeList of selects for this database
				selectXpr = xpath.compile("//select/expression" +
						"[header/dbnum/text()='" + (ii + 1) + "']");
				NodeList selects = (NodeList) selectXpr.evaluate(doc, XPathConstants.NODESET);
				//Set length of return array
				ret[ii] = new SelectExpression[selects.getLength()];
				
				//Create SelectExpression objects from XML
				for (int jj = 0, len = ret[ii].length; jj < len; jj++) {
					Node thisSelect = selects.item(jj);
					ret[ii][jj] = new SelectExpression(
							new Header(
								(String)hNameXpr.evaluate(thisSelect, XPathConstants.STRING),
								(String)hTypeXpr.evaluate(thisSelect, XPathConstants.STRING),
								((Double)hDbnumXpr.evaluate(thisSelect, XPathConstants.NUMBER)).intValue()
							),
							(String)opXpr.evaluate(thisSelect, XPathConstants.STRING),
							(String)valueXpr.evaluate(thisSelect, XPathConstants.STRING)
						);	
				}
			}//end for:2 dbs
			return ret;
			
		} catch(Exception e) {e.printStackTrace(); return null;}
	}
	
	//BLOCK can have at most one command
	//If none is specified, a reference to MatchmakerCommand.EMPTY_COMMAND is returned
	public MatchmakerCommand getBlock() {
		try {
			//Find if BLOCK was defined
			XPathExpression blockXpr = xpath.compile("//block");
			Node blockNode = ((NodeList)blockXpr.evaluate(doc, XPathConstants.NODESET)).item(0);
			if (blockNode==null) {
				System.out.println("CFP: No block command");
				return MatchmakerCommand.EMPTY_COMMAND;
			}
				
			return getCommand(blockNode);
			
		} catch(Exception e) {e.printStackTrace(); return null;}
	}

	//FILTER can have at most one command
	//If none is specified, a reference to MatchmakerCommand.EMPTY_COMMAND is returned
	public MatchmakerCommand getFilter() {
		try {
			//Find if FILTER was defined
			XPathExpression filterXpr = xpath.compile("//filter");
			Node filterNode = ((NodeList)filterXpr.evaluate(doc, XPathConstants.NODESET)).item(0);
			System.out.println(filterNode);
			if (filterNode == null) {
				System.out.println("No filter command");
				return MatchmakerCommand.EMPTY_COMMAND;
			}
				
			return getCommand(filterNode);
			
		} catch(Exception e) {e.printStackTrace(); return null;}
	}
	
	//MATCH must have at least one command
	//If none is specified, an empty array is returned
	//It is thus the responsibility of the main program to send an error warning that
	//there were no match criteria specified
	//Essentially, this is being treated as a bad-request error rather than a 
	//malformed XML error
	public MatchmakerCommand[] getMatch() {
		try {
			//Find if BLOCK was defined
			XPathExpression matchXpr = xpath.compile("//match");
			NodeList matchNodes = (NodeList)matchXpr.evaluate(doc, XPathConstants.NODESET);
			if (matchNodes.equals(null)) {
				System.out.println("No match command");
				return new MatchmakerCommand[0];
			}
				
			MatchmakerCommand[] ret = new MatchmakerCommand[matchNodes.getLength()];
			for (int ii = 0, len = ret.length; ii < len; ii++)
				ret[ii] = getCommand(matchNodes.item(ii));

			return ret;
			
		} catch(Exception e) {e.printStackTrace(); return null;}

	}
	
	//used by getBlock(), getFilter(), and getMatch()
	//Returns null if there's a problem with the data supplied
	//@param: the node whose immediate children are the headers and lines
	//for the command block to be returned
	//@param source: the node whose immediate children are the headers and lines
	//for the command block to be returned
	private MatchmakerCommand getCommand(Node source) throws XPathExpressionException {
		//Get headers
		//There should only be 2 - one for each database
		Header[][] blockHeaders = CommandFileProcessor.getHeaders(source);
		
		if (blockHeaders[0].length != 1 || blockHeaders[1].length != 1)
			return null;

		MatchmakerCommand ret = new MatchmakerCommand(
				new Header[] {blockHeaders[0][0], blockHeaders[1][0]});
		
		NodeList lines = (NodeList) xpath.evaluate("child::line", source, XPathConstants.NODESET);
		XPathExpression funcXpr = xpath.compile("child::function/text()");
		XPathExpression paramXpr = xpath.compile("child::param/text()");

		//Get command lines
		String func = "";
		String param = "";
		for (int ii = 0, len = lines.getLength(); ii < len; ii++) {
			//Read line from XML
			func = funcXpr.evaluate(lines.item(ii));
			param = paramXpr.evaluate(lines.item(ii));

			//Check that this line has a valid function
			boolean hasValidFunction = false;
			for (String f : MatchmakerCommand.VALID_FUNCTIONS)
				if (func.toLowerCase().equals(f))
					hasValidFunction = true;
			if (!hasValidFunction)
				return null;
			
			//If all good, add line to command
			if (param.equals(""))
				ret.addLine(func);
			else
				ret.addLine(func, Double.parseDouble(param));
		}
		
		return ret;
	}
	
	//For debugging purposes only
	public static void main(String[] args) {
		try {
			CommandFileProcessor cfp = new CommandFileProcessor(new File("C:/Users/James/Desktop/cfp.xml"));
			System.out.println("Created cfp");
			
			MatchmakerCommand block = cfp.getBlock();
			System.out.println(block.getLine(0));
			
		} catch(Exception e) {e.printStackTrace(); }
	}
}
