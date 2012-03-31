import java.io.*;
import java.util.*;
import javax.xml.bind.*;
import generated.*;

class Cascade {
	public static void main(String args[]) {
		Cascade myCascade = new Cascade();
		if (args.length == 0 ) {
			myCascade.printUsage();
			System.exit(0);
		}
		//myCascade.parseFile(args[0]);
		//myCascade.doXmlStuff();
		myCascade.readXml(args[0]);
	}

	public Cascade() {
		of = new ObjectFactory();	
	}

	private void printUsage() {
		System.out.println("Usage: java Cascade <path_to_config>");
	}

	private void parseFile(String firstArg) {
		confFile=new String(firstArg);
		try {
			String strLine;
			BufferedReader in = new BufferedReader(new FileReader(confFile));
			while ((strLine = in.readLine()) != null) {
				System.out.println(strLine);
			}
			in.close();
		} catch (IOException e) {
			System.err.println(e);
		}
	}

	private void doXmlStuff() {
		//Service whatever = of.createService();
		//whatever.setName("asd");
		//System.out.println(whatever.getName());
		//printXml(whatever);
		//unMarshal("/home/noc/cascade/conf.d/foo.xml");
		File xmlFiles[] = getFiles(this.confPath);
		for (File f : xmlFiles) {
			String fileName=f.getName();
			if (fileName.substring(fileName.length()-4,fileName.length()) == ".xml") 
				System.out.println(fileName);
			//readXml(confPath+f.getName());
		}
	}

	private File[] getFiles(String path) {
		try {
			File files[] = new File(path).listFiles();
			//for (File f : files) {
			//	System.out.println(f.getName());
			//}
			return files;
		} catch (Exception e) {
			System.out.println(path + "is not a valid directory");
			System.err.println(e);
			System.exit(1);
		}
		return null;
	}

	private void printXml(Site.Service printMe) {
		try {
			//JAXBElement<Service> gl = of.createService(printMe);
			JAXBContext jc = JAXBContext.newInstance("Service");
			Marshaller m = jc.createMarshaller();
			m.marshal( printMe, System.out );
		} catch( JAXBException jbe ){
			System.err.println(jbe);
		}
	}

	/*
	 * Thi method parses an xml file an called processNode and processService
	 */
	private void readXml(String fileName) {
		//String packageName = docClass.getPackage().getName();
		//JAXBElement<Service> koko = (JAXBElement<Service>)u.unmarshal( new FileInputStream(fileName));
		try {
			JAXBContext jc = JAXBContext.newInstance(Site.class);
			Unmarshaller u = jc.createUnmarshaller();
			Site mySite = (Site)u.unmarshal( new FileInputStream(fileName));
			System.out.println(mySite.getName());

			System.out.println("Printing out Services and Checks");
			List<Site.Service> serviceList = new ArrayList<Site.Service>();
			serviceList = mySite.getService();
			for (Site.Service s: serviceList ) {
				processService(s);
			}

			System.out.println("Printing out Nodes, their services and arguments");
			Site.Nodes nodeList = (Site.Nodes)mySite.getNodes();
			List<Site.Nodes.Node> nodes = new ArrayList<Site.Nodes.Node>();
			nodes = nodeList.getNode();
			for ( Site.Nodes.Node n : nodes ) {
				processNode(n);
			}
		} catch (Exception fnfe) {
			System.err.println(fnfe);
		}
	}

	private void processNode(Site.Nodes.Node node) {
		List<String> nodeType = new ArrayList<String>();
		String nodeName = node.getName();
		System.out.println(nodeName);
		nodeType = node.getType();
		for ( String tp : nodeType) {
			System.out.println(" + " + tp);
		}
		List<Site.Nodes.Node.Checkargs> checkArgs= new ArrayList<Site.Nodes.Node.Checkargs>();
		checkArgs=node.getCheckargs();
		for ( Site.Nodes.Node.Checkargs ca : checkArgs ) {
			System.out.println(ca.getCheck() + " " + ca.getArgs());
		}

	}

	private void processService(Site.Service service) {
		System.out.println(service.getName());
		System.out.println(" + " + service.getCheck());
	}
	
	private ObjectFactory of;
	private String confFile;
	private String confPath;
}
