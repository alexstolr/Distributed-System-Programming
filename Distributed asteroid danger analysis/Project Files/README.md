# Distributed-System-Programming
# Assignment 1
# Saving the Earth from Asteroids!

Submit your questions only to this e-mail. Ask questions only about assignment segments that are not understood.
Check Assignment1 FAQ before asking a question. All information given in the FAQ is mandatory. The FAQ 

# Do Not Forget (or try to avoid it XD)

Perform these tutorials (alternative link in case of problems).

# Getting Started

Read the assignment description.
Read the reading the material, and make sure you understand it.
Request a NASA API Key (They're free ^_^).
Go to NASA's NeoWS and learn to use it.
Write the code that does the API-Calls and the "Danger Analysis". Run it on your computer and make sure it works.
Write the local application code and make sure it works.
Write the manager code, run it on your computer and make sure it works.
Run the manager, the local application, together with a worker on your computer and make sure they work.
Run the local application on your computer, and let it start and run the manager on EC2, and let the manager start and run the workers on EC2. … 

# Abstract

In this assignment you will code a "Real-World" application to distributively process a list of asteroids, perform RESTful API-Calls to NASA's NeoWS and a simulated "Danger Analysis", and display the results on a web page. The goal of the assignment is to experiment with AWS.

# Input File Format
The input file contains a start-date, end-date, speed-threshold, diameter-threshold and miss-threshold parameters.

# Output File Format
The output is an HTML file containing a line for each input Asteroid, containing the asteroid name and it's relevant data:

0 - name (Text)
1 - (close_approach_data) close_approach_date (Text)
2 - (relative_velocity) kilometers_per_second (Number)
3 - (estimated_diameter (meters)) estimated_diameter_min (Number)
4 - (estimated_diameter (meters)) estimated_diameter_max (Number)
5 - (miss_distance) kilometers (Number) 


Each potentially hazardous asteroid line should be colored according to the following legend:

Green - if speed is less than 10 km/s && hazardous asteroid
Yellow - if speed is more than 10 km/s && estimated diameter is less than 200 m && hazardous asteroid
Red - if speed is more than 10 km/s && estimated diameter is more than 200 m && miss distance is less than 0.3 AU (astronomical unit) && hazardous asteroid 

# System Architecture
The system is composed of 3 elements:

Local application
Manager
Workers


The elements will communicate with each other using queues (SQS) and storage (S3). It is up to you to decide how many queues to use and how to split the jobs among the workers, but, and you will be graded accordingly, your system should strive to work in parallel. It should be as efficient as possible in terms of time and money.

# Local Application
The application resides on a local (non-cloud) machine. Once started, it reads the input file from the user, and:

Checks if a Manager node is active on the EC2 cloud. If it is not, the application will start the manager node.
Uploads the file to S3.
Sends a message to an SQS queue, stating the location of the file on S3
Checks an SQS queue for a message indicating the process is done and the response (the summary file) is available on S3.
Downloads the summary file from S3, and create an html file representing the results.
Sends a termination message to the Manager if it was supplied as one of its input arguments. 


IMPORTANT: There can be more than one than one local application running at the same time, and requesting service from the manager.

# The Manager
The manager process resides on an EC2 node. It checks a special SQS queue for messages from local applications. Once it receives a message it:

   If the message is that of a new task it:
        Downloads the input file from S3.
        Distributes the operations to be performed to the workers using SQS queue/s.
        Checks the SQS message count and starts Worker processes (nodes) accordingly.
            The manager should create a worker for every n periods, if there are no running workers.
            If there are k active workers, and the new job requires m workers, then the manager should create m-k new workers, if possible.
            Note that while the manager creates a node for every n periods, it does not delegate periods to specific nodes. All of the worker nodes take their periods from the same SQS queue; so it might be the case that with 2n periods, hence two worker nodes, one node processed n+(n/2) periods, while the other processed only n/2. 
        After the manger receives response messages from the workers on dates in the input file, then it:
            Creates a summary output file accordingly,
            Uploads the output file to S3,
            Sends a message to the application with the location of the file. 

    If the message is a termination message, then the manager:
        Does not accept any more input files from local applications. However, it does serve the local application that sent the termination message.
        Waits for all the workers to finish their job, and then terminates them.
        Creates response messages for the jobs, if needed.
        Terminates. 


IMPORTANT: the manager must process requests from local applications simultaneously; meaning, it must not handle each request at a time, but rather work on all requests in parallel.

# The Workers
A worker process resides on an EC2 node. Its life cycle is as follows:

Repeatedly:

   Get a message from an SQS queue.
    Perform the requested job, and return the result.
    remove the processed message from the SQS queue. 


IMPORTANT:

   If a worker stops working unexpectedly before finishing its work on a message, then some other worker should be able to handle that message. 

# The Queues and Messages
As described above, queues are used for:

   communication between the local application and the manager.
    communication between the manager and the workers. 


It is up to you to decide what the jobs and the messages are, and how many queues to use, but your system should run as efficiently as possible in terms of time!

# Running the Application
The application should be run as follows:

java -jar yourjar.jar inputFileName outputFileName n d

or, if you want to terminate the manager:

java  -jar yourjar.jar inputFileName outputFileName n d terminate

where:

yourjar.jar is the name of the jar file containing your code (do not include the libraries in it when you create it).
inputFileName is the name of the input file.
outputFileName is the name of the output file.
n is: workers - periods ratio (how many periods per worker). d is: days - how many days in each sampling period (between 1 and 7).

# System Summary
https://s3.amazonaws.com/dsp132/dsp132.assignment.1.png

   Local Application uploads the file with the running parameters to S3.
    Local Application sends a message (queue) stating the location of the input file on S3.
    Local Application does one of the following:
        Starts the manager.
        Checks if a manager is active and if not, starts it. 
    Manager divides the entire requested period into smaller periods according to d.
    Manager distributes API-Calls and "Danger Analysis" jobs on the workers.
    Manager bootstraps nodes to process messages.
    Worker gets a period message from an SQS queue. Worker performs the requested API-Call to NeoWS. Worker deserializes response into JSON.
    Worker checks if asteroids in period answer to the "Danger Criteria".
    Worker puts a message in an SQS queue indicating an asteroid is dangerous enough to appear on the list together with the parameters used in the operation.
    Manager reads all Workers' messages from SQS and creates one summary file.
    Manager uploads the summary file to S3.
    Manager posts an SQS message about the summary file.
    Local Application reads SQS message.
    Local Application downloads the summary file from S3.
    Local Application creates html output file.
    Local application send a terminate message to the manager if it received terminate as one of its arguments. 

# Technical Stuff
# Which AMI Image to Use?
You can choose whatever image you want. You probably want to have one which supports user-data. If you don't want to choose on your own, you can just use this one: ami-b66ed3de use the small one.

# The AWS SDK
The assignment will be written in Java, you'll need the SDK for Java for it. We advise you to read the Getting Started guide, and get comfortable with the SDK.

You may use 3rd party Java clients for AWS, such as this or this.

# SQS Visibility Time-out
Read the following regarding SQS timeout, understand it, and use it in your implementation: Visibility Timeout

# Bootstrapping
When you create and boot up an EC2 node, it turns on with a specified image, and that's it. You need to load it with software and run the software in order for it to do something useful. We refer to this as "bootstrapping" the machine.

The bootstrapping process should download .jar files from an S3 bucket and run them. In order to do that, you need a way to pass instructions to a new node. You can do that using this guide, and another guide. User-data allows you to pass a shell-script to the machine, to be run when it starts up. Notice that the script you're passing should be encoded to base64. Here's a code example of how to do that.

If you use an AMI without Java or the AWS SDK for Java, you will need to download and install them via the bootstrap script.

Downloading from the console: In Linux, the command wget is usually installed. You can use it to download web files from the shell.

Example: wget http://www.cs.bgu.ac.il/~dsps171/Main -O dsp.html will download the content at http://www.cs.bgu.ac.il/~dsp171/Main and save it to a file named dsp.html. wget man

Installing from the console: In Ubuntu (or Debian) Linux, you can use the apt-get command (assuming you have root access to the machine). Example: apt-get install wget will install the wget command if it is not installed. You can use it to install Java, or other packages. apt-get man.

shell scripting with bash: your user-data scripts can be written in any language you want (e.g. Python, Perl, tsch, bash). bash is a very common choice. Your scripts are going to be very simple. Nonetheless, you might find these bash tutorials useful.

Tips on Writing to Files using Java
Writing Files.

# Checking if a Manager Node is Active
You can check if the manager is running by listing all your running instances, and checking if one of them is a manager. Use the "tags" feature of Amzon EC2 API to mark a specific instance as one running a Manager:

   using tags
    CreateTagsRequest API 

Making RESTful API-Calls and JSON Deserialization
The NASA Website describes an API for reading its database of Near Earth Objects. It is recommended that you use Apache's HttpClient API to create your HTTP requests to the API. Consider using Java's JSONObject for deserializing the resuit.
"Danger Analysis"
To analyze the danger of each asteroid, we are going to use a simple function.

   First of all, we only consider asteroids in each period that have their is_potentially_hazardous_asteroid flag as 'true'
    Then we choose the appropriate "Danger" level for each asteroid according to the following criteria:
        Green - if speed is less than 10 km/s && hazardous asteroid
        Yellow - if speed is more than 10 km/s && estimated diameter is less than 200 m && hazardous asteroid
        Red - if speed is more than 10 km/s && estimated diameter is more than 200 m && miss distance is less than 0.3 AU (astronomical unit) && hazardous asteroid 

# Grading

   The assignment will be graded in a frontal setting.
    All information mentioned in the assignment description, or learnt in class is mandatory for the assignment.
    You will be reduced points for not reading the relevant reading material, not implementing the recommendations mentioned there, and not understanding them.
    Students belonging to the same group will not necessarily receive the same grade.
    All the requirements in the assignment will be checked, any missing functionality will cause a point reduction. Any additional functionality will compensate for lost points. You have the "freedom" to choose how to implement things that are not precisely defined in the assignment. 

# Notes
Cloud services are cheap but not free. Even if they were free, waste is bad. Therefore, please keep in mind that:

   It should be possible for you to easily remove all the things you put on S3. You can do that by putting them in a specified bucket under a folder, which you could delete later.
    While it is the Manager's job to turn off all the Worker nodes, do verify yourself that all the nodes really did shut down, and turn of the manger manually if it is still running.
    You won't be able to download the files unless you make them public.
    You may assume there will not be any race conditions; conditions were 2 local applications are trying to start a manger at the same time etc. 

# A Very Important Note about Security
As part of your application, you will have a machine on the cloud contacting an amazon service (SQS and S3). For the communication to be successful, the machine will have to supply the service with a security credentials (password) to authenticate. Security credentials is sensitive data – if someone gets it, they can use it to use amazon services for free (from your budget). You need to take good care to store the credentials in a secure manner. One way of doing that is by compressing the jar files with a password.

# Submission
Use the submission system to submit a zip file that contains:

   all sources, with the libraries that you've used;
    the output of running your system on the dates of this semester (30/10/2016 - 27/01/2017) with any n and d (try several different values for n and d and make sure you get the same results);
    the statistics file of each one of the workers; 1. how many asteroids did it parse 2. how many asteroids were "Dangerous". how many asteroids are totally safe.
    a text file called README with instructions on how to run your project, and an explanation of how your program works. Your README must also contain what type of instance you used (ami + type:micro/small/large…), how much time it took your program to finish working on the given parameters, and what was the n you used. It must also contain both of your names and your IDs.
    Read Mandatory Requirements section. 

# Mandatory Requirements

   Be sure to submit a README file. Does it contain all the requested information? If you miss any part, you will lose points. Yes including your names and ids.
    Did you think for more than 2 minutes about security? Do not send your credentials in plain text!
    Did you think about scalability? Will your program work properly when 1 million clients connected at the same time? How about 2 million? 1 billion? Scalability is very important aspect of the system, be sure it is scalable!
    What about persistence? What if a node dies? Have you taken care of all possible outcomes in the system? Think of more possible issues that might arise from failures. What did you do to solve it? What about broken communications? Be sure to handle all fail-cases!
    Threads in your application, when is it a good idea? When is it bad? Invest time to think about threads in your application!
    Did you run more than one client at the same time? Be sure they work properly, and finish properly, and your results are correct.
    Do you understand how the system works? Do a full run using pen and paper, draw the different parts and the communication that happens between them.
    Did you manage the termination process? Be sure all is closed once requested!
    Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!
    Are all your workers working hard? Or some are slacking? Why?
    Lastly, are you sure you understand what distributed means? Is there anything in your system awaiting another?
    All of this need to be explained properly and added to your README file. In addition to the requirements above.
<<<<<<< HEAD
    Remember: Asteroids aren't going to kill you nor cancel the finals, happy semester :). 
=======
    Remember: Asteroids aren't going to kill you nor cancel the finals, happy semester :). 
>>>>>>> a48f8b7a18ac398ca891cbc32e8c925ec8612c20
