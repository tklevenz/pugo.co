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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.w3c.tidy.Tidy;

import com.google.appengine.api.ThreadManager;

/**
 * 
 *
 * @author Tobias Klevenz (tobias.klevenz@gmail.com) Project work for M.CSc at
 *         Trier University of Applied Science.
 */
@SuppressWarnings("serial")
public class ConvertServlet extends HttpServlet {

	private static final Logger LOG = Logger.getLogger(ConvertServlet.class.getSimpleName());
	private static final String PARAM_BOOK_ID = "book-id"; 
	private static final String PARAM_PUB_LANGUAGE = "pub-language";
	private static final String PARAM_GROUPING_LEVEL = "grouping-level";

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		final String source = request.getParameter("source");
		final String token = request.getParameter("token");
		final String fname = request.getParameter("fname");
		
		final String bookId = request.getParameter(PARAM_BOOK_ID);
		final String pubLanguage = request.getParameter(PARAM_PUB_LANGUAGE);
		final int groupingLevel = Integer.parseInt(request.getParameter(PARAM_GROUPING_LEVEL));
		
		

		// download source
		final String sourceUrl = URLDecoder.decode(source, "UTF-8");
		final URL url = new URL(sourceUrl);
		final URLConnection urlConnection = url.openConnection();
		// if token is supplied set auth token
		if (token != null)
			urlConnection.setRequestProperty("Authorization", "Bearer " + token);

		final ByteArrayOutputStream xhtml = tidyHtml(urlConnection.getInputStream());

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final ZipOutputStream zos = new ZipOutputStream(baos);

		final Set<String> imageLinks = extractImageLinks(new ByteArrayInputStream(xhtml.toByteArray()));
		final HashMap<String, String> imageData = new HashMap<>();
		final ExecutorService service = Executors.newCachedThreadPool(ThreadManager.currentRequestThreadFactory());

		if (imageLinks != null) {
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
		}

		final String xslPath = this.getServletContext().getRealPath("/docsToEPub.xsl");

		service.execute(new Runnable() {

			@Override
			public void run() {
				final TransformerFactory tFactory = new net.sf.saxon.TransformerFactoryImpl();
				// OutputURIResolver to write files created using
				// xsl:result-document to zipstream
				tFactory.setAttribute("http://saxon.sf.net/feature/outputURIResolver", new ZipOutputURIResolver(zos));

				final ByteArrayInputStream inputStream = new ByteArrayInputStream(
						replaceImgSrcWithBase64(new ByteArrayInputStream(xhtml.toByteArray()), imageData)
								.toByteArray());
				final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

				try {
					Transformer transformer = tFactory.newTransformer(new StreamSource(xslPath));
					transformer.setParameter(PARAM_BOOK_ID, bookId);
					transformer.setParameter(PARAM_PUB_LANGUAGE, pubLanguage);
					transformer.setParameter(PARAM_GROUPING_LEVEL, groupingLevel);
					transformer.transform(new StreamSource(inputStream), new StreamResult(outputStream));
				} catch (TransformerException te) {
					LOG.severe(te.getMessage());
				} finally {
					try {
						inputStream.close();
						outputStream.close();
					} catch (IOException e) {
						LOG.severe(e.getMessage());
					}
				}

			}
		});

		service.shutdown();
		try {
			service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			LOG.severe(e.getMessage());
		}

		baos.flush();
		baos.close();
		zos.finish();
		zos.close();

		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename='" + fname + ".epub'");

		response.getOutputStream().write(baos.toByteArray());
		response.getOutputStream().flush();
	}

	/*
	 * @return a Set containing image data
	 */
	private Set<String> extractImageLinks(InputStream inputStream) {
		System.err.println("Extracting image data...");

		final Set<String> imageLinks = new HashSet<>();

		final Scanner scanner = new Scanner(inputStream);

		final Pattern imgPattern = Pattern.compile("<img(.*?)>", Pattern.DOTALL);
		// final Pattern altPattern = Pattern.compile("alt=\"(.*?)\"");
		final Pattern srcPattern = Pattern.compile("src=\"(.*?)\"");

		Matcher matchSrc;
		// Matcher matchAlt;
		String imgMatch;
		// int i = 1;

		while (scanner.findWithinHorizon(imgPattern, 0) != null) {
			imgMatch = scanner.match().group(1);

			matchSrc = srcPattern.matcher(imgMatch);
			// matchAlt = altPattern.matcher(imgMatch);
			/*
			 * if (matchSrc.find() && matchAlt.find()) { if
			 * (matchAlt.group(1).equals("")) { key = "image" + i + ".png"; i++;
			 * } else { key = matchAlt.group(1); } imageData.put(key,
			 * matchSrc.group(1)); System.err.println(key + ": " +
			 * matchSrc.group(1)); }
			 */

			if (matchSrc.find())
				imageLinks.add(matchSrc.group(1));
		}

		scanner.close();
		return imageLinks;
	}

	private ByteArrayOutputStream replaceImgSrcWithBase64(InputStream is, Map<String, String> imageData) {
		try {
			String content = IOUtils.toString(is);
			Iterator<Entry<String, String>> iterator = imageData.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, String> entry = (Entry<String, String>) iterator.next();
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
}