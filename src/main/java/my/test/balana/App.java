package my.test.balana;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.wso2.balana.*;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;

import org.wso2.balana.PDP;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.finder.AttributeFinder;
import org.wso2.balana.finder.AttributeFinderModule;
import org.wso2.balana.finder.PolicyFinder;

import se.sics.DBAttributeFinderModule;

/**
 * My BALANA-with-DATABASE test!
 * Created by ipatini on 14/12/2016, using code from ExamplePDP of Ludwig.
 */
public class App 
{
	/**
	 * Usage: java my.test.balana.App [-v] [-D<delay_sec>] [-R<eval_iterations>] [-c<precision>] [-I<uc_id>] [-P<policies_dir>] [<XACML-request-file> [<XACML-response-file> | -o]]
	 */
    public static void main( String[] args ) throws Exception
    {
		if (args.length==0) {
			System.out.println( "\nMy BALANA test!\n" );
			System.out.println("Usage: java my.test.balana.App [-v] [-D<delay_sec>] [-R<eval_iterations>] [-c<precision>] [-I<uc_id>] [-P<policies_dir>] [<XACML-request-file> [<XACML-response-file> | -o]]");
			return;
		}
		
		// Check for verbose flag
		int p = 0;
		boolean verbose = false;
		if (args.length>p && args[p].trim().equals("-v")) { verbose = true; p++; }
		
        if (verbose) System.out.println( "\nMy BALANA-with-DATABASE test!\n" );
		
		// Measure initial memory consumption
		Runtime rt = Runtime.getRuntime();
		double startMem = rt.totalMemory() - rt.freeMemory();
		
		// Insert a start delay if -D argument is specified
		long startDelay = 0;
		if (args.length>p && args[p].trim().startsWith("-D")) {
			startDelay = Long.parseLong(args[p].substring(2));
			try { Thread.currentThread().sleep(startDelay*1000); } catch (InterruptedException e) {}
			p++;
		}
		
		// Specify evaulation iterations if -R argument is specified
		int evalIterations = 1;
		if (args.length>p && args[p].trim().startsWith("-R")) { evalIterations = Integer.parseInt(args[p].trim().substring(2)); p++; }
		
		// Specify measurements print precision
		int prec = 3;
		if (args.length>p && args[p].trim().startsWith("-c")) { prec = Integer.parseInt(args[p].trim().substring(2)); p++; }
		
		// Define a Use Case id (used to report measurements at the end)
		String runId = "";
		if (args.length>p && args[p].trim().startsWith("-I")) { runId = args[p].trim().substring(2); p++; }
		
		// Specify "policies" directory (default "policies")
		String policyDirectoryLocation = "policies";
		if (args.length>p && args[p].trim().startsWith("-P")) { policyDirectoryLocation = args[p].trim().substring(2); p++; }
        if (verbose) System.out.println( "POLICY-DIR:  "+policyDirectoryLocation );
		
		// Specify XACML request file (default "xacml-request.xml")
		String xacmlRequestFile = "xacml-request.xml";
		if (args.length>p && !args[p].trim().isEmpty()) { xacmlRequestFile = args[p].trim(); p++; }
		// Load XACML request from file
		String xacmlRequest = new java.util.Scanner(new java.io.File(xacmlRequestFile)).useDelimiter("\\Z").next();
        if (verbose) System.out.println( "XACML-REQUEST:\n"+xacmlRequest+"\n" );
		
		// BALANA configuration (loading of policies?)
		long tm0 = System.nanoTime();
		String configFileLocation = "balana.cfg.xml";
		System.setProperty(ConfigurationStore.PDP_CONFIG_PROPERTY, configFileLocation);
		System.setProperty(FileBasedPolicyFinderModule.POLICY_DIR_PROPERTY, policyDirectoryLocation);
		//XXX: Balana balana = Balana.getInstance();
		//XXX: PDP pdp = new PDP(balana.getPdpConfig());

		Set<String> fileNames 
				= getFilesInFolder(policyDirectoryLocation, ".xml");
		PolicyFinder pf = new PolicyFinder();
		FileBasedPolicyFinderModule  pfm 
			= new FileBasedPolicyFinderModule(fileNames);
		pf.setModules(Collections.singleton(pfm));
		pfm.init(pf);

		// registering new attribute finder, so default PDPConfig is needed to change
		PDPConfig pdpConfig = org.wso2.balana.Balana.getInstance().getPdpConfig();
		AttributeFinder attributeFinder = pdpConfig.getAttributeFinder();
		java.util.List<AttributeFinderModule> finderModules = attributeFinder.getModules();
		AttributeFinderModule afm = new DBAttributeFinderModule(null, null, null);
		finderModules.add(afm);
		attributeFinder.setModules(finderModules);
		PDP pdp = new PDP(new PDPConfig(attributeFinder, pf, null, true));

		// Measure memory consumption after BALANA initialization
		double initMem = rt.totalMemory() - rt.freeMemory();

		// BALANA execution - XACML request evaluation
		long tm1 = System.nanoTime();
		String xacmlResponse = null;
		for (int i=0; i<evalIterations; i++) {
			xacmlResponse = pdp.evaluate(xacmlRequest);
		}
		long tm2 = System.nanoTime();
		
		// Specify XACML response file (default "xacml-response.xml")
		if (xacmlResponse!=null) {
			if (verbose) System.out.println( "\nXACML-RESPONSE:\n"+xacmlResponse+"\n" );
			String xacmlResponseFile = null;
			if (args.length>p && !args[p].trim().isEmpty()) xacmlResponseFile = args[p].trim();
			if (xacmlResponseFile!=null && xacmlResponseFile.equals("-o")) xacmlResponseFile = "xacml-response.xml";
			// Save XACML response to file
			if (xacmlResponseFile!=null) {
				try(java.io.PrintStream ps = new java.io.PrintStream(xacmlResponseFile)) { ps.println(xacmlResponse); }
			}
		}
		
		// Measure end-of-execution memory consumption
		double endMem = rt.totalMemory() - rt.freeMemory();
		double usedMem = endMem-initMem;
		
		double TO_MB = 1024*1024;
		double TO_MSEC = 1000000f;
		
		// Print measurements
		String reportHeader = "UC#\tTotal dur (ms)\tEval. dur (ms)\tEval. iter. (#)\tEval. iter. dur (ms)\tStart mem (mb)\tInit. mem (mb)\tEnd mem (mb)\n";
		int cols = reportHeader.split("[\t]+").length-1;
		StringBuilder sb = new StringBuilder("%%s");
		Integer[] colsArgs = new Integer[cols];
		for (int i=0; i<cols; i++) { sb.append("\t%%.%df"); colsArgs[i]=prec; }
		String preFmt = sb.append("\n").toString();
		
		String fmt = String.format( preFmt, colsArgs);
        System.out.printf( reportHeader );
        System.out.printf( fmt, runId, (tm2-tm0)/TO_MSEC, (tm2-tm1)/TO_MSEC, (double)evalIterations, (tm2-tm1)/evalIterations/TO_MSEC, startMem/TO_MB, initMem/TO_MB, endMem/TO_MB );
		
        if (verbose) System.out.println( "Bye!\n" );
    }

    private static Set<String> getFilesInFolder(String directory, final String extension) {
        File dir = new File(directory);
        String[] children = null;
        if (extension != null) {
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File f, String name) {
                    return name.endsWith(extension);
                }
            };
            children = dir.list(filter);
        } else {
            children = dir.list();
        }
        HashSet<String> result = new HashSet<String>();
        for (int i=0; i<children.length;i++) {
            result.add(directory + System.getProperty("file.separator") + children[i]);
        }
        return result;
    }

}
