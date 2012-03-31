import java.io.*;
import java.util.*;
import java.math.BigInteger;
import javax.xml.bind.*;
import generated.*;
import java.lang.Thread;

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
		typeMap = new HashMap<String,Vector<String>>();
		typeDeps = new HashMap<String,List<String>>();
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
	 * This method parses an xml file and calls processType and processNode
	 * on each type and node as defined in the xml file
	 */
	private void readXml(String fileName) {
		try {
			JAXBContext jc = JAXBContext.newInstance(Site.class);
			Unmarshaller u = jc.createUnmarshaller();
			Site mySite = (Site)u.unmarshal( new FileInputStream(fileName));
			System.out.println("Site Name : " +mySite.getName());
			checkPath = new String(mySite.getCheckpath());
			if (!checkPath.substring(checkPath.length()-1,  checkPath.length()).equals("/")) {
				checkPath = checkPath + "/";
			}
			System.out.println("checks path : " + checkPath);

			threads = mySite.getThreads();
			System.out.println("threads : " + threads);
			
			//System.out.println("Processing Types and Checks definitions");
			List<Site.Type> typeList  = new ArrayList<Site.Type>();
			typeList = mySite.getType();
			for (Site.Type s: typeList ) {
				processType(s);
			}
			//System.out.println("Processing Types complete\n");
			

			//System.out.println("Processing Nodes");
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

	/*
	 * This method is called for each type defined in <Types>
	 * It will set up a hashmap with
	 * key = typename (i.e. cds) and
	 * data = list of check String (i.e. /home/system/check/check_mysql -h $h)
	 * The map is called checkMap and is privately accessible
	 *
	 * It will create a hashmap with 
	 * key = typename (i.e. cds) and
	 * data = list of dependancies (named of other types)
	 * the map is called typeMap and is privately accessible
	 */
	private void processType(Site.Type type) {

		String typeName = type.getName();
		System.out.println("\nType: " +typeName);

		/*
		 * This part deals with checks per typr
		 */
		List<String> typeChecks = new ArrayList<String>();
		typeChecks = type.getCheck();
		if (typeChecks.isEmpty()) {
			System.out.println("Warning: No Checks Defined for Type: " + type.getName());
		} else {
			checkMap.put(typeName, typeChecks);
		}
		/*
		 * This just prints stuff
		 */
		for ( String chk : typeChecks) {
			System.out.println(" + " + chk);
		}

		/*
		 * This part deals with dependancies
		 */
		List<String> typeDep = new ArrayList<String>();
		typeDep = type.getDependson();
		if (!typeDep.isEmpty()) {
			typeDeps.put(typeName, typeDep);
		}
		for (String dep : typeDep) {
			System.out.println(" +- Dependson: " + dep);
		}

	}


	/* 
	 * This method processes a given node.
	 *
	 * It creates a hashmap with any special check arguments.
	 * The check must be the same as the whole check name 
	 * (including potential paths and arguments defined generally)
	 * key = nodename_checkname (i.e. thor_check_cds -h $h)
	 * data = String from <checkargs>
	 * Map is called argMap and is privately accessible
	 *
	 * Next, it iterates through that nodes defined types
	 *
	 * It creates a hashmap with type to node assosiation
	 * key = type (i.e. cds)
	 * data = list of nodes (i.e. thor, septera)
	 * Map is called typeMap and is privately accessible
	 */
	private void processNode(Site.Nodes.Node node) {
		//List<String> nodeType = new ArrayList<String>();
		String nodeName = node.getName();
		List<String> nodeIPs = node.getIp();
		System.out.println("\nNode: " + nodeName);

		/*
		 * This part deals with the custom arguments per check per node
		 */
		for ( Site.Nodes.Node.Checkargs ca : node.getCheckargs() ) {
			argMap.put(nodeName + "_" + ca.getCheck(), ca.getArgs());
		}

		/*
		 * This part generates all checks
		 */
		for ( String nodeType : node.getType() ) {
			List<String> typeCheck = new ArrayList<String>();
			typeCheck = checkMap.get(nodeType);
			if ( typeCheck != null ) {
				for ( String nodeTypeCheck : typeCheck ) {
					/*
					 * Check for special arguments and apply them
					 */
					String specialCheckArgs = argMap.get(nodeName+"_" + nodeTypeCheck);
					if (specialCheckArgs != null) {
						nodeTypeCheck = nodeTypeCheck+" "+specialCheckArgs;
					}
					/*
					 * Add checkPath
					 */
					if (!nodeTypeCheck.substring(0,1).equals("/")) {
						nodeTypeCheck = checkPath+nodeTypeCheck;
					//	System.out.println(" + " + nodeTypeCheck);
					}
					/*
					 * Deal with special chars
					 * $h is for hostname (name in xml)
					 */
					String nodeTypeCheckModified;
					if (!nodeIPs.isEmpty()) {
						for ( String ip : nodeIPs ) {
							nodeTypeCheckModified=nodeTypeCheck.replaceAll("\\$h", ip);
							System.out.println(" + " + nodeTypeCheckModified);
						}
					} else {
						nodeTypeCheck=nodeTypeCheck.replaceAll("\\$h", nodeName);
						System.out.println(" + " + nodeTypeCheck);
					}
					/*
					try {
						nodeTypeCheck=nodeTypeCheck.replaceAll("\\$i", nodeIP);
					} catch (NullPointerException e) {
						System.out.println("Error, $i is used but no ip is defined for "+nodeName);
					}
					*/
				}
			} else {
				System.out.println("Warning: Type not defined: " + nodeType);
			}
			Vector<String> getTypeMap = new Vector<String>();
			getTypeMap = typeMap.get(nodeType);
			if (getTypeMap == null) {
				Vector<String> newTypeMap = new Vector<String>();
				newTypeMap.addElement(nodeName);
				typeMap.put(nodeType,newTypeMap);
				//System.out.println("Added to typeMap : " + nodeType + " +- " + nodeName);
			} else {
				getTypeMap.addElement(nodeName);
				typeMap.put(nodeType,getTypeMap);
				//System.out.print(nodeType);
				//for ( String s : getTypeMap) {
				//	System.out.print(" " + s);
				//}
				//System.out.println();
			}
		}
	}

	/*
	 * This method executes a check.
	 * It prints the check output (from stdout)
	 * It prints the return code (i.e. 0)
	 */
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
	
	private BigInteger threads;
	private Map<String,Vector<String>> typeMap;
	private Map<String,List<String>> typeDeps;
	private Map<String,String> argMap;
	private Map<String,List<String>> checkMap;
	private String checkPath;
	private ObjectFactory of;
	private String confFile;
	private String confPath;
}
