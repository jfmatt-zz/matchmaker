package edu.american;

import java.io.*;
import java.util.Arrays;
import java.util.Scanner;
import javax.servlet.*;
import javax.servlet.http.*;

import matchmaker.*;

import java.sql.*;

public class MatchmakerServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
	public static final int BUFFER_SIZE = 4096;

	public static String MYSQL_DB = "dev";
	public static String MYSQL_URL = "jdbc:mysql://localhost:3306/";
	private static String MYSQL_USER = "root";
	private static String MYSQL_PASS = "jmaymseqsl";
	private static String FILE_ROOT = "C:/server/";
	private static String PUBLIC_ROOT = "C:/server/www/jfm.dev/public_html/";
	private static String OUTPUT_FOLDER = "matchmakeroutput/";
	private static String LOCATION = "http://jfm.dev:8080/MatchmakerWeb/MatchmakerServlet/";
	private static String PUBLIC_DOMAIN = "http://jfm.dev/";
	
	//Load essentials
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		System.out.println("MatchmakerServlet: init");
		try {
			Scanner in = new Scanner(new File("/var/lib/tomcat5/webapps/MatchmakerWeb/WEB-INF/classes/matchmaker.conf"));
			MYSQL_DB = in.nextLine().split(" ")[1];
			MYSQL_URL = in.nextLine().split(" ")[1];
			MYSQL_USER = in.nextLine().split(" ")[1];
			MYSQL_PASS = in.nextLine().split(" ")[1];
			FILE_ROOT = in.nextLine().split(" ")[1];
			PUBLIC_ROOT = in.nextLine().split(" ")[1];
			OUTPUT_FOLDER = in.nextLine().split(" ")[1];
			LOCATION = in.nextLine().split(" ")[1];
			PUBLIC_DOMAIN = in.nextLine().split(" ")[1];
		}catch(Exception e) {
			System.out.println("Init error.");
			throw new ServletException();
		}
	}
	
	public Connection getSQLConnection() {
		try {
			Class.forName ("com.mysql.jdbc.Driver").newInstance ();
			return DriverManager.getConnection(MYSQL_URL + MYSQL_DB, MYSQL_USER, MYSQL_PASS);
		} catch(Exception e) {e.printStackTrace(); return null;}		
	}
	
    //Send results to UploadApplet
	//@param request: HTTP headers will dictate what result is being asked for
	//If that result has not been evaluated yet, send back an error message.
	//Hopefully, the applet will deactivate buttons so that those errors will
	//be impossible.
	//Also used on applet startup to check what has been done for this user
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		System.out.println("MatchmakerServlet: doGet");

		if (request.getHeader("Matchmaker-Command")==null) {
			response.getWriter().println("Hello, MatchmakerServlet!");
			return;
		}
		
		Connection sqlConn = null;
		Statement sm = null;
		ResultSet rs = null;
		try {
			sqlConn = getSQLConnection();
			sm = sqlConn.createStatement();
		
			String command = request.getHeader("Matchmaker-Command");
			String id = request.getHeader("Matchmaker-ID");
			
			if (command.equals(MatchmakerServletConnector.ID_CHECK)) {
				int stepsDone = -2;
				String db1 = "";
				String db2 = "";
				String com = "";
				rs = sm.executeQuery("SELECT * FROM " + MYSQL_DB + ".users WHERE id = '" + id + "';");
				if (rs.next()) {
					stepsDone = rs.getInt("steps");
					db1 = rs.getString("db1");
					db2 = rs.getString("db2");
					com = rs.getString("commands");
				}
				response.addIntHeader("steps", stepsDone);
				response.addHeader("db1", db1);
				response.addHeader("db2", db2);
				response.addHeader("commands", com);
				return;
			}
			
			if (command.equals(MatchmakerServletConnector.ID_NEW)) {
	
				int newId = 0;
				//Create new user
				sm.execute("INSERT INTO " + MYSQL_DB + ".users(steps) VALUES(-1);");
				//Get id of last user added - i.e., the new one
				rs = sm.executeQuery("SELECT * FROM " + MYSQL_DB + ".users ORDER BY id DESC LIMIT 1;");
				rs.next();
				newId = rs.getInt("id");
				response.addHeader(MatchmakerServletConnector.ID_NEW, "" + newId);
				return;
			}
			
			if (command.equals("results")) {

				String fileName = PUBLIC_ROOT + OUTPUT_FOLDER + id + "results.csv";
				(new File(fileName)).delete();
				
				sm.execute("SELECT 'rowId1','rowId2','weight' UNION " +
						"(SELECT rowId1,rowId2,totalWeight INTO OUTFILE '" + fileName + "' " +
						"FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"' " +
						"LINES TERMINATED BY '\\r\\n' " +
						"FROM " + MYSQL_DB + "." + id + "LNK " +
						"ORDER BY totalWeight DESC" +
						");");
				
				response.addHeader("resultUrl", PUBLIC_DOMAIN + OUTPUT_FOLDER + id + "results.csv");
				return;
			}
		
		} catch(SQLException e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		finally {
			if (rs != null) try {rs.close();} catch(SQLException e) {}
			if (sm != null) try {sm.close();} catch(SQLException e) {}
			if (sqlConn != null) try {sqlConn.close();} catch(SQLException e) {}
		}
		
	}//End doGet()

	//Receive instructions from UploadApplet
	//@param request: HTTP headers will dictate what step should be evaluated, and for what user
	//If that result is not the next one in the chain, send back an error message.
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		System.out.println("Matchmaker: doPost");

		Connection sqlConn = null;
		Statement sm = null;
		ResultSet rs = null;
		try {
			sqlConn = getSQLConnection();
			sm = sqlConn.createStatement();
		
			String command = request.getHeader("Matchmaker-Command");
			String id = request.getHeader("Matchmaker-ID");
			//Useful macro for sql table names
			String tablePrefix = MYSQL_DB + "." + id;
			System.out.println("Carrying out command: '" + command + "' for user " + id);
			
			//Check that all files are present and accounted for
			rs = sm.executeQuery("Select * from " + MYSQL_DB + ".users WHERE id='" + id + "' AND db1!='NULL'" +
				"AND db2!='NULL' AND commands!='NULL';");
			if (!rs.next()) {
				System.out.println("Expectation Failed: Line 123");
				response.sendError(HttpServletResponse.SC_EXPECTATION_FAILED);
				return;
			}
			
			//Get command file
			//rs was advanced in try block above, so it holds info about this user already
			File commandFile;
			//Grab filename from MySQL
			String cfName = rs.getString("commands");
			//Make File
			commandFile = new File(FILE_ROOT + id + cfName);
			System.out.println("Got command file.");
			
			//Create CFP to do XML work
			CommandFileProcessor cfp;
			cfp = new CommandFileProcessor(commandFile);
			System.out.println("CFP made.");
			
			/////////////////////////////////////////////////////////////////////
			//The following blocks handle individual command requests from     //
			//the server, and make up the actual Matchmaker logic.             //
			/////////////////////////////////////////////////////////////////////
			
			//Step 1: Put files into MySQL
			//Creates or replaces 2 tables
			if (command.equals("step1")) {
				//Remove previous tables
				sm.execute("DROP TABLE IF EXISTS " + tablePrefix + "db1, " + tablePrefix + "db2;");
				System.out.println("Old tables removed.");
			
				//Search command file to find relevant columns - no need to copy everything
				Header[][] xmlHeaders;
				xmlHeaders = cfp.getHeaders();
				System.out.println("Got headers from XML.");
				if (xmlHeaders == null) {
					System.out.println("Matchmaker: No xmlHeaders found...");
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					return;
				}
					
				//Set up table according to types in command file (numeric/bool/text)
				//xmlHeaders will be sorted (by name) in this process
				for (int ii = 0; ii < 2; ii++) {
					String setupQuery;
					setupQuery =
						"CREATE TABLE " + tablePrefix + "db" + (ii + 1) + 
						"(rowId INT NOT NULL AUTO_INCREMENT";
					for (int jj = 0, len = xmlHeaders[ii].length; jj < len; jj++) {
						Header h = xmlHeaders[ii][jj];
						setupQuery += ", " + h.name.toLowerCase();
						if (h.type.equals(Header.TEXT)) {
							setupQuery += " VARCHAR(40)";
						}
						else if (h.type.equals(Header.NUMERIC)) {
							setupQuery += " DOUBLE";
						}
						else if (h.type.equals(Header.BOOLEAN)) {
							setupQuery += " TINYINT";
						}
					}
					setupQuery += ", PRIMARY KEY(rowId));";
					sm.execute(setupQuery);
					System.out.println("Built table " + (ii + 1));
						
					//Get database file
					//rs may have timed out, so do a fresh query
					rs = sm.executeQuery("Select db" + (ii + 1) + 
							" from " + MYSQL_DB + ".users WHERE id='" + id + "';");
					rs.next();
					String dbFName = FILE_ROOT + id + rs.getString("db" + (ii + 1));
					File dbFile = new File(dbFName);
									
					//Read headers from CSV to find where in a row each one is
					//Also create portion of LOAD DATA query that specifies headers
					Scanner dbScan = new Scanner(dbFile);
					String queryColNames = "";
					String[] cols = dbScan.nextLine().trim().split(",",-1);
					Arrays.sort(xmlHeaders[ii]); //makes searching faster

					String castAndSet = "";
					boolean isFirstCastXpr = true;
					for (int cc = 0, dd = cols.length; cc < dd; cc++) {
						for (int hNum = 0, hLen = xmlHeaders[ii].length; hNum < hLen; hNum++) {
							Header h = xmlHeaders[ii][hNum];

							int compare = cols[cc].compareToIgnoreCase(h.name);
								if (compare < 0) {
									//would have seen it by now
									queryColNames += "@discard";
									if (cc != dd - 1) {
										queryColNames += ",";
									}
								}
								else if (compare == 0){
									//If it matches, set header's colnum and move to next
									//entry from CSV
									h.colNum = cc;
									System.out.println("Header '" + h.name + "' is in column " + cc + " (0-indexed).");
									//Used columns are put into temp vars and only loaded if non-empty
									queryColNames += "@TEMP" + h.name;
									if (!isFirstCastXpr)
										castAndSet+=",";
									else
										isFirstCastXpr=false;
									castAndSet += h.name + "=IF(@TEMP"+h.name+"!='',@TEMP"+h.name+",NULL)";
									if (cc != dd - 1) {
										queryColNames += ",";
									}
								}
								if (compare <= 0)
									break;
			
								//At this point, compare > 0
								//meaning that we haven't gotten to the point in the list where
								//the CSV header would be yet
								//Just check if it's the end of the list; otherwise, keep looking
								if (hNum == hLen - 1) {
									queryColNames += "@discard";
									if (cc != dd - 1)
										queryColNames += ",";
								}
							}
						}
						System.out.println("Matched CSV headers with XML headers.");
						System.out.println(queryColNames);
						
						//If any header wasn't found, throw error
						for (Header h : xmlHeaders[ii])
							if (h.colNum == -1) {
								System.out.println("Matchmaker: Header '" + h.name + "' in XML file not found in CSV");
								response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
								return;
							}
						System.out.println("All XML headers found in CSV.");
						
						//Load CSV
						String q = "LOAD DATA INFILE '" + dbFName + "' "
								+ "INTO TABLE " + tablePrefix + "db" + (ii + 1) + " "
								+ "FIELDS TERMINATED BY ',' "
								+ "IGNORE 1 LINES "
								+ "(" + queryColNames + ")"
								+ ( (!castAndSet.equals("") )?" SET ":"")
								+ castAndSet
										+ ";";
						System.out.println(q);
						sm.execute(q);
						
						System.out.println("Data inserted into table.");
	
					} //End for: 2 files
					
				//Update user records to show that this step has been done
				sm.execute("UPDATE " + MYSQL_DB + ".users SET steps=1 where id='" + id + "';");
				
				//If everything went according to plan, set response code 200
				response.setStatus(HttpServletResponse.SC_OK);
				return;
	
			}
			
			//Step 2: Select and block - add select as boolean (pass/fail)
			//and block as string (regardless of actual data type) to master databases
			//Creates 0 new tables
			if (command.equals("step2")) {
				//Read select and block criteria from command file
				SelectExpression[][] select = cfp.getSelect();
				MatchmakerCommand block = cfp.getBlock();
				if (select.equals(null)) {
					//do something error-related
				}
				//need to check isValid() for all selects
				if (block.equals(null)) {
					//do something error-related
				}				
				if (!block.isValid()) {
					//do something error-related
				}
				
				//Check dbs against select/block specifications
				for (int ii = 0; ii < 2; ii++) {
					//Remove old selectCol column if it exists
					rs = sm.executeQuery("SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS "
							+ "WHERE TABLE_SCHEMA='" + MYSQL_DB + "' "
							+ "AND TABLE_NAME='" + id + "db" + (ii + 1) + "' "
							+ "AND column_name='selectCol';");
					if (rs.next())
						sm.execute("ALTER TABLE " + tablePrefix + "db" + (ii + 1)
								+ " DROP COLUMN selectCol;");
					//Add new selectCol column
					//All entries default to passing (1)
					sm.execute("ALTER TABLE " + tablePrefix + "db" + (ii + 1)
							+ " ADD COLUMN selectCol BOOLEAN DEFAULT '1';");
						
					int len = select[ii].length;
					//If there are selects for this database:
					if (len > 0) {
						//Give any record where any select expression is not met
						//a select value of 0
						String selectQuery = "UPDATE " + tablePrefix + "db" + (ii + 1)
								+ " SET selectCol = 0 WHERE ";
						for (int jj = 0; jj < len; jj++) {
							//SelectExpression.toString() is formatted to be used with SQL
							selectQuery += "!(" + select[ii][jj].toString() + ")";
							if (jj < len - 1)
								selectQuery += " OR ";
						}
						selectQuery += ";";
						sm.execute(selectQuery);
					}

					//Remove old block column if it exists
					rs = sm.executeQuery("SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS "
							+ "WHERE TABLE_SCHEMA='" + MYSQL_DB + "' "
							+ "AND TABLE_NAME='" + id + "db" + (ii + 1) + "' "
							+ "AND column_name='block';");
					if (rs.next())
						sm.execute("ALTER TABLE " + tablePrefix + "db" + (ii + 1)
								+ " DROP COLUMN block;");
					
					if (block.equals(MatchmakerCommand.EMPTY_COMMAND)) {
						//No BLOCK was specified, so set all the same
						System.out.println("No BLOCK specified");
						sm.execute("ALTER TABLE " + tablePrefix + "db" + (ii + 1)
								+ " ADD COLUMN block BOOLEAN DEFAULT '1';");
						//At this point, we're done with this db - select and block are handled
						continue;
					}
					
					//If we get here, then there is a valid BLOCK command available
					//because null or EMPTY_COMMAND values have already been dealt with

					//Set up block column of appropriate type
					if (block.getHeader(0).type.equals(Header.TEXT)) {
						sm.execute("ALTER TABLE " + tablePrefix + "db" + (ii + 1)
								+ " ADD COLUMN block VARCHAR(100);");
					} else if (block.getHeader(0).type.equals(Header.NUMERIC)) {
						sm.execute("ALTER TABLE " + tablePrefix + "db" + (ii + 1)
								+ " ADD COLUMN block DOUBLE;");
					} else if (block.getHeader(0).type.equals(Header.BOOLEAN)) {
						sm.execute("ALTER TABLE " + tablePrefix + "db" + (ii + 1)
							+ " ADD COLUMN block BOOLEAN;");
					}
					
					//Execute block command
					String blockQuery = "UPDATE " + tablePrefix + "db" + (ii + 1)
						+ " SET block = " + block.getSqlFunction(MYSQL_DB,tablePrefix,ii,0) + ";";
					sm.execute(blockQuery);
					
					//Index by block
					//A happy accident of this being after the above "continue" is that
					//it will be skipped if there is no block command, because in that case
					//indexing isn't helpful anyway because block=1 for all rows
					try {
						sm.execute("DROP INDEX blockIndex on " + tablePrefix + "db" + (ii + 1));
					} catch(SQLException e) {
						//Exception means that the index didn't exist anyway, so just proceed
					}
					sm.execute("CREATE INDEX blockIndex on " + tablePrefix + "db" + (ii + 1)
							 + "(block)");
					
				}
				//Update user records to show that this step has been done
				sm.execute("UPDATE " + MYSQL_DB + ".users SET steps=2 where id='" + id + "';");
				
				response.setStatus(HttpServletResponse.SC_OK);
				return;
			}
			
			//Step 3: Create frequency tables
			//Creates 1 table for each db for each MATCH command line - FRQ
			//Also adds one column for each MATCH command line to each master database
			if (command.equals("step3")) {

				System.out.println("Processing step 3 for user " + id);
				
				MatchmakerCommand[] match = cfp.getMatch();
				if (match == null || match.length == 0) {
					//error
				}
				
				//Get block datatype
				rs = sm.executeQuery("SHOW COLUMNS FROM " + tablePrefix 
						+ "db1 WHERE FIELD='block';");
				rs.next();
				String blockType = rs.getString("Type");
				System.out.println("Block type is " + blockType);
				
				//For each db:
				for (int db = 1; db < 3; db++) {
					//For each command block:
					for (int ii = 0; ii < match.length; ii++) {
						//For each line:
						System.out.println(match[0]);
						for (int jj = 0; jj < match[ii].getLineCount(); jj++) {
							String matchRowId = db + "_" + ii + "_" + jj;
							System.out.println("Making frequency table for row: " + matchRowId);
							
							//Add values that will be matched on as their own columns with a standardized
							//naming format. This becomes important if using functions like NYSIIS,
							//because this way they only need to be evaluated once
							try {
								sm.execute("ALTER TABLE " + tablePrefix + "db" + db
										+ " DROP COLUMN " + matchRowId + ";");
							} catch(SQLException e) {//means that col didn't exist
							}
							sm.execute("ALTER TABLE " + tablePrefix + "db" + db
									 + " ADD COLUMN " + matchRowId + " " 
									 + match[ii].headers[0].getSqlType() + ";");
							sm.execute("UPDATE " + tablePrefix + "db" + db
									+ " SET " + matchRowId + "=" 
									+ match[ii].getSqlFunction(MYSQL_DB, tablePrefix, db - 1, jj) + ";");

							//Make frequency table							
							sm.execute("DROP TABLE IF EXISTS " + tablePrefix + "FRQ" + matchRowId + ";");
							sm.execute("CREATE TABLE " + tablePrefix + "FRQ" + matchRowId
									+ "(value " + match[ii].getHeader(0).getSqlType()
									+ ",count INT,block " + blockType + ");");
							//Populate frequency table

							//Handle range matches separately 
							if (match[ii].getLine(jj).getFunction().equals("within")) {
								//Doesn't actually need to be handled differently here!
								//All we're doing is counting the # of occurrences of each unique value
								//which can be done the same way
								
								//Could theoretically normalize them to 30, 35, 40, etc
								//but that doesn't really make sense since 34 should match 36 on <=5
								//so we don't want everything divided harshly into blocks
							}
							//Populate FRQ for non-range matches
							String q = "INSERT INTO " + tablePrefix + "FRQ" + matchRowId
									+ " (SELECT " + matchRowId + " as value, count(*) as count, block from " +
											tablePrefix + "db" + db + " WHERE selectCol=1 " +
											"GROUP BY value, block);";
							System.out.println(q);
							sm.execute(q);
							
						}
					}
				}
				
				//Update user records to show that this step has been done
				sm.execute("UPDATE " + MYSQL_DB + ".users SET steps=3 where id='" + id + "';");
				
				response.setStatus(HttpServletResponse.SC_OK);
				return;
			}
			
			//Step 4: Pair entries, filter pairs, and get transmission weights
			//Creates 1 table - LNK
			if (command.equals("step4")) {
				MatchmakerCommand[] match = cfp.getMatch();
				if (match == null || match.length == 0) {
					//error
				}
				MatchmakerCommand filter = cfp.getFilter();
				if (filter == null) {
					//error
				}
				//Get block datatype
				rs = sm.executeQuery("SHOW COLUMNS FROM " + tablePrefix 
						+ "db1 WHERE FIELD='block';");
				rs.next();
				String blockType = rs.getString("Type");
				
				//Set up LNK table
				sm.execute("DROP TABLE IF EXISTS " + tablePrefix + "LNK;");
				String matchCols = "match0 INT DEFAULT -1, wgtMatch0 DECIMAL(5,2) DEFAULT 0";
				//One column for each match command to hold level at which they match
				for (int ii = 1; ii < match.length; ii++)
					matchCols += ", match" + ii + " INT DEFAULT -1, wgtMatch" + ii + " DECIMAL(5,2) DEFAULT 0";
				String q = "CREATE TABLE " + tablePrefix + 
						"LNK(rowId1 INT, rowId2 INT, block " + blockType + ", " +
						matchCols + ", totalWeight DECIMAL(5,2));";
				System.out.println(q);
				sm.execute(q);
				
				if (filter.equals(MatchmakerCommand.EMPTY_COMMAND)) {
					//Load all pairs that match on block					
					sm.execute("INSERT INTO " + tablePrefix + "LNK(rowId1,rowId2,block) "
							+ "SELECT db1.rowId as rowId1, db2.rowId as rowId2, db1.block "
							+ "FROM " + tablePrefix + "db1 as db1, " + tablePrefix + "db2 as db2 "
							+ "WHERE db1.selectCol=1 AND db2.selectCol=1 "
							+ "AND db1.block=db2.block;");
				} else {
					String filterCol = "filter";

					//If this operation has been used before in the match commands,
					//find out where and use it again to reduce redundancy
					//This check can be a huge time saver if the databases are large and
					//the command file is small, which will almost always be true

					//If filter operation hasn't been used before,
					//create and calculate new 'filter' column for each db   
					for (int db = 1; db <= 2; db++) {
						sm.execute("ALTER TABLE " + tablePrefix + "db" + db + 
								"ADD COLUMN filter " + filter.getHeader(0).getSqlType() +
								"DEFAULT " + filter.getSqlFunction(MYSQL_DB, tablePrefix, db - 1, 0) +
								";");
					}
					
					//Now populate LNK table, checking for filter conditions
					
					//Handle range matches separately
					if (filter.getLine(0).getFunction().equals("within")) {
						double range = filter.getLine(0).getParam();
						sm.execute("INSERT INTO " + tablePrefix + "LNK(rowId1,rowId2) " +
								"SELECT " + id + "db1.rowId as rowId1, " + id + "db2.rowId as rowId2 " +
								"FROM " + tablePrefix + "db1 as db1, " + tablePrefix + "db2 as db2 " +
								"WHERE db1.block=db2.block " + 
								"AND db1.selectCol=1 AND db2.selectCol=1 " +
								//here comes the range match
								"AND ABS(db1." + filterCol + "-db2." + filterCol + ")<=" + range + ";"
						);
						
					}
					//Handle all non-range matches
					else {	
						sm.execute("INSERT INTO " + tablePrefix + "LNK(rowId1,rowId2) " +
								"SELECT " + id + "db1.rowId as rowId1, " + id + "db2.rowId as rowId2 " +
								"FROM " + tablePrefix + "db1 as db1, " + tablePrefix + "db2 as db2 " +
								"WHERE db1.block=db2.block " + 
								"AND db1.selectCol=1 AND db2.selectCol=1 " +
								"AND db1." + filterCol + "=db2." + filterCol + ";"
						);
					}
				}
				
				//For each command:
				for (int ii = 0, l1 = match.length; ii < l1; ii++) {
					//For each row:
					for (int jj = 0, l2 = match[ii].getLineCount(); jj < l2; jj++) {
						//Find all pairs that match for this row and have not yet been marked
						//and mark them with this row number
						String matchRowId = ii + "_" + jj;

						//Handle range matches separately
						if (match[ii].getLine(jj).getFunction().equals("within")) {
							double range = match[ii].getLine(jj).getParam();
							sm.execute("UPDATE " + tablePrefix + "LNK as LNK, " 
									+ tablePrefix + "db1 as db1, " + tablePrefix + "db2 as db2 "
									//set this mark to the current row number
									+ "SET LNK.match" + ii + "=" + jj + " "
									//where there is no mark yet, so that we don't overwrite
									//all the level 0 matches
									+ "WHERE LNK.match" + ii + "=-1 "
									//using the rows from the dbs specified by the LNK rowIds
									+ "AND LNK.rowId1=db1.rowId "
									+ "AND LNK.rowId2=db2.rowId "
									//where the db entries match for this comparison
									//(the values were computed earlier for the FRQ table)
									+ "AND ABS(db1.1_" + matchRowId + "-db2.2_" + matchRowId + ")<=range"
									+ ";"
								);							
						}
						else {
							//Handle all non-range matches
							sm.execute("UPDATE " + tablePrefix + "LNK as LNK, " 
								+ tablePrefix + "db1 as db1, " + tablePrefix + "db2 as db2 "
								//set this mark to the current row number
								+ "SET LNK.match" + ii + "=" + jj + " "
								//where there is no mark yet, so that we don't overwrite
								//all the level 0 matches
								+ "WHERE LNK.match" + ii + "=-1 "
								//using the rows from the dbs specified by the LNK rowIds
								+ "AND LNK.rowId1=db1.rowId "
								+ "AND LNK.rowId2=db2.rowId "
								//where the db entries match for this comparison
								//(the values were computed earlier for the FRQ table)
								+ "AND db1.1_" + matchRowId + "=db2.2_" + matchRowId
								+ ";"
							);
						}
					}
				}
				
				//At this point, the column corresponding to each match command holds
				//the first row number in that command (0-indexed) where each pair of
				//entries matches, or -1 if they do not match
				
				//Create TRN table
				sm.execute("DROP TABLE IF EXISTS " + tablePrefix + "TRN;");
				sm.execute("CREATE TABLE " + tablePrefix + "TRN(matchRowId VARCHAR(100), count INT);");
				//For each command:
				for (int ii = 0, l1 = match.length; ii < l1; ii++) {
					//For each row:
					for (int jj = 0, l2 = match[ii].getLineCount(); jj < l2; jj++) {
						String matchRowId = ii + "_" + jj;
						//Count how many pairs hit on this comparison and write to TRN
						q = "INSERT INTO " + tablePrefix + "TRN "
								+ "SELECT '" + matchRowId + "', count(*) "
								+ "FROM " + tablePrefix + "LNK as LNK "
								+ "WHERE match" + ii + "<=" + jj + " "
								+ "AND match" + ii + ">=0;";
						System.out.println(q);
						sm.execute(q);
					}
				}

				//Update user records to show that this step has been done
				sm.execute("UPDATE " + MYSQL_DB + ".users SET steps=4 where id='" + id + "';");
				
				response.setStatus(HttpServletResponse.SC_OK);
				return;
			}
			
			//Step 5: Evaluate weights
			if (command.equals("step5")) {
				MatchmakerCommand[] match = cfp.getMatch();
				if (match == null || match.length == 0) {
					//error
				}
	
				rs = sm.executeQuery("SELECT count(*) AS count FROM " + tablePrefix + "db2;");
				rs.next();
				long db2RowCount = rs.getLong("count");
				rs = sm.executeQuery("SELECT count(*) AS count FROM " + tablePrefix + "db1;");
				rs.next();
				long db1RowCount = rs.getLong("count");
				rs = sm.executeQuery("SELECT count(*) AS count FROM " + tablePrefix + "TRN");
				rs.next();
				long lnkRowCount = rs.getLong("count");
				
				for (int ii = 0, l1 = match.length; ii < l1; ii++) {
					String matchRowId = ii + "_" + 0;
					System.out.println("Evaluating weight for " + matchRowId);
					//If it's a top-level command, use this formula
					String q = "UPDATE " + tablePrefix + "FRQ2_" + matchRowId + " as FRQ2, " +
						tablePrefix + "LNK as LNK, " +
						tablePrefix + "db2 as db2, " +
						tablePrefix + "TRN as TRN " +
						"SET LNK.wgtMatch" + ii + "=" +
								"(LOG2(POWER((TRN.count / "+lnkRowCount+"),2))" +
								"+LOG2("+db2RowCount+"/FRQ2.count)) " +
						"WHERE " +
						"LNK.match" + ii + "=0 AND " +
						"TRN.matchRowId='" + matchRowId + "' AND " + 
						"FRQ2.value=db2.2_" + matchRowId + " AND " +
						"db2.rowId=LNK.rowId2;"
					;
					System.out.println(q);
					sm.execute(q);
					System.out.println("done");

					//For non-top-level commands:
					for (int jj = 1, l2 = match[ii].getLineCount(); jj < l2; jj++) {
						matchRowId = ii + "_" + jj;
						q = "UPDATE " + tablePrefix + "FRQ2_" + matchRowId + " as FRQ2, " +
						tablePrefix + "LNK as LNK, " +
						tablePrefix + "db2 as db2, " +
						tablePrefix + "TRN as TRN " +
						"SET LNK.wgtMatch" + ii + "=" +
								"(LOG2(POWER((TRN.count / "+lnkRowCount+"),2))" +
								"+LOG2("+db2RowCount+"/FRQ2.count)) " +
						"WHERE " +
						"LNK.match" + ii + "=" + jj + " AND " +
						"TRN.matchRowId='" + matchRowId + "' AND " + 
						"FRQ2.value=db2.2_" + matchRowId + " AND " +
						"db2.rowId=LNK.rowId2;";

						System.out.println(q);
						sm.execute(q);
						System.out.println("done");
					}
					
					//Assign (negative) weights for non-matches
					matchRowId = ii + "_" + "0";
					System.out.println("Evaluating weight for " + ii + "_nomatch");
					sm.execute("UPDATE " + tablePrefix + "FRQ1_" + matchRowId + " as FRQ1, " +
							tablePrefix + "FRQ2_" + matchRowId + " as FRQ2, " + 
							tablePrefix + "LNK as LNK, " +
							tablePrefix + "db2 as db2, " +
							tablePrefix + "db1 as db1, " +
							tablePrefix + "TRN as TRN, " +
							tablePrefix + "TRN as TRN_CLONE " +
							"SET LNK.wgtMatch" + ii + "=" +
								"(LOG2(POWER((TRN.count / TRN_CLONE.count),2))" +
								"+LOG2((FRQ2.count * FRQ1.count)/(" + db1RowCount + "*" + db2RowCount + ")))" +
							" WHERE " +
							"LNK.match" + ii + "=-1 AND " + 
							"TRN_CLONE.matchRowId='numRows' AND " + 
							"TRN.matchRowId='" + matchRowId + "' AND " + 
							"FRQ1.value=db1.1_" + matchRowId + " AND " +
							"db1.rowId=LNK.rowId1 AND " +
							"FRQ2.value=db2.2_" + matchRowId + " AND " +
							"db2.rowId=LNK.rowId2;"
					);
				}
				
				//Add up total weight
				String wgtCols = "wgtMatch0";
				for (int ii = 1, l1 = match.length; ii < l1; ii++)
					wgtCols += "+wgtMatch" + ii;
				sm.execute("UPDATE " + tablePrefix + "LNK SET totalWeight=" + wgtCols + ";");
				
				//Update user records to show that this step has been done
				sm.execute("UPDATE " + MYSQL_DB + ".users SET steps=5 where id='" + id + "';");
				
				response.setStatus(HttpServletResponse.SC_OK);				
				return;
			}
		} catch(Exception e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		finally {
			if (rs != null) try {rs.close();} catch(SQLException e) {}
			if (sm != null) try {sm.close();} catch(SQLException e) {}
			if (sqlConn != null) try {sqlConn.close();} catch(SQLException e) {}
		}
	
		//If we got here, something's not right.
		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
	}//end doPost()
	
	//Upload files from applet
	//@param request: HTTP headers say what the filename and user id are for this file,
	//so that it can be stored with a unique filename
	//Headers also say which chunk this is for large files
	//Errors will be generated only in the case of chunks arriving out of order,
	//or too many chunks arriving - no checksum for data corruption
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException{
		System.out.println("MatchmakerServlet: doPut");

		Connection sqlConn = null;
		Statement sm = null;
		ResultSet rs = null;
		try {
			sqlConn = getSQLConnection();
			sm = sqlConn.createStatement();
		
			String fName = request.getHeader(UploadThread.FILE_NAME_HEADER);
			String role = request.getHeader(UploadThread.ROLE_HEADER);
			String id = request.getHeader(UploadThread.ID_HEADER);
			int nChunks = request.getIntHeader(UploadThread.CHUNK_COUNT_HEADER);
			int chunk = request.getIntHeader(UploadThread.CHUNK_HEADER);
			System.out.println("Writing chunk " + chunk + " of " + nChunks + " to " + FILE_ROOT + id + fName);
			
			//Set up streams
			File f = new File(FILE_ROOT + id + fName);
			//If the first chunk is arriving, assume intentional rewrite
			if (f.exists() && chunk == 0) {
				System.out.println("Overwriting...");
				f.delete();
			}
			if (!f.exists())
				f.createNewFile();
			FileOutputStream out = new FileOutputStream(f, true);
			InputStream in = request.getInputStream();

			try {
				//Check if file is the right size for this chunk to be arriving
				//Can't correct it if things went wrong, but will at least provide feedback
				//so the file can be re-upped later, hopefully with better results
				if (chunk != 0) {
					if (chunk >= nChunks || f.length() != (chunk * UploadThread.MAX_CHUNK_SIZE)) {
						response.sendError(HttpServletResponse.SC_BAD_REQUEST);//Send error in response
						return;
					}
				}
				
				//By this point, all error checking should be done
				
				//Move everything from request to file
				byte[] buf = new byte[BUFFER_SIZE];
				int bytesRead = 0;
				int read = 0;
				while((read = in.read(buf)) != -1) {
					bytesRead += read;
					out.write(buf, 0, read);
				}
				
				//If last chunk, update user records
				sm.execute("UPDATE " + MYSQL_DB + ".users SET " + role + " = '" +
						fName + "' WHERE id = " + id + ";");
				rs = sm.executeQuery("SELECT id FROM " + MYSQL_DB + ".users WHERE db1!='NULL'" +
						"AND db2!='NULL' AND commands!='NULL' AND steps='-1';");
				if (rs.next())
					sm.execute("UPDATE " + MYSQL_DB + ".users SET steps='0';");
	
				
				response.setStatus(HttpServletResponse.SC_OK);
			} finally {
				//Clean up and release files
				in.close();
				out.close();
			}
		} catch(SQLException e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} finally {
			if (rs != null) try {rs.close();} catch(SQLException e) {}
			if (sm != null) try {sm.close();} catch(SQLException e) {}
			if (sqlConn != null) try {sqlConn.close();} catch(SQLException e) {}
		}

	}

	//Deletes all of a user's files and databases
	//@param request: HTTP headers give user id to be erased
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		System.out.println("MatchmakerServlet: doDelete");
	}
}
