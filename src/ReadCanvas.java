import java.util.Vector;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;

public class ReadCanvas extends GameCanvas {

	private static final int WIDTH_MARGIN = 2;
	
	int layoutWidth;
	Vector parsed = new Vector();
	Vector render;

	int contentHeight;
	int width, height;

	int scroll;
	int lastX, lastY;
	int pressX, pressY;
	long pressTime, releaseTime;
	boolean pressed, draggedMuch;
	boolean scrollSlide, scrollPreSlide;
	boolean scrolling;
	float scrollSlideMaxTime, scrollSlideSpeed;
	int scrollTimer, scrollTarget;

	private Graphics graphics;
	
	public ReadCanvas() {
		super(false);
//		addCommand(Ran.backCmd);
//		setCommandListener(Ran.midlet);
	}
	
	public void paint_(Graphics g) {
		int w = width = getWidth(); int h = height = getHeight();
		int contentHeight = this.contentHeight;
		g.setColor(0);
		g.fillRect(0, 0, w, h);

		// layout
		if (render == null || w != layoutWidth) {
			if (render == null) {
				render = new Vector();
			} else render.removeAllElements();
			layoutWidth = w;
			Vector res = render;
			int x = 0, y = 0, idx = 0;
			
			int fh = 0;
			int l = parsed.size();
			for (int ei = 0; ei < l; ++ei) {
				Object[] e = (Object[]) parsed.elementAt(ei);
				String text = (String) e[0];
				Font font = (Font) e[1];
				
				fh = font.getHeight();
				if (text == null || "\n".equals(text)) {
					x = 0;
					y += fh;
					continue;
				}
				
				int ch = 0;
				int sl = text.length();
				char c;
				while (ch < sl && ((c = text.charAt(ch)) < ' ')) {
					ch++;
					if (c != '\n') continue;
					x = 0;
					y += fh;
				}
				
				if (text.indexOf('\n', ch) == -1) {
					int[] out = split(text, font, w, x, y, idx, ch, sl, fh, res);
					x = out[0]; y = out[1]; idx = out[2];
				} else {
					int j = ch;
					for (int i = ch; i < sl; ++i) {
						if ((c = text.charAt(i)) == '\n') {
							int[] out = split(text, font, w, x, y, idx, j, i, fh, res);
							x = 0; y = out[1] + fh; idx = out[2];
							j = i + 1;
						}
					}
					if (j != sl) {
						int[] out = split(text, font, w, x, y, idx, j, sl, fh, res);
						x = out[0]; y = out[1]; idx = out[2];
					}
				}
			}
			this.contentHeight = contentHeight = y + fh * 2;
		}
		
		if (scrolling) {
			if (!scrollPreSlide && (releaseTime - pressTime) > 0) {
				float f = Ran.repaintTime > 33 ? Ran.repaintTime / 33f : 1f;
				if (scrollSlide && Math.abs(scrollSlideSpeed) > 0.8F && (System.currentTimeMillis() - releaseTime) < scrollSlideMaxTime && scroll((int) (scrollSlideSpeed * f))) {
					scrollSlideSpeed *= 0.967f;
					if (f >= 2) {
						int j = (int)f-1;
						for(int i = 0; i < j; scrollSlideSpeed *= 0.967f, i++);
					}
				} else {
					scrollSlideSpeed = 0;
					scrolling = false;
				}
			}
		}
		
		if (scrollTarget >= 0) {
			scrolling = true;
			if (Math.abs(scroll - scrollTarget) < 1) {
				scroll = scrollTarget;
				scrollTarget = -1;
				scrolling = false;
			} else {
				scroll = (int) lerp(scroll, scrollTarget, Ran.repaintTime > 33 ? 4 * Ran.repaintTime / 33f : 4, 20);
			}
			if (scroll < 0) {
				scroll = 0;
				scrollTarget = -1;
				scrolling = false;
			}
			if (scroll > contentHeight - height) {
				scroll = contentHeight - height;
				scrollTarget = -1;
				scrolling = false;
			}
		}
		
		int scroll = (int) this.scroll;
		if (scroll < 0) this.scroll = scroll = 0;
		else if (contentHeight <= h) this.scroll = scroll = 0;
		else if (scroll > contentHeight - h) this.scroll = scroll = contentHeight - h;
		
		g.setColor(-1);
		int l = render.size();
		for (int i = 0; i < l; ++i) {
			Object[] obj = (Object[]) render.elementAt(i);
			int[] pos = (int[]) obj[2];
			int y = pos[1] - scroll;
			if (y < -30) continue;
			Font font = (Font) obj[1];
			if (y < -font.getHeight()) continue;
			if (y > h) break;
			g.setFont(font);
			g.drawString((String) obj[0], pos[0] + WIDTH_MARGIN, y, 0);
		}
		
		// scroll bar
		if (scrollTimer < 1 && contentHeight > h) {
			scrollTimer++;
			g.setColor(0xababab);
			int sw = 4;
			int hh = contentHeight;
			if (hh <= 0) hh = 1;
			int sby = (int)(((float)scroll / (float)hh) * h);
			int sbh = (int)(((float)h / (float)hh) * h);
			g.fillRect(w-sw, sby, sw-2, sbh);
		}
		if (scrollTarget <= 0) scrollTimer = 0;
	}

	public void paint() {
		paint_(graphics == null ? graphics = getGraphics() : graphics);
	}
	
	protected void showNotify() {
		graphics = null;
		needRepaint();
	}
	
	protected void hideNotify() {
		graphics = null;
	}
	
	protected void sizeChanged(int w, int h) {
		graphics = null;
		needRepaint();
	}
	
	protected void keyPressed(int key) {
		key(key, false);
	}
	
	protected void keyRepeated(int key) {
		key(key, true);
	}
	
	private void key(int key, boolean repeat) {
		int game = getGameAction(key);
		boolean repaint = false;
		if (game == Canvas.DOWN) {
			scrollTarget = scroll + height / 8;
			repaint = true;
		} else if (game == Canvas.UP) {
			scrollTarget = scroll - height / 8;
			repaint = true;
		} else if (key == -7) {
			Ran.midlet.commandAction(Ran.backCmd, this);
		}
		if (repaint) {
			if (scrollTarget < 0) scroll = 0;
			else if (scrollTarget > contentHeight - height) scrollTarget = contentHeight - height;
			needRepaint();
		}
	}
	
	public void pointerPressed(int x, int y) {
		pressed = true;
		lastX = pressX = x;
		lastY = pressY = y;
		pressTime = System.currentTimeMillis();
		draggedMuch = false;
		scrollSlide = false;
		scrollPreSlide = false;
		scrollSlideSpeed = 0;
		needRepaint();
	}

	public void pointerReleased(int x, int y) {
		int time = (int) ((releaseTime = System.currentTimeMillis()) - pressTime);
		int dx = Math.abs(x - pressX);
		int dy = y - pressY;
		int ady = Math.abs(dy);
		if (pressed) {
			if (draggedMuch) {
				if (time < 300) {
					scrollSlide = scrollPreSlide;
					scrolling = true;
				} else {
					scrollSlide = false;
				}
			} else {
				if (dx <= 6 && ady <= 6) {
//					tap(x, y, time);
				} else if (time < 200 && dx < 12) {
					scroll(y - lastY);
				}
			}
			scrollPreSlide = false;
			pressed = false;
		}
		lastX = x;
		lastY = y;
		needRepaint();
	}

	public void pointerDragged(int x, int y) {
		final int sdX = Math.abs(pressX - x);
		final int sdY = Math.abs(pressY - y);
		final int dX = lastX - x;
		final int dY = lastY - y;
		final int adX = Math.abs(dX);
		final int adY = Math.abs(dY);
		// if (draggedMuch) {
		if (adY > 0 && adX < 16) {
			float f1 = 8F;
			float f2 = 3F;
			scrollPreSlide = true;
			scrollSlideMaxTime += (Math.abs(dY) / f1) * 2400F;
			float m = dY / f2;
			// разные направления
			if (scrollSlideSpeed > 0 && m < 0 || scrollSlideSpeed < 0 && m > 0) scrollSlideSpeed = 0;
			if (Math.abs(scrollSlideSpeed) > 60) {
				scrollSlideSpeed *= 0.95;
				m *= 0.8;
			}
			scrollSlideSpeed += m;
			if (scrollSlideSpeed > 60) {
				scrollSlideSpeed = 60;
			} else if (scrollSlideSpeed < -60) {
				scrollSlideSpeed = -60;
			}
			scroll(dY);
			//preDrift += -deltaY;
			//if (adY < adX - 2) scrollHorizontally(dX);
		}
		// }
		if (sdY > 1 || sdX > 1) {
			draggedMuch = true;
			scrolling = true;
		}
		lastX = x;
		lastY = y;
		needRepaint();
	}

	private boolean scroll(int i) {
		if (contentHeight <= height) {
			return false;
		}
		int scroll = this.scroll + i;
		if (scroll < 0) {
			scroll = 0;
			return false;
		} else if (scroll > contentHeight - height) {
			scroll = contentHeight - height;
			return false;
		}
		this.scroll = scroll;
		scrollTimer = 0;
		scrollTarget = -1;
		return true;
	}
	
	private void needRepaint() {
		Ran.repaint();
	}
	
	static float lerp(float start, float target, float mul, float div) {
		return start + ((target - start) * mul / div);
	}
	
	private static int[] split(String text, Font font, int width, int x, int y, int idx, int ch, int sl, int fh, Vector res) {
		if (ch != sl) {
			int ew = font.substringWidth(text, ch, sl - ch);
			if (x + ew < width) {
				res.addElement(new Object[] { text.substring(ch, sl), font, new int[] {x, y} });
				x += ew; idx ++;
			} else {
				for (int i = ch; i < sl; i++) {
					if (x + font.stringWidth(text.substring(ch, i+1)) >= width) {
						w: {
							for (int j = i; j > ch; j--) {
								char c = text.charAt(j);
								if (c == ' ' || (c >= ',' && c <= '/')) {
									res.addElement(new Object[] { text.substring(ch, ++ j), font, new int[] {x, y} });
									x = 0; y += fh; idx ++;
									
									i = ch = j;
									break w;
								}
							}
	
							res.addElement(new Object[] { text.substring(ch, i), font, new int[] {x, y} });
							x = 0; y += fh; idx ++;
							ch = i;
						}
					}
				}
				if (ch != sl) {
					String s = text.substring(ch, sl);
					res.addElement(new Object[] { s, font, new int[] {x, y} });
					x += font.stringWidth(s); idx ++;
				}
			}
		}
		return new int[] {x, y, idx};
	}
	
	public void beginParagraph() {
		append("\n", Ran.smallPlainFont);
	}
	
	public void endParagraph() {
		append("\n", Ran.smallPlainFont);
	}

	public void append(String text, Font font) {
		parsed.addElement(new Object[] { text, font });
		layoutWidth = 0;
		if (isShown()) needRepaint();
	}

}
