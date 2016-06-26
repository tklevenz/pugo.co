package co.pugo.convert;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	Parameters(Map parameterMap) {
		Iterator iterator = parameterMap.entrySet().iterator();
		Pattern xslParamPattern = Pattern.compile("^xslParam_(.*?)$");
		while (iterator.hasNext()) {
			Map.Entry entry = (Map.Entry) iterator.next();
			String paramName = (String) entry.getKey();
			String[] paramValue = (String[]) entry.getValue();
			Matcher matcher = xslParamPattern.matcher(paramName);
			if (paramName.equals(Parameters.PARAM_SOURCE))
				source = paramValue[0];
			else if (paramName.equals(Parameters.PARAM_TOKEN))
				token = paramValue[0];
			else if (paramName.equals(Parameters.PARAM_FNAME))
				fname = paramValue[0];
			else if (paramName.equals(Parameters.PARAM_MODE))
				mode = paramValue[0];
			else if (matcher.find())
				xslParameters.put(matcher.group(1), paramValue[0]);
		}
	}

	String getSource() {
		return source;
	}

	String getToken() {
		return token;
	}

	String getFname() {
		return fname;
	}

	String getMode() {
		return mode;
	}

	Map<String, String> getXslParameters() {
		return xslParameters;
	}
}
