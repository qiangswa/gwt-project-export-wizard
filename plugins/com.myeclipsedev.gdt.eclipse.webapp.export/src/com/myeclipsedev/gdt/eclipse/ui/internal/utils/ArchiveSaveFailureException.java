/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Q.S Wang (qiangswa@google.com) - copied from WTP project and modified.
 *******************************************************************************/

package com.myeclipsedev.gdt.eclipse.ui.internal.utils;

public class ArchiveSaveFailureException extends Exception {
	private static final long serialVersionUID = 1L;

	public ArchiveSaveFailureException(String message) {
		super(message);
	}

	public ArchiveSaveFailureException(Throwable cause) {
		super(cause);
	}

	public ArchiveSaveFailureException(String message, Throwable cause) {
		super(message, cause);
	}
}
