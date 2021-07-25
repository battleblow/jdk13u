/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

package sun.jvm.hotspot.runtime.bsd_ppc64;

import java.io.*;
import java.util.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.ppc64.*;
import sun.jvm.hotspot.debugger.bsd.BsdDebugger;
import sun.jvm.hotspot.debugger.bsd.BsdDebuggerLocal;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.runtime.ppc64.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class BsdPPC64JavaThreadPDAccess implements JavaThreadPDAccess {
  private static AddressField  osThreadField;

  // Fields from OSThread
  private static CIntegerField osThreadThreadIDField;
  private static CIntegerField osThreadUniqueThreadIDField;

  // This is currently unneeded but is being kept in case we change
  // the currentFrameGuess algorithm
  private static final long GUESS_SCAN_RANGE = 128 * 1024;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("JavaThread");
    osThreadField = type.getAddressField("_osthread");

    Type osThreadType = db.lookupType("OSThread");
    osThreadThreadIDField = osThreadType.getCIntegerField("_thread_id");
    osThreadUniqueThreadIDField = osThreadType.getCIntegerField("_unique_thread_id");
  }

  public Address getLastJavaFP(Address addr) {
    return null;
  }

  public Address getLastJavaPC(Address addr) {
    return null;
  }

  public Address getBaseOfStackPointer(Address addr) {
    return null;
  }

  public Frame getLastFramePD(JavaThread thread, Address addr) {
    Address fp = thread.getLastJavaFP();
    if (fp == null) {
      return null; // no information
    }
    return new PPC64Frame(thread.getLastJavaSP(), fp);
  }

  public RegisterMap newRegisterMap(JavaThread thread, boolean updateMap) {
    return new PPC64RegisterMap(thread, updateMap);
  }

  public Frame getCurrentFrameGuess(JavaThread thread, Address addr) {
    ThreadProxy t = getThreadProxy(addr);
    PPC64ThreadContext context = (PPC64ThreadContext) t.getContext();
    PPC64CurrentFrameGuess guesser = new PPC64CurrentFrameGuess(context, thread);
    if (!guesser.run(GUESS_SCAN_RANGE)) {
      return null;
    }
    if (guesser.getPC() == null) {
      return new PPC64Frame(guesser.getSP(), guesser.getFP());
    } else {
      return new PPC64Frame(guesser.getSP(), guesser.getFP(), guesser.getPC());
    }
  }

  public void printThreadIDOn(Address addr, PrintStream tty) {
    tty.print(getThreadProxy(addr));
  }

  public void printInfoOn(Address threadAddr, PrintStream tty) {
    tty.print("Thread id: ");
    printThreadIDOn(threadAddr, tty);
    // tty.println("\nPostJavaState: " + getPostJavaState(threadAddr));
  }

  public Address getLastSP(Address addr) {
    ThreadProxy t = getThreadProxy(addr);
    PPC64ThreadContext context = (PPC64ThreadContext) t.getContext();
    return context.getRegisterAsAddress(PPC64ThreadContext.SP);
  }

  public Address getLastFP(Address addr) {
    return getLastSP(addr).getAddressAt(0);
  }

  public ThreadProxy getThreadProxy(Address addr) {
    // Addr is the address of the JavaThread.
    // Fetch the OSThread (for now and for simplicity, not making a
    // separate "OSThread" class in this package)
    Address osThreadAddr = osThreadField.getValue(addr);
    // Get the address of the _thread_id from the OSThread
    Address threadIdAddr = osThreadAddr.addOffsetTo(osThreadThreadIDField.getOffset());
    Address uniqueThreadIdAddr = osThreadAddr.addOffsetTo(osThreadUniqueThreadIDField.getOffset());

    JVMDebugger debugger = VM.getVM().getDebugger();
    // If this is BsdDebuggerLocal, use its getThreadForIdentifierAddress
    // method that allows the use of two thread ids
    if (debugger instanceof BsdDebuggerLocal) {
      return ((BsdDebuggerLocal) debugger).getThreadForIdentifierAddress(
        threadIdAddr, uniqueThreadIdAddr);
    }

    // Otherwise fall back to the standard method which only supports
    // one thread id
    return debugger.getThreadForIdentifierAddress(threadIdAddr);
  }
}
