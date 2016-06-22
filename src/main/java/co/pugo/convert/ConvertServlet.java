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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.w3c.tidy.Tidy;

/**
 * @author Tobias Klevenz (tobias.klevenz@gmail.com)
 * 		   This work is part of a paper for M.CSc at Trier University of Applied Science.
 */
@SuppressWarnings("serial")
public class ConvertServlet extends HttpServlet {

	static final Logger LOG = Logger.getLogger(ConvertServlet.class.getSimpleName());

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		Parameters parameters = new Parameters();
		if (!parseParams(request, response, parameters))
			return;

		// read config file
		Configuration configuration = new Configuration(getConfigFile(parameters.getMode()));

		// set response properties
		setResponseProperties(response, configuration.getMimeType(),
				parameters.getFname() + "." + configuration.getOutputExt());

		// get URLConnection
		URLConnection urlConnection = getSourceUrlConnection(parameters.getSource(), parameters.getToken());

		// convert html source to xhtml
		InputStream html = urlConnection.getInputStream();
		ByteArrayOutputStream xhtml = new ByteArrayOutputStream();
		tidyHtml(html, xhtml);

		// read xhtml to a String, close streams
		String content = IOUtils.toString(new ByteArrayInputStream(xhtml.toByteArray()));

		// closing streams
		IOUtils.closeQuietly(html);
		IOUtils.closeQuietly(xhtml);

		// process images
		if (configuration.isProcessImagesSet()) {
			Set<String> imageLinks = extractImageLinks(content);
			if (imageLinks != null) {
				content = replaceImgSrcWithBase64(content, downloadImageData(imageLinks));
			}
		}

		// xslt transformation
		setupAndRunXSLTransformation(response, content, parameters, configuration);
	}

	/**
	 * get config file as Stream based on provided mode
 	 * @param mode output mode set by parameter
	 * @return Config File as InputStream
	 */
	private InputStream getConfigFile(String mode) {
		InputStream configFileStream = null;
		if (mode != null)
			configFileStream = getServletContext().getResourceAsStream("/config_" + mode + ".xml");
		if (mode == null || configFileStream == null)
			configFileStream = getServletContext().getResourceAsStream("/config.xml");
		return configFileStream;
	}

	/**
	 * parse request parameters print error message if insufficient parameters are provided
	 * @param request HttpServletRequest
	 * @param response HttpServletResponse
	 * @param parameters local Parameters object
	 * @return success
	 */
	private boolean parseParams(HttpServletRequest request, HttpServletResponse response, Parameters parameters)
		throws IOException{
		if (request.getParameterMap().size() == 0) {
			response.getWriter().println("No Parameters specified, available Parameters are: \n" +
					"source=[Source URL] \n" +
					"token=[OAuth token] (optional, only if required by Source URL) \n" +
					"fname=[Output Filename] \n" +
					"mode=[md, epub, ...] (optional, xhtml mode if mode is not provided) \n" +
					"xslParam_<XSLT Parameter Name> (optional, any number of parameters can be provided " +
					"and will be passed to the XSLT transformation");
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
			if (paramName.equals(Parameters.PARAM_SOURCE))
				parameters.setSource(paramValue[0]);
			else if (paramName.equals(Parameters.PARAM_TOKEN))
				parameters.setToken(paramValue[0]);
			else if (paramName.equals(Parameters.PARAM_FNAME))
				parameters.setFname(paramValue[0]);
			else if (paramName.equals(Parameters.PARAM_MODE))
				parameters.setMode(paramValue[0]);
			else if (matcher.find())
				parameters.getXslParameters().put(matcher.group(1), paramValue[0]);
		}
		if (parameters.getSource() == null) {
			response.getWriter().println("No source provided");
			return false;
		}
		if (parameters.getFname() == null) {
			response.getWriter().println("No fname provided");
			return false;
		} else {
			return true;
		}
	}

	/**
	 * set properties of ServletResponse
	 * @param response HttpServletResponse
	 */
	private void setResponseProperties(HttpServletResponse response, String mimeType, String fileName) {
		response.setContentType(mimeType + "; charset=utf-8");
		response.setHeader("Content-Disposition", "attachment; filename='" + fileName);
	}

	/**
	 * retrieve URLConnection for source document
	 * @return URLConnection
	 * @throws IOException
	 */
	private URLConnection getSourceUrlConnection(String source, String token) throws IOException {
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
	private void setupAndRunXSLTransformation(HttpServletResponse response, String content,
											  Parameters parameters, Configuration configuration) throws IOException {
		ZipOutputStream zos = null;
		InputStream _xsl = getServletContext().getResourceAsStream(configuration.getXsl());
		InputStream xhtml = IOUtils.toInputStream(content, "utf-8");
		Transformation transformation;
		if (configuration.isZipOutputSet()) {
			zos = new ZipOutputStream(response.getOutputStream());
			transformation = new Transformation(_xsl, xhtml, zos);
		} else {
			transformation = new Transformation(_xsl, xhtml, response.getWriter());
		}

		Integer intValue = null;
		String param, value;
		for (Map.Entry<String, String> entry : parameters.getXslParameters().entrySet()) {
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
}