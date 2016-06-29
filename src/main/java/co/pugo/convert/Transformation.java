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
import org.apache.commons.io.output.NullOutputStream;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.Writer;
import java.util.Map;
import java.util.zip.ZipOutputStream;

/**
 * custom Transformation class
 */
class Transformation {
	private static final String OUTPUT_URIRESOLVER = "http://saxon.sf.net/feature/outputURIResolver";
	private TransformerFactory transformerFactory = new net.sf.saxon.TransformerFactoryImpl();

	private InputStream xsl;
	private InputStream source;
	private Writer writer;
	private ZipOutputStream zipOutputStream;
	private Transformer transformer;

	/**
	 * used for 'default' transformation
	 * @param xsl xslt stylesheet as InputStream
	 * @param source xhtml input
	 * @param writer output is written to response
	 */
	Transformation(InputStream xsl, InputStream source, Writer writer) {
		this.xsl = xsl;
		this.source = source;
		this.writer = writer;

		setupTransformer();
	}

	/**
	 * used for transformation when output is zipped
	 * @param xsl xslt stylesheet as InputStream
	 * @param source xhtml input
	 * @param zipOutputStream used to create ZipOutputURIResolver
	 */
	Transformation(InputStream xsl, InputStream source, ZipOutputStream zipOutputStream) {
		this.xsl = xsl;
		this.source = source;
		this.zipOutputStream = zipOutputStream;

		transformerFactory.setAttribute(OUTPUT_URIRESOLVER, new ZipOutputURIResolver(zipOutputStream));

		setupTransformer();
	}

	/**
	 * helper method to setup transformer
	 */
	private void setupTransformer() {
		try {
			transformer = transformerFactory.newTransformer(new StreamSource(xsl));
		} catch (TransformerConfigurationException e) { e.printStackTrace(); }
	}

	/**
	 * pass parameter to transformer based on their data type
	 * @param parameters map of xsl parameters passed to the transformer
	 */
	void setParameters(Map<String, String> parameters) {
		String parameterName, parameterValue;
		Integer intValue = null;
		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			parameterName = entry.getKey();
			parameterValue = entry.getValue();
			try {
				intValue = Integer.parseInt(parameterValue);
			} catch (NumberFormatException ignored) { }
			if (intValue != null)
				transformer.setParameter(parameterName, intValue);
			else if (parameterValue.equals("true") || parameterValue.equals("false"))
				transformer.setParameter(parameterName, parameterValue.equals("true"));
			else
				transformer.setParameter(parameterName, parameterValue);
		}
	}

	/**
	 * run transformation
	 */
	void transform() {
		try {
			if (writer != null)
				transformer.transform(new StreamSource(source), new StreamResult(writer));
			else
				transformer.transform(new StreamSource(source), new StreamResult(new NullOutputStream()));
		} catch (TransformerException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(xsl);
			IOUtils.closeQuietly(source);
			IOUtils.closeQuietly(writer);
			IOUtils.closeQuietly(zipOutputStream);
		}
	}
}