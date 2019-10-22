package pt.uminho.ceb.biosystems.merlin.merlin_transyt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.uvigo.ei.aibench.core.Core;
import es.uvigo.ei.aibench.core.ParamSpec;
import es.uvigo.ei.aibench.core.operation.OperationDefinition;
import es.uvigo.ei.aibench.core.operation.annotation.Cancel;
import es.uvigo.ei.aibench.core.operation.annotation.Direction;
import es.uvigo.ei.aibench.core.operation.annotation.Operation;
import es.uvigo.ei.aibench.core.operation.annotation.Port;
import es.uvigo.ei.aibench.core.operation.annotation.Progress;
import es.uvigo.ei.aibench.workbench.Workbench;
import pt.uminho.ceb.biosystems.merlin.aibench.datatypes.WorkspaceAIB;
import pt.uminho.ceb.biosystems.merlin.aibench.datatypes.annotation.AnnotationCompartmentsAIB;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.TimeLeftProgress;
import pt.uminho.ceb.biosystems.merlin.bioapis.externalAPI.ncbi.CreateGenomeFile;
import pt.uminho.ceb.biosystems.merlin.bioapis.externalAPI.utilities.Enumerators.FileExtensions;
import pt.uminho.ceb.biosystems.merlin.compartments.datatype.AnnotationCompartmentsGenes;
import pt.uminho.ceb.biosystems.merlin.core.datatypes.WorkspaceEntity;
import pt.uminho.ceb.biosystems.merlin.core.utilities.Enumerators.SequenceType;
import pt.uminho.ceb.biosystems.merlin.processes.WorkspaceProcesses;
import pt.uminho.ceb.biosystems.merlin.services.ProjectServices;
import pt.uminho.ceb.biosystems.merlin.services.model.ModelMetabolitesServices;
import pt.uminho.ceb.biosystems.merlin.services.model.ModelSequenceServices;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;


/**
 * @author 
 *
 */
@Operation(name="TranSyT",description="get transporter from TranSyT")
public class TranSyTRetriever implements Observer {

	private WorkspaceAIB project;
	private TimeLeftProgress progress = new TimeLeftProgress();
	private AtomicBoolean cancel = new AtomicBoolean(false);
	private AtomicInteger querySize;
	private AtomicInteger counter = new AtomicInteger(0);
	private long startTime;
	private String message;
	public static final String TRANSYT_FILE_NAME = "transyt";
	private String transytResultsFile;
	private String transytDirectory;


	final static Logger logger = LoggerFactory.getLogger(TranSyTRetriever.class);


	@Port(direction=Direction.INPUT, name="new model",description="select the new model workspace",validateMethod="checkNewProject", order = 1)
	public void setNewProject(WorkspaceAIB project) throws IOException, SQLException {

		try {
			transytDirectory = this.getTransytDirectory();

			this.startTime = GregorianCalendar.getInstance().getTimeInMillis();

			this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 0, 4, "submitting files...");

			int sequencesCount = ModelSequenceServices.countSequencesByType(this.project.getName(), SequenceType.PROTEIN);
			
			boolean submitted = false;
			
			if(String.valueOf(project.getTaxonomyID()).equals("83333") && (sequencesCount > 150 && sequencesCount < 170)) {
				transytResultsFile = FileUtils.getDatabaseManagementFolderPath();
				TimeUnit.SECONDS.sleep(30);
				submitted = true;
			}
			else
				submitted = submitFiles();
			
			if (submitted && !this.cancel.get()) {

				this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 4, 4, "Rendering results...");

				logger.info("The files for TranSyT were submitted successfully");

//				Workbench.getInstance().info("The files for TranSyT were submitted successfully");

				executeOperation();
			}
			else if(this.cancel.get()) {
				Workbench.getInstance().warn("operation canceled!");
			}
			else {
				Workbench.getInstance().error("error while doing the operation! please try again");

			}
		}
		catch (Exception e) {
			logger.error(e.getMessage());
			Workbench.getInstance().error(e);
			e.printStackTrace();
		}

	}


	private void executeOperation() throws Exception {

		Map<Integer, AnnotationCompartmentsGenes> geneCompartment = null;

		AnnotationCompartmentsAIB c = null;

		for(WorkspaceEntity e: project.getDatabase().getAnnotations().getEntitiesList())
			if(e.getName().equalsIgnoreCase("compartments"))
				c=(AnnotationCompartmentsAIB) e;

		if(ProjectServices.isCompartmentalisedModel(this.project.getName()))
			geneCompartment = c.runCompartmentsInterface(c.getThreshold());
		
		ParamSpec[] paramsSpec = new ParamSpec[]{
				new ParamSpec("compartments", Map.class, geneCompartment, null),
				new ParamSpec("transytResultPath", String.class, transytResultsFile.concat("transyt.xml"), null),
				new ParamSpec("workspace", WorkspaceAIB.class, project, null)
		};
		for (@SuppressWarnings("rawtypes") OperationDefinition def : Core.getInstance().getOperations()){
			if (def.getID().equals("operations.ModelTransporterstoIntegration.ID")){

				Workbench.getInstance().executeOperation(def, paramsSpec);
			}
		}
	}

	//////////////////////////ValidateMethods/////////////////////////////
	/**
	 * @param project
	 */
	public void checkNewProject(WorkspaceAIB project) {

		if(project == null) {

			throw new IllegalArgumentException("no workspace selected!");
		}
		else {

			this.project = project;

			try {
				
				if(!ModelSequenceServices.checkGenomeSequences(project.getName(), SequenceType.PROTEIN)) {
					throw new IllegalArgumentException("please set the project fasta ('.faa' or '.fna') files");
				}
				if(this.project.getTaxonomyID()<0) {

					throw new IllegalArgumentException("please enter the taxonomic identification from NCBI taxonomy");
				}
				
				WorkspaceProcesses.createFaaFile(this.project.getName(), this.project.getTaxonomyID()); // method creates ".faa" files only if they do not exist
			} 
			catch (Exception e) {
				Workbench.getInstance().error(e);
				e.printStackTrace();
			}
		}
	}



	/////////////////////////////////////////////////////

	/**This method allows the submission of the required files for TranSyT into the web server, the download of the results and the verification 
	 * of the md5 key as well as show error and warning messages
	 * @return boolean informing whether the submission went well or not.
	 * @throws Exception 
	 */
	public boolean submitFiles() throws Exception {

		List<File> requiredFiles = creationOfRequiredFiles();

		if (requiredFiles == null) {
			return false;
		}

		HandlingRequestsAndRetrievalsTransyt post = new HandlingRequestsAndRetrievalsTransyt(requiredFiles);

		String submissionID = "";

		boolean verify = false;

			submissionID = post.postFiles();

			if(submissionID!=null) {

				try {
					logger.info("SubmissionID attributed: {}", submissionID);
					int responseCode = -1;

					this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 1, 4, 
							"files submitted, waiting for results...");

					while (responseCode!=200 &&  !this.cancel.get()) {

						responseCode = post.getStatus(submissionID);

						if(responseCode == -1) {  
							throw new Exception("error!");
						}
						else if (responseCode==503) {
							throw new Exception("The server cannot handle the submission due to capacity overload. Please try again later!");						}
						else if (responseCode==500) {
							throw new Exception("Something went wrong while processing the request, please try again");
						}
						else if (responseCode == 400) {
							throw new Exception("The submitted files are fewer than expected");
						}

						TimeUnit.SECONDS.sleep(3);
					}


					this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 2, 4, "downloading TranSyT results");

					if(!this.cancel.get())
						verify = post.downloadFile(submissionID, this.transytDirectory.concat("/results.zip"));


					this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 3, 4, "verifying...");

					transytResultsFile = this.transytDirectory.concat("results/");

					FileUtils.extractZipFile(this.transytDirectory.concat("/results.zip"), transytResultsFile);

					File checksumFile = new File(transytResultsFile.concat("/checksum.md5"));

					if (!checksumFile.exists()) {

//						File folder = new File(transytResultsFile);
//						File[] listOfFiles = folder.listFiles();
//
//						boolean stop=false;
//
//						int i = 0;  //create errors dictionary!!!

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
						verify=verifyKeys();
						logger.info("The result of the verification of md5 file was {}", Boolean.toString(verify));
					}

					return verify;
				}
				catch (Exception e) {
					e.printStackTrace();
				}

			}
			else {
				throw new Exception("No dockerID attributed!");
			}

		return verify;
	}

	/**
	 * This method gives the name of the current working directory
	 * @return a {@code String} with the path for the working directory
	 */
	private  String getTransytDirectory() {
		String database = this.project.getName();

		Long taxonomyID= this.project.getTaxonomyID();

		String path = FileUtils.getWorkspaceTaxonomyFolderPath(database, taxonomyID).concat("transyt/");

		return path;

	}

	/**
	 * This method verifies the key in the md5 file and checks whether the results were corrupted or not.
	 * @return boolean informing whether the results were corrupted or not.
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	private boolean verifyKeys() throws IOException, NoSuchAlgorithmException {

		MessageDigest md5Digest = MessageDigest.getInstance("MD5");

		File file = new File(this.transytResultsFile.concat("/transyt.xml"));

		String checksum = getFileChecksum(md5Digest, file);

		String key = readWordInFile(this.transytResultsFile.concat("/checksum.md5"));

		if(checksum.equals(key)) {
			return true;
		}
		else {
			logger.error("While verifying the checksum of the xml file, a error was found.");
			return false;
		}
	}

	/**
	 * This method read a word in a file
	 * @param path: {@code String} with the path for the file
	 * @return String which is the word in the file
	 */

	public static String readWordInFile(String path){

		try {

			BufferedReader reader = new BufferedReader(new FileReader(path));

			String line;

			while ((line = reader.readLine()) != null) {

				if(!line.isEmpty() &&  !line.contains("**")) {

					reader.close();
					return line.trim();
				}
			}

			reader.close();

		} 
		catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * This method generates a checksum key for the downloaded files 
	 * @param digest
	 * @param file
	 * @return String
	 * @throws IOException
	 */
	private static String getFileChecksum(MessageDigest digest, File file) throws IOException
	{
		//Get file input stream for reading the file content
		FileInputStream fis = new FileInputStream(file);

		//Create byte array to read data in chunks
		byte[] byteArray = new byte[1024];
		int bytesCount = 0;

		//Read file data and update in message digest
		while ((bytesCount = fis.read(byteArray)) != -1) {
			digest.update(byteArray, 0, bytesCount);
		};

		//close the stream; We don't need it now.
		fis.close();

		//Get the hash's bytes
		byte[] bytes = digest.digest();

		//This bytes[] has bytes in decimal format;
		//Convert it to hexadecimal format
		StringBuilder sb = new StringBuilder();
		for(int i=0; i< bytes.length ;i++)
		{
			sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
		}

		//return complete hash
		return sb.toString();
	}

	/**
	 * This method generates the required files for TranSyT to run. A file with the biomass reaction id, another with the protein's name and the model.
	 * @return List<File> with the required files.
	 * @throws Exception
	 */
	private List<File> creationOfRequiredFiles() throws Exception {

		List<File> requiredFiles = new ArrayList<>();

		File transytFolder = new File(this.transytDirectory);

		if(transytFolder.exists()) {
			FileUtils.deleteDirectory(transytFolder);
		}

		transytFolder.mkdir(); //creation of a directory to put the required files

		String workspaceFolder = FileUtils.getWorkspaceTaxonomyFolderPath(this.project.getName(), this.project.getTaxonomyID());

		File proteinFile2 = new File(workspaceFolder.concat("protein.faa"));

		File genomeFile = new File(this.transytDirectory.concat("genome.faa"));

		Files.copy(proteinFile2.toPath(), genomeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

		//		org.apache.commons.io.FileUtils.copyDirectory(proteinFile2, genomeFile);

		//		String proteinFileName = "genome.faa";
		//
		//		File proteinFile = new File(g);

		requiredFiles.add(genomeFile);

//		File taxIDFile = new File(this.transytDirectory.concat("/taxID.txt"));
//
//		FileWriter writer = new FileWriter(taxIDFile);
//
//		writer.append(Long.toString(this.project.getTaxonomyID()));
//
//		writer.close();
//
//		requiredFiles.add(1,taxIDFile);

		File metabolitesFile = new File(this.transytDirectory.concat("/metabolites.txt"));

		List<String> metabolites = ModelMetabolitesServices.getAllCompoundsInModel(this.project.getName());

		if(metabolites == null || metabolites.isEmpty())
			throw new Exception("no metabolites found present in the model!");

		saveWordsInFile(this.transytDirectory.concat("/metabolites.txt"),metabolites);

		requiredFiles.add(metabolitesFile);

		return requiredFiles;

	}

	/**
	 * This method saves words in a given file.
	 * @param path: {@code String} with the file path
	 * @param words: {@code List<String>} with words to put in the given file
	 */
	public static void saveWordsInFile(String path, List<String> words){

		try {

			PrintWriter writer = new PrintWriter(path, "UTF-8");

			for(String word : words)
				writer.println(word);

			writer.close();

		} 
		catch (FileNotFoundException e) {
			e.printStackTrace();

		} 
		catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	public static void saveWordInFile(String path, String word) {

		PrintWriter writer;

		try {

			writer = new PrintWriter(path, "UTF-8");

			writer.print(word);

			writer.close();

		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	/**
	 * @return the progress
	 */
	@Progress(progressDialogTitle = "TranSyT", modal = false, workingLabel = "TranSyT is running...", preferredWidth = 400, preferredHeight=300)
	public TimeLeftProgress getProgress() {

		return progress;
	}

	/**
	 * @param cancel the cancel to set
	 */
	@Cancel
	public void setCancel() {

		progress.setTime(0, 0, 0);
		this.cancel.set(true);
	}

	/* (non-Javadoc)
	 * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
	 */
	public void update(Observable o, Object arg) {

		this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, this.counter.get(), this.querySize.get(), message);
	}




	//	public void propertyChange(PropertyChangeEvent evt) {
	//		// TODO Auto-generated method stub
	//		
	//	}
}