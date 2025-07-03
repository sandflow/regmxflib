/*
 * Copyright (c) Sandflow Consulting, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/**
* @author Pierre-Anthony Lemieux
*/

package com.sandflow.smpte.mxf;

import com.sandflow.util.events.BasicEvent;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;

/**
 * All events raised by this class are instance of this class
 */
public class MXFEvent extends BasicEvent {

  /**
   * Defines all events raised by this class
   */
  public static enum EventCodes {

    /**
     * No root object found
     */
    MISSING_ROOT_OBJECT(Event.Severity.FATAL),
    /**
     * No partition pack found in the MXF file
     */
    MISSING_PARTITION_PACK(Event.Severity.FATAL),
    /**
     * No primer pack found in the MXF file
     */
    MISSING_PRIMER_PACK(Event.Severity.FATAL),
    /**
     * Unexpected group sequence encountered
     */
    UNEXPECTED_STRUCTURE(Event.Severity.ERROR),
    /**
     * Failed to read Group
     */
    GROUP_READ_FAILED(Event.Severity.ERROR),
    /**
     * Inconsistent header metadata length
     */
    INCONSISTENT_HEADER_LENGTH(Event.Severity.FATAL);

    public final Event.Severity severity;

    private EventCodes(Event.Severity severity) {
      this.severity = severity;
    }

  }

  public MXFEvent(MXFEvent.EventCodes kind, String message) {
    super(kind.severity, kind, message);
  }

  static void handle(EventHandler handler, com.sandflow.util.events.Event evt) throws MXFException {
    if (handler != null) {
      if (!handler.handle(evt) ||
          evt.getSeverity() == MXFEvent.Severity.FATAL) {
        /* die on FATAL events or if requested by the handler */
        throw new MXFException(evt.getMessage());

      }
    } else if (evt.getSeverity() == MXFEvent.Severity.ERROR ||
        evt.getSeverity() == MXFEvent.Severity.FATAL) {
      /* if no event handler was provided, die on FATAL and ERROR events */
      throw new MXFException(evt.getMessage());
    }
  }

}