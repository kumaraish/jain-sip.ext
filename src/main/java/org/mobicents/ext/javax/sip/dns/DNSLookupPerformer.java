/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.ext.javax.sip.dns;

import gov.nist.javax.sip.stack.HopImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.sip.ListeningPoint;
import javax.sip.address.Hop;

import org.apache.log4j.Logger;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.NAPTRRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class DNSLookupPerformer {
	private static final Logger logger = Logger.getLogger(DNSLookupPerformer.class);
	
	public static final String SERVICE_SIPS = "SIPS";
	public static final String SERVICE_D2U = "D2U";
	public static final String SERVICE_D2T = "D2T";
	
	
	public List<Record> performSRVLookup(Name replacement) {
		Record[] srvRecords = (Record[]) new Lookup(replacement, Type.SRV).run();
		if(srvRecords != null && srvRecords.length > 0) {
			return Arrays.asList(srvRecords);	
		}
		return new ArrayList<Record>(0);
	}
	
	public List<NAPTRRecord> performNAPTRLookup(String domain, boolean isSecure, Set<String> supportedTransports) {
		List<NAPTRRecord> records = new ArrayList<NAPTRRecord>();
		
		try {
			Record[] naptrRecords = new Lookup(domain, Type.NAPTR).run();
			if(naptrRecords != null) {
				for (NAPTRRecord record : (NAPTRRecord[]) naptrRecords) {
					if(isSecure) {
						// First, a client resolving a SIPS URI MUST discard any services that
						// do not contain "SIPS" as the protocol in the service field.
						if(record.getService().startsWith(SERVICE_SIPS)) {
							records.add(record);
						}
					} else {	
						// The converse is not true, however.
						if(!record.getService().startsWith(SERVICE_SIPS) || 
								(record.getService().startsWith(SERVICE_SIPS) && supportedTransports.contains(ListeningPoint.TLS))) {
							//A client resolving a SIP URI SHOULD retain records with "SIPS" as the protocol, if the client supports TLS
							if((record.getService().contains(SERVICE_D2U) && supportedTransports.contains(ListeningPoint.UDP)) ||
									record.getService().contains(SERVICE_D2T) && (supportedTransports.contains(ListeningPoint.TCP) || supportedTransports.contains(ListeningPoint.TLS))) {
								// Second, a client MUST discard any service fields that identify
								// a resolution service whose value is not "D2X", for values of X that
								// indicate transport protocols supported by the client.
								records.add(record);
							}
						}
					}
				}
			}
		} catch (TextParseException e) {
			logger.warn("Couldn't parse domain " + domain, e);
		}
		if(records.size() > 0) {
			java.util.Collections.sort(records, new NAPTRRecordComparator());
		}
		return records;
	}

	/**
	 * 
	 * @param host
	 * @param port
	 * @param transport
	 * @return
	 */
	public Queue<Hop> locateHopsForNonNumericAddressWithPort(String host, int port, String transport) {
		Queue<Hop> priorityQueue = new LinkedList<Hop>();
		
		try {
			Record[] aRecords = new Lookup(host, Type.A).run();
			if(aRecords != null && aRecords.length > 0) {
				for(ARecord aRecord : (ARecord[]) aRecords) {
					priorityQueue.add(new HopImpl(aRecord.getAddress().getHostAddress(), port, transport));
				}
			}	
		} catch (TextParseException e) {
			logger.warn("Couldn't parse domain " + host, e);
		}	
		try {
			final Record[] aaaaRecords = new Lookup(host, Type.AAAA).run();
			if(aaaaRecords != null && aaaaRecords.length > 0) {
				for(AAAARecord aaaaRecord : (AAAARecord[]) aaaaRecords) {
					priorityQueue.add(new HopImpl(aaaaRecord.getAddress().getHostAddress(), port, transport));
				}
			}			
		} catch (TextParseException e) {
			logger.warn("Couldn't parse domain " + host, e);
		}	
		return priorityQueue;
	}
}
