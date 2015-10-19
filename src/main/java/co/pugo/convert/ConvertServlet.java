package co.pugo.convert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.w3c.tidy.Tidy;

import com.google.appengine.api.ThreadManager;

import net.sf.saxon.lib.OutputURIResolver;

@SuppressWarnings("serial")
public class ConvertServlet extends HttpServlet {

	private ConcurrentHashMap<String, byte[]> images = new ConcurrentHashMap<>();

	public ConvertServlet() {
		super();
	}

	public void init() throws ServletException {
		super.init();
		System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		String source = request.getParameter("source");
		String token = request.getParameter("token");
		String fname = request.getParameter("fname");

		convert(source, token, fname, response);
	}

	private void convert(String source, String token, String fname, HttpServletResponse response) throws IOException {

		ServletOutputStream out = response.getOutputStream();

		if (source == null) {
			out.println("No source provided...");
			return;
		}

		// download source
		String sourceUrl = URLDecoder.decode(source, "UTF-8");
		// out.println("Downloading " + sourceUrl);
		URL url = new URL(sourceUrl);
		URLConnection urlConnection = url.openConnection();

		// set OAuth request token
		if (token != null) {
			urlConnection.setRequestProperty("Authorization", "Bearer " + token);
		}

		InputStream is = urlConnection.getInputStream();

		ByteArrayOutputStream xhtml = tidyHtml(is);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipOutputStream zos = new ZipOutputStream(baos);

		HashMap<String, String> imageData = extractImageData(new ByteArrayInputStream(xhtml.toByteArray()));
		ExecutorService service = Executors.newCachedThreadPool(ThreadManager.currentRequestThreadFactory());

		if (imageData != null) {
			for (Map.Entry<String, String> image : imageData.entrySet()) {
				service.execute(new DownloadImageRunnable(image.getKey(), image.getValue()));
			}
		}

		// xhtml to epub
		service.execute(new XslTransformRunnable(
				this.getServletContext().getRealPath("/docsToEPub.xsl"), 
				new ByteArrayInputStream(xhtml.toByteArray()), 
				new ZipOutputURIResolver(zos)));
		
		
		service.shutdown();		
		try {
			service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		for (Map.Entry<String, byte[]> image : images.entrySet()) {
			zos.putNextEntry(new ZipEntry("EPUB/images/" + image.getKey()));
			zos.write(image.getValue());
			zos.closeEntry();
		}

		baos.flush();
		baos.close();
		zos.finish();
		zos.close();

		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename='" + fname + ".epub'");

		out.write(baos.toByteArray());
		out.flush();

	}

	private HashMap<String, String> extractImageData(InputStream is) {
		HashMap<String, String> imageData = new HashMap<>();

		Scanner s = new Scanner(is);

		Pattern imgPattern = Pattern.compile("<img(.*?)>", Pattern.DOTALL);
		Pattern altPattern = Pattern.compile("alt=\"(.*?)\"");
		Pattern srcPattern = Pattern.compile("src=\"(.*?)\"");
		Matcher matchSrc, matchAlt;
		String imgMatch;

		while (s.findWithinHorizon(imgPattern, 0) != null) {
			imgMatch = s.match().group(1);

			matchSrc = srcPattern.matcher(imgMatch);
			matchAlt = altPattern.matcher(imgMatch);

			if (matchSrc.find() && matchAlt.find()) {
				imageData.put(matchAlt.group(1), matchSrc.group(1));
			}
		}

		s.close();
		return imageData;
	}

	public class DownloadImageRunnable implements Runnable {
		private String alt;
		private String src;

		public DownloadImageRunnable(String alt, String src) {
			this.alt = alt;
			this.src = src;
		}

		@Override
		public void run() {
			try {
				URL srcUrl = new URL(src);
				URLConnection urlConnection = srcUrl.openConnection();

				InputStream is = urlConnection.getInputStream();
				images.put(alt, IOUtils.toByteArray(is));
			} catch (MalformedURLException e) {
				e.printStackTrace();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public class XslTransformRunnable implements Runnable {
		private String xslPath;
		private InputStream is;
		private OutputURIResolver uriResolver;
		private OutputStream os;

		public XslTransformRunnable(String xslPath, InputStream is, OutputURIResolver uriResolver) {
			this.xslPath = xslPath;
			this.is = is;
			this.os = new ByteArrayOutputStream();
			this.uriResolver = uriResolver;
		}

		@Override
		public void run() {
			TransformerFactory tFactory = TransformerFactory.newInstance();
			tFactory.setAttribute("http://saxon.sf.net/feature/outputURIResolver", uriResolver);
			try {
				Transformer transformer = tFactory.newTransformer(new StreamSource(xslPath));
				transformer.transform(new StreamSource(is), new StreamResult(os));			
			} catch (TransformerConfigurationException tce) {
				tce.printStackTrace();
			} catch (TransformerException te) {
				te.printStackTrace();
			} finally {
				try {
					is.close();
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}	
			}
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