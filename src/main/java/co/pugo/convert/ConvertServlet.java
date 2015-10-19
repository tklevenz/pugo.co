package co.pugo.convert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
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
import java.util.zip.ZipEntry;
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

import org.apache.commons.io.IOUtils;
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

	private static final Logger log = Logger.getLogger(ConvertServlet.class.getSimpleName());

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		final String source = request.getParameter("source");
		final String token = request.getParameter("token");
		final String fname = request.getParameter("fname");

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

		final HashMap<String, String> imageMap = extractImageData(new ByteArrayInputStream(xhtml.toByteArray()));
		final ExecutorService service = Executors.newCachedThreadPool(ThreadManager.currentRequestThreadFactory());

		if (imageMap != null) {
			for (final Map.Entry<String, String> image : imageMap.entrySet()) {

				RunnableFuture<byte[]> future = new FutureTask<>(new Callable<byte[]>() {
					@Override
					public byte[] call() {
						try {
							URL srcUrl = new URL(image.getValue());
							URLConnection urlConnection = srcUrl.openConnection();
							return IOUtils.toByteArray(urlConnection.getInputStream());
						} catch (IOException e) {
							log.severe(e.getMessage());
							return null;
						}
					}
				});

				service.execute(future);

				try {
					// write images to ZipOutputStream
					zos.putNextEntry(new ZipEntry("EPUB/images/" + image.getKey()));
					zos.write(future.get());
					zos.closeEntry();
				} catch (InterruptedException | ExecutionException e) {
					log.severe(e.getMessage());
				}
			}
		}
		
		
		final String xslPath = this.getServletContext().getRealPath("/docsToEPub.xsl");

		service.execute(new Runnable() {

			@Override
			public void run() {
				final TransformerFactory tFactory = new net.sf.saxon.TransformerFactoryImpl();
				// OutputURIResolver to write files created using xsl:result-document to zipstream 
				tFactory.setAttribute("http://saxon.sf.net/feature/outputURIResolver", new ZipOutputURIResolver(zos));
				
				final ByteArrayInputStream inputStream = new ByteArrayInputStream(xhtml.toByteArray());
				final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

				try {
					Transformer transformer = tFactory.newTransformer(new StreamSource(xslPath));
					transformer.transform(new StreamSource(inputStream), new StreamResult(outputStream));
				} catch (TransformerException te) {
					log.severe(te.getMessage());
				} finally {
					try {
						inputStream.close();
						outputStream.close();
					} catch (IOException e) {
						log.severe(e.getMessage());
					}
				}

			}
		});

		service.shutdown();
		try {
			service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			log.severe(e.getMessage());
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
	 * @return a HashMap containing image data
	 */
	private HashMap<String, String> extractImageData(InputStream inputStream) {
		final HashMap<String, String> imageData = new HashMap<>();

		final Scanner scanner = new Scanner(inputStream);

		final Pattern imgPattern = Pattern.compile("<img(.*?)>", Pattern.DOTALL);
		final Pattern altPattern = Pattern.compile("alt=\"(.*?)\"");
		final Pattern srcPattern = Pattern.compile("src=\"(.*?)\"");

		Matcher matchSrc;
		Matcher matchAlt;
		String imgMatch;

		while (scanner.findWithinHorizon(imgPattern, 0) != null) {
			imgMatch = scanner.match().group(1);

			matchSrc = srcPattern.matcher(imgMatch);
			matchAlt = altPattern.matcher(imgMatch);

			if (matchSrc.find() && matchAlt.find()) {
				imageData.put(matchAlt.group(1), matchSrc.group(1));
			}
		}

		scanner.close();
		return imageData;
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