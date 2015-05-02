package org.xbill.DNS;

/**
 * @author Kohsuke Kawaguchi
 */
public class SetResponses {
    public static final SetResponse NXDOMAIN = SetResponse.ofType(SetResponse.NXDOMAIN);

    public static SetResponse success(RRset rrset) {
        SetResponse sr = new SetResponse(SetResponse.SUCCESSFUL);
        sr.addRRset(rrset);
        return sr;
    }
}
