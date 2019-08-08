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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
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
import pt.uminho.ceb.biosystems.merlin.aibench.datatypes.WorkspaceTableAIB;
import pt.uminho.ceb.biosystems.merlin.aibench.datatypes.annotation.AnnotationCompartmentsAIB;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.AIBenchUtils;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.TimeLeftProgress;
import pt.uminho.ceb.biosystems.merlin.biocomponents.io.Enumerators.SBMLLevelVersion;
import pt.uminho.ceb.biosystems.merlin.biocomponents.io.writers.SBMLWriter;
import pt.uminho.ceb.biosystems.merlin.compartments.datatype.AnnotationCompartmentsGenes;
import pt.uminho.ceb.biosystems.merlin.core.datatypes.WorkspaceEntity;
import pt.uminho.ceb.biosystems.merlin.core.datatypes.WorkspaceGenericDataTable;
import pt.uminho.ceb.biosystems.merlin.database.connector.databaseAPI.ModelAPI;
import pt.uminho.ceb.biosystems.merlin.database.connector.datatypes.Connection;
import pt.uminho.ceb.biosystems.merlin.merlin_biocoiso.BiocoisoUtilities;
import pt.uminho.ceb.biosystems.merlin.merlin_biocoiso.HandlingRequestsAndRetrievalsBiocoiso;
import pt.uminho.ceb.biosystems.merlin.merlin_biocoiso.datatypes.ValidationBiocoisoAIB;
import pt.uminho.ceb.biosystems.merlin.services.ProjectServices;
import pt.uminho.ceb.biosystems.merlin.services.model.ModelGenesServices;
import pt.uminho.ceb.biosystems.merlin.utilities.Enumerators.SequenceType;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;
import pt.uminho.ceb.biosystems.mew.utilities.datastructures.pair.Pair;


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


	final static Logger logger = LoggerFactory.getLogger(TranSyTRetriever.class);


	@Port(direction=Direction.INPUT, name="new model",description="select the new model workspace",validateMethod="checkNewProject", order = 1)
	public void setNewProject(WorkspaceAIB project) throws IOException, SQLException {
		
		this.project = project;
		
		creationOfRequiredFiles();

		this.startTime = GregorianCalendar.getInstance().getTimeInMillis();

		this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 0, 4, "submitting files...");

		
		boolean submitted = false; //submitFiles();

		if (submitted && !this.cancel.get()) {


			this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 4, 4, "Rendering results...");

			logger.info("The files for BioCoISO were submitted successfully");

			Workbench.getInstance().info("The files for BioCoISO were submitted successfully");

			executeOperation();
		}
		else if(this.cancel.get()) {
			Workbench.getInstance().warn("operation canceled!");
		}
		else {
			Workbench.getInstance().error("error while doing the operation! please try again");

			executeOperation();

		}

	}


private void executeOperation() throws IOException, ParseException {
		
		int table_number = this.project.getDatabase().getValidation().getEntities().size() + 1;

		String name = "BioCoISO_" + Integer.toString(table_number);

		String[] columnsName = new String[] {"info","metabolite","flux", "children", "description"};

		WorkspaceTableAIB table = new WorkspaceTableAIB(name, columnsName , this.project.getName(), new Connection(this.project.getDatabase().getDatabaseAccess()));

		Pair<WorkspaceGenericDataTable, Map<?,?>> filledTableAndNextLevel = this.createDataTable(this.getWorkDirectory().concat("/biocoiso/results.json"), Arrays.asList(columnsName), this.project.getName(), name);

		ValidationBiocoisoAIB biocoiso = new ValidationBiocoisoAIB(table, name, filledTableAndNextLevel.getB());

		Connection connection = new Connection(this.msqlmt);

		biocoiso.setConnection(connection); 

		biocoiso.setWorkspace(this.project);

		biocoiso.setMainTableData(filledTableAndNextLevel.getA());

		biocoiso.setData(filledTableAndNextLevel.getA());

		ArrayList<WorkspaceEntity> newList = new ArrayList<WorkspaceEntity>();

		for (WorkspaceEntity entity : this.project.getDatabase().getValidation().getEntities()) {
			newList.add(entity);
		}

		newList.add(biocoiso);

		this.project.getDatabase().getValidation().setEntities(newList);
		
	}

	//////////////////////////ValidateMethods/////////////////////////////
	/**
	 * @param project
	 */
	public void checkNewProject(String workspaceName) {

		if(workspaceName == "") {

			throw new IllegalArgumentException("no workspace selected!");
		}
		else {

			this.project = AIBenchUtils.getProject(workspaceName);;

			try {

				if(!ModelGenesServices.checkGenomeSequences(workspaceName, SequenceType.PROTEIN)) {
					throw new IllegalArgumentException("please set the project fasta ('.faa' or '.fna') files");
				}
				else if(this.project.getTaxonomyID()<0) {

					throw new IllegalArgumentException("please enter the taxonomic identification from NCBI taxonomy");
				}

			} catch (Exception e) {
				Workbench.getInstance().error(e);
				e.printStackTrace();
			}
		}
	}



	/////////////////////////////////////////////////////

	/**This method allows the submission of the required files for BioCoISO into the web server, the download of the results and the verification 
	 * of the md5 key as well as show error and warning messages
	 * @return boolean informing whether the submission went well or not.
	 * @throws Exception 
	 */
	public boolean submitFiles() throws Exception {

		List<File> requiredFiles = creationOfRequiredFiles();

		if (requiredFiles == null) {
			return false;
		}

		HandlingRequestsAndRetrievalsBiocoiso post = new HandlingRequestsAndRetrievalsBiocoiso(requiredFiles);

		String submissionID = "";

		boolean verify = false;

		try {

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
							logger.error("Error!");
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


					this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 2, 4, "downloading BioCoISO results");

					if(!this.cancel.get())
						verify = post.downloadFile(submissionID, getWorkDirectory().concat("/"+BIOCOISO_FILE_NAME).concat("/results.zip"));


					this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 3, 4, "verifying...");

					biocoisoResultsFile = getWorkDirectory().concat("/"+BIOCOISO_FILE_NAME).concat("/results/");

					FileUtils.extractZipFile(getWorkDirectory().concat("/"+BIOCOISO_FILE_NAME).concat("/results.zip"), biocoisoResultsFile);

					File checksumFile = new File(biocoisoResultsFile.concat("/checksum.md5"));

					if (!checksumFile.exists()) {

						File folder = new File(biocoisoResultsFile);
						File[] listOfFiles = folder.listFiles();

						boolean stop=false;

						int i = 0;

						//The following code will show different error and warning messages to merlin users depending on the error founded

						while (!stop && i<listOfFiles.length) {
							if (listOfFiles[i].getName().equals("1") ) {
								Workbench.getInstance().warn("Fail loading the model");
								stop = true;
								verify=false;
							}
							else if (listOfFiles[i].getName().equals("2") ){
								Workbench.getInstance().warn("CPLEX was not found");
								stop = true;
								verify=false;
							}
							else if (listOfFiles[i].getName().equals("3") ){
								Workbench.getInstance().warn("There is no Biomass reaction or its ID is incorrect");
								stop = true;
								verify=false;
							}
							else if (listOfFiles[i].getName().equals("4") ){
								Workbench.getInstance().warn("The protein name is incorrect");
								stop = true;
								verify=false;
							}
							else if (listOfFiles[i].getName().equals("5") ){
								Workbench.getInstance().warn("The output file name is incorrect");
								stop = true;
								verify=false;
							}
							else if (listOfFiles[i].getName().equals("6") ) {
								Workbench.getInstance().warn("One or more files are not correctly named");
								stop = true;
								verify=false;
							}
							i++;
						}
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
			else
				logger.error("No dockerID attributed!");
		} 
		catch (Exception e) {
			e.printStackTrace();
			logger.error("Error submitting files");

		}
		return verify;
	}

	/**
	 * This method gives the name of the current working directory
	 * @return a {@code String} with the path for the working directory
	 */
	private  String getWorkDirectory() {
		String database = this.project.getDatabase().getDatabaseName();

		Long taxonomyID= this.project.getTaxonomyID();

		String path = FileUtils.getWorkspaceTaxonomyFolderPath(database, taxonomyID);

		return path;

	}

	/**
	 * This method verifies the key in the md5 file and checks whether the results were corrupted or not.
	 * @return boolean informing whether the results were corrupted or not.
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	private boolean verifyKeys() throws IOException, NoSuchAlgorithmException {

		String path = getWorkDirectory().concat("/"+BIOCOISO_FILE_NAME+"/results");

		MessageDigest md5Digest = MessageDigest.getInstance("MD5");

		File file = new File(path.concat("/result.csv"));

		String checksum = getFileChecksum(md5Digest, file);

		String key = readWordInFile(path.concat("/checksum.md5"));

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
	 * This method generates the required files for BioCoISO to run. A file with the biomass reaction id, another with the protein's name and the model.
	 * @return List<File> with the required files.
	 * @throws Exception
	 */
	private List<File> creationOfRequiredFiles() throws Exception {

		List<File> requiredFiles = new ArrayList<>();

		File biocoisoFolder = new File(getWorkDirectory().concat("/biocoiso"));

		if(biocoisoFolder.exists()) {
			FileUtils.deleteDirectory(biocoisoFolder);
		}

		biocoisoFolder.mkdir(); //creation of a directory to put the required files


		SBMLWriter sBMLWriter = new SBMLWriter(this.project.getName(), this.msqlmt, 
				biocoisoFolder.toString().concat("/model.xml"),
				project.getName(), 
				ProjectServices.isCompartmentalisedModel(this.project.getDatabase().getDatabaseName()), 
				false,
				"e-Biomass", 
				SBMLLevelVersion.L2V1);

		sBMLWriter.getDataFromDatabase();

		sBMLWriter.toSBML(true);


		saveWordInFile(biocoisoFolder.toString().concat("/reaction.txt"), this.reaction);

		saveWordInFile(biocoisoFolder.toString().concat("/level.txt"), this.level);

		File reactionFile = new File(biocoisoFolder.toString().concat("/reaction.txt"));

		File levelFile = new File(biocoisoFolder.toString().concat("/level.txt"));


		File modelFile = new File(biocoisoFolder.toString().concat("/model.xml"));

		if (modelFile.exists() && reactionFile.exists() && levelFile.exists()) {

			requiredFiles.add(reactionFile);

			requiredFiles.add(levelFile);

			requiredFiles.add(modelFile);

			return requiredFiles;

		}
		else
			return null;




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
	 * This method creates the data table with the results. This table will be rendered in BioCoISO's view.
	 * @param file
	 * @param columnsNames
	 * @param name
	 * @param windowName
	 * @return WorkspaceGenericDataTable with the results.
	 * @throws IOException
	 * @throws ParseException 
	 */

	public Pair<WorkspaceGenericDataTable, Map<?,?>> createDataTable(String file, List<String> columnsNames, String name, String windowName) throws IOException, ParseException {

		JSONParser jsonParser = new JSONParser();

		try (FileReader reader = new FileReader(file))
		{
			//Read JSON file
			Object obj = (JSONObject) jsonParser.parse(reader);

			JSONObject jo = (JSONObject) obj; 
			this.resultMap = (Map<?, ?>) jo; //level 1
			Pair<WorkspaceGenericDataTable, Map<?,?>> tableAndNextLevel = BiocoisoUtilities.tableCreator(resultMap, name, windowName, "M_fictitious");
			
			return tableAndNextLevel;
		}}
	

	
	/**
	 * @return the progress
	 */
	@Progress(progressDialogTitle = "BioCoISO", modal = false, workingLabel = "BioCoISO is running...", preferredWidth = 400, preferredHeight=300)
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