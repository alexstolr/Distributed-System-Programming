import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class SQSHandler {
	private static PropertiesCredentials credentials;
	private AmazonSQS sqs;

	public SQSHandler(PropertiesCredentials _credentials) {
		credentials = _credentials;
		sqs = new AmazonSQSClient(credentials);
		System.out.println("Amazon SQS Local Client created.");
	}

	/**
	 * Initiates a new standard or FIFO queue or returns the URL of an existing
	 * queue.
	 * 
	 * @return url of the SQS queue created.
	 */
	public String initSQSQueue(String queueName) {
		CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName);
		System.out.println("SQSHandler: Created SQS queue " + queueName);
		return sqs.createQueue(createQueueRequest).getQueueUrl();
	}

	public void changeMessageVisibility(String url, String receiptHandle, int timeToBeInvisible) {
		sqs.changeMessageVisibility(url, receiptHandle, timeToBeInvisible);
	}

	public void deleteMsg(String caller, String url, String receiptHandle) {
		sqs.deleteMessage(new DeleteMessageRequest().withQueueUrl(url).withReceiptHandle(receiptHandle));
		System.out.println(caller + ": Deleted message from " + url);
	}

	public void deleteQueue(String url) {
		sqs.deleteQueue(url);
	}

	public void printMsgData(String callerName, Message msg)

	{
		System.out.println("**** Message Data ****");
		System.out.println(callerName + ": printing message data. received from worker");
		System.out.println("Message");
		System.out.println("MessageId:     " + msg.getMessageId());
		System.out.println("ReceiptHandle: " + msg.getReceiptHandle());
		System.out.println("MD5OfBody:     " + msg.getMD5OfBody());
		System.out.println("Body:          " + msg.getBody());
		for (Entry<String, MessageAttributeValue> entry : msg.getMessageAttributes().entrySet()) {
			System.out.println("Attribute");
			System.out.println("Name:  " + entry.getKey());
			System.out.println("Value: " + entry.getValue().getStringValue());
		}
		System.out.println("**** Message Data ****");
	}

	// ----------------- Get messages to SQS -------------------->

	public Message getMsg(String callerName, String url) {
		System.out.println("\n" + callerName + ": trying to get a message from queue " + url);
		ReceiveMessageRequest request = new ReceiveMessageRequest(url);
		request.setMaxNumberOfMessages(1);
		List<Message> messages = sqs.receiveMessage(request.withWaitTimeSeconds(5).withMessageAttributeNames("All")).getMessages();
		if (!messages.isEmpty()) {
			return messages.iterator().next();
		}
		return null;
	}

	/**
	 * Returns list of messages.
	 * 
	 * @param url
	 * @return
	 */
	public List<Message> getMsgList(String url) {
		ReceiveMessageRequest request = new ReceiveMessageRequest();
		request.withQueueUrl(url).withMessageAttributeNames("All").withMaxNumberOfMessages(10).withWaitTimeSeconds(10); // changed from 5 to 10
		ReceiveMessageResult result = sqs.receiveMessage(request);
		return result.getMessages();
	}

	/**
	 * Checks if the queue has a message with Key 'key' if true returns the
	 * message, else null. used in local application for getting a response.
	 * 
	 * @param url
	 * @param key
	 * @return
	 */
	public Message checkIfGotMsgToSqs(String callerName, String url, String key) {
		System.out.println(callerName + ": trying to get message with key: " + key);
		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(url).withWaitTimeSeconds(5)
				.withMessageAttributeNames("All");
		List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
		for (Message message : messages) {
			for (Map.Entry<String, MessageAttributeValue> entry : message.getMessageAttributes().entrySet()) {
				if (entry.getKey().equals(key)) {
					System.out.println(callerName + ": Received message with key: " + key);
					return message;
				}
			}
			sqs.changeMessageVisibility(url, message.getReceiptHandle(), 0);
		}
		System.out.println();
		return null;
	}

	// <---------------- Get messages to SQS --------------------

	// ----------------- Send messages to SQS -------------------->

	/**
	 * Generic function for sending message to sqs with dynamic number of
	 * <key,value> Make sure to put the value you want to be in the body of the
	 * message in values[0]
	 * 
	 * @param url
	 * @param keys
	 * @param values
	 */
	public void sendMsgToSqs(String callerName, String url, String[] keys, String[] values) {
		System.out.println(
				String.format(callerName + ": Message sent to SQS. Message: %s,  Destination: %s\n ", values[0], url));
		Map<String, String> message = new HashMap<>();
		for (int i = 0; i < keys.length; i++) {
			message.put(keys[i], values[i]);
		}
		SendMessageRequest msgRequest = new SendMessageRequest(url, values[0]);
		for (Map.Entry<String, String> entry : message.entrySet()) {
			msgRequest.addMessageAttributesEntry(entry.getKey(),
					new MessageAttributeValue().withDataType("String").withStringValue(entry.getValue()));
		}
		sqs.sendMessage(msgRequest);
	}

	// <---------------- Send messages to SQS --------------------
}