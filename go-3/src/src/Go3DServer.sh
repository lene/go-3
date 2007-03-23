#!/bin/sh

set +xv

CONFIG_DIR="${HOME}/.3D-Go/client"
CONFIG_FILE="${CONFIG_DIR}/config"

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

		GOGRID_JARPATH=$($LOCATE_COMMAND ${GOGRID_JARNAME})
		GOGRID_INSTALLATIONS=$(echo ${GOGRID_JARPATH} | wc -w)
		if test ${GOGRID_INSTALLATIONS} -eq 0; then
			nogogrid
		elif test ${GOGRID_INSTALLATIONS} -gt 1; then
			toomanygogrid
		fi
	fi
	
	echo "GOGRID_JARPATH=\"${GOGRID_JARPATH}\"" >> "${CONFIG_FILE}"
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
or by editing the config file, ${CONFIG_FILE} and adding a line like
GOGRID_JARPATH=/path/to/correct/${GOGRID_JARNAME}
END_OF_BALK4
		
	exit 1
}

#
#	end of function declarations
#

#
#	main program
#
checkjvm

checkjar


CLASSPATH=${CLASSPATH}:"${GOGRID_JARPATH}"

java ${EXTRA_JVM_ARGS}  -cp "${CLASSPATH}" \
	net.hyperspacetravel.go3.server.GoGridServer "$@"