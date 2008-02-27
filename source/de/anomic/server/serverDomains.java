// serverDNSCache.java
// -----------------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 23.07.2007 on http://yacy.net
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.plasma.plasmaSwitchboard;

public class serverDomains {

    // a dns cache
    private static final Map<String, InetAddress> nameCacheHit = new ConcurrentHashMap<String, InetAddress>(); // a not-synchronized map resulted in deadlocks
    private static final Set<String> nameCacheMiss = Collections.synchronizedSet(new HashSet<String>());
    private static final kelondroMScoreCluster<String> nameCacheHitAges = new kelondroMScoreCluster<String>();
    private static final kelondroMScoreCluster<String> nameCacheMissAges = new kelondroMScoreCluster<String>();
    private static final int maxNameCacheHitAge = 24 * 60 * 60; // 24 hours in minutes
    private static final int maxNameCacheMissAge = 24 * 60 * 60; // 24 hours in minutes
    private static final int maxNameCacheHitSize = 3000; 
    private static final int maxNameCacheMissSize = 3000; 
    public  static final List<String> nameCacheNoCachingPatterns = Collections.synchronizedList(new LinkedList<String>());
    private static final Set<String> nameCacheNoCachingList = Collections.synchronizedSet(new HashSet<String>());
    private static final long startTime = System.currentTimeMillis();

    /**
    * Converts the time to a non negative int
    *
    * @param longTime Time in miliseconds since 01/01/1970 00:00 GMT
    * @return int seconds since startTime
    */
    private static int intTime(long longTime) {
        return (int) Math.max(0, ((longTime - startTime) / 1000));
    }

    /**
    * Does an DNS-Check to resolve a hostname to an IP.
    *
    * @param host Hostname of the host in demand.
    * @return String with the ip. null, if the host could not be resolved.
    */
    public static InetAddress dnsResolveFromCache(String host) throws UnknownHostException {
        if ((host == null) || (host.length() == 0)) return null;
        host = host.toLowerCase().trim();        
        
        // trying to resolve host by doing a name cache lookup
        InetAddress ip = (InetAddress) nameCacheHit.get(host);
        if (ip != null) return ip;
        
        if (nameCacheMiss.contains(host)) return null;
        throw new UnknownHostException("host not in cache");
    }
    
    public static InetAddress dnsResolve(String host) {
        if ((host == null) || (host.length() == 0)) return null;
        host = host.toLowerCase().trim();        
        
        // trying to resolve host by doing a name cache lookup
        InetAddress ip = (InetAddress) nameCacheHit.get(host);
        if (ip != null) return ip;
        
        if (nameCacheMiss.contains(host)) return null;
        //System.out.println("***DEBUG dnsResolve(" + host + ")");
        try {
            boolean doCaching = true;
            ip = InetAddress.getByName(host);
            if ((ip == null) ||
                (ip.isLoopbackAddress()) ||
                (nameCacheNoCachingList.contains(ip.getHostName()))
            ) {
                doCaching = false;
            } else {
                Iterator<String> noCachingPatternIter = nameCacheNoCachingPatterns.iterator();
                String nextPattern;
                while (noCachingPatternIter.hasNext()) {
                    nextPattern = noCachingPatternIter.next();
                    if (ip.getHostName().matches(nextPattern)) {
                        // disallow dns caching for this host
                        nameCacheNoCachingList.add(ip.getHostName());
                        doCaching = false;
                        break;
                    }
                }
            }
            
            if (doCaching) {
                // remove old entries
                flushHitNameCache();
                
                // add new entries
                synchronized (nameCacheHit) {
                    nameCacheHit.put(ip.getHostName(), ip);
                    nameCacheHitAges.setScore(ip.getHostName(), intTime(System.currentTimeMillis()));
                }
            }
            return ip;
        } catch (UnknownHostException e) {
            // remove old entries
            flushMissNameCache();
            
            // add new entries
            nameCacheMiss.add(host);
            nameCacheMissAges.setScore(host, intTime(System.currentTimeMillis()));
        }
        return null;
    }

//    /**
//    * Checks wether an hostname already is in the DNS-cache.
//    * FIXME: This method should use dnsResolve, as the code is 90% identical?
//    *
//    * @param host Searched for hostname.
//    * @return true, if the hostname already is in the cache.
//    */
//    public static boolean dnsFetch(String host) {
//        if ((nameCacheHit.get(host) != null) /*|| (nameCacheMiss.contains(host)) */) return false;
//        try {
//            String ip = InetAddress.getByName(host).getHostAddress();
//            if ((ip != null) && (!(ip.equals("127.0.0.1"))) && (!(ip.equals("localhost")))) {
//                nameCacheHit.put(host, ip);
//                return true;
//            }
//            return false;
//        } catch (UnknownHostException e) {
//            //nameCacheMiss.add(host);
//            return false;
//        }
//    }

    /**
    * Returns the number of entries in the nameCacheHit map
    *
    * @return int The number of entries in the nameCacheHit map
    */
    public static int nameCacheHitSize() {
        return nameCacheHit.size();
    }

    public static int nameCacheMissSize() {
        return nameCacheMiss.size();
    }

    /**
    * Returns the number of entries in the nameCacheNoCachingList list
    *
    * @return int The number of entries in the nameCacheNoCachingList list
    */
    public static int nameCacheNoCachingListSize() {
        return nameCacheNoCachingList.size();
    }
    

    /**
    * Removes old entries from the dns hit cache
    */
    public static void flushHitNameCache() {
        int cutofftime = intTime(System.currentTimeMillis()) - maxNameCacheHitAge;
        String k;
        while ((nameCacheHitAges.size() > maxNameCacheHitSize) || (nameCacheHitAges.getMinScore() < cutofftime)) {
            k = (String) nameCacheHitAges.getMinObject();
            if (nameCacheHit.remove(k) == null) break; // ensure termination
            nameCacheHitAges.deleteScore(k);
        }
        
    }
    
    /**
     * Removes old entries from the dns miss cache
     */
     public static void flushMissNameCache() {
        int cutofftime = intTime(System.currentTimeMillis()) - maxNameCacheMissAge;
        String k;
        while ((nameCacheMissAges.size() > maxNameCacheMissSize) || (nameCacheMissAges.getMinScore() < cutofftime)) {
            k = (String) nameCacheMissAges.getMinObject();
            if (!nameCacheMiss.remove(k)) break; // ensure termination
            nameCacheMissAges.deleteScore(k);
        }
        
    }

    private static InetAddress[] localAddresses = null;
    static {
        try {
            localAddresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            localAddresses = new InetAddress[0];
        }
    }
    
    public static boolean isLocal(String address) {

        // attention! because this method does a dns resolve to look up an IP address,
        // the result may be very slow. Consider 100 milliseconds per access
        
        assert (address != null);

        // check local ip addresses
        if (address.equals("localhost") || address.startsWith("127")
                || address.startsWith("192.168")
                || address.startsWith("10.")
                || address.startsWith("169.254")
                ||
                // 172.16.0.0-172.31.255.255 (I think this is faster than a regex)
                (address.startsWith("172.") && (address.startsWith("172.16.")
                        || address.startsWith("172.17.")
                        || address.startsWith("172.18.")
                        || address.startsWith("172.19.")
                        || address.startsWith("172.20.")
                        || address.startsWith("172.21.")
                        || address.startsWith("172.22.")
                        || address.startsWith("172.23.")
                        || address.startsWith("172.24.")
                        || address.startsWith("172.25.")
                        || address.startsWith("172.26.")
                        || address.startsWith("172.27.")
                        || address.startsWith("172.28.")
                        || address.startsWith("172.29.")
                        || address.startsWith("172.30.")
                        || address.startsWith("172.31."))))
            return true;

        // make a dns resolve if a hostname is given and check again
        final InetAddress clientAddress = dnsResolve(address);
        if (clientAddress != null) {
            if ((clientAddress.isAnyLocalAddress()) || (clientAddress.isLoopbackAddress())) return true;
            if (address.charAt(0) > '9') address = clientAddress.getHostAddress();
        }

        // finally check if there are other local IP adresses that are not in
        // the standard IP range
        for (int i = 0; i < localAddresses.length; i++) {
            if (localAddresses[i].equals(clientAddress)) return true;
        }

        // the address must be a global address
        return false;
    }
    
    public static String myPublicIP() {
        try {

            // if a static IP was configured, we have to return it here ...
            plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            if (sb != null) {
                String staticIP = sb.getConfig("staticIP", "");
                if ((!staticIP.equals(""))) {
                    return staticIP;
                }
            }

            // If port forwarding was enabled we need to return the remote IP
            // Address
            if ((serverCore.portForwardingEnabled) && (serverCore.portForwarding != null)) {
                // does not return serverCore.portForwarding.getHost(), because
                // hostnames are not valid, except in DebugMode
                return InetAddress.getByName(
                        serverCore.portForwarding.getHost()).getHostAddress();
            }

            // otherwise we return the real IP address of this host
            InetAddress pLIP = myPublicLocalIP();
            if (pLIP != null) return pLIP.getHostAddress();
            return null;
        } catch (java.net.UnknownHostException e) {
            System.err.println("ERROR: (internal) " + e.getMessage());
            return null;
        }
    }

    public static InetAddress myPublicLocalIP() {
        try {
            String hostName;
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (java.net.UnknownHostException e) {
                hostName = "localhost"; // hopin' nothing serious happened only the hostname changed while running yacy
                System.err.println("ERROR: (internal) " + e.getMessage());
            }
            // list all addresses
            InetAddress[] ia = InetAddress.getAllByName(hostName);
            // for (int i = 0; i < ia.length; i++) System.out.println("IP: " +
            // ia[i].getHostAddress()); // DEBUG
            if (ia.length == 0) {
                try {
                    return InetAddress.getLocalHost();
                } catch (UnknownHostException e) {
                    try {
                        return InetAddress.getByName("127.0.0.1");
                    } catch (UnknownHostException ee) {
                        return null;
                    }
                }
            }
            if (ia.length == 1) {
                // only one network connection available
                return ia[0];
            }
            // we have more addresses, find an address that is not local
            int b0, b1;
            for (int i = 0; i < ia.length; i++) {
                b0 = 0Xff & ia[i].getAddress()[0];
                b1 = 0Xff & ia[i].getAddress()[1];
                if ((b0 != 10) && // class A reserved
                        (b0 != 127) && // loopback
                        ((b0 != 172) || (b1 < 16) || (b1 > 31)) && // class B reserved
                        ((b0 != 192) || (b1 != 168)) && // class C reserved
                        (ia[i].getHostAddress().indexOf(":") < 0))
                    return ia[i];
            }
            // there is only a local address, we filter out the possibly
            // returned loopback address 127.0.0.1
            for (int i = 0; i < ia.length; i++) {
                if (((0Xff & ia[i].getAddress()[0]) != 127) && (ia[i].getHostAddress().indexOf(":") < 0)) return ia[i];
            }
            // if all fails, give back whatever we have
            for (int i = 0; i < ia.length; i++) {
                if (ia[i].getHostAddress().indexOf(":") < 0) return ia[i];
            }
            return ia[0];
        } catch (java.net.UnknownHostException e) {
            System.err.println("ERROR: (internal) " + e.getMessage());
            return null;
        }
    }

}
