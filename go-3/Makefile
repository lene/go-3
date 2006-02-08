I         = /opt/sun-java3d-bin/lib
CLASSPATH = .:/usr/share/sun-java3d-bin/lib/vecmath.jar:/usr/share/sun-java3d-bin/lib/j3dutils.jar:/usr/share/sun-java3d-bin/lib/j3dcore.jar
CFLAGS    = -Xlint:unchecked -cp $(CLASSPATH)
LFLAGS    = 
JAVAC     = javac 
A2PS      = a2ps
ARCHIVE   = go3d.`date +%y%m%d`.tar.gz


SERVER    = GoGridServer.java
CLIENT    = GridDisplay.java

########  files



########  implicit rules

.SUFFIXES: .java

.java.class:
	$(JAVAC) $(CFLAGS) $<


SERVER_SOURCES = Colour.java GoGrid.java GoGridProtocol.java GoGridServer.java \
		 Player.java ConnectedPlayer.java GameBase.java Utility.java \
		 Game.java
CLIENT_SOURCES = Colour.java Cursor.java \
		 ConnectedPlayer.java GameBase.java \
		 Exchange.java GoGrid.java GoGridClient.java GoGridProtocol.java \
		 Grid.java GridObject.java Handicap.java ConnectionDialog.java \
		 IntersectInfoBehavior.java ConnectionData.java \
		 GridDisplay.java Player.java SimpleBehavior.java Stone.java \
		 Utility.java GridDisplay.java
UNUSED_SOURCES = SettingsDialog.java StartupDialog.java		


########  build rules

default: distclean all

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

archive: 
	tar zcf $(ARCHIVE) *.java *.class *.form JavaDoc Makefile TO-DO # HISTORY

jar: all
	jar cf GoGrid.jar *.class

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
	$(A2PS) -o Thunen.ps -gE -l 110 *.java
	ps2pdf13 Thunen.ps Thunen.pdf && rm Thunen.ps
