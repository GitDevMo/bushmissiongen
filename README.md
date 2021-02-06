# About

The tool generates all the directories and files needed, including default single color images, to compile a bush mission or landing mission. The input file is one single text file containing waypoint names, coordinates, generic mission data, nav log texts, etc. The input file can be generated by the tool from an existing flight plan (PLN file)! There are many options to use to trigger failures, warnings, play WAV files or TTS and much more.<br>
<br>
Project homepage on Flightsim.to:<br>
https://flightsim.to/file/3681/bushmissiongen#

# Development

Eclipse 2020-12 is the prefered IDE for developing BMG.

### Required downloads and installation

https://www.eclipse.org/downloads/packages/release/2020-12/r/eclipse-ide-rcp-and-rap-developers<br>
https://www.oracle.com/se/java/technologies/javase-jdk11-downloads.html<br
<br>
* Extract the Eclipse zip package to your home folder.
* Extract the JDK into the Eclipse folder created above.
* Edit the file eclipse.ini and add this ABOVE -vmargs:<br>
<code>-vm<br>
{The path to the Eclipse folder}\eclipse\jdk-11.0.8\bin\</code>

### Eclipse configuration and program start

* Right-click on the Starter.java file and select <b>Run As --> Run Configurations...</b>.
* Set the working directory to "${workspace_loc:BushMissonGen/run}".
* Run the application.

### Create JAR file

* Extract jar-in-jar-loader.zip from eclipse_install_dir/plugins/org.eclipse.jdt.ui_XXXXXXX.jar and place it next to the build script.
* Right click on build.xml and select <b>Run As --> Ant Build...</b>.
* Under "Environment", add an entry with name = "ECLIPSE_HOME" and value = "{the path to your Eclipse folder}".
* Run build.xml and make sure BushMissionGen.jar is created in the 'run' folder.
* Launch the JAR file.

# TO-DO

* What is the Overview.htm file used for in landing challenges?
* Leaderboards for landing challenges? Possible for 3rd party missions?
* Is there the possibility of setting the flight departure at the parking area instead of on the runway (leg 2-X)?
