FLAGS= -Xlint:unchecked -Xlint:deprecation
SRCFILES= Thergioni.java
JAVAC= /usr/bin/javac
CURDIR= $(shell pwd)
CPATH= $(CURDIR):$(CURDIR)/ext/*

all:
	$(JAVAC) -cp $(CPATH) $(FLAGS) $(SRCFILES)

clean: 
	rm -f ./*.class */*.class

clgen:
	rm -f generated/*

gen: 
	xjc Service.xsd 
