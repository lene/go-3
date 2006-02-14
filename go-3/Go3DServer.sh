#!/bin/sh


function checkjvm () {
  # I hope Sun doesn't change the output of "java -version"!
	VERSION=$(java -version 2>&1 | grep version | cut -d \" -f 2)
	echo ${VERSION} | grep "1.5" > /dev/null 2>&1 || \
	  nojava15
}

function nojava15 () {
  cat << END_OF_BALK
You don't have Java 1.5 installed. This is needed for the 3D Go game to run.
Either activate Java 1.5, if you have it on your system, or download and install
it from 
  http://java.sun.com/j2se/1.5.0/download.jsp
The JRE 5.0 Update X should be what you're looking for.
Your installed Java Virtual Machine is:
END_OF_BALK
  
  java -version
}

checkjvm

java GoGridServer