#!/bin/sh

# Sigh. Java3D installation and usage usually is a mess. 
# You have to add the files j3dcore.jar, j3dutils.jar and vecmath.jar to your
# CLASSPATH. Also, if you use Sun's Java3D distribution, you have to add the
# provided libj3dcore-ogl.so to the java.library.path. That way, you end up with
# a command line to start up a Java3D application like 
#   java -Djava.library.path=/opt/sun-java3d-bin/lib \
#     -cp /usr/share/sun-java3d-bin/lib/vecmath.jar:/usr/share/sun-java3d-bin/lib/j3dutils.jar:/usr/share/sun-java3d-bin/lib/j3dcore.jar:. 
#     Go3DClient
# To save you that, this script tries to guess the correct Java3D installation 
# path. I have only tested it with the Sun Java3D distribution though.
# Also makes sure that you have Java 1.5 installed. I'm not yet checking for
# higher versions, as 1.5 is relatively fresh and 1.6 has not even been 
# discussed yet.


set +xv


J3D_LOCATION=
J3D_INSTALLATIONS=0
J3D_PATH=
J3D_LIBPATH=
# hack to choose the wanted j3d installation
num_installed_j3d=2
EXTRA_JVM_ARGS="-ea"


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

function noj3d() {
	cat << END_OF_BALK
Java3D is not installed on your system or could not be found.
The Java3D extension is necessary to run the 3D Go game.

You can download it from 
  http://java.sun.com/products/java-media/3D/download.html

If you use Gentoo Linux, try 
  emerge dev-java/sun-java3d-bin					or
  emerge dev-java/blackdown-java3d-bin		, depending on your installed JVM.

On FreeBSD, do
  cd /usr/ports/java/java3d; make install	, or simply
  portinstall java/java3d
			
On Windows, OS/X or any other than the described UNIX/Linux Dialects, I'm
afraid I can't help you with the installation. You'll have to find out by
yourself. Maybe you can find some help at
  http://java.sun.com/products/java-media/3D/java3d-install.html
END_OF_BALK

	exit 1
}

function toomanyj3d () {
echo ${J3D_LOCATION}
	cat << END_OF_BALK2
I can't automatically determine which one to use.
Please add the correct installation to your CLASSPATH before running the 
3D Go game, like this:
  CLASSPATH=\$CLASSPATH:/path/to/correct/installation/j3dcore.jar
  CLASSPATH=\$CLASSPATH:/path/to/correct/installation/j3dutil.jar
  CLASSPATH=\$CLASSPATH:/path/to/correct/installation/vecmath.jar
  export CLASSPATH

Also, if you use the Sun Java3D Engine, it relies on finding libj3dcore-ogl.so
somewhere on your System. Make sure it is there.
END_OF_BALK2
		
	exit 1
}

function checkj3d () {
	test x${J3D_PATH} != x && return

	J3D_LOCATION=$(locate j3dcore.jar)
	J3D_INSTALLATIONS=$(echo ${J3D_LOCATION} | wc -w)
	
	echo ${J3D_INSTALLATIONS} Java3D installation\(s\) found

	J3D_PATH=$(dirname $(echo ${J3D_LOCATION} | cut -d ' ' -f ${num_installed_j3d}))
	J3D_LIBPATH=$(dirname $(locate libj3dcore-ogl.so))

	if test ${J3D_INSTALLATIONS} -eq 0; then
		noj3d
	elif test ${J3D_INSTALLATIONS} -gt 1; then
		echo ${CLASSPATH} | grep j3dcore.jar > /dev/null 2>&1 || \
			toomanyj3d
	fi
}

function j3dincomplete () {
	echo "Java3D seems to be incompletely installed: ${1} is not present in"
	echo ${J3D_PATH}
	exit 1
}

function addtoclasspath () {
  echo ${CLASSPATH} | grep ${1} >/dev/null 2>&1
  if test $? -eq 1 ; then
  	checkj3d
    if test -f ${J3D_PATH}/${1}; then
      CLASSPATH=${CLASSPATH}:${J3D_PATH}/${1}
    else
      j3dincomplete ${1}
    fi
  fi
}

checkjvm

checkj3d

# check whether Java3D is in the CLASSPATH
echo ${CLASSPATH} | grep j3dcore.jar > /dev/null 2>&1

if test $? -ne 0; then
	CLASSPATH=${CLASSPATH}:${J3D_PATH}/j3dcore.jar
fi

addtoclasspath j3dutils.jar 
addtoclasspath vecmath.jar

CLASSPATH=${CLASSPATH}:GoGrid.jar

java ${EXTRA_JVM_ARGS} -Djava.library.path=${J3D_LIBPATH} -cp ${CLASSPATH} Go3DClient $@ 
