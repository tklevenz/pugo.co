package co.pugo.convert;

import org.apache.commons.io.IOUtils;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.zip.ZipOutputStream;

/**
 * custom Transformation class
 */
class Transformation {
	private TransformerFactory transformerFactory = new net.sf.saxon.TransformerFactoryImpl();
	private Transformer transformer;
	private InputStream source;
	private OutputStream result;
	private InputStream xsl;
	private Writer writer;

	/**
	 * used for transformation when output is zipped
	 * @param xsl xslt stylesheet as InputStream
	 * @param source xhtml input
	 * @param result will be given to ZipOutputURIResolver
	 */
	Transformation(InputStream xsl, InputStream source, OutputStream result) {
		this.source = source;
		this.result = result;
		this.xsl = xsl;

		if (result instanceof ZipOutputStream)
			transformerFactory.setAttribute("http://saxon.sf.net/feature/outputURIResolver",
					new ZipOutputURIResolver((ZipOutputStream) result));

		setupTransformer();
	}

	/**
	 * used for 'default' transformation
	 * @param xsl xslt stylesheet as InputStream
	 * @param source xhtml input
	 * @param writer output is written to response
	 */
	Transformation(InputStream xsl, InputStream source, Writer writer) {
		this.source = source;
		this.writer = writer;
		this.xsl = xsl;

		setupTransformer();
	}

	/**
	 * helper method to setup transformer
	 */
	private void setupTransformer() {
		try {
			transformer = transformerFactory.newTransformer(new StreamSource(xsl));
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		}
	}

	/**
	 * pass parameter to transformer
	 * @param paramName parameter name
	 * @param paramValue parameter value
	 */
	void setParameter(String paramName, Object paramValue) {
		transformer.setParameter(paramName, paramValue);
	}

	/**
	 * run transformation
	 */
	void transform() {
		StreamResult streamResult = null;
		StreamSource streamSource = new StreamSource(source);

		if (this.result instanceof ZipOutputStream)
			streamResult = new StreamResult(new ByteArrayOutputStream());

		if (writer != null)
			streamResult = new StreamResult(writer);

		try {
			transformer.transform(streamSource, streamResult);
		} catch (TransformerException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(xsl);
			IOUtils.closeQuietly(source);
			IOUtils.closeQuietly(writer);
		}
	}
}
