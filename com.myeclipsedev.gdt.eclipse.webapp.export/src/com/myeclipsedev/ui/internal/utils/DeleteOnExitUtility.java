/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Q.S Wang (wangqs_eclipse@yahoo.com) - copied from WTP project and modified.
 *******************************************************************************/
package com.myeclipsedev.ui.internal.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DeleteOnExitUtility {
	private static Set<File> tempFilesToDelete;
	private static boolean initialized = false;
	private static int cleanupCount = 0;
	private static final int CLEANUP_THRESHOLD = 10000;

	public static void markForDeletion(File file) {
		if (!initialized) {
			initialized = true;
			try {
				Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
					public void run() {
						if (tempFilesToDelete != null) {
							synchronized (tempFilesToDelete) {
								Iterator<File> iterator = tempFilesToDelete
										.iterator();
								File file = null;
								while (iterator.hasNext()) {
									try {
										file = (File) iterator.next();
										if (file.exists()) {
											file.delete();
										}
									} catch (SecurityException e) {
									}
								}
							}
						}
					}
				}));
				tempFilesToDelete = new HashSet<File>();
			} catch (SecurityException e) {
			}
		}
		if (tempFilesToDelete != null) {
			synchronized (tempFilesToDelete) {
				cleanupCount++;
				tempFilesToDelete.add(file);
			}
			if (cleanupCount > CLEANUP_THRESHOLD) {
				runCleanup();
			}
		}
	}

	public static void fileHasBeenDeleted(File file) {
		if (tempFilesToDelete != null) {
			synchronized (tempFilesToDelete) {
				cleanupCount++;
				try {
					if (!file.exists()) {
						tempFilesToDelete.remove(file);
					}
				} catch (SecurityException e) {
				}
			}
			if (cleanupCount > CLEANUP_THRESHOLD) {
				runCleanup();
			}
		}
	}

	public static void runCleanup() {
		if (tempFilesToDelete != null) {
			synchronized (tempFilesToDelete) {
				cleanupCount = 0;
				List<File> filesToRemove = null;
				Iterator<File> iterator = tempFilesToDelete.iterator();
				File file = null;
				while (iterator.hasNext()) {
					try {
						file = (File) iterator.next();
						if (!file.exists()) {
							if (filesToRemove == null) {
								filesToRemove = new ArrayList<File>();
							}
							filesToRemove.add(file);
						}
					} catch (SecurityException e) {
					}
				}
				if (filesToRemove != null) {
					tempFilesToDelete.removeAll(filesToRemove);
				}
			}
		}
	}
}
