Media Delivery Network Simulator
============================

The purpose of the project is to build a “life sized” simulation of Internet-based media distribution, with a flexible framework that will allow tinkering, experimentation and evolution.
It allows the user to experiment with different media delivery algorithms and different type of loads and get different metrics like packet loss, latency, transfer rate. These metrics can be used to figure out which delivery strategy works best in a given scenario.

For more details, please refer the docs folder.

License
================
The project is released under BSD-3 license.

Project Members
====================================
Advisors: Vladimir Katardjiev, Alvin Jude, Jia Zhang

Developers: Geng Fu (Jeremy-Fu), Jigar Patel (jigarbjpatel), Vinay Kumar Vavilli (vinaykumar1690), Hao Wang (davidbuick)

Contact: To contact developers, please use the github handle given in brackets and to contact advisors, please let any of the developer know.


How to Run?
===========================
There are 3 majors components to start to make this application running.

1. Master node - Run the Master.java file and when it says that it is registered then start the other two components.
2. WebClient - Open any web browser (preferably Chrome) and go to http://localhost:8888/static/index.html (replace localhost by Master Node IP Address)
3. NodeContainer(s) - Run the NodeContainer.java file with argument label:NodeContainerName 

Once all the components are up, upload a simultion script as per format given in demo-1-flowlist-source-processing-relay-sink.json file (in test/demo folder) and select Start Flow (or post the json object to Start Flow API resource mentioned below)

To stop a flow, upload the Json file in same format as used above. Just ensure that you have only those flows in the file which you want to stop. 

For more details on file format, please refer to the Technical Report in docs folder.

Note: We are in process of creating Runnable Jar files and docker file which will simplify starting of the simulator.

Important APIs
==============================
1. <a name="1"></a>**START FLOW**
    - **Purpose**: To start a new flow or add new flow to existing simulation.
    - **Method**: POST
    - **URL**: WebClientURI/work_config
  
2. <a name="2"></a>**STOP FLOW**
    - **Purpose**: To stop existing flow.
    - **Method**: DELETE
    - **URL**: WebClientURI/work_config

3. <a name="3"></a>**RESET SIMULATION**
    - **Purpose**: To reset the entire simulation.
    - **Method**: DELETE
    - **URL**: WebClientURI/simulations

Dependencies
===============================
To run the system

1. Java version 7 and above
2. Browser with support for SVG rendering

To extend the system

1. Access to Ericsson Warp and Trap Libraries
2. Maven
