/*
* Copyright (c), Sandflow Consulting LLC
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

/*
 * Dumps the header metadata of an MXF file as a RegXML fragment.
 *
 */

#include <com/sandflow/smpte/regxml/dict/MetaDictionaryCollection.h>
#include <com/sandflow/smpte/regxml/dict/importers/XMLImporter.h>
#include <com/sandflow/smpte/regxml/MXFFragmentBuilder.h>
#include <com/sandflow/util/DOMHelper.h>
#include <com/sandflow/util/events/EventHandler.h>
#include <xercesc/util/PlatformUtils.hpp>
#include <xercesc/sax/ErrorHandler.hpp>
#include <xercesc/sax/SAXParseException.hpp>
#include <xercesc/parsers/XercesDOMParser.hpp>
#include <xercesc/framework/StdOutFormatTarget.hpp>
#include <xercesc/dom/DOM.hpp>

#include <algorithm>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

using namespace rxml;

static const char USAGE[] =
	"Dump header metadata of an MXF file as a RegXML structure.\n"
	"  Usage:\n"
	"     RegXMLDump -d regxmldictionarydirorfile_1 ... regxmldictionarydirorfile_n -i mxffile\n"
	"     RegXMLDump -?\n"
	"  Where:\n"
	"     -d: one or more RegXML metadictionary files, or directories containing them (*.xml)\n"
	"     -i: MXF file whose header partition metadata is dumped to stdout\n";

class StderrErrorHandler : public xercesc::ErrorHandler {
public:
	virtual void warning(const xercesc::SAXParseException &exc) {
		std::cerr << "XML warning: " << DOMHelper::toUTF8(exc.getMessage()) << std::endl;
	}

	virtual void error(const xercesc::SAXParseException &exc) {
		std::cerr << "XML error: " << DOMHelper::toUTF8(exc.getMessage()) << std::endl;
	}

	virtual void fatalError(const xercesc::SAXParseException &exc) {
		std::cerr << "XML fatal error: " << DOMHelper::toUTF8(exc.getMessage()) << std::endl;
	}

	virtual void resetErrors() {}
};

class StderrEventHandler : public EventHandler {
public:
	bool hasErrors = false;

	virtual bool info(const std::string &code, const std::string &reason, const std::string &where) {
		return log("INFO", code, reason, where);
	}

	virtual bool warn(const std::string &code, const std::string &reason, const std::string &where) {
		return log("WARN", code, reason, where);
	}

	virtual bool error(const std::string &code, const std::string &reason, const std::string &where) {
		hasErrors = true;
		return log("ERROR", code, reason, where);
	}

	virtual bool fatal(const std::string &code, const std::string &reason, const std::string &where) {
		hasErrors = true;
		return log("FATAL", code, reason, where);
	}

private:
	bool log(const char *level, const std::string &code, const std::string &reason, const std::string &where) {
		std::cerr << level << " " << code << ": " << reason << " at " << where << std::endl;
		return true;
	}
};

/* expands a -d argument into the list of dictionary files it designates */
static bool collectDictionaryPaths(const std::string &arg, std::vector<std::string> &paths) {
	namespace fs = std::filesystem;

	std::error_code ec;

	if (fs::is_directory(arg, ec)) {
		std::vector<std::string> dirpaths;

		for (const fs::directory_entry &e : fs::directory_iterator(arg, ec)) {
			if (e.is_regular_file() && e.path().extension() == ".xml") {
				dirpaths.push_back(e.path().string());
			}
		}

		if (ec) {
			std::cerr << "Cannot read dictionary directory: " << arg << std::endl;
			return false;
		}

		/* deterministic load order */
		std::sort(dirpaths.begin(), dirpaths.end());
		paths.insert(paths.end(), dirpaths.begin(), dirpaths.end());

	} else if (fs::is_regular_file(arg, ec)) {
		paths.push_back(arg);

	} else {
		std::cerr << "Dictionary file or directory not found: " << arg << std::endl;
		return false;
	}

	return true;
}

static int run(const std::vector<std::string> &dictpaths, const std::string &mxfpath) {
	StderrErrorHandler xmlerrhandler;
	StderrEventHandler evthandler;

	MetaDictionaryCollection mds;
	std::vector<MetaDictionary*> dicts;

	int ret_val = 0;

	/* load dictionaries */

	{
		xercesc::XercesDOMParser parser;
		parser.setErrorHandler(&xmlerrhandler);
		parser.setDoNamespaces(true);

		for (const std::string &path : dictpaths) {
			parser.parse(path.c_str());

			xercesc::DOMDocument *doc = parser.getDocument();

			if (parser.getErrorCount() != 0 || doc == NULL || doc->getDocumentElement() == NULL) {
				std::cerr << "Cannot parse dictionary: " << path << std::endl;
				ret_val = 1;
				break;
			}

			MetaDictionary *md = new MetaDictionary();
			dicts.push_back(md);

			XMLImporter::fromDOM(*doc, *md, &evthandler);

			mds.addDictionary(md);
		}
	}

	if (ret_val == 0 && dicts.empty()) {
		std::cerr << "No dictionaries found" << std::endl;
		ret_val = 1;
	}

	/* dump the MXF file */

	if (ret_val == 0) {
		std::ifstream f(mxfpath.c_str(), std::ifstream::in | std::ifstream::binary);

		if (!f.good()) {
			std::cerr << "Cannot read input file: " << mxfpath << std::endl;
			ret_val = 1;

		} else {
			XMLCh ls[3] = { xercesc::chLatin_L, xercesc::chLatin_S, xercesc::chNull };
			xercesc::DOMImplementation *impl = xercesc::DOMImplementationRegistry::getDOMImplementation(ls);
			xercesc::DOMDocument *doc = impl->createDocument();

			xercesc::DOMDocumentFragment *frag =
				MXFFragmentBuilder::fromInputStream(f, mds, NULL, NULL, *doc, &evthandler);

			if (frag == NULL) {
				std::cerr << "Cannot build RegXML fragment from: " << mxfpath << std::endl;
				ret_val = 1;

			} else {
				doc->appendChild(frag);

				xercesc::DOMLSSerializer *ser = ((xercesc::DOMImplementationLS*)impl)->createLSSerializer();
				xercesc::DOMLSOutput *output = ((xercesc::DOMImplementationLS*)impl)->createLSOutput();
				xercesc::StdOutFormatTarget ft;

				if (ser->getDomConfig()->canSetParameter(xercesc::XMLUni::fgDOMWRTFormatPrettyPrint, true))
					ser->getDomConfig()->setParameter(xercesc::XMLUni::fgDOMWRTFormatPrettyPrint, true);

				output->setByteStream(&ft);

				if (!ser->write(doc, output)) {
					std::cerr << "Cannot serialize RegXML fragment" << std::endl;
					ret_val = 1;
				}

				output->release();
				ser->release();
			}

			doc->release();
		}
	}

	for (MetaDictionary *md : dicts) {
		delete md;
	}

	if (ret_val == 0 && evthandler.hasErrors) {
		ret_val = 1;
	}

	return ret_val;
}

int main(int argc, char **argv) {
	std::vector<std::string> dictpaths;
	std::string mxfpath;
	bool hasDicts = false;
	bool error = false;

	for (int i = 1; i < argc && !error;) {
		std::string arg = argv[i];

		if (arg == "-?") {
			std::cout << USAGE;
			return 0;

		} else if (arg == "-d") {
			if (hasDicts) {
				error = true;
				break;
			}

			hasDicts = true;

			for (i++; i < argc && argv[i][0] != '-'; i++) {
				if (!collectDictionaryPaths(argv[i], dictpaths)) {
					return 1;
				}
			}

			if (dictpaths.empty()) {
				error = true;
			}

		} else if (arg == "-i") {
			if (!mxfpath.empty() || i + 1 >= argc || argv[i + 1][0] == '-') {
				error = true;
				break;
			}

			mxfpath = argv[i + 1];
			i += 2;

		} else {
			error = true;
		}
	}

	if (error || dictpaths.empty() || mxfpath.empty()) {
		std::cerr << USAGE;
		return 1;
	}

	int ret_val;

	try {
		xercesc::XMLPlatformUtils::Initialize();
	} catch (const xercesc::XMLException &e) {
		std::cerr << "Cannot initialize Xerces-C: " << DOMHelper::toUTF8(e.getMessage()) << std::endl;
		return 1;
	}

	try {
		ret_val = run(dictpaths, mxfpath);
	} catch (const std::exception &e) {
		std::cerr << "Unexpected error: " << e.what() << std::endl;
		ret_val = 1;
	}

	xercesc::XMLPlatformUtils::Terminate();

	return ret_val;
}
