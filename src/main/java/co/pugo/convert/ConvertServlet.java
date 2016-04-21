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
import java.io.ByteArrayOutputStream;
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

import com.google.appengine.api.ThreadManager;
import org.xml.sax.SAXException;

/**
 * @author Tobias Klevenz (tobias.klevenz@gmail.com) Project work for M.CSc at
 *         Trier University of Applied Science.
 */
@SuppressWarnings("serial")
public class ConvertServlet extends HttpServlet {

	private static final Logger LOG = Logger.getLogger(ConvertServlet.class.getSimpleName());

	private static final String CONFIG_FILE = "config_html.xml";
	private static final String CONFIG_XSL_TAG = "xsl";

	private static final String CONFIG_OUTPUT_EXT_TAG = "outputExt";
	private static final String CONFIG_MIMETYPE_TAG = "mimeType";
	private static final String CONFIG_ZIP_OUTPUT_TAG = "zipOutput";

	private static final String PARAM_SOURCE = "source";
	private static final String PARAM_TOKEN = "token";
	private static final String PARAM_FNAME = "fname";

	private static String xsl;
	private static String outputExt;
	private static String mimeType;
	private static boolean zipOutput;

	private String source;
	private String token;
	private String fname;
	private Map<String, String> xslParamMap = new HashMap<>();

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		parseParams(request, response);

		readConfig(CONFIG_FILE);

		// download source
		String sourceUrl = URLDecoder.decode(source, "UTF-8");
		URL url = new URL(sourceUrl);
		URLConnection urlConnection = url.openConnection();

		urlConnection.setRequestProperty("Authorization", "Bearer " + token);

		ByteArrayOutputStream xhtml = tidyHtml(urlConnection.getInputStream());

		Set<String> imageLinks = extractImageLinks(new ByteArrayInputStream(xhtml.toByteArray()));
		HashMap<String, String> imageData = new HashMap<>();

		if (imageLinks != null) {
			downloadImageData(imageLinks, imageData);
			xhtml = replaceImgSrcWithBase64(new ByteArrayInputStream(xhtml.toByteArray()), imageData);
		}



		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipOutputStream zos = null;

		String xslPath = this.getServletContext().getRealPath(xsl);
		InputStream inputStream = new ByteArrayInputStream(xhtml.toByteArray());

		Transformation transformation;

		if (zipOutput) {
			zos =  new ZipOutputStream(baos);
			transformation = new Transformation(xslPath, inputStream, zos);
		} else {
			transformation = new Transformation(xslPath, inputStream, baos);
		}

		setXsltParameters(transformation);

		ThreadManager.createThreadForCurrentRequest(transformation).run();

		baos.flush();
		baos.close();

		if (zos != null) {
			zos.finish();
			zos.close();
		}

		response.setContentType(mimeType);
		response.setHeader("Content-Disposition", "attachment; filename='" + fname + "." + outputExt);

		response.getOutputStream().write(baos.toByteArray());
		response.getOutputStream().flush();
	}

	private void parseParams(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (request.getParameterMap().size() == 0) {
			printOut(response, "No Parameters specified, requirted Parameters are: \n"
					+ "/convert?source=[googleDocs-ExportLink]&token=[OAuthToken]&fname=[OutputFileName]");
			return;
		}

		Map paramtereMap = request.getParameterMap();
		Iterator iterator = paramtereMap.entrySet().iterator();

		Pattern xslParamPattern = Pattern.compile("^xslParam_(.*?)$");

		Matcher matcher;
		String paramName, paramValue;

		while (iterator.hasNext()) {
			Map.Entry<String, String[]> entry = (Entry<String, String[]>) iterator.next();
			paramName = entry.getKey();
			paramValue = entry.getValue()[0];
			matcher = xslParamPattern.matcher(paramName);
			if (paramName.equals(PARAM_SOURCE))
				source = paramValue;
			else if (paramName.equals(PARAM_TOKEN))
				token = paramValue;
			else if (paramName.equals(PARAM_FNAME))
				fname = paramValue;
			else if (matcher.find())
				xslParamMap.put(matcher.group(1), paramValue);
		}

		if (source == null) {
			printOut(response, "No source provided");
			return;
		}

		if (token == null) {
			printOut(response, "No token provided");
			return;
		}

		if (fname == null) {
			printOut(response, "No fname provided");
			return;
		}


	}


	private void printOut(HttpServletResponse response, String str) throws IOException {
		PrintWriter out = response.getWriter();
		out.println(str);
	}

	private void readConfig(String config) {
		File configFile = new File(this.getServletContext().getRealPath(config));
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document = documentBuilder.parse(configFile);
			xsl = getConfigElementTextContent(document, CONFIG_XSL_TAG);
			outputExt = getConfigElementTextContent(document, CONFIG_OUTPUT_EXT_TAG);
			mimeType = getConfigElementTextContent(document, CONFIG_MIMETYPE_TAG);
			zipOutput = getConfigElementTextContent(document, CONFIG_ZIP_OUTPUT_TAG).equals("true");
		} catch (ParserConfigurationException | SAXException | IOException e) {
			LOG.severe("Error reading config.xml: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private String getConfigElementTextContent(Document document, String tag) {
		return document.getElementsByTagName(tag).item(0).getTextContent();
	}


	private void setXsltParameters(Transformation transformation) {
		Integer intValue = null;
		String param, value;
		for (Map.Entry<String, String> entry : xslParamMap.entrySet()) {
			param = entry.getKey();
			value = entry.getValue();
			try {
				intValue = Integer.parseInt(value);
			} catch (NumberFormatException ignored) {
			}

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


	/*
	 * @return a Set containing image data
	 */
	private Set<String> extractImageLinks(InputStream inputStream) {
		System.err.println("Extracting image data...");

		final Set<String> imageLinks = new HashSet<>();

		final Scanner scanner = new Scanner(inputStream);

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

	private void downloadImageData(Set<String> imageLinks, HashMap<String, String> imageData) {

		ExecutorService service = Executors.newCachedThreadPool(ThreadManager.currentRequestThreadFactory());

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
	}

	private ByteArrayOutputStream replaceImgSrcWithBase64(InputStream is, Map<String, String> imageData) {
		try {
			String content = IOUtils.toString(is);
			for (Entry<String, String> entry : imageData.entrySet()) {
				String base64String = entry.getValue();
				Tika tika = new Tika();
				String mimeType = tika.detect(Base64.decodeBase64(base64String));
				content = content.replaceAll(entry.getKey(),
						Matcher.quoteReplacement("data:" + mimeType + ";base64," + base64String));
			}
			is.close();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			IOUtils.write(content, os);
			return os;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	/*
	 * runs JTidy to convert html to xhtml
	 */
	private ByteArrayOutputStream tidyHtml(InputStream in) {
		Tidy tidy = new Tidy();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		tidy.setXHTML(true);
		tidy.parse(in, baos);
		return baos;
	}

	private class Transformation implements Runnable {
		private TransformerFactory transformerFactory = new net.sf.saxon.TransformerFactoryImpl();
		private Transformer transformer;
		private InputStream inputStream;
		private OutputStream outputStream;

		public Transformation(String xslPath, InputStream inputStream, OutputStream outputStream) {
			this.inputStream = inputStream;
			this.outputStream = outputStream;

			if (outputStream instanceof ZipOutputStream)
				transformerFactory.setAttribute("http://saxon.sf.net/feature/outputURIResolver",
						new ZipOutputURIResolver((ZipOutputStream) outputStream));

			try {
				transformer = transformerFactory.newTransformer(new StreamSource(xslPath));
			} catch (TransformerConfigurationException e) {
				e.printStackTrace();
			}

		}

		void setParameter(String name, Object parameter) {
			transformer.setParameter(name, parameter);
		}

		@Override
		public void run() {
			StreamResult result;

			if (outputStream instanceof ZipOutputStream)
				result = new StreamResult(new ByteArrayOutputStream());
			else
				result = new StreamResult(outputStream);

			try {
				transformer.transform(new StreamSource(inputStream), result);
			} catch (TransformerException e) {
				e.printStackTrace();
			}
		}
	}
}