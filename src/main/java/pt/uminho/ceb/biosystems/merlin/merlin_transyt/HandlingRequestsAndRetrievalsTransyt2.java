package pt.uminho.ceb.biosystems.merlin.merlin_transyt;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandlingRequestsAndRetrievalsTransyt2 {

//	private static final String URL = "http://transytmanager.rosalind.di.uminho.pt";
	private static final String URL = "http://rosalind.di.uminho.pt:8085/";

	final static Logger logger = LoggerFactory.getLogger(HandlingRequestsAndRetrievalsTransyt2.class);
	
	private final List<File> requiredFiles;

	public HandlingRequestsAndRetrievalsTransyt2(List<File> requiredFiles){

		this.requiredFiles = requiredFiles;
		

	}

	/**
	 * Method that makes a connection to the URL http://transytmanager.rosalind.di.uminho.pt and submit files there
	 * 
	 * @return docker is a {@code String} which represents the ID of the docker used
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public String postFiles() throws IOException, InterruptedException {

		String uploadUrl = URL.concat("/submit");


		String charset = "UTF-8";
		String param = "value";


		//	File binaryFile = new File("C:\\Users\\Davide\\Desktop\\roff2018.pdf");
		String boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.
		String CRLF = "\r\n"; // Line separator required by multipart/form-data.

		URL url = new URL(uploadUrl);

		URLConnection connection = url.openConnection();
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

		try (
				OutputStream output = connection.getOutputStream();
				PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
				) {
			for (File file : this.requiredFiles){

				// Send normal param.
				writer.append("--" + boundary).append(CRLF);
				writer.append("Content-Disposition: form-data; name=\"param\"").append(CRLF);
				writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF);
				writer.append(CRLF).append(param).append(CRLF).flush();

				writer.append("--" + boundary).append(CRLF);
				writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"").append(CRLF);
				writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF); // Text file itself must be saved in this charset!
				writer.append(CRLF).flush();
				Files.copy(file.toPath(), output);
				output.flush(); // Important before continuing with writer!
				writer.append(CRLF).flush();
			}

			writer.append("--" + boundary + "--").append(CRLF).flush();
		}
		catch (Exception e) {
			e.printStackTrace();
			logger.error("Error submitting files");

		}

		// Request is lazily fired whenever you need to obtain information about response.
		int responseCode = ((HttpURLConnection) connection).getResponseCode();


		//TimeUnit.SECONDS.sleep(5);

		BufferedReader in = new BufferedReader(new InputStreamReader(((HttpURLConnection) connection).getInputStream()));
		String html = "";
		String docker = null;
		
		logger.info(uploadUrl);
		if (responseCode==202)
		{
			while ((html = in.readLine()) != null){
				if (html.contains("workerID")) {
					String[] parts = html.split(":");
					docker = parts[1].replace("\"", "").trim();
				}
			}
		}

		else if (responseCode==503) {
			logger.error("The server cannot handle the submission due to capacity overload. Please try again later!");
			return null;
		}
		else if (responseCode==500) {
			logger.error("Something went wrong while processing the request, please try again");
			return null;
		}
		else {
			logger.error("Unkown error");
			return null;
		}
		
		logger.info("The docker {} was assigned to work", docker);
		return docker;
	}

	/**
	 * This method aims to get information about a given docker status in order to determine whether the submission is completed or not.
	 * 
	 * @param docker: the parameter is a {@code String} which represents the docker ID whose status will be analised
	 * @return Boolean: if the docker status is either 200 or 202 the method returns true or false, respectivelly, otherwise returns null
	 * @throws IOException
	 */
	public Boolean getStatus(String docker) throws IOException {

		String uploadUrl = URL.concat("/status");

		uploadUrl = uploadUrl.concat("/"+docker);

		URL url = new URL(uploadUrl);

		HttpURLConnection conn = (HttpURLConnection)url.openConnection();

		int responseCode = conn.getResponseCode();

		if (responseCode==200) {
			return true;
		}
		else if (responseCode==202) {
			return false;
		}
		return null;

	}

	/**
	 * Method that enables the download of the file related to the submission previously made by the user.
	 * @param docker: This parameter is a {@code String} which is the docker ID that the user has been used
	 * @param path: This parameter is a {@code String} with the output .tar.gz file
	 * @throws IOException 
	 */
	public boolean downloadFile(String docker, String path) throws IOException {

		try {
			String uploadUrl = URL.concat("/download");

			uploadUrl = uploadUrl.concat("/"+docker);

			URL downloadUrl = new URL(uploadUrl);

			HttpURLConnection conn = (HttpURLConnection)downloadUrl.openConnection();

			int responseCode = conn.getResponseCode();

			logger.info("The response code was {}", Integer.toString(responseCode));
			
			if (responseCode==200) {

				File f = new File(path.concat(".tar.gz"));

				FileUtils.copyURLToFile(downloadUrl, f);
				
				logger.info("The file was downloaded successfully.");
				return true;
			}
			else {
				logger.error("Error downloading the files.");
				return false;
			}


		} 
		catch (Exception e) {
			logger.error("Error downloading the files.");
			e.printStackTrace();
			return false;

		}
	}

	/**
	 * The function of this method is to close the connection previously established.
	 * @param docker : This parameter is a {@code String} which is the docker ID that the user has been used
	 */
	public void closeConnection(String docker){
		try {
			String uploadUrl = URL.concat("/closeConnection");

			uploadUrl = uploadUrl.concat("/"+docker);

			URL url = new URL(uploadUrl);

			//		int responseCode = ((HttpURLConnection) connection).getResponseCode();

			HttpURLConnection conn = (HttpURLConnection)url.openConnection();

			int responseCode = conn.getResponseCode();
						
			if (responseCode!=200) 
				logger.error("Error closing the connection for docker identified by: " + docker); 
			else 
				logger.info("The connection was closed successfully for docker identified by: " + docker); 

		} 
		catch (MalformedURLException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}


}
