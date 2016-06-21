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

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.w3c.tidy.Tidy;

import org.xml.sax.SAXException;

/**
 * @author Tobias Klevenz (tobias.klevenz@gmail.com)
 * 		   This work is part of a paper for M.CSc at Trier University of Applied Science.
 */
@SuppressWarnings("serial")
public class ConvertServlet extends HttpServlet {

	private static final Logger LOG = Logger.getLogger(ConvertServlet.class.getSimpleName());
	// default config file
	private static final String DEFAULT_CONFIG = "config.xml";
	// xml tags in config file
	private static final String CONFIG_XSL_TAG = "xsl";
	private static final String CONFIG_OUTPUT_EXT_TAG = "outputExt";
	private static final String CONFIG_MIMETYPE_TAG = "mimeType";
	private static final String CONFIG_ZIP_OUTPUT_TAG = "zipOutput";
	private static final String CONFIG_PROCEESS_IMAGES_TAG = "processImages";
	// fields to hold values from config file
	private static String xsl;
	private static String outputExt;
	private static String mimeType;
	private static boolean zipOutput;
	private static boolean processImages;

	// parameter names
	private static final String PARAM_SOURCE = "source";
	private static final String PARAM_TOKEN = "token";
	private static final String PARAM_FNAME = "fname";
	private static final String PARAM_MODE = "mode";
	// fields to hold param values
	private String source;
	private String token;
	private String fname;
	private String mode;

	// map that will hold xslt parameters that are provided as xslParam_<xslt parameter name>
	private final Map<String, String> xslParamMap = new HashMap<>();

	private String parseParamsError;

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		// parse parameter and print error message if unsuccessful
		if (!parseParams(request)) {
			response.getWriter().println(parseParamsError);
			return;
		}

		// read config file
		readConfig();

		// set response properties
		setResponseProperties(response);

		// get URLConnection
		URLConnection urlConnection = getSourceUrlConnection();

		// convert html source to xhtml
		InputStream html = urlConnection.getInputStream();
		ByteArrayOutputStream xhtml = new ByteArrayOutputStream();
		tidyHtml(html, xhtml);

		// read xhtml to a String, close streams
		String content = IOUtils.toString(new ByteArrayInputStream(xhtml.toByteArray()));
		IOUtils.closeQuietly(html);
		IOUtils.closeQuietly(xhtml);

		// process images
		if (processImages) {
			Set<String> imageLinks = extractImageLinks(content);
			if (imageLinks != null) {
				content = replaceImgSrcWithBase64(content, downloadImageData(imageLinks));
			}
		}

		// xslt transformation
		setupAndRunXSLTransformation(response, content);
	}

	/**
	 * parse request parameters print information if insufficient parameters are provided
	 * @param request HttpServletRequest
	 * @return success
	 */
	private boolean parseParams(HttpServletRequest request) {
		if (request.getParameterMap().size() == 0) {
			parseParamsError = "No Parameters specified, available Parameters are: \n" +
					"source=[Source URL] \n" +
					"token=[OAuth token] (optional, only if required by Source URL) \n" +
					"fname=[Output Filename] \n" +
					"mode=[md, epub, ...] (optional, xhtml mode if mode is not provided) \n" +
					"xslParam_<XSLT Parameter Name> (optional, any number of parameters can be provided " +
					"and will be passed to the XSLT transformation";
			return false;
		}
		Map parameterMap = request.getParameterMap();
		Iterator iterator = parameterMap.entrySet().iterator();
		Pattern xslParamPattern = Pattern.compile("^xslParam_(.*?)$");
		while (iterator.hasNext()) {
			Map.Entry entry = (Map.Entry) iterator.next();
			String paramName = (String) entry.getKey();
			String[] paramValue = (String[]) entry.getValue();
			Matcher matcher = xslParamPattern.matcher(paramName);
			if (paramName.equals(PARAM_SOURCE))
				source = paramValue[0];
			else if (paramName.equals(PARAM_TOKEN))
				token = paramValue[0];
			else if (paramName.equals(PARAM_FNAME))
				fname = paramValue[0];
			else if (paramName.equals(PARAM_MODE))
				mode = paramValue[0];
			else if (matcher.find())
				xslParamMap.put(matcher.group(1), paramValue[0]);
		}
		if (source == null) {
			parseParamsError = "No source provided";
			return false;
		}
		if (fname == null) {
			parseParamsError = "No fname provided";
			return false;
		} else {
			return true;
		}
	}

	/**
	 * read config file
	 * if mode parameter is provided config_<mode>.xml is used
	 * otherwise default config.xml is used
	 */
	private void readConfig() {
		String config = (mode != null) ? "config_" + mode + ".xml" : DEFAULT_CONFIG;
		InputStream is = getServletContext().getResourceAsStream("/" + config);
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document = documentBuilder.parse(is);
			xsl = getConfigElementTextContent(document, CONFIG_XSL_TAG);
			outputExt = getConfigElementTextContent(document, CONFIG_OUTPUT_EXT_TAG);
			mimeType = getConfigElementTextContent(document, CONFIG_MIMETYPE_TAG);
			zipOutput = getConfigElementTextContent(document, CONFIG_ZIP_OUTPUT_TAG).equals("true");
			processImages = getConfigElementTextContent(document, CONFIG_PROCEESS_IMAGES_TAG).equals("true");
		} catch (ParserConfigurationException | SAXException | IOException e) {
			LOG.severe("Error reading config.xml: " + e.getMessage());
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(is);
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

	/**
	 * set properties of ServletResponse
	 * @param response HttpServletResponse
	 */
	private void setResponseProperties(HttpServletResponse response) {
		response.setContentType(mimeType + "; charset=utf-8");
		response.setHeader("Content-Disposition", "attachment; filename='" + fname + "." + outputExt);
	}

	/**
	 * retrieve URLConnection for source document
	 * @return URLConnection
	 * @throws IOException
	 */
	private URLConnection getSourceUrlConnection() throws IOException {
		URLConnection urlConnection;
		String sourceUrl = URLDecoder.decode(source, "UTF-8");
		URL url = new URL(sourceUrl);
		urlConnection = url.openConnection();
		if (token != null) {
			urlConnection.setRequestProperty("Authorization", "Bearer " + token);
		}
		return urlConnection;
	}

	/**
	 * Setup XSL Transformation and execute
	 * @param response HttpServletResponse
	 * @param content document content as String
	 * @throws IOException
	 */
	private void setupAndRunXSLTransformation(HttpServletResponse response, String content) throws IOException {
		ZipOutputStream zos = null;
		InputStream _xsl = getServletContext().getResourceAsStream(xsl);
		InputStream xhtml = IOUtils.toInputStream(content, "utf-8");
		Transformation transformation;
		if (zipOutput) {
			zos = new ZipOutputStream(response.getOutputStream());
			transformation = new Transformation(_xsl, xhtml, zos);
		} else {
			transformation = new Transformation(_xsl, xhtml, response.getWriter());
		}
		setXsltParameters(transformation);
		transformation.transform();

		// close ZipOutputStream
		if (zos != null) {
			zos.finish();
			zos.close();
		}
	}

	/**
	 * extract a set of image links
	 * @param content document content as String
	 * @return Set of http links to images
	 */
	private Set<String> extractImageLinks(String content) {
		System.err.println("Extracting image data...");

		final Set<String> imageLinks = new HashSet<>();

		final Scanner scanner = new Scanner(content);

		final Pattern imgPattern = Pattern.compile("<img(.*?)>", Pattern.DOTALL);
		final Pattern srcPattern = Pattern.compile("src=\"(.*?)\"");

		Matcher matchSrc;
		String imgMatch;

		while (scanner.findWithinHorizon(imgPattern, 0) != null) {
			imgMatch = scanner.match().group(1);
			matchSrc = srcPattern.matcher(imgMatch);
			if (matchSrc.find())
				imageLinks.add(matchSrc.group(1));
		}

		scanner.close();
		return imageLinks;
	}

	/**
	 * download imageData and encode it base64
	 * @param imageLinks set of image links extracted with extractImageLinks()
	 * @return map, key = imageLink, value = base64 encoded image
	 */
	private HashMap<String, String> downloadImageData(Set<String> imageLinks) {
		HashMap<String, String> imageData = new HashMap<>();
		ExecutorService service = Executors.newCachedThreadPool();
		for (final String imageLink : imageLinks) {
			RunnableFuture<byte[]> future = new FutureTask<>(new Callable<byte[]>() {
				@Override
				public byte[] call() {
					try {
						URL srcUrl = new URL(imageLink);
						URLConnection urlConnection = srcUrl.openConnection();
						return IOUtils.toByteArray(urlConnection.getInputStream());
					} catch (IOException e) {
						LOG.severe(e.getMessage());
						return null;
					}
				}
			});
			service.execute(future);
			try {
				imageData.put(imageLink, Base64.encodeBase64String(future.get()));
			} catch (InterruptedException | ExecutionException e) {
				LOG.severe(e.getMessage());
			}
		}
		service.shutdown();
		try {
			service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			LOG.severe(e.getMessage());
		}
		return imageData;
	}

	/**
	 * search and replace image links with base64 encoded image
	 * @param content document content as String
	 * @param imageData map of extracted imageData
	 * @return content after image links have been replaced with base64 code
	 */
	private String replaceImgSrcWithBase64(String content, Map<String, String> imageData) {
		for (Entry<String, String> entry : imageData.entrySet()) {
			String base64String = entry.getValue();
			Tika tika = new Tika();
			String mimeType = tika.detect(Base64.decodeBase64(base64String));
			content = content.replaceAll(entry.getKey(),
					Matcher.quoteReplacement("data:" + mimeType + ";base64," + base64String));
		}
		return content;
	}

	/*
	 *
	 */

	/**
	 * run JTidy to convert html to xhtml
	 * @param html InputStream of html data
	 * @param xhtml OutputStream for xhtml data
	 */
	private void tidyHtml(InputStream html, OutputStream xhtml) {
		Tidy tidy = new Tidy();
		tidy.setXHTML(true);
		tidy.parse(html, xhtml);
	}

	/**
	 * set xslt parameters stored in xslParamMap to the transformation
	 * @param transformation
	 */
	private void setXsltParameters(Transformation transformation) {
		Integer intValue = null;
		String param, value;
		for (Map.Entry<String, String> entry : xslParamMap.entrySet()) {
			param = entry.getKey();
			value = entry.getValue();
			try {
				intValue = Integer.parseInt(value);
			} catch (NumberFormatException ignored) { }
			// pass Integer to transformer
			if (intValue != null)
				transformation.setParameter(param, intValue);
				// pass boolean to transformer
			else if (value.equals("true") || value.equals("false"))
				transformation.setParameter(param, value.equals("true"));
				// pass String to transformer
			else
				transformation.setParameter(param, value);
		}
	}

	/**
	 * custom Transformation class
	 */
	private class Transformation {
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
		public Transformation(InputStream xsl, InputStream source, OutputStream result) {
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
		public Transformation(InputStream xsl, InputStream source, Writer writer) {
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
}