package co.pugo.convert;

/**
 * class to store output configuration
 */
class Configuration {
	// default config file
	static final String DEFAULT_CONFIG = "config.xml";
	// xml tags in config file
	static final String CONFIG_XSL_TAG = "xsl";
	static final String CONFIG_OUTPUT_EXT_TAG = "outputExt";
	static final String CONFIG_MIMETYPE_TAG = "mimeType";
	static final String CONFIG_ZIP_OUTPUT_TAG = "zipOutput";
	static final String CONFIG_PROCEESS_IMAGES_TAG = "processImages";

	private String xsl;
	private String outputExt;
	private String mimeType;
	private boolean zipOutput;
	private boolean processImages;

	String getXsl() {
		return xsl;
	}

	void setXsl(String xsl) {
		this.xsl = xsl;
	}

	String getOutputExt() {
		return outputExt;
	}

	void setOutputExt(String outputExt) {
		this.outputExt = outputExt;
	}

	String getMimeType() {
		return mimeType;
	}

	void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	boolean isZipOutput() {
		return zipOutput;
	}

	void setZipOutput(boolean zipOutput) {
		this.zipOutput = zipOutput;
	}

	boolean isProcessImages() {
		return processImages;
	}

	void setProcessImages(boolean processImages) {
		this.processImages = processImages;
	}
}
