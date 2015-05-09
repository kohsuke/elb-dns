package org.kohsuke.elbdns;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.RRset;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.SetResponses;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Maps <tt>*.NAME-elb-ID.REGION.elb.io</tt> to <tt>NAME-elb-ID.REGION.elb.amazonaws.com</tt>.
 *
 * <p>
 * This way, every ELB gets automatic wildcard domain names.
 *
 * @author Kohsuke Kawaguchi
 */
public class ElbApp extends App {
    private Name base;

    public static void main(String[] args) throws CmdLineException {
        ElbApp app = new ElbApp();
        new CmdLineParser(app).parseArgument(args);
        app.main();
    }

    @Option(name="-b",usage="Based domain name",required=true)
    public void setBase(String name) throws TextParseException {
        if (!name.endsWith("."))    name=name+".";
        this.base = Name.fromString(name);
    }

    @Override
    protected boolean isNameInZone(Name name) {
        return name.subdomain(base);
    }

    @Override
    protected SetResponse findMatchingRecords(Name name, int type) {
        LOGGER.fine(String.format("%d:%s\n", type, name));
        Name n = name.relativize(base);

//        if (type==Type.NS) {
//            return SetResponses.success(new RRset(new NSRecord(name, DClass.IN, 3600, Name.fromConstantString("infradna.com."))));
//        }

        int l = n.labels();

        if (l<2) {// expecting at least two labels: NAME-elb-ID.REGION
            LOGGER.fine("Expecting 2 labels but found " + n);
            return SetResponses.NXDOMAIN;
        }

        // for simple string manipulation, we allow names like
        // http://xyz.mesos-master-elb-1684063001.us-west-2.elb.amazonaws.com.elb.kohsuke.org/
        while (IGNORED_LABELS.contains(n.getLabelString(l - 1))) {
            l--;
        }

        try {
            Name t = Name.fromString(n.getLabelString(l - 2) + "." + n.getLabelString(l - 1) + ".elb.amazonaws.com.");
            LOGGER.fine(name + " -> " + t);
            return SetResponses.success(new RRset(new CNAMERecord(name, DClass.IN, 3600, t)));
        } catch (TextParseException e) {
            LOGGER.log(WARNING, "Failed to parse "+name, e);
            return SetResponses.NXDOMAIN;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ElbApp.class.getName());

    private static final Set<String> IGNORED_LABELS = new HashSet<>(Arrays.asList("elb","amazonaws","com"));
}
