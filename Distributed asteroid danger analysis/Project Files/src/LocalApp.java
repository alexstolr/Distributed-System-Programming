/**
 * @brief Local Application - starts program, runs all AWS Instances. 
 * @author Ohad Ifrach & Alex Stoliar (alexstolr@gmail.com) 
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalTime;
import java.util.UUID;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.sqs.model.Message;

public class LocalApp {
	private static final int visibility = 10;
	
	// Arguments
	private static File inputFile; 				// args[0] --> outPutFileName
	private static String outputFileName; 		// args[1] --> inputPutFileName
	private static int workersPeriodRatio; 		// args[2] --> n
	private static int days; 					// args[3] --> d

	// AWS
	private static AWSServicesHandler awsHandler;
	private static String propertiesFilePath = "C:\\Workspace\\Java\\access\\access.properties";
	private static PropertiesCredentials credentials;
	private static String bucketName = "alexohad";

	// SQS
	private static String toManagerSQS;
	private static String fromManagerSQS;

	private static String localID = UUID.randomUUID().toString();

	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
		System.out.println("LocalApp: Local time is: " + LocalTime.now());

		// Bad number of arguments
		if (args.length < 4 || args.length > 5) {
			System.out.println("Wrong arguments number. Shuting down...");
			return;
		} else {
			LocalApp localApp = new LocalApp();
			localApp.parseArgs(args);

			credentials = new PropertiesCredentials(new FileInputStream(propertiesFilePath));
			awsHandler = new AWSServicesHandler(credentials);

			// Start Manager
			awsHandler.startManager("Manager", 1);

			// Create S3 bucket or check if already exists, and upload input file.
			awsHandler.uploadFileToBucket("LocalApp", bucketName, inputFile);

			// Uploading jars
			//awsHandler.uploadFileToBucket("LocalApp", bucketName, new File("Include\\Manager.jar"));
			//awsHandler.uploadFileToBucket("LocalApp",bucketName, new File("Include\\Worker.jar"));

			// Create SQS for communication between manager and local and Send location of input file on S3 to SQS
			createSQSandSendMsgToManager();

			Message responseMsg = new Message();
			responseMsg = null;
			while (responseMsg == null) {
				Thread.sleep(1000);
				responseMsg = awsHandler.checkIfGotMsgToSqs("LocalApp", fromManagerSQS, "Response");
			}
			awsHandler.setMessageVisibility(fromManagerSQS, responseMsg.getReceiptHandle(), visibility);

			switch (responseMsg.getMessageAttributes().get("Response").getStringValue()) {

			// Job has been done
			case "Done":
				String summary = awsHandler.downloadFileFromS3("LocalApp", responseMsg.getMessageAttributes().get("LocalID").getStringValue(), bucketName);
				awsHandler.deleteFileFromS3("LocalApp", responseMsg.getMessageAttributes().get("LocalID").getStringValue(), bucketName);
				summary = LocalApp.summaryToHtml(summary);

				PrintWriter writer = new PrintWriter(outputFileName, "UTF-8");
				writer.println(summary);
				writer.close();

				System.out.println("LocalApp: " + outputFileName + " file Created successfully!");

				awsHandler.deleteMsgFromSQS("LocalApp", fromManagerSQS, responseMsg.getReceiptHandle());
				// Dialog.infoBox("LocalApp: Summary file" + outputFileName + " file Created successfully!", "Success!");
				System.out.println("LocalApp: Summary file " + outputFileName + " file Created successfully!");
				
				if (!(args.length == 5 && args[4].equalsIgnoreCase("terminate"))) {
					awsHandler.deleteQueueWithMessages(fromManagerSQS);
				}
				break;

			case "Sorry":
				System.out.println("LocalApp: The manager terminated and hence didn't get my request. My LocalID is: " + localID);
				break;

			default:
				System.out.println("This shouldnt happend");
				break;
			}

			if (args.length == 5 && args[4].equalsIgnoreCase("terminate")) {
				System.out.println("LocalApp: Sending Termination");
				awsHandler.sendMsgToSqs("LocalApp", toManagerSQS, new String[] {"Request", "LocalID"},
						new String[] {"Terminate", localID});

				Message responseMsg2 = new Message();
				responseMsg2 = null;
				while (responseMsg2 == null) {
					Thread.sleep(1000);
					responseMsg2 = awsHandler.checkIfGotMsgToSqs("LocalApp", fromManagerSQS, "Response");
					if (responseMsg2 != null) {
						if (!responseMsg2.getMessageAttributes().get("LocalID").getStringValue().toString().equals(localID)) {
							responseMsg2 = null;
						}
					}
				}

				awsHandler.setMessageVisibility(fromManagerSQS, responseMsg2.getReceiptHandle(), visibility);

				switch (responseMsg2.getMessageAttributes().get("Response").getStringValue().toString()) {

				case "Terminated":
					System.out.println("\nProgram Terminated, Statistics file uploaded to s3");
					awsHandler.deleteQueueWithMessages(fromManagerSQS);
					break;

				default:
					System.out.println("This shouldnt happend");
					break;
				}
			}
			
			else System.out.println("Thank you manager, bye bye");
		}
		
		System.out.println("LocalApp: Local time is: " + LocalTime.now());
	}

	private static void createSQSandSendMsgToManager() {
		toManagerSQS = awsHandler.initSQSQueue("LocalToManagerQueue");
		fromManagerSQS = awsHandler.initSQSQueue("ManagerToLocalQueue-" + localID);

		awsHandler.sendMsgToSqs("LocalApp", toManagerSQS, new String[] {"Request", "LocalID", "WorkersPeriodRatio", "Days", "BucketName", "SQSname"}, 
				new String[] {inputFile.getName(), localID, String.valueOf(workersPeriodRatio), String.valueOf(days), bucketName, fromManagerSQS});
	}

	/**
	 * Parses program arguments.
	 * 
	 * @param args
	 */
	private void parseArgs(String[] args) {
		System.out.println("****");
		System.out.println("Parsing arguments...");
		System.out.println("Input file:              " + args[0]);
		inputFile = new File(args[0]);

		System.out.println("Output file:             " + args[1]);
		outputFileName = args[1];

		System.out.println("Workers - periods ratio: " + args[2]);
		workersPeriodRatio = Integer.parseInt(args[2]);

		System.out.println("Days:                    " + args[3]);
		days = Integer.parseInt(args[3]);
		System.out.println("****\n");
	}

	private static String summaryToHtml(String strSummary) throws IOException {
		StringBuilder stringBuilder = new StringBuilder();

		stringBuilder.append("<!DOCTYPE html>\n");
		stringBuilder.append("<HTML>\n");
		stringBuilder.append("<HEAD>\n");
		stringBuilder.append("<title>Asteroids</title>\n");
		stringBuilder.append("<link rel=\"stylesheet\" href=\"http://cdn.datatables.net/1.10.13/css/jquery.dataTables.min.css\">\n");
		stringBuilder.append("<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/3.1.0/jquery.min.js\"></script>\n");
		stringBuilder.append("<script src=\"http://cdn.datatables.net/1.10.13/js/jquery.dataTables.min.js\"></script>\n");
		stringBuilder.append("<script> $(document).ready(function() {$('#myTable').DataTable();});</script>\n");
		stringBuilder.append("</HEAD>\n");
		stringBuilder.append("<BODY>\n");
		stringBuilder.append("<table id=\"myTable\" style=\"width:100%\">\n");
		stringBuilder.append("<thead style=\"background-color:#323232\">\n");
		stringBuilder.append("<th>Name</th>\n");
		stringBuilder.append("<th>Close Approach Date</th>\n");
		stringBuilder.append("<th>Kilometers Per Second</th>\n");
		stringBuilder.append("<th>Estimated Diameter Min</th>\n");
		stringBuilder.append("<th>Estimated Diameter Max</th>\n");
		stringBuilder.append("<th>Kilometers</th>\n");
		stringBuilder.append("<th>Color</th>\n");
		stringBuilder.append("</thead>\n");
		stringBuilder.append("<tbody>\n");
		
		String htmlEpilog = "</tbody>\n" +"</table>\n" + "</BODY>\n" + "</HTML>\n";
		
		return stringBuilder.toString() + strSummary + htmlEpilog;
	}
}
