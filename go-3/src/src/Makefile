I         = /opt/sun-java3d-bin/lib
CLASSPATH = .:/usr/share/sun-java3d-bin/lib/vecmath.jar:/usr/share/sun-java3d-bin/lib/j3dutils.jar:/usr/share/sun-java3d-bin/lib/j3dcore.jar
CFLAGS    = -Xlint:unchecked -cp $(CLASSPATH)
LFLAGS    = 
JAVAC     = javac 
A2PS      = a2ps
ARCHIVE   = go3d.`date +%y%m%d`.tar.gz


SERVER    = GoGridServer.java
CLIENT    = Go3DClient.java

########  files



########  implicit rules

.SUFFIXES: .java

.java.class:
	$(JAVAC) $(CFLAGS) $<


SERVER_SOURCES = Colour.java \
		 Player.java ConnectedPlayer.java \
		 GoGridProtocol.java GameProtocol.java ServerProtocol.java \
		 GameBase.java GoGrid.java GoGridServer.java \
		 Utility.java \
		 Game.java 
CLIENT_SOURCES = Colour.java Materials.java \
		 Cursor.java Stone.java GridObject.java Handicap.java Grid.java \
		 Player.java ConnectedPlayer.java \
		 ConnectionDialog.java ChooseGameDialog.java \
		 ClientProtocol.java GoGridProtocol.java \
		 GameBase.java GoGrid.java GoGridClient.java \
		 IntersectInfoBehavior.java SimpleBehavior.java \
		 Exchange.java ConnectionData.java \
		 Utility.java \
		 GridDisplay.java Go3DClient.java


########  build rules

default: distclean all

help:
	@echo possible targets include:
	@echo "all         -  make "
	@echo "help        -  this "
	@echo "server	   -  compile the server"
	@echo "client	   -  compile the client"
	@echo "archive     -  pack the current sources into $(ARCHIVE)"
	@echo "jar	  	   -  nyi"
	@echo "help        -  nyi"
	@echo "javadoc	   -  generate javadoc documentation under directory JavaDoc"
	@echo "listing	   -  generate PDF file for GoGrid source code"
	@echo "clean       -  remove   backup  files  and  corefiles"
	@echo "distclean   -  remove   all  meta- and  object  files"


clean:
	rm -f *~ core*

distclean: clean
	rm -f *.class *.ps *.pdf

$(TARGET): $(OBJECTS)
	$(JAVAC) $(OBJECTS) -o $(TARGET)

archive: 
	tar zcf $(ARCHIVE) *.java *.class *.form JavaDoc Makefile TO-DO # HISTORY

jar: all
	jar cf GoGrid.jar *.class

deploy: jar
	scp -P 10022 GoGrid.jar hyperspace-travel.de:

javadoc:	
	javadoc -d JavaDoc -author -version -private -breakiterator *.java

server:	$(SERVER_SOURCES)
	$(JAVAC) $(CFLAGS) $(SERVER)
	@echo "Start the server with \"java GoGridServer\""

client: $(CLIENT_SOURCES)
	$(JAVAC) $(CFLAGS) $(CLIENT)
	@echo "Start client with \"java -Djava.library.path=$(I) -cp $(CLASSPATH) GridDisplay\""

all: server client

listing:
	$(A2PS) -o GoGrid.ps -gE -l 110 *.java
	ps2pdf13 GoGrid.ps GoGrid.pdf && rm GoGrid.ps
