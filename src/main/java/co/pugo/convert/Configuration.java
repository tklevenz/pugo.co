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
	private static final String CONFIG_OUTPUT_EXT_TAG = "outputExt";
	private static final String CONFIG_MIMETYPE_TAG = "mimeType";
	private static final String CONFIG_ZIP_OUTPUT_TAG = "zipOutput";
	private static final String CONFIG_PROCEESS_IMAGES_TAG = "processImages";

	private String xsl;
	private String outputExt;
	private String mimeType;
	private boolean zipOutput;
	private boolean processImages;

	Configuration(InputStream configFile) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
			Document document = documentBuilder.parse(configFile);
			xsl = getConfigElementTextContent(document, CONFIG_XSL_TAG);
			outputExt = getConfigElementTextContent(document, CONFIG_OUTPUT_EXT_TAG);
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

	String getOutputExt() {
		return outputExt;
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
