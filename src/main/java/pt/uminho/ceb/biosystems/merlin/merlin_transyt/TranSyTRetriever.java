package pt.uminho.ceb.biosystems.merlin.merlin_transyt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
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
import java.util.HashMap;
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
import pt.uminho.ceb.biosystems.merlin.gui.datatypes.WorkspaceAIB;
import pt.uminho.ceb.biosystems.merlin.gui.datatypes.annotation.AnnotationCompartmentsAIB;
import pt.uminho.ceb.biosystems.merlin.gui.utilities.TimeLeftProgress;
import pt.uminho.ceb.biosystems.merlin.core.containers.model.CompartmentContainer;
import pt.uminho.ceb.biosystems.merlin.core.datatypes.WorkspaceEntity;
import pt.uminho.ceb.biosystems.merlin.core.datatypes.model.compartments.AnnotationCompartmentsGenes;
import pt.uminho.ceb.biosystems.merlin.core.utilities.Enumerators.SequenceType;
import pt.uminho.ceb.biosystems.merlin.processes.WorkspaceProcesses;
import pt.uminho.ceb.biosystems.merlin.processes.verifiers.CompartmentsVerifier;
import pt.uminho.ceb.biosystems.merlin.services.ProjectServices;
import pt.uminho.ceb.biosystems.merlin.services.model.ModelMetabolitesServices;
import pt.uminho.ceb.biosystems.merlin.services.model.ModelSequenceServices;
import pt.uminho.ceb.biosystems.merlin.utilities.io.FileUtils;


/**
 * @author 
 *
 */
@Operation(name="TranSyT",description="create transport reactions using TranSyT")
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
	private CompartmentContainer outsideCompartment;
	private CompartmentContainer insideCompartment;
	private CompartmentContainer membraneCompartment;
	private String url;
	//	private Boolean overrideOntologiesFilter;
	//	private TranSyTSupportedDatabases identifiersDatabase;
	private Map<String, String> inputs = new HashMap<>();

	final static Logger logger = LoggerFactory.getLogger(TranSyTRetriever.class);

	@Port(direction=Direction.INPUT, name="workspace",description="select workspace",validateMethod="checkNewProject", order = 1)
	public void setNewProject(WorkspaceAIB project) throws IOException, SQLException {}

	@Port(direction=Direction.INPUT, name="compounds' identifiers",description="please select the reference database of the compounds used in the model",
			order = 3)
	public void setIdentifiersDatabase(TranSyTSupportedDatabases identifiersDatabase) {
		this.inputs.put("reference_database", identifiersDatabase.toString());
	}

	@Port(direction=Direction.INPUT, name="override ontologies filter",description="select true to override TranSyT's ontologies filter", 
			advanced = true, order = 3)
	public void setOverrideFilter(boolean overrideOntologiesFilter) {
		this.inputs.put("override_ontologies_filter", overrideOntologiesFilter+"");
	}

	@Port(direction=Direction.INPUT, name="Reactions annotation alpha value",description="insert a number between 0 and 1", 
			advanced = true, defaultValue = "0.75", validateMethod="checkDecimalAbove0Below1", order = 4)
	public void setAlphaValue(double alpha) throws Exception{
		this.inputs.put("alpha", alpha+"");
	}

	@Port(direction=Direction.INPUT, name="Reactions annotation beta value",description="insert a number between 0 and 1", 
			advanced = true, defaultValue = "0.3", validateMethod="checkDecimalAbove0Below1", order = 5)
	public void setBetaValue(double beta) throws Exception{
		this.inputs.put("beta", beta+"");
	}

	@Port(direction=Direction.INPUT, name="Reactions annotation minimum hits penalty",
			description="Minimum hits to avoid penalty in taxonomy score calculations.", 
			advanced = true, defaultValue = "2", validateMethod="checkIntAbove0", order = 6)
	public void setMinHitsValue(int minHits) throws Exception{
		this.inputs.put("minimum_hits_penalty", minHits+"");
	}

	@Port(direction=Direction.INPUT, name="BLAST bit score threshold",
			description="Parameter used to filter the results of the NCBI Blast+ software.", 
			advanced = true, defaultValue = "50", validateMethod="checkIntAbove0", order = 7)
	public void setBitScoreValue(int bitScore) throws Exception{
		this.inputs.put("bitscore_threshold", bitScore+"");
	}

	@Port(direction=Direction.INPUT, name="Minimum query coverage threshold (%)",
			description="Parameter used to filter the results of the NCBI Blast+ software.", 
			advanced = true, defaultValue = "80", validateMethod="checkPercentage", order = 8)
	public void setQueryCovValue(double queryCov) throws Exception{
		this.inputs.put("query_coverage_threshold", queryCov+"");
	}

	@Port(direction=Direction.INPUT, name="BLAST e-value threshold (1e-x)",
			description="Parameter '-evalue' of the NCBI Blast+ software.", 
			advanced = true, defaultValue = "20", validateMethod="checkIntAbove0", order = 9)
	public void setEvalueThreshold(int evalueThreshold) throws Exception{
		this.inputs.put("blast_evalue_threshold", evalueThreshold+"");
	}

	@Port(direction=Direction.INPUT, name="Reactions annotation score threshold",
			description="Score above which a reaction is accepted.", 
			advanced = true, defaultValue = "0.75", validateMethod="checkDecimalAbove0Below1", order = 10)
	public void setScoreThreshold(double scoreThreshold) throws Exception{
		this.inputs.put("score_threshold", scoreThreshold+"");
	}

	@Port(direction=Direction.INPUT, name="Similarity score (%)",
			description="Parameter used to filter the results of the NCBI Blast+ software.", 
			advanced = true, defaultValue = "30", validateMethod="checkPercentage", order = 11)
	public void setSimScore(double simScore) throws Exception{
		this.inputs.put("similarity_score", simScore+"");
	}

	@Port(direction=Direction.INPUT, name="TC families annotation alpha value",description="insert a number between 0 and 1", 
			advanced = true, defaultValue = "0.4", validateMethod="checkDecimalAbove0Below1", order = 12)
	public void setFamiliesAlphaValue(double alphaFamily) throws Exception{
		this.inputs.put("alpha_families", alphaFamily+"");
	}

	@Port(direction=Direction.INPUT, name="E-value auto-accept threshold (1e-x)",
			description="The value below which all hits are accepted as possible transporters.", 
			advanced = true, defaultValue = "0", validateMethod="checkIntAbove0", order = 13)
	public void setAutoAccept(int autoAccept) throws Exception{
		this.inputs.put("auto_accept_evalue", autoAccept+"");
	}

	@Port(direction=Direction.INPUT, name="Accept top results (%)",
			description="Percentage of top blast results that should be accepted for each entry.", 
			advanced = true, defaultValue = "10", validateMethod="checkPercentage", order = 14)
	public void setTopPercent(double percentage) throws Exception{
		this.inputs.put("percent_accept", percentage+"");
	}

	@Port(direction=Direction.INPUT, name="E-value threshold method-1 (1e-x)",
			description="E-value above which all results should be disregarded by method-1.", 
			advanced = true, defaultValue = "50", validateMethod="checkIntAbove0", order = 15)
	public void setEvalThresh(int eval) throws Exception{
		this.inputs.put("limit_evalue_accept", eval+"");
	}

	@Port(direction=Direction.INPUT, name="Ignore method-2",
			description="select true to ignore method-2 of reactions annotation", 
			advanced = true, order = 16)
	public void setIgnoreMethod2(boolean ignoreMethod2) {
		this.inputs.put("ignore_method2", ignoreMethod2+"");
	}

	@Port(direction=Direction.INPUT, name="external compartment",description="name of the default external compartment", advanced=true, defaultValue = "auto", validateMethod="checkExternalCompartment", order = 44)
	public void setExternalCompartment(String compartment) throws Exception {}

	@Port(direction=Direction.INPUT, name="internal compartment",description="name of the default external compartment", advanced=true, defaultValue = "auto", validateMethod="checkInternalCompartment", order = 45)
	public void setInternalCompartment(String compartment) throws Exception {}

	@Port(direction=Direction.INPUT, name="membrane compartment",description="name of the default membrane compartment", advanced=true, defaultValue = "auto", validateMethod="checkMembraneCompartment", order = 46)
	public void setMembraneCompartment(String compartment) throws Exception {

		this.url = FileUtils.readTransytConfFile().get("host");

		try {
			transytDirectory = this.getTransytDirectory();

			this.startTime = GregorianCalendar.getInstance().getTimeInMillis();

			this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 0, 4, "submitting files...");

			boolean submitted = submitFiles();
			//boolean submitted = true;


			if (submitted && !this.cancel.get()) {

				this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 4, 4, "Rendering results...");

				logger.info("The files for TranSyT were submitted successfully");

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

		logger.info("Default interior compartment: " + this.insideCompartment);
		logger.info("Default exterior compartment: " + this.outsideCompartment);
		logger.info("Default membrane compartment: " + this.membraneCompartment);

		Map<Integer, AnnotationCompartmentsGenes> geneCompartment = null;

		AnnotationCompartmentsAIB c = null;

		for(WorkspaceEntity e: project.getDatabase().getAnnotations().getEntitiesList())
			if(e.getName().equalsIgnoreCase("compartments"))
				c=(AnnotationCompartmentsAIB) e;

		if(ProjectServices.isCompartmentalisedModel(this.project.getName()))
			geneCompartment = c.runCompartmentsInterface(c.getThreshold());
		
		
		//transytResultsFile = "C:\\Users\\Bisbii\\Desktop\\MERLIN\\merlin-project\\merlin-gui\\ws\\xfastidiosa_v4\\698414\\transyt\\results\\"; // REMOVE LATER
		
		
		ParamSpec[] paramsSpec = new ParamSpec[]{
				new ParamSpec("compartments", Map.class, geneCompartment, null),
				new ParamSpec("transytResultPath", String.class, transytResultsFile.concat("transyt.xml"), null),
				new ParamSpec("defaultInternalCompartment", CompartmentContainer.class, this.insideCompartment, null),
				new ParamSpec("defaultExternalCompartment", CompartmentContainer.class, this.outsideCompartment, null),
				new ParamSpec("defaultMembraneCompartment", CompartmentContainer.class, this.membraneCompartment, null),
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

				WorkspaceProcesses.createFaaFileWithDbIdsOnly(this.project.getName(), this.project.getTaxonomyID()); // method creates ".faa" files only if they do not exist
			} 
			catch (Exception e) {
				Workbench.getInstance().error(e);
				e.printStackTrace();
			}
		}
	}

	/**
	 * @param value
	 */
	public void checkDecimalAbove0Below1(double value) {

		if(value < 0)
			throw new IllegalArgumentException("Value should be above 0. Inserted: " + value);
		else if(value > 1)
			throw new IllegalArgumentException("Value should be below 1. Inserted: " + value);
	}

	/**
	 * @param value
	 */
	public void checkIntAbove0(int value) {

		if(value < 0)
			throw new IllegalArgumentException("Value should be above 0. Inserted: " + value);
	}

	/**
	 * @param value
	 */
	public void checkPercentage(double value) {

		if(value < 0)
			throw new IllegalArgumentException("Value should be above 0. Inserted: " + value);
		else if(value > 100)
			throw new IllegalArgumentException("Value should be below 100. Inserted: " + value);
	}

	/**
	 * @param compartment
	 * @throws Exception
	 */
	public void checkExternalCompartment(String compartment) throws Exception {

		this.outsideCompartment = CompartmentsVerifier.checkExternalCompartment(compartment, this.project.getName());

		if(this.outsideCompartment == null) {
			logger.warn("No external compartmentID defined!");
			//			Workbench.getInstance().warn("No external compartmentID defined!");
		}

	}

	/**
	 * @param compartment
	 * @throws Exception
	 */
	public void checkInternalCompartment(String compartment) throws Exception {

		this.insideCompartment = CompartmentsVerifier.checkInteriorCompartment(compartment, this.project.getName());

		if(this.insideCompartment == null) {
			logger.warn("No interior compartmentID defined!");
			//			Workbench.getInstance().warn("No interior compartmentID defined!");
		}

	}

	/**
	 * @param compartment
	 * @throws Exception
	 */
	public void checkMembraneCompartment(String compartment) throws Exception {

		this.membraneCompartment = CompartmentsVerifier.checkMembraneCompartment(compartment, this.project.getName(), this.project.isEukaryoticOrganism());

		if(this.membraneCompartment == null) {
			logger.warn("No membrane compartmentID defined!");
			//			Workbench.getInstance().warn("No membrane compartmentID defined!");
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

		HandlingRequestsAndRetrievalsTransyt post = new HandlingRequestsAndRetrievalsTransyt(requiredFiles, this.project.getTaxonomyID(), this.url);

		String submissionID = "";

		boolean verify = false;

		submissionID = post.postFiles(this.inputs);

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

				FileUtils.extractZipFile(this.transytDirectory.concat("/results.zip"), this.transytDirectory);

				File checksumFile = new File(transytResultsFile.concat("/checksum.md5"));

				if (!checksumFile.exists()) {
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

		File proteinFile2 = new File(workspaceFolder.concat("proteinGeneIds.faa"));

		File genomeFile = new File(this.transytDirectory.concat("genomeGeneIds.faa"));

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