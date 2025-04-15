/*
 * Copyright (c) Pierre-Anthony Lemieux (pal@sandflow.com)
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
package com.sandflow.smpte.tools;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.util.logging.Logger;

import com.sandflow.smpte.register.LabelsRegister;
import com.sandflow.smpte.regxml.dict.MetaDictionary;
import com.sandflow.smpte.regxml.dict.MetaDictionaryCollection;
import com.sandflow.smpte.util.UL;

/**
 *
 * @author Pierre-Anthony Lemieux (pal@sandflow.com)
 */
public class GenerateHeaderMetadataClasses {

    private final static Logger LOG = Logger.getLogger(GenerateHeaderMetadataClasses.class.getName());

    private static final UL ESSENCE_DESCRIPTOR_KEY
        = new UL(new byte[]{0x06, 0x0e, 0x2b, 0x34, 0x02, 0x01, 0x01, 0x01, 0x0D, 0x01, 0x01, 0x01, 0x01, 0x01, 0x24, 0x00});

    private static final UL PREFACE_KEY
        = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.01012f00");

    protected final static String USAGE = "Dump header metadata of an MXF file as a RegXML structure.\n"
        + "  Usage:\n"
        + "     RegXMLDump ( -all | -ed ) ( -header | -footer | -auto ) (-l labelsregister) -d regxmldictionarydirorfile_1 ... regxmldictionarydirorfile_n -i mxffile\n"
        + "     RegXMLDump -?\n"
        + "  Where:\n"
        + "     -all: dumps all header metadata (default)\n"
        + "     -ed: dumps only the first essence descriptor found\n"
        + "     -l labelsregister: given a SMPTE labels register, inserts the symbol of labels as XML comment\n"
        + "     -header: dumps metadata from the header partition (default)\n"
        + "     -footer: dumps metadata from the footer partition\n"
        + "     -auto: dumps metadata from the footer partition if available and from the header if not\n";

    private enum TargetPartition {
        HEADER,
        FOOTER,
        AUTO
    }

    /**
     * Usage is specified at {@link #USAGE}
     */
    public static void main(String[] args) throws Exception {

        boolean error = false;
        MetaDictionaryCollection mds = null;
        FileReader labelreader = null;
        Path p = null;

        for (int i = 0; i < args.length;) {

            if ("-d".equals(args[i])) {

                if (mds != null) {
                    error = true;
                    break;
                }

                i++;

                mds = new MetaDictionaryCollection();

                for (; i < args.length && args[i].charAt(0) != '-'; i++) {

                    File mdf = new File(args[i]);

                    File mdfs[];

                    if (mdf.isDirectory()) {

                        mdfs = mdf.listFiles(
                            new FilenameFilter(){
                        
                                @Override
                                public boolean accept(File dir, String name) {
                                    return name.endsWith(".xml");
                                }
                            }
                        );

                    } else {

                        mdfs = new File[] {mdf};

                    }

                    for(int j = 0; j < mdfs.length; j++ ) {

                        /* load the regxml metadictionary */
                        FileReader fr = new FileReader(mdfs[j]);

                        /* add it to the dictionary group */
                        mds.addDictionary(MetaDictionary.fromXML(fr));

                    }

                }

                if (mds.getDictionaries().isEmpty()) {
                    error = true;
                    break;
                }

            } else if ("-l".equals(args[i])) {

                if (labelreader != null) {
                    error = true;
                    break;
                }

                i++;

                labelreader = new FileReader(args[i]);

                i++;

            } else {

                error = true;
                break;

            }

        }


        if (error || mds == null || p == null) {
            System.out.println(USAGE);
            return;
        }

        /* create an enum name resolver, if available */
        final LabelsRegister lr;

        if (labelreader != null) {

            lr = LabelsRegister.fromXML(labelreader);

        } else {

            lr = null;

        }

        /*                     LabelsRegister.Entry e = lr.getEntryByUL(enumid.asUL());

                    return e == null ? null : e.getSymbol(); */
       

    }
}
