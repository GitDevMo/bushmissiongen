package bushmissiongen.handling;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;

public class ImageHandling {
	public ImageHandling() {}
	
	private Color getContrastColor(Color color) {
		double y = (299 * color.getRed() + 587 * color.getGreen() + 114 * color.getBlue()) / 1000;
		return y >= 128 ? Color.black : Color.white;
	}

	private int drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
		int lastY = 0;

		// Get the FontMetrics
		FontMetrics metrics = g.getFontMetrics(font);
		// Set the font
		g.setFont(font);

		String[] textSplit = text.split("\\|", -1);
		int count = 0;
		double startF = -(textSplit.length-1)/2.0;
		for (String s : textSplit) {
			// Determine the X coordinate for the text
			int x = rect.x + (rect.width - metrics.stringWidth(s)) / 2;
			// Determine the Y coordinate for the text (note we add the ascent, as in java 2d 0 is top of the screen)
			int term = (int)Math.round((startF + count)*(metrics.getAscent()*2));
			int y = term + rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
			lastY = y;

			// Draw the String
			g.drawString(s, x, y);
			count++;
		}

		return lastY;
	}

	public Message generateImage(File file, int width, int height, String format, String text, int style, double scale) {
		// Never ovewrite!
		if (file.exists()) {
			return null;
		}

		// Constructs a BufferedImage of one of the predefined image types.
		BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		// Create a graphics which can be used to draw into the buffered image
		Graphics2D g2d = bufferedImage.createGraphics();

		// fill all the image with a random color
		Color backColor = Color.getHSBColor((float)Math.random(), (float)Math.random(), (float)(0.2+0.6*Math.random()));
		g2d.setColor(backColor);
		g2d.fillRect(0, 0, width, height);

		// create a high contrast color
		Color textColor = getContrastColor(backColor);
		g2d.setColor(textColor);

		// create a string to output
		Font textFont = new Font("Arial", style, (int)Math.round(scale*height/10.0));
		g2d.setFont(textFont);
		int lastY = drawCenteredString(g2d, text, new Rectangle(width, height), textFont);

		// draw format information
		textFont = new Font("Arial", style, (int)Math.round(scale*height/15.0));
		g2d.setFont(textFont);
		drawCenteredString(g2d, format.toUpperCase() + " " + width + " X " + height, new Rectangle(0, lastY, width, height-lastY), textFont);

		// Disposes of this graphics context and releases any system resources that it is using. 
		g2d.dispose();

		try {
			if (format.equals("png")) {
				// Save as PNG
				ImageIO.write(bufferedImage, "png", file);
			}

			// Save as JPEG
			if (format.equals("jpg")) {
				ImageIO.write(bufferedImage, "jpg", file);
			}
		} catch (IOException e) {
			return new ErrorMessage("Could not create image.\n\n" + file.getAbsolutePath());
		}

		return null;
	}
}
