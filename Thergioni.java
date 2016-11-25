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
import java.time.LocalDateTime;

class Thergioni {
	public static void main(String args[]) {
		Thergioni myThergioni = new Thergioni();
		if (args.length == 0 ) {
			myThergioni.printUsage();
			System.exit(0);
		}
		myThergioni.readXml(args[0]);
		myThergioni.createRotationNotifier();
		myThergioni.enterMainLoop();
	}

	public Thergioni() {
		of = new ObjectFactory();	
		checkMap = new HashMap<String,List<String>>();
		argMap = new HashMap<String,List<String>>();
		typeMap = new HashMap<String,Vector<String>>();
		typeDeps = new HashMap<String,List<String>>();
		nodeCheckMap = new HashMap<String,Vector<String>>();
		typeCheckMap = new HashMap<String,Vector<String>>();
		snoozeMap = new HashMap<String, Snooze>();
		topTypes = new Vector<String>();
		longOutputTypes = new Vector<String>();
		sentNotif = new HashMap<String,Integer>();
		//sentWNotif = new HashMap<String,Integer>();
		//sentUNotif = new HashMap<String,Integer>();
		//warnScripts = new Vector<String>();
		//errorScripts = new Vector<String>();
		warnMap = new HashMap<String,Vector<String>>();
		errorMap = new HashMap<String,Vector<String>>();
		spamMap = new HashMap<String,Vector<String>>();
		reactionMap = new HashMap<String,Vector<String>>();
		notifyMap = new HashMap<String,List<String>>();
		typeThresholds = new HashMap<String,int[]>();
		accumMap = new HashMap<String, TypeAccum>();
		lastNotif = new HashMap<String,Long>();
		breakerMap = new HashMap<String,List<String>>();
		stateMap = new HashMap<String, Short>();
		defaultNotif = new ArrayList<String>();
		rotMap = new HashMap<String,Rotater>();
		failedColors = new String[5];
		failedColors[0] = new String("greenyellow");
		failedColors[1] = new String("antiquewhite");
		failedColors[2] = new String("bisque");
		failedColors[3] = new String("chocolate");
		failedColors[4] = new String("crimson");
		failedFavicons = new String[5];
		loopCount=0;
		threadCount=0;
	}

	private void printUsage() {
		System.err.println("Usage: java Thergioni <path_to_config>");
	}
	
	/*
	 * This method does excactly what is says on the tin
	 */
	private void setupLogger(String fileName, String logLevel, String webFile) {
		try {
			// This one is for log messages
			FileHandler fh = new FileHandler(fileName, 52428800, 2, true);
			logger = Logger.getLogger("Thergioni");
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
			System.err.println("Cannot create log file: "+fileName);
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
			System.err.println("Cannot create web file: "+webFile);
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

                        String webPath = new String("./");
			if (mySite.getWebPath() != null ) {
                                webPath = mySite.getWebPath();
				webPath = webPath + "/";
                        }

			if (mySite.getLogLevel() == null) {
				setupLogger(mySite.getLogFile(),"Info",webPath+mySite.getWebFile());
			} else {
				setupLogger(mySite.getLogFile(),mySite.getLogLevel(),webPath+mySite.getWebFile());
			}

			logger.config("Site Name : " +mySite.getName());
			checkPath = new String(mySite.getCheckpath());

			logger.config("web_path: " + webPath);

			logger.fine("Creating web config file");
			try {
				String configFile = webPath;
				if (mySite.getWebConfig() == null)
					configFile = configFile + "thergioni_config.html";
				else
					configFile = configFile + mySite.getWebConfig();
				logger.config("web_config: " + configFile);

				FileHandler fhc = new FileHandler(configFile, 512000, 1, false);
				webConf = Logger.getLogger("WebConfOutput");
				webConf.setLevel(Level.ALL);
				fhc.setFormatter(new WebConfFormatter());
				webConf.setUseParentHandlers(false);
				webConf.addHandler(fhc);
			} catch (IOException e) {
				System.err.println("Cannot create web conf file");
				System.exit(1);
			}

			logger.fine("Setting web status file path");
			if (mySite.getWebStatus() == null) {
				logger.warning("web_status not set, using default = thergioni_web_status.html");
				statusFilePath = new String(webPath + "thergioni_web_status.html");
			} else {
				statusFilePath = webPath + mySite.getWebStatus();
			}
			logger.config("web_status path: " + statusFilePath);

//			logger.config("threads : " + threads);
//			threads = mySite.getParallelChecks().intValue();

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

			if (mySite.getUrgentThresh() == null) {
				logger.warning("urgent_thresh not set, using default = 0");
				defUrgentThresh=0;
			} else {
				defUrgentThresh = mySite.getUrgentThresh().intValue();
			}
			logger.config("urgent threshold : " + defUrgentThresh);

			if (mySite.getAccumThreshWarn() == null) {
				logger.warning("accum_thresh_warn not set, using default = 0 (disabled)");
				defAccumThreshWarn=0;
			} else {
				defAccumThreshWarn = mySite.getAccumThreshWarn().intValue();
				if (defAccumThreshWarn <= defNotifThresh) {
					logger.warning("accum_thresh_warn must be greater than notif_thresh. using default = 0 (disabled)");
					defAccumThreshWarn=0;
				}	
			}
			logger.config("accumulative theshold warning : " + defAccumThreshWarn);

			if (mySite.getAccumThreshError() == null) {
				logger.warning("accum_thresh_error not set, using default = 0 (disabled)");
				defAccumThreshError=0;
			} else {
				defAccumThreshError = mySite.getAccumThreshError().intValue();
				if (defAccumThreshError <= defNotifThresh) {
					logger.warning("accum_thresh_error must be greater than notif_thresh. using default = 0 (disabled)");
					defAccumThreshError=0;
				}
			}
			logger.config("accumulative theshold error : " + defAccumThreshError);

			if (mySite.getAccumTimeWarn() == null) {
				logger.warning("accum_time_error not set, using default = 60 (mins)");
				defAccumTimeWarn=60;
			} else {
				defAccumTimeWarn = mySite.getAccumTimeWarn().intValue();
			}
			logger.config("accumulative time error (mins) : " + defAccumTimeWarn);

			if (mySite.getAccumTimeError() == null) {
				logger.warning("accum_time_error not set, using default = 60 (mins)");
				defAccumTimeError=60;
			} else {
				defAccumTimeError = mySite.getAccumTimeError().intValue();
			}
			logger.config("accumulative time error (mins) : " + defAccumTimeError);

			if (mySite.getNotifFlapBuffer() == null) {
				logger.warning("notif_flap_buffer not set, using default = 10 (mins)");
				defFlapBuffer=1200000L;
			} else {
				defFlapBuffer = (60000*(mySite.getNotifFlapBuffer().longValue()));
			}
			logger.config("notification flapping buffer : " + (defFlapBuffer/60000) + "(mins)");

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
			if (defaultNotif.size() == 0 ) {
				logger.severe("No default Notification set !");
			}

			if (mySite.getAccumTimeError() == null) {
                                logger.warning("accum_time_error not set, using default = 60 (mins)");
                                defAccumTimeError=60;
                        } else {
                                defAccumTimeError = mySite.getAccumTimeError().intValue();
                        }
                        logger.config("accumulative time error (mins) : " + defAccumTimeError);

			logger.fine("Setting status script");
			if (mySite.getStatusScript() == null) {
				logger.warning("status_script not set, using default = echo");
				statusScript = new String("echo ");
			} else {
				statusScript = mySite.getStatusScript().replaceAll("\\$cp", checkPath);
			}
			logger.config("status_script: " + statusScript);

			logger.fine("Setting web title and favicons");
			if (mySite.getWebTitle() == null) {
				logger.warning("web_title not set, using default = Thergioni");
				webTitle = new String("Thergioni");
			} else {
				webTitle = mySite.getWebTitle();
			}
			logger.config("web_title: " + webTitle);

			logger.fine("Setting web title and favicons");
			if (mySite.getFaviconOk() != null) 
				failedFavicons[0] = mySite.getFaviconOk();
			else
				failedFavicons[0] = new String("favicon.png");

			if (mySite.getFaviconNotice() != null) 
				failedFavicons[1] = mySite.getFaviconNotice();
			else
				failedFavicons[1] = new String("notice.png");

			if (mySite.getFaviconWarning() != null) 
				failedFavicons[2] = mySite.getFaviconWarning();
			else
				failedFavicons[2] = new String("warning.png");

			if (mySite.getFaviconError() != null) 
				failedFavicons[3] = mySite.getFaviconError();
			else
				failedFavicons[3] = new String("error.png");

			if (mySite.getFaviconUrgent() != null) 
				failedFavicons[4] = mySite.getFaviconUrgent();
			else
				failedFavicons[4] = new String("urgent.png");
			
			logger.info("Initialization Complete");
			argMap.clear();
			checkMap.clear();
		} catch (Exception fnfe) {
			fnfe.printStackTrace();
			//System.err.println(fnfe);
			System.exit(1);
			//logger.severe(fnfe.toString());
		}
	}

	private void createRotationNotifier() {
		RotationNotifier rn = new RotationNotifier();
		new Thread(rn).start();
	}

	private void dumpType(String type) {
		Vector<String> checks = typeCheckMap.get(type);
		if (checks != null) {
			webConf.warning("Checks: ");
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

	private void dumpNotif(String type) {
		if (notifyMap.containsKey(type)) {
			for (String s : notifyMap.get(type)) {
				webConf.warning("Notify: "+s);
			}
		} else {
			webConf.warning("Notify: default");
		}
	}

	private void dumpConf() {
		for (String type : topTypes) {
			webConf.severe("Top: "+type);
			dumpNotif(type);
			dumpType(type);
		}
	}

	private void dumpStatus(Executor executor) {
		try {
		Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(statusFilePath), "utf-8"));
		writer.write("<html>\n\t<head>");
		writer.write("\n\t\t<title>Status</title>");
		//writer.write("\n\t\t<meta http-equiv=\"refresh\" content="+sleeper+">");
		writer.write("\n\t\t<style>");
		writer.write("\n\t\t\tdiv {");
		writer.write("\n\t\t\t\ttext-align:center;");
		writer.write("\n\t\t\t\tmargin:2 auto;");
		writer.write("\n\t\t\t\twidth:99%;");
		writer.write("\n\t\t\t}");
		writer.write("\n\t\t</style>");
		writer.write("\n\t</head>");
		writer.write("\n\t<body>\n\n");

		boolean somethingFailed=false;
		int failedLevel = 0;
		String failedType = new String();
		for (String type : topTypes) {
			if (getState(type) == STATE_URGENT) {
				failedLevel = 4;
				failedType += "[U]"+type+" ";
			} else if (getState(type) == STATE_ERROR) {
				if (failedLevel < 3)
					failedLevel = 3;
				failedType += "[E]"+type+" ";
			} else if (getState(type) == STATE_WARN) {
				if (failedLevel < 2)
					failedLevel = 2;
				failedType += "[W]"+type+" ";
			} else if (getState(type) == STATE_NOTICE) {
				if (failedLevel == 0)
					failedLevel = 1;
				failedType += "[N]"+type+" ";
			}
		}
		String toScript = statusScript;
		if (failedLevel == 0)
			failedType = "Super Green";

		if (failedLevel == 4)
			toScript += " URGENT ";
		else if (failedLevel == 3)
			toScript += " ERROR ";
		else if (failedLevel == 2)
			toScript += " WARNING ";
		else if (failedLevel == 1)
			toScript += " NOTICE ";
		else
			toScript += " OK ";

		toScript += failedType;

		writer.write("<div style=\"background-color:"+failedColors[failedLevel]+";\">"+failedType+"</div>\n");
		writer.write("\n\t\t<script>parent.document.title=\"" + failedType + " - " + webTitle + "\"</script>");
		writer.write("\n\t\t<script>parent.document.querySelector('#favicon').href = '"+failedFavicons[failedLevel]+"'</script>");
		Runner r = new Runner(toScript);
		executor.execute(r);
		writer.write("\n\t</body>\n</html>");
		writer.close();
		} catch (IOException e) {
			logger.severe("Failed to write status page\n"+e);
		}
	}

	/*
	 * This method generates notifications
	 */
	private void processNotification(Site.Notification notification) {
		logger.config("Notification group: "+notification.getName());
		if (warnMap.containsKey(notification.getName())) {
			logger.severe("ERROR: Duplicate notification name: "+notification.getName());
			System.err.println("ERROR: Duplicate notification name: "+notification.getName());
			System.exit(1);
		}
		if (notification.isDefault()) {
			defaultNotif.add(notification.getName());
		}
		Vector<String> warnScripts = new Vector<String>();
		Vector<String> errorScripts = new Vector<String>();
		Vector<String> spamScripts = new Vector<String>();

		List<String> wrscr = notification.getWarningScript();
		for (String w : wrscr) {
			logger.config("+ Warning Script: " + w);
			w=w.replaceAll("\\$cp", checkPath);
			warnScripts.addElement(w);
		}

		List<String> erscr = notification.getErrorScript();
		for (String e : erscr) {
			logger.config("+ Error Script: " + e);
			e=e.replaceAll("\\$cp", checkPath);
			errorScripts.addElement(e);
		}

		List<String> spmscr = notification.getSpamScript();
		for (String e : spmscr) {
			logger.config("+ Spam Script: " + e);
			e=e.replaceAll("\\$cp", checkPath);
			spamScripts.addElement(e);
		}

		warnMap.put(notification.getName(), warnScripts);
		errorMap.put(notification.getName(), errorScripts);
		spamMap.put(notification.getName(), spamScripts);
		
		// NEW -> the rotation crap
		Site.Notification.Rotation rot = notification.getRotation();
		if (rot == null) {
			logger.config("+ No rotation defined");
		} else {
			Rotater r = new Rotater(notification.getName(), logger);
			r.setTime(rot.getTime());
			r.setDay(rot.getDay());
			r.setRemind(rot.getRemind());
			r.setElevate(rot.isElevate());
			r.setWarn(rot.getWarningScript());
			r.setError(rot.getErrorScript());
			List<Site.Notification.Rotation.OnCall> onCall = rot.getOnCall();
			for (Site.Notification.Rotation.OnCall onc : onCall) {
				r.setOnCall(new OnCall(onc.getName(), onc.getEmail(), onc.getNumber(), onc.getXmpp(), onc.isElevateOnly()));
			}
			rotMap.put(r.getName(), r);
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
		 * This part deals with reaction script
		 */
                List<String> reactionList = new ArrayList<String>();
		Vector<String> reactions = new Vector<String>();
                reactionList = type.getReaction();
                if (!reactionList.isEmpty()) {
                        for (String react : reactionList) {
				react=react.replaceAll("\\$cp", checkPath);
                                logger.config(" +++ : reaction -> "+react);
				reactions.addElement(react);
                        }
                        reactionMap.put(typeName, reactions);
                } else {
                        logger.config(" +++ : reaction -> none");
                }

		/* 
		 * This part deals with "notify" attribute.
		 * It creates a list of "who to notify" if said type goes wrong.
		 */
		List<String> notifyList = new ArrayList<String>();
		notifyList = type.getNotify();
		if (!notifyList.isEmpty()) {
			for (String tonot : notifyList) {
				logger.config(" +++ : notify -> "+tonot);
			}
			notifyMap.put(typeName, notifyList);
		} else {
			logger.config(" +++ : notify -> default");
		}


		/*
		 * This part deals with "snooze" attribute
		 */
		int snc = 0;
		try {
			snc = type.getSnooze().intValue();
			snoozeMap.put(typeName, new Snooze(snc));
			logger.config(" +++ : Snooze -> "+snc);
		} catch (NullPointerException npe) {
			logger.config(" +++ : Snooze -> OFF");
                }
		
		/*
		 * This part deals with threshold
		 * ttw - total threshold warning (how many failures make a warning)
		 * tte - total threshold error (how many failures make an error)
		 * nt - number of failures after which it should notify
		 * nr - number of failures after which is should notify again
		 */

		int ttw, tte, nt, nr, ut, atw, ate, atmw, atme, rt;
		int[] thresholds = new int[6];

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
			rt = type.getReactThresh().intValue();
		} catch (NullPointerException npe) {
                        rt=nt;
                }
		try {
			nr = type.getNotifRepeat().intValue();
		} catch (NullPointerException npe) {
                        nr=defNotifRepeat;
                }
		try {
			ut = type.getUrgentThresh().intValue();
		} catch (NullPointerException npe) {
                        ut=defUrgentThresh;
                }
		try {
			atw = type.getAccumThreshWarn().intValue();
		} catch (NullPointerException npe) {
                        atw=defAccumThreshWarn;
                }
		try {
			ate = type.getAccumThreshError().intValue();
		} catch (NullPointerException npe) {
                        ate=defAccumThreshError;
                }
		try {
			atmw = type.getAccumTimeWarn().intValue();
		} catch (NullPointerException npe) {
                        atmw=defAccumTimeWarn;
                }
		try {
			atme = type.getAccumTimeError().intValue();
		} catch (NullPointerException npe) {
                        atme=defAccumTimeError;
                }

		logger.config(" +- total threshold warning: " + ttw);
		thresholds[0]=ttw;
		logger.config(" +- total threshold error: " + tte);
		thresholds[1]=tte;
		logger.config(" +- notification threshold: " + nt);
		thresholds[2]=nt;
		logger.config(" +- notification repeat: " + nr);
		thresholds[3]=nr;
		logger.config(" +- urgent threshold: " + ut);
		thresholds[4]=ut;
		logger.config(" +- react threshold: " + rt);
		thresholds[5]=rt;
		logger.config(" +- accumulative threshold warning: " + atw);
		logger.config(" +- accumulative threshold error: " + ate);
		logger.config(" +- accumulative time warning: " + atmw + " (mins)");
		logger.config(" +- accumulative time error: " + atme + " (mins)");
		typeThresholds.put(typeName,thresholds);
		accumMap.put(typeName, new TypeAccum(atw, ate, atmw, atme));

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
					 * Check for special arguments
					 */
					List<String> specialCheckArgs = new ArrayList<String>();
					if (argMap.containsKey(nodeName + "_" + nodeTypeCheck))
						specialCheckArgs = argMap.get(nodeName+"_" + nodeTypeCheck);
					else
						specialCheckArgs.add(new String(""));

					/*
					 * Add checkPath
					 */
					nodeTypeCheck=nodeTypeCheck.replaceAll("\\$cp", checkPath);
					/*
					 * Deal with special chars
					 * $h is for hostname (name in xml)
					 * $p is for port
					 * $v is for var
					 */
					if (node.getVar() != null) {
						nodeTypeCheck=nodeTypeCheck.replaceAll("\\$v", node.getVar());
					} else {
						nodeTypeCheck=nodeTypeCheck.replaceAll("\\$v", "");
					}
					if (node.getPort() != null) {
						nodeTypeCheck=nodeTypeCheck.replaceAll("\\$p", node.getPort());
					} else {
						nodeTypeCheck=nodeTypeCheck.replaceAll("\\$p", "");
					}

					for (String args : specialCheckArgs) {
						String nodeTypeCheckArgs = nodeTypeCheck+" "+args;
						String nodeTypeCheckModified = new String();
						if (!nodeIPs.isEmpty()) {
							for ( String ip : nodeIPs ) {
								nodeTypeCheckModified=nodeTypeCheckArgs.replaceAll("\\$h", ip);
								logger.config(" + " + nodeTypeCheckModified);
								mapCheck(nodeName, nodeType, nodeTypeCheckModified);
							}
						} else {
							nodeTypeCheckModified=nodeTypeCheckArgs.replaceAll("\\$h", nodeName);
							logger.config(" + " + nodeTypeCheckModified);
							mapCheck(nodeName, nodeType, nodeTypeCheckModified);
						}
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
	private String check(String type, ExecutorService executor, boolean extraText) {
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
			List<Checker> list = new ArrayList<Checker>();
			for ( String check : checks ) {
				Checker worker = new Checker(check);
				logger.fine(check);
				list.add(worker);
			}
			try {
				List<Future<String>> output = executor.invokeAll(list, timeOut, TimeUnit.SECONDS);
				for (Future<String> future : output) {
					int chkpos=output.indexOf(future);
					if (future.isCancelled()) {
						//	System.err.println("Timeout: " + checks.get(chkpos));
						String shortCheck=checks.get(chkpos);
						if (shortCheck.contains(" "))
							shortCheck = shortCheck.substring(shortCheck.indexOf(" "));
						else
							shortCheck = shortCheck.replaceAll(checkPath,"");

						logger.warning(shortCheck + " exceeded "+timeOut+" seconds");
						results[1]+=1;
						failedOutput=failedOutput+shortCheck.replaceAll("\"","\'")+":timeout, ";
					} else {
						threadOutput = future.get();
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
			} catch (CancellationException|InterruptedException|ExecutionException e) {
				System.err.println("Exception during check: " + e);
				logger.severe("Exception during check: " +e);
				results[1]+=1;
				failedOutput=(""+e.getCause());
			}
		}
		if (results[1] == 0) {
			//all OK, just return.
			//System.err.println("returning null for "+type);
			return null;
		}

		int ttw = typeThresholds.get(type)[0];
		int tte = typeThresholds.get(type)[1];
		int ut = typeThresholds.get(type)[4];

		message = type.toUpperCase() + "(";
		if (extraText) {
			if ((ut > 0) && (results[1] >= ut)) {
				message = message + "Urgent: ";
			} else if (results[1] >= tte) {
				message = message + "Failed: ";
			} else if (results[1] >= ttw) {
				message = message + "Warning: ";
			} else if (results[1] >= 1) {
				message = message + "Notice: ";
			}
		}

		message = message + results[1]+"f/"+results[0]+"ok";
		failedOutput = failedOutput.substring(0,(failedOutput.length()-2));
		longMessage = message + "(" + failedOutput+")";
		message = message +")";
		longMessage = longMessage + ")";

		if (topTypes.contains(type)) {
			message = message.substring(0,1).toUpperCase()+message.substring(1);
			longMessage = longMessage.substring(0,1).toUpperCase()+longMessage.substring(1);
		}

		if (results[1] >= 1) {
			List<String> breakers = breakerMap.get(type);
			if (breakers != null) {
				for (String b : breakers) {
					logger.info(" ++ Broken by : " + b);
					String breakerMessage = check(b, executor, true);
					if (breakerMessage == null) {
						logger.warning(" ++ breaker result: OK");
					} else {
						logger.warning(" ++ breaker result: " + breakerMessage);
						String mfc = breakerMessage.substring(b.length()+1,b.length()+2);
						if (mfc.equals("F")) {
							message = type + " Broken by "+b;
							longMessage = type + " Broken by "+b;
						}
					}
				}
			} 
			if (!message.contains("Broken by")) {
				List<String> deps = typeDeps.get(type);
				if (deps != null) {
					for (String d : deps) {
						logger.info(" ++ Depends on : " + d);
						String depMessage = check(d, executor, false);
						if (depMessage != null) {
							message = message + ", " + depMessage;
							longMessage = longMessage + ", " + depMessage;
						}
					}
				}
			}
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
			logger.info(type +" Not warning or Error, but marking it down for cumulative");
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
		//ExecutorService executor = Executors.newFixedThreadPool(threads, Executors.defaultThreadFactory());
		ExecutorService executor = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
		String message = new String();
		logger.info("Entering Main Loop\n");
		long sleeper = 0l;
		while (true) {
			for (String top : topTypes) {
				if (getState(top) != STATE_OK) {
					logger.fine("Not Snoozing " + top + " (state not OK)");
				} else if (snoozeMap.containsKey(top)) {
					logger.finest("Snooze found for "+top);
					if (snoozeMap.get(top).snooze()) {
						logger.fine("Snoozing "+top);
						continue;
					}
					logger.fine("NOT Snoozing "+top);
				} else 
					logger.finest("Snooze NOT found for "+top);
				message=check(top, executor, true);
				dispatchNotification(top, message, executor);
			}
			dumpStatus(executor);
			try {
				sleeper = pause.longValue() + (long)(Math.random()*pauseExtra.longValue());
				logger.fine("top checks complete, initiating sleep for " + sleeper + " sec");
				//logger.finest("loop iteration: "+ ++loopCount);
				Thread.sleep(1000*sleeper);
			} catch (Exception e) {
				logger.severe(e.getMessage());
				/*
				executor.execute(new Runner(e.getStackTrace(), e.toString()));
				executor.shutdown();
				*/
				System.exit(1);
			}
		}
	}

	/*
	 * stores which notifications have been sent, and sends new ones
	 */
	private void dispatchNotification(String type, String message, Executor executor) {
		int notifThresh = typeThresholds.get(type)[2];
		short state = getState(type);
		if (message == null) {
			boolean recovery = false;
			switch(state) {
				case STATE_URGENT:
				case STATE_ERROR:
				case STATE_WARN:
					recovery = true;
					setState(type, STATE_OK);
					break;
				case STATE_NOTICE:
					setState(type, STATE_OK);
					break;
			}

			sentNotif.remove("U_"+type);
			sentNotif.remove("F_"+type);
			sentNotif.remove("W_"+type);
			sentNotif.remove("N_"+type);

			if (recovery) {
				notifyStateChange(type, executor, STATE_OK);
				setState(type, STATE_OK);
			}
			return;
		}

		Vector<String> v = new Vector<String>();
		ArrayList<String> notifyGroups = new ArrayList<String>();

		if (!notifyMap.containsKey(type)) {
			notifyGroups.addAll(defaultNotif);
		} else {
			notifyGroups.addAll(notifyMap.get(type));
		}

		String mfc = message.substring(type.length()+1,type.length()+2);
		short accum = 0;
		if (getState(type) < STATE_WARN) {
			if (mfc.equals("F") || mfc.equals("U") || mfc.equals("W")) {
				if (accumMap.get(type).isDisabled()) {
					logger.fine("Accum -> disabled for "+type);
				} else {
					accum = accumMap.get(type).fail(true);
					logger.info("Accum -> " + type + " ("+ accum +") "+ accumMap.get(type).getMessage(accum));
				}
			}
		}

		if (mfc.equals("N") && (accum == ACCUMNONE)) {
                        sentNotif.remove("U_"+type);
                        sentNotif.remove("F_"+type);
			sentNotif.remove("W_"+type);
			sentNotif.put("N_"+type, 0);
			if (state > STATE_NOTICE) {
				notifyStateChange(type, executor, STATE_OK);
				setState(type, STATE_OK);
			}

/*
		} else if ((!mfc.equals("W"))&&(!mfc.equals("F"))&&(!mfc.equals("U"))&&(!mfc.equals("N"))) {
			sentWNotif.remove("W_"+type);
			sentFNotif.remove("F_"+type);
			sentUNotif.remove("U_"+type);
*/
		} else {
			int notifRepeat = typeThresholds.get(type)[3];
			long lastMessage = 0l;
			Integer sn = new Integer(0);
			String key = new String();

			if (mfc.equals("U")) {
				key="U_"+type;
				sn = sentNotif.get(key);
				if (sn == null)
					sn=new Integer(0);
				sentNotif.put(key, ++sn);
				sentNotif.remove("F_"+type);
				sentNotif.remove("W_"+type);
				sentNotif.remove("N_"+type);
				notifThresh=1; // Urgent, send on 1st failure.
			} else if (mfc.equals("F")) {
				key="F_"+type;
				sn = sentNotif.get(key);
				if (sn == null)
					sn=new Integer(0);
				sentNotif.put(key, ++sn);
				sentNotif.remove("U_"+type);
				sentNotif.remove("W_"+type);
				sentNotif.remove("N_"+type);
				if (state > STATE_NOTICE && state != STATE_ERROR) {
					notifyStateChange(type, executor, STATE_ERROR);
					setState(type, STATE_ERROR);
				}
			} else if (mfc.equals("W")) {
				key="W_"+type;
				sn = sentNotif.get(key);
				if (sn == null)
					sn=new Integer(0);
				sentNotif.put(key, ++sn);
				sentNotif.remove("U_"+type);
				sentNotif.remove("F_"+type);
				sentNotif.remove("N_"+type);
				if (state > STATE_WARN) {
					notifyStateChange(type, executor, STATE_WARN);
					setState(type, STATE_WARN);
				}
			} else if (accum == ACCUMWARN) {
				key="AW_"+type;
			} else if (accum == ACCUMERROR) {
				key="AE_"+type;
			}
			try {
				lastMessage = lastNotif.get(key);
			} catch (NullPointerException npe) {
				lastMessage = 0L;
			}

			long timeNow = System.currentTimeMillis();
			long timeDiff = timeNow - lastMessage;



			logger.info("Count: "+sn+" (threshold:"+notifThresh+" repeat:"+notifRepeat+" sn%repeat:"+sn%notifRepeat+")");
			//boolean hitRepeatThresh = ((sn % notifRepeat) == notifThresh || sn == notifThresh);
			boolean hitRepeatThresh = (((sn - notifThresh) % notifRepeat) == 0);
			boolean hitReactThresh = (sn == typeThresholds.get(type)[5]);
			logger.fine("hitRepeatThresh: " + hitRepeatThresh);

			if ( hitRepeatThresh || (accum >= ACCUMWARN) ) {
				for (String ng : notifyGroups) {
					short warnOrError=0;
					if ((mfc.equals("F") && hitRepeatThresh) || mfc.equals("U") || accum == ACCUMERROR ) {
						v.addAll(errorMap.get(ng));
						warnOrError=2;
						logger.fine("Error scripts...");
					} else {
						v.addAll(warnMap.get(ng));
						warnOrError=1;
						logger.fine("Warning scripts...");
					}

					if (rotMap.containsKey(ng)) {
						logger.info("Rotation detected...");
						if ( sn == notifThresh || !rotMap.get(ng).getElevate() ) {
							OnCall o = rotMap.get(ng).getOnCall();
							logger.info("turn -> " + o.getName());
							v.addAll(rotMap.get(ng).getScripts(o, warnOrError));
						} else {
							logger.info("Repeat notification. Alerting everyone...");
							v.addAll(rotMap.get(ng).getAllScripts(warnOrError));
							logger.fine("Got Scripts: "+v.size());
						}
					}
				}
			}

			if ( hitReactThresh ) {
				if (mfc.equals("U"))
					react(type, executor);
				else if (mfc.equals("F"))
					react(type, executor);
				else if (mfc.equals("W"))
					react(type, executor);
			}

			boolean flapping = false;
			if (timeDiff <= defFlapBuffer && sn == notifThresh)
				flapping = true;

			if ( hitRepeatThresh ) {
				message="\""+message+" count:"+sn+"\"";
				if (!flapping) lastNotif.put(key,timeNow);
				accumMap.get(type).reset(ACCUMWARN);
				accumMap.get(type).reset(ACCUMERROR);
				if (mfc.equals("U"))
					setState(type, STATE_URGENT);
				else if (mfc.equals("F"))
					setState(type, STATE_ERROR);
				else if (mfc.equals("W"))
					setState(type, STATE_WARN);
				else if (mfc.equals("N"))
					setState(type, STATE_NOTICE);
			} else if (accum >= ACCUMWARN) {
				String foo = ( accum == ACCUMWARN ) ? "Warning" : "Error" ;
				message = "Accumulative "+foo+": " + type + " (" + accumMap.get(type).getMessage(accum)+")";
				accumMap.get(type).reset(accum);
				lastNotif.put(key,timeNow);
			}

			if (flapping) {
				webLog.info("Flapping service: " + type + "(" + (timeDiff/1000) + " secs since last notification)");
				logger.warning("Flapping service: " + type + " (" + (timeDiff/1000) + " secs since last notification) - Skipped");
			}
		}

		for (String ng : notifyGroups) {
			v.addAll(spamMap.get(ng));
		}

		for (String s : v) {
			Runnable r = new Runner(s+" "+message);
			logger.info("Dispatching notification " + s + " " + message);
			executor.execute(r);
		}

	} 

	private void react(String type, Executor executor) {
		if (reactionMap.containsKey(type)) {
			Vector<String> reactions = new Vector<String>(reactionMap.get(type));
			Runnable r;
			logger.info("Executing reaction scripts for "+type);
			for (String reaction:reactions) {
				r = new Runner(reaction);
				logger.info(" + "+reaction);
				webLog.info("Reaction initiated for "+type);
				executor.execute(r);
			}
		}
	}

	private short getState(String type) {
		if (stateMap.containsKey(type))
			return stateMap.get(type);
		else
			return STATE_OK;
	}
	private void setState(String type, short state) {
		if (state == STATE_OK) {
			stateMap.remove(type);
		} else {
			stateMap.put(type, state);
		}
	}

	private String stateToString(short state) {
		switch(state) {
                        case STATE_URGENT:
                                return "URGENT";
                        case STATE_ERROR:
				return "ERROR";
                        case STATE_WARN:
				return "WARNING";
                        case STATE_NOTICE:
				return "NOTICE";
                        case STATE_OK:
				return "OK";
                }
		return "Invalid State";
	}

	private void notifyStateChange(String type, Executor executor, short newState) {
		logger.warning("State change to "+ stateToString(newState) +" for type: "+type);
		String message = new String();
		switch(newState) {
			case STATE_URGENT:
			case STATE_ERROR:
			case STATE_WARN:
				message = type + " transitioned to "+ stateToString(newState) +" state!";
				break;
			case STATE_NOTICE:
			case STATE_OK:
				message = "Recovery: "+type;
				break;
		}

		ArrayList<String> notifyGroups = new ArrayList<String>();
		if (!notifyMap.containsKey(type)) {
			notifyGroups.addAll(defaultNotif);
		} else {
			notifyGroups.addAll(notifyMap.get(type));
		}

		Vector<String> v = new Vector<String>();
		for (String ng : notifyGroups) {
			v.addAll(warnMap.get(ng));
			if (rotMap.containsKey(ng))
				v.addAll(rotMap.get(ng).getAllScripts((short)1));// 1 is warning
		}
		for (String s : v) {
			Runnable r = new Runner(s+" "+message);
			logger.info("Dispatching notification " + s + " " + message);
			executor.execute(r);		
		}
		//Runnable r = new Runner(type, message, "recovery");
		//executor.execute(r);
	}


/*
	 * This class is used for threading.
	 * The check name is given in the constructor
	 * It returns null if the check returns OK
	 * Else it returns the 1st line of the output of the script
	 */
	private static class Checker implements Callable<String> {
		public Checker( String theCheck ) {
			check = new String(theCheck);
		}

		public String getCheck() {
			return check;
		}

		public String call() throws IOException, InterruptedException {
//			try {
				Process process = Runtime.getRuntime().exec(check);
				String line = new String();
				try {
					process.waitFor();
				} catch (InterruptedException e) {
					//System.err.println(e.getMessage());
					//return("1 "+check+": timeout");
					return(null);
				}
				line = process.exitValue() + " ";
				InputStream stdin = process.getInputStream();
				BufferedReader is = new BufferedReader(new InputStreamReader(stdin));
				String result = is.readLine();
				is.close();
				line = line + result;
				return line;
/*
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
*/
		}

		private String check;
	}

	/*
	 * Simple thread runner
	 */
	private static class Runner implements Runnable {
		public Runner(String all){
			runme = new String(all);
		}

		public void run() {
			try {
				Process process = Runtime.getRuntime().exec(runme);
				try {
					//System.err.println(runme);
					process.waitFor();
				} catch (InterruptedException e) {
					System.err.println(e.getMessage());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		private String runme;
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
			SimpleDateFormat date_format = new SimpleDateFormat("MMM dd HH:mm:ss.S");
			Date resultdate = new Date(millisecs);
			return date_format.format(resultdate);
		}

		public String getHead(Handler h) {
			return "Thergioni Logger Initiated : " + (new Date()) + "\n";
		}
		public String getTail(Handler h) {
			return "Thergioni Logger Exiting : " + (new Date()) + "\n";
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
			return "<html><head><meta charset=\"utf-8\"></head><body>\n";
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

	private class Rotater {
		public Rotater(String n, Logger logger) {
			name = n;
			rotPerWeek = 0;
			rotTime = 12;
			oncMap = new HashMap<String,OnCall>();
			oncNames = new ArrayList<String>();
			elevateNames = new ArrayList<String>();
			logger.config("+ Rotation -> group "+n);
			warnScripts = new Vector<String>();
			errorScripts = new Vector<String>();
		}
	
		public Vector<String> getWarn() {
			return warnScripts;
		}

		public void setWarn(List<String> wrscr) {
			for (String w : wrscr) {
				w=w.replaceAll("\\$cp", checkPath);
				logger.config("++ Warning Script: " + w);
				warnScripts.addElement(w);
			}
		}

		public Vector<String> getScripts(OnCall onc, short warnOrError) {
			Vector<String> scripts = new Vector<String>(); 
			Vector<String> op = new Vector<String>();

			if (warnOrError == 1 )
				scripts.addAll(warnScripts);
			else
				scripts.addAll(errorScripts);

			for (String es : scripts) {
				es=es.replaceAll("\\$name", onc.getName());
				try {
					es=es.replaceAll("\\$email", onc.getEmail());
				} catch (NullPointerException npe) {
					logger.warning("No Email set for "+onc.getName());
				}
				try {
					es=es.replaceAll("\\$number", onc.getNumber());
				} catch (NullPointerException npe) {
					logger.warning("No Number set for "+onc.getName());
				}
				try {
					es=es.replaceAll("\\$xmpp", onc.getXmpp());
				} catch (NullPointerException npe) {
					logger.warning("No xmpp set for "+onc.getName());
				}
				if (es.contains("$name") || es.contains("$email") || es.contains("$number") || es.contains("$xmpp")) {
					logger.warning("Skipping (unset var): " +es);
				} else {
					logger.info("Adding notification: "+es);
					op.add(es);
				}
			}
			return op;
		}

		public Vector<String> getAllScripts(short warnOrError) {
			Vector<String> scripts = new Vector<String>(); 
			Vector<String> op = new Vector<String>();

			if (warnOrError == 1)
				scripts.addAll(warnScripts);
			else
				scripts.addAll(errorScripts);

			ArrayList<String> allNames = new ArrayList<String>();
			allNames.addAll(oncNames);
			allNames.addAll(elevateNames);
			for (String oName : allNames) {
				OnCall onc = oncMap.get(oName);
				Vector<String> scriptClone = new Vector<String>();
				scriptClone.addAll(scripts);
				for (String es : scripts) {
					es=es.replaceAll("\\$name", onc.getName());
					try {
						es=es.replaceAll("\\$email", onc.getEmail());
					} catch (NullPointerException npe) {
						logger.warning("No Email set for "+onc.getName());
					}
					try {
						es=es.replaceAll("\\$number", onc.getNumber());
					} catch (NullPointerException npe) {
						logger.warning("No Number set for "+onc.getName());
					}
					try {
						es=es.replaceAll("\\$xmpp", onc.getXmpp());
					} catch (NullPointerException npe) {
						logger.warning("No xmpp set for "+onc.getName());
					}

					if (es.contains("$name") || es.contains("$email") || es.contains("$number") || es.contains("$xmpp")) {
						logger.warning("Skipping (unset var): " +es);
					} else {
						logger.info("Adding notification: "+es);
						op.add(es);
					}
				}
			}
			logger.fine("Script count: " + op.size());
			return op;
		}

		public void setError(List<String> erscr) {
			//logger.config("++ Setting warning to " +w);
			//Do something
			//List<String> wrscr = notification.getWarningScript();
			for (String e : erscr) {
				e=e.replaceAll("\\$cp", checkPath);
				logger.config("++ Error Script: " + e);
				errorScripts.addElement(e);
			}
		}

		public void setTime(BigInteger t) {
			logger.config("++ Setting Time to " +t);
			rotTime = t.intValue();
		}
		public void setDay(List<BigInteger> d) {
			rotDays = new TreeSet<Integer>();
			for (BigInteger day : d) {
				logger.config("++ Setting Day to " +day+ " (1=Sun)");
				rotDays.add(day.intValue());
			}
			rotPerWeek = d.size();
		}
		public void setOnCall(OnCall onc) {
			logger.config("++ Adding onCall " +onc.getName());
			logger.config("+++ with email " + onc.getEmail());
			logger.config("+++ with number " + onc.getNumber());
			logger.config("+++ with xmpp " + onc.getXmpp());
			if (!onc.isElevateOnly()) {
				oncNames.add(onc.getName());
			} else {
				elevateNames.add(onc.getName());
			}
			oncMap.put(onc.getName(), onc);
		}
		public OnCall getOnCall() {
			//This has to calculate who's turn it is. Or via thread + sleep etc keep track of who's turn it is.
			long currentTime = System.currentTimeMillis();
			long sundayOffset = new GregorianCalendar(1970,0,4).getTimeInMillis();
			logger.info("CurrentTime: "+currentTime+" SundayOffset: "+sundayOffset);
			currentTime -= sundayOffset;
			long week = 604800000l;
			int weeks = (int)(currentTime / week);
			int rotations = (int)(weeks * rotPerWeek);

			Calendar c = Calendar.getInstance();
			int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
			int hrOfDay = c.get(Calendar.HOUR_OF_DAY);
			logger.info("Day: " + dayOfWeek + " - hr: "+hrOfDay);

			int rotThisWeek = rotDays.headSet(new Integer(dayOfWeek)).size();
			if (rotDays.contains(dayOfWeek) && (hrOfDay >= rotTime))
				rotThisWeek++;

			int who = (rotations + rotThisWeek) % oncNames.size();

			String onc = oncNames.get(who);
			logger.info("Weeks since epoch: "+ weeks);
			String exc = (hrOfDay >= rotTime) ? "has" : "has not yet";
			logger.info("Rotation time "+ exc +" exceeded rotation hour ("+rotTime+")");
			logger.info("Rotations per week: "+rotPerWeek);
			logger.info("Rotations this week: "+rotThisWeek);
			logger.info("Rotations (= weeks since epoch * rotPerWeek + rotThisWeek): "+ (rotations+rotThisWeek));
			logger.info("On call count: " +oncNames.size());
			logger.info("Rotations % oncall_Count: "+ who);
			logger.info("which is... " + onc);
			return oncMap.get(onc);
		}
		public void setRemind(List<String> rem) {
			reminders = rem;
		}
		public void setElevate(boolean tof) {
			elevate=tof;
		}
		public boolean getElevate() {
			logger.info("Elevation is " + elevate);
			return elevate;
		}
		public void setName(String foo) {
			name = foo;
		}
		public String getName() {
			return name;
		}

		private String name;
		private boolean elevate;
		private List<String> reminders;
		private Map<String, OnCall> oncMap;
		private Map<String, OnCall> elevateMap;
		private List<String> oncNames;
		private List<String> elevateNames;
		private int rotPerWeek;
		private int rotTime;
		private SortedSet<Integer> rotDays;
		private Vector<String> warnScripts;
		private Vector<String> errorScripts;
	}

	private class RotationNotifier implements Runnable {
                public RotationNotifier() {
			rotNotifierMap = new HashMap<String, String>();
                }

                public void run() {
			LocalDateTime now = LocalDateTime.now();
			int minute = now.getMinute();
			int toSleep = (60 - minute)*60000;
			while (true) {
				checkRotations();
				try {
					Thread.sleep(toSleep);
				} catch (InterruptedException e) {
					System.err.println("Ooops: "+e.getCause());
				}
				toSleep = 3600000;
			}
                }

		private void checkRotations() {
			for (String k : rotMap.keySet()) {
				String ocName = rotMap.get(k).getOnCall().getName();
				if (!ocName.equals(rotNotifierMap.get(k))) {
					String output = "Oncall changed for "+k+" from " + rotNotifierMap.get(k) + " to "+ocName;
					logger.info(output);
					webLog.info(output);
					rotNotifierMap.put(k, ocName);
				}
                        }
		}

		private Map<String, String> rotNotifierMap;
        }


	private class OnCall {
		public OnCall(String nm, String em, String num, String xm, boolean elvOnly) {
			name=nm;
			email=em;
			number=num;
			xmpp=xm;
			elevateOnly=elvOnly;
		}
		public String getName() {
			return name;
		}
		public String getEmail() {
			return email;
		}
		public String getNumber() {
			return number;
		}
		public String getXmpp() {
			return xmpp;
		}
		public boolean isElevateOnly() {
			return elevateOnly;
		}
		private String name;
		private String email;
		private String number;
		private String xmpp;
		private boolean elevateOnly;
	}

	private class Snooze {
		public Snooze(int snm) {
			snoozeMax = snm;
			snoozeCnt = snm;
		}
		public boolean snooze() {
			snoozeCnt++;
			logger.fine("Snooze cnt:"+snoozeCnt+" max:"+snoozeMax);
			if (snoozeCnt >= snoozeMax) {
				snoozeCnt = 0;
				return false;
			}	
			return true;
		}
		private int snoozeMax;
		private int snoozeCnt;
	}

	private class TypeAccum {
		public TypeAccum(int atw, int ate, int atmw, int atme ) {
			long timeNow = System.currentTimeMillis();
			accThreshWarn = atw;
			accThreshError = ate;

			disabled=false;

			if ((atw == 0) && (ate == 0))
				disabled=true;

			accTimeWarn = atmw*60000l;
			accTimeError = atme*60000l;

			tsWarn = timeNow;
			tsErr = timeNow;
			cntWarn = 0;
			cntErr = 0;
		}

		public String getMessage(short f) {
			String returnText;
			long timeNow = System.currentTimeMillis();
			if (disabled) {
				returnText = "Accum Checks Disabled";
			} else if (f == ACCUMWARN) {
				returnText = cntWarn + " failed checks in last " + ((timeNow - tsWarn)/60000l) +" mins.";
			} else if (f == ACCUMERROR) {
				returnText = cntErr + " failed checks in last " + ((timeNow - tsErr)/60000l) +" mins.";
			} else {
				returnText = "Warn:" + cntWarn + " Err:" + cntErr;
			}
			return returnText;
		}

		public short fail(boolean inc) {
			short fail = 0;
			if (disabled)
				return fail;

			long timeNow = System.currentTimeMillis();

			if (accThreshWarn > 0) {
				if ((timeNow - tsWarn) > accTimeWarn) {
					tsWarn = timeNow;
					cntWarn = 0;
				}
				if (inc)
					cntWarn++;
				if (cntWarn >= accThreshWarn) {
					fail = 1;
				}
			}

			if (accThreshError > 0) {
				if ((timeNow - tsErr) > accTimeError) {
					tsErr = timeNow;
					cntErr = 0;	
				}
				if (inc)
					cntErr++;
				if (cntErr >= accThreshError) {
					fail = 2;
				}
			}

			return fail;
		}

		public void reset(short f) {
			long timeNow = System.currentTimeMillis();
			if (f == ACCUMWARN) {
				tsWarn = timeNow;
				cntWarn = 0;
			} else if (f == ACCUMERROR) {
				tsErr = timeNow;
				cntErr = 0;	
			}
		}

		private boolean isDisabled() {
			return disabled;
		}

		private int accThreshWarn;
		private int accThreshError;
		private long accTimeWarn;
		private long accTimeError;

		private int cntWarn;
		private int cntErr;
		private long tsErr;
		private long tsWarn;

		private boolean disabled;
	}

	// Maps thresholds
	private Map<String,int[]> typeThresholds;
	// Maps F_type
	private Map<String,Integer> sentNotif;
	// Maps W_type
	//private Map<String,Integer> sentWNotif;
	// Maps U_type
	//private Map<String,Integer> sentUNotif;
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
	private Map<String,List<String>> argMap;
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
	private int defUrgentThresh;
	private int defAccumThreshWarn;
	private int defAccumThreshError;
	private int defAccumTimeWarn;
	private int defAccumTimeError;
	private Long defFlapBuffer;
//	private int threads;
	private int loopCount;
	private int threadCount;
	private String checkPath;
	private Map<String,Vector<String>> warnMap;
	private Map<String,Vector<String>> errorMap;
	private Map<String,Vector<String>> spamMap;
	private Map<String,List<String>> notifyMap;
	private Map<String,TypeAccum> accumMap;
	private Map<String,Snooze> snoozeMap;
	private Map<String,Vector<String>> reactionMap;
	private Long timeOut;
	private BigInteger pause;
	private BigInteger pauseExtra;
	private ObjectFactory of;
//	private String confFile;
//	private String confPath;
	private String statusFilePath;
	private String statusScript;
	private Logger logger;
	private Logger webLog;
	private Logger webConf;
	private String webTitle;
	private String faviconOk;
	private String faviconNotice;
	private String faviconWarning;
	private String faviconError;
	private String faviconUrgent;
	private Vector<String> topTypes;
	private Vector<String> longOutputTypes;
	private List<String> defaultNotif;
	private Map<String, Rotater> rotMap;
	private Map<String, Short> stateMap;
	private String[] failedColors;
	private String[] failedFavicons;
	private static final short ACCUMNONE = 0;
	private static final short ACCUMWARN = 1;
	private static final short ACCUMERROR = 2;
	private static final short STATE_OK = 0;
	private static final short STATE_NOTICE = 1;
	private static final short STATE_WARN = 2;
	private static final short STATE_ERROR = 3;
	private static final short STATE_URGENT = 4;
}
