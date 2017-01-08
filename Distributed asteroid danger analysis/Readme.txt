Alex Stoliar & Ohad Ifrach

Notes:
1.	When checking the program, one have to wait at least 1 min from one test WITH TERMINATE 
	to another due to Amazon SQS (it is not possible to open queue with the same name for 60 seconds).
2.	The output html file shows 10 results per page as default. 


Questions:

1. 	How to run the project:
	
	a. Import maven project into eclipse.
	b. Export 2 jars - Manager.jar & Worker.jar
	c. Upload the jars into S3 (the bucket name should be the same bucket which the Local Application using).
	d. Put the properties file in - C:\Workspace\Java\access\   and call it - "access.properties".
	e. Select arguments.
	f. Run the Local Application.
	d. the dependencies we used are as shown in the pom.xml file - org.apache.httpcomponents, com.googlecode.json-simple, com.amazonaws, joda-time

	
2.	How the program works:
	
	A local application creates queues from and to manager and starts the manager (if not running already). then, uploads an input file to s3 and sends a message to notify the manager.
	The manager breaks the input file into smaller tasks and distributes them to the workers (starts the workers if not yet running) using messages. 
	Each worker handles the tasks and returns the result to the manager.
	A manager then assembles the results and sends them to the local application that requested the job. Local application then creates an html of the result.
	The program is scalable, persistant and multithreaded. Each worker works simultaniously and the manager handles messages received from local applications over different threads.
	A worker can handle requests from different local apps and the manager knows how to reassemble the responses into a result for the local application that gave the job request.
	A worker that crashes while handling a request won't crash the handling of an entire job, rather a message will be returned to the queue and handled by a different worker.
	
	
3. 	Instance type: t2.micro, ami-b73b63a0.


4. 	To check how much time it took the program to run we checked 4 cases - all with n=3 and d=5:
	
	a. First run (no active worker) with termination: 13:30:57.423 - 13:33:43.976 (02:46 minutes). Statistic file attached - "Statistics_1".
	b. First run (no active worker) witout termination: 13:39:02.236 - 13:40:56.513 (01:54 minutes).
	c. Second run (with active workers) witout termination: 13:42:50.067 - 13:43:02.705 (12 seconds).
	d. Third run (with active workers) with termination: 13:45:06.919 - 13:45:55.581 (49 seconds). Statistic file attached - "Statistics_2".


5.	Did you think for more than 2 minutes about security?

	Yes. We don't write the access and secret key in plain text, rather we use methods to get them. Therefore they are not shown in plain text.
	Because it is not shown in plain text, the JAR doesn't include the credantials and hence it is secure.
	Also the bash script itself is also encoded and not shown in plain text.


6.	Did you think about scalability? 

	Yes, the bottle neck is the communication from the manager to local applications.
	If there will be only 1 managerToLocal queue, there will be a problem when wrong local will read a message ment for other local and throws it back to the queue.
	Having different qeueues from manager to each local solves this issue.
	

7.	What about persistence? What if a node dies? Have you taken care of all possible outcomes in the system? Think of more possible issues that might arise from failures. What did you do 
	to solve it? What about broken communications?

	If one node dies, other nodes should take his message from the SQS - even if he died while processing a message, becuase the message will turn visible again after few seconds.
	If all node die, we have a backup function that opens another thread which wait few seconds and checks if the number of active worker is 0 where it shouldn't be and start couple of workers. In case of broken communication between the local and the manager, the manager uploads backup outputfile to S3.


8.	Threads in your application, when is it a good idea? When is it bad?
	
	We used threads in the manager class in order to enable the manager both "listen" to the Local-Manager SQS and distribute the input that already received simultaneously.


9.	Did you run more than one client at the same time?

	Yes. We have tried 2 Local Applications on the same input file with different arguments and got the same result.


10.	Do you understand how the system works? Do a full run using pen and paper, draw the different parts and the communication that happens between them.
	
	Attached.


11.	Did you manage the termination process? Be sure all is closed once requested!

	The manager terminate and close all the processes successfully.


12.	Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!
	

13.	Are all your workers working hard? Or some are slacking? Why?

	It depends in the number of requests, but all workers have equally chance to work hard from the time they've been created.
	when there is a short difference between the start and end date, and there arent many asteroids to process, 
	the first worker to run has a big chance to process most of the asteroids.


14.	Are you sure you understand what distributed means? Is there anything in your system awaiting another?

    Yes, we want to distribute the tasks to as many workers in the most effective manner (regarding time complexity and space complexity - resources) and they handle the tasks simultaniously. So that there wont be a situation where a single computer handles request.
	While others await and dont work. This concept should be held for small numbers of connections/tasks and large as well without need of making changes
	in the program. All that should be achived while concidering a possible failure in one or some of the components - 
    that failure should not affect the running system nor the outcome.
