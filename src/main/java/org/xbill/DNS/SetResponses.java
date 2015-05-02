package org.xbill.DNS;

/**
 * @author Kohsuke Kawaguchi
 */
public class SetResponses {
    public static final SetResponse NXDOMAIN = SetResponse.ofType(SetResponse.NXDOMAIN);

    public static SetResponse success(RRset rrs) {
        SetResponse sr = new SetResponse(SetResponse.SUCCESSFUL);
        sr.addRRset(rrs);
        return sr;
    }

    public static SetResponse cname(RRset rrs) {
        SetResponse sr = new SetResponse(SetResponse.CNAME);
        sr.addRRset(rrs);
        return sr;
    }
}
