/*******************************************************************************
 * Copyright (c) 2000, 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.core.internal.resources;

import org.eclipse.core.resources.*;
import java.util.*;
import java.io.IOException;
import java.io.DataOutputStream;
//
public class MarkerWriter {
	
	protected MarkerManager manager;
	
	// version numbers
	public static final int MARKERS_SAVE_VERSION = 2;
	public static final int MARKERS_SNAP_VERSION = 1;

	// type constants
	public static final byte INDEX = 1;
	public static final byte QNAME = 2;

	// marker attribute types
	public static final byte ATTRIBUTE_NULL = 0;
	public static final byte ATTRIBUTE_BOOLEAN = 1;
	public static final byte ATTRIBUTE_INTEGER = 2;
	public static final byte ATTRIBUTE_STRING = 3;

public MarkerWriter(MarkerManager manager) {
	super();
	this.manager = manager;
}
/**
 * Returns an Object array of length 2. The first element is an Integer which is the number 
 * of persistent markers found. The second element is an array of boolean values, with a 
 * value of true meaning that the marker at that index is to be persisted.
 */
private Object[] filterMarkers(IMarkerSetElement[] markers) {
	Object[] result = new Object[2];
	boolean[] isPersistent = new boolean[markers.length];
	int count = 0;
	MarkerTypeDefinitionCache cache = manager.getCache();
	for (int i = 0; i < markers.length; i++) {
		MarkerInfo info = (MarkerInfo) markers[i];
		if (cache.isPersistent(info.getType())) {
			isPersistent[i] = true;
			count++;
		}
	}
	result[0] = new Integer(count);
	result[1] = isPersistent;
	return result;
}
/**
 * SAVE_FILE -> VERSION_ID RESOURCE+
 * VERSION_ID -> int
 * RESOURCE -> RESOURCE_PATH MARKERS_SIZE MARKER+
 * RESOURCE_PATH -> String
 * MARKERS_SIZE -> int
 * MARKER -> MARKER_ID TYPE ATTRIBUTES_SIZE ATTRIBUTE*
 * MARKER_ID -> long
 * TYPE -> INDEX | QNAME
 * INDEX -> byte int
 * QNAME -> byte String
 * ATTRIBUTES_SIZE -> short
 * ATTRIBUTE -> ATTRIBUTE_KEY ATTRIBUTE_VALUE
 * ATTRIBUTE_KEY -> String
 * ATTRIBUTE_VALUE -> INTEGER_VALUE | BOOLEAN_VALUE | STRING_VALUE | NULL_VALUE
 * INTEGER_VALUE -> byte int
 * BOOLEAN_VALUE -> byte boolean
 * STRING_VALUE -> byte String
 * NULL_VALUE -> byte
 * 	
 */
public void save(IResource resource, DataOutputStream output, List writtenTypes) throws IOException {
	ResourceInfo info = ((Resource) resource).getResourceInfo(false, false);
	if (info == null)
		return;
	MarkerSet markers = info.getMarkers();
	if (markers == null)
		return;
	IMarkerSetElement[] elements = markers.elements();
	// filter out the markers...determine if there are any persistent ones
	Object[] result = filterMarkers(elements);
	int count = ((Integer) result[0]).intValue();
	if (count == 0)
		return;
	// if this is the first set of markers that we have written, then
	// write the version id for the file.
	if (output.size() == 0)
		output.writeInt(MARKERS_SAVE_VERSION);
	boolean[] isPersistent = (boolean[]) result[1];
	output.writeUTF(resource.getFullPath().toString());
	output.writeInt(count);
	for (int i = 0; i < elements.length; i++)
		if (isPersistent[i])
			write((MarkerInfo) elements[i], output, writtenTypes);
}
/**
 * Snapshot the markers for the specified resource to the given output stream.
 * 
 * SNAP_FILE -> VERSION_ID RESOURCE*
 * VERSION_ID -> int (used for backwards compatibiliy)
 * RESOURCE -> RESOURCE_PATH MARKER_SIZE MARKER+
 * RESOURCE_PATH -> String
 * MARKER_SIZE -> int
 * MARKER -> MARKER_ID TYPE ATTRIBUTES_SIZE ATTRIBUTE*
 * MARKER_ID -> long
 * TYPE -> INDEX | QNAME
 * INDEX -> byte int
 * QNAME -> byte String
 * ATTRIBUTES_SIZE -> short
 * ATTRIBUTE -> ATTRIBUTE_KEY ATTRIBUTE_VALUE
 * ATTRIBUTE_KEY -> String
 * ATTRIBUTE_VALUE -> BOOLEAN_VALUE | INTEGER_VALUE | STRING_VALUE | NULL_VALUE
 * BOOLEAN_VALUE -> byte boolean
 * INTEGER_VALUE -> byte int
 * STRING_VALUE -> byte String
 * NULL_VALUE -> byte
 */
public void snap(IResource resource, DataOutputStream output) throws IOException {
	ResourceInfo info = ((Resource) resource).getResourceInfo(false, false);
	if (info == null)
		return;
	if (!info.isSet(ICoreConstants.M_MARKERS_SNAP_DIRTY))
		return;
	MarkerSet markers = info.getMarkers();
	if (markers == null)
		return;
	IMarkerSetElement[] elements = markers.elements();
	// filter out the markers...determine if there are any persistent ones
	Object[] result = filterMarkers(elements);
	int count = ((Integer) result[0]).intValue();
	// write the version id for the snapshot.
	output.writeInt(MARKERS_SNAP_VERSION);
	boolean[] isPersistent = (boolean[]) result[1];
	output.writeUTF(resource.getFullPath().toString());
	// always write out the count...even if its zero. this will help
	// use pick up marker deletions from our snapshot.
	output.writeInt(count);
	List writtenTypes = new ArrayList();
	for (int i = 0; i < elements.length; i++)
		if (isPersistent[i])
			write((MarkerInfo) elements[i], output, writtenTypes);
	info.clear(ICoreConstants.M_MARKERS_SNAP_DIRTY);
}
/* 
 * Write out the given marker attributes to the given output stream.
 */
private void write(Map attributes, DataOutputStream output) throws IOException {
	output.writeShort(attributes.size());
	for (Iterator i = attributes.keySet().iterator(); i.hasNext();) {
		String key = (String) i.next();
		output.writeUTF(key);
		Object value = attributes.get(key);
		if (value instanceof Integer) {
			output.writeByte(ATTRIBUTE_INTEGER);
			output.writeInt(((Integer) value).intValue());
			continue;
		}
		if (value instanceof Boolean) {
			output.writeByte(ATTRIBUTE_BOOLEAN);
			output.writeBoolean(((Boolean) value).booleanValue());
			continue;
		}
		if (value instanceof String) {
			output.writeByte(ATTRIBUTE_STRING);
			output.writeUTF((String) value);
			continue;
		}
		// otherwise we came across an attribute of an unknown type
		// so just write out null since we don't know how to marshal it.
		output.writeByte(ATTRIBUTE_NULL);
	}
}
private void write(MarkerInfo info, DataOutputStream output, List writtenTypes) throws IOException {
	output.writeLong(info.getId());
	// if we have already written the type once, then write an integer
	// constant to represent it instead to remove duplication
	String type = info.getType();
	int index = writtenTypes.indexOf(type);
	if (index == -1) {
		output.writeByte(QNAME);
		output.writeUTF(type);
		writtenTypes.add(type);
	} else {
		output.writeByte(INDEX);
		output.writeInt(index);
	}
	if (info.getAttributes(false) == null) {
		output.writeShort(0);
	} else
		write(info.getAttributes(false), output);
}
}
