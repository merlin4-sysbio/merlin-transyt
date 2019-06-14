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
import pt.uminho.ceb.biosystems.merlin.compartments.datatype.AnnotationCompartmentsGenes;
import pt.uminho.ceb.biosystems.merlin.core.datatypes.WorkspaceEntity;
import pt.uminho.ceb.biosystems.merlin.database.connector.databaseAPI.ModelAPI;
import pt.uminho.ceb.biosystems.merlin.database.connector.datatypes.Connection;
import pt.uminho.ceb.biosystems.merlin.services.ProjectServices;
import pt.uminho.ceb.biosystems.merlin.utilities.Enumerators.SequenceType;
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


	final static Logger logger = LoggerFactory.getLogger(TranSyTRetriever.class);


	@Port(direction=Direction.INPUT, name="new model",description="select the new model workspace",validateMethod="checkNewProject", order = 1)
	public void setNewProject(WorkspaceAIB project) throws IOException, SQLException {
		
		this.startTime = GregorianCalendar.getInstance().getTimeInMillis();

		this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 0, 4, "submiting files...");

		boolean submitted = submitFiles();
		
		if (submitted && !this.cancel.get()) {
			
			this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 4, 4, "integrating transport reactions...");

			String workspaceName = project.getName();

			try {

				Connection conn = new Connection(project.getDatabase().getDatabaseAccess());
				Statement statement = conn.createStatement();

				Map<Integer, AnnotationCompartmentsGenes> geneCompartment = null;

				AnnotationCompartmentsAIB c = null;

				for(WorkspaceEntity e: project.getDatabase().getAnnotations().getEntitiesList())
					if(e.getName().equalsIgnoreCase("compartments"))
						c=(AnnotationCompartmentsAIB) e;

				if(ProjectServices.isCompartmentalisedModel(workspaceName))
					geneCompartment = c.runCompartmentsInterface(c.getThreshold(), statement);

				ParamSpec[] paramsSpec = new ParamSpec[]{
						new ParamSpec("compartments", Map.class, geneCompartment, null),
						new ParamSpec("transytResultPath", String.class, transytResultsFile, null),
						new ParamSpec("workspace", WorkspaceAIB.class, project, null)
				};
				for (@SuppressWarnings("rawtypes") OperationDefinition def : Core.getInstance().getOperations()){
					if (def.getID().equals("operations.ModelTransporterstoIntegration.ID")){

						Workbench.getInstance().executeOperation(def, paramsSpec);
					}
				}

				conn.closeConnection();	


//				Workbench.getInstance().info("Transyt's transport reactions were integrated successfully");
			}
			catch(Exception ex){
				ex.printStackTrace();
			}
		}
		else if(this.cancel.get()) {
			Workbench.getInstance().warn("operation canceled!");
		}
		else {
			Workbench.getInstance().error("error while doing the operation! please try again");
		}
	}


	//////////////////////////ValidateMethods/////////////////////////////
	/**
	 * @param project
	 */
	public void checkNewProject(WorkspaceAIB project) {
		
		if(project == null) {

			throw new IllegalArgumentException("no worksapce selected!");
		}
		else {

			this.project = project;

			try {
				Connection conn = new Connection(project.getDatabase().getDatabaseAccess());
				Statement stmt = conn.createStatement();

				if(!ModelAPI.checkGenomeSequences(stmt,SequenceType.PROTEIN)) {
					throw new IllegalArgumentException("please set the project fasta ('.faa' or '.fna') files");
				}
				else if(this.project.getTaxonomyID()<0) {

					throw new IllegalArgumentException("please enter the taxonomic identification from NCBI taxonomy");
				}

				stmt.close();
				conn.closeConnection();

			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}
	}



	/////////////////////////////////////////////////////

	/**This method allows the submittion of the required files for TranSyT to work, the download of the results and the verification 
	 * of the md5 key
	 * @return
	 * @throws IOException 
	 * @throws SQLException 
	 */
	public boolean submitFiles() throws IOException, SQLException {

		Connection connection = new Connection(this.project.getDatabase().getDatabaseAccess());

		Statement statement = connection.createStatement();

		List<File> requiredFiles = creationOfRequiredFiles(statement); //the first element of the list is the protein file, 
		//the second is the file with the taxonomy ID and the third is the file with the metabolites

		HandlingRequestsAndRetrievalsTransyt post = new HandlingRequestsAndRetrievalsTransyt(requiredFiles);

		String docker = "";

		boolean verify = false;

		try {

			docker = post.postFiles();

			if(docker!=null) {

				try {
					logger.info("DockerID attributed: {}", docker);
					Boolean go = false;
					
					this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 1, 4, 
							"files submitted, waiting for results...");

					while (!go &&  !this.cancel.get()) {
						go = post.getStatus(docker);

						if(go == null) {  
							logger.error("Error!");
							post.closeConnection(docker);
							System.exit(1);
						}

						TimeUnit.SECONDS.sleep(3);
					}
					
					if(this.cancel.get())
						post.closeConnection(docker);
					
					this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 2, 4, "downloading transport reactions");

					if(!this.cancel.get())
						verify = post.downloadFile(docker, getWorkDirectory().concat("/"+TRANSYT_FILE_NAME).concat("/results"));
					else
						post.closeConnection(docker);
					
					FileUtils.extractZipFile(getWorkDirectory().concat("/"+TRANSYT_FILE_NAME+"/results.tar.gz"), getWorkDirectory().concat("/"+TRANSYT_FILE_NAME));

					this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, 3, 4, "verifying...");
					
					if (verify) {
						verify=verifyKeys();
						logger.info("The result of the verification of md5 file was {}", Boolean.toString(verify));
					}

					transytResultsFile = getWorkDirectory().concat("/"+TRANSYT_FILE_NAME).concat("/results/transyt.xml");
					post.closeConnection(docker);
					return verify;
				}
				catch (Exception e) {
					post.closeConnection(docker);
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


	private  String getWorkDirectory() {
		String database = this.project.getDatabase().getDatabaseName();

		Long taxonomyID= this.project.getTaxonomyID();

		String path = FileUtils.getWorkspaceTaxonomyFolderPath(database, taxonomyID);

		return path;

	}

	private boolean verifyKeys() throws IOException, NoSuchAlgorithmException {

		String path = getWorkDirectory().concat("/"+TRANSYT_FILE_NAME+"/results");

		MessageDigest md5Digest = MessageDigest.getInstance("MD5");

		File file = new File(path.concat("/transyt.xml"));

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
	//	public void zipFiles(String source, String output_file) throws IOException{
	//		FileUtils.createZipFile(source, output_file, 5);
	//	}
	//
	//	public void unzipFiles(String source, String destination) throws IOException {
	//		FileUtils.extractZipFile(source, destination);
	//	}

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

	private List<File> creationOfRequiredFiles(Statement statement) {

		try {

			List<File> requiredFiles = new ArrayList<>();

			File transytFile = new File(getWorkDirectory().concat("/transyt"));

			if (transytFile.exists()) {
				transytFile.delete();
			} 

			transytFile.mkdir(); //creation of a directory to put the required files
			
			File proteinFile2 = new File(getWorkDirectory().concat("/").concat("protein.faa"));
			
			File genomeFile = new File(getWorkDirectory().concat("/").concat("transyt/genome.faa"));
			
			Files.copy(proteinFile2.toPath(), genomeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			
//			org.apache.commons.io.FileUtils.copyDirectory(proteinFile2, genomeFile);
			
//			String proteinFileName = "genome.faa";
//
//			File proteinFile = new File(g);

			requiredFiles.add(0,genomeFile);

			File taxIDFile = new File(getWorkDirectory().concat("/transyt").concat("/taxID.txt"));

			FileWriter writer = new FileWriter(taxIDFile);

			writer.append(Long.toString(this.project.getTaxonomyID()));

			writer.close();

			requiredFiles.add(1,taxIDFile);

			File metabolitesFile = new File(getWorkDirectory().concat("/transyt").concat("/metabolites.txt"));

			List<String> metabolites = getQueries(statement);

			saveWordsInFile(getWorkDirectory().concat("/transyt").concat("/metabolites.txt"),metabolites);

			requiredFiles.add(2,metabolitesFile);

			return requiredFiles;

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} //write the taxonomy ID in a file
		catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

	}

	public static List<String> getQueries(Statement statement) throws SQLException {

		List<String> ret = new ArrayList<>();

		String query = "SELECT DISTINCT(kegg_id) FROM model_compound INNER JOIN model_stoichiometry ON idcompound = compound_idcompound INNER JOIN model_reaction ON idreaction = reaction_idreaction WHERE inModel;";

		ResultSet rs = statement.executeQuery(query);

		while(rs.next())	
			ret.add(rs.getString(1));

		return ret;
	}

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





	/**
	 * @return the progress
	 */
	@Progress(progressDialogTitle = "TranSyT annotation", modal = false, workingLabel = "performing TranSyT annotation", preferredWidth = 400, preferredHeight=300)
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

