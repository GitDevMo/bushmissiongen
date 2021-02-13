package bushmissiongen.handling;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import bushmissiongen.BushMissionGen;
import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.InfoMessage;
import bushmissiongen.messages.Message;

public class FileHandling {
	public FileHandling() {}
	
	public List<String> readFromXLS(String recept_file) {
		List<String> list = new ArrayList<>();
		try {
			FileInputStream inputStream = new FileInputStream(new File(recept_file));

			Workbook workbook;
			workbook = new XSSFWorkbook(inputStream);
			Sheet firstSheet = workbook.getSheetAt(0);

			// Decide which rows to process
			int rowStart = Math.min(15, firstSheet.getFirstRowNum());
			int rowEnd = Math.max(1400, firstSheet.getLastRowNum());

			boolean foundMeta = false;
			boolean foundWps = false;
			boolean foundLoc = false;
			boolean landing = false;

			for (int rowNum = rowStart; rowNum < rowEnd; rowNum++) {
				Row r = firstSheet.getRow(rowNum);
				if (r==null) {
					continue;
				}
				int lastColumn = foundLoc ? BushMissionGen.WP_LOCALIZATION_LEN : (landing ? BushMissionGen.WP_LANDING_LEN : BushMissionGen.WP_EXTRA_SPLIT_LEN);
				StringBuffer sb = new StringBuffer();

				boolean firstCell = true;

				for (int cn = 0; cn < lastColumn; cn++) {
					Cell cell = r.getCell(cn, MissingCellPolicy.RETURN_BLANK_AS_NULL);

					if (!firstCell && foundWps) {
						sb.append("|");
					}

					if (cell == null) {
						// VOID
					} else if (cell.getCellType() == CellType.STRING) {					
						sb.append(cell.getStringCellValue());

						if (cell.getStringCellValue().trim().equalsIgnoreCase("missiontype=landing")) {
							landing = true;
						}

						if (foundMeta && foundWps && cell.getStringCellValue().trim().equalsIgnoreCase("meta")) {
							foundLoc = true;
							lastColumn = BushMissionGen.WP_LOCALIZATION_LEN;
						}

						if (foundMeta && !foundWps && cell.getStringCellValue().trim().length()>0 && cell.getStringCellValue().contains("#icao")) {
							foundWps = true;
						}

						if (!foundMeta && !foundWps && cell.getStringCellValue().contains("=")) {
							foundMeta = true;
						}
					} else if (cell.getCellType() == CellType.NUMERIC) {					
						sb.append(String.valueOf(cell.getNumericCellValue()));
					}

					firstCell = false;
				}

				String candidateItem = sb.toString();
				if (!candidateItem.replace("|", "").trim().isEmpty()) {
					list.add(candidateItem);
				}
			}

			workbook.close();
			inputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return list;
	}

	public Message writeToXLS(String filename, StringBuffer sb1, StringBuffer sb3WpHeader, List<String[]> listWps) {
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet("Mission");

		List<String> list1 = new ArrayList<String>();
		String[] splitStr1 = sb1.toString().split(System.lineSeparator());
		for (String s : splitStr1) {
			list1.add(s);
		}

		List<String[]> listHeader = new ArrayList<String[]>();
		String[] splitStrHeader = sb3WpHeader.toString().split("\\|");
		for (int i=0; i<splitStrHeader.length; i++) {
			splitStrHeader[i] = splitStrHeader[i].trim();
		}
		listHeader.add(splitStrHeader);

		int rowCount = 0;

		// Write metadata
		for (String field : list1) {
			Row row = sheet.createRow(rowCount++);

			int columnCount = 0;
			Cell cell = row.createCell(columnCount++);
			cell.setCellValue(field);
		}

		// Write Wp header
		for (String[] items : listHeader) {
			Row row = sheet.createRow(rowCount++);

			int columnCount = 0;
			for (int i=0; i<items.length; i++) {
				String field = items[i];
				Cell cell = row.createCell(columnCount++);
				cell.setCellValue(field);
			}
		}

		// Write Wps
		for (String[] items : listWps) {
			Row row = sheet.createRow(rowCount++);

			int columnCount = 0;
			for (int i=0; i<items.length; i++) {
				String field = items[i];
				if (i==6) {
					String[] split = field.split(",");
					for (String s : split) {
						Cell cell = row.createCell(columnCount++);
						cell.setCellValue(s);
					}
				} else {
					Cell cell = row.createCell(columnCount++);
					cell.setCellValue(field);
				}
			}
		}

		String error = null;
		try (FileOutputStream outputStream = new FileOutputStream(filename)) {
			workbook.write(outputStream);
		} catch (FileNotFoundException e) {
			error = "Could not write the output file!";
		} catch (IOException e) {
			error = "Could not write the output file!";
		}

		try {
			workbook.close();
		} catch (IOException e) {
			if (error != null) {
				return new ErrorMessage(error);
			} else {
				return new ErrorMessage("Could not close the XLSX file!");
			}
		}

		return null;
	}
	
	public String readUrlToString(String url, Charset cs) {
		ClassLoader loader = BushMissionGen.class.getClassLoader();
		InputStream in = loader.getResourceAsStream(url);
		BufferedReader br = new BufferedReader(new InputStreamReader(in, cs));
		String file = "";
		String str;
		String kept_str = null;
		try {
			while ((str = br.readLine()) != null) {

				if (kept_str == null) {
					kept_str = str;
				} else {
					file += kept_str + System.lineSeparator();
					kept_str = str;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
		file += kept_str;

		return file;
	}

	public String readFileToString(String filename, Charset cs) {
		try {
			Path path = FileSystems.getDefault().getPath(filename);
			List<String> list = Files.readAllLines(path, cs);
			return list.stream().map(Object::toString).collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

	public Message writeStringToFile(String filename, String str, Charset cs) {
		try {
			Files.write(Paths.get(filename), Collections.singleton(str), cs);
			//System.out.println("File written to: " + filename);
		} catch (IOException e) {
			return new ErrorMessage("Could not write the output file!");
		}

		return null;
	}

	public Message writeStringToFile(String filename, StringBuffer sb1, StringBuffer sb2WpHeader, StringBuffer sb2, Charset cs) {
		String str = sb1.append(sb2WpHeader).append(sb2).toString();

		try {
			Files.write(Paths.get(filename), Collections.singleton(str), cs);
		} catch (IOException e) {
			return new ErrorMessage("Could not write the output file!");
		}

		return null;
	}
	
	public boolean copyDirectoryRecursively(String source, String destination) {
		Path sourceDir = Paths.get(source);
		Path destinationDir = Paths.get(destination);

		// Traverse the file tree and copy each file/directory.
		try {
			Files.walk(sourceDir)
			.forEach(sourcePath -> {
				try {
					Path targetPath = destinationDir.resolve(sourceDir.relativize(sourcePath));
					Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ex) {
					return;
				}
			});
		} catch (IOException e) {
			return false;
		}

		return true;
	}

	public boolean deleteDirectory(File directoryToBeDeleted) {
		File[] allContents = directoryToBeDeleted.listFiles();
		if (allContents != null) {
			for (File file : allContents) {
				deleteDirectory(file);
			}
		}
		return directoryToBeDeleted.delete();
	}

	public Message showFolder(String path) {
		Desktop desktop = Desktop.getDesktop();
		File dirToOpen = null;
		try {
			dirToOpen = new File(path);
			desktop.open(dirToOpen);
		} catch (IllegalArgumentException | IOException iae) {
			return new ErrorMessage("Could not find the path:\n\n" + path);
		}
		return null;
	}

	public Message execCmd(String... cmd) {
		try {
			ProcessBuilder builder = new ProcessBuilder(cmd);
			builder.directory(new File(cmd[0]).getParentFile());
			builder.redirectErrorStream(true);
			Process process =  builder.start();

			Scanner s = new Scanner(process.getInputStream());
			StringBuilder text = new StringBuilder();
			while (s.hasNextLine()) {
				text.append(s.nextLine());
				text.append("\n");
			}
			s.close();

			process.waitFor();
			return new InfoMessage(text.toString());
		} catch (Exception e) {
			return new ErrorMessage(e.getLocalizedMessage());
		}
	}
}
