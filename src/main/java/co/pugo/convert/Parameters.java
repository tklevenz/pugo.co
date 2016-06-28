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
	private static final String PARAM_SOURCE = "source";
	private static final String PARAM_TOKEN = "token";
	private static final String PARAM_FNAME = "fname";
	private static final String PARAM_MODE = "mode";
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