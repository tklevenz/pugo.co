package co.pugo.convert;

import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;

import net.sf.saxon.lib.OutputURIResolver;

public class ZipOutputURIResolver implements OutputURIResolver {
	
	private ZipOutputStream zos;
	
	public ZipOutputURIResolver(ZipOutputStream zos) {
		super();
		this.zos = zos;
	}

	@Override
	public void close(Result arg0) throws TransformerException {}

	@Override
	public OutputURIResolver newInstance() {
		// TODO Auto-generated method stub
		return new ZipOutputURIResolver(zos);
	}

	@Override
	public Result resolve(String href, String base) throws TransformerException {
		try {
			zos.putNextEntry(new ZipEntry(href));
		} catch (IOException e) {
			e.printStackTrace();
		}
		Result result = new StreamResult(zos);
		result.setSystemId(UUID.randomUUID().toString());
		return result;
	}

}
