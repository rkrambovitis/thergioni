import java.io.*;
import java.util.*;
import java.math.BigInteger;
import javax.xml.bind.*;
import generated.*;
import java.lang.Thread;
import java.util.concurrent.*;
import java.util.logging.*;
import java.text.SimpleDateFormat;
import java.util.Date;

class Cascade {
	public static void main(String args[]) {
		Cascade myCascade = new Cascade();
		if (args.length == 0 ) {
			myCascade.printUsage();
			System.exit(0);
		}
		myCascade.readXml(args[0]);
		//myCascade.check("bestprice");
		//myCascade.check("cds");
		myCascade.enterMainLoop();
	}

	public Cascade() {
		of = new ObjectFactory();	
		checkMap = new HashMap<String,List<String>>();
		argMap = new HashMap<String,String>();
		typeMap = new HashMap<String,Vector<String>>();
		typeDeps = new HashMap<String,List<String>>();
		nodeCheckMap = new HashMap<String,Vector<String>>();
		typeCheckMap = new HashMap<String,Vector<String>>();
		topTypes = new Vector<String>();
		sentNotif = new HashMap<String,String>();
		warnScripts = new Vector<String>();
		errorScripts = new Vector<String>();
	}

	private void printUsage() {
		System.err.println("Usage: java Cascade <path_to_config>");
	}
	
	/*
	 * This method does excactly what is says on the tin
	 */
	private void setupLogger(String fileName, String logLevel) {
		try {
			FileHandler fh = new FileHandler(fileName, 52428800, 2, true);
			logger = Logger.getLogger("Cascade");
			String lcll = logLevel.toLowerCase();
			if (lcll.equals("severe")) {
				logger.setLevel(Level.SEVERE);
			} else if  (lcll.equals("warning")) {
				logger.setLevel(Level.WARNING);
			} else if  (lcll.equals("info")) {
				logger.setLevel(Level.INFO);
			} else if  (lcll.equals("config")) {
				logger.setLevel(Level.CONFIG);
			} else if  (lcll.equals("fine")) {
				logger.setLevel(Level.FINE);
			} else if  (lcll.equals("finer")) {
				logger.setLevel(Level.FINER);
			} else if  (lcll.equals("finest")) {
				logger.setLevel(Level.FINEST);
			} else if  (lcll.equals("all")) {
				logger.setLevel(Level.ALL);
			} else {
				logger.setLevel(Level.INFO);
			}

			fh.setFormatter(new myLogFormatter());
			logger.setUseParentHandlers(false);
			logger.addHandler(fh);
		} catch (IOException e) {
			System.err.println("Cannot create log file");
			System.exit(1);
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

			if (mySite.getLogLevel() == null) {
				setupLogger(mySite.getLogFile(),"Info");
			} else {
				setupLogger(mySite.getLogFile(),mySite.getLogLevel());
			}

			logger.config("Site Name : " +mySite.getName());
			checkPath = new String(mySite.getCheckpath());
			/*
			if (!checkPath.substring(checkPath.length()-1,  checkPath.length()).equals("/")) {
				checkPath = checkPath + "/";
			}
			logger.config("checks path : " + checkPath);
			*/

			threads = mySite.getParallelChecks().intValue();
			logger.config("threads : " + threads);


			if (mySite.getCheckTimeout() == null) {
				logger.warning("check_timeout not set, using default = 5");
				timeOut=5L;
			} else {
				timeOut = mySite.getCheckTimeout().longValue();
			}
			logger.config("check timeout : " + timeOut);

			pause = mySite.getMainLoopPause();
			if (pause == null) {
				logger.warning("main_loop_pause not set, using default = 60");
				pause=new BigInteger("60");
			}
			logger.config("Main loop pause time : " + pause);

			logger.fine("Processing Types and Checks definitions");
			List<Site.Type> typeList  = new ArrayList<Site.Type>();
			typeList = mySite.getType();
			for (Site.Type s: typeList ) {
				processType(s);
			}
			logger.fine("Processing Types complete\n");
			

			logger.fine("Processing Nodes");
			Site.Nodes nodeList = (Site.Nodes)mySite.getNodes();
			List<Site.Nodes.Node> nodes = new ArrayList<Site.Nodes.Node>();
			nodes = nodeList.getNode();
			for ( Site.Nodes.Node n : nodes ) {
				processNode(n);
			}

			logger.fine("Processing Notifications");
			List<Site.Notification> notificationList = new ArrayList<Site.Notification>();//mySite.getNotification();
			notificationList = mySite.getNotification();
			for ( Site.Notification not : notificationList) {
				processNotification(not);
			}

			logger.info("Initialization Complete\n");
			argMap.clear();
			checkMap.clear();
		} catch (Exception fnfe) {
			logger.severe(fnfe.getMessage());
		}
	}

	private void processNotification(Site.Notification notification) {
		logger.finer(notification.getName());
		List<String> wrscr = notification.getWarningScript();
		for (String w : wrscr) {
			logger.config("Warning Script: " + w);
			w=w.replaceAll("\\$cp", checkPath);
			warnScripts.addElement(w);
		}

		List<String> erscr = notification.getErrorScript();
		for (String e : erscr) {
			logger.config("Error Script: " + e);
			e=e.replaceAll("\\$cp", checkPath);
			errorScripts.addElement(e);
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
		logger.config("Type: " +typeName);

		/*
		 * This part deals with checks per typr
		 */
		List<String> typeChecks = new ArrayList<String>();
		typeChecks = type.getCheck();
		if (typeChecks.isEmpty()) {
			logger.warning("Warning: No Checks Defined for Type: " + type.getName());
		} else {
			checkMap.put(typeName, typeChecks);
		}
		/*
		 * This just prints stuff
		 */
		for ( String chk : typeChecks) {
			logger.config(" + " + chk);
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
			logger.config(" +- Dependson: " + dep);
		}

		if (type.isTop()) {
			logger.config(" +++ : type " + typeName + " is top level");
			topTypes.addElement(typeName);
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
		String nodeName = node.getName();
		List<String> nodeIPs = node.getIp();
		logger.config("Node: " + nodeName);

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
					/*
					if (!nodeTypeCheck.substring(0,1).equals("/")) {
						nodeTypeCheck = checkPath+nodeTypeCheck;
					}
					*/
					nodeTypeCheck=nodeTypeCheck.replaceAll("\\$cp", checkPath);
					/*
					 * Deal with special chars
					 * $h is for hostname (name in xml)
					 */
					String nodeTypeCheckModified = new String();
					if (!nodeIPs.isEmpty()) {
						for ( String ip : nodeIPs ) {
							nodeTypeCheckModified=nodeTypeCheck.replaceAll("\\$h", ip);
							logger.config(" + " + nodeTypeCheckModified);
							mapCheck(nodeName, nodeType, nodeTypeCheckModified);
						}
					} else {
						nodeTypeCheck=nodeTypeCheck.replaceAll("\\$h", nodeName);
						logger.config(" + " + nodeTypeCheck);
						mapCheck(nodeName, nodeType, nodeTypeCheck);
					}
				}
			} else {
				logger.warning("Warning: Type not defined: " + nodeType);
			}
			Vector<String> getTypeMap = new Vector<String>();
			getTypeMap = typeMap.get(nodeType);
			if (getTypeMap == null) {
				Vector<String> newTypeMap = new Vector<String>();
				newTypeMap.addElement(nodeName);
				typeMap.put(nodeType,newTypeMap);
			} else {
				getTypeMap.addElement(nodeName);
				typeMap.put(nodeType,getTypeMap);
			}
		}
	}
	/*
	 * This method adds items to a map that contains all checks.
	 * The form of data in the map is like this:
	 * key = nodename_type i.e. thor_cds
	 * data = vector with checks i.e. check_cds -h thor
	 * The Map is called nodeCheckMap
	 *
	 * Second map added:
	 * key - type i.e. cds
	 * data = vector with checks
	 * The map is called typeCheckMap
	 */
	private void mapCheck(String node, String type, String check) {
		/*
		 * first part, maps to nodeCheckMap
		 */
		String nodePlusCheck = (node+"_"+type);
		Vector<String> getNodeCheckMap = new Vector<String>();
		getNodeCheckMap = nodeCheckMap.get(nodePlusCheck);
		if (getNodeCheckMap == null) {
			Vector<String> newNodeCheckMap = new Vector<String>();
			newNodeCheckMap.addElement(check);
			nodeCheckMap.put(nodePlusCheck,newNodeCheckMap);
		} else {
			getNodeCheckMap.addElement(check);
			nodeCheckMap.put(nodePlusCheck, getNodeCheckMap);
		}

		/*
		 * Second part, maps to typeCheckMap
		 */
		Vector<String> getTypeCheckMap = new Vector<String>();
		getTypeCheckMap = typeCheckMap.get(type);
		if (getTypeCheckMap == null) {
			Vector<String> newTypeCheckMap = new Vector<String>();
			newTypeCheckMap.addElement(check);
			typeCheckMap.put(type,newTypeCheckMap);
		} else {
			getTypeCheckMap.addElement(check);
			typeCheckMap.put(type, getTypeCheckMap);
		}
	}

	/*
	 * This method deals with checks and threading.
	 * Really needs commenting as it's messy
	 * Mainly for testing.
	 */
	private String check(String type, ExecutorService executor) {
		boolean somethingFailed=false;
		int[] results = new int[3];
		results[0]=0;
		results[1]=0;
		results[2]=0;
		String message = new String();
		logger.info("Performing checks for " + type);
		Vector<String> checks = typeCheckMap.get(type);
		if (checks == null) {
			logger.info("No "+type+" checks found!");
		} else {
			int checksCount = checks.size();
			List<Future<String>> list = new ArrayList<Future<String>>();
			for ( String check : checks ) {
				Callable<String> worker = new Checker(check);
				logger.fine(check);
				Future<String> submit = executor.submit(worker);
				list.add(submit);
			}
			for (Future<String> future : list) {
				try {
					String threadOutput = future.get(timeOut, TimeUnit.SECONDS);
					future.cancel(true);
					String fdgt=threadOutput.substring(0,1);
					if (fdgt.equals("0")) {
						results[0]+=1;
					} else if (fdgt.equals("1")) {
						results[1]+=1;
					} else {
						results[2]+=1;
					}

					/*
					if (!threadOutput.substring(0,1).equals("0")) {
						if (!somethingFailed) {
							somethingFailed=true;
							logger.warning("NOT OK: dependancy check triggered");
							logger.warning(threadOutput);
						} else {
							logger.info(threadOutput);
						}
						if (message.equals("")) {
							message = "type:"+type;
						}
						message = message + "\n" + threadOutput;
					} else {
						logger.info(threadOutput);
					}
					*/
				} catch (Exception e) {
					logger.warning("Check Timeout ");
					//somethingFailed=true;
					results[2]+=1;
				}
			}
			if (results[2] > 0) message = "Fail: "+results[2]+" ";
			if (results[1] > 0) message = message + "Warn: "+results[1]+" ";
			if (results[0] > 0) message = message + "OK: " + results[0];
		}
		if ((results[1] > 0) || (results[2] > 0)) {
			List<String> deps = typeDeps.get(type);
			if (deps != null) {
				for (String d : deps) {
					logger.info(" ++ Depends on : " + d);
					message = message + "\n" + check(d, executor);
				}
			}
		}
		return message;
	}


	private void enterMainLoop() {
		if (topTypes.size() == 0) {
			logger.severe("ERROR: You must declare at least 1 <top> type in the config");
			System.err.println("ERROR: You must declare at least 1 <top> type in the config");
			System.exit(1);
		}
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		String message = new String();
		while (true) {
			for (String top : topTypes) {
				message=check(top, executor);
				dispatchNotification(top, message, executor);
			}
			try {
				logger.fine("top checks complete, initiating sleep for " + pause + " sec");
				Thread.sleep(1000*pause.longValue());
			} catch (InterruptedException e) {
				logger.severe(e.getMessage());
				executor.shutdown();
			}
		}
	}

	private void dispatchNotification(String type, String message, Executor executor) {
		String mfc = message.substring(0,1);
		String key = "F_"+type;
		if (mfc.equals("F")) {
			if (sentNotif.get(key) == null) {
				sentNotif.put(key, message);
				for (String s : errorScripts) {
					Runnable r = new Notifier(s+" "+message);
					executor.execute(r);		
				}
			}
		} else if (mfc.equals("O")) {
			if (sentNotif.get(key) != null) {
				sentNotif.remove(key);
			}
		} else {
			for (String s : warnScripts) {
				Runnable r = new Notifier(s+" "+message);
				executor.execute(r);
			}
		}
	}

	/*
	 * This class is used for threading.
	 * The check name is given in the constructor
	 * It returns OK if the check returns OK
	 * Else it returns the 1st line of the output of the script
	 */
	private static class Checker implements Callable<String> {
		public Checker(String theCheck){
			check = new String(theCheck);
		}

		public String call() {
			try {
				Process process = Runtime.getRuntime().exec(check);
				String line = new String();
				try {
					process.waitFor();
				} catch (InterruptedException e) {
					System.err.println(e.getMessage());
				}
				line = process.exitValue() + " ";
				InputStream stdin = process.getInputStream();
				BufferedReader is = new BufferedReader(new InputStreamReader(stdin));
				String result = is.readLine();
				is.close();
				line = line + result;
				return line;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		private String check;
	}

	private static class Notifier implements Runnable {
		public Notifier(String all){
			note = new String(all);
		}

		public void run() {
			try {
				Process process = Runtime.getRuntime().exec(note);
				try {
					process.waitFor();
				} catch (InterruptedException e) {
					System.err.println(e.getMessage());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		private String note;
	}

	/*
	 * Simple Log Formatter
	 * Keeps logs nice and compact
	 */
	private class myLogFormatter extends java.util.logging.Formatter {
		public String format(LogRecord rec) {
			StringBuffer buf = new StringBuffer(1000);
			buf.append(calcDate(rec.getMillis()));
			buf.append(" ");
			buf.append(rec.getLevel());
			buf.append(" : "); 
			buf.append(formatMessage(rec));
			buf.append('\n');
			return buf.toString();
		}

		private String calcDate(long millisecs) {
			SimpleDateFormat date_format = new SimpleDateFormat("MMM dd HH:mm");
			Date resultdate = new Date(millisecs);
			return date_format.format(resultdate);
		}

		public String getHead(Handler h) {
			return "Cascade Logger Initiated : " + (new Date()) + "\n";
		}
		public String getTail(Handler h) {
			return "Cascade Logger Exiting : " + (new Date()) + "\n";
		}
	}

	// Maps F_type 1
	private Map<String,String> sentNotif;
	// Maps type to all checks
	private Map<String,Vector<String>> typeCheckMap;
	// Maps node_type to full checks
	private Map<String,Vector<String>> nodeCheckMap;
	// Maps type to nodes (not actually used yet)
	private Map<String,Vector<String>> typeMap;
	// Maps type to deps
	private Map<String,List<String>> typeDeps;
	// Maps node_check to special args (used at initialization)
	private Map<String,String> argMap;
	// Maps type to default checks (used at initialization)
	private Map<String,List<String>> checkMap;
	private int threads;
	private String checkPath;
	private Vector<String> warnScripts;
	private Vector<String> errorScripts;
	private Long timeOut;
	private BigInteger pause;
	private ObjectFactory of;
	private String confFile;
	private String confPath;
	private Logger logger;
	private Vector<String> topTypes;
}
