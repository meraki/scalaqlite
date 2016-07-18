# Copyright (c) 2010 Sean C. Rhea <sean.c.rhea@gmail.com>
# All rights reserved.
#
# See the file LICENSE included in this distribution for details.

SCALA_VERSION=2.11

ifeq ($(shell uname),Darwin)
LIBEXT=dylib
else
LIBEXT=so
endif

ifndef JAVA_HOME
JAVA_HOME=/usr/lib/jvm/java-1.7.0-openjdk-i386
endif

TARGET=libscalaqlite.$(LIBEXT)

all: $(TARGET)

clean:
	rm -f Sqlite3C.h $(TARGET)

.PHONY: all, clean

libscalaqlite.so: Sqlite3C.cc Sqlite3C.h
	g++ -O2 -o $@ -shared -Wl,-soname,libscalaqlite.so \
	        -I$(JAVA_HOME)/include \
	        -I$(JAVA_HOME)/include/linux \
		$< -lc -lsqlite3 -fPIC

libscalaqlite.dylib: Sqlite3C.cc Sqlite3C.h
	g++ -O2 -dynamiclib -o $@ \
		-I /System/Library/Frameworks/JavaVM.framework/Versions/Current/Headers \
		-L/opt/local/lib \
		$< -lsqlite3

Sqlite3C.h: ../../../target/scala-$(SCALA_VERSION)/classes/org/srhea/scalaqlite/Sqlite3C.class
	javah -classpath ../../../target/scala-$(SCALA_VERSION)/classes org.srhea.scalaqlite.Sqlite3C
