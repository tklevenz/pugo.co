package co.pugo.convert;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;
import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;

import org.apache.commons.io.IOUtils;

import org.w3c.tidy.Tidy;

import net.sf.saxon.lib.OutputURIResolver;

public class ConvertServlet extends HttpServlet {

	public ConvertServlet() {
		super();
	}

	public void init() throws ServletException {
		super.init();
		System.setProperty("javax.xml.transform.TransformerFactory", 
			"net.sf.saxon.TransformerFactoryImpl");
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		String source = request.getParameter("source");
		String token = request.getParameter("token");
		String fname = request.getParameter("fname");

		convert(source, token, fname, response);
	}

	private void convert(String source, String token, String fname, HttpServletResponse response) 
			throws IOException {

		ServletOutputStream out = response.getOutputStream();

		if (source == null) {
			out.println("No source provided...");
			return;
		}

		// TODO: better downloading!!
		// download source
		String sourceUrl = URLDecoder.decode(source, "UTF-8");
		//out.println("Downloading " + sourceUrl);
		URL url = new URL(sourceUrl);
		URLConnection urlConnection = url.openConnection();

		// set OAuth request token
		if (token != null) {
			urlConnection.setRequestProperty("Authorization", "Bearer " + token);
		}
		
		//urlConnection.setConnectTimeout(1000);
		//urlConnection.setReadTimeout(1000);
		InputStream is = urlConnection.getInputStream();


		ByteArrayOutputStream xhtml = tidyHtml(is);
			
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipOutputStream zos = new ZipOutputStream(baos);

		HashMap<String, byte[]> images = getImages(new ByteArrayInputStream(xhtml.toByteArray()));
		
		for(Map.Entry<String, byte[]> image : images.entrySet()) {
			zos.putNextEntry(new ZipEntry("EPUB/images/" + image.getKey()));
			zos.write(image.getValue());
			zos.closeEntry();
		}

		// xhtml to epub
		InputStream xmlIn = new ByteArrayInputStream(xhtml.toByteArray());
		ByteArrayOutputStream xmlOut = new ByteArrayOutputStream();
		String xslPath = this.getServletContext().getRealPath("/docsToEPub.xsl");
		OutputURIResolver uriResolver = new ZipOutputURIResolver(zos);
		xslTransform(xslPath, xmlIn, xmlOut, uriResolver);
		
		baos.flush();
		baos.close();
		zos.finish();
		zos.close();
			
		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename='" + fname + ".epub'");

		out.println("Finished processing...");
		out.write(baos.toByteArray());
		out.flush();

	}

	private HashMap<String, byte[]> getImages(InputStream is) {
		HashMap<String, byte[]> imageMap = new HashMap<>();

		Scanner s = new Scanner(is);

		Pattern imgPattern = Pattern.compile("<img(.*?)/>", Pattern.DOTALL);
		Pattern altPattern = Pattern.compile("alt=\"(.*?)\"");
		Pattern srcPattern = Pattern.compile("src=\"(.*?)\"");

		while (s.findWithinHorizon(imgPattern, 0) != null) {
			String imgMatch = s.match().group(1);
			Matcher m = altPattern.matcher(imgMatch);
			String alt = "", src = "";
			if (m.find())
				alt = m.group(1);

			m = srcPattern.matcher(imgMatch);
			if (m.find())
				src = m.group(1);
			URL srcUrl;
			try {
				srcUrl = new URL(src);
				URLConnection urlConnection = srcUrl.openConnection();
				urlConnection.setConnectTimeout(1000);
				urlConnection.setReadTimeout(1000);

				InputStream imageIs = urlConnection.getInputStream();

				imageMap.put(alt, IOUtils.toByteArray(imageIs));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		s.close();
		return imageMap;
	}

	/*
	 * runs xsl transformation
	 */
	private void xslTransform(String xslPath, InputStream is, OutputStream os, OutputURIResolver uriResolver) {
		TransformerFactory tFactory = TransformerFactory.newInstance();
		tFactory.setAttribute("http://saxon.sf.net/feature/outputURIResolver", uriResolver);
		try {
			Transformer transformer = tFactory.newTransformer(new StreamSource(xslPath));
			transformer.transform(new StreamSource(is), new StreamResult(os));
		} catch (Exception e) {
			e.printStackTrace();
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