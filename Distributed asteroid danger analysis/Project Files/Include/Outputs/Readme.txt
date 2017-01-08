Alex Stoliar 310668629 & Ohad Ifrach 201486180

Notes:
1.	When checking the program, one have to wait at least 1 min from one test WITH TERMINATE 
	to another due to Amazon SQS (it is not possible to open queue with the same name for 60 seconds). 
2.	The output html file shows 10 result per page as default.


Questions:

1. 	How to run the project:
	
	a. Import maven project into eclipse.
	b. Export 2 jars - Manager.jar & Worker.jar
	c. Upload the jars into S3 (the bucket name should be the same bucket which the Local Application using).
	d. Put the properties file in - C:\Workspace\Java\access\   and call it - "access.properties".
	e. Select arguments.
	f. Run the Local Application.

2. 	Instance type: t2.micro, ami-b73b63a0.

3. 	To check how much time it took the program to run we checked 4 cases - all with n=3 and d=5:
	
	a. First run (no active worker) with termination: 13:30:57.423 - 13:33:43.976. Statistic file attached - "Statistics_1".
	b. First run (no active worker) witout termination: 13:39:02.236 - 13:40:56.513.
	c. Second run (with active workers) witout termination: 13:42:50.067 - 13:43:02.705.
	d. Third run (with active workers) with termination: 13:45:06.919 - 13:45:55.581. Statistic file attached - "Statistics_2".

4.	Did you think for more than 2 minutes about security?
	We..

5.	Did you think about scalability? 

6.	What about persistence? What if a node dies? Have you taken care of all possible outcomes in the system? Think of more possible issues that might arise from failures. What did you do 
	to solve it? What about broken communications?

	If one node dies, other nodes should take his message from the SQS - even if he died while processing a message, becuase the message will turn visible again after few seconds.
	If all node die, we have a backup function that checks if the number of active worker is 0 where it shouldn't be and start couple of workers.
	In case of broken communication between the local and the manager, the manager uploads backup outputfile to S3.

7.	Threads in your application, when is it a good idea? When is it bad?
	
	We used threads in the manager class in order to enable the manager both "listen" to the Local-Manager SQS and distribute the input that already received simultaneously.

8.	Did you run more than one client at the same time?

	Yes. We have tried 2 Local Applications on the same input file with different arguments and got the same result.

9.	Do you understand how the system works? Do a full run using pen and paper, draw the different parts and the communication that happens between them.
	Attached.

10.	Did you manage the termination process? Be sure all is closed once requested!

	The manager terminate and close all the processes successfully.

11.	Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!
	

12.	Are all your workers working hard? Or some are slacking? Why?
	It depends in the number of requests, but all workers have equally chance to work hard from the time they've been created.


13.	Are you sure you understand what distributed means? Is there anything in your system awaiting another?
