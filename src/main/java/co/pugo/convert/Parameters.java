package co.pugo.convert;

import java.util.HashMap;
import java.util.Map;

/**
 * class to store received http parameters
 */
class Parameters {
	// parameter names
	static final String PARAM_SOURCE = "source";
	static final String PARAM_TOKEN = "token";
	static final String PARAM_FNAME = "fname";
	static final String PARAM_MODE = "mode";
	// parameters
	private String source;
	private String token;
	private String fname;
	private String mode;
	private Map<String, String> xslParameters = new HashMap<>();

	String getSource() {
		return source;
	}

	void setSource(String source) {
		this.source = source;
	}

	String getToken() {
		return token;
	}

	void setToken(String token) {
		this.token = token;
	}

	String getFname() {
		return fname;
	}

	void setFname(String fname) {
		this.fname = fname;
	}

	String getMode() {
		return mode;
	}

	void setMode(String mode) {
		this.mode = mode;
	}

	Map<String, String> getXslParameters() {
		return xslParameters;
	}
}
