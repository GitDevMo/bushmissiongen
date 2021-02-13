package bushmissiongen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Settings {
	public String lastUsedFile = "";

	public static final int MAX_RECENT_FILES = 5;
	public List<String> recentFiles = new ArrayList<>();

	private String settingsFile = "settings.ini";

	public Settings() {
		loadSettings();
	}

	public void loadSettings() {
		String pathString = System.getProperty("user.dir") + File.separator + settingsFile;
		File pathFile = new File(pathString);

		if (pathFile.exists()) {
			try {
				Path path = FileSystems.getDefault().getPath(pathString);
				List<String> list = Files.readAllLines(path, StandardCharsets.UTF_8);
				recentFiles.clear();

				for (String item : list) {
					if (item.startsWith("recent")) {
						String value = item.substring(7);
						if (new File(value).exists()) {
							recentFiles.add(value);
						}
					}
				}
				
				trimRecent();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void saveSettings() {
		String pathString = System.getProperty("user.dir") + File.separator + settingsFile;
		File pathFile = new File(pathString);


		try {
			FileWriter fw = new FileWriter(pathFile);
			BufferedWriter bw = new BufferedWriter(fw);

			for (String recent : recentFiles) {
				bw.write("recent=" + recent + System.lineSeparator());
			}

			bw.close();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void addRecent(String recent) {
		if (recentFiles.contains(recent)) {
			recentFiles.remove(recent);
		}		

		recentFiles.add(0, recent);

		trimRecent();
	}

	private void trimRecent() {
		while (recentFiles.size() > MAX_RECENT_FILES) {
			recentFiles.remove(recentFiles.size()-1);
		}
	}
}
