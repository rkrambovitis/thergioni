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
		longOutputTypes = new Vector<String>();
		sentFNotif = new HashMap<String,Integer>();
		sentWNotif = new HashMap<String,Integer>();
		//warnScripts = new Vector<String>();
		//errorScripts = new Vector<String>();
		warnMap = new Map<String,Vector<String>>();
		errorMap = new Map<String,Vector<String>>();
		typeThresholds = new HashMap<String,int[]>();
		lastNotif = new HashMap<String,Long>();
		breakerMap = new HashMap<String,List<String>>();
		defaultNotif = "";
		loopCount=0;
		threadCount=0;
	}

	private void printUsage() {
		System.err.println("Usage: java Cascade <path_to_config>");
	}
	
	/*
	 * This method does excactly what is says on the tin
	 */
	private void setupLogger(String fileName, String logLevel, String webFile) {
		try {
			// This one is for log messages
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
				logger.setLevel(Level.WARNING);
			}

			fh.setFormatter(new MyLogFormatter());
			logger.setUseParentHandlers(false);
			logger.addHandler(fh);

		} catch (IOException e) {
			System.err.println("Cannot create log file");
			System.exit(1);
		}
		try {
			// This one is for web output
			FileHandler fh2 = new FileHandler(webFile, 32768, 1, false);
			webLog = Logger.getLogger("WebOutput");
			webLog.setLevel(Level.ALL);
			fh2.setFormatter(new WebLogFormatter());
			webLog.setUseParentHandlers(false);
			webLog.addHandler(fh2);
		} catch (IOException e) {
			System.err.println("Cannot create web file");
			System.exit(1);
		}
	}

	/*
	 * This method parses an xml file and calls processType, processNode, processNotification
	 * It also sets up some global variables
	 */
	private void readXml(String fileName) {
		try {
			JAXBContext jc = JAXBContext.newInstance(Site.class);
			Unmarshaller u = jc.createUnmarshaller();
			Site mySite = (Site)u.unmarshal( new FileInputStream(fileName));

			if (mySite.getLogLevel() == null) {
				setupLogger(mySite.getLogFile(),"Info",mySite.getWebFile());
			} else {
				setupLogger(mySite.getLogFile(),mySite.getLogLevel(),mySite.getWebFile());
			}

			logger.config("Site Name : " +mySite.getName());
			checkPath = new String(mySite.getCheckpath());

			logger.config("threads : " + threads);
			threads = mySite.getParallelChecks().intValue();

			if (mySite.getTotalThreshWarn() == null) {
				logger.warning("total_thresh_warn not set, using default = 1");
				defTotalThreshWarn=1;
			} else {
				defTotalThreshWarn=mySite.getTotalThreshWarn().intValue();
			}
			logger.config("total threshold warn: " + defTotalThreshWarn);

			if (mySite.getTotalThreshError() == null) {
				logger.warning("total_thresh_error not set, using default = 2");
				defTotalThreshError=2;
			} else {
				defTotalThreshError=mySite.getTotalThreshError().intValue();
			}
			logger.config("total threshold error: " + defTotalThreshError);

			if (mySite.getCheckTimeout() == null) {
				logger.warning("check_timeout not set, using default = 5");
				timeOut=5L;
			} else {
				timeOut = mySite.getCheckTimeout().longValue();
			}
			logger.config("check timeout : " + timeOut);

			if (mySite.getNotifThresh() == null) {
				logger.warning("notif_thresh not set, using default = 2");
				defNotifThresh=2;
			} else {
				defNotifThresh = mySite.getNotifThresh().intValue();
			}
			logger.config("notification threshold : " + defNotifThresh);

			if (mySite.getNotifRepeat() == null) {
				logger.warning("notif_repeat not set, using default = 5");
				defNotifRepeat=5;
			} else {
				defNotifRepeat = mySite.getNotifRepeat().intValue();
			}
			logger.config("notification repeat : " + defNotifRepeat);

			if (mySite.getNotifRepeat() == null) {
				logger.warning("notif_flap_buffer not set, using default = 3600");
				defFlapBuffer=3600000L;
			} else {
				defFlapBuffer = (1000*(mySite.getNotifFlapBuffer().longValue()));
			}
			logger.config("notification flapping buffer : " + (defFlapBuffer/1000) + "(sec)");

			pause = mySite.getMainLoopPause();
			if (pause == null) {
				logger.warning("main_loop_pause not set, using default = 60");
				pause=new BigInteger("60");
			}
			logger.config("Main loop pause time : " + pause);

			pauseExtra = mySite.getMainLoopExtraRandomPause();
			if ((pauseExtra == null) || (pauseExtra.longValue() < 0l)) {
				logger.warning("main_loop_ExtraRandom not set, using default = 0");
				pauseExtra=new BigInteger("0");
			}
			logger.config("Main loop pause Extra Random max time : " + pauseExtra);

			logger.fine("Processing Types and Checks definitions");
			//List<Site.Type> typeList  = new ArrayList<Site.Type>();
			typeList  = new ArrayList<Site.Type>();
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
			List<Site.Notification> notificationList = new ArrayList<Site.Notification>();
			notificationList = mySite.getNotification();
			for ( Site.Notification not : notificationList) {
				processNotification(not);
			}

			logger.fine("Dumping config to web file");
			try {
				FileHandler fhc = new FileHandler(mySite.getWebConfig(), 32768, 1, false);
				webConf = Logger.getLogger("WebConfOutput");
				webConf.setLevel(Level.ALL);
				fhc.setFormatter(new WebConfFormatter());
				webConf.setUseParentHandlers(false);
				webConf.addHandler(fhc);
			} catch (IOException e) {
				System.err.println("Cannot create web file");
				System.exit(1);
			}

			logger.info("Initialization Complete");
			argMap.clear();
			checkMap.clear();
		} catch (Exception fnfe) {
			logger.severe(fnfe.getMessage());
		}
	}

	private void dumpType(String type) {
		Vector<String> checks = typeCheckMap.get(type);
		if (checks != null) {
			for ( String check : checks ) {
				webConf.config(check);
			}
		}
		List<String> deps = typeDeps.get(type);
		if (deps != null) {
			for (String dep : deps) {
				webConf.warning("Dep: "+dep);
				dumpType(dep);
			}
		}
	}

	private void dumpConf() {
		for (String type : topTypes) {
			webConf.severe("Top: "+type);
			dumpType(type);
		}
	}

	/*
	 * This method generates two vectors with all defined notification scripts.
	 */
	private void processNotification(Site.Notification notification) {
		logger.config(notification.getName());
		if (warnMap.containsKey(notification.getName()) {
			logger.severe("ERROR: Duplicate notification name: "+notification.getName());
			System.err.println("ERROR: Duplicate notification name: "+notification.getName());
			System.exit(1);
		}
		if (Site.getDefault()) {
			if (defaultNotif.equals("")) {
				defaultNotif = notification.getName();
			} else {
				logger.severe("ERROR: Only 1 default Notification block allowed: ("+defaultNotif+", "+notification.getName()+")");
				System.err.println("ERROR: Only 1 default Notification block allowed: ("+defaultNotif+", "notification.getName()+")");
				System.exit(1);
			}
		}
		warnScripts = new Vector<String>();
		errorScripts = new Vector<String>();
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
		warnMap.add(notification.getName(), warnScripts);
		errorMap.add(notification.getName(), errorScripts);
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
		 * This part deals with checks per type
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

		/*
		 * This part deals with breakers
		 */
		List<String> typeBreaker = new ArrayList<String>();
		typeBreaker = type.getBrokenBy();
		if (!typeBreaker.isEmpty()) {
			breakerMap.put(typeName, typeBreaker);
		}
		for (String brk : typeBreaker) {
			logger.config(" +- Broken by: " + brk);
		}

		/* 
		 * This part deals with "long_output" attribute
		 */
		if (type.isLongOutput()) {
			logger.config(" +- : type " + typeName + " is set to Long Output");
			longOutputTypes.addElement(typeName);
		}

		/* 
		 * This part deals with "top" attribute
		 */
		if (type.isTop()) {
			logger.config(" +++ : type " + typeName + " is top level");
			topTypes.addElement(typeName);
		}
		
		/*
		 * This part deals with threshold
		 * ttw - total threshold warning (how many failures make a warning)
		 * tte - total threshold error (how many failures make an error)
		 * nt - number of failures after which it should notify
		 * nr - number of failures after which is should notify again
		 */

		int wt, et, ttw, tte, nt, nr;
		int[] thresholds = new int[4];

		try {
			ttw = type.getTotalThreshWarn().intValue();
		} catch (NullPointerException npe) {
                        ttw=defTotalThreshWarn;
                }
		try {
			tte = type.getTotalThreshError().intValue();
		} catch (NullPointerException npe) {
                        tte=defTotalThreshError;
                }
		try {
			nt = type.getNotifThresh().intValue();
		} catch (NullPointerException npe) {
                        nt=defNotifThresh;
                }
		try {
			nr = type.getNotifRepeat().intValue();
		} catch (NullPointerException npe) {
                        nr=defNotifRepeat;
                }

		logger.config(" +- total threshold warning: " + ttw);
		thresholds[0]=ttw;
		logger.config(" +- total threshold error: " + tte);
		thresholds[1]=tte;
		logger.config(" +- notification threshold: " + nt);
		thresholds[2]=nt;
		logger.config(" +- notification repeat: " + nr);
		thresholds[3]=nr;
		typeThresholds.put(typeName,thresholds);

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
		int[] results = new int[3];
		results[0]=0;
		results[1]=0;
		results[2]=0;
		String message = new String();
		String longMessage = new String();
		String threadOutput = new String();
		logger.fine("Performing checks for " + type);
		Vector<String> checks = typeCheckMap.get(type);
		String failedOutput = new String("");
		if (checks == null) {
			logger.info("No "+type+" checks found!");
			return new String("No "+type+" checks found!");
		} else {
			int checksCount = checks.size();
			List<Future<String>> list = new ArrayList<Future<String>>();
			for ( String check : checks ) {
				Callable<String> worker = new Checker(check);
				logger.fine(check);
				Future<String> submit = executor.submit(worker);
				list.add(submit);
				//logger.finest("Threaded total: "+ ++threadCount);
			}
			for (Future<String> future : list) {
				try {
					threadOutput = future.get(timeOut, TimeUnit.SECONDS);
					future.cancel(true);
				} catch (Exception e) {
					logger.warning(e.toString());
					logger.warning("Check exceeded "+timeOut+" seconds");
					//logger.warning(future.toString());
					results[1]+=1;
					//webLog.severe("timeout");
					//webLog.severe(future.getCheck());
					failedOutput=failedOutput+"timeout, ";
					continue;
				}
				String fdgt=threadOutput.substring(0,1);
				if (fdgt.equals("0")) {
					results[0]+=1;
					logger.finest(threadOutput.substring(2));
				} else {
					results[1]+=1;
					logger.warning(threadOutput.substring(2));
					failedOutput=failedOutput+threadOutput.substring(2)+", ";
				}
			}

		}
		int ttw = typeThresholds.get(type)[0];
		int tte = typeThresholds.get(type)[1];
		message = type.toUpperCase() + "(";
		if (results[1] >= tte) message = message + "Failed: ";
		else if (results[1] >= ttw) message = message + "Warning: ";
		message = message + results[1]+"F/"+results[0]+"OK";
		if (results[1] > 0 ) { 
			failedOutput = failedOutput.substring(0,(failedOutput.length()-2));
			longMessage = message + "(" + failedOutput+")";
		}
		message = message +")";
		longMessage = longMessage + ")";
//			logger.warning(type+" w:"+results[1]+"/"+wt+", e:"+results[2]+"/"+et+", ttw="+ttw+", tte="+tte);

		if (topTypes.contains(type)) {
			message = message.substring(0,1).toUpperCase()+message.substring(1);
			longMessage = longMessage.substring(0,1).toUpperCase()+longMessage.substring(1);
		}

		if (results[1] >= 1) {
			List<String> breakers = breakerMap.get(type);
			if (breakers != null) {
				for (String b : breakers) {
					logger.info(" ++ Broken by : " + b);
					String breakerMessage = new String(check(b,executor));
					logger.warning(" ++ breaker result: " + breakerMessage);
					String mfc = breakerMessage.substring(b.length()+1,b.length()+2);
					//logger.info("MFC is: "+mfc);
					if (mfc.equals("F")) {
						message = type + " Broken by "+b;
						longMessage = type + " Broken by "+b;
					}
				}
			} 
			if (!message.contains("Broken by")) {
				List<String> deps = typeDeps.get(type);
				if (deps != null) {
					for (String d : deps) {
						logger.info(" ++ Depends on : " + d);
						message = message + ", " + check(d, executor);
						longMessage = longMessage + ", " + check(d, executor);
					}
				}
			}
		} else {
			message = type.toUpperCase() + "(OK:"+ results[0]+")";
			longMessage = type.toUpperCase() + "(OK:"+ results[0]+")";
		}

		
		if (longOutputTypes.contains(type)) {
			message = longMessage;
		}

		if (results[1] >= tte) {
		       	webLog.severe(longMessage);
			logger.warning(type +" "+ message);
		} else if (results[1] >= ttw) {
		       	webLog.warning(longMessage);
			logger.warning(type +" "+ message);
		} else if (results[1] >= 1 ) {
			webLog.info(longMessage);
			logger.info(type +" "+ message);
		}

		return message;
	}


	/*
	 * Nothing Special.
	 * Just loops, calls the checker, calls the notifier, sleeps
	 */
	private void enterMainLoop() {
		if (topTypes.size() == 0) {
			logger.severe("ERROR: You must declare at least 1 <top> type in the config");
			System.err.println("ERROR: You must declare at least 1 <top> type in the config");
			System.exit(1);
		}
		dumpConf();
		ExecutorService executor = Executors.newFixedThreadPool(threads, Executors.defaultThreadFactory());
		String message = new String();
		logger.info("Entering Main Loop\n");
		long sleeper = 0l;
		while (true) {
			for (String top : topTypes) {
				message=check(top, executor);
				dispatchNotification(top, message, executor);
			}
			try {
				sleeper = pause.longValue() + (long)(Math.random()*pauseExtra.longValue());
				logger.fine("top checks complete, initiating sleep for " + sleeper + " sec");
				//logger.finest("loop iteration: "+ ++loopCount);
				Thread.sleep(1000*sleeper);
			} catch (InterruptedException e) {
				logger.severe(e.getMessage());
				executor.shutdown();
				System.exit(1);
			}
		}
	}

	/*
	 * stores which notifications have been sent, and sends new ones
	 */
	private void dispatchNotification(String type, String message, Executor executor) {
		String mfc = message.substring(type.length()+1,type.length()+2);
//		System.err.println("mfc=" + mfc + "\nmessage=" + message);
		int notifThresh = typeThresholds.get(type)[2];
		int notifRepeat = typeThresholds.get(type)[3];
		String key = "F_"+type;
		String warnKey = "W_"+type;
		long lastMessage;
		long lastWarn;
		try {
			lastMessage = lastNotif.get(key);
		} catch (NullPointerException npe) {
			lastMessage = 0L;
		}
		try {
			lastWarn = lastNotif.get(warnKey);
		} catch (NullPointerException npe) {
			lastWarn = 0L;
		}
		long timeNow = System.currentTimeMillis();

		if (mfc.equals("F")) {
			long timeDiff = timeNow - lastMessage;
			Integer sn = sentFNotif.get(key);
			if (sn == null) {
				sn=new Integer(1);
				sentFNotif.put(key, sn);
			} else {
				sentFNotif.put(key, ++sn);
			}
			logger.info("Count: "+sn+" (threshold:"+notifThresh+" repeat:"+notifRepeat+")");
			//System.err.println(sn % notifRepeat);
//			webLog.severe(mfc + " " + message);
			if (sn % notifRepeat == notifThresh) {
				//message=message+" count:"+sn;
				message="\""+message+" count:"+sn+"\"";
				if (sn == notifThresh && timeDiff <= defFlapBuffer) {
					webLog.info("Flapping service: " + type + "(" + (timeDiff/1000) + " secs since last notification)");
					logger.warning("Flapping service: " + type + " (" + (timeDiff/1000) + " secs since last notification) - Skipped");
				} else {
					lastNotif.put(key,timeNow);
					//for (String s : errorScripts) {
					for (String s : errorMap.get(defaultNotif)) {
						Runnable r = new Notifier(s+" "+message);
						logger.info("Dispatching error notification " + s + " " + message);
						executor.execute(r);		
					}
				}
			}
		} else if (mfc.equals("W")) {
			long timeDiff = timeNow - lastWarn;
			Integer sn = sentWNotif.get(key);
			if (sn == null) {
				sn=new Integer(1);
				sentWNotif.put(key, sn);
			} else {
				sentWNotif.put(key, ++sn);
			}
			logger.info("Count: "+sn+" (threshold:"+notifThresh+" repeat:"+notifRepeat+")");
//			webLog.warning(mfc + " " + message);
			if (sn % notifRepeat == notifThresh) {
				if (sn == notifThresh && timeDiff <= defFlapBuffer) {
					webLog.info("Flapping service: " + type + "(" + (timeDiff/1000) + " secs since last notification)");
					logger.warning("Flapping service: " + type + " (" + (timeDiff/1000) + " secs since last notification) - Skipped");
				} else {
					lastNotif.put(warnKey, timeNow);
					//for (String s : warnScripts) {
					for (String s : warnMap.get(defaultNotif)) {
						Runnable r = new Notifier(s+" "+message);
						logger.info("Dispatching warning notification" + s + " " + message);
						executor.execute(r);		
					}
				}
			}
		} else if (mfc.equals("B")) {
			//webLog.info(message);
			sentWNotif.remove(key);
			sentFNotif.remove(key);
		} else {
//			webLog.info(mfc + " " + message);
			sentWNotif.remove(key);
			sentFNotif.remove(key);
		}
	}

	/*
	 * This class is used for threading.
	 * The check name is given in the constructor
	 * It returns null if the check returns OK
	 * Else it returns the 1st line of the output of the script
	 */
	private static class Checker implements Callable<String> {
		public Checker(String theCheck){
			check = new String(theCheck);
		}
/*
		public String getCheck() {
			return check;
		}
*/
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
				//System.err.println(line);
				//line = line + result + "\n(From check:" +check+")";


				return line;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		private String check;
	}

	/*
	 * Simple thread notifier
	 */
	private static class Notifier implements Runnable {
		public Notifier(String all){
			note = new String(all);
		}

		public void run() {
			try {
				Process process = Runtime.getRuntime().exec(note);
				try {
					System.err.println(note);
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
	private class MyLogFormatter extends java.util.logging.Formatter {
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

	private class WebLogFormatter extends java.util.logging.Formatter {
		public String format(LogRecord rec) {
			StringBuffer buf = new StringBuffer(1000);
			String time = calcDate(rec.getMillis());
			Level lev = rec.getLevel();
			if (lev.equals(Level.WARNING)) buf.append("<span style='color:orange'>"+time+"</span> "+formatMessage(rec)+"<hr>\n");
			else if (lev.equals(Level.SEVERE)) buf.append("<span style='color:red'>"+time+"</span>  "+formatMessage(rec)+"</span><hr>\n");
			else buf.append("<span style='color:green'>"+time+"</span> "+formatMessage(rec)+"<hr>\n");
			//else buf.append("<span style='color:green'>O</span>");
			//buf.append(formatMessage(rec));
			//buf.append('\n');
			return buf.toString();
		}

		private String calcDate(long millisecs) {
			SimpleDateFormat date_format = new SimpleDateFormat("MMM dd HH:mm:ss");
			Date resultdate = new Date(millisecs);
			return date_format.format(resultdate);
		}

		public String getHead(Handler h) {
			return "<html><body>\n";
		}
		public String getTail(Handler h) {
			return "</body></html>\n";
		}
	}

	private class WebConfFormatter extends java.util.logging.Formatter {
		public String format(LogRecord rec) {
			StringBuffer buf = new StringBuffer(1000);
			Level lev = rec.getLevel();
			if (lev.equals(Level.SEVERE)) buf.append(formatMessage(rec)+"\n");
			else if (lev.equals(Level.WARNING)) buf.append("\t+ "+formatMessage(rec)+"\n");
			else if (lev.equals(Level.CONFIG)) buf.append("\t\t+ "+formatMessage(rec)+"\n");
			return buf.toString();
		}

		public String getHead(Handler h) {
			return "<html><body><pre>\n";
		}
		public String getTail(Handler h) {
			return "</pre></body></html>\n";
		}
	}

	// Maps thresholds
	private Map<String,int[]> typeThresholds;
	// Maps F_type 1
	private Map<String,Integer> sentFNotif;
	// Maps W_type 1
	private Map<String,Integer> sentWNotif;
	// Maps type to all checks
	private Map<String,Vector<String>> typeCheckMap;
	// Maps node_type to full checks
	private Map<String,Vector<String>> nodeCheckMap;
	// Maps type to check breaker type
	private Map<String,List<String>> breakerMap;
	// Maps type to nodes (not actually used yet)
	private Map<String,Vector<String>> typeMap;
	// Maps type to deps
	private Map<String,List<String>> typeDeps;
	// Maps node_check to special args (used at initialization)
	private Map<String,String> argMap;
	// Maps type to default checks (used at initialization)
	private Map<String,List<String>> checkMap;
	// Stores last notification time per type
	private Map<String,Long> lastNotif;
	// list of types
	private List<Site.Type> typeList;
	private int defTotalThreshWarn;
	private int defTotalThreshError;
	private int defNotifThresh;
	private int defNotifRepeat;
	private Long defFlapBuffer;
	private int threads;
	private int loopCount;
	private int threadCount;
	private String checkPath;
	private Vector<String> warnScripts;
	private Vector<String> errorScripts;
	private Map<String,Vector<String>> warnMap;
	private Map<String,Vector<String>> errorMap;
	private Long timeOut;
	private BigInteger pause;
	private BigInteger pauseExtra;
	private ObjectFactory of;
	private String confFile;
	private String confPath;
	private Logger logger;
	private Logger webLog;
	private Logger webConf;
	private Vector<String> topTypes;
	private Vector<String> longOutputTypes;
	private String DefaultNotif;
}
