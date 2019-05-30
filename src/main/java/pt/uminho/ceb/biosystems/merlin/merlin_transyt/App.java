package pt.uminho.ceb.biosystems.merlin.merlin_transyt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import pt.uminho.ceb.biosystems.merlin.database.connector.databaseAPI.ModelAPI;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;

public class App 
{
	/**
	 * @param args
	 * @throws IOException
	 * @throws NoSuchAlgorithmException 
	 * @throws InterruptedException 
	 */
	public static void main( String[] args ) throws IOException, NoSuchAlgorithmException, InterruptedException 

	//		System.out.println(TranSyTRetriever.verifyKeys());
	//		FileUtils.createZipFile("C:\\Users\\mario\\Desktop\\transyt\\", "C:\\Users\\mario\\Desktop\\transyt.tar.gz", 5);
	//		FileUtils.extractZipFile("C:\\Users\\mario\\Desktop\\teste2.tar.gz", "C:\\Users\\mario\\Desktop\\teste3");

	{

//		File textFile = new File("C:\\Users\\mario\\Desktop\\teste2\\metabolites.txt");
//		File textFile2 = new File("C:\\Users\\mario\\Desktop\\teste2\\genome.faa");
//		File textFile3 = new File("C:\\Users\\mario\\Desktop\\teste2\\taxID.txt");
		
		File textFile = new File("C:\\Users\\mario\\Desktop\\teste3\\metabolites.txt");
		File textFile2 = new File("C:\\Users\\mario\\Desktop\\teste3\\genome.faa");
		File textFile3 = new File("C:\\Users\\mario\\Desktop\\teste3\\taxID.txt");

		List<File> requiredFiles = new ArrayList<>();

		requiredFiles.add(0,textFile);
		requiredFiles.add(1,textFile2);
		requiredFiles.add(2,textFile3);

		HandlingRequestsAndRetrievalsTransyt post = new HandlingRequestsAndRetrievalsTransyt(requiredFiles);
		String docker= "";

		docker = post.postFiles();
		System.out.println(docker);
		//post.downloadFile();

		if(docker!=null) {
			Boolean go = false;

			while (!go) {
				go = post.getStatus(docker);

				if(go == null) {
					System.out.println("Error!");
					System.exit(1);
				}

				TimeUnit.SECONDS.sleep(3);
			} 

			post.downloadFile(docker,"C:\\Users\\mario\\Desktop\\test");

			//				MessageDigest md5Digest = MessageDigest.getInstance("MD5");
			//
			//				FileUtils.extractZipFile("C:\\Users\\mario\\Desktop\\test".concat(".tar.gz"), "C:\\Users\\mario\\Desktop");
			//
			//				File file = new File("C:\\Users\\mario\\Desktop\\results".concat("/transyt.xml"));
			//
			//				String checksum = getFileChecksum(md5Digest, file);
			//
			//				String key = readWordInFile("C:\\Users\\mario\\Desktop\\results".concat("/checksum.md5"));
			//
			//				System.out.println(checksum);
			//
			//				System.out.println(key);

			post.closeConnection(docker);

			//		File transytFile = new File("C:\\Users\\mario\\Desktop\\".concat("/transytteste"));
			//
			//		transytFile.mkdir();
			//
			//		File taxID = new File("C:\\Users\\mario\\Desktop\\transytteste\\tax.txt");
			//
			//		FileWriter writer = new FileWriter(taxID);
			//
			//		writer.append("3456");
			//
			//		writer.close();
		}}}


		//catch (Exception e) {
		//	post.closeConnection(docker);
		//}}
		//
		//private static String getFileChecksum(MessageDigest digest, File file) throws IOException
		//{
		//	//Get file input stream for reading the file content
		//	FileInputStream fis = new FileInputStream(file);
		//
		//	//Create byte array to read data in chunks
		//	byte[] byteArray = new byte[1024];
		//	int bytesCount = 0;
		//
		//	//Read file data and update in message digest
		//	while ((bytesCount = fis.read(byteArray)) != -1) {
		//		digest.update(byteArray, 0, bytesCount);
		//	};
		//
		//	//close the stream; We don't need it now.
		//	fis.close();
		//
		//	//Get the hash's bytes
		//	byte[] bytes = digest.digest();
		//
		//	//This bytes[] has bytes in decimal format;
		//	//Convert it to hexadecimal format
		//	StringBuilder sb = new StringBuilder();
		//	for(int i=0; i< bytes.length ;i++)
		//	{
		//		sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
		//	}
		//
		//	//return complete hash
		//	return sb.toString();
		//}
		//
		//public static String readWordInFile(String path){
		//
		//	try {
		//
		//		BufferedReader reader = new BufferedReader(new FileReader(path));
		//
		//		String line;
		//
		//		while ((line = reader.readLine()) != null) {
		//
		//			if(!line.isEmpty() &&  !line.contains("**")) {
		//
		//				reader.close();
		//				return line.trim();
		//			}
		//		}

		//		reader.close();
		//
		//	} 
		//	catch (IOException e) {
		//		e.printStackTrace();
		//
		//	}
		//	return null;
		//} 
		//}




