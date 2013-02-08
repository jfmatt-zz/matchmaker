package matchmaker;
import java.io.*;
import java.util.Scanner;
import java.util.Random;

public class FileFlood {

	public static void main(String[] args) throws IOException {

		System.out.println("Filename?");
		Scanner in = new Scanner(System.in);
		String fname = in.next();

		File data = new File("C:/Users/James/Desktop/" + fname);
		FileWriter fstream;
		BufferedWriter fWriter;
		try {
			fstream = new FileWriter(data);
			fWriter = new BufferedWriter(fstream);
		} catch (Exception e) {
			System.out.println("Error opening file.");
			return;
		}
		
		System.out.println("Number of lines?");
		int numLines = in.nextInt();
		
		Random random = new Random();
		
		for (int i = 0; i < numLines; i++) {
			try {
				for (int j = 0; j < 20; j++) {
					for (int k = 0; k < 10; k++)
						fWriter.write("" + (char)(random.nextInt('Z'-'A'+1)+'A') );
					if (j != 19)
						fWriter.write(",");
				}
				fWriter.write("\r\n");
			} catch (Exception e) {
				System.out.println("Error writing to file.");
			}
		}
		
		System.out.println("Done.");
	}

}
