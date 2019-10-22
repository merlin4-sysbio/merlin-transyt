package pt.uminho.ceb.biosystems.merlin.merlin_transyt;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import es.uvigo.ei.aibench.workbench.Workbench;
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

	{

		//FILES MUST BE SUBMITTED IN THE FOLLOWING ORDER: 1.PROTEOME -> 2.MODEL or METABOLITES
		
		String transytDirectory = "C:\\\\Users\\\\BioSystems\\\\Desktop\\\\rosalind\\\\testFiles\\\\emanuel\\\\new3\\";
		String transytResultsFile;

		File textFile = new File("C:\\Users\\BioSystems\\Desktop\\rosalind\\testFiles\\emanuel\\metabolites.txt");
		File textFile2 = new File("C:\\Users\\BioSystems\\Desktop\\rosalind\\testFiles\\emanuel\\genome.faa");
//		File textFile3 = new File("/Users/davidelagoa/Downloads/taxID.txt");

		List<File> requiredFiles = new ArrayList<>();

		
		requiredFiles.add(textFile2);
		requiredFiles.add(textFile);
//		requiredFiles.add(textFile3);

		HandlingRequestsAndRetrievalsTransyt post = new HandlingRequestsAndRetrievalsTransyt(requiredFiles, null);

		String submissionID = "";

		boolean verify = false;

		try {

			submissionID = post.postFiles();

			if(submissionID!=null) {

				try {
					System.out.println("SubmissionID attributed: " + submissionID);
					int responseCode = -1;

				
					System.out.println("files submitted, waiting for results...");

					while (responseCode!=200) {

						responseCode = post.getStatus(submissionID);

						if(responseCode == -1) {  
							System.out.println("Error!");
							System.exit(1);
						}
						else if (responseCode==503) {
							Workbench.getInstance().warn("The server cannot handle the submission due to capacity overload. Please try again later!");
							System.exit(1);
						}
						else if (responseCode==500) {
							Workbench.getInstance().warn("Something went wrong while processing the request, please try again");
							System.exit(1);
						}
						else if (responseCode == 400) {
							Workbench.getInstance().warn("The submitted files are fewer than expected");
							System.exit(1);
						}

						TimeUnit.SECONDS.sleep(3);
					}


					System.out.println("downloading TranSyT results");

//					verify = post.downloadFile(submissionID, transytDirectory.concat("/results.zip"));


					System.out.println("verifying...");

					transytResultsFile = transytDirectory.concat("results/");

					FileUtils.extractZipFile(transytDirectory.concat("/results.zip"), transytResultsFile);

					File checksumFile = new File(transytResultsFile.concat("/checksum.md5"));

					if (!checksumFile.exists()) {

						File folder = new File(transytResultsFile);
						File[] listOfFiles = folder.listFiles();

						boolean stop=false;

						int i = 0;  //create errors dictionary!!!

						//The following code will show different error and warning messages to merlin users depending on the error founded

						//						while (!stop && i<listOfFiles.length) {
						//							if (listOfFiles[i].getName().equals("1") ) {
						//								Workbench.getInstance().warn("Fail loading the model");
						//								stop = true;
						//								verify=false;
						//							}
						//							else if (listOfFiles[i].getName().equals("2") ){
						//								Workbench.getInstance().warn("CPLEX was not found");
						//								stop = true;
						//								verify=false;
						//							}
						//							else if (listOfFiles[i].getName().equals("3") ){
						//								Workbench.getInstance().warn("There is no Biomass reaction or its ID is incorrect");
						//								stop = true;
						//								verify=false;
						//							}
						//							else if (listOfFiles[i].getName().equals("4") ){
						//								Workbench.getInstance().warn("The protein name is incorrect");
						//								stop = true;
						//								verify=false;
						//							}
						//							else if (listOfFiles[i].getName().equals("5") ){
						//								Workbench.getInstance().warn("The output file name is incorrect");
						//								stop = true;
						//								verify=false;
						//							}
						//							else if (listOfFiles[i].getName().equals("6") ) {
						//								Workbench.getInstance().warn("One or more files are not correctly named");
						//								stop = true;
						//								verify=false;
						//							}
						//							i++;
						//						}
					}
					else if (verify) {
						//verify=verifyKeys();
						System.out.println("The result of the verification of md5 file was " + Boolean.toString(verify));
					}

					System.out.println( verify);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

			}
			else
				System.out.println("No dockerID attributed!");
		} 
		catch (Exception e) {
			e.printStackTrace();
			System.out.println("Error submitting files");

		}
	}
}




