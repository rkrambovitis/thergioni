FLAGS= -Xlint:unchecked -Xlint:deprecation
SRCFILES= Thergioni.java
JAVAC= /usr/bin/javac

all:
	$(JAVAC) $(FLAGS) $(SRCFILES)

clean: 
	rm -f ./*.class */*.class

clgen:
	rm -f generated/*

gen: 
	xjc Service.xsd 
