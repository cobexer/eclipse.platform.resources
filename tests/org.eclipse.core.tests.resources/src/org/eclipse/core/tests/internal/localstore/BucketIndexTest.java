/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.internal.localstore;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.internal.localstore.BucketIndex;
import org.eclipse.core.internal.utils.UniversalUniqueIdentifier;
import org.eclipse.core.runtime.*;
import org.eclipse.core.tests.resources.ResourceTest;

public class BucketIndexTest extends ResourceTest {

	public static Test suite() {
		return new TestSuite(BucketIndexTest.class);
	}

	public BucketIndexTest(String name) {
		super(name);
	}

	public void testSort() {
		IPath baseLocation = getRandomLocation();
		try {
			BucketIndex index = new BucketIndex(baseLocation.toFile());
			IPath path = new Path("/foo");
			assertNull("1.0", index.getEntry(path));
			UniversalUniqueIdentifier uuid1 = new UniversalUniqueIdentifier();
			long timestamp1 = 10;
			index.addBlob(path, uuid1, timestamp1);
			BucketIndex.Entry entry = index.getEntry(path);
			assertNotNull("2.0", entry);
			assertEquals("2.1", 1, entry.getOccurrences());
			assertEquals("2.2", uuid1, entry.getUUID(0));
			assertEquals("2.3", timestamp1, entry.getTimestamp(0));
			// adds a new state with a more recent timestamp
			UniversalUniqueIdentifier uuid2 = new UniversalUniqueIdentifier();
			long timestamp2 = timestamp1 + 1;
			index.addBlob(path, uuid2, timestamp2);
			entry = index.getEntry(path);
			assertNotNull("3.0", entry);
			// since it is newer, should appear first
			assertEquals("3.1", 2, entry.getOccurrences());
			assertEquals("3.2", uuid2, entry.getUUID(0));
			assertEquals("3.3", timestamp2, entry.getTimestamp(0));
			assertEquals("3.4", uuid1, entry.getUUID(1));
			assertEquals("3.5", timestamp1, entry.getTimestamp(1));
			// adds a 3rd state, with the same timestamp as the 1st 
			UniversalUniqueIdentifier uuid3 = new UniversalUniqueIdentifier();
			long timestamp3 = timestamp1;
			index.addBlob(path, uuid3, timestamp3);
			entry = index.getEntry(path);
			assertNotNull("4.0", entry);
			// its UUID was created later so it will be considered more recent
			// even if it has the same timestamp
			assertEquals("4.1", 3, entry.getOccurrences());
			assertEquals("4.2", uuid2, entry.getUUID(0));
			assertEquals("4.3", timestamp2, entry.getTimestamp(0));
			assertEquals("4.4", uuid3, entry.getUUID(1));
			assertEquals("4.5", timestamp3, entry.getTimestamp(1));
			assertEquals("4.6", uuid1, entry.getUUID(2));
			assertEquals("4.7", timestamp1, entry.getTimestamp(2));
		} finally {
			ensureDoesNotExistInFileSystem(baseLocation.toFile());
		}
	}

	/**
	 * Ensures that if another entry having exactly the same UUID is added,
	 * the original one is not replaced.
	 */
	public void testDuplicates() {
		IPath baseLocation = getRandomLocation();
		try {
			BucketIndex index1 = new BucketIndex(baseLocation.toFile());
			IPath location1 = baseLocation.append("location1");
			try {
				index1.load(location1.toFile());
			} catch (CoreException e) {
				fail("1.0", e);
			}
			IPath path = new Path("/foo/bar");
			UniversalUniqueIdentifier uuid = new UniversalUniqueIdentifier();
			long lastModified = (long) (Math.random() * Long.MAX_VALUE);
			index1.addBlob(path, uuid, lastModified);
			assertEquals("2.0", 1, index1.getEntryCount());
			index1.addBlob(path, uuid, lastModified);
			assertEquals("3.0", 1, index1.getEntryCount());
			BucketIndex.Entry entry = index1.getEntry(path);
			assertNotNull("3.1", entry);
			assertEquals("3.2", path, entry.getPath());
			assertEquals("3.3", 1, entry.getOccurrences());
			assertEquals("3.4", uuid, entry.getUUID(0));
			assertEquals("3.5", lastModified, entry.getTimestamp(0));
		} finally {
			ensureDoesNotExistInFileSystem(baseLocation.toFile());
		}
	}

	public void testPersistence() {
		IPath baseLocation = getRandomLocation();
		try {
			BucketIndex index1 = new BucketIndex(baseLocation.toFile());
			IPath location = baseLocation.append("location");
			try {
				index1.load(location.toFile());
			} catch (CoreException e) {
				fail("1.0", e);
			}
			assertEquals("1.1", 0, index1.getEntryCount());
			IPath path = new Path("/foo/bar");
			UniversalUniqueIdentifier uuid1 = new UniversalUniqueIdentifier();
			long lastModified1 = (long) (Math.random() * Long.MAX_VALUE);
			index1.addBlob(path, uuid1, lastModified1);
			assertEquals("2.0", 1, index1.getEntryCount());
			try {
				index1.save();
			} catch (CoreException e) {
				fail("2.1", e);
			}
			BucketIndex index2 = new BucketIndex(baseLocation.toFile());
			try {
				index2.load(location.toFile(), false);
			} catch (CoreException e) {
				fail("3.0", e);
			}
			assertEquals("3.1", 1, index1.getEntryCount());
			BucketIndex.Entry entry = index1.getEntry(path);
			assertNotNull("3.2", entry);
			assertEquals("3.3", path, entry.getPath());
			assertEquals("3.4", 1, entry.getOccurrences());
			assertEquals("3.5", uuid1, entry.getUUID(0));
			assertEquals("3.6", lastModified1, entry.getTimestamp(0));
			UniversalUniqueIdentifier uuid2 = new UniversalUniqueIdentifier();
			long lastModified2 = (long) Math.abs((Math.random() * Long.MAX_VALUE));
			index2.addBlob(path, uuid2, lastModified2);
			try {
				index2.save();
			} catch (CoreException e) {
				fail("4.0", e);
			}
			try {
				index1.load(location.toFile(), true);
			} catch (CoreException e) {
				fail("4.1", e);
			}
			assertEquals("4.2", 1, index1.getEntryCount());
			entry = index1.getEntry(path);
			assertNotNull("4.3", entry);
			assertEquals("4.4", path, entry.getPath());
			assertEquals("4.5", 2, entry.getOccurrences());

			// test deletion
			try {
				index1.accept(new BucketIndex.Visitor() {
					public int visit(BucketIndex.Entry fileEntry) {
						fileEntry.delete();
						return CONTINUE;
					}
				}, path, true);
			} catch (CoreException e) {
				fail("5.0", e);
			}
			entry = index1.getEntry(path);
			assertNull("5.1", entry);
			try {
				index2.load(location.toFile(), true);
			} catch (CoreException e) {
				fail("5.2", e);
			}
			entry = index2.getEntry(path);
			assertNull("5.3", entry);
		} finally {
			ensureDoesNotExistInFileSystem(baseLocation.toFile());
		}
	}
}
