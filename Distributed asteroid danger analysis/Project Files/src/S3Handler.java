import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

public class S3Handler {
	private static PropertiesCredentials credentials;
	private static AmazonS3 s3;

	public S3Handler(PropertiesCredentials _credentials) {
		credentials = _credentials;
		s3 = new AmazonS3Client(credentials);
		System.out.println("Amazon S3 Local Client created.");
	}

	/**
	 * if S3 bucket 'bucketName' doesn't exist, creates it. Uploads file
	 * 'inputFile' to S3 bucket 'bucketName'
	 * 
	 * @param bucketName
	 * @param inputFile
	 */
	public void uploadFileToBucket(String callerName, String bucketName, File inputFile) {
		// Create bucket or check if exists.
		if (s3.doesBucketExist(bucketName)) {
		} else {
			Region usEast1 = Region.getRegion(Regions.US_EAST_1);
			s3.setRegion(usEast1);
			s3.createBucket(bucketName);
			System.out.println(callerName + ": Amazon S3 Bucket " + bucketName + " created.");
		}

		// Upload file to s3
		PutObjectRequest por = new PutObjectRequest(bucketName, inputFile.getName(), inputFile);
		s3.putObject(por);
		System.out.println(callerName + ": File " + inputFile.getName() + " uploaded to " + bucketName);
	}

	/**
	 * Upload file to s3 as string.
	 * 
	 * @param callerName
	 * @param bucketName
	 * @param content
	 *            - string to be turned to file
	 * @param key
	 *            - the name of the file
	 */
	public void uploadStringFile(String callerName, String bucketName, String content, String key) {
		// Create bucket or check if exists.
		if (s3.doesBucketExist(bucketName)) {
			System.out.println(callerName + ": Bucket " + bucketName + " already exists.");
		} else {
			Region usEast1 = Region.getRegion(Regions.US_EAST_1);
			s3.setRegion(usEast1);
			s3.createBucket(bucketName);
			System.out.println(callerName + ": Amazon S3 Bucket " + bucketName + " created.");
		}

		// Upload file to s3
		s3.putObject(bucketName, key, content);
		System.out.println(callerName + ": uploaded to " + bucketName);
	}

	public void deleteFileFromBucket(String callerName, String bucketName, String fileName) {
		s3.deleteObject(bucketName, fileName);
		System.out.println(String.format(callerName + ": Successfully deleted file %s from S3 bucket", fileName));
	}

	public String downloadFile(String caller, String fileName, String bucketName) throws IOException {
		System.out.println(String.format(caller + ": downloading file:  %s from S3 bucket: %s", fileName, bucketName));
		S3Object object = s3.getObject(new GetObjectRequest(bucketName, fileName));
		System.out.println(String.format(caller + ": Successfully downloaded file %s from S3 bucket", fileName));

		BufferedReader br = new BufferedReader(new InputStreamReader(object.getObjectContent()));
		String str = "";
		String line;
		while ((line = br.readLine()) != null) {
			str += line;
		}
		return str;
	}
}