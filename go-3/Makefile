CFLAGS  = 
LFLAGS  = 
JAVAC   = javac
A2PS    = a2ps
ARCHIVE = go3d.`date +%y%m%d`.tar.gz
CLIENT  = J3DApp.java

########  files



########  implicit rules

.SUFFIXES: .java

.java.class:
	$(JAVAC) $(CFLAGS) $<

########  build rules

default: all

help:
	@echo possible targets include:
	@echo "all         -  make "
	@echo "help        -  this "
	@echo "server	   -  compile the server"
	@echo "client	   -  compile the client"
	@echo "archive     -  pack the current sources into $(ARCHIVE)"
	@echo "jar	   -  nyi"
	@echo "help        -  nyi"
	@echo "javadoc	   -  generate javadoc documentation under directory JavaDoc"
	@echo "listing	   -  generate PDF file for GoGrid classes"
	@echo "clean       -  remove   backup  files  and  corefiles"
	@echo "distclean   -  remove   all  meta- and  object  files"


clean:
	rm -f *~ core*

distclean: clean
	rm -f *.class *.ps *.pdf

$(TARGET): $(OBJECTS)
	$(JAVAC) $(OBJECTS) -o $(TARGET)

help:	
	@echo "not yet implemented"

archive: 
	tar zcf $(ARCHIVE) *.java *.class *.form JavaDoc Makefile TO-DO # HISTORY

jar:	
	@echo "not yet implemented"

javadoc:	
	javadoc -d JavaDoc -author -version -private -breakiterator *.java

server:	
	javac GoGridServer.java
	@echo "Start the server with \"java GoGridServer\""

client: 
	$(JAVAC) $(CFLAGS) $(CLIENT)

all: server client

listing:
	$(A2PS) -o Thunen.ps -gE -l 110 *.java
	ps2pdf13 Thunen.ps Thunen.pdf && rm Thunen.ps