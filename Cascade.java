import java.io.*;
import java.util.*;
import javax.xml.bind.*;
import generated.*;

class Cascade {
	public static void main(String args[]) {
		//Service myTestService = new Service("BulletProof");
		//System.out.println(myTestService.getName());
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

	private void printXml(Service printMe) {
		try {
			//JAXBElement<Service> gl = of.createService(printMe);
			JAXBContext jc = JAXBContext.newInstance("Service");
			Marshaller m = jc.createMarshaller();
			m.marshal( printMe, System.out );
		} catch( JAXBException jbe ){
			System.err.println(jbe);
		}
	}

	private void readXml(String fileName) {
		//String packageName = docClass.getPackage().getName();
		//JAXBElement<Service> koko = (JAXBElement<Service>)u.unmarshal( new FileInputStream(fileName));
		try {
			JAXBContext jc = JAXBContext.newInstance(Site.class);
			Unmarshaller u = jc.createUnmarshaller();
			Site ite = (Site)u.unmarshal( new FileInputStream(fileName));
			List<Site.Nodes> nodes = new ArrayList<Site.Nodes>();
			System.out.println(ite.getName());
			//nodes = ite.getNodes();
			//for ( Site.Node n : nodes ) {
			//	System.out.println(n.getName());
			//}
			//System.out.println(koko.getNode().getName());
			//List<String> types = new ArrayList<String>();
			//types = koko.getType();
			//for ( String s : types ) {
			//	System.out.println(s);
			//}
		} catch (Exception fnfe) {
			System.err.println(fnfe);
		}
	}
	
	private ObjectFactory of;
	private String confFile;
	private String confPath;
}
