import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.util.EC2MetadataUtils;

/**
 * @author Alex Stoliar && Ohad Ifrach
 *
 */
public class Worker {

	private static String workerId;
	private static boolean Terminate;

	// Statistics
	private static int numberOfManagerRequestHandled;
	private static int safe;
	private static int dangerous;
	private static int green;
	private static int yellow;
	private static int red;

	// EC2
	private static AWSServicesHandler awsHandler;
	private static String propertiesFilePath = "/tmp/bin/jar/access.properties";
	private static PropertiesCredentials credentials;

	// SQS
	private static String fromManagerQueueUrl;
	private static String toManagerQueueUrl;

	// Parsing
	private static String m_LocalID;
	private static double m_diameter_threshold;
	private static double m_miss_threshold;
	private static double m_speed_threshold;
	private static String m_diameter_thresholdUnits;
	private static String m_miss_thresholdUnits;
	private static String m_speed_thresholdUnits;

	public static void main(String[] args) throws ClientProtocolException, IOException {
		initWorker();
		while (!Terminate) {
			Message message = awsHandler.getMsgFromSQS("Worker", fromManagerQueueUrl); 
			if(message == null){ 
			} 
			else if (message.getBody().toString().equals("Terminate"))
				handleTerminateMessage(message);
			else
				handleMessageFromManager(message);
		}
		terminate();
	}

	private static void initWorker() throws FileNotFoundException, IOException {
		System.out.println("****");
		System.out.println("Creating Worker class");
		credentials = new PropertiesCredentials(new FileInputStream(propertiesFilePath));
		awsHandler = new AWSServicesHandler(credentials);
		workerId = EC2MetadataUtils.getInstanceId();
		connectToSQS();
		System.out.println("****\n");
	}

	private static void handleMessageFromManager(Message message) throws ClientProtocolException, IOException {
		awsHandler.setMessageVisibility(fromManagerQueueUrl, message.getReceiptHandle(), 10);	
		String urlOfNasa = message.getBody();
		for (Map.Entry<String, MessageAttributeValue> entry : message.getMessageAttributes().entrySet()) {
			initAnalysisParams(entry.getKey(), entry.getValue().getStringValue());
		}
		System.out.println("Worker: Initiated all values for danger analysis");
		String jsonNasaString = getNasaAsteroidsString(urlOfNasa);

		// Analyze and send msg to sqs
		String msgToManager = analyzeDanger(jsonNasaString);
		if (msgToManager.equals("")) {
			msgToManager = "empty";
		}

		// Send it - off you go
		numberOfManagerRequestHandled++;
		String[] keys = {"Response", "LocalID"};
		String[] values = {msgToManager, m_LocalID};
		awsHandler.sendMsgToSqs("Worker", toManagerQueueUrl, keys, values);
		System.out.println("Number of green astroids: " + green);
		System.out.println("Number of yellow astroids: " + yellow);
		System.out.println("Number of red astroids: " + red);

		// Delete from Queue
		awsHandler.deleteMsgFromSQS("Worker", fromManagerQueueUrl, message.getReceiptHandle());
	}

	private static void handleTerminateMessage(Message message) {
		awsHandler.setMessageVisibility(fromManagerQueueUrl, message.getReceiptHandle(), 10);
		System.out.println("Worker: Received Termination message, Starting Termination...");
		Terminate = true;
		awsHandler.deleteMsgFromSQS("Worker", fromManagerQueueUrl, message.getReceiptHandle());
	}

	private static void terminate() throws FileNotFoundException, UnsupportedEncodingException {
		String statistics = generateStatistics();
		awsHandler.sendMsgToSqs("Worker", toManagerQueueUrl, new String[] {"Response", "Statistics", "InstanceID"},
				new String[] {"Terminated", statistics, workerId});
		awsHandler.terminateInstance(workerId);
	}

	private static String generateStatistics() throws FileNotFoundException, UnsupportedEncodingException {
		String s = "**********************\n";
		s += "Requests Handled: " + numberOfManagerRequestHandled + "\n";
		s += "Worker ID: " + workerId + "\n";
		s += "Total asteroids parsed: " + Integer.toString(dangerous + safe) + "\n";
		s += "Safe asteroids: " + Integer.toString(safe) + "\n";
		s += "Dangerous asteroids: " + Integer.toString(dangerous) + "\n";
		s += "Green asteroids: " + Integer.toString(green) + "\n";
		s += "Yellow asteroids: " + Integer.toString(yellow) + "\n";
		s += "Red asteroids: " + Integer.toString(red) + "\n";
		return s;
	}

	private static String analyzeDanger(String jsonAsteroidString) {
		String ans = "";
		JSONObject nasaAsteroidsJson = (JSONObject) JSONValue.parse(jsonAsteroidString);
		JSONObject nearEarthObjects = (JSONObject) nasaAsteroidsJson.get("near_earth_objects");

		// for each day
		for (Object key : nearEarthObjects.keySet()) {
			JSONArray dailyAsteroidArr = (JSONArray) nearEarthObjects.get((String) key);
			for (int i = 0; i < dailyAsteroidArr.size(); i++) {
				JSONObject asteroid = (JSONObject) dailyAsteroidArr.get(i);

				// Get asteroid data
				String name = asteroid.get("name").toString();

				JSONArray close_approach_data = (JSONArray) asteroid.get("close_approach_data");
				String close_approach_date = ((JSONObject) close_approach_data.get(0)).get("close_approach_date")
						.toString();

				String relative_velocity = ((JSONObject) ((JSONObject) close_approach_data.get(0))
						.get("relative_velocity")).get(m_speed_thresholdUnits).toString();

				JSONObject estimated_diameter_kilometers = ((JSONObject) ((JSONObject) asteroid
						.get("estimated_diameter")).get(m_diameter_thresholdUnits));
				double estimated_diameter_min = Double
						.parseDouble(estimated_diameter_kilometers.get("estimated_diameter_min").toString());

				double estimated_diameter_max = Double
						.parseDouble(estimated_diameter_kilometers.get("estimated_diameter_max").toString());

				String miss_distance_S = ((JSONObject) ((JSONObject) close_approach_data.get(0)).get("miss_distance"))
						.get(m_miss_thresholdUnits).toString();
				double miss_distance_D = Double.parseDouble(miss_distance_S);

				// Start analysis
				if (asteroid.get("is_potentially_hazardous_asteroid").toString() == "true") {
					// Green?
					if (Double.parseDouble(relative_velocity) >= m_speed_threshold) {
						if (estimated_diameter_min >= m_diameter_threshold) {
							if (miss_distance_D >= m_miss_threshold) {
								ans += addHtmlLine("Red", name, close_approach_date, relative_velocity,
										estimated_diameter_min, estimated_diameter_max, miss_distance_D);
								dangerous++;
								red++;
							} else {
								ans += addHtmlLine("Yellow", name, close_approach_date, relative_velocity,
										estimated_diameter_min, estimated_diameter_max, miss_distance_D);
								dangerous++;
								yellow++;
							}
						} else {
							ans += addHtmlLine("Green", name, close_approach_date, relative_velocity,
									estimated_diameter_min, estimated_diameter_max, miss_distance_D);
							dangerous++;
							green++;
						}
					} else {
						safe++;
					}
				}
			}
		}
		return ans;
	}

	private static String addHtmlLine(String color, String name, String close_approach_date, String relative_velocity,
			double estimated_diameter_min, double estimated_diameter_max, double miss_distance_D) {

		String s = "<tr style=\"background-color:" + color + "\">\n";
		s += "<th>" + name + "</th>\n";
		s += "<th>" + close_approach_date + "</th>\n";
		s += "<th>" + relative_velocity + "</th>\n";
		s += "<th>" + estimated_diameter_min + "</th>\n";
		s += "<th>" + estimated_diameter_max + "</th>\n";
		s += "<th>" + miss_distance_D + "</th>\n";
		s += "<th>" + color + "</th>\n";
		s += "</tr>\n";
		return s;
	}

	private static String getNasaAsteroidsString(String urlOfNasa) throws ClientProtocolException, IOException {
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();
		HttpGet getRequest = new HttpGet(urlOfNasa);
		HttpResponse response = httpClient.execute(getRequest);

		// Error
		if (response.getStatusLine().getStatusCode() != 200) {
			throw new RuntimeException(EntityUtils.toString(response.getEntity()) + "\nFailed : HTTP error code : "
					+ +response.getStatusLine().getStatusCode());
		}

		// OK
		else {
			String json_string = EntityUtils.toString(response.getEntity());
			return json_string;
		}
	}

	private static void initAnalysisParams(String key, String stringValue) {
		switch (key) {
		case "LocalID":
			m_LocalID = stringValue;
			break;
		case "diameter_threshold":
			m_diameter_threshold = Double.parseDouble(stringValue);
			break;
		case "miss_threshold":
			m_miss_threshold = Double.parseDouble(stringValue);
			break;
		case "speed_threshold":
			m_speed_threshold = Double.parseDouble(stringValue);
			break;
		case "diameter_thresholdUnits":
			m_diameter_thresholdUnits = stringValue;
			break;
		case "miss_thresholdUnits":
			m_miss_thresholdUnits = stringValue;
			break;
		case "speed_thresholdUnits":
			m_speed_thresholdUnits = stringValue;
			break;
		case "Request":
			// The value is the same as Body, and it was parsed before. so, we can ignore it.
			break;
		default:
			System.out.println("This shouldnt happend. key:" + key);
			break;
		}
	}

	/**
	 * Connects to fromManager and toManager queues
	 */
	private static void connectToSQS() {
		fromManagerQueueUrl = awsHandler.initSQSQueue("ManagertoWorkersQueue");
		toManagerQueueUrl = awsHandler.initSQSQueue("WorkerstoManagerQueue");
	}
}
