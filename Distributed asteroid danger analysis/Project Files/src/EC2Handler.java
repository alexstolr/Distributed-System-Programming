import java.util.ArrayList;
import java.util.List;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.util.EC2MetadataUtils;
import com.amazonaws.util.Base64;

public class EC2Handler {

	private static PropertiesCredentials credentials;
	private AmazonEC2Client ec2Client;
	private static String bucketName = "alexohad";

	public EC2Handler(PropertiesCredentials _credentials) {
		credentials = _credentials;
		ec2Client = new AmazonEC2Client(credentials);
		System.out.println("Amazon EC2 Local Client created.");
	}

	public String getInstanceId() {
		return EC2MetadataUtils.getInstanceId();
	}

	public void terminateInstance(String instanceId) {
		List<String> instancesToTerminate = new ArrayList<String>();
		instancesToTerminate.add(instanceId);
		TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest();
		terminateRequest.setInstanceIds(instancesToTerminate);
		ec2Client.terminateInstances(terminateRequest);
	}

	/**
	 * Starting EC2 instances 
	 * @param jarName
	 * @param instaceType
	 * @param numOfInstances
	 */
	public void startInstace(String jarName, String instaceType, int numOfInstances) {
		RunInstancesRequest request = new RunInstancesRequest("ami-b73b63a0", numOfInstances, numOfInstances);
		// setInstanceType vs withInstanceType:
		// 'with..' enables overloading of settings on request.
		// 'set' doesn't.
		request.withInstanceType(InstanceType.T2Micro.toString()).withSecurityGroups("default").withUserData(getBootStrap(jarName));;
		//if(jarName.equals("Worker")) request.withUserData(getBootStrap(jarName));
		
		// Request to start instances
		List<Instance> runningInstances = ec2Client.runInstances(request).getReservation().getInstances();
		System.out.println("Amazon EC2 instance " + instaceType + " created.");
		
		// Create list of id's of all running instances - in order to tag them. 
		List<String> instanceIDs = new ArrayList<>();
		for (Instance instance : runningInstances) {
			instanceIDs.add(instance.getInstanceId());
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			System.out.println("Start instances: Failed to sleep with error: " + e.getMessage());
		}
		
		// Tag all instances. 
		List<Tag> tags = new ArrayList<>();
		tags.add(new Tag("InstanceType", instaceType));
		CreateTagsRequest createTagsRequest = new CreateTagsRequest(instanceIDs, tags);
		ec2Client.createTags(createTagsRequest);
	}

	private String getBootStrap(String jarName) {
		String accessKey = credentials.getAWSAccessKeyId();
		String secretAccessKey = credentials.getAWSSecretKey();
		String script = "#!/bin/sh\n" + "BIN_DIR=/tmp\n" + "AWS_ACCESS_KEY_ID=" + accessKey + "\n"
				+ "AWS_SECRET_ACCESS_KEY=" + secretAccessKey + "\n"
				+ "cd $BIN_DIR\n" + "mkdir -p $BIN_DIR/bin/jar\n" // no error if existing, make parent directories as needed
				+ "export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY\n"
				// export makes the variable available to sub-processes
				+ "aws s3 cp s3://" + bucketName + "/" + jarName + ".jar $BIN_DIR/bin/jar\n"
				+ "echo accessKey = $AWS_ACCESS_KEY_ID > $BIN_DIR/bin/jar/access.properties\n"
				+ "echo secretKey = $AWS_SECRET_ACCESS_KEY >> $BIN_DIR/bin/jar/access.properties\n"
				+ "ls $BIN_DIR/bin/jar/\n"
				+ "java -jar $BIN_DIR/bin/jar/" + jarName + ".jar";
		String str = new String(Base64.encode(script.getBytes()));
		return str;
	}

	/**
	 * Checks if manager instance exist and if not - start new manager.
	 * 
	 * @param jarName
	 * @param type
	 * @param numOfInstances
	 */
	public void startManager(String jarName, String type, int numOfInstances) {
		if (!managerExist()) {
			try {
				System.out.println("****");
				startInstace(jarName, type, numOfInstances);
				System.out.println("**** \n");
			} catch (AmazonServiceException ase) {
				System.out.println("Couldn't open a manager instance");
			}
		}
	}

	/**
	 * Checks if there is a manager instance, running or pending.
	 * getReservations() returns List of all instances.
	 * 
	 * @return
	 */
	private boolean managerExist() {
		for (Reservation reservation : ec2Client.describeInstances().getReservations()) {
			for (Instance ins : reservation.getInstances()) {
				for (Tag tag : ins.getTags()) {
					if (tag.getValue().equals("Manager") && (ins.getState().getName().equals("running")
							|| (ins.getState().getName().equals("pending")))) {
						System.out.println("Found instance of manager\n");
						return true;
					}
				}
			}
		}
		return false;
	}

	public int countActiveWorkers() {
		int counter = 0;
		List<Reservation> reservList = ec2Client.describeInstances().getReservations();
		for (Reservation reservation : reservList) {
			for (Instance instance : reservation.getInstances()) {
				if (instance.getState().getName().equals("running")) {
					for (Tag tag : instance.getTags()) {
						if (tag.getValue().equals("Worker"))
							counter++;
					}
				}
			}
		}
		return counter;
	}

	public int countActiveAndPendingWorkers() {
		int counter = 0;
		List<Reservation> reservList = ec2Client.describeInstances().getReservations();
		for (Reservation reservation : reservList) {
			for (Instance instance : reservation.getInstances()) {
				if (instance.getState().getName().equals("running") || instance.getState().getName().equals("pending")) {
					for (Tag tag : instance.getTags()) {
						if (tag.getValue().equals("Worker"))
							counter++;
					}
				}
			}
		}
		return counter;
	}

}