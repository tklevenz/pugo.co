/*
	The MIT License (MIT)

	Copyright (c) 2016 Tobias Klevenz (tobias.klevenz@gmail.com)

	Permission is hereby granted, free of charge, to any person obtaining a copy
	of this software and associated documentation files (the "Software"), to deal
	in the Software without restriction, including without limitation the rights
	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
	copies of the Software, and to permit persons to whom the Software is
	furnished to do so, subject to the following conditions:

	The above copyright notice and this permission notice shall be included in all
	copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
	SOFTWARE.
*/

package co.pugo.convert;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;

/**
 * class to store output configuration
 */
class Configuration {
	// xml tags in config file
	private static final String CONFIG_XSL_TAG = "xsl";
	private static final String CONFIG_MIMETYPE_TAG = "mimeType";
	private static final String CONFIG_ZIP_OUTPUT_TAG = "zipOutput";
	private static final String CONFIG_PROCEESS_IMAGES_TAG = "processImages";

	private String xsl;
	private String mimeType;
	private boolean zipOutput;
	private boolean processImages;

	Configuration(InputStream configFile) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
			Document document = documentBuilder.parse(configFile);
			xsl = getConfigElementTextContent(document, CONFIG_XSL_TAG);
			mimeType = getConfigElementTextContent(document, CONFIG_MIMETYPE_TAG);
			zipOutput = getConfigElementTextContent(document, CONFIG_ZIP_OUTPUT_TAG).equals("true");
			processImages = getConfigElementTextContent(document, CONFIG_PROCEESS_IMAGES_TAG).equals("true");
		} catch (ParserConfigurationException | SAXException | IOException e) {
			ConvertServlet.LOG.severe("Error reading config.xml: " + e.getMessage());
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(configFile);
		}
	}

	/**
	 * helper method to get element content
	 * @param document xml document
	 * @param tag xml tag
	 * @return element content
	 */
	private String getConfigElementTextContent(Document document, String tag) {
		return document.getElementsByTagName(tag).item(0).getTextContent();
	}

	String getXsl() {
		return xsl;
	}

	String getMimeType() {
		return mimeType;
	}

	boolean isZipOutputSet() {
		return zipOutput;
	}

	boolean isProcessImagesSet() {
		return processImages;
	}
}
