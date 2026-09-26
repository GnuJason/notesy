JAVA_HOME ?= /usr/lib64/jvm/java-21-openjdk
JAVAFX_LIB ?= /usr/share/java/openjfx
JAVAC ?= $(JAVA_HOME)/bin/javac
JAR ?= $(JAVA_HOME)/bin/jar
SOURCE_DATE_EPOCH ?= 1790294400
SOURCES := $(shell find src/main/java -name '*.java' | LC_ALL=C sort)

.PHONY: all clean source-dist check-metadata
all: target/notesy-1.0.jar

target/notesy-1.0.jar: $(SOURCES) $(shell find src/main/resources -type f)
	mkdir -p target/system-classes
	$(JAVAC) --release 21 -encoding UTF-8 -cp '$(JAVAFX_LIB)/*' -d target/system-classes $(SOURCES)
	cp -a src/main/resources/. target/system-classes/
	$(JAR) --create --file $@ --main-class org.stickynotefx.Launcher --date="$$(date -u -d @$(SOURCE_DATE_EPOCH) +%Y-%m-%dT%H:%M:%SZ)" -C target/system-classes .

check-metadata:
	desktop-file-validate packaging/notesy.desktop
	appstreamcli validate --no-net packaging/notesy.appdata.xml

source-dist:
	bash scripts/source-dist.sh

clean:
	rm -rf target