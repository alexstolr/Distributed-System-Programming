import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.util.EC2MetadataUtils;

public class Manager {

	// private static String managerId;
	private static String managerId;
	private static boolean Terminate;
	private static ExecutorService executor;

	// EC2
	private static AWSServicesHandler awsHandler;
	private static String propertiesFilePath = "/tmp/bin/jar/access.properties";
	private static PropertiesCredentials credentials;

	// S3
	private static String GeneralpurposeBucketName = "alexohad";

	// SQS
	private static String fromLocalSQSUrl;
	private static String toWorkersQueueUrl;
	private static String fromWorkersQueueUrl;
	private static String terminatorLocalAppId;

	private static Map<String, String> localApps;

	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
		initManager();
		while (!Terminate) {
			Message message = awsHandler.getMsgFromSQS("Manager", fromLocalSQSUrl);
			if (message == null) {
			} 
			else if (message.getBody().toString().equals("Terminate")) {
				handleTerminateMessage(message);
			} else
				handleJobMessage(message);
		}
		System.out.println("Starting manager termination");
		terminate();
	}

	private static void initManager() throws FileNotFoundException, IOException {
		System.out.println("****\n");
		System.out.println("Creating Manager Class");
		credentials = new PropertiesCredentials(new FileInputStream(propertiesFilePath));
		awsHandler = new AWSServicesHandler(credentials);
		executor = Executors.newCachedThreadPool();
		managerId = EC2MetadataUtils.getInstanceId();
		localApps = new ConcurrentHashMap<>();
		connectAndCreateSQS();
		System.out.println("****\n");
	}

	/**
	 * Connect to an existing SQS or create a new one.
	 */
	private static void connectAndCreateSQS() {
		fromLocalSQSUrl = awsHandler.initSQSQueue("LocalToManagerQueue");
		toWorkersQueueUrl = awsHandler.initSQSQueue("ManagertoWorkersQueue");
		fromWorkersQueueUrl = awsHandler.initSQSQueue("WorkerstoManagerQueue");
	}

	private static void handleJobMessage(Message message) {
		awsHandler.setMessageVisibility(fromLocalSQSUrl, message.getReceiptHandle(), 10);

		final String inputFileName = message.getMessageAttributes().get("Request").getStringValue(); // getting input file name

		System.out.println("Manager: receiveing message from local application. Message: " + inputFileName);

		final String bucketName = message.getMessageAttributes().get("BucketName").getStringValue();
		final String localID = message.getMessageAttributes().get("LocalID").getStringValue();
		final String tempNameLocalSQSUrl = message.getMessageAttributes().get("SQSname").getStringValue();
		localApps.put(localID, tempNameLocalSQSUrl);

		final int workersPeriodRatio = Integer.parseInt(message.getMessageAttributes().get("WorkersPeriodRatio").getStringValue());
		final int days = Integer.parseInt(message.getMessageAttributes().get("Days").getStringValue());


		awsHandler.deleteMsgFromSQS("Manager", fromLocalSQSUrl, message.getReceiptHandle());

		// Create new thread for handling the job request from local and keep trying to get new jobs in main thread
		executor.execute(new Runnable() {
			@Override
			public void run() {
				String inputFileFromLocal;
				try {
					inputFileFromLocal = awsHandler.downloadFileFromS3("Manager", inputFileName, bucketName);
					awsHandler.deleteFileFromS3("LocalApp", inputFileName, bucketName);
					String values[] = parseInputFile(inputFileFromLocal);

					double speed_threshold = Double.parseDouble(values[2].substring(0, values[2].lastIndexOf("-")));
					String speed_thresholdUnits = values[2].substring(values[2].lastIndexOf("-") + 1);

					double diameter_threshold = Double.parseDouble(values[3].substring(0, values[3].lastIndexOf("-")));
					String diameter_thresholdUnits = values[3].substring(values[3].lastIndexOf("-") + 1);

					double miss_threshold = Double.parseDouble(values[4].substring(0, values[4].lastIndexOf("-")));
					String miss_thresholdUnits = values[4].substring(values[4].lastIndexOf("-") + 1);

					final String units[] = {speed_thresholdUnits, diameter_thresholdUnits, miss_thresholdUnits};
					final double doubleValues[] = {speed_threshold, diameter_threshold, miss_threshold};

					long numberOfDays = calculateDayDiff(values[0], values[1]); // values[0] = Start date & values[1] = End date

					final String nasaApiGetRequests[] = generateGetRequest(values[0], numberOfDays, days);
					System.out.println("Manager: The number of total API NASA requests is: " + nasaApiGetRequests.length);

					int numberOfRequests = distributeOperations(workersPeriodRatio, nasaApiGetRequests, units, doubleValues, localID, bucketName);

					String summary = getMessagesFromWorkers(localID, numberOfRequests);
					awsHandler.uploadStringFile("Manager", GeneralpurposeBucketName, summary, "backupOutput-" + managerId + ".html");
					System.out.println("Summary from workers: " + summary);
					awsHandler.uploadStringFile("Manager", bucketName, summary, localID);
					awsHandler.sendMsgToSqs("Manager", localApps.get(localID), new String[] {"Response", "LocalID"},
							new String[] {"Done", localID});

				} catch (IOException e) {
					e.printStackTrace();
				} catch (org.json.simple.parser.ParseException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Distributes the operations to be performed to the workers using SQS
	 * queue/s
	 * 
	 * @param workersPeriodRatio
	 * @param nasaApiGetRequests
	 * @param units
	 *            {speed_thresholdUnits,diameter_thresholdUnits,
	 *            miss_thresholdUnits}
	 * @param doubleValues
	 *            {speed_threshold,diameter_threshold, miss_threshold}
	 * @param localID
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	protected static synchronized int distributeOperations(int workersPeriodRatio, String[] nasaApiGetRequests, String[] units,
			double[] doubleValues, String localID, String bucketName) throws FileNotFoundException, IOException {

		// Count active workers
		int activeWorkers = awsHandler.countActiveWorkers();

		// Calculate number of needed workers = r
		int periods = nasaApiGetRequests.length;
		int numOfWorkersNeeded = (int) Math.ceil((periods / ((double) workersPeriodRatio)));

		// create and run r-activeWorkers workers
		if (numOfWorkersNeeded - activeWorkers > 0) {
			awsHandler.startWorkers("Worker", numOfWorkersNeeded - activeWorkers);
		}

		// push all messages to ManagerToWorker sqs
		for (int i = 0; i < periods; i++) {
			awsHandler.sendMsgToSqs("Manager", toWorkersQueueUrl,
					new String[] {"Request", "LocalID", "speed_threshold", "speed_thresholdUnits",
							"diameter_threshold", "diameter_thresholdUnits", "miss_threshold", "miss_thresholdUnits"},
					new String[] {nasaApiGetRequests[i], localID, String.valueOf(doubleValues[0]), units[0],
							String.valueOf(doubleValues[1]), units[1], String.valueOf(doubleValues[2]), units[2]});
		}
		return nasaApiGetRequests.length;
	}

	protected static synchronized String getMessagesFromWorkers(String localID, int numOfRequests) {
		checkIfBackupNeeded();
		String summary = "";
		int requestsHandled = 0;
		while (requestsHandled != numOfRequests) {
			List<Message> messages = awsHandler.getMsgList(fromWorkersQueueUrl);
			if (!messages.isEmpty()) {
				for (Message msg : messages) {
					if (msg.getMessageAttributes().size() > 0) {
						if (msg.getMessageAttributes().get("LocalID").getStringValue().equals(localID)) {
							awsHandler.setMessageVisibility(fromWorkersQueueUrl, msg.getReceiptHandle(), 5);
							requestsHandled++;
							System.out.println("requestsHandled: " + requestsHandled);
							if (!(msg.getBody().equals("empty"))) {
								summary += msg.getBody();
							}
							awsHandler.deleteMsgFromSQS("Manager", fromWorkersQueueUrl, msg.getReceiptHandle());
						}
					}
				}
			}
		}
		return summary;
	}

	private synchronized static String[] generateGetRequest(String startDate, long numberOfDays, int _days) throws ParseException {
		String address = "https://api.nasa.gov/neo/rest/v1/feed?";
		String API_KEY = "zbZYl5AVZhJAjIA0fW97Kj6f0xlRKcz6ydx7DoDU";

		if (_days >= 1 && _days <= 7) {
			int periods = (int) Math.ceil((numberOfDays / ((double) _days)));
			int daysLeft = (int) numberOfDays;
			String getRequests[] = new String[periods];
			for (int i = 0; i < periods; i++) {
				String endDate = startDate; // Start date
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
				Calendar c = Calendar.getInstance();
				c.setTime(sdf.parse(endDate));
				if (daysLeft < _days) {
					c.add(Calendar.DATE, daysLeft - 1); // number of days to add
					endDate = sdf.format(c.getTime()); // endDate is now the new date
					getRequests[i] = address + String.format("start_date=%s&" + "end_date=%s&" + "api_key=%s",
							startDate, endDate, API_KEY);
					c.setTime(sdf.parse(endDate));
					c.add(Calendar.DATE, 1);
					startDate = sdf.format(c.getTime());
					daysLeft -= daysLeft;
					System.out.println(getRequests[i]);
				} else {
					c.add(Calendar.DATE, _days - 1); // number of days to add
					endDate = sdf.format(c.getTime()); // endDate is now the new date
					getRequests[i] = address + String.format("start_date=%s&" + "end_date=%s&" + "api_key=%s",
							startDate, endDate, API_KEY);
					c.setTime(sdf.parse(endDate));
					c.add(Calendar.DATE, 1);
					startDate = sdf.format(c.getTime());
					daysLeft -= _days;
					System.out.println(getRequests[i]);
				}
			}
			return getRequests;
		} else {
			System.out.println("Bad argument days. should be >= 1 && <= 7");
			return null;
		}
	}

	private static long calculateDayDiff(String startDate, String endDate) {
		String inputString1 = startDate;
		String inputString2 = endDate;
		String format = "yyyy-MM-dd";
		SimpleDateFormat myFormat = new SimpleDateFormat(format);

		try {
			Date date1 = myFormat.parse(inputString1);
			Date date2 = myFormat.parse(inputString2);
			long diff = date2.getTime() - date1.getTime();
			return (TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS) + 1);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * Parses input file into array of keys
	 * 
	 * @param fileToParse
	 * @return [start_date,end_date,speed_threshold,diameter_threshold,
	 *         miss_threshold]
	 * @throws org.json.simple.parser.ParseException
	 */
	private static String[] parseInputFile(String fileToParse) throws org.json.simple.parser.ParseException {

		JSONParser jParser = new JSONParser();
		JSONArray jasonArr = (JSONArray) jParser.parse(fileToParse);

		String keysToValues[] = {"start_date", "end_date", "speed_threshold", "diameter_threshold", "miss_threshold"};

		// Replaces keys with their values.
		for (int i = 0; i < keysToValues.length; i++) {
			JSONObject tmoJObj = (JSONObject) jasonArr.get(i);
			keysToValues[i] = (String) tmoJObj.get(keysToValues[i]);
		}
		return keysToValues;
	}

	private static void handleTerminateMessage(Message message) {
		awsHandler.setMessageVisibility(fromLocalSQSUrl, message.getReceiptHandle(), 10);
		terminatorLocalAppId = message.getMessageAttributes().get("LocalID").getStringValue();
		System.out.println("Manager: Received Termination message from Local App: "
				+ message.getMessageAttributes().get("LocalID").getStringValue());
		Terminate = true;
		awsHandler.deleteMsgFromSQS("Manager", fromLocalSQSUrl, message.getReceiptHandle());
	}

	/**
	 * Take all messages from Local that have not started yet (not even taken
	 * from sqs) for each message, send its sender a terminate message. handle
	 * all workers replies and terminate self
	 * 
	 * @throws InterruptedException
	 */
	private static void terminate() throws InterruptedException {
		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (Exception e) {
			e.getMessage();
		}

		deleteQueue(fromLocalSQSUrl);
		int activeWorkers = awsHandler.countActiveWorkers();
		System.out.println("The number of active workers is: " + awsHandler.countActiveWorkers());
		for (int i = 0; i < activeWorkers; i++) {
			awsHandler.sendMsgToSqs("Manager", toWorkersQueueUrl, new String[] {"Request", "LocalID"},
					new String[] {"Terminate", "0"});
		}

		// Wait until workers are done, then takes all messages from Worker queue and finally delete toWorkers and from workers.
		System.out.println("Manager: generating statistics");
		generateAndUploadStatisticsFile(activeWorkers);

		awsHandler.sendMsgToSqs("Manager", localApps.get(terminatorLocalAppId), new String[] {"Response", "LocalID"}, new String[] {"Terminated", terminatorLocalAppId});
		System.out.println("Manager: I've finished my job. All should be stopped. Closing myself");
		awsHandler.deleteQueueWithMessages(toWorkersQueueUrl);
		awsHandler.deleteQueueWithMessages(fromWorkersQueueUrl);
		awsHandler.terminateInstance(managerId);
	}

	private static void generateAndUploadStatisticsFile(int numberOfStatisticsToBeHandled) {
		String statistics = "";
		int statisticsHandled = 0;
		while (statisticsHandled != numberOfStatisticsToBeHandled) {
			Message message = awsHandler.getMsgFromSQS("Manager", fromWorkersQueueUrl);
			if (!(message == null)) {
				if (message.getMessageAttributes().size() > 0) {
					Map<String, MessageAttributeValue> map = message.getMessageAttributes();
					if (map.get("Response").getStringValue().equals("Terminated")) {
						awsHandler.setMessageVisibility(fromWorkersQueueUrl, message.getReceiptHandle(), 5);
						System.out.println("Manger: Workers terminate response handled. ");
						awsHandler.deleteMsgFromSQS("Manager", fromWorkersQueueUrl, message.getReceiptHandle());
						System.out.println(map.get("Statistics").getStringValue());
						statistics += map.get("Statistics").getStringValue();
						statisticsHandled++;
					}
				}
			}
		}
		awsHandler.uploadStringFile("Manager", GeneralpurposeBucketName, statistics, "statistics-" + managerId + ".txt");
	}

	private static void deleteQueue(String url) throws InterruptedException {

		List<Message> lastMessages = awsHandler.deleteQueueWithMessages(url); 
		// Send messages to LocalApps that request was canceled due to termination
		for (Message message : lastMessages) {
			awsHandler.setMessageVisibility(url, message.getReceiptHandle(), 10);
			// If the message was sent by a Local Application
			awsHandler.sendMsgToSqs("Manager", localApps.get(message.getMessageAttributes().get("LocalID").getStringValue()), new String[] {"Response", "LocalID"},
					new String[] {"Sorry", message.getMessageAttributes().get("LocalID").getStringValue()});
		}
	}

	private static void checkIfBackupNeeded() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(20000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if(awsHandler.countActiveAndPendingWorkers() == 0) {
					System.out.println("Manager: Activating backup workers");
					awsHandler.startWorkers("Worker", 2);
				}
			}
		});
	}
}
