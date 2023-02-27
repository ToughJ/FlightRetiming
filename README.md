# FlightRetiming
This repository contains datasets related to flight delays and the related code about modeling, which were used in the paper titled "Flight Retiming Problem under Time-Dependent Uncertainty".
#### Code
**These are the custom classes that exist in this project.**
- rtData.java   
which include 
  - Flight.class    
  - Airport.class

**This is the class used to build the robust time-dependent model for our time-dependent schedul(TDS)**
- work_sos_hd.java

**This is the class used to build the robust non-time-dependent model for the non-time-dependent schedule (NTDS)**
- work_fb.java

**These are the classes used to generate the evaluation delay data and conduct the evaluation.**
- scenarioGenerator.java
- evaulatePerformance.java

#### Data
There are data obtain from ASQP for robust time-dependent model and robust non-time-dependent model and also the generated the scenario set for evaluation.

**This is the data for the basic flight information such as flight numbers, departure and arrival times, and delay information provided by ASQP.**
- Month8_Flights.csv


**This is the data pertaining to the robust time-dependent model used to generate the Time-Dependent Schedule (TDS).**
- EventBased package
 > {Our training set is constructed using data from a specific date range, namely June 22 to July 21. The data is organized into files named after the airport code and the type of event (departure or arrival). We extract the relevant data from these files to create our training set. Such as:
 - BeforeMonth8_ABQArrDelayStats.csv
 - BeforeMonth8_ABQDepDelayStats.csv}
 

**This is the data pertaining to the robust non-time-dependent model used to generate the Non-Time-Dependent Schedule (NTDS).**
-LegBased package
  This is also the training set, but for non-time-dependent model. For non-time-dependent models based on flight leg information, airport information is not necessary.
  - BeforeMonth8_LegBasedDelayStats.csv

Both the TDS and NTDS files contain sample mean and standard deviation values for each group, as well as the covariance matrix.

**The scenario set for evaluation is counstructed based on data from from July 22 to August 20. Nevertheless, The data is organized into files named after the airport code and the type of event (departure or arrival).**
Such as:
- Month8_ABQArrDelayStats.csv
- Month8_ABQDepDelayStats.csv


