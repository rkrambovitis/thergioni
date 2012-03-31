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
		checkMap = new HashMap<String,List<String>>();
		argMap = new HashMap<String,String>();
		//nodeCheckMap = new HashMap<String,String>();
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

	private void printXml(Site.Type printMe) {
		try {
			//JAXBElement<Service> gl = of.createService(printMe);
			JAXBContext jc = JAXBContext.newInstance("Type");
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
			System.out.println("Site Name : " +mySite.getName());
			checkPath = new String(mySite.getCheckpath());
			if (!checkPath.substring(checkPath.length()-1,  checkPath.length()).equals("/")) {
				checkPath = checkPath + "/";
			}
			System.out.println("checks path : " +checkPath + "\n");

			
			System.out.println("Processing Types and Checks definitions");
			List<Site.Type> typeList  = new ArrayList<Site.Type>();
			typeList = mySite.getType();
			for (Site.Type s: typeList ) {
				processTypes(s);
			}
			System.out.println("Processing Types complete\n");
			

			System.out.println("Processing Nodes");
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

	private void processTypes(Site.Type type) {
		String typeName = type.getName();
		System.out.println(typeName);

		checkMap.put(typeName, type.getCheck());
		if (type.getCheck().isEmpty()) {
			System.out.println("Warning: No Checks Defined for Type: " + type.getName());
		}
		for ( String chk : type.getCheck()) {
			System.out.println(" + " + chk);
		}
	}

	private void processNode(Site.Nodes.Node node) {
		List<String> nodeType = new ArrayList<String>();
		String nodeName = node.getName();
		String nodeIP = node.getIp();
		System.out.println("\nNode: " + nodeName);

		//List<Site.Nodes.Node.Checkargs> checkArgs= new ArrayList<Site.Nodes.Node.Checkargs>();
		//checkArgs=node.getCheckargs();
		//for ( Site.Nodes.Node.Checkargs ca : checkArgs ) {
		for ( Site.Nodes.Node.Checkargs ca : node.getCheckargs() ) {
			argMap.put(nodeName + "_" + ca.getCheck(), ca.getArgs());
		}

		//nodeType = node.getType();
		for ( String ntype : node.getType() ) {
			List<String> whatever = new ArrayList<String>();
			whatever = checkMap.get(ntype);
			if ( whatever != null) {
				for ( String ntcheck : whatever) {
					String ca = argMap.get(nodeName+"_"+ntcheck);
					if (ca != null) {
						ntcheck = ntcheck+" "+ca;
					}
					ntcheck=ntcheck.replaceAll("\\$h", nodeName);
					try {
						ntcheck=ntcheck.replaceAll("\\$i", nodeIP);
					} catch (NullPointerException e) {
						System.out.println("Error, $i is used but no ip is defined for "+nodeName);
					}
					if (!ntcheck.substring(0,1).equals("/")) {
						ntcheck = checkPath+ntcheck;
					}
					//System.out.println(" + " + ntcheck);
					doCheck(ntcheck);
				}
			} else {
				System.out.println("Warning: Type not defined: " + ntype);
			}
		}

	}

	private void doCheck(String check) {
		try {
			Process process = Runtime.getRuntime().exec(check);
			try {
				process.waitFor();
			} catch (InterruptedException e) {
				System.out.println(e);
			}
			InputStream stdin = process.getInputStream();
			String line;
			BufferedReader is = new BufferedReader(new InputStreamReader(stdin));
			while ((line = is.readLine ()) != null) {
				System.out.println(line);
			}
			System.out.println(process.exitValue());
			is.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//private List nodeCheckMap;
	private Map<String,String> argMap;
	private Map<String,List<String>> checkMap;
	private String checkPath;
	private ObjectFactory of;
	private String confFile;
	private String confPath;
}
