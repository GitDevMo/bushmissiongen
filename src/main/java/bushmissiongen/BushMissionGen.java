package bushmissiongen;

import java.awt.Font;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import bushmissiongen.entries.DialogEntry;
import bushmissiongen.entries.FailureEntry;
import bushmissiongen.entries.FailureEntry.FailureEntryMode;
import bushmissiongen.entries.MetaEntry;
import bushmissiongen.entries.MissionEntry;
import bushmissiongen.entries.MissionEntry.WpType;
import bushmissiongen.entries.MissionFailureEntry;
import bushmissiongen.entries.MissionFailureEntry.MissionFailureEntryMode;
import bushmissiongen.entries.WarningEntry;
import bushmissiongen.entries.WarningEntry.WarningEntryMode;
import bushmissiongen.handling.FileHandling;
import bushmissiongen.handling.ImageHandling;
import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.InfoMessage;
import bushmissiongen.messages.Message;
import bushmissiongen.misc.DelayedText;
import bushmissiongen.misc.GeoJSON;
import bushmissiongen.misc.Localization;
import bushmissiongen.misc.SimData;
import bushmissiongen.misc.ToggleTrigger;

/**
 * BushMissionGen
 *
 * @author  f99mlu
 */
public class BushMissionGen {
	public static final String VERSION = "1.73";

	// NEWS
	// - Put WAV files in the same directory as the input file to automatically get them copied to the output folder.

	// TO DO
	// - What is the Overview.htm file used for in landing challenges?
	// - Leaderboards for landing challenges? Possible for 3rd party missions?
	// - Is there the possibility of setting the flight departure at the parking area instead of on the runway (leg 2-X)?

	private static final int META_REQUIRED_ITEMS = 20;
	private static final int META_SPLIT_LEN = 2;

	public static final int WP_SPLIT_LEN = 9;
	public static final int WP_EXTRA_SPLIT_LEN = 16;
	public static final int WP_LANDING_LEN = 6;
	public static final int WP_LOCALIZATION_LEN = 5;

	private static final int MAX_COUNT_LEGS = 728;  // 2*26 (!A, ?A, AA, AB, ..)
	private static final int MAX_COUNT_POIS = 1035; //  (1035 == FF)

	public GUI mGUI;

	public String[] mArgs = null;
	public static String COMMUNITY_DIR = null;
	public static String OFFICIAL_DIR = null;

	public Document mDoc;
	public String mSavedPreviewFile = null;
	public boolean mMultipleSameAirports = false;
	public Integer mPOIs = null;
	public GeoJSON mGeoJSON = new GeoJSON();
	public Set<String> mSounds = null;
	public Set<String> mCopiedSounds = null;
	public FileHandling mFileHandling = new FileHandling();
	public ImageHandling mImageHandling = new ImageHandling();
	public Settings mSettings = new Settings();
	public SimData mSimData = new SimData();

	private static String FLT_AIRLINER_BUSH;
	private static String FLT_AIRLINER_LAND;

	private static String LOC_LANGUAGE;
	private static String LOC_STRING;

	private static String PLN_ATCWAYPOINTS_V1;
	private static String PLN_ATCWAYPOINTS_V2;
	private static String PLN_ATCWAYPOINTS_V3;

	private static String XML_ALTITUDESPEEDTRIGGER;
	private static String XML_ALTITUDETRIGGER;
	private static String XML_CALC;
	private static String XML_COUNTACTION;
	private static String XML_COUNTERTRIGGER;
	private static String XML_DIALOGACTION;
	private static String XML_DIALOGS;
	private static String XML_DIALOGSEXIT;
	private static String XML_FAILUREACTION;
	private static String XML_FAILURES;
	private static String XML_FAILURESEXIT;
	private static String XML_FORMULATRIGGER;
	private static String XML_GOAL;
	private static String XML_INTRODIALOG;
	private static String XML_LANDEDDIALOGS;
	private static String XML_LANDEDTRIGGER;
	private static String XML_LEG;
	private static String XML_OBJECTIVE;
	private static String XML_OBJECTACTIVATIONACTION;
	private static String XML_PROXIMITYTRIGGER;
	private static String XML_RESETACTION;
	private static String XML_SPEEDTRIGGER;
	private static String XML_SUBLEG;
	private static String XML_TIMERTRIGGER;

	public BushMissionGen(String[] args) {
		mArgs = args;
		mGUI = new GUI(this);

		String[] simPaths = mSimData.getPaths();
		if (simPaths != null) {
			COMMUNITY_DIR = simPaths[0];
			OFFICIAL_DIR = simPaths[1];
		}
	}

	public String preScan(List<String> list) {
		String contents = String.join(System.lineSeparator(), list);

		int countPipe = contents.length() - contents.replace("|", "").length();
		int countSemicolon = contents.length() - contents.replace(";", "").length();
		int countTabs = contents.length() - contents.replace("\t", "").length();

		if (countSemicolon>countPipe && countSemicolon>countTabs) {
			return ";";
		} else if (countTabs>countPipe && countTabs>countSemicolon) {
			return "\\t";
		} else {
			return "\\|";
		}
	}

	public Message generate(String recept_file, int mode) {
		// Pre-check!
		int[] lengthArray = new int[] {WP_SPLIT_LEN, WP_EXTRA_SPLIT_LEN, WP_LANDING_LEN, WP_LOCALIZATION_LEN};
		Set<Integer> lengthSet = new HashSet<>();
		for (int length : lengthArray) {
			if (lengthSet.add(length) == false) {
				return new ErrorMessage("Oh no Mats! You can´t have multiple splits with the same length!");
			}
		}

		if (recept_file==null || recept_file.isEmpty()) {
			return new ErrorMessage("No input file has been selected.");
		}

		if (!new File(recept_file).exists() || new File(recept_file).isDirectory()) {
			return new ErrorMessage("The input file does not exist.");
		}

		if (mode == 2) {
			String editor = "c:\\Program Files (x86)\\Notepad++\\notepad++.exe";
			if (!new File(editor).exists()) {
				editor = "c:\\Program Files\\Notepad++\\notepad++.exe";
			}
			if (new File(editor).exists()) {
				if (recept_file.toLowerCase().endsWith(".xlsx")) {
					return new ErrorMessage("Only text files can be opened by Notepad++.");
				}

				try {
					Runtime.getRuntime().exec(editor + " \"" + recept_file + "\"");
				} catch (IOException e1) {
					return new ErrorMessage("Could not find Notepad++ where it usually is installed.");
				}
			}
			return null;
		}

		// Init
		MetaEntry metaEntry = new MetaEntry();
		List<MissionEntry> entries = new ArrayList<>();
		mSavedPreviewFile = null;
		mMultipleSameAirports = false;

		// Read recipe file
		int count_POI = 0;
		int count_AIRPORT = 0;
		int count_META = 0;
		try {
			Path path = FileSystems.getDefault().getPath(recept_file);

			List<String> list;
			if (!recept_file.toLowerCase().endsWith(".xlsx")) {
				list = Files.readAllLines(path, StandardCharsets.UTF_8);
			} else {
				list = mFileHandling.readFromXLS(recept_file);
			}

			String separator = preScan(list);

			for (String line : list) {
				line = line.trim();
				if (line.startsWith("#") || line.isEmpty()) {
					continue;
				}

				String[] splitMeta = line.split("=", -1);
				String[] split = line.split(separator, -1);

				if (splitMeta.length == META_SPLIT_LEN) {
					String metaName = null;
					String metaField = splitMeta[0];
					String metaString = splitMeta[1];

					String[] nameSplit = metaField.split("::", -1);
					if (nameSplit != null && nameSplit.length==2) {
						metaName = nameSplit[0];
						metaField = nameSplit[1];
						line = line.substring(metaName.length()+2);
					}

					if (metaField.equalsIgnoreCase("author")) {metaEntry.author = metaString.trim(); metaEntry.remove("author"); count_META++;}
					if (metaField.equalsIgnoreCase("title")) {metaEntry.title = metaString.trim(); metaEntry.remove("title"); count_META++;}
					if (metaField.equalsIgnoreCase("project")) {
						metaEntry.project = metaString.trim();

						// Fulfill FS2020 package name validation
						Pattern pattern = Pattern.compile("^[a-z0-9]+-[a-z0-9-]+$");
						Matcher matcher = pattern.matcher(metaEntry.project);
						if (!matcher.find()) {
							return new ErrorMessage("Project names must be in the form 'aaa-bbb-...-xxx'\nand only contain lower case letters or digits.");
						}
						metaEntry.remove("project"); 
						count_META++;
					}
					if (metaField.equalsIgnoreCase("version")) {metaEntry.version = metaString.trim(); metaEntry.remove("version"); count_META++;}
					if (metaField.equalsIgnoreCase("location")) {metaEntry.location = metaString.trim(); metaEntry.remove("location"); count_META++;}
					if (metaField.equalsIgnoreCase("description")) {metaEntry.description = metaString.trim(); metaEntry.remove("description"); count_META++;}
					if (metaField.equalsIgnoreCase("intro")) {metaEntry.intro = metaString.trim(); metaEntry.remove("intro"); count_META++;}
					if (metaField.equalsIgnoreCase("latitude")) {
						Message msg = metaEntry.setLat(metaString.trim());
						if (msg != null) {
							return msg;
						}
						metaEntry.remove("latitude"); 
						count_META++;
					}
					if (metaField.equalsIgnoreCase("longitude")) {
						Message msg = metaEntry.setLon(metaString.trim());
						if (msg != null) {
							return msg;
						}
						metaEntry.remove("longitude"); 
						count_META++;
					}
					if (metaField.equalsIgnoreCase("altitude")) {
						Message msg = metaEntry.setAlt(metaString.trim());
						if (msg != null) {
							return msg;
						}
						metaEntry.remove("altitude"); 
						count_META++;
					}
					if (metaField.equalsIgnoreCase("pitch")) {metaEntry.pitch = metaString.trim(); metaEntry.remove("pitch"); count_META++;}
					if (metaField.equalsIgnoreCase("bank")) {metaEntry.bank = metaString.trim(); metaEntry.remove("bank"); count_META++;}
					if (metaField.equalsIgnoreCase("heading")) {metaEntry.heading = metaString.trim(); metaEntry.remove("heading"); count_META++;}
					if (metaField.equalsIgnoreCase("plane")) {metaEntry.plane = metaString.trim(); metaEntry.remove("plane"); count_META++;}
					if (metaField.equalsIgnoreCase("season")) {metaEntry.season = metaString.trim(); metaEntry.remove("season"); count_META++;}
					if (metaField.equalsIgnoreCase("year")) {metaEntry.year = metaString.trim(); metaEntry.remove("year"); count_META++;}
					if (metaField.equalsIgnoreCase("day")) {metaEntry.day = metaString.trim(); metaEntry.remove("day"); count_META++;}
					if (metaField.equalsIgnoreCase("hours")) {metaEntry.hours = metaString.trim(); metaEntry.remove("hours"); count_META++;}
					if (metaField.equalsIgnoreCase("minutes")) {metaEntry.minutes = metaString.trim(); metaEntry.remove("minutes"); count_META++;}
					if (metaField.equalsIgnoreCase("seconds")) {metaEntry.seconds = metaString.trim(); metaEntry.remove("seconds"); count_META++;}

					// Optional
					if (metaField.equalsIgnoreCase("sdkPath")) {metaEntry.sdkPath = metaString.trim();}
					if (metaField.equalsIgnoreCase("altitudeWarning")) {
						WarningEntry we = new WarningEntry(metaName, metaField.trim(), metaString.trim(), WarningEntryMode.ALTITUDE);
						Message msgWE = we.handle();
						if (msgWE != null) {
							return msgWE;
						}
						metaEntry.warningsEntries.add(we);
					}
					if (metaField.equalsIgnoreCase("speedWarning")) {
						WarningEntry we = new WarningEntry(metaName, metaField.trim(), metaString.trim(), WarningEntryMode.SPEED);
						Message msgWE = we.handle();
						if (msgWE != null) {
							return msgWE;
						}
						metaEntry.warningsEntries.add(we);
					}
					if (metaField.equalsIgnoreCase("altitudeSpeedWarning") || metaField.equalsIgnoreCase("altitudeAndSpeedWarning")) {
						WarningEntry we = new WarningEntry(metaName, metaField.trim(), metaString.trim(), WarningEntryMode.ALTITUDE_AND_SPEED);
						Message msgWE = we.handle();
						if (msgWE != null) {
							return msgWE;
						}
						metaEntry.warningsEntries.add(we);
					}
					if (metaField.equalsIgnoreCase("formulaWarning")) {
						WarningEntry we = new WarningEntry(metaName, metaField.trim(), metaString.trim(), WarningEntryMode.FORMULA);
						Message msgWE = we.handle();
						if (msgWE != null) {
							return msgWE;
						}
						metaEntry.warningsEntries.add(we);
					}
					if (metaField.equalsIgnoreCase("uniqueApImages")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.uniqueApImages = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("tooltip") || metaField.equalsIgnoreCase("loadingtip")) {metaEntry.tooltips.add(metaString.trim());}
					if (metaField.equalsIgnoreCase("pilot")) {metaEntry.pilot = metaString.trim();}
					if (metaField.equalsIgnoreCase("coPilot")) {metaEntry.coPilots.add(metaString.trim());}
					if (metaField.equalsIgnoreCase("introSpeech")) {
						String[] splitIS = metaString.split("#");
						boolean formatError = false;
						if (splitIS != null && splitIS.length==2) {
							// Text validation
							if (splitIS[0].trim().length()==0) formatError = true;

							Pattern pattern = Pattern.compile("^\\d+(\\.\\d{3})$");
							boolean res1 = pattern.matcher(splitIS[1]).find();
							if (!res1) formatError = true;

							if (!formatError) metaEntry.introSpeeches.add(new DelayedText(splitIS[0].trim(), splitIS[1].trim()));

							// Error?
							if (formatError) {
								return new ErrorMessage("Wrong format for introSpeech:\n\n" + line);
							}
						} else {
							metaEntry.introSpeeches.add(new DelayedText(metaString.trim(), "0.000"));
						}
					}
					if (metaField.equalsIgnoreCase("poiSpeech")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.poiSpeech = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("poiSpeechBefore")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.poiSpeechBefore = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("finishedEntry")) {
						String[] splitFE = metaString.split("#");
						boolean formatError = false;
						if (splitFE!= null && splitFE.length>=3) {
							// Text validation
							if (splitFE[0].trim().length()==0) formatError = true;

							// ID validation
							if (splitFE[1].trim().length()==0) formatError = true;

							if (splitFE.length>=3 && splitFE.length<=4) {
								Pattern pattern = Pattern.compile("^\\d+(\\.\\d{3})$");
								boolean res2 = pattern.matcher(splitFE[2]).find();
								if (!res2) formatError = true;
							} else if (splitFE.length==4) {
								Pattern pattern = Pattern.compile("^\\d+(\\.\\d{3})$");
								boolean res3 = pattern.matcher(splitFE[3]).find();
								if (!res3) formatError = true;
							} else {
								formatError = true;
							}

							if (!formatError) {
								if (!metaEntry.finishedEntries.containsKey(splitFE[1].trim())) {
									metaEntry.finishedEntries.put(splitFE[1].trim(), new ArrayList<>());
								}
								List<DelayedText> feList = metaEntry.finishedEntries.get(splitFE[1].trim());
								if (splitFE.length==3) {
									feList.add(new DelayedText(splitFE[0].trim(), splitFE[2].trim()));
								} else {
									feList.add(new DelayedText(splitFE[0].trim(), splitFE[2].trim(), splitFE[3].trim()));
								}
								metaEntry.finishedEntries.put(splitFE[1].trim(), feList);
							}
						} else {
							formatError = true;
						}

						// Error?
						if (formatError) {
							return new ErrorMessage("Wrong format for finishedEntry:\n\n" + line);
						}
					}
					if (metaField.startsWith("dialogEntry")) {
						DialogEntry de = new DialogEntry(metaName, metaField.trim(), metaString.trim());
						Message msgDE = de.handle();
						if (msgDE != null) {
							return msgDE;
						}
						metaEntry.dialogEntries.add(de);						
					}
					if (metaField.equalsIgnoreCase("activateTriggers") || metaField.equalsIgnoreCase("deactivateTriggers")) {
						String[] splitTR = metaString.split("#");
						if (splitTR!= null && splitTR.length==2) {
							boolean activate = metaField.equalsIgnoreCase("activateTriggers");
							metaEntry.toggleTriggers.put(splitTR[0].trim(), new ToggleTrigger(activate, "", splitTR[1].trim().split(",")));
						} else {
							return new ErrorMessage("Wrong format for " + metaField + ":\n\n" + line);
						}
					}
					if (metaField.equalsIgnoreCase("counterActivateTriggers") || metaField.equalsIgnoreCase("counterDeactivateTriggers")) {
						String[] splitTR = metaString.split("#");
						if (splitTR!= null && (splitTR.length==2 || splitTR.length==3)) {
							boolean activate = metaField.equalsIgnoreCase("counterActivateTriggers");
							String text = splitTR.length==3 ? splitTR[2].trim() : "";
							metaEntry.counterToggleTriggers.put(splitTR[0].trim(), new ToggleTrigger(activate, text, splitTR[1].trim().split(",")));

							String[] split1 = splitTR[0].split(",");
							for (String s : split1) {
								s = s.trim();

								if (!metaEntry.counterToggleTriggersCompanion.containsKey(s)) {
									metaEntry.counterToggleTriggersCompanion.put(s, new ArrayList<>());
								}
								List<String> compList = metaEntry.counterToggleTriggersCompanion.get(s);
								compList.add(splitTR[0]);
								metaEntry.counterToggleTriggersCompanion.put(s, compList);
							}
						} else {
							return new ErrorMessage("Wrong format for " + metaField + ":\n\n" + line);
						}
					}
					if (metaField.equalsIgnoreCase("simFile")) {metaEntry.simFile = metaString.trim();}
					if (metaField.equalsIgnoreCase("fuelPercentage")) {metaEntry.fuelPercentage = metaString.trim();}
					if (metaField.equalsIgnoreCase("parkingBrake")) {metaEntry.parkingBrake = metaString.trim();}
					if (metaField.equalsIgnoreCase("tailNumber")) {metaEntry.tailNumber = metaString.trim();}
					if (metaField.equalsIgnoreCase("airlineCallSign")) {metaEntry.airlineCallSign = metaString.trim();}
					if (metaField.equalsIgnoreCase("flightNumber")) {metaEntry.flightNumber = metaString.trim();}
					if (metaField.equalsIgnoreCase("appendHeavy")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.appendHeavy = val.equals("true") ? "True" : "False";
					}
					if (metaField.equalsIgnoreCase("showVfrMap")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.showVfrMap = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("showNavLog")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.showNavLog = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("enableRefueling")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.enableRefueling = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("enableAtc")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.enableAtc = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("enableChecklist")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.enableChecklist = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("enableObjectives")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.enableObjectives = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("requireEnginesOff")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.requireEnginesOff = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("requireBatteryOff")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.requireBatteryOff = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("requireAvionicsOff")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.requireAvionicsOff = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("useAGL")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.useAGL = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("useOneShotTriggers")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.useOneShotTriggers = val.equals("true") ? "True" : "";
					}
					if (metaField.equalsIgnoreCase("standardAirportExitAreaSideLength")) {
						String val = metaString.trim();
						Pattern pattern = Pattern.compile("^\\d+(\\.\\d{3})$");
						boolean resVal = pattern.matcher(val).find();
						if (!resVal) {
							return new ErrorMessage("Wrong format for standardAirportExitAreaSideLength:\n\n" + line);
						} else {						
							metaEntry.standardAirportExitAreaSideLength = val;
						}
					}
					if (metaField.equalsIgnoreCase("standardEnterAreaSideLength")) {
						String val = metaString.trim();
						Pattern pattern = Pattern.compile("^\\d+(\\.\\d{3})$");
						boolean resVal = pattern.matcher(val).find();
						if (!resVal) {
							return new ErrorMessage("Wrong format for standardEnterAreaSideLength:\n\n" + line);
						} else {						
							metaEntry.standardEnterAreaSideLength = val;
						}
					}
					if (metaField.equalsIgnoreCase("weather")) {metaEntry.weather = metaString.trim();}
					if (metaField.startsWith("failure") ||
							metaField.startsWith("altitudeFailure") ||
							metaField.startsWith("speedFailure") ||
							metaField.startsWith("altitudeSpeedFailure") || metaField.startsWith("altitudeAndSpeedFailure") ||
							metaField.startsWith("formulaFailure")) {
						// Failures
						Pattern patternArm = Pattern.compile("^[f][a][i][l][u][r][e]([A-Za-z]+)([\\d]+)=([\\d]+)[-]([\\d]+)$");
						Pattern patternFailExit = Pattern.compile("^[f][a][i][l][u][r][e][E][x][i][t]([A-Za-z]+)([\\d]+)=(.*)");
						Pattern patternFail = Pattern.compile("^[f][a][i][l][u][r][e]([A-Za-z]+)([\\d]+)=(.*)");
						Pattern patternAltitudeFail = Pattern.compile("^[a][l][t][i][t][u][d][e][F][a][i][l][u][r][e]([A-Za-z]+)([\\d]+)=(.*)");
						Pattern patternSpeedFail = Pattern.compile("^[s][p][e][e][d][F][a][i][l][u][r][e]([A-Za-z]+)([\\d]+)=(.*)");
						Pattern patternAltitudeSpeedFail1 = Pattern.compile("^[a][l][t][i][t][u][d][e][A][n][d][S][p][e][e][d][F][a][i][l][u][r][e]([A-Za-z]+)([\\d]+)=(.*)");
						Pattern patternAltitudeSpeedFail2 = Pattern.compile("^[a][l][t][i][t][u][d][e][S][p][e][e][d][F][a][i][l][u][r][e]([A-Za-z]+)([\\d]+)=(.*)");
						Pattern patternFormulaFail = Pattern.compile("^[f][o][r][m][u][l][a][F][a][i][l][u][r][e]([A-Za-z]+)([\\d]+)=(.*)");

						String system = "";
						String subIndex = "";
						String value = "";
						boolean exit = false;
						FailureEntryMode feMode = null;

						Matcher matcherFailureExit = patternFailExit.matcher(line);
						Matcher matcherFailure = patternFail.matcher(line);
						Matcher matcherArm = patternArm.matcher(line);
						Matcher matcherAltitudeFailure = patternAltitudeFail.matcher(line);
						Matcher matcherSpeedFailure = patternSpeedFail.matcher(line);
						Matcher matcherAltitudeSpeedFailure1 = patternAltitudeSpeedFail1.matcher(line);
						Matcher matcherAltitudeSpeedFailure2 = patternAltitudeSpeedFail2.matcher(line);
						Matcher matcherFormulaFailure = patternFormulaFail.matcher(line);
						if (matcherArm.find()) {
							system = matcherArm.group(1);
							subIndex = matcherArm.group(2);
							value = matcherArm.group(3) + "-" + matcherArm.group(4);
							feMode = FailureEntryMode.ARM;
						} else if (matcherFailureExit.find()) {
							system = matcherFailureExit.group(1);
							subIndex = matcherFailureExit.group(2);
							value = matcherFailureExit.group(3);
							exit = true;
							feMode = FailureEntryMode.AREA;
						} else if (matcherFailure.find()) {
							system = matcherFailure.group(1);
							subIndex = matcherFailure.group(2);
							value = matcherFailure.group(3);
							feMode = FailureEntryMode.AREA;
						} else if (matcherAltitudeFailure.find()) {
							system = matcherAltitudeFailure.group(1);
							subIndex = matcherAltitudeFailure.group(2);
							value = matcherAltitudeFailure.group(3);
							feMode = FailureEntryMode.ALTITUDE;
						} else if (matcherSpeedFailure.find()) {
							system = matcherSpeedFailure.group(1);
							subIndex = matcherSpeedFailure.group(2);
							value = matcherSpeedFailure.group(3);
							feMode = FailureEntryMode.SPEED;
						} else if (matcherAltitudeSpeedFailure1.find()) {
							system = matcherAltitudeSpeedFailure1.group(1);
							subIndex = matcherAltitudeSpeedFailure1.group(2);
							value = matcherAltitudeSpeedFailure1.group(3);
							feMode = FailureEntryMode.ALTITUDE_AND_SPEED;
						} else if (matcherAltitudeSpeedFailure2.find()) {
							system = matcherAltitudeSpeedFailure2.group(1);
							subIndex = matcherAltitudeSpeedFailure2.group(2);
							value = matcherAltitudeSpeedFailure2.group(3);
							feMode = FailureEntryMode.ALTITUDE_AND_SPEED;
						} else if (matcherFormulaFailure.find()) {
							system = matcherFormulaFailure.group(1);
							subIndex = matcherFormulaFailure.group(2);
							value = matcherFormulaFailure.group(3);
							feMode = FailureEntryMode.FORMULA;
						}

						if (!system.isEmpty()) {
							boolean wasFound = mSimData.systemsList.contains(system);

							if (wasFound) {
								FailureEntry fe = new FailureEntry(metaName, "Failure", value.trim(), system, subIndex, exit, feMode);
								Message msgFE = fe.handle();
								if (msgFE != null) {
									return msgFE;
								}
								metaEntry.failureEntries.add(fe);
							} else {
								return new ErrorMessage("Could not find failing system: " + system);
							}
						}
					}

					if (metaField.startsWith("missionFailure")) {
						MissionFailureEntry mfe = new MissionFailureEntry(metaName, metaField.trim(), metaString.trim());
						Message msgMFE = mfe.handle();
						if (msgMFE != null) {
							return msgMFE;
						}
						metaEntry.missionFailures.add(mfe);
					}
					if (metaField.equalsIgnoreCase("flapsHandle")) {metaEntry.flapsHandle = metaString.trim();}
					if (metaField.equalsIgnoreCase("leftFlap")) {metaEntry.leftFlap = metaString.trim();}
					if (metaField.equalsIgnoreCase("rightFlap")) {metaEntry.rightFlap = metaString.trim();}
					if (metaField.equalsIgnoreCase("elevatorTrim")) {metaEntry.elevatorTrim = metaString.trim();}

					// Landing challenge
					if (metaField.equalsIgnoreCase("missionType")) {metaEntry.missionType = metaString.trim();}
					if (metaField.equalsIgnoreCase("challengeType")) {metaEntry.challengeType = metaString.trim();}
					if (metaField.equalsIgnoreCase("velocity")) {metaEntry.velocity = metaString.trim();}
					if (metaField.equalsIgnoreCase("multiPlayer")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.multiPlayer = val.equals("true") ? "1" : "0";
					}
					if (metaField.equalsIgnoreCase("noGear")) {
						String val = metaString.trim().toLowerCase();
						metaEntry.noGear = val.equals("true") ? "True" : "";
					}
				} else if (split.length == WP_SPLIT_LEN) {
					MissionEntry entry = new MissionEntry();				

					entry.id = split[0].trim();
					entry.runway = split[1].trim();
					if (entry.runway.startsWith("0")) {
						return new ErrorMessage("Runways must not have with leading zeros: " + entry.runway);
					}
					if (entry.runway.contains(".")) {
						int sp = entry.runway.indexOf(".");
						entry.runway = entry.runway.substring(0, sp);
					}
					if (entry.runway.contains(",")) {
						int sp = entry.runway.indexOf(",");
						entry.runway = entry.runway.substring(0, sp);
					}
					entry.name = split[2].trim();
					entry.type2 = split[3].trim().equals("A") ? WpType.AIRPORT : WpType.USER;

					if (entry.type2.equals(WpType.USER)) {
						count_POI++;
						entry.id = "POI" + multiCount(count_POI, 0);

						if (count_AIRPORT > 0*26 && count_AIRPORT <= 1*26) {
							char c=(char)((count_AIRPORT-1)+'A');
							entry.region = "!" + String.valueOf(c);
						} else if (count_AIRPORT > 1*26 && count_AIRPORT <= 2*26) {
							char c=(char)((count_AIRPORT-26-1)+'A');
							entry.region = "?" + String.valueOf(c);
						} else {
							entry.region = multiCount(count_AIRPORT-53, 1);
						}
					} else if (entry.type2.equals(WpType.AIRPORT)) {
						count_AIRPORT++;
					}

					// Too many airports?? Regions are limiting!
					if (count_AIRPORT > MAX_COUNT_LEGS) {
						return new ErrorMessage("Maximum " + MAX_COUNT_LEGS + " legs are allowed!");
					}

					Message msgLatLong = entry.setLatlon(split[4].trim());
					if (msgLatLong != null) {
						return msgLatLong;
					}

					Message msgAlt = entry.setAlt(split[5].trim());
					if (msgAlt != null) {
						return msgAlt;
					}

					entry.wpInfo = split[6].trim();
					entry.legText = split[7].trim();
					entry.subLegText = split[8].trim();

					// Replace bad chars in legText and subLegText
					entry.legText = entry.legText.replace("–", "-");
					entry.subLegText = entry.subLegText.replace("–", "-");

					entry.legText = entry.legText.replace("&", "&amp;");
					entry.subLegText = entry.subLegText.replace("&", "&amp;");

					entry.legText = entry.legText.replace("'", "&#39;");
					entry.subLegText = entry.subLegText.replace("'", "&#39;");

					entry.legText = entry.legText.replace("\"", "&quot;");
					entry.subLegText = entry.subLegText.replace("\"", "&quot;");

					entry.legText = entry.legText.replace("\r\n", "<br>");
					entry.subLegText = entry.subLegText.replace("\r\n", "<br>");

					entry.legText = entry.legText.replace("\n", "<br>");
					entry.subLegText = entry.subLegText.replace("\n", "<br>");

					entries.add(entry);
				} else if (split.length == WP_EXTRA_SPLIT_LEN) {
					MissionEntry entry = new MissionEntry();				

					entry.id = split[0].trim();

					entry.runway = split[1].trim();
					if (entry.runway.startsWith("0")) {
						return new ErrorMessage("Runways must not have with leading zeros: " + entry.runway);
					}
					int foundRunwayDecimal = entry.runway.indexOf(".");
					if (foundRunwayDecimal>=0) {
						entry.runway = entry.runway.substring(0, foundRunwayDecimal);
					}
					entry.name = split[2].trim();
					entry.type2 = split[3].trim().equals("A") ? WpType.AIRPORT : WpType.USER;

					if (entry.type2.equals(WpType.USER)) {
						count_POI++;
						entry.id = "POI" + multiCount(count_POI, 0);

						if (count_AIRPORT > 0*26 && count_AIRPORT <= 1*26) {
							char c=(char)((count_AIRPORT-1)+'A');
							entry.region = "!" + String.valueOf(c);
						} else if (count_AIRPORT > 1*26 && count_AIRPORT <= 2*26) {
							char c=(char)((count_AIRPORT-26-1)+'A');
							entry.region = "?" + String.valueOf(c);
						} else {
							entry.region = multiCount(count_AIRPORT-53, 1);
						}
					} else if (entry.type2.equals(WpType.AIRPORT)) {
						count_AIRPORT++;
					}

					// Too many airports?? Regions are limiting!
					if (count_AIRPORT > MAX_COUNT_LEGS) {
						return new ErrorMessage("Maximum " + MAX_COUNT_LEGS + " legs are allowed!");
					}

					Message msgLatLong = entry.setLatlon(split[4].trim());
					if (msgLatLong != null) {
						return msgLatLong;
					}

					Message msgAlt = entry.setAlt(split[5].trim().replace(",", "."));
					if (msgAlt != null) {
						return msgAlt;
					}

					entry.wpInfo = "";
					for (int i=6; i<14; i++) {
						entry.wpInfo += split[i].trim().replace(",", ".");

						// Remove decimals from certain values
						if (i<11) {
							int foundWpDecimal = entry.wpInfo.indexOf(".");
							if (foundWpDecimal>=0) {
								entry.wpInfo = entry.wpInfo.substring(0, foundWpDecimal);
							}
						}

						if (i<13)  entry.wpInfo += ", ";
					}

					entry.legText = split[14].trim();
					entry.subLegText = split[15].trim();

					// Replace bad chars in legText and subLegText
					entry.legText = entry.legText.replace("–", "-");
					entry.subLegText = entry.subLegText.replace("–", "-");

					entry.legText = entry.legText.replace("&", "&amp;");
					entry.subLegText = entry.subLegText.replace("&", "&amp;");

					entry.legText = entry.legText.replace("'", "&#39;");
					entry.subLegText = entry.subLegText.replace("'", "&#39;");

					entry.legText = entry.legText.replace("\"", "&quot;");
					entry.subLegText = entry.subLegText.replace("\"", "&quot;");

					entry.legText = entry.legText.replace("\r\n", "<br>");
					entry.subLegText = entry.subLegText.replace("\r\n", "<br>");

					entry.legText = entry.legText.replace("\n", "<br>");
					entry.subLegText = entry.subLegText.replace("\n", "<br>");

					entries.add(entry);
				} else if (split.length == WP_LOCALIZATION_LEN) {
					Localization localization = new Localization();
					localization.id = split[0].trim();
					localization.language = split[1].trim();
					localization.value1 = split[2].trim();
					localization.value2 = split[3].trim();
					localization.value3 = split[4].trim();

					// Replace bad chars in legText and subLegText
					localization.value3 = localization.value3.replace("–", "-");
					localization.value3 = localization.value3.replace("&", "&amp;");
					localization.value3 = localization.value3.replace("'", "&#39;");
					localization.value3 = localization.value3.replace("\"", "&quot;");
					localization.value3 = localization.value3.replace("\r\n", "<br>");
					localization.value3 = localization.value3.replace("\n", "<br>");

					// Format check! We know that languages must have a - char and be len = 5
					if (!(localization.language.contains("-") && localization.language.length() == 5)) {
						continue;
					}

					if (localization.id.equalsIgnoreCase("meta")) {
						metaEntry.localizations.add(localization);
					} else {
						boolean setLocalization = false;
						// Airports first
						for (MissionEntry me : entries) {
							if (me.id.equals(localization.id)) {
								// Handle mission where the same airport is used more than once
								boolean foundLocalization = false;
								for (Localization lc : me.localizations) {
									if (lc.language.equals(localization.language)) {
										foundLocalization = true;
										break;
									}
								}

								if (!foundLocalization) {
									me.localizations.add(localization);
									setLocalization = true;
									break;
								} else {
									continue;
								}
							}
						}

						// Then POIS if not set for an airport
						if (!setLocalization) {
							for (MissionEntry me : entries) {
								if (me.type2.equals(WpType.USER)) {
									boolean foundLocalization = false;
									for (Localization lc : me.localizations) {
										if (lc.language.equals(localization.language)) {
											foundLocalization = true;
											break;
										}
									}

									if (!foundLocalization) {
										me.localizations.add(localization);
										break;
									} else {
										continue;
									}
								}
							}
						}
					}
				} else if (split.length == WP_LANDING_LEN) {
					MissionEntry entry = new MissionEntry();				

					entry.id = split[0].trim();
					entry.runway = split[1].trim();
					if (entry.runway.startsWith("0")) {
						return new ErrorMessage("Runways must not have with leading zeros: " + entry.runway);
					}
					if (entry.runway.contains(".")) {
						int sp = entry.runway.indexOf(".");
						entry.runway = entry.runway.substring(0, sp);
					}
					if (entry.runway.contains(",")) {
						int sp = entry.runway.indexOf(",");
						entry.runway = entry.runway.substring(0, sp);
					}
					entry.name = split[2].trim();
					entry.type2 = split[3].trim().equals("A") ? WpType.AIRPORT : WpType.USER;

					if (entry.type2.equals(WpType.USER)) {
						count_POI++;
						entry.id = "POI" + multiCount(count_POI, 0);

						if (count_AIRPORT > 0*26 && count_AIRPORT <= 1*26) {
							char c=(char)((count_AIRPORT-1)+'A');
							entry.region = "!" + String.valueOf(c);
						} else if (count_AIRPORT > 1*26 && count_AIRPORT <= 2*26) {
							char c=(char)((count_AIRPORT-26-1)+'A');
							entry.region = "?" + String.valueOf(c);
						} else {
							entry.region = multiCount(count_AIRPORT-53, 1);
						}
					} else if (entry.type2.equals(WpType.AIRPORT)) {
						count_AIRPORT++;
					}

					// Too many airports?? Regions are limiting!
					if (count_AIRPORT > MAX_COUNT_LEGS) {
						return new ErrorMessage("Maximum " + MAX_COUNT_LEGS + " legs are allowed!");
					}

					Message msgLatLong = entry.setLatlon(split[4].trim());
					if (msgLatLong != null) {
						return msgLatLong;
					}

					Message msgAlt = entry.setAlt(split[5].trim());
					if (msgAlt != null) {
						return msgAlt;
					}

					entries.add(entry);
				} else {
					if (count_META > 0) {
						return new ErrorMessage("Wrong number of fields on the line! " + split.length + ".\n\n" + line);
					} else {
						return new ErrorMessage("Did you REALLY choose an appropriate input file?");
					}
				}
			}
		} catch (IOException e) {
			return new ErrorMessage("Input files is not UTF-8 encoded?");
		}

		if (count_META < META_REQUIRED_ITEMS) {
			// Which are missing??
			return new ErrorMessage("Missing metadata fields:\n" + metaEntry.requiredFields.stream().map(Object::toString).collect(Collectors.joining(System.lineSeparator())));
		} else if (count_META > META_REQUIRED_ITEMS) {
			return new ErrorMessage("Too many metadata entries! Doublet fields?");
		}

		// Max POI count reached?
		if (count_POI>MAX_COUNT_POIS) {
			return new ErrorMessage("Too many POIs! Maximum count is " + MAX_COUNT_POIS + ".");
		}

		// Mission type handling
		if (metaEntry.missionType.toLowerCase().startsWith("land")) {
			metaEntry.missionType = "land";
		} else {
			metaEntry.missionType = "bush";
		}

		File receptFile = new File(recept_file);
		String pathRoot = receptFile.getParentFile().getAbsolutePath();
		String pathCurrent = System.getProperty("user.dir");

		String recept_fileXML = "##PATH_DIR##" + File.separator + "templates" + File.separator + metaEntry.missionType + "_template.xml";
		if (metaEntry.missionType.equals("land")) {
			String recept_landing_private = "##PATH_DIR##" + File.separator + "templates" + File.separator + MetaEntry.LandingChallenge_PrivateTemplate + ".xml";
			String recept_landing_nogear = "##PATH_DIR##" + File.separator + "templates" + File.separator + MetaEntry.LandingChallenge_NoGearTemplate + ".xml";

			if (metaEntry.noGear.isEmpty()) {
				recept_fileXML = recept_landing_private;
			} else {
				recept_fileXML = recept_landing_nogear;
			}
		}
		String recept_root = "##PATH_DIR##";
		String recept_fileFLT = "##PATH_DIR##" + File.separator + "templates" + File.separator + metaEntry.missionType + "_template.FLT";
		String recept_filePLN = "##PATH_DIR##" + File.separator + "templates" + File.separator + metaEntry.missionType + "_template.PLN";
		String recept_fileLOC = "##PATH_DIR##" + File.separator + "templates" + File.separator + "template.loc";
		String recept_project = "##PATH_DIR##" + File.separator + "templates" + File.separator + "project.xml";
		String recept_package = "##PATH_DIR##" + File.separator + "templates" + File.separator + "package.xml";
		String recept_overview = "##PATH_DIR##" + File.separator + "templates" + File.separator + "Overview.htm";
		String recept_weather = "##PATH_DIR##" + File.separator + "Weather.WPR";

		String[] recept_files_bush = new String[] {
				recept_root,
				recept_fileXML,
				recept_fileFLT,
				recept_filePLN,
				recept_fileLOC,
				recept_project,
				recept_package,
				recept_weather
		};

		String[] recept_files_land = new String[] {
				recept_root,
				recept_fileXML,
				recept_fileFLT,
				recept_fileLOC,
				recept_project,
				recept_package,
				recept_overview,
				recept_weather
		};

		String projectPath = pathRoot + File.separator + "output" + File.separator + metaEntry.project;
		if (recept_file.contains("\\samples\\")) {
			projectPath = pathCurrent + File.separator + "output" + File.separator + metaEntry.project;
		}
		String packagesPath = projectPath + File.separator + "PackageDefinitions";
		String fourFilesPath = projectPath + File.separator + "source";
		String imagesPath = fourFilesPath + File.separator + "images";
		String soundPath = fourFilesPath + File.separator + "sound";
		String contentInfoPath = packagesPath + File.separator + "ContentInfo";
		String compiledPath = projectPath + File.separator + "Packages";

		if (mode >= 3 && mode <= 6) {
			if (mode == 3) return mFileHandling.showFolder(projectPath);
			if (mode == 4) return mFileHandling.showFolder(imagesPath);
			if (mode == 5) return mFileHandling.showFolder(soundPath);
			if (mode == 6) return mFileHandling.showFolder(compiledPath);
		}

		String outFileXML = fourFilesPath +  File.separator + metaEntry.project + ".xml";
		String outFileFLT = fourFilesPath + File.separator + metaEntry.project + ".FLT";
		String outFilePLN = fourFilesPath + File.separator + metaEntry.project + ".PLN";
		String outFileLOC = fourFilesPath + File.separator + metaEntry.project + ".loc";
		String outFileHTM = fourFilesPath + File.separator + "Overview.htm";
		String outFileProject = projectPath + File.separator + metaEntry.project + "project.xml";
		String outFilePackage = packagesPath + File.separator + metaEntry.project + ".xml";
		String outFilePreview = projectPath + File.separator + "preview.html";
		String outFileJSON = projectPath + File.separator + "preview.geojson";

		// Recept files
		int rf_mode = 0;
		while (true) {
			String foundNot = "";

			String[] recept_files = null;
			if (metaEntry.missionType.equals("bush")) {
				recept_files = recept_files_bush;
			} else {
				recept_files = recept_files_land;
			}

			for (String rf : recept_files) {
				if (rf_mode == 0) {
					String rfPath = rf.replace("##PATH_DIR##", pathRoot);
					if (!new File(rfPath).exists()) {
						foundNot = rfPath;
					}
				} else {
					String rfPath = rf.replace("##PATH_DIR##", pathCurrent);
					if (!new File(rfPath).exists()) {
						return new ErrorMessage("Template file not found!\n\n" + rfPath);
					}
				}
			}

			if (foundNot.isEmpty()) {
				if (metaEntry.missionType.equals("bush")) {
					recept_root = recept_files[0].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_fileXML = recept_files[1].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_fileFLT = recept_files[2].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_filePLN = recept_files[3].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_fileLOC = recept_files[4].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_project = recept_files[5].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_package = recept_files[6].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_weather = recept_files[7].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
				} else {
					recept_root = recept_files[0].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_fileXML = recept_files[1].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_fileFLT = recept_files[2].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_fileLOC = recept_files[3].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_project = recept_files[4].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_package = recept_files[5].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_overview = recept_files[6].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
					recept_weather = recept_files[7].replace("##PATH_DIR##", rf_mode == 0 ? pathRoot : pathCurrent);
				}
				break;
			}

			if (++rf_mode>1) {
				break;
			}
		}

		// CHECKING CHECKING CHECKING CHECKING!!!
		if (metaEntry.missionType.equals("bush")) {
			// Make sure there are POIs in every legs
			boolean foundA = false;
			boolean foundU = true;
			for (MissionEntry me : entries) {
				if (me.type2.equals(WpType.AIRPORT)) {
					if (foundA && !foundU) {
						return new ErrorMessage("At least one POI per leg is required.");
					}

					foundA = true;
					foundU = false;
				} else {
					foundU = true;
				}
			}

			// Check if airports are present more than once and requireEnginesOff etc is not set
			List<String> airportICAOs = new ArrayList<>();
			for (MissionEntry me : entries) {
				if (me.type2.equals(WpType.AIRPORT)) {
					airportICAOs.add(me.id);
				}
			}
			Set<String> checkSet = new HashSet<>();
			checkSet.addAll(airportICAOs);
			if (airportICAOs.size() != checkSet.size()) {
				mMultipleSameAirports = true;
				if (metaEntry.requireEnginesOff.isEmpty() &&
						metaEntry.requireBatteryOff.isEmpty() &&
						metaEntry.requireAvionicsOff.isEmpty()) {
					return new ErrorMessage("Same airports appear more than once but none of the fields\nrequiresEnginesOff/requireBatteryOff/requireAvionicsOff\nare set to True!");
				}
			}
		} else {
			int count_CUSTS = 0;
			int count_APS = 0;
			MissionEntry lastME = null;
			// Make sure there is only one airport
			for (MissionEntry me : entries) {
				if (me.type2.equals(WpType.AIRPORT)) {
					count_APS++;
				} else {
					count_CUSTS++;
				}
				lastME = me;
			}
			if (count_APS<1) {
				return new ErrorMessage("Missing airport to land at.");
			} else if (count_APS>1) {
				return new ErrorMessage("Only one airport please.");
			}

			// The airport should be the last entry
			if (!lastME.type2.equals(WpType.AIRPORT)) {
				return new ErrorMessage("The airport must be the last waypoint!");
			}

			// There should be at least one waypoint before the airport
			if (count_CUSTS<1) {
				return new ErrorMessage("At least one waypoint before the airport is needed.");
			}

			// There must be a runway set for the landing airport
			if (lastME.runway.isEmpty()) {
				return new ErrorMessage("Please set a runway for the airport.");
			}

			// Duplicate the first waypoint?
			if (!entries.get(0).latlon.equals(entries.get(1).latlon)) {
				MissionEntry clone = new MissionEntry(entries.get(0));
				entries.add(0, clone);
			}
		}

		// If a runway is set, then it must be correct
		if (!entries.get(0).runway.isEmpty()) {
			try {
				int nbr = Integer.parseInt(entries.get(0).runway);
				if (nbr<1 || nbr>36) {
					return new ErrorMessage("Invalid runway number: " + entries.get(0).runway);
				}
			} catch (NumberFormatException nfe) {
			}
		}
		if (!entries.get(entries.size()-1).runway.isEmpty()) {
			try {
				int nbr = Integer.parseInt(entries.get(entries.size()-1).runway);
				if (nbr<1 || nbr>36) {
					return new ErrorMessage("Invalid runway number: " + entries.get(entries.size()-1).runway);
				}
			} catch (NumberFormatException nfe) {
			}
		}

		// Create directories
		File outputDir = new File(fourFilesPath);
		outputDir.mkdirs();

		File imagesDir = new File(imagesPath);
		imagesDir.mkdirs();

		File soundsDir = new File(soundPath);
		soundsDir.mkdirs();

		File packagesDir = new File(packagesPath);
		packagesDir.mkdirs();

		File contentInfoDir = new File(contentInfoPath);
		contentInfoDir.mkdirs();

		mSounds = new HashSet<>();
		mCopiedSounds = new HashSet<>();

		// Copy weather file
		try {
			Files.copy(new File(recept_weather).toPath(), new File(fourFilesPath + File.separator + "Weather.WPR").toPath());
		} catch (IOException e) {
		}

		// Copy WAV files to the sound folder
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get(recept_root), "*.WAV")) {
			dirStream.forEach(path -> {
				try {
					String toFile = soundPath + File.separator + path.getFileName().toString();
					Files.copy(path, new File(toFile).toPath());
					mCopiedSounds.add(toFile);
				} catch (IOException e) {
				}
			});
		} catch (IOException e) {
		}

		// Unique airports set?
		Map<String, Integer> uniqueMap = new HashMap<>();
		Map<String, Integer> uniqueMapCounter = new HashMap<>();
		int count_UNIQUE = 0;
		for (MissionEntry entry : entries) {
			if (entry.type2.equals(WpType.AIRPORT)) {
				if (count_UNIQUE>0) {
					if (!uniqueMap.containsKey(entry.id)) {
						uniqueMap.put(entry.id, 0);
						uniqueMapCounter.put(entry.id, 0);
					} else {
						int uniqueCount = uniqueMap.get(entry.id);
						uniqueMap.put(entry.id, ++uniqueCount);
					}
				}
				count_UNIQUE++;
			}
		}
		count_UNIQUE = 0;
		for (MissionEntry entry : entries) {
			if (entry.type2.equals(WpType.AIRPORT)) {
				if (count_UNIQUE>0) {
					if (uniqueMap.get(entry.id) == 0) {
						entry.uniqueId =  entry.id;
					} else {
						int uniqueCount = uniqueMapCounter.get(entry.id);
						entry.uniqueId =  entry.id + "_" + uniqueMapCounter.get(entry.id);
						uniqueMapCounter.put(entry.id, ++uniqueCount);
					}
				}
				count_UNIQUE++;
			}
		}

		// Handle multiple same airports by settings regions on all airports too.
		if (mMultipleSameAirports) {
			for (int i=0; i<entries.size(); i++) {
				if (entries.get(i).type2.equals(WpType.AIRPORT) && i < entries.size()-1) {
					if (entries.get(i+1).type2.equals(WpType.USER)) {
						entries.get(i).region = entries.get(i+1).region;
					}
				}
			}
		}

		if (metaEntry.missionType.equals("bush")) {
			// Create images for bush missions
			int count_ENTRY = 0;
			for (MissionEntry entry : entries) {
				if (entry.type2.equals(WpType.AIRPORT)) {
					if (count_ENTRY>0) {
						// Unique airport?
						String id = entry.id;
						if (!metaEntry.uniqueApImages.isEmpty()) {
							id = entry.uniqueId;
						}

						String imageFile = imagesPath + File.separator + id;
						Message msgJPG = mImageHandling.generateImage(new File(imageFile + ".jpg"), 1920, 1080, "jpg", "LEG - " + entry.id, Font.PLAIN, 1.0d);
						if (msgJPG != null) {
							return msgJPG;
						}
						Message msgPNG = mImageHandling.generateImage(new File(imageFile + ".png"), 1200, 800, "png", "NAVLOG - " + entry.id, Font.PLAIN, 1.0d);
						if (msgPNG != null) {
							return msgPNG;
						}
					}
					count_ENTRY++;
				}
			}
		} else {
			String imageFileBriefing = imagesPath + File.separator + "Briefing_Screen.jpg";
			Message msgJPGBriefing = mImageHandling.generateImage(new File(imageFileBriefing), 3840, 2160, "jpg", "Briefing", Font.PLAIN, 1.0d);
			if (msgJPGBriefing != null) {
				return msgJPGBriefing;
			}
		}

		String imageFile1 = imagesPath + File.separator + "Loading_Screen.jpg";
		Message msgJPG1 = mImageHandling.generateImage(new File(imageFile1), 3840, 2160, "jpg", "Loading", Font.PLAIN, 1.0d);
		if (msgJPG1 != null) {
			return msgJPG1;
		}
		String imageFile2 = imagesPath + File.separator + "Activity_Widget.jpg";
		Message msgJPG2 = mImageHandling.generateImage(new File(imageFile2), 816, 626, "jpg", "Generated|in|BushMissionGen v" + BushMissionGen.VERSION, Font.PLAIN, 1.0d);
		if (msgJPG2 != null) {
			return msgJPG2;
		}
		String imageFile3 = contentInfoPath + File.separator + "Thumbnail.jpg";
		Message msgJPG3 = mImageHandling.generateImage(new File(imageFile3), 412, 170, "jpg", "Generated|in|BushMissionGen v" + BushMissionGen.VERSION, Font.BOLD, 1.5d);
		if (msgJPG3 != null) {
			return msgJPG3;
		}

		if (mode == 1) {
			// Did the user add POI images after generating??
			if (mPOIs != null) {
				Pattern patternPNG = Pattern.compile("^[P][O][I].*\\.[p][n][g]");
				File[] outputPNGs = new File(imagesPath).listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						Matcher matcherPNG = patternPNG.matcher(name);
						return matcherPNG.find();
					}
				});
				if (outputPNGs.length != mPOIs.intValue()) {
					return new ErrorMessage("New or deleted POI images have been detected!\n\nPlease generate you mission again and press Compile.");
				}
			}

			// Compile and leave
			return compileMission(metaEntry, outFileProject, projectPath, metaEntry.sdkPath);
		}

		// Create preview HTML
		mDoc = Jsoup.parse("<html></html>");
		mDoc.head().appendText("NAVLOG PREVIEW!");
		mDoc.head().appendElement("br");
		mDoc.head().appendElement("br");
		mDoc.head().appendText("Open the file preview.geojson manually (Open --> File) after pressing the link below!");
		mDoc.head().appendElement("br");
		Element anchor = mDoc.head().appendElement("a");
		anchor.appendText("LINK");
		anchor.attr("href", "http://geojson.io/");
		anchor.attr("target", "blank");
		mDoc.head().appendElement("br");
		mDoc.body().appendElement("div");

		// JSON
		mGeoJSON.reset();

		// Reset POI images count
		mPOIs = 0;

		// Load resource files
		FLT_AIRLINER_BUSH = mFileHandling.readUrlToString("FLT/AIRLINER_BUSH.txt", StandardCharsets.UTF_8);
		FLT_AIRLINER_LAND = mFileHandling.readUrlToString("FLT/AIRLINER_LAND.txt", StandardCharsets.UTF_8);

		LOC_STRING = mFileHandling.readUrlToString("LOC/STRING.txt", StandardCharsets.UTF_8);
		LOC_LANGUAGE = mFileHandling.readUrlToString("LOC/LANGUAGE.txt", StandardCharsets.UTF_8);

		PLN_ATCWAYPOINTS_V1 = mFileHandling.readUrlToString("PLN/ATCWAYPOINTS_V1.txt", StandardCharsets.UTF_8);
		PLN_ATCWAYPOINTS_V2 = mFileHandling.readUrlToString("PLN/ATCWAYPOINTS_V2.txt", StandardCharsets.UTF_8);
		PLN_ATCWAYPOINTS_V3 = mFileHandling.readUrlToString("PLN/ATCWAYPOINTS_V3.txt", StandardCharsets.UTF_8);

		XML_ALTITUDESPEEDTRIGGER = mFileHandling.readUrlToString("XML/ALTITUDESPEEDTRIGGER.txt", Charset.forName("windows-1252"));
		XML_ALTITUDETRIGGER = mFileHandling.readUrlToString("XML/ALTITUDETRIGGER.txt", Charset.forName("windows-1252"));
		XML_CALC = mFileHandling.readUrlToString("XML/CALC.txt", Charset.forName("windows-1252"));
		XML_COUNTACTION = mFileHandling.readUrlToString("XML/COUNTACTION.txt", Charset.forName("windows-1252"));
		XML_COUNTERTRIGGER = mFileHandling.readUrlToString("XML/COUNTERTRIGGER.txt", Charset.forName("windows-1252"));
		XML_DIALOGACTION = mFileHandling.readUrlToString("XML/DIALOGACTION.txt", Charset.forName("windows-1252"));
		XML_DIALOGS = mFileHandling.readUrlToString("XML/DIALOGS.txt", Charset.forName("windows-1252"));
		XML_DIALOGSEXIT = mFileHandling.readUrlToString("XML/DIALOGSEXIT.txt", Charset.forName("windows-1252"));
		XML_FAILUREACTION = mFileHandling.readUrlToString("XML/FAILUREACTION.txt", Charset.forName("windows-1252"));
		XML_FAILURES = mFileHandling.readUrlToString("XML/FAILURES.txt", Charset.forName("windows-1252"));
		XML_FAILURESEXIT = mFileHandling.readUrlToString("XML/FAILURESEXIT.txt", Charset.forName("windows-1252"));
		XML_FORMULATRIGGER = mFileHandling.readUrlToString("XML/FORMULATRIGGER.txt", Charset.forName("windows-1252"));
		XML_GOAL = mFileHandling.readUrlToString("XML/GOAL.txt", Charset.forName("windows-1252"));
		XML_INTRODIALOG = mFileHandling.readUrlToString("XML/INTRODIALOG.txt", Charset.forName("windows-1252"));
		XML_LANDEDDIALOGS = mFileHandling.readUrlToString("XML/LANDEDDIALOGS.txt", Charset.forName("windows-1252"));
		XML_LANDEDTRIGGER = mFileHandling.readUrlToString("XML/LANDEDTRIGGER.txt", Charset.forName("windows-1252"));
		XML_LEG = mFileHandling.readUrlToString("XML/LEG.txt", Charset.forName("windows-1252"));
		XML_OBJECTIVE = mFileHandling.readUrlToString("XML/OBJECTIVE.txt", Charset.forName("windows-1252"));
		XML_OBJECTACTIVATIONACTION = mFileHandling.readUrlToString("XML/OBJECTACTIVATIONACTION.txt", Charset.forName("windows-1252"));
		XML_PROXIMITYTRIGGER = mFileHandling.readUrlToString("XML/PROXIMITYTRIGGER.txt", Charset.forName("windows-1252"));
		XML_RESETACTION = mFileHandling.readUrlToString("XML/RESETACTION.txt", Charset.forName("windows-1252"));
		XML_SPEEDTRIGGER = mFileHandling.readUrlToString("XML/SPEEDTRIGGER.txt", Charset.forName("windows-1252"));
		XML_SUBLEG = mFileHandling.readUrlToString("XML/SUBLEG.txt", Charset.forName("windows-1252"));
		XML_TIMERTRIGGER = mFileHandling.readUrlToString("XML/TIMERTRIGGER.txt", Charset.forName("windows-1252"));

		// Create output files
		Message msgLOC = handleLOC(metaEntry, entries, recept_fileLOC, outFileLOC); // MUST BE FIRST!
		if (msgLOC != null) {
			return msgLOC;
		}
		if (metaEntry.missionType.equals("bush")) {
			Message msgXML = handleXML(metaEntry, entries, recept_fileXML, outFileXML, pathRoot, imagesPath);
			if (msgXML != null) {
				return msgXML;
			}
			Message msgFLT = handleFLT(metaEntry, entries, recept_fileFLT, outFileFLT);
			if (msgFLT != null) {
				return msgFLT;
			}
			Message msgPLN = handlePLN(metaEntry, entries, recept_filePLN, outFilePLN);
			if (msgPLN != null) {
				return msgPLN;
			}

			// Write preview HTML to file
			Charset cs = StandardCharsets.UTF_8;
			Message msg = mFileHandling.writeStringToFile(outFilePreview, mDoc.toString(), cs);
			if (msg != null) {
				return msg;
			}
			mSavedPreviewFile = outFilePreview;

			// JSON
			mGeoJSON.finish();
			Message msgJSON = mFileHandling.writeStringToFile(outFileJSON, mGeoJSON.toString(), cs);
			if (msgJSON != null) {
				return msgJSON;
			}
		} else {
			Message msgXML = handleLandXML(metaEntry, entries, recept_fileXML, outFileXML);
			if (msgXML != null) {
				return msgXML;
			}
			Message msgFLT = handleFLT(metaEntry, entries, recept_fileFLT, outFileFLT);
			if (msgFLT != null) {
				return msgFLT;
			}
			Message msgOverview = handleHTM(metaEntry, entries, recept_overview, outFileHTM);
			if (msgOverview != null) {
				return msgOverview;
			}
		}

		Message msgProject = handleProject(metaEntry, entries, recept_project, outFileProject);
		if (msgProject != null) {
			return msgProject;
		}
		Message msgPackage = handlPackage(metaEntry, entries, recept_package, outFilePackage);
		if (msgPackage != null) {
			return msgPackage;
		}

		// Check if sound files are in the sound folder
		if (!mSounds.isEmpty()) {
			StringBuffer sb_Sounds = new StringBuffer();
			List<String> list = new ArrayList<String>(mSounds);
			Collections.sort(list);
			for (String sound :  list) {
				String soundString = soundPath + File.separator + sound;
				File soundFile = new File(soundString);

				if (!soundFile.exists()) {
					sb_Sounds.append(soundFile + System.lineSeparator());
				}
			}

			// Show a dialog if missing WAVs
			if (sb_Sounds.length()>0) {
				Message msg = new InfoMessage("The following sound file were not found on disk:\n\n" + sb_Sounds.toString() + "\nPlease add those before compiling!");
				JOptionPane.showMessageDialog(mGUI, msg.getMessage(), "Generate", JOptionPane.INFORMATION_MESSAGE);
			}
		}

		// Check if founds were copied
		if (!mCopiedSounds.isEmpty()) {
			StringBuffer sb_Sounds = new StringBuffer();
			List<String> list = new ArrayList<String>(mCopiedSounds);
			Collections.sort(list);
			for (String sound :  list) {
				sb_Sounds.append(sound + System.lineSeparator());
			}

			// Show a dialog
			if (sb_Sounds.length()>0) {
				Message msg = new InfoMessage("The following sound file copied to the output folder:\n\n" + sb_Sounds.toString());
				JOptionPane.showMessageDialog(mGUI, msg.getMessage(), "Generate", JOptionPane.INFORMATION_MESSAGE);
			}
		}

		return null;
	}

	public String multiCount(int count, int mode) {
		if (mode == 0) {
			if (count>99) {
				int base = count-100;
				int base1 = base / 36;
				int base2 = base % 36;
				return numberToChar(10+base1) + numberToChar(base2);
			} else {
				return String.valueOf(count);
			}
		} else {
			int base = count;
			int base1 = base / 26;
			int base2 = 10 + (base % 26);
			return numberToChar(10+base1) + numberToChar(base2);
		}
	}

	private String numberToChar(int nbr) {
		if (nbr<=9) {
			return String.valueOf(nbr);
		} else {
			char c=(char)((nbr-9-1)+'A');
			return c + "";
		}
	}

	private Message handleXML(MetaEntry metaEntry, List<MissionEntry> entries, String inFile, String outFile, String pathRoot, String imagesPath) {
		Charset cs = Charset.forName("windows-1252");
		String XML_FILE = mFileHandling.readFileToString(inFile, cs);
		String XML_REGION = System.lineSeparator() +
				"                <idRegion>##REGION##</idRegion>";
		String XML_IMAGEPATH = System.lineSeparator() +
				"              <ImagePath>images\\##AIRPORT_ID##.png</ImagePath>";

		// Copy POI images from project source folder if they exist.
		File pathRootFile = new File(pathRoot);
		Pattern patternPNG = Pattern.compile("^[P][O][I].*\\.[p][n][g]");
		File[] rootPNGs = pathRootFile.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				Matcher matcherPNG = patternPNG.matcher(name);
				return matcherPNG.find();
			}
		});
		for (File rootPNG : rootPNGs) {
			try {
				Files.copy(new File(rootPNG.getAbsolutePath()).toPath(), 
						new File(imagesPath + File.separator + rootPNG.getName()).toPath(),
						StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
			}
		}

		// Preview
		Element div = mDoc.select("div").first();
		div.addClass("contents");
		Element table = null;
		boolean poiBefore = false;
		String imageWidth = "200";

		StringBuffer sb = new StringBuffer();
		StringBuffer sb_CALCS = new StringBuffer();
		StringBuffer sb_LANDINGACTIONS = new StringBuffer();
		int count_LANDINGACTIONS = 0;
		int count_LANDINGDIALOGS = 0;
		String string_CURRENTLEG = "";
		List<String> list_subLegs = new ArrayList<>();
		int count_AIRPORT = 0;
		int count_POI = 0;
		int count_ENTRY = 0;
		MissionEntry lastEntry = new MissionEntry();
		String lastRefId = "";
		for (MissionEntry entry : entries) {
			if (entry.type2.equals(WpType.AIRPORT)) {
				// Unique airport?
				String id = entry.id;
				if (!metaEntry.uniqueApImages.isEmpty()) {
					id = entry.uniqueId;
				}

				if (!string_CURRENTLEG.isEmpty()) {
					if (entry.subLegText.isEmpty()) {
						return new ErrorMessage("No subleg text found!!\n\n" + entry.toString());
					}
					String ss = XML_SUBLEG;
					ss = ss.replace("##SUBLEG_DESCR##", entry.subLegTextID);
					ss = ss.replace("##IMAGEPATH##", XML_IMAGEPATH.replace("##AIRPORT_ID##", id));
					ss = ss.replace("##FROM_ID##", lastEntry.id);
					ss = ss.replace("##TO_ID##", entry.id);
					String fromRegion = XML_REGION;
					fromRegion = fromRegion.replace("##REGION##", lastEntry.region);
					String toRegion = XML_REGION;
					toRegion = toRegion.replace("##REGION##", entry.region);
					ss = ss.replace("##FROM_REGION##", lastEntry.region.isEmpty() ? "": fromRegion);
					ss = ss.replace("##TO_REGION##", entry.region.isEmpty() ? "": toRegion);
					list_subLegs.add(ss);

					StringBuffer sb_subLegs = new StringBuffer();
					for (int i=0; i<list_subLegs.size(); i++) {
						String subleg = list_subLegs.get(i);
						sb_subLegs.append(subleg);
						if (i<list_subLegs.size()-1) sb_subLegs.append(System.lineSeparator());
					}
					string_CURRENTLEG = string_CURRENTLEG.replace("##SUBLEGS##", sb_subLegs.toString());
					sb.append(string_CURRENTLEG);
					sb.append(System.lineSeparator());

					// JSON
					mGeoJSON.appendLine(lastEntry.latlon, entry.latlon);

					string_CURRENTLEG = "";
					list_subLegs.clear();
				}

				String ss = XML_LEG;
				ss = ss.replace("##LEG_DESCR##", entry.legTextID);
				String refId = "FEBB002C-519C-4FC9-ABEF-6ECEA508A";
				refId += String.format("%03d", count_AIRPORT + 1);
				ss = ss.replace("##REF_ID##", refId);
				string_CURRENTLEG = ss;

				if (count_ENTRY>0) {
					String st = XML_CALC;
					st = st.replace("##AIRPORT_ID##", entry.id);
					st = st.replace("##REF_ID##", lastRefId);

					StringBuffer sb_CALC_PARAMS = new StringBuffer();
					if (!metaEntry.requireEnginesOff.isEmpty()) {
						sb_CALC_PARAMS.append(" (A:GENERAL ENG RPM:1, rpm) 10 &lt; and");
					}
					if (!metaEntry.requireBatteryOff.isEmpty()) {
						sb_CALC_PARAMS.append(" (A:ELECTRICAL MASTER BATTERY, Boolean) 1 &lt; and");
					}
					if (!metaEntry.requireAvionicsOff.isEmpty()) {
						sb_CALC_PARAMS.append(" (A:AVIONICS MASTER SWITCH, Boolean) 1 &lt; and");
					}

					if (sb_CALC_PARAMS.length()>0) {
						st = st.replace("##CALC_PARAMS##", sb_CALC_PARAMS);
					} else {
						st = st.replace("##CALC_PARAMS##", "");
					}

					StringBuffer calcActions = new StringBuffer();

					if (!metaEntry.missionFailures.isEmpty()) {
						int count_MISSIONFAILURES = 0;

						// Add ResetTimerAction
						for (MissionFailureEntry mfe : metaEntry.missionFailures) {
							count_MISSIONFAILURES++;

							if (mfe.currentMode == MissionFailureEntryMode.TIME) {
								String refId5 = "ADB0CDCD-5C03-4DD3-9201-0F46B74B0";
								refId5 += String.format("%03d", count_MISSIONFAILURES);
								calcActions.append("            " + "<ObjectReference id=\"ResetTimerAction" + count_MISSIONFAILURES + "\" InstanceId=\"{" + refId5 + "}\" />").append(System.lineSeparator());
							}
						}
					}

					String landXML = "            " + "<WorldBase.ObjectReference id=\"FlowEvent_LandingRest\" InstanceId=\"{F4FEBADA-8867-43E7-832D-947FAFCD8187}\" />";
					if (metaEntry.finishedEntries.containsKey(entry.uniqueId)) {
						count_LANDINGACTIONS++;
						List<DelayedText> feList = metaEntry.finishedEntries.get(entry.uniqueId);
						StringBuffer landedDialogs = new StringBuffer();

						// Calculate total duration of all entries
						double maxDelay = 0.0d;
						for (DelayedText dt : feList) {
							double delay = Double.parseDouble(dt.start) + Double.parseDouble(dt.delay);
							if (delay > maxDelay) {
								maxDelay = delay;
							}
						}
						String maxDelayString = String.format(Locale.US, "%.3f", maxDelay);

						int calcCount = 0;
						for (DelayedText fe : feList) {
							calcCount++;
							count_LANDINGDIALOGS++;
							String refId1 = "44C55CBB-743A-4ABC-AC33-06BE2ECF4";
							refId1 += String.format("%03d", count_LANDINGDIALOGS);

							String refId2 = "06025EFA-CB10-46C0-8C72-AE8CB46E3";
							refId2 += String.format("%03d", count_LANDINGDIALOGS);

							String refId4 = "79F5AF5D-0C87-491B-BAAA-B4A2BB68B";
							refId4 += String.format("%03d", count_LANDINGDIALOGS);

							calcActions.append("            <ObjectReference id=\"DialogActionLanding_" +  entry.uniqueId + calcCount + "\" InstanceId=\"{" + refId1 + "}\" />").append(System.lineSeparator());

							String sl = XML_LANDEDDIALOGS;
							sl = sl.replace("##REF_ID_DIALOG##", refId4);

							// Wav subtitles?
							String wav = fe.procWave;
							String text = fe.procTextID;

							String textXML = "";
							if (!wav.isEmpty()) {
								textXML += "<SoundFileName>" + wav + "</SoundFileName>";
								if (text != null) {
									textXML += System.lineSeparator() + "      " + "<Text>" + text + "</Text>";
								}
								mSounds.add(wav);
							} else {
								textXML += "<Text>" + text + "</Text>";
							}

							sl = sl.replace("##TEXT_DIALOG##", textXML);
							sl = sl.replace("##DESCR_DIALOG##", "DialogLanding_" +  entry.uniqueId + calcCount);
							sl = sl.replace("##REF_ID_DIALOGTRIGGER##", refId2);
							sl = sl.replace("##DESCR_DIALOGTRIGGER##", "DialogTriggerLanding_" +  entry.uniqueId + calcCount);
							sl = sl.replace("##REF_ID_DIALOGACTION##", refId1);
							sl = sl.replace("##DESCR_DIALOGACTION##", "DialogActionLanding_" +  entry.uniqueId + calcCount);
							sl = sl.replace("##DELAY_DIALOGTRIGGER##", fe.start);

							landedDialogs.append(System.lineSeparator());
							landedDialogs.append(sl);
							landedDialogs.append(System.lineSeparator());
						}

						String refId3 = "0FB9281F-86A4-4441-8D5E-78638A376";
						refId3 += String.format("%03d", count_LANDINGACTIONS);

						String refId5 = "EEDE3F56-DFD0-4862-9C72-789AB0606";
						refId5 += String.format("%03d", count_LANDINGACTIONS);

						String actionXML = "<ObjectReference id=\"ActionLanding_" +  entry.uniqueId + "\" InstanceId=\"{" + refId3 + "}\" />";
						calcActions.append("            " + actionXML);

						String sx = XML_LANDEDTRIGGER;
						sx = sx.replace("##LANDING_DIALOGS##", landedDialogs.toString());

						sx = sx.replace("##REF_ID_TRIGGER##", refId5);
						sx = sx.replace("##DESCR_TRIGGER##", "TriggerLanding_" +  entry.uniqueId);
						sx = sx.replace("##DELAY_TRIGGER##", maxDelayString);

						sx = sx.replace("##REF_ID_ACTION##", refId3);
						sx = sx.replace("##DESCR_ACTION##", "ActionLanding_" +  entry.uniqueId);

						sb_LANDINGACTIONS.append(System.lineSeparator());
						sb_LANDINGACTIONS.append(sx);
						sb_LANDINGACTIONS.append(System.lineSeparator());
					} else {
						calcActions.append(landXML);
					}

					st = st.replace("##LANDING_ACTION##", calcActions.toString());

					sb_CALCS.append(st);
					sb_CALCS.append(System.lineSeparator());
				}

				// Preview
				if (poiBefore) {
					Element tr = table.appendElement("tr");
					Element td1 = tr.appendElement("td");
					td1.appendText(entry.id);
					Element td2 = tr.appendElement("td");
					td2.attr("width", (100/(1+entry.localizations.size())) + "%");
					td2.appendText(entry.subLegText);

					for (Localization loc : entry.localizations) {
						Element tdLoc = tr.appendElement("td");
						tdLoc.attr("width", (100/(1+entry.localizations.size())) + "%");
						tdLoc.appendText(loc.value3);
					}

					Element td3 = tr.appendElement("td");
					td3.attr("width", imageWidth);
					Element image = td3.appendElement("image");
					image.attr("src", "source" + File.separator + "images" + File.separator + id + ".png");
					image.attr("width", imageWidth);
				}

				if (count_ENTRY < entries.size()-1) {
					div.appendElement("br");
					div.appendElement("br");
					Element b = div.appendElement("b");

					b.appendText("LEG " + (count_AIRPORT + 1));
					table = div.appendElement("table");

					table.attr("width", "100%");
					table.attr("border", "1px solid black;");
					table.attr("bgcolor", "#f3f3f3");

					Element tr = table.appendElement("tr");
					Element td1 = tr.appendElement("td");
					td1.appendText(entry.id);
					Element td2 = tr.appendElement("td");
					td2.attr("width", (100/(1+entry.localizations.size())) + "%");
					td2.appendText(entry.legText);
					for (Localization loc : entry.localizations) {
						Element tdLoc = tr.appendElement("td");
						tdLoc.attr("width", (100/(1+entry.localizations.size())) + "%");
						tdLoc.appendText(loc.value2);
					}
					tr.appendElement("td");
				}

				// JSON
				mGeoJSON.appendPoint(entry.latlon, "#ff0000");

				lastRefId = refId;
				count_AIRPORT++;
			} else {
				if (entry.subLegText.isEmpty()) {
					return new ErrorMessage("No subleg text found!!");
				}
				String ss = XML_SUBLEG;
				ss = ss.replace("##SUBLEG_DESCR##", entry.subLegTextID);

				// Add subleg image if an image exists!
				String imageName = "POI" + multiCount(++count_POI, 0);
				if (new File(imagesPath + File.separator + imageName + ".png").exists()) {
					ss = ss.replace("##IMAGEPATH##", XML_IMAGEPATH.replace("##AIRPORT_ID##", imageName));
					mPOIs++;
				} else {
					ss = ss.replace("##IMAGEPATH##", "");
				}

				ss = ss.replace("##FROM_ID##", lastEntry.id);
				ss = ss.replace("##TO_ID##", entry.id);
				String fromRegion = XML_REGION;
				fromRegion = fromRegion.replace("##REGION##", lastEntry.region);
				String toRegion = XML_REGION;
				toRegion = toRegion.replace("##REGION##", entry.region);
				ss = ss.replace("##FROM_REGION##", lastEntry.region.isEmpty() ? "": fromRegion);
				ss = ss.replace("##TO_REGION##", entry.region.isEmpty() ? "": toRegion);
				list_subLegs.add(ss);

				if (table == null) {
					div.appendElement("br");
					div.appendElement("br");
					table = div.appendElement("table");
				}
				Element tr = table.appendElement("tr");
				Element td1 = tr.appendElement("td");
				td1.appendText(entry.id);
				Element td2 = tr.appendElement("td");
				td2.attr("width", (100/(1+entry.localizations.size())) + "%");
				td2.appendText(entry.subLegText);
				for (Localization loc : entry.localizations) {
					Element tdLoc = tr.appendElement("td");
					tdLoc.attr("width", (100/(1+entry.localizations.size())) + "%");
					tdLoc.appendText(loc.value3);
				}
				Element td3 = tr.appendElement("td");
				td3.attr("width", imageWidth);

				if (new File(imagesPath + File.separator + imageName + ".png").exists()) {
					Element image = td3.appendElement("image");
					image.attr("src", "source" + File.separator + "images" + File.separator + imageName + ".png");
					image.attr("width", imageWidth);
				}

				poiBefore = true;

				// JSON
				mGeoJSON.appendPoint(entry.latlon, "#000000");
				mGeoJSON.appendLine(lastEntry.latlon, entry.latlon);
			}
			count_ENTRY++;
			lastEntry = entry;			
		}

		StringBuffer sb_DIALOGS = new StringBuffer();
		if (!metaEntry.poiSpeech.isEmpty() || !metaEntry.poiSpeechBefore.isEmpty()) {
			sb_DIALOGS.append(System.lineSeparator());
			int count = 0;
			DialogEntry de = new DialogEntry(); // To get standard values
			if (metaEntry.poiSpeechBefore.isEmpty()) {
				for (MissionEntry me : entries) {
					if (count == 0) {
						count++;
						continue;
					}

					String ss = XML_DIALOGS;

					String refId1 = "DDC6F3F4-F6CF-47BA-A1CD-84951D95A";
					refId1 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_DIALOG##", refId1);
					ss = ss.replace("##DESCR_DIALOG##",  "DialogPOI" + (count + 0));
					ss = ss.replace("##TEXT_DIALOG##", "<Text>" + me.subLegTextID + "</Text>");

					String refId2 = "E653E102-8059-467E-8E43-A55D21D5A";
					refId2 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_TIMERTRIGGER##", refId2);
					ss = ss.replace("##DESCR_TIMERTRIGGER##", "TimerTriggerPOI" + (count + 0));
					ss = ss.replace("##DELAY_TIMERTRIGGER##", de.delay);

					String refId3 = "BD7BADEE-15B1-4DDF-8CA1-49D7C591F";
					refId3 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_ACTIVATION##", refId3);
					ss = ss.replace("##DESCR_ACTIVATION##", "ActivationPOI" + (count + 0));

					String refId4 = "EDC6F3F4-F6CF-47BA-A1CD-94951D95A";
					refId4 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId4);
					ss = ss.replace("##DESCR_TRIGGER##", "ProximityTriggerPOI" + (count + 0));
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");

					String boxSideSize = de.width;
					if (!metaEntry.standardEnterAreaSideLength.isEmpty()) {
						boxSideSize = metaEntry.standardEnterAreaSideLength;
					}

					String refId5 = "FDC6F3F4-F6CF-47BA-A1CD-A4951D95A";
					refId5 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_AREA##", refId5);
					ss = ss.replace("##DESCR_AREA##",  "RectangleAreaPOI" + (count + 0));
					ss = ss.replace("##LENGTH_AREA##", boxSideSize);
					ss = ss.replace("##WIDTH_AREA##", boxSideSize);
					ss = ss.replace("##HEIGHT_AREA##", de.height);
					ss = ss.replace("##HEADING_AREA##", de.heading);
					ss = ss.replace("##LLA_AREA##", me.latlon + ",-000200.00");
					ss = ss.replace("##USE_AGL##", de.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "False" : "True") : de.agl);
					ss = ss.replace("##TOGGLE_ACTIONS##", "");
					ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");

					// JSON
					mGeoJSON.appendPolygon(me.latlon, boxSideSize, boxSideSize, de.heading, "#555555", "#007700");

					sb_DIALOGS.append(ss);
					sb_DIALOGS.append(System.lineSeparator());
					count++;
				}
			} else {
				MissionEntry prevMe = null;
				for (MissionEntry me : entries) {
					if (count == 0) {
						prevMe = me;
						count++;
						continue;
					}

					String ss = XML_DIALOGS;
					if (prevMe.type2.equals(WpType.AIRPORT)) {
						ss = XML_DIALOGSEXIT;
					}

					String refId1 = "DDC6F3F4-F6CF-47BA-A1CD-84951D95A";
					refId1 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_DIALOG##", refId1);
					ss = ss.replace("##DESCR_DIALOG##",  "DialogPOI" + (count + 0));
					ss = ss.replace("##TEXT_DIALOG##", "<Text>" + me.subLegTextID + "</Text>");

					String refId2 = "E653E102-8059-467E-8E43-A55D21D5A";
					refId2 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_TIMERTRIGGER##", refId2);
					ss = ss.replace("##DESCR_TIMERTRIGGER##", "TimerTriggerPOI" + (count + 0));
					ss = ss.replace("##DELAY_TIMERTRIGGER##", de.delay);

					String refId3 = "BD7BADEE-15B1-4DDF-8CA1-49D7C591F";
					refId3 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_ACTIVATION##", refId3);
					ss = ss.replace("##DESCR_ACTIVATION##", "ActivationPOI" + (count + 0));

					String refId4 = "EDC6F3F4-F6CF-47BA-A1CD-94951D95A";
					refId4 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId4);
					ss = ss.replace("##DESCR_TRIGGER##", "ProximityTriggerPOI" + (count + 0));
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");

					String refId5 = "FDC6F3F4-F6CF-47BA-A1CD-A4951D95A";
					refId5 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_AREA##", refId5);
					ss = ss.replace("##DESCR_AREA##",  "RectangleAreaPOI" + (count + 0));

					if (prevMe.type2.equals(WpType.AIRPORT)) {
						String boxSideSize = "2000.000";
						if (!metaEntry.standardAirportExitAreaSideLength.isEmpty()) {
							boxSideSize = metaEntry.standardAirportExitAreaSideLength;
						}

						ss = ss.replace("##LENGTH_AREA##", boxSideSize);
						ss = ss.replace("##WIDTH_AREA##", boxSideSize);
						ss = ss.replace("##HEIGHT_AREA##", de.height);

						// JSON
						mGeoJSON.appendPolygon(prevMe.latlon, boxSideSize, boxSideSize, de.heading, "#555555", "#00ff00");
					} else {
						String boxSideSize = de.width;
						if (!metaEntry.standardEnterAreaSideLength.isEmpty()) {
							boxSideSize = metaEntry.standardEnterAreaSideLength;
						}

						ss = ss.replace("##LENGTH_AREA##", boxSideSize);
						ss = ss.replace("##WIDTH_AREA##", boxSideSize);
						ss = ss.replace("##HEIGHT_AREA##", de.height);

						// JSON
						mGeoJSON.appendPolygon(prevMe.latlon, boxSideSize, boxSideSize, de.heading, "#555555", "#007700");
					}
					ss = ss.replace("##HEADING_AREA##", de.heading);
					ss = ss.replace("##LLA_AREA##", prevMe.latlon + ",-000200.00");
					ss = ss.replace("##USE_AGL##", de.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "False" : "True") : de.agl);
					ss = ss.replace("##TOGGLE_ACTIONS##", "");
					ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");

					sb_DIALOGS.append(ss);
					sb_DIALOGS.append(System.lineSeparator());

					prevMe = me;
					count++;
				}
			} 
		}

		if (!metaEntry.dialogEntries.isEmpty()) {
			sb_DIALOGS.append(System.lineSeparator());
			int count = 0;
			for (DialogEntry de : metaEntry.dialogEntries) {
				String ss = XML_DIALOGS;
				if (de.exit) {
					ss = XML_DIALOGSEXIT;
				}

				String refId1 = "3DC6F3F4-F6CF-47BA-A1CD-84951D95A";
				refId1 += String.format("%03d", count + 1);
				ss = ss.replace("##REF_ID_DIALOG##", refId1);
				ss = ss.replace("##DESCR_DIALOG##",  "DialogEntry" + (count + 1));

				// Wav subtitles?
				String wav = de.procWave;
				String text = de.procTextID;

				String textXML = "";
				if (!wav.isEmpty()) {
					textXML += "<SoundFileName>" + wav + "</SoundFileName>";
					if (text != null) {
						textXML += System.lineSeparator() + "      " + "<Text>" + text + "</Text>";
					}
					mSounds.add(wav);
				} else {
					textXML += "<Text>" + text + "</Text>";
				}

				ss = ss.replace("##TEXT_DIALOG##", textXML);

				String refId2 = "BFE13B95-3296-4983-A6A5-7723C0E6D";
				refId2 += String.format("%03d", count + 1);
				ss = ss.replace("##REF_ID_TIMERTRIGGER##", refId2);
				ss = ss.replace("##DESCR_TIMERTRIGGER##", "TimerTriggerEntry" + (count + 1));
				ss = ss.replace("##DELAY_TIMERTRIGGER##", de.delay);

				String refId3 = "B3E12438-DC99-4362-A595-8D894D373";
				refId3 += String.format("%03d", count + 1);
				ss = ss.replace("##REF_ID_ACTIVATION##", refId3);
				ss = ss.replace("##DESCR_ACTIVATION##", "ActivationEntry" + (count + 1));

				String refId4 = "F58B3153-54C7-455B-A195-ABF779019";
				refId4 += String.format("%03d", count + 1);
				ss = ss.replace("##REF_ID_TRIGGER##", refId4);
				String triggerName = "ProximityTriggerEntry" + (count + 1);
				de.triggerId = triggerName;
				de.triggerGUID = refId4;
				ss = ss.replace("##DESCR_TRIGGER##", triggerName);
				ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");

				String refId5 = "9204A2ED-3C98-44C3-B7F3-2A4266485";
				refId5 += String.format("%03d", count + 1);
				ss = ss.replace("##REF_ID_AREA##", refId5);
				ss = ss.replace("##DESCR_AREA##",  "RectangleAreaEntry" + (count + 1));
				ss = ss.replace("##LENGTH_AREA##", de.length);
				ss = ss.replace("##WIDTH_AREA##", de.width);
				ss = ss.replace("##HEIGHT_AREA##", de.height);
				ss = ss.replace("##HEADING_AREA##", de.heading);
				ss = ss.replace("##LLA_AREA##", de.latlon + ",+000000.00");
				ss = ss.replace("##USE_AGL##", de.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "False" : "True") : de.agl);

				if (de.mName != null && metaEntry.toggleTriggers.containsKey(de.mName)) {
					String[] toggleList = metaEntry.toggleTriggers.get(de.mName).mTriggerList;
					boolean activate = metaEntry.toggleTriggers.get(de.mName).mActivate;

					StringBuffer toggleTriggers = new StringBuffer();
					for (String tl : toggleList) {
						String sr = "            <ObjectReference id=\"" + tl + "_id\" InstanceId=\"{" + tl + "_guid}\"/>";
						toggleTriggers.append(System.lineSeparator()).append(sr);
					}

					String so = XML_OBJECTACTIVATIONACTION;
					String refId6 = "6E8A46C1-92A8-4629-92AC-DEBC040F2";
					refId6 += String.format("%03d", count + 1);
					so = so.replace("##REF_ID_ACTION##", refId6);
					so = so.replace("##DESCR_ACTION##", "DialogToggleTrigger" + (count + 1));
					so = so.replace("##STATE_ACTION##", String.valueOf(activate));
					so = so.replace("##LIST_TRIGGERS##", toggleTriggers);

					String sr = "        <ObjectReference id=\"" + "DialogToggleTrigger" + (count + 1) + "\" InstanceId=\"{" + refId6 + "}\"/>";
					ss = ss.replace("##TOGGLE_ACTIONS##", System.lineSeparator() + sr);
					ss = ss.replace("##TOGGLE_TRIGGERS##", System.lineSeparator() + so);
				} else {
					ss = ss.replace("##TOGGLE_ACTIONS##", "");
					ss = ss.replace("##TOGGLE_TRIGGERS##", "");
				}

				if (de.mName != null && metaEntry.counterToggleTriggersCompanion.containsKey(de.mName)) {
					List<String> toggleList = metaEntry.counterToggleTriggersCompanion.get(de.mName);
					StringBuffer toggleTriggers = new StringBuffer();
					for (String tl : toggleList) {
						String sr = "        <ObjectReference id=\"" + tl + "_cid\" InstanceId=\"{" + tl + "_cguid}\"/>";
						toggleTriggers.append(System.lineSeparator()).append(sr);
					}
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", System.lineSeparator() + toggleTriggers);
				} else {
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");
				}

				// JSON
				mGeoJSON.appendPolygon(de.latlon, de.width, de.length, de.heading, "#555555", de.exit ? "#0000ff" : "#000077");

				sb_DIALOGS.append(ss);
				sb_DIALOGS.append(System.lineSeparator());
				count++;
			}
		}

		StringBuffer sb_FAILURES = new StringBuffer();
		if (!metaEntry.failureEntries.isEmpty()) {
			sb_FAILURES.append(System.lineSeparator());

			int count = 0;
			for (FailureEntry fe : metaEntry.failureEntries) {
				String ss = XML_FAILURES;
				if (fe.exit) {
					ss = XML_FAILURESEXIT;
				}

				boolean foundMode = false;

				if (fe.currentMode == FailureEntryMode.ALTITUDE) {
					foundMode = true;
					ss = XML_ALTITUDETRIGGER;
					ss = ss.replace("##ACTION##", XML_FAILUREACTION);

					String refId1 = "F3D0A9CD-6948-407A-9F86-348D62A89";
					refId1 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_FAILURE##", refId1);
					ss = ss.replace("##DESCR_FAILURE##",  "AltitudeFailureEntry" + (count + 1));
					ss = ss.replace("##HEALTH_FAILURE##",  fe.health);
					ss = ss.replace("##SYSTEM_FAILURE##",  fe.system);
					ss = ss.replace("##SYSTEMINDEX_FAILURE##",  fe.systemIndex);

					String refId2 = "C5403B66-2213-41A0-8467-94F6057A3";
					refId2 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId2);
					String triggerName = "PropertyTriggerAltitudeFailure" + (count + 1);
					fe.triggerId = triggerName;
					fe.triggerGUID = refId2;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##ALTITUDEMODE##", fe.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "AMSL" : "AGL") : fe.agl.equals("False") ? "AMSL" : "AGL");
					ss = ss.replace("##HEIGHT_TRIGGER##", fe.height);
					ss = ss.replace("##DESCR_ACTION##",  "AltitudeFailureEntry" + (count + 1));
					ss = ss.replace("##REF_ID_ACTION##", refId1);
				} else if (fe.currentMode == FailureEntryMode.SPEED) {
					foundMode = true;
					ss = XML_SPEEDTRIGGER;
					ss = ss.replace("##ACTION##", XML_FAILUREACTION);

					String refId1 = "C7C0B08C-E7F2-4AEF-A61E-21190CA8F";
					refId1 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_FAILURE##", refId1);
					ss = ss.replace("##DESCR_FAILURE##",  "SpeedFailureEntry" + (count + 1));
					ss = ss.replace("##HEALTH_FAILURE##",  fe.health);
					ss = ss.replace("##SYSTEM_FAILURE##",  fe.system);
					ss = ss.replace("##SYSTEMINDEX_FAILURE##",  fe.systemIndex);

					String refId2 = "DAE63D82-96A9-4662-9F58-C5A67A784";
					refId2 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId2);
					String triggerName = "PropertyTriggerSpeedFailure" + (count + 1);
					fe.triggerId = triggerName;
					fe.triggerGUID = refId2;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##SPEED_TRIGGER##", fe.speed);
					ss = ss.replace("##DESCR_ACTION##",  "SpeedFailureEntry" + (count + 1));
					ss = ss.replace("##REF_ID_ACTION##", refId1);
				} else if (fe.currentMode == FailureEntryMode.ALTITUDE_AND_SPEED) {
					foundMode = true;
					ss = XML_ALTITUDESPEEDTRIGGER;
					ss = ss.replace("##ACTION##", XML_FAILUREACTION);

					String refId1 = "88617178-9F2D-4426-A909-292D9D10B";
					refId1 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_FAILURE##", refId1);
					ss = ss.replace("##DESCR_FAILURE##",  "AltitudeSpeedFailureEntry" + (count + 1));
					ss = ss.replace("##HEALTH_FAILURE##",  fe.health);
					ss = ss.replace("##SYSTEM_FAILURE##",  fe.system);
					ss = ss.replace("##SYSTEMINDEX_FAILURE##",  fe.systemIndex);

					String refId2 = "25AD848F-3AE7-4B43-B90D-1666E58CF";
					refId2 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId2);
					String triggerName = "PropertyTriggerAltitudeSpeedFailure" + (count + 1);
					fe.triggerId = triggerName;
					fe.triggerGUID = refId2;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##ALTITUDEMODE##", fe.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "AMSL" : "AGL") : fe.agl.equals("False") ? "AMSL" : "AGL");
					ss = ss.replace("##HEIGHT_TRIGGER##", fe.height);
					ss = ss.replace("##SPEED_TRIGGER##", fe.speed);
					ss = ss.replace("##DESCR_ACTION##",  "AltitudeSpeedFailureEntry" + (count + 1));
					ss = ss.replace("##REF_ID_ACTION##", refId1);
				} else if (fe.currentMode == FailureEntryMode.FORMULA) {
					foundMode = true;
					ss = XML_FORMULATRIGGER;
					ss = ss.replace("##ACTION##", XML_FAILUREACTION);

					String refId1 = "367032D4-E062-4630-8E37-F8FB28AB3";
					refId1 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_FAILURE##", refId1);
					ss = ss.replace("##DESCR_FAILURE##",  "FormulaFailureEntry" + (count + 1));
					ss = ss.replace("##HEALTH_FAILURE##",  fe.health);
					ss = ss.replace("##SYSTEM_FAILURE##",  fe.system);
					ss = ss.replace("##SYSTEMINDEX_FAILURE##",  fe.systemIndex);

					String refId2 = "5CCC53E7-2493-485D-8AC1-D1CD9C567";
					refId2 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId2);
					String triggerName = "PropertyTriggerFormulaFailure" + (count + 1);
					fe.triggerId = triggerName;
					fe.triggerGUID = refId2;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##FORMULA_TRIGGER##", fe.formula);
					ss = ss.replace("##DESCR_ACTION##",  "FormulaFailureEntry" + (count + 1));
					ss = ss.replace("##REF_ID_ACTION##", refId1);
				} else if (fe.currentMode == FailureEntryMode.AREA) {
					foundMode = true;
					String refId1 = "1B74D4C2-22B3-4405-B1FA-7CEE2AE3D";
					refId1 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_FAILURE##", refId1);
					ss = ss.replace("##DESCR_FAILURE##",  "FailureEntry" + (count + 1));
					ss = ss.replace("##HEALTH_FAILURE##",  fe.health);
					ss = ss.replace("##SYSTEM_FAILURE##",  fe.system);
					ss = ss.replace("##SYSTEMINDEX_FAILURE##",  fe.systemIndex);

					String refId2 = "2B74D4C2-22B3-4405-B1FA-7CEE2AE3D";
					refId2 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId2);
					String triggerName = "ProximityTriggerFail" + (count + 1);
					fe.triggerId = triggerName;
					fe.triggerGUID = refId2;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");

					String refId3 = "3B74D4C2-22B3-4405-B1FA-7CEE2AE3D";
					refId3 += String.format("%03d", count + 1);
					ss = ss.replace("##REF_ID_AREA##", refId3);
					ss = ss.replace("##DESCR_AREA##",  "RectangleAreaFail" + (count + 1));
					ss = ss.replace("##LENGTH_AREA##", fe.length);
					ss = ss.replace("##WIDTH_AREA##", fe.width);
					ss = ss.replace("##HEIGHT_AREA##", fe.height);
					ss = ss.replace("##HEADING_AREA##", fe.heading);
					ss = ss.replace("##LLA_AREA##", fe.latlon + ",+000000.00");
					ss = ss.replace("##USE_AGL##", fe.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "False" : "True") : fe.agl);

					// JSON
					mGeoJSON.appendPolygon(fe.latlon, fe.width, fe.length, fe.heading, "#555555", "#ff0000");
				}

				if (foundMode) {
					if (fe.mName != null && metaEntry.toggleTriggers.containsKey(fe.mName)) {
						String[] toggleList = metaEntry.toggleTriggers.get(fe.mName).mTriggerList;
						boolean activate = metaEntry.toggleTriggers.get(fe.mName).mActivate;

						StringBuffer toggleTriggers = new StringBuffer();
						for (String tl : toggleList) {
							String sr = "            <ObjectReference id=\"" + tl + "_id\" InstanceId=\"{" + tl + "_guid}\"/>";
							toggleTriggers.append(System.lineSeparator()).append(sr);
						}

						String so = XML_OBJECTACTIVATIONACTION;
						String refId6 = "64EEDC19-C47C-498C-8C14-4061971B5";
						refId6 += String.format("%03d", count + 1);
						so = so.replace("##REF_ID_ACTION##", refId6);
						so = so.replace("##DESCR_ACTION##", "FailureToggleTrigger" + (count + 1));
						so = so.replace("##STATE_ACTION##", String.valueOf(activate));
						so = so.replace("##LIST_TRIGGERS##", toggleTriggers);

						String sr = "        <ObjectReference id=\"" + "FailureToggleTrigger" + (count + 1) + "\" InstanceId=\"{" + refId6 + "}\"/>";
						ss = ss.replace("##TOGGLE_ACTIONS##", System.lineSeparator() + sr);
						ss = ss.replace("##TOGGLE_TRIGGERS##", System.lineSeparator() + so);
						count++;
					} else {
						ss = ss.replace("##TOGGLE_ACTIONS##", "");
						ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					}

					if (fe.mName != null && metaEntry.counterToggleTriggersCompanion.containsKey(fe.mName)) {
						List<String> toggleList = metaEntry.counterToggleTriggersCompanion.get(fe.mName);
						StringBuffer toggleTriggers = new StringBuffer();
						for (String tl : toggleList) {
							String sr = "        <ObjectReference id=\"" + tl + "_cid\" InstanceId=\"{" + tl + "_cguid}\"/>";
							toggleTriggers.append(System.lineSeparator()).append(sr);
						}
						ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", System.lineSeparator() + toggleTriggers);
					} else {
						ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");
					}

					sb_FAILURES.append(ss);
					sb_FAILURES.append(System.lineSeparator());
					count++;
				}
			}
		}

		StringBuffer sb_INTRODIALOG = new StringBuffer();
		if (!metaEntry.introSpeeches.isEmpty()) {
			int count1 = 0;

			for (DelayedText is : metaEntry.introSpeeches) {
				String ss = XML_INTRODIALOG;

				// Wav subtitles?
				String wav = is.procWave;
				String text = is.procTextID;

				String textXML = "";
				if (!wav.isEmpty()) {
					textXML += "<SoundFileName>" + wav + "</SoundFileName>";
					if (text != null) {
						textXML += System.lineSeparator() + "      " + "<Text>" + text + "</Text>";
					}
					mSounds.add(wav);
				} else {
					textXML += "<Text>" + text + "</Text>";
				}

				ss = ss.replace("##INTRODIALOG##", textXML);

				String refId1 = "33C886C6-2E7B-4DD3-AF43-CE2FD4CE3";
				refId1 += String.format("%03d", count1 + 1);
				ss = ss.replace("##REF_ID_DIALOG##", refId1);
				ss = ss.replace("##DESCR_DIALOG##",  "DialogIntro" + (count1 + 1));
				ss = ss.replace("##DELAY_DIALOG##",  "2.000");

				String refId2 = "AD3E6999-7687-479D-81B8-B327E1D21";
				refId2 += String.format("%03d", count1 + 1);
				ss = ss.replace("##REF_ID_TRIGGER##", refId2);
				ss = ss.replace("##DESCR_TRIGGER##", "TimerTriggerIntro" + (count1 + 1));
				ss = ss.replace("##DELAY_TRIGGER##", is.delay);

				// JSON
				mGeoJSON.appendPolygon(entries.get(0).latlon, "150.000", "150.000", "0.000", "#555555", "#00ffff");

				sb_INTRODIALOG.append(System.lineSeparator());
				sb_INTRODIALOG.append(System.lineSeparator());
				sb_INTRODIALOG.append(ss);
				count1++;
			}
		}

		StringBuffer sb_WARNINGS = new StringBuffer();
		if (!metaEntry.warningsEntries.isEmpty()) {
			int count = 0;
			int count1 = 0;
			int count2 = 0;
			int count3 = 0;
			int count4 = 0;

			for (WarningEntry we : metaEntry.warningsEntries) {
				boolean foundMode = false;
				String ss = null;

				// Wav subtitles?
				String wav = we.procWave;
				String text = we.procTextID;

				String textXML = "";
				if (!wav.isEmpty()) {
					textXML += "<SoundFileName>" + wav + "</SoundFileName>";
					if (text != null) {
						textXML += System.lineSeparator() + "      " + "<Text>" + text + "</Text>";
					}
					mSounds.add(wav);
				} else {
					textXML += "<Text>" + text + "</Text>";
				}

				if (we.currentMode == WarningEntryMode.ALTITUDE) {
					foundMode = true;
					ss = XML_ALTITUDETRIGGER;

					ss = ss.replace("##ACTION##", XML_DIALOGACTION);

					String refId1 = "926B2C33-081B-4D7C-8865-FF2FDB0AF";
					refId1 += String.format("%03d", count1 + 1);
					ss = ss.replace("##REF_ID_DIALOG##", refId1);
					ss = ss.replace("##DESCR_DIALOG##",  "DialogAltitude" + (count1 + 1));
					ss = ss.replace("##TEXT_DIALOG##", textXML);
					ss = ss.replace("##DELAY_DIALOG##",  "2.000");

					String refId2 = "BB9EF18D-07A0-488B-87C2-6F61417D9";
					refId2 += String.format("%03d", count1 + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId2);
					String triggerName = "PropertyTriggerAltitude" + (count1 + 1);
					we.triggerId = triggerName;
					we.triggerGUID = refId2;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##ALTITUDEMODE##", we.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "AMSL" : "AGL") : we.agl.equals("False") ? "AMSL" : "AGL");
					ss = ss.replace("##HEIGHT_TRIGGER##", we.height);
					ss = ss.replace("##DESCR_ACTION##",  "DialogAltitude" + (count1 + 1));
					ss = ss.replace("##REF_ID_ACTION##", refId1);
					count1++;
				} else if (we.currentMode == WarningEntryMode.SPEED) {
					foundMode = true;
					ss = XML_SPEEDTRIGGER;

					ss = ss.replace("##ACTION##", XML_DIALOGACTION);

					String refId1 = "528ADAB1-D26C-45BA-A281-A7CA5D6DD";
					refId1 += String.format("%03d", count2 + 1);
					ss = ss.replace("##REF_ID_DIALOG##", refId1);
					ss = ss.replace("##DESCR_DIALOG##",  "DialogSpeed" + (count2 + 1));
					ss = ss.replace("##TEXT_DIALOG##", textXML);
					ss = ss.replace("##DELAY_DIALOG##",  "2.000");

					String refId2 = "EFCE14C2-ADB7-4F15-9240-35E5B9DE8";
					refId2 += String.format("%03d", count2 + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId2);
					String triggerName = "PropertyTriggerSpeed" + (count2 + 1);
					we.triggerId = triggerName;
					we.triggerGUID = refId2;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##SPEED_TRIGGER##", we.speed);
					ss = ss.replace("##DESCR_ACTION##",  "DialogSpeed" + (count2 + 1));
					ss = ss.replace("##REF_ID_ACTION##", refId1);
					count2++;
				} else if (we.currentMode == WarningEntryMode.ALTITUDE_AND_SPEED) {
					foundMode = true;
					ss = XML_ALTITUDESPEEDTRIGGER;

					ss = ss.replace("##ACTION##", XML_DIALOGACTION);

					String refId1 = "B9028A6F-3009-449A-850D-FF55FBD24";
					refId1 += String.format("%03d", count3 + 1);
					ss = ss.replace("##REF_ID_DIALOG##", refId1);
					ss = ss.replace("##DESCR_DIALOG##",  "DialogAltitudeSpeed" + (count3 + 1));
					ss = ss.replace("##TEXT_DIALOG##", textXML);
					ss = ss.replace("##DELAY_DIALOG##",  "2.000");

					String refId2 = "59E181A7-399D-412E-91DF-6BA1A6987";
					refId2 += String.format("%03d", count3 + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId2);
					String triggerName = "PropertyTriggerAltitudeSpeed" + (count3 + 1);
					we.triggerId = triggerName;
					we.triggerGUID = refId2;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##ALTITUDEMODE##", we.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "AMSL" : "AGL") : we.agl.equals("False") ? "AMSL" : "AGL");
					ss = ss.replace("##HEIGHT_TRIGGER##", we.height);
					ss = ss.replace("##SPEED_TRIGGER##", we.speed);
					ss = ss.replace("##DESCR_ACTION##",  "DialogAltitudeSpeed" + (count3 + 1));
					ss = ss.replace("##REF_ID_ACTION##", refId1);
					count3++;
				} else if (we.currentMode == WarningEntryMode.FORMULA) {
					foundMode = true;
					ss = XML_FORMULATRIGGER;

					ss = ss.replace("##ACTION##", XML_DIALOGACTION);

					String refId1 = "00EC366E-9A41-444A-9E42-FC2B11C94";
					refId1 += String.format("%03d", count4 + 1);
					ss = ss.replace("##REF_ID_DIALOG##", refId1);
					ss = ss.replace("##DESCR_DIALOG##",  "DialogFormula" + (count4 + 1));
					ss = ss.replace("##TEXT_DIALOG##", textXML);
					ss = ss.replace("##DELAY_DIALOG##",  "2.000");

					String refId2 = "A4C03C2A-9860-484B-83A2-943149B24";
					refId2 += String.format("%03d", count4 + 1);
					ss = ss.replace("##REF_ID_TRIGGER##", refId2);
					String triggerName = "PropertyTriggerFormula" + (count4 + 1);
					we.triggerId = triggerName;
					we.triggerGUID = refId2;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##FORMULA_TRIGGER##", we.formula);
					ss = ss.replace("##DESCR_ACTION##",  "DialogFormula" + (count4 + 1));
					ss = ss.replace("##REF_ID_ACTION##", refId1);
					count4++;
				}

				if (foundMode) {
					if (we.mName != null && metaEntry.toggleTriggers.containsKey(we.mName)) {
						String[] toggleList = metaEntry.toggleTriggers.get(we.mName).mTriggerList;
						boolean activate = metaEntry.toggleTriggers.get(we.mName).mActivate;

						StringBuffer toggleTriggers = new StringBuffer();
						for (String tl : toggleList) {
							String sr = "            <ObjectReference id=\"" + tl + "_id\" InstanceId=\"{" + tl + "_guid}\"/>";
							toggleTriggers.append(System.lineSeparator()).append(sr);
						}

						String so = XML_OBJECTACTIVATIONACTION;
						String refId6 = "45FE2455-132C-4FC8-8AF6-F673211E1";
						refId6 += String.format("%03d", count + 1);
						so = so.replace("##REF_ID_ACTION##", refId6);
						so = so.replace("##DESCR_ACTION##", "WarningToggleTrigger" + (count + 1));
						so = so.replace("##STATE_ACTION##", String.valueOf(activate));
						so = so.replace("##LIST_TRIGGERS##", toggleTriggers);

						String sr = "        <ObjectReference id=\"" + "WarningToggleTrigger" + (count + 1) + "\" InstanceId=\"{" + refId6 + "}\"/>";
						ss = ss.replace("##TOGGLE_ACTIONS##", System.lineSeparator() + sr);
						ss = ss.replace("##TOGGLE_TRIGGERS##", System.lineSeparator() + so);
						count++;
					} else {
						ss = ss.replace("##TOGGLE_ACTIONS##", "");
						ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					}

					if (we.mName != null && metaEntry.counterToggleTriggersCompanion.containsKey(we.mName)) {
						List<String> toggleList = metaEntry.counterToggleTriggersCompanion.get(we.mName);
						StringBuffer toggleTriggers = new StringBuffer();
						for (String tl : toggleList) {
							String sr = "        <ObjectReference id=\"" + tl + "_cid\" InstanceId=\"{" + tl + "_cguid}\"/>";
							toggleTriggers.append(System.lineSeparator()).append(sr);
						}
						ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", System.lineSeparator() + toggleTriggers);
					} else {
						ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");
					}

					sb_WARNINGS.append(System.lineSeparator());
					sb_WARNINGS.append(System.lineSeparator());
					sb_WARNINGS.append(ss);
				}
			}
		}

		StringBuffer sb_ACTIONS = new StringBuffer();
		StringBuffer sb_OBJECTIVES = new StringBuffer();
		StringBuffer sb_GOALS = new StringBuffer();
		StringBuffer sb_FINISHEDACTIONS = new StringBuffer();
		if (!metaEntry.missionFailures.isEmpty()) {
			int count_MISSIONFAILURES = 0;

			for (MissionFailureEntry mfe : metaEntry.missionFailures) {
				count_MISSIONFAILURES++;

				if (mfe.currentMode == MissionFailureEntryMode.AREA) {
					String ss = XML_PROXIMITYTRIGGER;
					ss = ss.replace("##ACTION##", "");

					if (mfe.exit) {
						ss = ss.replace("##ENTERACTION##", "");
						ss = ss.replace("##EXITACTION##", System.lineSeparator() + "        <ObjectReference id=\"##DESCR_ACTION##\" InstanceId=\"{##REF_ID_ACTION##}\"/>");
					} else {
						ss = ss.replace("##ENTERACTION##", System.lineSeparator() + "        <ObjectReference id=\"##DESCR_ACTION##\" InstanceId=\"{##REF_ID_ACTION##}\"/>");
						ss = ss.replace("##EXITACTION##", "");
					}

					String refId1 = "5B13D11A-76B1-40D7-B7AD-E4D93DA83";
					refId1 += String.format("%03d", count_MISSIONFAILURES);
					String refId2 = "F42CA18A-A30C-4FB9-8127-30CB76EF3";
					refId2 += String.format("%03d", count_MISSIONFAILURES);
					String refId3 = "E60C6BCF-EDEE-437A-A65D-5DE08A42C";
					refId3 += String.format("%03d", count_MISSIONFAILURES);
					String refId4 = "2B301ED3-3737-48E1-AF8E-4EA398348";
					refId4 += String.format("%03d", count_MISSIONFAILURES);
					String refId5 = "07972A81-E6C5-4AAA-A784-74E0F4237";
					refId5 += String.format("%03d", count_MISSIONFAILURES);

					// ACTIONS
					ss = ss.replace("##REF_ID_TRIGGER##", refId1);
					String triggerName = "ProximityTriggerLimit" + count_MISSIONFAILURES;
					mfe.triggerId = triggerName;
					mfe.triggerGUID = refId1;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##LENGTH_AREA##", mfe.length);
					ss = ss.replace("##WIDTH_AREA##", mfe.width);
					ss = ss.replace("##HEIGHT_AREA##", mfe.height);
					ss = ss.replace("##HEADING_AREA##", mfe.heading);
					ss = ss.replace("##LLA_AREA##", mfe.latlon + ",-000200.00");
					ss = ss.replace("##USE_AGL##", mfe.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "False" : "True") : mfe.agl);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##REF_ID_AREA##", refId5);
					ss = ss.replace("##DESCR_AREA##",  "RectangleAreaLimit" + count_MISSIONFAILURES);
					ss = ss.replace("##DESCR_ACTION##", "ACT_FailGoal");
					ss = ss.replace("##REF_ID_ACTION##", refId3);
					ss = ss.replace("##TOGGLE_ACTIONS##", "");
					ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");
					sb_ACTIONS.append(System.lineSeparator());
					sb_ACTIONS.append(ss);

					// OBJECTIVES
					String st = XML_OBJECTIVE;
					sb_OBJECTIVES.append(System.lineSeparator());
					st = st.replace("##REF_ID_GOAL##", refId2);
					st = st.replace("##FAILURETEXT_GOAL##", "ILLEGAL AREA!");
					sb_OBJECTIVES.append(st);

					// GOALS
					sb_GOALS.append(System.lineSeparator());
					sb_GOALS.append(System.lineSeparator());
					String su = XML_GOAL;
					su = su.replace("##REF_ID_GOAL##", refId2);
					su = su.replace("##REF_ID_GOALPASSACTION##", refId4);
					su = su.replace("##REF_ID_GOALFAILACTION##", refId3);
					sb_GOALS.append(su);

					// Finished actions
					sb_FINISHEDACTIONS.append(System.lineSeparator());
					sb_FINISHEDACTIONS.append("        <WorldBase.ObjectReference id=\"End Of Mission\" InstanceId=\"{" + refId4 + "}\" />");
				}

				if (mfe.currentMode == MissionFailureEntryMode.ALTITUDE) {
					String ss = XML_ALTITUDETRIGGER;
					ss = ss.replace("##ACTION##", "");

					String refId1 = "0B9767BE-30E4-4273-BAD9-4693CEBBD";
					refId1 += String.format("%03d", count_MISSIONFAILURES);
					String refId2 = "D763694F-0F80-432A-B844-911B7C2A4";
					refId2 += String.format("%03d", count_MISSIONFAILURES);
					String refId3 = "A531D277-017E-4126-B99E-BA7EA0D39";
					refId3 += String.format("%03d", count_MISSIONFAILURES);
					String refId4 = "1DBDDEDC-602A-4417-8E1A-0215F4A29";
					refId4 += String.format("%03d", count_MISSIONFAILURES);

					// ACTIONS
					ss = ss.replace("##REF_ID_TRIGGER##", refId1);
					String triggerName = "AltitudeTriggerLimit" + count_MISSIONFAILURES;
					mfe.triggerId = triggerName;
					mfe.triggerGUID = refId1;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##ALTITUDEMODE##", mfe.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "AMSL" : "AGL") : mfe.agl.equals("False") ? "AMSL" : "AGL");
					ss = ss.replace("##HEIGHT_TRIGGER##", mfe.value1);
					ss = ss.replace("##DESCR_ACTION##", "ACT_FailGoal");
					ss = ss.replace("##REF_ID_ACTION##", refId3);
					ss = ss.replace("##TOGGLE_ACTIONS##", "");
					ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");
					sb_ACTIONS.append(System.lineSeparator());
					sb_ACTIONS.append(ss);

					// OBJECTIVES
					String st = XML_OBJECTIVE;
					sb_OBJECTIVES.append(System.lineSeparator());
					st = st.replace("##REF_ID_GOAL##", refId2);
					st = st.replace("##FAILURETEXT_GOAL##", "TOO HIGH UP!");
					sb_OBJECTIVES.append(st);

					// GOALS
					sb_GOALS.append(System.lineSeparator());
					sb_GOALS.append(System.lineSeparator());
					String su = XML_GOAL;
					su = su.replace("##REF_ID_GOAL##", refId2);
					su = su.replace("##REF_ID_GOALPASSACTION##", refId4);
					su = su.replace("##REF_ID_GOALFAILACTION##", refId3);
					sb_GOALS.append(su);

					// Finished actions
					sb_FINISHEDACTIONS.append(System.lineSeparator());
					sb_FINISHEDACTIONS.append("        <WorldBase.ObjectReference id=\"End Of Mission\" InstanceId=\"{" + refId4 + "}\" />");
				}

				if (mfe.currentMode == MissionFailureEntryMode.SPEED) {
					String ss = XML_SPEEDTRIGGER;
					ss = ss.replace("##ACTION##", "");

					String refId1 = "DF6CF193-6779-4A24-A30C-453C2F7E1";
					refId1 += String.format("%03d", count_MISSIONFAILURES);
					String refId2 = "CF794CE7-8AC1-40BB-ACC1-A4788D260";
					refId2 += String.format("%03d", count_MISSIONFAILURES);
					String refId3 = "1861CAFB-5321-4E3D-8F32-A90F58B3A";
					refId3 += String.format("%03d", count_MISSIONFAILURES);
					String refId4 = "0A46C3F9-9613-44D4-92C8-E91FB02B4";
					refId4 += String.format("%03d", count_MISSIONFAILURES);

					// ACTIONS
					ss = ss.replace("##REF_ID_TRIGGER##", refId1);
					String triggerName = "SpeedTriggerLimit" + count_MISSIONFAILURES;
					mfe.triggerId = triggerName;
					mfe.triggerGUID = refId1;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##SPEED_TRIGGER##", mfe.value1);
					ss = ss.replace("##DESCR_ACTION##", "ACT_FailGoal");
					ss = ss.replace("##REF_ID_ACTION##", refId3);
					ss = ss.replace("##TOGGLE_ACTIONS##", "");
					ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");
					sb_ACTIONS.append(System.lineSeparator());
					sb_ACTIONS.append(ss);

					// OBJECTIVES
					String st = XML_OBJECTIVE;
					sb_OBJECTIVES.append(System.lineSeparator());
					st = st.replace("##REF_ID_GOAL##", refId2);
					st = st.replace("##FAILURETEXT_GOAL##", "TOO FAST!");
					sb_OBJECTIVES.append(st);

					// GOALS
					sb_GOALS.append(System.lineSeparator());
					sb_GOALS.append(System.lineSeparator());
					String su = XML_GOAL;
					su = su.replace("##REF_ID_GOAL##", refId2);
					su = su.replace("##REF_ID_GOALPASSACTION##", refId4);
					su = su.replace("##REF_ID_GOALFAILACTION##", refId3);
					sb_GOALS.append(su);

					// Finished actions
					sb_FINISHEDACTIONS.append(System.lineSeparator());
					sb_FINISHEDACTIONS.append("        <WorldBase.ObjectReference id=\"End Of Mission\" InstanceId=\"{" + refId4 + "}\" />");
				}

				if (mfe.currentMode == MissionFailureEntryMode.ALTITUDE_AND_SPEED) {
					String ss = XML_ALTITUDESPEEDTRIGGER;
					ss = ss.replace("##ACTION##", "");

					String refId1 = "6597EED8-DF9E-4383-B021-894525A5C";
					refId1 += String.format("%03d", count_MISSIONFAILURES);
					String refId2 = "0A7CEB60-809F-4AF3-AD5A-DAB7BC482";
					refId2 += String.format("%03d", count_MISSIONFAILURES);
					String refId3 = "A362DAB2-DF87-4ABE-B7DC-DA1FF7D81";
					refId3 += String.format("%03d", count_MISSIONFAILURES);
					String refId4 = "03BC1996-2676-4133-A97D-C96B13533";
					refId4 += String.format("%03d", count_MISSIONFAILURES);

					// ACTIONS
					ss = ss.replace("##REF_ID_TRIGGER##", refId1);
					String triggerName = "AltitudeSpeedTriggerLimit" + count_MISSIONFAILURES;
					mfe.triggerId = triggerName;
					mfe.triggerGUID = refId1;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##ALTITUDEMODE##", mfe.agl.isEmpty() ? (metaEntry.useAGL.isEmpty() ? "AMSL" : "AGL") : mfe.agl.equals("False") ? "AMSL" : "AGL");
					ss = ss.replace("##HEIGHT_TRIGGER##", mfe.value1);
					ss = ss.replace("##SPEED_TRIGGER##", mfe.value2);
					ss = ss.replace("##DESCR_ACTION##", "ACT_FailGoal");
					ss = ss.replace("##REF_ID_ACTION##", refId3);
					ss = ss.replace("##TOGGLE_ACTIONS##", "");
					ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");
					sb_ACTIONS.append(System.lineSeparator());
					sb_ACTIONS.append(ss);

					// OBJECTIVES
					String st = XML_OBJECTIVE;
					sb_OBJECTIVES.append(System.lineSeparator());
					st = st.replace("##REF_ID_GOAL##", refId2);
					st = st.replace("##FAILURETEXT_GOAL##", "TOO HIGH UP AND TOO FAST!");
					sb_OBJECTIVES.append(st);

					// GOALS
					sb_GOALS.append(System.lineSeparator());
					sb_GOALS.append(System.lineSeparator());
					String su = XML_GOAL;
					su = su.replace("##REF_ID_GOAL##", refId2);
					su = su.replace("##REF_ID_GOALPASSACTION##", refId4);
					su = su.replace("##REF_ID_GOALFAILACTION##", refId3);
					sb_GOALS.append(su);

					// Finished actions
					sb_FINISHEDACTIONS.append(System.lineSeparator());
					sb_FINISHEDACTIONS.append("        <WorldBase.ObjectReference id=\"End Of Mission\" InstanceId=\"{" + refId4 + "}\" />");
				}

				if (mfe.currentMode == MissionFailureEntryMode.TIME) {
					String ss = XML_TIMERTRIGGER;
					ss = ss.replace("##ACTION##", "");

					String refId1 = "F21AA39E-FA25-46ED-89FE-570BC30ED";
					refId1 += String.format("%03d", count_MISSIONFAILURES);
					String refId2 = "E86EF2FE-0357-43FA-B51F-97EC10CA6";
					refId2 += String.format("%03d", count_MISSIONFAILURES);
					String refId3 = "6F8B8424-29BE-48E4-B3DC-690F0694D";
					refId3 += String.format("%03d", count_MISSIONFAILURES);
					String refId4 = "35375727-C95C-431B-ABDD-0B7DA0FED";
					refId4 += String.format("%03d", count_MISSIONFAILURES);
					String refId5 = "ADB0CDCD-5C03-4DD3-9201-0F46B74B0";
					refId5 += String.format("%03d", count_MISSIONFAILURES);

					// ACTIONS
					ss = ss.replace("##REF_ID_TRIGGER##", refId1);
					String triggerName = "TimerTriggerLimit" + count_MISSIONFAILURES;
					mfe.triggerId = triggerName;
					mfe.triggerGUID = refId1;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##DELAY_TRIGGER##", mfe.value1);
					ss = ss.replace("##DESCR_ACTION##", "ACT_FailGoal");
					ss = ss.replace("##REF_ID_ACTION##", refId3);
					ss = ss.replace("##ONSCREEN_TRIGGER##", "True");
					ss = ss.replace("##TOGGLE_ACTIONS##", "");
					ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");
					sb_ACTIONS.append(System.lineSeparator());
					sb_ACTIONS.append(ss);

					// OBJECTIVES
					String st = XML_OBJECTIVE;
					sb_OBJECTIVES.append(System.lineSeparator());
					st = st.replace("##REF_ID_GOAL##", refId2);
					st = st.replace("##FAILURETEXT_GOAL##", "TIME OUT!");
					sb_OBJECTIVES.append(st);

					// GOALS
					sb_GOALS.append(System.lineSeparator());
					sb_GOALS.append(System.lineSeparator());
					String su = XML_GOAL;
					su = su.replace("##REF_ID_GOAL##", refId2);
					su = su.replace("##REF_ID_GOALPASSACTION##", refId4);
					su = su.replace("##REF_ID_GOALFAILACTION##", refId3);
					sb_GOALS.append(su);

					// Reset action!
					sb_GOALS.append(System.lineSeparator());
					sb_GOALS.append(System.lineSeparator());
					String sv = XML_RESETACTION;
					sv = sv.replace("##REF_ID_RESET##", refId5);
					sv = sv.replace("##DESCR_RESET##", "ResetTimerAction" + count_MISSIONFAILURES);
					sv = sv.replace("##DESCR_TRIGGER##", "TimerTriggerLimit" + count_MISSIONFAILURES);
					sv = sv.replace("##REF_ID_TRIGGER##", refId1);
					sb_GOALS.append(sv);

					// Finished actions
					sb_FINISHEDACTIONS.append(System.lineSeparator());
					sb_FINISHEDACTIONS.append("        <WorldBase.ObjectReference id=\"End Of Mission\" InstanceId=\"{" + refId4 + "}\" />");
				}

				if (mfe.currentMode == MissionFailureEntryMode.FORMULA) {
					String ss = XML_FORMULATRIGGER;
					ss = ss.replace("##ACTION##", "");

					String refId1 = "D2FC8BD7-5ED8-4B16-9379-559804080";
					refId1 += String.format("%03d", count_MISSIONFAILURES);
					String refId2 = "41DFF23F-7743-4B42-928B-B7897DBD8";
					refId2 += String.format("%03d", count_MISSIONFAILURES);
					String refId3 = "6C3FDBD8-ECC9-47A8-BD79-33DE0F2FC";
					refId3 += String.format("%03d", count_MISSIONFAILURES);
					String refId4 = "972FAFBD-BD94-4827-B58F-A43D65389";
					refId4 += String.format("%03d", count_MISSIONFAILURES);

					// ACTIONS
					ss = ss.replace("##REF_ID_TRIGGER##", refId1);
					String triggerName = "FormulaTriggerLimit" + count_MISSIONFAILURES;
					mfe.triggerId = triggerName;
					mfe.triggerGUID = refId1;
					ss = ss.replace("##DESCR_TRIGGER##", triggerName);
					ss = ss.replace("##ONESHOT_TRIGGER##", metaEntry.useOneShotTriggers.isEmpty() ? "False" : "True");
					ss = ss.replace("##FORMULA_TRIGGER##", mfe.value1);
					ss = ss.replace("##DESCR_ACTION##", "ACT_FailGoal");
					ss = ss.replace("##REF_ID_ACTION##", refId3);
					ss = ss.replace("##TOGGLE_ACTIONS##", "");
					ss = ss.replace("##TOGGLE_TRIGGERS##", "");
					ss = ss.replace("##TOGGLE_COUNT_ACTIONS##", "");
					sb_ACTIONS.append(System.lineSeparator());
					sb_ACTIONS.append(ss);

					// OBJECTIVES
					String st = XML_OBJECTIVE;
					sb_OBJECTIVES.append(System.lineSeparator());
					st = st.replace("##REF_ID_GOAL##", refId2);
					if (!mfe.value2.isEmpty()) {
						st = st.replace("##FAILURETEXT_GOAL##", mfe.value2);
					} else {
						st = st.replace("##FAILURETEXT_GOAL##", "Formula triggered!");
					}
					sb_OBJECTIVES.append(st);

					// GOALS
					sb_GOALS.append(System.lineSeparator());
					sb_GOALS.append(System.lineSeparator());
					String su = XML_GOAL;
					su = su.replace("##REF_ID_GOAL##", refId2);
					su = su.replace("##REF_ID_GOALPASSACTION##", refId4);
					su = su.replace("##REF_ID_GOALFAILACTION##", refId3);
					sb_GOALS.append(su);

					// Finished actions
					sb_FINISHEDACTIONS.append(System.lineSeparator());
					sb_FINISHEDACTIONS.append("        <WorldBase.ObjectReference id=\"End Of Mission\" InstanceId=\"{" + refId4 + "}\" />");
				}
			}
		}

		// Count triggers
		StringBuffer sb_COUNTERS = new StringBuffer();
		if (!metaEntry.counterToggleTriggers.isEmpty()) {
			sb_COUNTERS.append(System.lineSeparator());

			String ca = XML_COUNTACTION;
			String ct = XML_COUNTERTRIGGER;
			int count = 0;

			for (String key : metaEntry.counterToggleTriggers.keySet()) {
				ToggleTrigger tt = metaEntry.counterToggleTriggers.get(key);
				int toggleCount = key.split(",").length;
				boolean hasText = !tt.text.isEmpty();

				String refId1 = "ED1B10BC-DAB4-4D39-960F-E70B6F06D";
				refId1 += String.format("%03d", count + 1);
				String refId2 = "0D9F1CC9-E671-4142-A867-57498CD9C";
				refId2 += String.format("%03d", count + 1);
				String refId3 = "A88D41F8-D419-4323-A1AE-D9267F409";
				refId3 += String.format("%03d", count + 1);

				ca = ca.replace("##REF_ID_ACTION##", refId1);
				ca = ca.replace("##DESCR_ACTION##", "CounterAction" + (count + 1));
				ca = ca.replace("##LIST_TRIGGERS##", System.lineSeparator() + "			<ObjectReference id=\"" +  "CounterTrigger" + (count + 1) + "\" InstanceId=\"{" + refId2 + "}\" />");

				ct = ct.replace("##REF_ID_TRIGGER##", refId2);
				ct = ct.replace("##DESCR_TRIGGER##", "CounterTrigger" + (count + 1));
				ct = ct.replace("##ACTIVATED_TRIGGER##", "false");
				ct = ct.replace("##STOPCOUNT_TRIGGER##", String.valueOf(toggleCount));

				String actions = System.lineSeparator() + "			<ObjectReference id=\"" +  "CounterToggleAction" + (count + 1) + "\" InstanceId=\"{" + refId3 + "}\" />";
				if (hasText) {
					String refId7 = "DEA53119-44DC-47E6-A605-7B75812A9";
					refId7 += String.format("%03d", count + 1);
					actions += System.lineSeparator() + "			<ObjectReference id=\"" + "CounterToggleDialog" + (count + 1) + "\" InstanceId=\"{" + refId7 + "}\"/>";

					// Wav subtitles?
					String wav = tt.procWave;
					String text = tt.procTextID;

					String textXML = "";
					if (!wav.isEmpty()) {
						textXML += "<SoundFileName>" + wav + "</SoundFileName>";
						if (text != null) {
							textXML += System.lineSeparator() + "      " + "<Text>" + text + "</Text>";
						}
						mSounds.add(wav);
					} else {
						textXML += "<Text>" + text + "</Text>";
					}

					String ss = XML_DIALOGACTION;
					ss = ss.replace("##REF_ID_DIALOG##", refId7);
					ss = ss.replace("##DESCR_DIALOG##",  "CounterToggleDialog" + (count + 1));
					ss = ss.replace("##TEXT_DIALOG##", textXML);
					ss = ss.replace("##DELAY_DIALOG##",  "0.000");

					sb_COUNTERS.append(ss);
					sb_COUNTERS.append(System.lineSeparator());
				}
				ct = ct.replace("##LIST_ACTIONS##", actions);

				StringBuffer toggleTriggers = new StringBuffer();
				for (String tl : tt.mTriggerList) {
					String sr = "            <ObjectReference id=\"" + tl + "_ccid\" InstanceId=\"{" + tl + "_ccguid}\"/>";
					toggleTriggers.append(System.lineSeparator()).append(sr);
				}

				String so = XML_OBJECTACTIVATIONACTION;
				so = so.replace("##REF_ID_ACTION##", refId3);
				so = so.replace("##DESCR_ACTION##", "CounterToggleAction" + (count + 1));
				so = so.replace("##STATE_ACTION##", String.valueOf(tt.mActivate));
				so = so.replace("##LIST_TRIGGERS##", toggleTriggers);

				sb_COUNTERS.append(System.lineSeparator());
				sb_COUNTERS.append(ca);
				sb_COUNTERS.append(System.lineSeparator());
				sb_COUNTERS.append(ct);
				sb_COUNTERS.append(System.lineSeparator());
				sb_COUNTERS.append(so);
				sb_COUNTERS.append(System.lineSeparator());

				count++;
			}
		}

		// To be able to manipulate the dialog data
		String text_COUNTERS = sb_COUNTERS.toString();

		// To be able to manipulate the dialog data
		String text_DIALOGS = sb_DIALOGS.toString();

		// To be able to manipulate the failure data
		String text_FAILURES = sb_FAILURES.toString();

		// To be able to manipulate the warning data
		String text_WARNINGS = sb_WARNINGS.toString();

		// To be able to manipulate the mission failure data
		String text_ACTIONS = sb_ACTIONS.toString();

		// DialogEntry triggers
		if (!metaEntry.dialogEntries.isEmpty()) {
			for (DialogEntry de : metaEntry.dialogEntries) {
				if (de.mName != null) {
					String find1 = "\"" + de.mName + "_id\"";
					String find2 = "\\{" + de.mName + "_guid\\}";
					String replace1 = "\"" + de.triggerId + "\"";
					String replace2 = "{" + de.triggerGUID + "}";
					text_DIALOGS = text_DIALOGS.replaceAll(find1, replace1);
					text_DIALOGS = text_DIALOGS.replaceAll(find2, replace2);
					text_FAILURES = text_FAILURES.replaceAll(find1, replace1);
					text_FAILURES = text_FAILURES.replaceAll(find2, replace2);
					text_WARNINGS = text_WARNINGS.replaceAll(find1, replace1);
					text_WARNINGS = text_WARNINGS.replaceAll(find2, replace2);
					text_ACTIONS = text_ACTIONS.replaceAll(find1, replace1);
					text_ACTIONS = text_ACTIONS.replaceAll(find2, replace2);
				}
				if (de.mName != null) {
					String find1 = "\"" + de.mName + "_ccid\"";
					String find2 = "\\{" + de.mName + "_ccguid\\}";
					String replace1 = "\"" + de.triggerId + "\"";
					String replace2 = "{" + de.triggerGUID + "}";
					text_COUNTERS = text_COUNTERS.replaceAll(find1, replace1);
					text_COUNTERS = text_COUNTERS.replaceAll(find2, replace2);
				}
			}
		}

		// FailureEntry triggers
		if (!metaEntry.failureEntries.isEmpty()) {
			for (FailureEntry fe : metaEntry.failureEntries) {
				if (fe.mName != null) {
					String find1 = "\"" + fe.mName + "_id\"";
					String find2 = "\\{" + fe.mName + "_guid\\}";
					String replace1 = "\"" + fe.triggerId + "\"";
					String replace2 = "{" + fe.triggerGUID + "}";
					text_DIALOGS = text_DIALOGS.replaceAll(find1, replace1);
					text_DIALOGS = text_DIALOGS.replaceAll(find2, replace2);
					text_FAILURES = text_FAILURES.replaceAll(find1, replace1);
					text_FAILURES = text_FAILURES.replaceAll(find2, replace2);
					text_WARNINGS = text_WARNINGS.replaceAll(find1, replace1);
					text_WARNINGS = text_WARNINGS.replaceAll(find2, replace2);
					text_ACTIONS = text_ACTIONS.replaceAll(find1, replace1);
					text_ACTIONS = text_ACTIONS.replaceAll(find2, replace2);
				}
				if (fe.mName != null) {
					String find1 = "\"" + fe.mName + "_ccid\"";
					String find2 = "\\{" + fe.mName + "_ccguid\\}";
					String replace1 = "\"" + fe.triggerId + "\"";
					String replace2 = "{" + fe.triggerGUID + "}";
					text_COUNTERS = text_COUNTERS.replaceAll(find1, replace1);
					text_COUNTERS = text_COUNTERS.replaceAll(find2, replace2);
				}
			}
		}

		// WarningEntry triggers
		if (!metaEntry.warningsEntries.isEmpty()) {
			for (WarningEntry we : metaEntry.warningsEntries) {
				if (we.mName != null) {
					String find1 = "\"" + we.mName + "_id\"";
					String find2 = "\\{" + we.mName + "_guid\\}";
					String replace1 = "\"" + we.triggerId + "\"";
					String replace2 = "{" + we.triggerGUID + "}";
					text_DIALOGS = text_DIALOGS.replaceAll(find1, replace1);
					text_DIALOGS = text_DIALOGS.replaceAll(find2, replace2);
					text_FAILURES = text_FAILURES.replaceAll(find1, replace1);
					text_FAILURES = text_FAILURES.replaceAll(find2, replace2);
					text_WARNINGS = text_WARNINGS.replaceAll(find1, replace1);
					text_WARNINGS = text_WARNINGS.replaceAll(find2, replace2);
					text_ACTIONS = text_ACTIONS.replaceAll(find1, replace1);
					text_ACTIONS = text_ACTIONS.replaceAll(find2, replace2);
				}
				if (we.mName != null) {
					String find1 = "\"" + we.mName + "_ccid\"";
					String find2 = "\\{" + we.mName + "_ccguid\\}";
					String replace1 = "\"" + we.triggerId + "\"";
					String replace2 = "{" + we.triggerGUID + "}";
					text_COUNTERS = text_COUNTERS.replaceAll(find1, replace1);
					text_COUNTERS = text_COUNTERS.replaceAll(find2, replace2);
				}
			}
		}

		// MissionFailureEntry triggers
		if (!metaEntry.missionFailures.isEmpty()) {
			for (MissionFailureEntry mfe : metaEntry.missionFailures) {
				if (mfe.mName != null) {
					String find1 = "\"" + mfe.mName + "_id\"";
					String find2 = "\\{" + mfe.mName + "_guid\\}";
					String replace1 = "\"" + mfe.triggerId + "\"";
					String replace2 = "{" + mfe.triggerGUID + "}";
					text_DIALOGS = text_DIALOGS.replaceAll(find1, replace1);
					text_DIALOGS = text_DIALOGS.replaceAll(find2, replace2);
					text_FAILURES = text_FAILURES.replaceAll(find1, replace1);
					text_FAILURES = text_FAILURES.replaceAll(find2, replace2);
					text_WARNINGS = text_WARNINGS.replaceAll(find1, replace1);
					text_WARNINGS = text_WARNINGS.replaceAll(find2, replace2);
					text_ACTIONS = text_ACTIONS.replaceAll(find1, replace1);
					text_ACTIONS = text_ACTIONS.replaceAll(find2, replace2);
				}
				if (mfe.mName != null) {
					String find1 = "\"" + mfe.mName + "_ccid\"";
					String find2 = "\\{" + mfe.mName + "_ccguid\\}";
					String replace1 = "\"" + mfe.triggerId + "\"";
					String replace2 = "{" + mfe.triggerGUID + "}";
					text_COUNTERS = text_COUNTERS.replaceAll(find1, replace1);
					text_COUNTERS = text_COUNTERS.replaceAll(find2, replace2);
				}
			}
		}

		if (!metaEntry.counterToggleTriggers.isEmpty()) {
			int count = 0;

			for (String key : metaEntry.counterToggleTriggers.keySet()) {
				String refId1 = "ED1B10BC-DAB4-4D39-960F-E70B6F06D";
				refId1 += String.format("%03d", count + 1);

				String find1 = "\"" + key + "_cid\"";
				String find2 = "\\{" + key + "_cguid\\}";
				String replace1 = "\"" + "CounterAction" + (count + 1) + "\"";
				String replace2 = "{" + refId1 + "}";
				text_DIALOGS = text_DIALOGS.replaceAll(find1, replace1);
				text_DIALOGS = text_DIALOGS.replaceAll(find2, replace2);
				text_FAILURES = text_FAILURES.replaceAll(find1, replace1);
				text_FAILURES = text_FAILURES.replaceAll(find2, replace2);
				text_WARNINGS = text_WARNINGS.replaceAll(find1, replace1);
				text_WARNINGS = text_WARNINGS.replaceAll(find2, replace2);
				text_ACTIONS = text_ACTIONS.replaceAll(find1, replace1);
				text_ACTIONS = text_ACTIONS.replaceAll(find2, replace2);

				count++;
			}
		}

		StringBuffer sb_DISABLE_STUFF = new StringBuffer();
		StringBuffer sb_SHOW_STUFF = new StringBuffer();
		StringBuffer sb_CALCULATOR_STUFF = new StringBuffer();
		StringBuffer sb_FLOWEVENT_STUFF = new StringBuffer();
		if (!metaEntry.showNavLog.isEmpty()) {
			sb_SHOW_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_GAME_NAVLOG_SHOW\" />");
		} else {
			sb_DISABLE_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_GAME_NAVLOG_FORCE_DISABLED\" />");
		}
		if (!metaEntry.showVfrMap.isEmpty()) {
			sb_SHOW_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_VFR_MAP_SHOW\" />");
		} else {
			sb_DISABLE_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_VFR_MAP_FORCE_DISABLED\" />");
		}

		if (!metaEntry.enableRefueling.isEmpty()) {
			sb_DISABLE_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_FUEL_PAYLOAD_FORCE_ENABLED\" />");

			sb_CALCULATOR_STUFF.append(System.lineSeparator()).append("        <CalculatorAction>");
			sb_CALCULATOR_STUFF.append(System.lineSeparator()).append("          <CalculatorFormula>");
			sb_CALCULATOR_STUFF.append(System.lineSeparator()).append("          [OnGround] [FlyingOutOfFuel] not and");
			sb_CALCULATOR_STUFF.append(System.lineSeparator()).append("          </CalculatorFormula>");
			sb_CALCULATOR_STUFF.append(System.lineSeparator()).append("          <Actions>");
			sb_CALCULATOR_STUFF.append(System.lineSeparator()).append("            <WorldBase.ObjectReference id=\"FlowEvent_EnableFuel\" InstanceId=\"{09B64DD4-77E5-4A41-BB2C-E1188175B93D}\" />");
			sb_CALCULATOR_STUFF.append(System.lineSeparator()).append("          </Actions>");
			sb_CALCULATOR_STUFF.append(System.lineSeparator()).append("        </CalculatorAction>");

			sb_FLOWEVENT_STUFF.append(System.lineSeparator()).append("    <SimMission.FlowEventAction InstanceId=\"{09B64DD4-77E5-4A41-BB2C-E1188175B93D}\">");
			sb_FLOWEVENT_STUFF.append(System.lineSeparator()).append("     <Descr>FlowEvent_EnableFuel</Descr>");
			sb_FLOWEVENT_STUFF.append(System.lineSeparator()).append("     <FlowEvents>");
			sb_FLOWEVENT_STUFF.append(System.lineSeparator()).append("       <FlowEvent id=\"PANEL_FUEL_PAYLOAD_FORCE_ENABLED\" />");
			sb_FLOWEVENT_STUFF.append(System.lineSeparator()).append("     </FlowEvents>");
			sb_FLOWEVENT_STUFF.append(System.lineSeparator()).append("    </SimMission.FlowEventAction>");
		} else {
			sb_DISABLE_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_FUEL_PAYLOAD_FORCE_DISABLED\" />");
		}

		if (!metaEntry.enableAtc.isEmpty()) {
			sb_DISABLE_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_ATC_FORCE_ENABLED\" />");
		}

		if (!metaEntry.enableChecklist.isEmpty()) {
			sb_DISABLE_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_CHECKLIST_FORCE_ENABLED\" />");
		} else {
			sb_DISABLE_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_CHECKLIST_FORCE_DISABLED\" />");
		}

		if (!metaEntry.enableObjectives.isEmpty()) {
			sb_DISABLE_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_OBJECTIVES_FORCE_ENABLED\" />");
		} else {
			sb_DISABLE_STUFF.append(System.lineSeparator()).append("        <FlowEvent id=\"PANEL_OBJECTIVES_FORCE_DISABLED\" />");
		}

		XML_FILE = XML_FILE.replace("##META_PROJECT##", metaEntry.project);
		XML_FILE = XML_FILE.replace("##META_TITLE##", metaEntry.titleID);
		XML_FILE = XML_FILE.replace("##LEGS##", sb.toString());
		XML_FILE = XML_FILE.replace("##CALCS##", sb_CALCS);
		XML_FILE = XML_FILE.replace("##ACTIONS##", text_ACTIONS);
		XML_FILE = XML_FILE.replace("##OBJECTIVES##", sb_OBJECTIVES);
		XML_FILE = XML_FILE.replace("##GOALS##", sb_GOALS);
		XML_FILE = XML_FILE.replace("##FINISHEDACTIONS##", sb_FINISHEDACTIONS);
		XML_FILE = XML_FILE.replace("##INTRODIALOG##", sb_INTRODIALOG);
		XML_FILE = XML_FILE.replace("##DIALOGS##", text_DIALOGS);
		XML_FILE = XML_FILE.replace("##FAILURES##", text_FAILURES);
		XML_FILE = XML_FILE.replace("##WARNINGS##", text_WARNINGS);
		XML_FILE = XML_FILE.replace("##LANDINGACTIONS##", sb_LANDINGACTIONS);
		XML_FILE = XML_FILE.replace("##COUNTERS##", text_COUNTERS);
		XML_FILE = XML_FILE.replace("##DISABLE_STUFF##", sb_DISABLE_STUFF);
		XML_FILE = XML_FILE.replace("##SHOW_STUFF##", sb_SHOW_STUFF);
		XML_FILE = XML_FILE.replace("##CALCULATOR_STUFF##", sb_CALCULATOR_STUFF);
		XML_FILE = XML_FILE.replace("##FLOWEVENT_STUFF##", sb_FLOWEVENT_STUFF);

		Message msg = mFileHandling.writeStringToFile(outFile, XML_FILE, cs);
		if (msg != null) {
			return msg;
		}

		return null;
	}

	private Message handleLandXML(MetaEntry metaEntry, List<MissionEntry> entries, String inFile, String outFile) {
		Charset cs = Charset.forName("windows-1252");
		String XML_FILE = mFileHandling.readFileToString(inFile, cs);

		XML_FILE = XML_FILE.replace("##META_PROJECT##", metaEntry.project);
		XML_FILE = XML_FILE.replace("##META_TITLE##", metaEntry.titleID);

		Message msg = mFileHandling.writeStringToFile(outFile, XML_FILE, cs);
		if (msg != null) {
			return msg;
		}

		return null;
	}

	private Message handleFLT(MetaEntry metaEntry, List<MissionEntry> entries, String inFile, String outFile) {
		Charset cs = Charset.forName("windows-1252");
		String FLT_FILE = mFileHandling.readFileToString(inFile, cs);

		StringBuffer sb_BRIEFINGIMAGES = new StringBuffer();
		StringBuffer sb_WAYPOINTS = new StringBuffer();
		StringBuffer sb_WPINFOS = new StringBuffer();
		int count_AIRPORT = 0;
		int count_ENTRY = 0;
		for (MissionEntry entry : entries) {
			// [Briefing]
			if (entry.type2.equals(WpType.AIRPORT)) {
				if (count_ENTRY>0) {
					// Unique airport?
					String id = entry.id;
					if (!metaEntry.uniqueApImages.isEmpty()) {
						id = entry.uniqueId;
					}

					String ss = "BriefingImage" + count_AIRPORT + "=\"./Images/" + id + ".jpg\"";
					sb_BRIEFINGIMAGES.append(ss);
					sb_BRIEFINGIMAGES.append(System.lineSeparator());
					count_AIRPORT++;
				}
			}

			// [ATC_Aircraft.0] + [ATC_ActiveFlightPlan.0]
			StringBuffer ss = new StringBuffer();
			ss.append("Waypoint." + count_ENTRY + "=");
			ss.append((metaEntry.missionType.equals("bush") ? entry.region : "") + ", ");
			ss.append((metaEntry.missionType.equals("bush") ? entry.id : (entry.type2.equals(WpType.AIRPORT) ? entry.id : "")) + ", ");
			ss.append(", ");
			ss.append((entry.nameID) + ", ");
			ss.append((entry.type2.equals(WpType.USER) ? "U" : "A") + ", ");
			ss.append(entry.latlon + ", ");
			ss.append(entry.alt + ", ");
			sb_WAYPOINTS.append(ss);

			// [GPS_Engine]
			StringBuffer st = new StringBuffer();
			st.append("WpInfo" + count_ENTRY + "=");
			st.append(entry.wpInfo);
			sb_WPINFOS.append(st);

			if (count_ENTRY < entries.size()-1) {
				sb_WAYPOINTS.append(System.lineSeparator());
				sb_WPINFOS.append(System.lineSeparator());
			}
			count_ENTRY++;
		}

		FLT_FILE = FLT_FILE.replace("##META_PROJECT##", metaEntry.project);

		// Pilot
		StringBuffer sbP = new StringBuffer();
		if (metaEntry.pilot.isEmpty()) {
			sbP.append("Pilot_Male");
		} else {
			String gender = "Male";
			if (metaEntry.pilot.equals("Female")) {
				gender = metaEntry.pilot;
			}
			sbP.append("Pilot_" + gender);
		}
		FLT_FILE = FLT_FILE.replace("##META_PILOT##", sbP.toString());

		// Copilots
		StringBuffer sbCP = new StringBuffer();
		if (metaEntry.coPilots.isEmpty()) {
			sbCP.append("Copilot.0=Pilot_Male").append(System.lineSeparator());
			sbCP.append("Copilot.1=Pilot_Male").append(System.lineSeparator());
		} else {
			int count = 0;
			for (String cp : metaEntry.coPilots) {
				String gender = "Male";
				if (cp.equals("Female")) {
					gender = cp;
				}
				sbCP.append("Copilot." + (count++) + "=Pilot_" + gender).append(System.lineSeparator());
			}
		}
		FLT_FILE = FLT_FILE.replace("##COPILOTS##", sbCP.toString());

		// Assistance
		if (metaEntry.enableAtc.isEmpty()) {
			FLT_FILE = FLT_FILE.replace("##META_ASSISTANCE##", System.lineSeparator() + "Preset=ASSISTANCE_PRESET_BUSH_TRIP");
		} else {
			FLT_FILE = FLT_FILE.replace("##META_ASSISTANCE##", "");
		}

		// Tooltips
		StringBuffer sbTT = new StringBuffer();
		if (metaEntry.missionType.equals("bush")) {
			String[] tooltipArray = new String[5];
			for (int i=0; i<tooltipArray.length; i++) {
				if (i<metaEntry.tooltips.size()) {
					sbTT.append("Tips" + i + "=" + metaEntry.tooltipsID.get(i)).append(System.lineSeparator());
				} else {
					sbTT.append("Tips" + i + "=TT:LOADING.TIPS.BUSH_TRIPS." + (i+1)).append(System.lineSeparator());
				}
			}
		} else {
			String[] tooltipArray = new String[] {
					"Tips0=TT:LOADING.SPECIFIC_TIPS.LANDING_PRECISION",
					"Tips1=TT:LOADING.SPECIFIC_TIPS.CENTERLINE",
					"Tips2=TT:LOADING.SPECIFIC_TIPS.LANDING_SMOOTHNESS",
					"Tips3=TT:LOADING.SPECIFIC_TIPS.CHALLENGE_003",
					"Tips4=TT:LOADING.TIPS.THROTTLE_001"
			};
			for (int i=0; i<tooltipArray.length; i++) {
				if (i<metaEntry.tooltips.size()) {
					sbTT.append("Tips" + i + "=" + metaEntry.tooltipsID.get(i)).append(System.lineSeparator());
				} else {
					sbTT.append(tooltipArray[i]).append(System.lineSeparator());
				}
			}
		}
		FLT_FILE = FLT_FILE.replace("##META_TOOLTIPS##", sbTT.toString());

		FLT_FILE = FLT_FILE.replace("##META_LOCATION##", metaEntry.locationID);
		FLT_FILE = FLT_FILE.replace("##META_DESCR##", metaEntry.descriptionID);
		FLT_FILE = FLT_FILE.replace("##META_TITLE##", metaEntry.titleID);
		FLT_FILE = FLT_FILE.replace("##META_INTRO##",metaEntry.introID);
		FLT_FILE = FLT_FILE.replace("##META_PLANE##", metaEntry.plane);

		FLT_FILE = FLT_FILE.replace("##META_SIMFILE##", metaEntry.simFile);
		FLT_FILE = FLT_FILE.replace("##META_FUELPERCENTAGE##", metaEntry.fuelPercentage);
		FLT_FILE = FLT_FILE.replace("##META_PARKINGBRAKE##", metaEntry.parkingBrake);
		FLT_FILE = FLT_FILE.replace("##META_TAILNUMBER##", metaEntry.tailNumber);
		FLT_FILE = FLT_FILE.replace("##META_AIRLINECALLSIGN##", metaEntry.airlineCallSign);
		FLT_FILE = FLT_FILE.replace("##META_FLIGHTNUMBER##", metaEntry.flightNumber);
		FLT_FILE = FLT_FILE.replace("##META_APPENDHEAVY##", metaEntry.appendHeavy);

		FLT_FILE = FLT_FILE.replace("##META_FLAPSHANDLE##", metaEntry.flapsHandle);
		FLT_FILE = FLT_FILE.replace("##META_LEFTFLAP##", metaEntry.leftFlap);
		FLT_FILE = FLT_FILE.replace("##META_RIGHTFLAP##", metaEntry.rightFlap);
		FLT_FILE = FLT_FILE.replace("##META_ELEVATORTRIM##", metaEntry.elevatorTrim);

		// Weather
		if (metaEntry.weather.isEmpty() || metaEntry.weather.startsWith("custom")) {
			StringBuffer sb = new StringBuffer();
			sb.append("UseWeatherFile=True");
			FLT_FILE = FLT_FILE.replace("##META_WEATHER##", sb.toString());
		} else if (metaEntry.weather.startsWith("live")) {
			StringBuffer sb = new StringBuffer();
			sb.append("UseWeatherFile=False").append(System.lineSeparator());
			sb.append("UseLiveWeather=True").append(System.lineSeparator());
			sb.append("WeatherPresetFile=").append(System.lineSeparator());
			sb.append("WeatherCanBeLive=True");
			FLT_FILE = FLT_FILE.replace("##META_WEATHER##", sb.toString());
		} else {
			StringBuffer sb = new StringBuffer();
			sb.append("UseWeatherFile=False").append(System.lineSeparator());
			sb.append("UseLiveWeather=False").append(System.lineSeparator());
			sb.append("WeatherPresetFile=" + metaEntry.weather).append(System.lineSeparator());
			sb.append("WeatherCanBeLive=False");
			FLT_FILE = FLT_FILE.replace("##META_WEATHER##", sb.toString());
		}

		// Failures
		String failureTemplate = "[SystemFailure##FAILURE_COUNT##.0]" + System.lineSeparator() +
				"ID=##FAILURE_ID##" + System.lineSeparator() +
				"SubIndex=##FAILURE_SUBINDEX##" + System.lineSeparator() +
				"Health=1" + System.lineSeparator() +
				"Armed=True" + System.lineSeparator() +
				"ArmedFailureFromTime=##FAILURE_FROM##" + System.lineSeparator() +
				"ArmedFailureToTime=##FAILURE_TO##" + System.lineSeparator() + System.lineSeparator();
		int count_FAILURES = 0;
		StringBuffer sbFAIL = new StringBuffer();

		for (FailureEntry fe : metaEntry.failureEntries) {
			if (fe.currentMode == FailureEntryMode.ARM) {
				String failureCode = mSimData.systemToFailureCodeMap.get(fe.system);

				if (failureCode != null) {
					String failure = failureTemplate.replace("##FAILURE_COUNT##", String.valueOf(count_FAILURES++));
					failure = failure.replace("##FAILURE_ID##", failureCode);
					failure = failure.replace("##FAILURE_SUBINDEX##", fe.systemIndex);
					failure = failure.replace("##FAILURE_FROM##", fe.timeFrom);
					failure = failure.replace("##FAILURE_TO##", fe.timeTo);
					sbFAIL.append(failure);
				} else {
					return new ErrorMessage("Could not find failing system: " + fe.system);
				}
			}
		}
		FLT_FILE = FLT_FILE.replace("##META_FAILURES##", sbFAIL.toString());

		FLT_FILE = FLT_FILE.replace("##BRIEFINGIMAGES##", sb_BRIEFINGIMAGES.toString());
		FLT_FILE = FLT_FILE.replace("##WAYPOINTS##", sb_WAYPOINTS.toString());
		FLT_FILE = FLT_FILE.replace("##WAYPOINTS_COUNT##", String.valueOf(count_ENTRY));
		FLT_FILE = FLT_FILE.replace("##WPINFOS##", sb_WPINFOS.toString());
		FLT_FILE = FLT_FILE.replace("##START_RUNWAY##", entries.get(0).runway);
		FLT_FILE = FLT_FILE.replace("##START_ICAO##", entries.get(0).id);
		FLT_FILE = FLT_FILE.replace("##START_AP##", entries.get(0).nameID);
		FLT_FILE = FLT_FILE.replace("##START_LLA##", entries.get(0).latlon + "," + entries.get(0).alt);
		FLT_FILE = FLT_FILE.replace("##START_LLA_SHORT##", entries.get(0).getShortLatlon() + "," + entries.get(0).getShortAlt());
		FLT_FILE = FLT_FILE.replace("##STOP_ICAO##", entries.get(entries.size()-1).id);
		FLT_FILE = FLT_FILE.replace("##STOP_AP##", entries.get(entries.size()-1).nameID);
		FLT_FILE = FLT_FILE.replace("##STOP_LLA##", entries.get(entries.size()-1).latlon + "," + entries.get(entries.size()-1).alt);
		FLT_FILE = FLT_FILE.replace("##STOP_LLA_SHORT##", entries.get(entries.size()-1).getShortLatlon() + "," + entries.get(entries.size()-1).getShortAlt());

		// [SimVars.0]
		FLT_FILE = FLT_FILE.replace("##START_LAT##", metaEntry.getShortLat());
		FLT_FILE = FLT_FILE.replace("##START_LON##", metaEntry.getShortLon());
		FLT_FILE = FLT_FILE.replace("##START_ALT##", metaEntry.getShortAlt());
		FLT_FILE = FLT_FILE.replace("##START_PITCH##", metaEntry.pitch);
		FLT_FILE = FLT_FILE.replace("##START_BANK##", metaEntry.bank);
		FLT_FILE = FLT_FILE.replace("##START_HEADING##", metaEntry.heading);

		// [DateTimeSeason]
		FLT_FILE = FLT_FILE.replace("##META_SEASON##", metaEntry.season);
		FLT_FILE = FLT_FILE.replace("##META_YEAR##", metaEntry.year);
		FLT_FILE = FLT_FILE.replace("##META_DAY##", metaEntry.day);
		FLT_FILE = FLT_FILE.replace("##META_HOURS##", metaEntry.hours);
		FLT_FILE = FLT_FILE.replace("##META_MINUTES##", metaEntry.minutes);
		FLT_FILE = FLT_FILE.replace("##META_SECONDS##", metaEntry.seconds);

		// [MultiPlayer]
		FLT_FILE = FLT_FILE.replace("##META_MULTIPLAYER##", metaEntry.multiPlayer);

		// Landing challenge
		Pattern patternRunway = Pattern.compile("^([\\d]+)([A-Z]?)");
		String runway = "";
		String designator = "NONE";
		Matcher matcherRunway = patternRunway.matcher(entries.get(entries.size()-1).runway);
		if (matcherRunway.find()) {
			runway = matcherRunway.group(1);
			if (matcherRunway.group(2) != null && !matcherRunway.group(2).isEmpty()) {
				designator = matcherRunway.group(2);
			}
		}

		FLT_FILE = FLT_FILE.replace("##META_CHALLENGE_TYPE##", metaEntry.challengeType);
		FLT_FILE = FLT_FILE.replace("##START_VELOCITY##", metaEntry.velocity);
		FLT_FILE = FLT_FILE.replace("##STOP_RUNWAY##", runway);
		FLT_FILE = FLT_FILE.replace("##STOP_DESIGNATOR##", designator);

		// Airliner bush?
		String airlinerBushText = "";
		if (mSimData.airliners.contains(metaEntry.plane)) {
			airlinerBushText = FLT_AIRLINER_BUSH;
		}
		FLT_FILE = FLT_FILE.replace("##META_AIRLINER_BUSH##", airlinerBushText);

		// Airliner landing?
		String airlinerLandText = "";
		if (mSimData.airliners.contains(metaEntry.plane)) {
			airlinerLandText = FLT_AIRLINER_LAND;
		}
		FLT_FILE = FLT_FILE.replace("##META_AIRLINER_LAND##", airlinerLandText);

		Message msg = mFileHandling.writeStringToFile(outFile, FLT_FILE, cs);
		if (msg != null) {
			return msg;
		}

		return null;
	}

	private Message handlePLN(MetaEntry metaEntry, List<MissionEntry> entries, String inFile, String outFile) {
		Charset cs = StandardCharsets.UTF_8;
		String PLN_FILE = mFileHandling.readFileToString(inFile, cs);

		StringBuffer sb = new StringBuffer();
		int count_POI = 0;
		for (MissionEntry entry : entries) {
			if (entry.type2.equals(WpType.AIRPORT)) {
				String ss = entry.runway.isEmpty() ? PLN_ATCWAYPOINTS_V1 : PLN_ATCWAYPOINTS_V2;
				ss = ss.replace("##WP_ID##", entry.id);
				ss = ss.replace("##WP_TYPE##", MissionEntry.TYPE_AIRPORT);
				ss = ss.replace("##WP_LATLON##", entry.latlon);
				ss = ss.replace("##WP_ALT##", entry.alt);
				ss = ss.replace("##WP_RUNWAY##", entry.runway);
				sb.append(ss);
			} else {
				String ss = PLN_ATCWAYPOINTS_V3;
				ss = ss.replace("##WP_ID##", "POI" + multiCount(++count_POI, 0));
				ss = ss.replace("##WP_TYPE##", MissionEntry.TYPE_USER);
				ss = ss.replace("##WP_LATLON##", entry.latlon);
				ss = ss.replace("##WP_ALT##", entry.alt);
				sb.append(ss);
			}
			sb.append(System.lineSeparator());
		}

		PLN_FILE = PLN_FILE.replace("##ATCWAYPOINTS##", sb.toString());
		PLN_FILE = PLN_FILE.replace("##START_ICAO##", entries.get(0).id);
		PLN_FILE = PLN_FILE.replace("##START_LLA##", metaEntry.lat + "," + metaEntry.lon + "," + metaEntry.alt);
		PLN_FILE = PLN_FILE.replace("##STOP_ICAO##", entries.get(entries.size()-1).id);
		PLN_FILE = PLN_FILE.replace("##STOP_LLA##", entries.get(entries.size()-1).latlon + "," + entries.get(entries.size()-1).alt);
		Message msg = mFileHandling.writeStringToFile(outFile, PLN_FILE, cs);
		if (msg != null) {
			return msg;
		}

		return null;
	}

	private Message handleLOC(MetaEntry metaEntry, List<MissionEntry> entries, String inFile, String outFile) {
		Charset cs = StandardCharsets.UTF_8;
		String LOC_FILE = mFileHandling.readFileToString(inFile, cs);

		StringBuffer stringsBuffer = new StringBuffer();

		int count = 1;
		String ss = System.lineSeparator();
		String sl = "";
		stringsBuffer.append(ss);

		// Intro
		ss = LOC_STRING;
		sl = LOC_LANGUAGE;
		ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
		ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
		sl = sl.replace("##LANGUAGE##", "en-US");
		sl = sl.replace("##STRING##", metaEntry.intro);
		for (Localization lc : metaEntry.localizations) {
			if (lc.value2.equalsIgnoreCase("intro")) {
				String sl2 = LOC_LANGUAGE;
				sl2 = sl2.replace("##LANGUAGE##", lc.language);
				sl2 = sl2.replace("##STRING##", lc.value3);
				sl += ",\n" + sl2;
			}
		}
		ss = ss.replace("##LANGUAGES##", sl);
		metaEntry.introID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
		ss += "," + System.lineSeparator();
		stringsBuffer.append(ss);

		// Location
		ss = LOC_STRING;
		sl = LOC_LANGUAGE;
		ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
		ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
		sl = sl.replace("##LANGUAGE##", "en-US");
		sl = sl.replace("##STRING##", metaEntry.location);
		for (Localization lc : metaEntry.localizations) {
			if (lc.value2.equalsIgnoreCase("location")) {
				String sl2 = LOC_LANGUAGE;
				sl2 = sl2.replace("##LANGUAGE##", lc.language);
				sl2 = sl2.replace("##STRING##", lc.value3);
				sl += ",\n" + sl2;
			}
		}
		ss = ss.replace("##LANGUAGES##", sl);
		metaEntry.locationID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
		ss += "," + System.lineSeparator();
		stringsBuffer.append(ss);

		// Title
		ss = LOC_STRING;
		sl = LOC_LANGUAGE;
		ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
		ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
		sl = sl.replace("##LANGUAGE##", "en-US");
		sl = sl.replace("##STRING##", metaEntry.title);
		for (Localization lc : metaEntry.localizations) {
			if (lc.value2.equalsIgnoreCase("title")) {
				String sl2 = LOC_LANGUAGE;
				sl2 = sl2.replace("##LANGUAGE##", lc.language);
				sl2 = sl2.replace("##STRING##", lc.value3);
				sl += ",\n" + sl2;
			}
		}
		ss = ss.replace("##LANGUAGES##", sl);
		metaEntry.titleID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
		ss += "," + System.lineSeparator();
		stringsBuffer.append(ss);

		// Description
		ss = LOC_STRING;
		sl = LOC_LANGUAGE;
		ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
		ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
		sl = sl.replace("##LANGUAGE##", "en-US");
		sl = sl.replace("##STRING##", metaEntry.description);
		for (Localization lc : metaEntry.localizations) {
			if (lc.value2.equalsIgnoreCase("description")) {
				String sl2 = LOC_LANGUAGE;
				sl2 = sl2.replace("##LANGUAGE##", lc.language);
				sl2 = sl2.replace("##STRING##", lc.value3);
				sl += ",\n" + sl2;
			}
		}
		ss = ss.replace("##LANGUAGES##", sl);
		metaEntry.descriptionID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
		ss += "," + System.lineSeparator();
		stringsBuffer.append(ss);

		// Tooltips
		for (int i=0; i<metaEntry.tooltips.size(); i++) {
			ss = LOC_STRING;
			sl = LOC_LANGUAGE;
			ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
			ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
			sl = sl.replace("##LANGUAGE##", "en-US");
			sl = sl.replace("##STRING##", metaEntry.tooltips.get(i));
			int ttCount = 0;
			for (Localization lc : metaEntry.localizations) {
				if (lc.value2.equalsIgnoreCase("tooltip") || lc.value2.equalsIgnoreCase("loadingtip")) {
					if (ttCount % metaEntry.tooltips.size() == i) {
						String sl2 = LOC_LANGUAGE;
						sl2 = sl2.replace("##LANGUAGE##", lc.language);
						sl2 = sl2.replace("##STRING##", lc.value3);
						sl += ",\n" + sl2;
					}
					ttCount++;
				}
			}
			ss = ss.replace("##LANGUAGES##", sl);
			metaEntry.tooltipsID.add("TT:" + metaEntry.project + ".Mission." + String.valueOf(count++));
			ss += "," + System.lineSeparator();
			stringsBuffer.append(ss);
		}

		// WP names
		for (MissionEntry me : entries) {
			ss = LOC_STRING;
			sl = LOC_LANGUAGE;
			ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
			ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
			sl = sl.replace("##LANGUAGE##", "en-US");
			sl = sl.replace("##STRING##", me.name);
			for (Localization lc : me.localizations) {
				String sl2 = LOC_LANGUAGE;
				sl2 = sl2.replace("##LANGUAGE##", lc.language);
				sl2 = sl2.replace("##STRING##", lc.value1);
				sl += ",\n" + sl2;
			}
			ss = ss.replace("##LANGUAGES##", sl);
			me.nameID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
			ss += "," + System.lineSeparator();
			stringsBuffer.append(ss);
		}

		// leg texts
		for (MissionEntry me : entries) {
			if (!me.legText.isEmpty()) {
				ss = LOC_STRING;
				sl = LOC_LANGUAGE;
				ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
				ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
				sl = sl.replace("##LANGUAGE##", "en-US");
				sl = sl.replace("##STRING##", me.legText);
				for (Localization lc : me.localizations) {
					String sl2 = LOC_LANGUAGE;
					sl2 = sl2.replace("##LANGUAGE##", lc.language);
					sl2 = sl2.replace("##STRING##", lc.value2);
					sl += ",\n" + sl2;
				}
				ss = ss.replace("##LANGUAGES##", sl);
				me.legTextID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
				ss += "," + System.lineSeparator();
				stringsBuffer.append(ss);
			}
		}

		// subleg texts
		for (MissionEntry me : entries) {
			if (!me.subLegText.isEmpty()) {
				ss = LOC_STRING;
				sl = LOC_LANGUAGE;
				ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
				ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
				sl = sl.replace("##LANGUAGE##", "en-US");
				sl = sl.replace("##STRING##", me.subLegText);
				for (Localization lc : me.localizations) {
					String sl2 = LOC_LANGUAGE;
					sl2 = sl2.replace("##LANGUAGE##", lc.language);
					sl2 = sl2.replace("##STRING##", lc.value3);
					sl += ",\n" + sl2;
				}
				ss = ss.replace("##LANGUAGES##", sl);
				me.subLegTextID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
				ss += "," + System.lineSeparator();
				stringsBuffer.append(ss);
			}
		}

		// Intro Speeches
		for (DelayedText dt : metaEntry.introSpeeches) {
			if (!dt.text.isEmpty()) {
				ss = LOC_STRING;
				sl = LOC_LANGUAGE;
				ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
				ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
				sl = sl.replace("##LANGUAGE##", "en-US");

				// Wav subtitles?
				String wav = dt.text;
				String subtitles = null;
				if (wav.contains("|")) {
					String[] split = wav.split("\\|");
					wav = split[0];
					subtitles = split[1];
				}

				if (wav.toUpperCase().endsWith(".WAV")) {
					dt.procWave = wav;
					if (subtitles != null) {
						dt.procText = subtitles;
					}
				} else {
					dt.procText = dt.text;
				}

				dt.procTextID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
				sl = sl.replace("##STRING##", dt.procText);
				ss = ss.replace("##LANGUAGES##", sl);
				ss += "," + System.lineSeparator();
				stringsBuffer.append(ss);
			}
		}

		// Dialog Entries
		for (DialogEntry de : metaEntry.dialogEntries) {
			if (!de.text.isEmpty()) {
				ss = LOC_STRING;
				sl = LOC_LANGUAGE;
				ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
				ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
				sl = sl.replace("##LANGUAGE##", "en-US");

				// Wav subtitles?
				String wav = de.text;
				String subtitles = null;
				if (wav.contains("|")) {
					String[] split = wav.split("\\|");
					wav = split[0];
					subtitles = split[1];
				}

				if (wav.toUpperCase().endsWith(".WAV")) {
					de.procWave = wav;
					if (subtitles != null) {
						de.procText = subtitles;
					}
				} else {
					de.procText = de.text;
				}

				de.procTextID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
				sl = sl.replace("##STRING##", de.procText);
				ss = ss.replace("##LANGUAGES##", sl);
				ss += "," + System.lineSeparator();
				stringsBuffer.append(ss);
			}
		}

		// Warning Entries
		for (WarningEntry we : metaEntry.warningsEntries) {
			if (!we.text.isEmpty()) {
				ss = LOC_STRING;
				sl = LOC_LANGUAGE;
				ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
				ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
				sl = sl.replace("##LANGUAGE##", "en-US");

				// Wav subtitles?
				String wav = we.text;
				String subtitles = null;
				if (wav.contains("|")) {
					String[] split = wav.split("\\|");
					wav = split[0];
					subtitles = split[1];
				}

				if (wav.toUpperCase().endsWith(".WAV")) {
					we.procWave = wav;
					if (subtitles != null) {
						we.procText = subtitles;
					}
				} else {
					we.procText = we.text;
				}

				we.procTextID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
				sl = sl.replace("##STRING##", we.procText);
				ss = ss.replace("##LANGUAGES##", sl);
				ss += "," + System.lineSeparator();
				stringsBuffer.append(ss);
			}
		}

		// Finished Entries
		for (String feKey : metaEntry.finishedEntries.keySet()) {
			List<DelayedText> feList = metaEntry.finishedEntries.get(feKey);
			for (DelayedText fe : feList) {
				if (!fe.text.isEmpty()) {
					ss = LOC_STRING;
					sl = LOC_LANGUAGE;
					ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
					ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
					sl = sl.replace("##LANGUAGE##", "en-US");

					// Wav subtitles?
					String wav = fe.text;
					String subtitles = null;
					if (wav.contains("|")) {
						String[] split = wav.split("\\|");
						wav = split[0];
						subtitles = split[1];
					}

					if (wav.toUpperCase().endsWith(".WAV")) {
						fe.procWave = wav;
						if (subtitles != null) {
							fe.procText = subtitles;
						}
					} else {
						fe.procText = fe.text;
					}

					fe.procTextID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
					sl = sl.replace("##STRING##", fe.procText);
					ss = ss.replace("##LANGUAGES##", sl);
					ss += "," + System.lineSeparator();
					stringsBuffer.append(ss);
				}
			}
		}

		// Counter Toggle Triggers
		for (String key : metaEntry.counterToggleTriggers.keySet()) {
			ToggleTrigger tr = metaEntry.counterToggleTriggers.get(key);

			if (!tr.text.isEmpty()) {
				ss = LOC_STRING;
				sl = LOC_LANGUAGE;
				ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
				ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
				sl = sl.replace("##LANGUAGE##", "en-US");

				// Wav subtitles?
				String wav = tr.text;
				String subtitles = null;
				if (wav.contains("|")) {
					String[] split = wav.split("\\|");
					wav = split[0];
					subtitles = split[1];
				}

				if (wav.toUpperCase().endsWith(".WAV")) {
					tr.procWave = wav;
					if (subtitles != null) {
						tr.procText = subtitles;
					}
				} else {
					tr.procText = tr.text;
				}

				tr.procTextID = "TT:" + metaEntry.project + ".Mission." + String.valueOf(count++);
				sl = sl.replace("##STRING##", tr.procText);
				ss = ss.replace("##LANGUAGES##", sl);
				ss += "," + System.lineSeparator();
				stringsBuffer.append(ss);
			}
		}

		// Outro
		ss = LOC_STRING;
		sl = LOC_LANGUAGE;
		ss = ss.replace("##STRING_COUNT##", String.valueOf(count));
		ss = ss.replace("##STRING_COUNT_3PADDED##", String.format("%03d", count));
		sl = sl.replace("##LANGUAGE##", "en-US");
		sl = sl.replace("##STRING##", "Thank you for using BushMissionGen!");
		ss = ss.replace("##LANGUAGES##", sl);
		stringsBuffer.append(ss);

		LOC_FILE = LOC_FILE.replace("##STRINGS##", stringsBuffer.toString()); // MUST BE FIRST!
		LOC_FILE = LOC_FILE.replace("##META_PROJECT##", metaEntry.project);
		LOC_FILE = LOC_FILE.replace("##META_INTRO##", metaEntry.intro);
		LOC_FILE = LOC_FILE.replace("##META_AUTHOR##", metaEntry.author);

		Message msg = mFileHandling.writeStringToFile(outFile, LOC_FILE, cs);
		if (msg != null) {
			return msg;
		}

		return null;
	}

	private Message handleHTM(MetaEntry metaEntry, List<MissionEntry> entries, String inFile, String outFile) {
		Charset cs = StandardCharsets.UTF_8;
		String OVERVIEW_FILE = mFileHandling.readFileToString(inFile, cs);

		OVERVIEW_FILE = OVERVIEW_FILE.replace("##META_TITLE##", metaEntry.title);
		OVERVIEW_FILE = OVERVIEW_FILE.replace("##META_DESCR##", metaEntry.description);

		Message msg = mFileHandling.writeStringToFile(outFile, OVERVIEW_FILE, cs);
		if (msg != null) {
			return msg;
		}

		return null;
	}

	private Message handleProject(MetaEntry metaEntry, List<MissionEntry> entries, String inFile, String outFile) {
		Charset cs = StandardCharsets.UTF_8;
		String PROJECT_FILE = mFileHandling.readFileToString(inFile, cs);

		PROJECT_FILE = PROJECT_FILE.replace("##META_PROJECT##", metaEntry.project);
		PROJECT_FILE = PROJECT_FILE.replace("##META_AUTHOR##", metaEntry.author);

		Message msg = mFileHandling.writeStringToFile(outFile, PROJECT_FILE, cs);
		if (msg != null) {
			return msg;
		}

		return null;
	}

	private Message handlPackage(MetaEntry metaEntry, List<MissionEntry> entries, String inFile, String outFile) {
		Charset cs = StandardCharsets.UTF_8;
		String PACKAGE_FILE = mFileHandling.readFileToString(inFile, cs);

		PACKAGE_FILE = PACKAGE_FILE.replace("##META_PROJECT##", metaEntry.project);
		PACKAGE_FILE = PACKAGE_FILE.replace("##META_AUTHOR##", metaEntry.author);
		PACKAGE_FILE = PACKAGE_FILE.replace("##META_TITLE##", metaEntry.title);
		PACKAGE_FILE = PACKAGE_FILE.replace("##META_VERSION##", metaEntry.version);

		Message msg = mFileHandling.writeStringToFile(outFile, PACKAGE_FILE, cs);
		if (msg != null) {
			return msg;
		}

		return null;
	}

	public Message compileMission(MetaEntry metaEntry, String source, String destination, String sdkPath) {
		String compilerExe = "C:\\MSFS SDK\\Tools\\bin\\fspackagetool.exe";
		int startScan = 0;
		if (!sdkPath.isEmpty()) {
			compilerExe = metaEntry.sdkPath;
			startScan = 1;
		}

		int count = 0;
		while (true) {
			if (new File(compilerExe).exists()) {
				Message msg = mFileHandling.execCmd(compilerExe, "\"" + source + "\"");
				if (msg instanceof InfoMessage) {
					if (BushMissionGen.COMMUNITY_DIR != null) {
						String useCommunityDir = BushMissionGen.COMMUNITY_DIR;
						File useCommunityPath = new File(BushMissionGen.COMMUNITY_DIR);

						int nCPY = JOptionPane.showConfirmDialog(
								null,
								"Do you want to copy the mission to the community folder?\nNote! This overwrites an existing instance.",
								"Compile",
								JOptionPane.YES_NO_OPTION);
						if (nCPY==0) {
							String packageDir = destination + File.separator + "Packages" + File.separator + metaEntry.project;
							File packageDirPath = new File(packageDir);
							String communityProjectDir = useCommunityDir + File.separator + metaEntry.project;
							File communityProjectPath = new File(communityProjectDir);
							if (packageDirPath.exists()) {
								mFileHandling.deleteDirectory(communityProjectPath);
								mFileHandling.copyDirectoryRecursively(packageDir, communityProjectDir);

								String saveFolder = useCommunityPath.getParentFile().getParentFile().getParentFile().getAbsolutePath() + File.separator + "LocalState\\MISSIONS\\ACTIVITIES\\" + metaEntry.project.toUpperCase() + "_SAVE";
								File saveFolderPath = new File(saveFolder);
								if (saveFolderPath.exists()) {
									int nSAVE = JOptionPane.showConfirmDialog(
											null,
											"Delete the SAVE folder too?",
											"Compile",
											JOptionPane.YES_NO_OPTION);
									if (nSAVE==0) {
										mFileHandling.deleteDirectory(saveFolderPath);									
									}
								}
							} else {
								return new ErrorMessage("OUTPUT TO: " + destination + "\\Packages\n\nCould not copy to the community folder!\nWas the compile successful?");
							}

							return new InfoMessage("OUTPUT TO: " + destination + "\\Packages\n\nAnd also to the community folder.");
						} 
					} else {
						return new InfoMessage("Please copy the compiled package manually to your Community folder.\nAnd delete the _SAVE folder! Important!");
					}
				}
				return new InfoMessage("OUTPUT TO: " + destination + "\\Packages");
			} else if (startScan == 1 && count == 0) {
				compilerExe = "C:\\MSFS SDK\\Tools\\bin\\fspackagetool.exe";
			}

			char c=(char)((++count - startScan)+'C');
			compilerExe = c + compilerExe.substring(1);
			if (count-startScan>23) {
				break;
			}
		}
		return new ErrorMessage("Compiler not found on any drive.\nPlease install the SDK in the root folder of a drive.");
	}

	public Message convertPLN(File file) {
		Charset cs = StandardCharsets.UTF_8;
		String PLN_FILE = mFileHandling.readFileToString(file.getAbsolutePath(), cs);

		// MissonType
		String missionType = null;
		Object[] missionTypeList = mSimData.missionTypeList;
		int nMT = JOptionPane.showOptionDialog(mGUI,
				"Which type of mission to create?",
				"Mission type selector",
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				missionTypeList,
				missionTypeList[0]);
		if (nMT <= 0) {
			missionType = "bush";
		} else if (nMT == 1) {
			missionType = "land";
		}

		// ChallengeType
		String challengeType = null;
		if (missionType.equals("land")) {
			Object[] challengeTypeList = mSimData.challengeTypeList;
			int nCT = JOptionPane.showOptionDialog(mGUI,
					"Which type of challenge to create?",
					"Challenge type selector",
					JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE,
					null,
					challengeTypeList,
					challengeTypeList[0]);
			if (nCT >= 0) {
				challengeType = (String)challengeTypeList[nCT];
			} else {
				challengeType = (String)challengeTypeList[0];
			}
		}

		String[] split = PLN_FILE.split("<ATCWaypoint id=", -1);
		if (split != null && split.length>1) {
			// Parse meta
			MetaEntry metaEntry = new MetaEntry();
			boolean headingSet = false;
			String initData = split[0];

			// <DepartureLLA>....</DepartureLLA>
			Pattern patternDLLA = Pattern.compile("<DepartureLLA>(.*)</DepartureLLA>");
			Matcher matcherDLLA = patternDLLA.matcher(initData);
			if (matcherDLLA.find())
			{
				String[] posSplit = matcherDLLA.group(1).split(",");

				Message msgLat = metaEntry.setLat(posSplit[0]);
				if (msgLat != null) {
					return msgLat;
				}
				Message msgLon = metaEntry.setLon(posSplit[1]);
				if (msgLon != null) {
					return msgLon;
				}
				Message msgAlt = metaEntry.setAlt(posSplit[2]);
				if (msgAlt != null) {
					return msgAlt;
				}

				// Strip spaces and zeros
				metaEntry.lat = metaEntry.getShortLat();
				metaEntry.lon = metaEntry.getShortLon();
				metaEntry.alt = metaEntry.getShortAlt();
			} else {
				return new ErrorMessage("Could not parse the <DepartureLLA> field.");
			}

			// <DepartureName>....</DepartureName>
			String departureName = null;
			Pattern patternDEP = Pattern.compile("<DepartureName>(.*)</DepartureName>");
			Matcher matcherDEP = patternDEP.matcher(initData);
			if (matcherDEP.find())
			{
				departureName = matcherDEP.group(1).trim();
			}

			// <DestinationName>....</DestinationName>
			String destinationName = null;
			Pattern patternDEST = Pattern.compile("<DestinationName>(.*)</DestinationName>");
			Matcher matcherDEST = patternDEST.matcher(initData);
			if (matcherDEST.find())
			{
				destinationName = matcherDEST.group(1).trim();
			}

			List<MissionEntry> list = new ArrayList<>();

			// Parse waypoints
			int count_POI = 0;
			for (int i= 1; i<split.length; i++) {
				String s = split[i];
				//System.out.println(s);

				MissionEntry wpEntry = new MissionEntry();

				// "...">
				Pattern pattern = Pattern.compile("^\"([A-Za-z0-9\\s]+)\"");
				Matcher matcher = pattern.matcher(s);
				if (matcher.find())
				{
					wpEntry.name = matcher.group(1);
				} else {
					return new ErrorMessage("Could not parse the id field.");
				}

				if (wpEntry.name.equalsIgnoreCase("TIMECRUIS") || wpEntry.name.equalsIgnoreCase("TIMEDSCNT")) {
					continue;
				}

				// <ATCWaypointType>....</ATCWaypointType>
				pattern = Pattern.compile("<ATCWaypointType>(.*)</ATCWaypointType>");
				matcher = pattern.matcher(s);
				if (matcher.find())
				{
					String preType = matcher.group(1).substring(0, 1).toUpperCase();

					// Skip some types
					if (preType.equals("I")) {
						continue;
					}

					if (preType.equals("A")) {
						wpEntry.type2 = WpType.AIRPORT;
					} else {
						wpEntry.type2 = WpType.USER;
					}
					if (missionType.equals("bush")) {
						if (wpEntry.type2.equals(WpType.USER)) {
							wpEntry.name = "POI" + multiCount(++count_POI, 0);
						}
					} else {
						if (wpEntry.type2.equals(WpType.USER)) {
							wpEntry.name = "CUST" + count_POI++;
						}
					}
				} else {
					return new ErrorMessage("Could not parse the <ATCWaypointType> field.");
				}

				// <WorldPosition>....</WorldPosition>
				pattern = Pattern.compile("<WorldPosition>(.*)</WorldPosition>");
				matcher = pattern.matcher(s);
				if (matcher.find())
				{
					String[] posSplit = matcher.group(1).split(",");
					Message msgLatlon = wpEntry.setLatlon(posSplit[0] + "," + posSplit[1]);
					if (msgLatlon != null) {
						return msgLatlon;
					}
					Message msgAlt = wpEntry.setAlt(posSplit[2]);
					if (msgAlt != null) {
						return msgAlt;
					}
				} else {
					return new ErrorMessage("Could not parse the <WorldPosition> field.");
				}

				// <RunwayNumberFP>....</RunwayNumberFP>
				pattern = Pattern.compile("<RunwayNumberFP>(.*)</RunwayNumberFP>");
				matcher = pattern.matcher(s);
				if (matcher.find())
				{
					wpEntry.runway = matcher.group(1).trim();
					metaEntry.heading = wpEntry.runway + "0";
					headingSet=true;
				}

				// <ICAOIdent>....</ICAOIdent>
				pattern = Pattern.compile("<ICAOIdent>(.*)</ICAOIdent>");
				matcher = pattern.matcher(s);
				if (matcher.find())
				{
					wpEntry.id = matcher.group(1);
				}

				list.add(wpEntry);
			}

			String projectName = null;
			String defaultProjectName = "unknown-" + missionType + "-generated";
			while (true) {
				projectName = JOptionPane.showInputDialog(null, "Leave empty for \"unknown-" + missionType + "-generated\"", "Enter a project name", JOptionPane.INFORMATION_MESSAGE);
				if (projectName == null || projectName.isEmpty()) {
					projectName = defaultProjectName;
				}

				// Fulfill FS2020 package name validation
				Pattern pattern = Pattern.compile("^[a-z0-9]+-[a-z0-9-]+$");
				Matcher matcher = pattern.matcher(projectName);
				if (!matcher.find()) {
					JOptionPane.showMessageDialog(null,
							"Project names must be in the form 'aaa-bbb-...-xxx'\nand only contain lower case letters or digits.",
							"Project name error",
							JOptionPane.ERROR_MESSAGE);
				} else {
					break;
				}
			}

			// Select plane
			Object[] planes = mSimData.planes;
			String selectedPlane = (String)JOptionPane.showInputDialog(
					null,
					"Choose a plane:",
					"Conversion",
					JOptionPane.INFORMATION_MESSAGE,
					null,
					planes,
					planes[0]);

			String plane = (String)planes[0];
			if (selectedPlane != null && selectedPlane.length() > 0) {
				plane = selectedPlane;
			}

			// Select weather
			Object[] weatherTypes = mSimData.weatherTypes;
			String selectedWeather = (String)JOptionPane.showInputDialog(
					null,
					"Choose a weather profile:",
					"Conversion",
					JOptionPane.INFORMATION_MESSAGE,
					null,
					weatherTypes,
					weatherTypes[0]);

			String weatherType = (String)weatherTypes[0];
			if (selectedWeather != null && selectedWeather.length() > 0) {
				weatherType = selectedWeather;
			}
			int sep = weatherType.indexOf(" - ");
			if (sep>=0) {
				weatherType = weatherType.substring(0, sep);
			}

			if (!headingSet) {
				String headingValue = null;
				String defaultHeading = "0";
				while (true) {
					headingValue = JOptionPane.showInputDialog(null, "Leave empty for 0 degrees.", "Enter a start heading", JOptionPane.INFORMATION_MESSAGE);
					if (headingValue == null || headingValue.isEmpty()) {
						headingValue = defaultHeading;
					}

					// Must be a number
					Pattern pattern = Pattern.compile("^[\\d]+$");
					Matcher matcher = pattern.matcher(headingValue);
					if (!matcher.find()) {
						JOptionPane.showMessageDialog(null,
								"Headings must only consist of digits.",
								"Heading value error",
								JOptionPane.ERROR_MESSAGE);
					} else {
						break;
					}
				}
				metaEntry.heading = headingValue;				
			}

			// Location
			String location = "World";
			if (departureName != null && destinationName != null) {
				location = departureName + " to " + destinationName;
			}

			// Title
			String title = list.get(0).id + " to " + list.get(list.size()-1).id;

			// Description
			String description = title;
			if (departureName != null && destinationName != null) {
				description = departureName + " to " + destinationName;
			}

			// Create output file
			StringBuffer sb1 = new StringBuffer();

			sb1.append("# Input file for BushMissonGen").append(System.lineSeparator());
			sb1.append("#").append(System.lineSeparator());
			sb1.append("# Auto-generated in v").append(VERSION).append(System.lineSeparator());
			sb1.append(System.lineSeparator());
			sb1.append("author=unknown").append(System.lineSeparator());
			sb1.append("title=" + title).append(System.lineSeparator());
			sb1.append("project=" + projectName).append(System.lineSeparator());
			sb1.append("version=1.0.0").append(System.lineSeparator());
			sb1.append("location=" + location).append(System.lineSeparator());
			sb1.append("plane=" + plane).append(System.lineSeparator());
			sb1.append("tailNumber=").append(System.lineSeparator());
			sb1.append("airlineCallSign=").append(System.lineSeparator());
			sb1.append("flightNumber=").append(System.lineSeparator());
			sb1.append("introSpeech=").append(System.lineSeparator());
			sb1.append("simFile=runway.FLT").append(System.lineSeparator());
			sb1.append("fuelPercentage=100").append(System.lineSeparator());
			sb1.append("parkingBrake=100.00").append(System.lineSeparator());
			sb1.append("description=" + description).append(System.lineSeparator());
			sb1.append("loadingTip=Generated by BushMissionGen.").append(System.lineSeparator());
			sb1.append("intro=Welcome!").append(System.lineSeparator());
			sb1.append("latitude=" + metaEntry.lat).append(System.lineSeparator());
			sb1.append("longitude=" + metaEntry.lon).append(System.lineSeparator());
			sb1.append("altitude=" + metaEntry.alt).append(System.lineSeparator());
			sb1.append("pitch=0").append(System.lineSeparator());
			sb1.append("bank=0").append(System.lineSeparator());
			sb1.append("heading=" + metaEntry.heading).append(System.lineSeparator());
			sb1.append("weather=" + weatherType).append(System.lineSeparator());
			sb1.append("season=Summer").append(System.lineSeparator());
			sb1.append("year=2018").append(System.lineSeparator());
			sb1.append("day=167").append(System.lineSeparator());
			sb1.append("hours=9").append(System.lineSeparator());
			sb1.append("minutes=35").append(System.lineSeparator());
			sb1.append("seconds=0").append(System.lineSeparator());

			if (missionType.equals("land")) {
				sb1.append("missionType=landing").append(System.lineSeparator());
				sb1.append("challengeType=" + challengeType).append(System.lineSeparator());
				sb1.append("velocity=100").append(System.lineSeparator());
			} else {
				// Check if airports are present more than once
				List<String> airportICAOs = new ArrayList<>();
				for (MissionEntry me : list) {
					if (me.type2.equals(WpType.AIRPORT)) {
						airportICAOs.add(me.id);
					}
				}
				Set<String> checkSet = new HashSet<>();
				checkSet.addAll(airportICAOs);
				if (airportICAOs.size() != checkSet.size()) {
					sb1.append("requireEnginesOff=True").append(System.lineSeparator());
				}
			}

			sb1.append(System.lineSeparator());

			StringBuffer sb2WpHeader = new StringBuffer();
			StringBuffer sb3WpHeader = new StringBuffer();

			if (missionType.equals("bush")) {
				sb2WpHeader.append("#icao rw    name        type            LL                   alt             WpInfo                legtext               sublegtext").append(System.lineSeparator());
				sb3WpHeader.append("#icao|runway|name|type|latitude,longitude|altitude|estimated knots|actual knots|height in meters|actual time enroute|estimated time of arrival|fuel remaining when arrived|estimate of fuel required for the leg|actual fuel used for the leg|leg text|subleg text");
			} else {
				sb2WpHeader.append("#icao rw    name        type            LL                   alt").append(System.lineSeparator());
				sb3WpHeader.append("#icao|runway|name|type|latitude,longitude|altitude");
			}

			StringBuffer sb2 = new StringBuffer();
			List<String[]> sb3 = new ArrayList<String[]>();
			int count_ENTRIES = 0;
			int count_SUBLEG = 0;
			for (MissionEntry me : list) {
				String v1 = me.type2.equals(WpType.AIRPORT) ? me.id : "";
				sb2.append(v1).append("|");

				String v2 = me.runway;
				sb2.append(v2).append("|");

				String v3 = me.name;
				sb2.append(v3).append("|");

				String v4 = me.type2.equals(WpType.USER) ? "U" : "A";
				sb2.append(v4).append("|");

				String v5 = me.latlon;
				sb2.append(v5).append("|");

				String v6 = me.alt;
				String v7 = "126, 0, 304, 0, 0, 0.0, 0.0, 0.0";

				String v8 = "";
				String v9 = "-";

				// Counts
				if (!me.type2.equals(WpType.AIRPORT)) {
					count_SUBLEG++;
				}

				// Row 1 is special
				if (count_ENTRIES == 0 && me.type2.equals(WpType.AIRPORT)) {
					v8 = "Flying from " + me.id;
				}
				// Mid part
				else if (count_ENTRIES > 0 && count_ENTRIES < list.size()-1) {
					if (me.type2.equals(WpType.AIRPORT)) {
						v8 = "Flying from " + me.id;
						v9 = "How to get to airport " + me.id + " and nice info about it.";
					} else {
						v9 = "How to fly to POI" + multiCount(count_SUBLEG, 0) + " and some facts about it.";
					}
				}

				// Last row
				else if (count_ENTRIES == list.size()-1 && me.type2.equals(WpType.AIRPORT)) {
					v9 = "How to get to airport " + me.id + " and nice info about it.";
				}

				if (missionType.equals("bush")) {
					sb2.append(v6).append("|");
					sb2.append(v7).append("|");
					sb2.append(v8).append("|");
					sb2.append(v9);
				} else {
					sb2.append(v6);
				}

				sb2.append(System.lineSeparator());

				if (missionType.equals("bush")) {
					sb3.add(new String[] {v1,v2,v3,v4,v5,v6,v7,v8,v9});
				} else {
					sb3.add(new String[] {v1,v2,v3,v4,v5,v6});
				}

				count_ENTRIES++;
			}	

			// Save file
			Object[] options = {"Text file",
			"XLSX file"};
			int nOPT = JOptionPane.showOptionDialog(mGUI,
					"Which output format?",
					"Save converted PLN data",
					JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE,
					null,
					options,
					options[0]);
			if (nOPT <= 0) {
				String outFile = file.getAbsolutePath().substring(0, file.getAbsolutePath().length()-3) + "txt";
				Message msg = mFileHandling.writeStringToFile(outFile, sb1, sb2WpHeader, sb2, cs);
				if (msg != null) {
					return msg;
				}
				mGUI.mInputPathField.setText(outFile);
				mPOIs = null;
				mSounds = null;
				mCopiedSounds = null;
				mGUI.showFileContents(outFile);
				JOptionPane.showMessageDialog(mGUI, "Input file generated!\n\n" + outFile, "Convert", JOptionPane.INFORMATION_MESSAGE);
			} else if (nOPT == 1) {
				String outFileXLS = file.getAbsolutePath().substring(0, file.getAbsolutePath().length()-3) + "xlsx";
				Message msg = mFileHandling.writeToXLS(outFileXLS, sb1, sb3WpHeader, sb3);
				if (msg != null) {
					return msg;
				}
				mGUI.mInputPathField.setText(outFileXLS);
				mPOIs = null;
				mSounds = null;
				mCopiedSounds = null;
				String contents = String.join(System.lineSeparator(), mFileHandling.readFromXLS(outFileXLS));
				mGUI.mTextArea.setText(contents);
				mGUI.mTextArea.setCaretPosition(0);
				JOptionPane.showMessageDialog(mGUI, "Input file generated!\n\n" + outFileXLS, "Convert", JOptionPane.INFORMATION_MESSAGE);
			}
		}
		return null;
	}
}
