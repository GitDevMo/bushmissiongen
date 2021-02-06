package bushmissiongen.entries;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;

public abstract class GenericEntry {
	public String alt = "";

	public Message setAlt(String string) {
		// Check format!

		{
			// +000028.00, +000028.0, 000028.0, +28.00
			Pattern pattern = Pattern.compile("^([\\+\\-]?)([0]+)?([\\d]+)[\\.]([\\d]+)");
			Matcher matcher = pattern.matcher(string);
			if (this.alt.isEmpty() && matcher.find())
			{
				String sign = matcher.group(1);
				if (sign.isEmpty()) {
					sign = "+";
				}

				String numbers = matcher.group(3);
				int numbersInt = Integer.parseInt(numbers);
				numbers = String.format("%06d", numbersInt);

				// Make sure we have two decimals
				String decimals = matcher.group(4);
				if (decimals.length()>2) {
					decimals = decimals.substring(0, 2);
				}
				while (decimals.length()<2) {
					decimals += "0";
				}

				this.alt = sign + numbers + "." + decimals;
			}
		}

		{
			// 000028
			Pattern pattern = Pattern.compile("^([\\+\\-]?)([0]+)?([\\d]+)");
			Matcher matcher = pattern.matcher(string);
			if (this.alt.isEmpty() && matcher.find())
			{
				String sign = matcher.group(1);
				if (sign.isEmpty()) {
					sign = "+";
				}

				String numbers = matcher.group(3);
				int numbersInt = Integer.parseInt(numbers);
				numbers = String.format("%06d", numbersInt);

				// Make sure we have two decimals
				String decimals = "";
				while (decimals.length()<2) {
					decimals += "0";
				}

				this.alt = sign + numbers + "." + decimals;
			}
		}

		if (this.alt.isEmpty()) {
			return new ErrorMessage("Bad alt: " + string);
		}

		return null;
	}

	public String getShortAlt() {
		// Remove zeros padding
		String noPaddingAlt = this.alt;
		String altSign = noPaddingAlt.substring(0, 1);
		String altRest = noPaddingAlt.substring(1);
		while (altRest.startsWith("0")) {
			altRest = altRest.substring(1);
		}
		if (altRest.startsWith(".")) {
			altRest = "0" + altRest;
		}
		return altSign + altRest;
	}
}
