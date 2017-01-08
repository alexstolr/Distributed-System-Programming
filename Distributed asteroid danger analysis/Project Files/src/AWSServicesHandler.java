import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.sqs.model.Message;

public class AWSServicesHandler {
	private static PropertiesCredentials credentials;

	private EC2Handler ec2Client;
	private S3Handler s3Client;
	private SQSHandler sqsClient;

	public AWSServicesHandler(PropertiesCredentials _credentials) throws FileNotFoundException, IOException {
		credentials = _credentials;
		System.out.println("****");
		//System.out.println("Your AWS Access Key:     " + credentials.getAWSAccessKeyId());
		initLocalClient();
		System.out.println("**** \n");
	}

	/**
	 * Initiates local aws clients
	 */
	private void initLocalClient() {
		System.out.println("Initiating local AWS clients.");
		ec2Client = new EC2Handler(credentials);
		s3Client = new S3Handler(credentials);
		sqsClient = new SQSHandler(credentials);
	}

	/**
	 * Uploads file to S3 bucket.
	 * 
	 * @param bucketName
	 * @param inputFile
	 */
	public void uploadFileToBucket(String callerName, String bucketName, File inputFile) {
		s3Client.uploadFileToBucket(callerName, bucketName, inputFile);
	}

	/**
	 * initiates a SQS queue.
	 * 
	 * @return url of the SQS queue created.
	 */
	public String initSQSQueue(String queueName) {
		return sqsClient.initSQSQueue(queueName);
	}

	/**
	 * Starts manager (EC2)
	 * 
	 * @param type
	 *            - type of instance (e.g. "Manager", "Worker")
	 * @param numOfInstances
	 *            - number of instances to be started
	 */
	public void startManager(String type, int numOfInstances) {
		ec2Client.startManager("Manager", type, numOfInstances);
	}

	/**
	 * Sends message message to SQS with given URL
	 * 
	 * @param sqsUrl
	 * @param msg
	 */
	public void sendMsgToSqs(String callerName, String sqsUrl, String keys[], String values[]) {
		sqsClient.sendMsgToSqs(callerName, sqsUrl, keys, values);
	}

	public void printMsgData(String callerName, Message msg) {
		sqsClient.printMsgData(callerName, msg);
	}

	public void uploadStringFile(String callerName, String bucketName, String content, String fileName) {
		s3Client.uploadStringFile(callerName, bucketName, content, fileName);
	}

	/**
	 * Checks if message with Summary file location has arrived to SQS
	 * 
	 * @param sqsUrl
	 * @return
	 */
	public Message checkIfGotMsgToSqs(String callerName, String sqsUrl, String key) {
		return sqsClient.checkIfGotMsgToSqs(callerName, sqsUrl, key);
	}

	/**
	 * Dummy
	 * 
	 * @param bucketName
	 * @param inputFile
	 */
	public void dummySummarytoS3(String callerName, String bucketName, File inputFile) {
		uploadFileToBucket(callerName, bucketName, inputFile);
	}

	/**
	 * download Summary file and parse to an HTML output file.
	 * 
	 * @param outputFileName
	 * @return
	 * @throws IOException
	 */
	public String downloadFileFromS3(String caller, String fileName, String bucketName) throws IOException {
		return s3Client.downloadFile(caller, fileName, bucketName);
		// Delete input file
		// Download Summary file and delete it
	}

	public void deleteFileFromS3(String callerName, String fileName, String bucketName) {
		s3Client.deleteFileFromBucket(callerName, bucketName, fileName);
	}

	public Message getMsgFromSQS(String callerName, String url) {
		return sqsClient.getMsg(callerName, url);
	}

	public void deleteMsgFromSQS(String caller, String nameOfSqs, String receiptHandle) {
		sqsClient.deleteMsg(caller, nameOfSqs, receiptHandle);
	}

	public int countActiveWorkers() {
		return ec2Client.countActiveWorkers();
	}
	
	public int countActiveAndPendingWorkers() {
		return ec2Client.countActiveAndPendingWorkers();
	}

	public void startWorkers(String jarName, int numOfInstances) {
		ec2Client.startInstace(jarName, "Worker", numOfInstances);
	}

	public void setMessageVisibility(String url, String receiptHandle, int timeToBeInvisible) {
		sqsClient.changeMessageVisibility(url, receiptHandle, timeToBeInvisible);

	}

	public List<Message> getMsgList(String url) {
		return sqsClient.getMsgList(url);
	}

	/**
	 * Deletes the queue and returns a list of all messages from the Queue
	 * 
	 * @param url
	 * @return list of messages
	 */
	public List<Message> deleteQueueWithMessages(String url) {
		List<Message> messages = sqsClient.getMsgList(url);
		sqsClient.deleteQueue(url);
		return messages;
	}

	// --------------------- EC2 --------------------->

	public String getInstanceId() {
		return ec2Client.getInstanceId();
	}

	public void terminateInstance(String instanceId) {
		ec2Client.terminateInstance(instanceId);
	}

	// <--------------------- EC2 ---------------------

}
