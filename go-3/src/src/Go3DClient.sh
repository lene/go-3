#!/bin/sh

# Sigh. Java3D installation and usage usually is a mess. 
# You have to add the files j3dcore.jar, j3dutils.jar and vecmath.jar to your
# CLASSPATH. Also, if you use Sun's Java3D distribution, you have to add the
# provided libj3dcore-ogl.so to the java.library.path. That way, you end up with
# a command line to start up a Java3D application like 
#   java -Djava.library.path=/opt/sun-java3d-bin/lib \
#     -cp /usr/share/sun-java3d-bin/lib/vecmath.jar:\
#		  /usr/share/sun-java3d-bin/lib/j3dutils.jar:\
#		  /usr/share/sun-java3d-bin/lib/j3dcore.jar:.
#     Go3DClient
# To save you that, this script tries to guess the correct Java3D installation 
# path. I have only tested it with the Sun Java3D distribution though.
# Also makes sure that you have at least Java 1.5 installed.

set +xv

CONFIG_DIR="${HOME}/.3D-Go/client"
CONFIG_FILE="${CONFIG_DIR}/config"

J3D_SEARCHROOT="/usr /opt"

J3D_LOCATION=
J3D_INSTALLATIONS=0
J3D_PATH=
J3D_LIBPATH=
# hack to choose the wanted j3d installation
num_installed_j3d=2
EXTRA_JVM_ARGS="-ea"

GOGRID_JARPATH=
GOGRID_JARNAME=GoGrid.jar

test -f "${CONFIG_FILE}" && . "${CONFIG_FILE}"

if locate -h > /dev/null ; then 
	LOCATE_COMMAND="locate"
else 
	LOCATE_COMMAND="find ${J3D_SEARCHROOT} -name"
fi
	
#
#   config ends.
#

#
#	function declarations
#

#
#   check whether installed JVM is at least version 1.5
#
function checkjvm () {
  # I hope Sun doesn't change the output of "java -version"!
	VERSION=$(java -version 2>&1 | grep version | cut -d \" -f 2)
	MAJOR=$(echo ${VERSION} | cut -d '.' -f 1)
	MINOR=$(echo ${VERSION} | cut -d '.' -f 2)

	test $MAJOR -gt 1 && return
	test $MINOR -ge 5 && return

	nojava15
}

#
#   complain that java VM is too old
#
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

#
#	Search for a Java3D installation and set the variables J3D_PATH and
#	J3D_LIBPATH acodingly. If they aren't yet, they will be written to the
#	config file.
#	If more than one instance of Java3D is found, check whether the variable
#	num_installed_j3d is set. This variable picks the xth installation out of
#	the list of installed Java3Ds (beginning with 1).
#	If no Java3D is found, or if there are multiple instances without a
#	num_installed_j3d defined, abort.
#
function checkj3d () {
	test "x${J3D_PATH}" != x && return

	J3D_LOCATION="$($LOCATE_COMMAND j3dcore.jar)"
	J3D_INSTALLATIONS=$(echo "${J3D_LOCATION}" | wc -l)
	
	echo ${J3D_INSTALLATIONS} Java3D installation\(s\) found

	if test ${J3D_INSTALLATIONS} -eq 0; then
		noj3d
	elif test ${J3D_INSTALLATIONS} -gt 1; then
		echo ${CLASSPATH} | grep j3dcore.jar > /dev/null 2>&1 || \
			test ${num_installed_j3d} || \
				toomanyj3d
	fi

	if test ${num_installed_j3d}; then 
		J3D_PATH=$(dirname $(echo ${J3D_LOCATION} | cut -d ' ' -f ${num_installed_j3d}))
	else
		J3D_PATH=$(dirname $(echo ${J3D_LOCATION} | cut -d ' ' -f 1))
	fi
	
	J3D_LIBS=$($LOCATE_COMMAND libj3dcore-ogl.so)
	J3D_INSTALLATIONS=$(echo ${J3D_LIBS} | wc -w)

	if test ${J3D_INSTALLATIONS} -eq 0; then
		noj3d
	elif test ${J3D_INSTALLATIONS} -gt 1; then
		test ${num_installed_j3d} || toomanyj3d
	fi

	if test ${num_installed_j3d}; then 
		J3D_LIBPATH=$(dirname $(echo ${J3D_LIBS} | cut -d ' ' -f ${num_installed_j3d}))
	else
		J3D_LIBPATH=$(dirname $(echo ${J3D_LIBS} | cut -d ' ' -f 1))
	fi

	echo "J3D_PATH=\"${J3D_PATH}\"" >> "${CONFIG_FILE}"
	echo "J3D_LIBPATH=\"${J3D_LIBPATH}\"" >> "${CONFIG_FILE}"
}

#
#	find the GoGrid jar file and set the variable GOGRID_JARPATH. If it isn't 
#	yet, it will be written to the config file.
#
function checkjar () {
	#	if a jarfile is present in the current dir, it has precedence
	if test -f ./"${GOGRID_JARNAME}"; then
		GOGRID_JARPATH="$(pwd)/${GOGRID_JARNAME}"
	else 
		#	if jar path is already set, do nothing
		test "x${GOGRID_JARPATH}" != x && test -f "${GOGRID_JARPATH}" && return	

		GOGRID_JARPATH="$($LOCATE_COMMAND ${GOGRID_JARNAME})"
		GOGRID_INSTALLATIONS=$(echo "${GOGRID_JARPATH}" | wc -l)
		if test ${GOGRID_INSTALLATIONS} -eq 0; then
			nogogrid
		elif test ${GOGRID_INSTALLATIONS} -gt 1; then
			toomanygogrid
		fi
	fi
	
	echo "GOGRID_JARPATH=\"${GOGRID_JARPATH}\"" >> "${CONFIG_FILE}"
}

#
#	complain that Java3D is not installed
#
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

#
#	complain that there is more than one Java3D installation. I can't guess
#	which one to use.
#
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

#
#	complain that important parts of Java3D are missing
#
function j3dincomplete () {
	echo "Java3D seems to be incompletely installed: ${1} is not present in"
	echo ${J3D_PATH}
	exit 1
}

#
#	complain that $GOGRID_JARNAME is missing
#
function nogogrid () {
	cat << END_OF_BALK3
3D-Go is apparently incompletely installed on your system - the JAR file,
${GOGRID_JARNAME}, could not be found.

Please download a complete installation from 
  http://sourceforge.net/project/showfiles.php?group_id=39112
END_OF_BALK3

	exit 1
}

#
#	complain that there is more than one $GOGRID_JARNAME. I can't guess which
#	one to use.
#
function toomanygogrid () {
	cat << END_OF_BALK4
There is more than one instance of ${GOGRID_JARNAME} on your system. I can't 
automatically determine which one to use.

Please tell the startup script the path to the relevant ${GOGRID_JARNAME}, either
by setting the environment variable GOGRID_JARPATH before calling the script,
or by editing the config file, ${CONFIG_FILE} 
and adding a line like:
GOGRID_JARPATH=/path/to/correct/${GOGRID_JARNAME}
END_OF_BALK4
		
	exit 1
}

#
#	add a .jar file to $CLASSPATH, if it isn't yet in there
#
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

#
#	end of function declarations
#

#
#	main program
#
mkdir -p "${CONFIG_DIR}"

checkjvm

checkj3d

checkjar

for jarfile in j3dcore.jar j3dutils.jar vecmath.jar; do
	# check whether Java3D is in the CLASSPATH
	echo ${CLASSPATH} | grep ${jarfile} > /dev/null 2>&1
	if test $? -ne 0; then
		CLASSPATH=${CLASSPATH}:${J3D_PATH}/${jarfile}
	fi
done

CLASSPATH=${CLASSPATH}:"${GOGRID_JARPATH}"

java ${EXTRA_JVM_ARGS} -Djava.library.path=${J3D_LIBPATH} -cp "${CLASSPATH}" \
	net.hyperspacetravel.go3.client.gui.Go3DClient "$@"
