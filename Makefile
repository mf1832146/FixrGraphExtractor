# Makefile for Test Execution of Fixr Graph Extractor
# Author: Rhys Braginton Pettee Olsen <rhol9958@colorado.edu>
# CUPLV Lab, Boulder, Colorado
# Last updated: 2016-05-19
#
# As of this writing, this Makefile is for executing the main method of the Graph Extractor for testing purposes.

### CAUSE FOR `Exception in thread "main" java.lang.RuntimeException: couldn't find class: java.lang.Object (is your soot-class-path set properly?) Try adding rt.jar to Soot's classpath, e.g.:`:
# An version of rt.jar incompatible with the Java compiled used is specified. A version matching the locally installed Java 1.7 compiler must be selected. By default, the version of rt.jar corresponding with the latest version of the 1.7 JDK is selected. We suggest selecting from the list generated by the following shell command:
# `find / -name "rt.jar" 2>/dev/null | grep 1.7`
# If this doesn't work, consider uncommenting one of the pre-selected rt.jar commands below.
# RULED OUT AS CAUSE (as of this writing):
#   * Version of Java (removed version from build; tried different rt.jar files)
#   * Order of classpath entries for every classpath variable
#   * Wrong extractor .jar file

SCALA = scala
# SCALA = sbt run
# JAVA = java
# JAVA = /Library/Java/JavaVirtualMachines/jdk1.7.0_80.jdk/Contents/Home/bin/java

SOOT = ./lib/soot-2.5.0.jar

EXTRACTOR = ./target/scala-2.10/fixrgraphextractor_2.10-0.1-SNAPSHOT.jar

MAIN_CLASS = edu.colorado.plv.fixr.Main

# OSX 10.10.5 (Tested on Hedy): Uncomment one of the following:
# RT = /Library/Java/JavaVirtualMachines/jdk1.7.0_71.jdk/Contents/Home/jre/lib/rt.jar
# RT = /Library/Java/JavaVirtualMachines/jdk1.7.0_79.jdk/Contents/Home/jre/lib/rt.jar
# RT = /Library/Java/JavaVirtualMachines/jdk1.7.0_80.jdk/Contents/Home/jre/lib/rt.jar

# Linux (Tested on Sergio's Machine):
# RT = /usr/lib/jvm/jdk1.7.0/jre/lib/rt.jar

TEST = ./src/test/resources

ANDROID = ./build/resources/test/libs/android.jar

TEST_CLASS = simple.Simple

TEST_METHOD = main

ANDROID_TEST_CLASS = androidtests.HelloWorldActivity

ANDROID_TEST_METHOD = onCreate

ifndef RT
$(error RT is not set. Please uncomment or write a suitable assignment of RT to the location of your Java installation\'s rt.jar file)
endif

android:
	$(SCALA) -cp $(SOOT):$(RT):$(EXTRACTOR) $(MAIN_CLASS) $(RT):$(ANDROID):$(TEST) $(ANDROID_TEST_CLASS) $(ANDROID_TEST_METHOD)

test:
	$(SCALA) -cp $(SOOT):$(RT):$(EXTRACTOR) $(MAIN_CLASS) $(RT):$(TEST) $(TEST_CLASS) $(TEST_METHOD)
